package com.hmx.ide.ai.agent.core

/**
 * Strongly typed lifecycle state for a single agent run.
 *
 * All transitions must go through [AgentStateMachine.transition]; arbitrary
 * string states are not representable by design.
 */
enum class AgentState {
  IDLE,
  RUNNING,
  WAITING_FOR_PERMISSION,
  WAITING_FOR_TOOL,
  PAUSED,
  COMPLETED,
  FAILED,
  CANCELLED,
}

/**
 * Enforces the legal transition graph. Throws [IllegalArgumentException] on
 * invalid transitions so bad wiring fails loudly in tests and logs.
 */
object AgentStateMachine {

  private val allowed: Map<AgentState, Set<AgentState>> = mapOf(
    AgentState.IDLE to setOf(AgentState.RUNNING, AgentState.CANCELLED),
    AgentState.RUNNING to setOf(
      AgentState.WAITING_FOR_PERMISSION,
      AgentState.WAITING_FOR_TOOL,
      AgentState.PAUSED,
      AgentState.COMPLETED,
      AgentState.FAILED,
      AgentState.CANCELLED,
    ),
    AgentState.WAITING_FOR_PERMISSION to setOf(
      AgentState.WAITING_FOR_TOOL,
      AgentState.RUNNING,
      AgentState.FAILED,
      AgentState.CANCELLED,
    ),
    AgentState.WAITING_FOR_TOOL to setOf(
      AgentState.RUNNING,
      AgentState.FAILED,
      AgentState.CANCELLED,
    ),
    AgentState.PAUSED to setOf(AgentState.RUNNING, AgentState.CANCELLED),
    AgentState.COMPLETED to emptySet(),
    AgentState.FAILED to emptySet(),
    AgentState.CANCELLED to emptySet(),
  )

  fun transition(from: AgentState, to: AgentState): AgentState {
    require(to in (allowed[from].orEmpty())) {
      "Invalid agent transition: $from -> $to"
    }
    return to
  }

  fun isTerminal(state: AgentState): Boolean =
    state == AgentState.COMPLETED ||
      state == AgentState.FAILED ||
      state == AgentState.CANCELLED
}
