package com.hmx.ide.ai.agent.trace

/**
 * Every observable transition inside one agent run. In-memory only in
 * Phase 1; the field set is deliberately persistence-ready (primitives +
 * strings) so a later SQLite store maps 1:1 without reshaping events.
 */
enum class TraceEventType {
  SESSION_STARTED,
  STATE_CHANGED,
  ACTION_CREATED,
  TOOL_STARTED,
  TOOL_COMPLETED,
  OBSERVATION_CREATED,
  ERROR,
  PERMISSION_REQUESTED,
  SESSION_COMPLETED,
  SESSION_CANCELLED,
}

data class TraceEvent(
  val timestampMs: Long = System.currentTimeMillis(),
  val sessionId: String,
  val type: TraceEventType,
  val actionId: String? = null,
  val toolName: String? = null,
  val status: String? = null,
  val metadata: Map<String, String> = emptyMap(),
)

/** Append-only in-memory recorder. Synchronous: loop is single-owner. */
class AgentTrace {
  private val events = mutableListOf<TraceEvent>()

  @Synchronized
  fun add(event: TraceEvent) {
    events.add(event)
  }

  @Synchronized
  fun events(): List<TraceEvent> = events.toList()

  @Synchronized
  fun ofType(type: TraceEventType): List<TraceEvent> =
    events.filter { it.type == type }

  @Synchronized
  fun clear() {
    events.clear()
  }

  @Synchronized
  fun size(): Int = events.size
}
