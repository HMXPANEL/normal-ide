/*
 *  This file is part of HMX IDE.
 *
 *  HMX IDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  HMX IDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with HMX IDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.hmx.ide.activities.aichat

import java.io.File

data class ChatMessage(
  val role: String, // "user" | "assistant" | "system"
  val content: String
)

/**
 * Provides read-only access to the currently opened project so the AI Chat
 * always knows which project is open, can list its structure and read files.
 */
object ProjectContext {

  /** Relative path -> absolute file, of source/config files (build/.git excluded). */
  fun listFiles(root: File): List<File> {
    val result = mutableListOf<File>()
    val queue = ArrayDeque<File>().apply { add(root) }
    var visited = 0
    while (queue.isNotEmpty() && visited < 2000) {
      val dir = queue.removeFirst()
      visited++
      val children = dir.listFiles() ?: continue
      for (child in children) {
        val name = child.name
        if (name == "build" || name == ".git" || name == ".gradle" || name == "bin" || name == "obj") {
          continue
        }
        if (child.isDirectory) {
          queue.add(child)
        } else if (isRelevant(child)) {
          result.add(child)
        }
      }
    }
    return result
  }

  private fun isRelevant(file: File): Boolean {
    val ext = file.extension.lowercase()
    return ext in setOf(
      "kt", "kts", "java", "xml", "gradle", "json", "md", "txt",
      "pro", "properties", "yaml", "yml", "toml", "html", "css", "js", "ts"
    ) && file.length() < 512 * 1024
  }

  fun readFile(file: File): String? {
    return runCatching { file.readText() }.getOrNull()
  }

  /** A compact text representation of the project tree. */
  fun describe(root: File): String {
    val sb = StringBuilder()
    sb.append("Project: ").append(root.name).append("\n")
    sb.append("Path: ").append(root.absolutePath).append("\n\n")
    sb.append("Files:\n")
    listFiles(root).forEach {
      sb.append("- ").append(it.relativeTo(root).path).append("\n")
    }
    return sb.toString()
  }
}
