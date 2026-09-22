package com.hmx.ide.ai.agent.core

import java.util.UUID

/**
 * Mutable holder for a single agent run. Owned by exactly one [AgentLoop]
 * coroutine at a time; no internal synchronization is provided by design so
 * misuse across threads fails obviously instead of corrupting silently.
 *
 * Never stores repository contents or large file bodies — only counters,
 * state, and small metadata.
 */
class AgentSession(
  val sessionId: String = UUID.randomUUID().toString(),
  val taskId: String = UUID.randomUUID().toString(),
  val projectRootPath: String,
  val task: String,
  val startTimeMs: Long = System.currentTimeMillis(),
  val metadata: Map<String, String> = emptyMap(),
) {
  var state: AgentState = AgentState.IDLE
    private set

  var iterationCount: Int = 0
    private set

  var toolCallCount: Int = 0
    private set

  var errorCount: Int = 0
    private set

  @Volatile
  var cancelled: Boolean = false
    private set

  fun moveTo(next: AgentState) {
    state = AgentStateMachine.transition(state, next)
  }

  fun recordIteration() {
    iterationCount++
  }

  fun recordToolCall() {
    toolCallCount++
  }

  fun recordError() {
    errorCount++
  }

  fun cancel() {
    cancelled = true
  }
}
