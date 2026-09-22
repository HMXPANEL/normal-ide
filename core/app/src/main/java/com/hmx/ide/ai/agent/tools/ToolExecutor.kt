package com.hmx.ide.ai.agent.tools

import com.hmx.ide.ai.agent.events.Action
import com.hmx.ide.ai.agent.events.Observation
import com.hmx.ide.ai.agent.events.PermissionDenied
import com.hmx.ide.ai.agent.events.ToolFailure
import com.hmx.ide.ai.agent.events.ToolOutput
import com.hmx.ide.ai.agent.permissions.PermissionDecision
import com.hmx.ide.ai.agent.permissions.PermissionManager
import com.hmx.ide.ai.agent.trace.AgentTrace
import com.hmx.ide.ai.agent.trace.TraceEvent
import com.hmx.ide.ai.agent.trace.TraceEventType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Pipeline: validate -> permission check -> execute (IO) -> observation ->
 * trace. Cancellation propagates; [CancellationException] is never swallowed
 * into a [ToolFailure]. Never touches the main thread.
 */
class ToolExecutor(
  private val registry: ToolRegistry,
  private val permissions: PermissionManager = PermissionManager(),
  private val trace: AgentTrace? = null,
  private val sessionId: String = "",
) {

  suspend fun run(action: Action): Observation {
    coroutineContext.ensureActive()
    val startedAt = System.currentTimeMillis()
    trace?.add(
      TraceEvent(
        sessionId = sessionId,
        type = TraceEventType.TOOL_STARTED,
        actionId = action.id,
        toolName = action.toolName,
      ),
    )

    val tool = registry.find(action.toolName)
    if (tool == null) {
      return finish(action, startedAt, ToolFailure("Unknown tool '${action.toolName}'"))
    }

    trace?.add(
      TraceEvent(
        sessionId = sessionId,
        type = TraceEventType.PERMISSION_REQUESTED,
        actionId = action.id,
        toolName = tool.name,
        metadata = mapOf("permission" to tool.permission.name),
      ),
    )
    when (val decision = permissions.check(tool, action.input)) {
      is PermissionDecision.Deny -> {
        return finish(
          action,
          startedAt,
          PermissionDenied(
            toolName = tool.name,
            required = tool.permission,
            reason = decision.reason,
          ),
        )
      }
      is PermissionDecision.Allow -> Unit
    }

    val output = try {
      withContext(Dispatchers.IO) {
        tool.execute(action.input)
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (security: SecurityException) {
      ToolFailure("Denied by workspace guard: ${security.message}", recoverable = false)
    } catch (t: Throwable) {
      ToolFailure("Tool '${tool.name}' failed: ${t.message ?: t.javaClass.simpleName}")
    }
    return finish(action, startedAt, output)
  }

  private fun finish(action: Action, startedAt: Long, output: ToolOutput): Observation {
    val observation = Observation(
      actionId = action.id,
      toolName = action.toolName,
      output = output,
      durationMs = System.currentTimeMillis() - startedAt,
    )
    trace?.add(
      TraceEvent(
        sessionId = sessionId,
        type = TraceEventType.TOOL_COMPLETED,
        actionId = action.id,
        toolName = action.toolName,
        status = output.javaClass.simpleName,
        metadata = mapOf("durationMs" to observation.durationMs.toString()),
      ),
    )
    trace?.add(
      TraceEvent(
        sessionId = sessionId,
        type = TraceEventType.OBSERVATION_CREATED,
        actionId = action.id,
        toolName = action.toolName,
      ),
    )
    return observation
  }
}
