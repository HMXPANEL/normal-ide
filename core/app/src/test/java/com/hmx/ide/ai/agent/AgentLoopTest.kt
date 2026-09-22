package com.hmx.ide.ai.agent

import com.google.common.truth.Truth.assertThat
import com.hmx.ide.ai.agent.core.AgentConfig
import com.hmx.ide.ai.agent.core.AgentLoop
import com.hmx.ide.ai.agent.core.AgentModel
import com.hmx.ide.ai.agent.core.AgentResult
import com.hmx.ide.ai.agent.core.AgentTurn
import com.hmx.ide.ai.agent.core.ModelDecision
import com.hmx.ide.ai.agent.events.PermissionDenied
import com.hmx.ide.ai.agent.events.ReadFileInput
import com.hmx.ide.ai.agent.events.ToolFailure
import com.hmx.ide.ai.agent.events.ToolInput
import com.hmx.ide.ai.agent.permissions.PermissionManager
import com.hmx.ide.ai.agent.tools.ReadFileTool
import com.hmx.ide.ai.agent.tools.ToolDescriptor
import com.hmx.ide.ai.agent.tools.ToolExecutor
import com.hmx.ide.ai.agent.tools.ToolRegistry
import com.hmx.ide.ai.agent.trace.AgentTrace
import com.hmx.ide.ai.agent.trace.TraceEventType
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Deterministic script model: replays decisions, records what it observed. */
class ScriptModel(
  private val script: ArrayDeque<suspend (List<AgentTurn>) -> ModelDecision>,
) : AgentModel {
  val seenHistories = mutableListOf<Int>()
  override suspend fun decide(
    history: List<AgentTurn>,
    tools: List<ToolDescriptor>,
  ): ModelDecision {
    seenHistories.add(history.size)
    val next = script.removeFirstOrNull() ?: return ModelDecision.Finish("script exhausted")
    return next(history)
  }
}

fun scriptModel(vararg decisions: suspend (List<AgentTurn>) -> ModelDecision): ScriptModel =
  ScriptModel(ArrayDeque(decisions.toList()))

fun callTool(name: String, input: ToolInput): suspend (List<AgentTurn>) -> ModelDecision =
  { ModelDecision.CallTool(name, input) }

fun finish(summary: String): suspend (List<AgentTurn>) -> ModelDecision =
  { ModelDecision.Finish(summary) }

class AgentLoopTest {

  @get:Rule
  val tmp = TemporaryFolder()

  private fun registryWithFiles(): ToolRegistry {
    tmp.newFile("Main.kt").writeText("fun main() {}\n")
    return ToolRegistry().apply {
      register(ReadFileTool(tmp.root))
      register(EchoTool())
    }
  }

  private fun loopOf(
    model: AgentModel,
    registry: ToolRegistry,
    config: AgentConfig = AgentConfig(),
    trace: AgentTrace = AgentTrace(),
  ): AgentLoop = AgentLoop(
    model = model,
    registry = registry,
    executorFactory = { trace, sessionId ->
      ToolExecutor(registry, PermissionManager(config.validated().permissionPolicy), trace, sessionId)
    },
    config = config,
    trace = trace,
  )

  @Test
  fun `loop completes after one tool call`() {
    runBlocking {
    val model = scriptModel(callTool("read_file", ReadFileInput("Main.kt")), finish("done"))
    val trace = AgentTrace()
    val result = loopOf(model, registryWithFiles(), trace = trace).run("read main", tmp.root)
    val completed = result as AgentResult.Completed
    assertThat(completed.toolCalls).isEqualTo(1)
    assertThat(completed.iterations).isEqualTo(2)
    assertThat(completed.summary).isEqualTo("done")
    assertThat(trace.ofType(TraceEventType.SESSION_STARTED)).hasSize(1)
    assertThat(trace.ofType(TraceEventType.SESSION_COMPLETED)).hasSize(1)
    assertThat(trace.ofType(TraceEventType.OBSERVATION_CREATED)).hasSize(1)
    val sessionId = trace.ofType(TraceEventType.SESSION_STARTED).single().sessionId
    assertThat(sessionId).isNotEmpty()
    assertThat(trace.events().map { it.sessionId }.toSet()).containsExactly(sessionId)
  }
  }

  @Test
  fun `observation content reaches the model next turn`() {
    runBlocking {
    var observed: String? = null
    val model = scriptModel(
      callTool("read_file", ReadFileInput("Main.kt")),
      { history ->
        observed = (history.single().observation.output as com.hmx.ide.ai.agent.events.FileReadOutput).content
        ModelDecision.Finish("saw it")
      },
    )
    val result = loopOf(model, registryWithFiles()).run("t", tmp.root)
    assertThat(result).isInstanceOf(AgentResult.Completed::class.java)
    assertThat(observed).contains("fun main()")
  }
  }

  @Test
  fun `loop runs multiple iterations then finishes`() {
    runBlocking {
    val model = scriptModel(
      callTool("echo", ReadFileInput("a")),
      callTool("echo", ReadFileInput("b")),
      callTool("echo", ReadFileInput("c")),
      finish("all three"),
    )
    val result = loopOf(model, registryWithFiles()).run("t", tmp.root)
    val completed = result as AgentResult.Completed
    assertThat(completed.toolCalls).isEqualTo(3)
    assertThat(model.seenHistories).containsExactly(0, 1, 2, 3)
  }
  }

  @Test
  fun `max iterations stops the loop with limit result`() {
    runBlocking {
    val model = scriptModel(
      callTool("echo", ReadFileInput("a")),
      callTool("echo", ReadFileInput("b")),
      callTool("echo", ReadFileInput("c")),
      callTool("echo", ReadFileInput("d")),
    )
    val result = loopOf(model, registryWithFiles(), AgentConfig(maxIterations = 3))
      .run("t", tmp.root)
    val limited = result as AgentResult.CompletedWithLimit
    assertThat(limited.iterations).isEqualTo(3)
    assertThat(limited.toolCalls).isEqualTo(3)
  }
  }

  @Test
  fun `max tool calls is enforced separately from iterations`() {
    runBlocking {
    val model = scriptModel(
      callTool("echo", ReadFileInput("a")),
      callTool("echo", ReadFileInput("b")),
      callTool("echo", ReadFileInput("c")),
    )
    val result = loopOf(
      model,
      registryWithFiles(),
      AgentConfig(maxIterations = 10, maxToolCalls = 2),
    ).run("t", tmp.root)
    val limited = result as AgentResult.CompletedWithLimit
    assertThat(limited.toolCalls).isEqualTo(2)
  }
  }

  @Test
  fun `recoverable tool failure is fed back and loop continues`() {
    runBlocking {
    var sawFailure = false
    val model = scriptModel(
      callTool("read_file", ReadFileInput("DoesNotExist.kt")),
      { history ->
        sawFailure = history.single().observation.output is ToolFailure
        ModelDecision.Finish("recovered")
      },
    )
    val result = loopOf(model, registryWithFiles()).run("t", tmp.root)
    assertThat(result).isInstanceOf(AgentResult.Completed::class.java)
    assertThat(sawFailure).isTrue()
  }
  }

  @Test
  fun `permission denial fails the run without retry`() {
    runBlocking {
    val registry = ToolRegistry().apply { register(WriteLevelTool()) }
    val model = scriptModel(callTool("write_file", ReadFileInput("a.kt")))
    val result = loopOf(model, registry).run("t", tmp.root)
    val failed = result as AgentResult.Failed
    assertThat(failed.reason).contains("Permission denied")
  }
  }

  @Test
  fun `unknown tool surfaces as failure observation then model finishes`() {
    runBlocking {
    var sawUnknown = false
    val model = scriptModel(
      callTool("ghost_tool", ReadFileInput("a.kt")),
      { history ->
        val output = history.single().observation.output as ToolFailure
        sawUnknown = output.reason.contains("Unknown tool")
        ModelDecision.Finish("ok")
      },
    )
    val result = loopOf(model, registryWithFiles()).run("t", tmp.root)
    assertThat(result).isInstanceOf(AgentResult.Completed::class.java)
    assertThat(sawUnknown).isTrue()
  }
  }

  @Test
  fun `model throwing fails the run`() {
    runBlocking {
    val model = object : AgentModel {
      override suspend fun decide(
        history: List<AgentTurn>,
        tools: List<ToolDescriptor>,
      ): ModelDecision = throw IllegalStateException("model exploded")
    }
    val result = loopOf(model, registryWithFiles()).run("t", tmp.root)
    val failed = result as AgentResult.Failed
    assertThat(failed.reason).contains("Model decision failed")
  }
  }

  @Test
  fun `cancellation during decide yields Cancelled result`() {
    runBlocking {
    val model = object : AgentModel {
      override suspend fun decide(
        history: List<AgentTurn>,
        tools: List<ToolDescriptor>,
      ): ModelDecision {
        delay(30_000)
        return ModelDecision.Finish("never")
      }
    }
    val loop = loopOf(model, registryWithFiles())
    var result: AgentResult? = null
    val job = launch { result = loop.run("t", tmp.root) }
    delay(100)
    job.cancelAndJoin()
    assertThat(result).isInstanceOf(AgentResult.Cancelled::class.java)
  }
  }

  @Test
  fun `denied observation type is PermissionDenied`() {
    runBlocking {
    val registry = ToolRegistry().apply { register(WriteLevelTool()) }
    val executor = ToolExecutor(registry, PermissionManager())
    val observation = executor.run(
      com.hmx.ide.ai.agent.events.Action(toolName = "write_file", input = ReadFileInput("a")),
    )
    assertThat(observation.output).isInstanceOf(PermissionDenied::class.java)
    }
  }
}
