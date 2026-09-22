package com.hmx.ide.ai.agent.permissions

import com.hmx.ide.ai.agent.events.ToolInput
import com.hmx.ide.ai.agent.tools.AgentTool

/**
 * Minimal permission tiers. Grows in later phases (WRITE, DELETE,
 * GIT_COMMIT, GIT_PUSH, DEVICE_INSTALL, RELEASE_BUILD) without changing
 * the check call-sites.
 */
enum class PermissionLevel {
  READ,
  WRITE,
  DESTRUCTIVE,
  EXTERNAL,
}

sealed interface PermissionDecision {
  data object Allow : PermissionDecision
  data class Deny(val reason: String) : PermissionDecision
}

/** Pure policy: no I/O, no UI. UI approval flows wrap this in later phases. */
interface PermissionPolicy {
  fun decide(tool: AgentTool, input: ToolInput): PermissionDecision
}

/**
 * Phase 1 default: anything the tool declares as READ runs; everything else
 * is denied. This is intentionally conservative — future destructive tools
 * fail closed until an approval policy exists.
 */
object AllowReadPolicy : PermissionPolicy {
  override fun decide(tool: AgentTool, input: ToolInput): PermissionDecision =
    if (tool.permission == PermissionLevel.READ) {
      PermissionDecision.Allow
    } else {
      PermissionDecision.Deny(
        "Tool '${tool.name}' requires ${tool.permission}; " +
          "Phase 1 policy only allows READ tools.",
      )
    }
}

/** Thin wrapper so the executor stays decoupled from policy instances. */
class PermissionManager(
  val policy: PermissionPolicy = AllowReadPolicy,
) {
  fun check(tool: AgentTool, input: ToolInput): PermissionDecision =
    policy.decide(tool, input)
}
