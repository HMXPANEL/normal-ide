package com.hmx.ide.ai.agent.tools

import com.hmx.ide.ai.agent.events.ToolInput
import com.hmx.ide.ai.agent.events.ToolOutput
import com.hmx.ide.ai.agent.permissions.PermissionLevel

/** Stable, UI-independent tool metadata for future model adapters. */
data class ToolDescriptor(
  val name: String,
  val description: String,
  val permission: PermissionLevel,
)

/**
 * Generic typed tool. Implementations must be free of Activity/UI references
 * and safe to call from a background coroutine.
 */
interface AgentTool {
  val name: String
  val description: String
  val permission: PermissionLevel

  suspend fun execute(input: ToolInput): ToolOutput

  fun descriptor(): ToolDescriptor =
    ToolDescriptor(name = name, description = description, permission = permission)
}

/** Provider-independent registry. Tools are instances bound at session setup. */
class ToolRegistry {
  private val tools = LinkedHashMap<String, AgentTool>()

  @Synchronized
  fun register(tool: AgentTool) {
    require(!tools.containsKey(tool.name)) {
      "Tool already registered: '${tool.name}'"
    }
    require(tool.name.isNotBlank()) { "Tool name must not be blank" }
    tools[tool.name] = tool
  }

  @Synchronized
  fun unregister(name: String): Boolean = tools.remove(name) != null

  @Synchronized
  fun find(name: String): AgentTool? = tools[name]

  @Synchronized
  fun require(name: String): AgentTool =
    find(name) ?: throw NoSuchElementException("No tool registered as '$name'")

  @Synchronized
  fun names(): List<String> = tools.keys.toList()

  @Synchronized
  fun descriptors(): List<ToolDescriptor> = tools.values.map { it.descriptor() }
}
