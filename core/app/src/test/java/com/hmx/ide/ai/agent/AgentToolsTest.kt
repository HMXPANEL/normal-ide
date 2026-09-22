package com.hmx.ide.ai.agent

import com.google.common.truth.Truth.assertThat
import com.hmx.ide.ai.agent.events.Action
import com.hmx.ide.ai.agent.events.FileReadOutput
import com.hmx.ide.ai.agent.events.FindSymbolInput
import com.hmx.ide.ai.agent.events.PermissionDenied
import com.hmx.ide.ai.agent.events.ReadFileInput
import com.hmx.ide.ai.agent.events.SearchInput
import com.hmx.ide.ai.agent.events.SearchOutput
import com.hmx.ide.ai.agent.events.SymbolOutput
import com.hmx.ide.ai.agent.events.ToolFailure
import com.hmx.ide.ai.agent.events.ToolInput
import com.hmx.ide.ai.agent.events.ToolOutput
import com.hmx.ide.ai.agent.permissions.PermissionLevel
import com.hmx.ide.ai.agent.permissions.PermissionManager
import com.hmx.ide.ai.agent.tools.AgentTool
import com.hmx.ide.ai.agent.tools.FindSymbolTool
import com.hmx.ide.ai.agent.tools.ReadFileTool
import com.hmx.ide.ai.agent.tools.SearchTool
import com.hmx.ide.ai.agent.tools.ToolExecutor
import com.hmx.ide.ai.agent.tools.ToolRegistry
import com.hmx.ide.ai.agent.trace.AgentTrace
import com.hmx.ide.ai.agent.trace.TraceEventType
import com.hmx.ide.indexing.model.SymbolLocation
import com.hmx.ide.knowledge.KnowledgeEngine
import com.hmx.ide.knowledge.model.DeclarationModel
import com.hmx.ide.knowledge.model.FileModel
import com.hmx.ide.knowledge.model.ProjectModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Shared fakes for tool/executor/loop tests. */
class FakeKnowledgeEngine(
  private val exact: Map<String, SymbolLocation> = emptyMap(),
  private val prefix: Map<String, List<SymbolLocation>> = emptyMap(),
) : KnowledgeEngine {
  override val currentProject: ProjectModel? = null
  override fun start() = Unit
  override fun refresh(projectDir: File) = Unit
  override fun getFile(file: File): FileModel? = null
  override fun searchSymbol(fqn: String): SymbolLocation? = exact[fqn]
  override fun searchSymbols(prefix: String): List<SymbolLocation> =
    this.prefix[prefix].orEmpty()
  override fun findDeclarationsInFile(file: File): List<DeclarationModel> = emptyList()
  override fun invalidateFile(file: File) = Unit
  override fun invalidateAll() = Unit
}

class EchoTool(
  override val name: String = "echo",
  override val permission: PermissionLevel = PermissionLevel.READ,
  private val reply: (ToolInput) -> ToolOutput = { FileReadOutput("x", "ok", false, 2) },
) : AgentTool {
  override val description = "test echo tool"
  override suspend fun execute(input: ToolInput): ToolOutput = reply(input)
}

class WriteLevelTool : AgentTool {
  override val name = "write_file"
  override val description = "future write tool, must be denied in Phase 1"
  override val permission = PermissionLevel.WRITE
  var calls = 0
  override suspend fun execute(input: ToolInput): ToolOutput {
    calls++
    return FileReadOutput("x", "should never run", false, 0)
  }
}

class AgentToolsTest {

  @get:Rule
  val tmp = TemporaryFolder()

  // --- Registry ---

  @Test
  fun `registry registers looks up and lists tools`() {
    val registry = ToolRegistry()
    registry.register(EchoTool())
    assertThat(registry.names()).containsExactly("echo")
    assertThat(registry.require("echo").description).isNotEmpty()
    assertThat(registry.descriptors()).hasSize(1)
    assertThat(registry.descriptors()[0].permission).isEqualTo(PermissionLevel.READ)
  }

  @Test
  fun `registry rejects duplicate registration`() {
    val registry = ToolRegistry()
    registry.register(EchoTool())
    var rejected = false
    try {
      registry.register(EchoTool())
    } catch (_: IllegalArgumentException) {
      rejected = true
    }
    assertThat(rejected).isTrue()
    assertThat(registry.unregister("echo")).isTrue()
    assertThat(registry.find("echo")).isNull()
  }

  // --- Executor ---

  @Test
  fun `executor runs allowed tool and traces observation`() = runBlocking {
    val trace = AgentTrace()
    val registry = ToolRegistry().apply { register(EchoTool()) }
    val executor = ToolExecutor(registry, PermissionManager(), trace, "s1")
    val observation = executor.run(Action(toolName = "echo", input = ReadFileInput("a.kt")))
    assertThat(observation.output).isInstanceOf(FileReadOutput::class.java)
    assertThat(trace.ofType(TraceEventType.TOOL_STARTED)).hasSize(1)
    assertThat(trace.ofType(TraceEventType.TOOL_COMPLETED)).hasSize(1)
    assertThat(trace.ofType(TraceEventType.OBSERVATION_CREATED)).hasSize(1)
    assertThat(trace.ofType(TraceEventType.PERMISSION_REQUESTED)).hasSize(1)
  }

  @Test
  fun `executor denies WRITE tool and never executes it`() = runBlocking {
    val tool = WriteLevelTool()
    val registry = ToolRegistry().apply { register(tool) }
    val executor = ToolExecutor(registry, PermissionManager())
    val observation = executor.run(Action(toolName = "write_file", input = ReadFileInput("a.kt")))
    assertThat(observation.output).isInstanceOf(PermissionDenied::class.java)
    assertThat(tool.calls).isEqualTo(0)
  }

  @Test
  fun `executor maps unknown tool to failure`() = runBlocking {
    val executor = ToolExecutor(ToolRegistry(), PermissionManager())
    val observation = executor.run(Action(toolName = "nope", input = ReadFileInput("a.kt")))
    val output = observation.output as ToolFailure
    assertThat(output.reason).contains("Unknown tool")
  }

  @Test
  fun `executor maps tool exception to failure`() = runBlocking {
    val registry = ToolRegistry().apply {
      register(EchoTool(reply = { throw IllegalStateException("boom") }))
    }
    val executor = ToolExecutor(registry, PermissionManager())
    val observation = executor.run(Action(toolName = "echo", input = ReadFileInput("a.kt")))
    val output = observation.output as ToolFailure
    assertThat(output.reason).contains("boom")
  }

  @Test
  fun `executor propagates cancellation instead of converting to failure`() = runBlocking {
    var sawCancellation = false
    val registry = ToolRegistry().apply {
      register(
        EchoTool(reply = {
          throw CancellationException("stop")
        }),
      )
    }
    val executor = ToolExecutor(registry, PermissionManager())
    val job = launch {
      try {
        executor.run(Action(toolName = "echo", input = ReadFileInput("a.kt")))
      } catch (_: CancellationException) {
        sawCancellation = true
      }
    }
    job.join()
    assertThat(sawCancellation).isTrue()
  }

  // --- read_file ---

  @Test
  fun `read_file returns content for valid file`() = runBlocking {
    tmp.newFile("Hello.kt").writeText("package a\n\nclass Hello\n")
    val tool = ReadFileTool(tmp.root)
    val output = tool.execute(ReadFileInput("Hello.kt")) as FileReadOutput
    assertThat(output.content).contains("class Hello")
    assertThat(output.truncated).isFalse()
  }

  @Test
  fun `read_file reports missing file as failure`() = runBlocking {
    val tool = ReadFileTool(tmp.root)
    val output = tool.execute(ReadFileInput("Missing.kt"))
    assertThat(output).isInstanceOf(ToolFailure::class.java)
  }

  @Test
  fun `read_file truncates huge files within bounds`() = runBlocking {
    val big = buildString {
      repeat(2000) { appendLine("val x$it = $it") }
    }
    tmp.newFile("Big.kt").writeText(big)
    val tool = ReadFileTool(tmp.root, maxChars = 1000, maxLines = 50)
    val output = tool.execute(ReadFileInput("Big.kt")) as FileReadOutput
    assertThat(output.truncated).isTrue()
    assertThat(output.content.length).isAtMost(1200)
  }

  @Test
  fun `read_file blocks traversal`() = runBlocking {
    val tool = ReadFileTool(tmp.root)
    val output = tool.execute(ReadFileInput("../outside.kt"))
    val failure = output as ToolFailure
    assertThat(failure.recoverable).isFalse()
  }

  // --- search ---

  @Test
  fun `search finds single match with location`() = runBlocking {
    File(tmp.root, "src").mkdirs()
    File(tmp.root, "src/A.kt").writeText("class Apple\n")
    File(tmp.root, "src/B.kt").writeText("class Banana\n")
    val tool = SearchTool(tmp.root)
    val output = tool.execute(SearchInput("apple")) as SearchOutput
    assertThat(output.matches).hasSize(1)
    assertThat(output.matches[0].line).isEqualTo(1)
    assertThat(output.filesScanned).isAtLeast(2)
  }

  @Test
  fun `search finds multiple matches and reports no match cleanly`() = runBlocking {
    File(tmp.root, "A.kt").writeText("shared_token = 1\n")
    File(tmp.root, "B.kt").writeText("shared_token = 2\n")
    val tool = SearchTool(tmp.root)
    val multi = tool.execute(SearchInput("shared_token")) as SearchOutput
    assertThat(multi.matches.size).isEqualTo(2)
    val none = tool.execute(SearchInput("zzz_no_such_token")) as SearchOutput
    assertThat(none.matches).isEmpty()
  }

  // --- find_symbol ---

  @Test
  fun `find_symbol returns known symbol location`() = runBlocking {
    val engine = FakeKnowledgeEngine(
      exact = mapOf("com.example.Foo" to SymbolLocation("src/Foo.kt", 10, 5)),
    )
    val tool = FindSymbolTool(engine)
    val output = tool.execute(FindSymbolInput("com.example.Foo")) as SymbolOutput
    assertThat(output.hits).hasSize(1)
    assertThat(output.hits[0].filePath).isEqualTo("src/Foo.kt")
    assertThat(output.hits[0].line).isEqualTo(10)
  }

  @Test
  fun `find_symbol returns empty hits for unknown symbol`() = runBlocking {
    val tool = FindSymbolTool(FakeKnowledgeEngine())
    val output = tool.execute(FindSymbolInput("com.example.Missing")) as SymbolOutput
    assertThat(output.hits).isEmpty()
  }

  @Test
  fun `slow tool cancellation reaches caller`() = runBlocking {
    val registry = ToolRegistry().apply {
      register(
        EchoTool(reply = {
          delay(30_000)
          FileReadOutput("x", "late", false, 4)
        }),
      )
    }
    val executor = ToolExecutor(registry, PermissionManager())
    var cancelled = false
    val job = launch {
      try {
        executor.run(Action(toolName = "echo", input = ReadFileInput("a.kt")))
      } catch (_: CancellationException) {
        cancelled = true
      }
    }
    delay(50)
    job.cancelAndJoin()
    assertThat(cancelled).isTrue()
  }
}
