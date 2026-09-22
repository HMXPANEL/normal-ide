package com.hmx.ide.ai.agent.core

import com.hmx.ide.ai.agent.events.Observation
import com.hmx.ide.ai.agent.events.ToolInput
import com.hmx.ide.ai.agent.tools.ToolDescriptor

/**
 * Model side of the Model -> ToolCall -> Registry -> Executor ->
 * Observation -> Model contract.
 *
 * Deliberately narrow: the existing `AiProvider` architecture remains the
 * model layer. A provider-backed implementation (function-calling adapter)
 * is a Phase 2+ concern; Phase 1 proves the loop with deterministic models.
 */
interface AgentModel {
  suspend fun decide(history: List<AgentTurn>, tools: List<ToolDescriptor>): ModelDecision
}

/** One completed Action/Observation round, fed back to the model. */
data class AgentTurn(
  val action: com.hmx.ide.ai.agent.events.Action,
  val observation: Observation,
)

sealed interface ModelDecision {
  data class CallTool(val toolName: String, val input: ToolInput) : ModelDecision
  data class Finish(val summary: String) : ModelDecision
}
