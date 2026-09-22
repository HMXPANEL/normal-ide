package com.hmx.ide.ai.agent.core

import com.hmx.ide.ai.agent.context.ContextBudget
import com.hmx.ide.ai.agent.permissions.AllowReadPolicy
import com.hmx.ide.ai.agent.permissions.PermissionPolicy

/**
 * Single place for all runtime bounds. Use [validated] before running a loop
 * so oversized values are coerced to safe limits instead of failing mid-run.
 */
data class AgentConfig(
  val maxIterations: Int = DEFAULT_MAX_ITERATIONS,
  val maxToolCalls: Int = DEFAULT_MAX_TOOL_CALLS,
  val contextBudget: ContextBudget = ContextBudget(),
  val permissionPolicy: PermissionPolicy = AllowReadPolicy,
  val traceEnabled: Boolean = true,
) {
  fun validated(): AgentConfig = copy(
    maxIterations = maxIterations.coerceIn(1, HARD_MAX_ITERATIONS),
    maxToolCalls = maxToolCalls.coerceIn(1, HARD_MAX_TOOL_CALLS),
    contextBudget = contextBudget.validated(),
  )

  companion object {
    const val DEFAULT_MAX_ITERATIONS = 15
    const val HARD_MAX_ITERATIONS = 30
    const val DEFAULT_MAX_TOOL_CALLS = 30
    const val HARD_MAX_TOOL_CALLS = 100
  }
}
