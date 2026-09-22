package com.hmx.ide.ai.agent.core

import com.hmx.ide.ai.agent.events.Action
import com.hmx.ide.ai.agent.events.Observation
import com.hmx.ide.ai.agent.events.PermissionDenied
import com.hmx.ide.ai.agent.events.ToolFailure
import com.hmx.ide.ai.agent.events.newEventId
import com.hmx.ide.ai.agent.tools.ToolExecutor
import com.hmx.ide.ai.agent.tools.ToolRegistry
import com.hmx.ide.ai.agent.trace.AgentTrace
import com.hmx.ide.ai.agent.trace.TraceEvent
import com.hmx.ide.ai.agent.trace.TraceEventType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Foundational single-agent loop:
 * request -> state -> model decision -> action -> tool -> observation ->
 * state update -> next iteration.
 *
 * No automatic code writing happens here: Phase 1 tools are READ-only and
 * the loop terminates on Finish, budget exhaustion, denial, failure, or
 * structured cancellation. Runs on the caller's coroutine (never main).
 */
class AgentLoop(
  private val model: AgentModel,
  private val registry: ToolRegistry,
  private val executorFactory: (AgentTrace, String) -> ToolExecutor,
  private val config: AgentConfig = AgentConfig(),
  private val trace: AgentTrace = AgentTrace(),
) {

  suspend fun run(task: String, projectRoot: File): AgentResult {
    val effective = config.validated()
    val session = AgentSession(projectRootPath = projectRoot.absolutePath, task = task)
    trace.add(
      TraceEvent(
        sessionId = session.sessionId,
        type = TraceEventType.SESSION_STARTED,
        metadata = mapOf("taskChars" to task.length.toString()),
      ),
    )
    val executor = executorFactory(trace, session.sessionId)
    val history = mutableListOf<AgentTurn>()

    try {
      session.moveTo(AgentState.RUNNING)
      emitState(session)

      while (true) {
        coroutineContext.ensureActive()
        if (session.cancelled) {
          return cancelled(session, "cancel() called")
        }
        if (session.iterationCount >= effective.maxIterations) {
          return limited(session, "maxIterations=${effective.maxIterations} reached")
        }

        session.recordIteration()
        val decision = try {
          model.decide(history.toList(), registry.descriptors())
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (t: Throwable) {
          session.recordError()
          return failed(session, "Model decision failed: ${t.message}", t)
        }

        when (decision) {
          is ModelDecision.Finish -> {
            return completed(session, decision.summary)
          }
          is ModelDecision.CallTool -> {
            if (session.toolCallCount >= effective.maxToolCalls) {
              return limited(session, "maxToolCalls=${effective.maxToolCalls} reached")
            }
            val action = Action(
              id = newEventId(),
              toolName = decision.toolName,
              input = decision.input,
            )
            trace.add(
              TraceEvent(
                sessionId = session.sessionId,
                type = TraceEventType.ACTION_CREATED,
                actionId = action.id,
                toolName = action.toolName,
                metadata = mapOf("input" to action.input.describe().take(240)),
              ),
            )

            session.moveTo(AgentState.WAITING_FOR_PERMISSION)
            emitState(session)
            session.moveTo(AgentState.WAITING_FOR_TOOL)
            emitState(session)

            val observation = try {
              executor.run(action)
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (t: Throwable) {
              session.recordError()
              return failed(session, "Tool executor failed: ${t.message}", t)
            } finally {
              if (!AgentStateMachine.isTerminal(session.state)) {
                session.moveTo(AgentState.RUNNING)
                emitState(session)
              }
            }

            session.recordToolCall()
            history.add(AgentTurn(action, observation))

            when (val out = observation.output) {
              is PermissionDenied -> {
                return failed(
                  session,
                  "Permission denied for '${out.toolName}': ${out.reason}",
                )
              }
              is ToolFailure -> {
                session.recordError()
                // Recoverable failures are fed back to the model next turn.
              }
              else -> Unit
            }
          }
        }
      }
    } catch (cancelled: CancellationException) {
      return cancelled(session, cancelled.message ?: "coroutine cancelled")
    } finally {
      if (!AgentStateMachine.isTerminal(session.state)) {
        runCatching { session.moveTo(AgentState.CANCELLED) }
      }
    }
  }

  private fun emitState(session: AgentSession) {
    trace.add(
      TraceEvent(
        sessionId = session.sessionId,
        type = TraceEventType.STATE_CHANGED,
        status = session.state.name,
        metadata = mapOf(
          "iteration" to session.iterationCount.toString(),
          "toolCalls" to session.toolCallCount.toString(),
        ),
      ),
    )
  }

  private fun completed(session: AgentSession, summary: String): AgentResult {
    session.moveTo(AgentState.COMPLETED)
    emitState(session)
    trace.add(
      TraceEvent(sessionId = session.sessionId, type = TraceEventType.SESSION_COMPLETED),
    )
    return AgentResult.Completed(
      sessionId = session.sessionId,
      iterations = session.iterationCount,
      toolCalls = session.toolCallCount,
      summary = summary,
    )
  }

  private fun limited(session: AgentSession, reason: String): AgentResult {
    session.moveTo(AgentState.COMPLETED)
    emitState(session)
    trace.add(
      TraceEvent(
        sessionId = session.sessionId,
        type = TraceEventType.SESSION_COMPLETED,
        status = "LIMIT",
        metadata = mapOf("reason" to reason.take(240)),
      ),
    )
    return AgentResult.CompletedWithLimit(
      sessionId = session.sessionId,
      iterations = session.iterationCount,
      toolCalls = session.toolCallCount,
      summary = reason,
    )
  }

  private fun failed(session: AgentSession, reason: String, cause: Throwable? = null): AgentResult {
    session.moveTo(AgentState.FAILED)
    emitState(session)
    trace.add(
      TraceEvent(
        sessionId = session.sessionId,
        type = TraceEventType.ERROR,
        status = "FAILED",
        metadata = mapOf("reason" to reason.take(240)),
      ),
    )
    return AgentResult.Failed(
      sessionId = session.sessionId,
      iterations = session.iterationCount,
      toolCalls = session.toolCallCount,
      reason = reason,
      cause = cause,
    )
  }

  private fun cancelled(session: AgentSession, reason: String): AgentResult {
    runCatching { session.moveTo(AgentState.CANCELLED) }
    trace.add(
      TraceEvent(
        sessionId = session.sessionId,
        type = TraceEventType.SESSION_CANCELLED,
        metadata = mapOf("reason" to reason.take(240)),
      ),
    )
    return AgentResult.Cancelled(
      sessionId = session.sessionId,
      iterations = session.iterationCount,
      toolCalls = session.toolCallCount,
    )
  }
}

/** Convenience: last observation or null when no tool has run yet. */
fun List<AgentTurn>.lastObservation(): Observation? = lastOrNull()?.observation
