package com.hmx.ide.ai.agent.events

import com.hmx.ide.ai.agent.permissions.PermissionLevel
import java.io.Serializable
import java.util.UUID

/** Stable identifiers so actions and observations correlate across trace and tests. */
fun newEventId(): String = UUID.randomUUID().toString()

/**
 * Tool input envelope. Each tool declares its own input type; the loop and
 * any future model adapter only handle this sealed type, never tool internals.
 */
sealed interface ToolInput : Serializable {
  fun describe(): String
}

data class ReadFileInput(val path: String) : ToolInput {
  override fun describe(): String = "read_file(path=$path)"
}

data class SearchInput(
  val query: String,
  val pathScope: String? = null,
) : ToolInput {
  override fun describe(): String = "search(query=$query, scope=${pathScope ?: "/"})"
}

data class FindSymbolInput(val symbol: String) : ToolInput {
  override fun describe(): String = "find_symbol(symbol=$symbol)"
}

/**
 * A single decision by the model (or test harness) to invoke a tool.
 * Created by the agent, executed by [com.hmx.ide.ai.agent.tools.ToolExecutor].
 */
data class Action(
  val id: String = newEventId(),
  val toolName: String,
  val input: ToolInput,
  val createdAtMs: Long = System.currentTimeMillis(),
) : Serializable

/**
 * Typed result of executing one [Action]. Recoverable tool errors are
 * observations (fed back to the model); only loop-level failures throw.
 */
sealed interface ToolOutput : Serializable

data class FileReadOutput(
  val path: String,
  val content: String,
  val truncated: Boolean,
  val totalChars: Int,
) : ToolOutput

data class SearchMatch(
  val path: String,
  val line: Int,
  val snippet: String,
) : Serializable

data class SearchOutput(
  val query: String,
  val matches: List<SearchMatch>,
  val filesScanned: Int,
  val truncated: Boolean,
) : ToolOutput

data class SymbolHit(
  val symbol: String,
  val filePath: String,
  val line: Int,
  val column: Int,
) : Serializable

data class SymbolOutput(
  val symbol: String,
  val hits: List<SymbolHit>,
) : ToolOutput

/** A tool ran but could not produce a result (missing file, denied, I/O error). */
data class ToolFailure(
  val reason: String,
  val recoverable: Boolean = true,
) : ToolOutput

/** Permission layer refused the call before execution. Never retried silently. */
data class PermissionDenied(
  val toolName: String,
  val required: PermissionLevel,
  val reason: String,
) : ToolOutput

/**
 * Recorded outcome of one [Action], correlated via [actionId].
 */
data class Observation(
  val id: String = newEventId(),
  val actionId: String,
  val toolName: String,
  val output: ToolOutput,
  val durationMs: Long = 0L,
  val createdAtMs: Long = System.currentTimeMillis(),
) : Serializable
