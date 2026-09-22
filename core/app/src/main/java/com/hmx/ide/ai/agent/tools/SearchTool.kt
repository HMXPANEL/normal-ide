package com.hmx.ide.ai.agent.tools

import com.hmx.ide.ai.agent.events.SearchInput
import com.hmx.ide.ai.agent.events.SearchMatch
import com.hmx.ide.ai.agent.events.SearchOutput
import com.hmx.ide.ai.agent.events.ToolFailure
import com.hmx.ide.ai.agent.events.ToolInput
import com.hmx.ide.ai.agent.events.ToolOutput
import com.hmx.ide.ai.agent.permissions.PermissionLevel
import com.hmx.ide.ai.agent.security.WorkspaceGuard
import java.io.File

/**
 * READ-only literal (case-insensitive) content search over the workspace.
 * Bounded BFS mirroring [com.hmx.ide.ai.context.ProjectScanner] skip rules;
 * caps files scanned, matches returned, and per-file bytes to stay safe on
 * low-end devices. No indexing engine is built here by design.
 */
class SearchTool(
  private val projectRoot: File,
  private val maxFilesScanned: Int = DEFAULT_MAX_FILES_SCANNED,
  private val maxMatches: Int = DEFAULT_MAX_MATCHES,
  private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
) : AgentTool {

  override val name: String = NAME
  override val description: String =
    "Search file contents for a literal query inside the project (or pathScope). Returns file/line matches, bounded."
  override val permission: PermissionLevel = PermissionLevel.READ

  override suspend fun execute(input: ToolInput): ToolOutput {
    if (input !is SearchInput) {
      return ToolFailure("search expects a query input")
    }
    if (input.query.isBlank()) return ToolFailure("Query must not be blank")
    val scope = try {
      if (input.pathScope.isNullOrBlank()) {
        projectRoot.canonicalFile
      } else {
        WorkspaceGuard.resolve(projectRoot, input.pathScope)
      }
    } catch (e: SecurityException) {
      return ToolFailure(e.message ?: "Scope rejected", recoverable = false)
    } catch (e: IllegalArgumentException) {
      return ToolFailure(e.message ?: "Invalid scope", recoverable = false)
    }
    if (!scope.exists()) return ToolFailure("Scope does not exist: '${input.pathScope}'")

    val needle = input.query.lowercase()
    val matches = mutableListOf<SearchMatch>()
    var scanned = 0
    var truncated = false

    if (scope.isFile) {
      if (scope.length() <= maxFileBytes) {
        scanned++
        collectMatches(scope, needle, matches)
      }
      return SearchOutput(
        query = input.query,
        matches = matches.take(maxMatches),
        filesScanned = scanned,
        truncated = matches.size >= maxMatches,
      )
    }

    val queue = ArrayDeque<File>()
    val visitedDirs = mutableSetOf<String>()
    queue.add(scope)

    while (queue.isNotEmpty()) {
      if (matches.size >= maxMatches || scanned >= maxFilesScanned) {
        truncated = true
        break
      }
      val dir = queue.removeFirst()
      val canonicalDir = runCatching { dir.canonicalPath }.getOrNull() ?: continue
      if (!visitedDirs.add(canonicalDir)) continue // Symlink cycle guard.
      val children = try {
        dir.listFiles() ?: continue
      } catch (_: SecurityException) {
        continue
      }
      for (child in children) {
        if (child.isDirectory) {
          if (child.name !in SKIP_DIRS) queue.add(child)
        } else if (child.isFile && child.length() <= maxFileBytes) {
          scanned++
          if (scanned > maxFilesScanned) {
            truncated = true
            break
          }
          collectMatches(child, needle, matches)
          if (matches.size >= maxMatches) {
            truncated = true
            break
          }
        }
      }
    }
    return SearchOutput(
      query = input.query,
      matches = matches.take(maxMatches),
      filesScanned = scanned,
      truncated = truncated || matches.size >= maxMatches,
    )
  }

  private fun collectMatches(file: File, needle: String, out: MutableList<SearchMatch>) {
    try {
      file.bufferedReader().useLines { lines ->
        var lineNo = 0
        for (line in lines) {
          lineNo++
          if (out.size >= maxMatches) return
          if (line.lowercase().contains(needle)) {
            val rel = runCatching { file.relativeTo(projectRoot.canonicalFile).path }
              .getOrDefault(file.path)
            out.add(SearchMatch(path = rel, line = lineNo, snippet = line.trim().take(240)))
          }
        }
      }
    } catch (_: Throwable) {
      // Unreadable/binary files are skipped, never fatal.
    }
  }

  companion object {
    const val NAME = "search"
    const val DEFAULT_MAX_FILES_SCANNED = 500
    const val DEFAULT_MAX_MATCHES = 100
    const val DEFAULT_MAX_FILE_BYTES = 512L * 1024L

    private val SKIP_DIRS = setOf(
      "build", ".git", ".gradle", ".idea", "bin", "obj",
      "node_modules", ".hmx", "cmake-build-debug",
    )
  }
}
