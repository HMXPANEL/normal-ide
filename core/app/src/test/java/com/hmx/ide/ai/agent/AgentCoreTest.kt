package com.hmx.ide.ai.agent

import com.google.common.truth.Truth.assertThat
import com.hmx.ide.ai.agent.context.ContextBudget
import com.hmx.ide.ai.agent.context.ContextRequest
import com.hmx.ide.ai.agent.core.AgentConfig
import com.hmx.ide.ai.agent.core.AgentSession
import com.hmx.ide.ai.agent.core.AgentState
import com.hmx.ide.ai.agent.core.AgentStateMachine
import com.hmx.ide.ai.agent.security.WorkspaceGuard
import com.hmx.ide.ai.agent.trace.AgentTrace
import com.hmx.ide.ai.agent.trace.TraceEvent
import com.hmx.ide.ai.agent.trace.TraceEventType
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AgentCoreTest {

  @get:Rule
  val tmp = TemporaryFolder()

  // --- State machine ---

  @Test
  fun `valid lifecycle IDLE to COMPLETED`() {
    var state = AgentState.IDLE
    state = AgentStateMachine.transition(state, AgentState.RUNNING)
    state = AgentStateMachine.transition(state, AgentState.WAITING_FOR_TOOL)
    state = AgentStateMachine.transition(state, AgentState.RUNNING)
    state = AgentStateMachine.transition(state, AgentState.COMPLETED)
    assertThat(state).isEqualTo(AgentState.COMPLETED)
  }

  @Test
  fun `failure path RUNNING to FAILED`() {
    val state = AgentStateMachine.transition(AgentState.RUNNING, AgentState.FAILED)
    assertThat(state).isEqualTo(AgentState.FAILED)
  }

  @Test
  fun `cancellation path RUNNING to CANCELLED`() {
    val state = AgentStateMachine.transition(AgentState.RUNNING, AgentState.CANCELLED)
    assertThat(state).isEqualTo(AgentState.CANCELLED)
  }

  @Test
  fun `invalid transition IDLE to COMPLETED is rejected`() {
    var rejected = false
    try {
      AgentStateMachine.transition(AgentState.IDLE, AgentState.COMPLETED)
    } catch (_: IllegalArgumentException) {
      rejected = true
    }
    assertThat(rejected).isTrue()
  }

  @Test
  fun `terminal states have no exits`() {
    for (terminal in listOf(AgentState.COMPLETED, AgentState.FAILED, AgentState.CANCELLED)) {
      assertThat(AgentStateMachine.isTerminal(terminal)).isTrue()
      var rejected = false
      try {
        AgentStateMachine.transition(terminal, AgentState.RUNNING)
      } catch (_: IllegalArgumentException) {
        rejected = true
      }
      assertThat(rejected).isTrue()
    }
    assertThat(AgentStateMachine.isTerminal(AgentState.RUNNING)).isFalse()
  }

  // --- Session ---

  @Test
  fun `session creation assigns ids and initial state`() {
    val session = AgentSession(projectRootPath = "/proj", task = "do thing")
    assertThat(session.sessionId).isNotEmpty()
    assertThat(session.taskId).isNotEmpty()
    assertThat(session.sessionId).isNotEqualTo(session.taskId)
    assertThat(session.state).isEqualTo(AgentState.IDLE)
    assertThat(session.iterationCount).isEqualTo(0)
    assertThat(session.cancelled).isFalse()
  }

  @Test
  fun `session tracks state and counters`() {
    val session = AgentSession(projectRootPath = "/proj", task = "t")
    session.moveTo(AgentState.RUNNING)
    session.recordIteration()
    session.recordIteration()
    session.recordToolCall()
    session.recordError()
    assertThat(session.state).isEqualTo(AgentState.RUNNING)
    assertThat(session.iterationCount).isEqualTo(2)
    assertThat(session.toolCallCount).isEqualTo(1)
    assertThat(session.errorCount).isEqualTo(1)
    session.cancel()
    assertThat(session.cancelled).isTrue()
  }

  // --- Config + budget ---

  @Test
  fun `config validated coerces over-limit values to hard maxima`() {
    val coerced = AgentConfig(maxIterations = 9999, maxToolCalls = 9999).validated()
    assertThat(coerced.maxIterations).isEqualTo(AgentConfig.HARD_MAX_ITERATIONS)
    assertThat(coerced.maxToolCalls).isEqualTo(AgentConfig.HARD_MAX_TOOL_CALLS)
    assertThat(AgentConfig().maxIterations).isEqualTo(15)
  }

  @Test
  fun `context budget detects over-allocation`() {
    assertThat(ContextBudget().isValid()).isTrue()
    val bad = ContextBudget(systemBudget = 20_000, historyBudget = 20_000)
    assertThat(bad.isValid()).isFalse()
    assertThat(bad.violations()).isNotEmpty()
    var rejected = false
    try {
      bad.requireValid()
    } catch (_: IllegalArgumentException) {
      rejected = true
    }
    assertThat(rejected).isTrue()
  }

  @Test
  fun `context request requires positive maxChars`() {
    assertThat(ContextRequest("read", 100).maxChars).isEqualTo(100)
    var rejected = false
    try {
      ContextRequest("read", 0)
    } catch (_: IllegalArgumentException) {
      rejected = true
    }
    assertThat(rejected).isTrue()
  }

  // --- Trace ---

  @Test
  fun `trace records and filters events`() {
    val trace = AgentTrace()
    trace.add(TraceEvent(sessionId = "s", type = TraceEventType.SESSION_STARTED))
    trace.add(TraceEvent(sessionId = "s", type = TraceEventType.ERROR))
    assertThat(trace.size()).isEqualTo(2)
    assertThat(trace.ofType(TraceEventType.ERROR)).hasSize(1)
    assertThat(trace.events()).hasSize(2)
  }

  // --- Workspace guard ---

  @Test
  fun `guard allows valid nested path`() {
    val root = tmp.root
    File(root, "a/b").mkdirs()
    val resolved = WorkspaceGuard.resolve(root, "a/b/c.kt")
    assertThat(resolved.path.startsWith(root.canonicalPath)).isTrue()
  }

  @Test
  fun `guard rejects parent traversal`() {
    var rejected = false
    try {
      WorkspaceGuard.resolve(tmp.root, "../outside.txt")
    } catch (_: SecurityException) {
      rejected = true
    }
    assertThat(rejected).isTrue()
    assertThat(WorkspaceGuard.isWithin(tmp.root, "../outside.txt")).isFalse()
  }

  @Test
  fun `guard rejects absolute path outside root`() {
    val outside = File(tmp.root.parentFile, "sibling.txt").absolutePath
    assertThat(WorkspaceGuard.isWithin(tmp.root, outside)).isFalse()
  }
}
