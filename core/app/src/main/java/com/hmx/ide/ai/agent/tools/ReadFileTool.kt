package com.hmx.ide.ai.agent.tools

import com.hmx.ide.ai.agent.events.FileReadOutput
import com.hmx.ide.ai.agent.events.ReadFileInput
import com.hmx.ide.ai.agent.events.ToolFailure
import com.hmx.ide.ai.agent.events.ToolInput
import com.hmx.ide.ai.agent.events.ToolOutput
import com.hmx.ide.ai.agent.permissions.PermissionLevel
import com.hmx.ide.ai.agent.security.WorkspaceGuard
import java.io.File

/**
 * READ-only bounded file reader. Rejects directories, binaries (NUL-byte
 * sniff), and over-limit files instead of loading them fully.
 */
class ReadFileTool(
  private val projectRoot: File,
  private val maxChars: Int = DEFAULT_MAX_CHARS,
  private val maxLines: Int = DEFAULT_MAX_LINES,
) : AgentTool {

  override val name: String = NAME
  override val description: String =
    "Read a text file inside the project workspace. Returns truncated content with a flag when the file exceeds limits."
  override val permission: PermissionLevel = PermissionLevel.READ

  override suspend fun execute(input: ToolInput): ToolOutput {
    if (input !is ReadFileInput) {
      return ToolFailure("read_file expects a file path input")
    }
    val file = try {
      WorkspaceGuard.resolve(projectRoot, input.path)
    } catch (e: SecurityException) {
      return ToolFailure(e.message ?: "Path rejected", recoverable = false)
    } catch (e: IllegalArgumentException) {
      return ToolFailure(e.message ?: "Invalid path", recoverable = false)
    }
    if (!file.exists()) return ToolFailure("File not found: '${input.path}'")
    if (!file.isFile) return ToolFailure("Not a file: '${input.path}'")
    if (looksBinary(file)) {
      return ToolFailure("Refusing to read binary file: '${input.path}'", recoverable = false)
    }
    return try {
      readBounded(file, input.path)
    } catch (t: Throwable) {
      ToolFailure("Cannot read '${input.path}': ${t.message}")
    }
  }

  private fun readBounded(file: File, requested: String): FileReadOutput {
    val sb = StringBuilder()
    var lines = 0
    var truncated = false
    file.bufferedReader().use { reader ->
      while (true) {
        val line = reader.readLine() ?: break
        lines++
        if (lines > maxLines || sb.length + line.length + 1 > maxChars) {
          truncated = true
          break
        }
        sb.appendLine(line)
      }
    }
    return FileReadOutput(
      path = requested,
      content = sb.toString(),
      truncated = truncated,
      totalChars = file.length().coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    )
  }

  private fun looksBinary(file: File): Boolean {
    val sniff = ByteArray(MAX_SNIFF_BYTES)
    val read = try {
      file.inputStream().use { it.read(sniff) }
    } catch (_: Throwable) {
      return true
    }
    if (read <= 0) return false
    for (i in 0 until read) {
      if (sniff[i] == 0.toByte()) return true
    }
    return false
  }

  companion object {
    const val NAME = "read_file"
    const val DEFAULT_MAX_CHARS = 8_192
    const val DEFAULT_MAX_LINES = 500
    private const val MAX_SNIFF_BYTES = 4_096
  }
}
