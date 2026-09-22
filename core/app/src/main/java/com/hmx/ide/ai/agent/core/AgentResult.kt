package com.hmx.ide.ai.agent.core

/**
 * Terminal outcome of [AgentLoop.run]. Uses the repository's preferred
 * sealed-result style (mirrors `AiException` hierarchy conventions).
 */
sealed class AgentResult {
  abstract val sessionId: String
  abstract val iterations: Int
  abstract val toolCalls: Int

  data class Completed(
    override val sessionId: String,
    override val iterations: Int,
    override val toolCalls: Int,
    val summary: String,
  ) : AgentResult()

  /** Iteration or tool-call budget exhausted before the model finished. */
  data class CompletedWithLimit(
    override val sessionId: String,
    override val iterations: Int,
    override val toolCalls: Int,
    val summary: String,
  ) : AgentResult()

  data class Failed(
    override val sessionId: String,
    override val iterations: Int,
    override val toolCalls: Int,
    val reason: String,
    val cause: Throwable? = null,
  ) : AgentResult()

  data class Cancelled(
    override val sessionId: String,
    override val iterations: Int,
    override val toolCalls: Int,
  ) : AgentResult()
}
