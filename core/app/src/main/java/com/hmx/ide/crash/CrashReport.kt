/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.hmx.ide.crash

import android.os.Build
import com.blankj.utilcode.util.ThrowableUtils
import com.hmx.ide.BuildConfig
import com.hmx.ide.buildinfo.BuildInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds an in-memory, sanitized crash report for HMX IDE's own process.
 *
 * The report is never written to disk, shared preferences, a database or sent over the network.
 * It only captures what is needed to diagnose a crash of the IDE itself (not user projects):
 * versions, build metadata, device, thread and the full (caused-by aware) stack trace.
 * Any secrets that happen to appear in the trace are redacted before the report leaves the handler.
 */
object CrashReport {

  // ponytail: redaction is a naive regex scan; good enough to keep credentials out of the
  // copy-to-clipboard report. If a structured secret store is added later, filter at the source.
  private val SECRET_PATTERNS = listOf(
    Regex("((?i)api[_-]?key\\s*[=:]\\s*)\\S+"),
    Regex("((?i)apikey\\s*[=:]\\s*)\\S+"),
    Regex("((?i)access[_-]?token\\s*[=:]\\s*)\\S+"),
    Regex("((?i)token\\s*[=:]\\s*)\\S+"),
    Regex("((?i)password\\s*[=:]\\s*)\\S+"),
    Regex("((?i)secret\\s*[=:]\\s*)\\S+"),
    Regex("((?i)github[_-]?token\\s*[=:]\\s*)\\S+"),
    Regex("((?i)authorization\\s*:\\s*Bearer\\s+)\\S+"),
    Regex("((?i)Bearer\\s+)\\S+"),
  )

  fun build(thread: Thread, throwable: Throwable): String {
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    val raw = buildString {
      appendLine("HMX IDE Crash Report")
      appendLine("=====================")
      appendLine("Time        : $timestamp")
      appendLine("App Version : v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
      appendLine("Build Type  : ${BuildConfig.BUILD_TYPE}")
      appendLine("Package     : ${BuildConfig.APPLICATION_ID}")
      appendLine("Process     : ${currentProcessName()}")
      appendLine("ABIs        : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
      appendLine("CI Build    : ${BuildInfo.CI_BUILD}")
      appendLine("Branch      : ${BuildInfo.CI_GIT_BRANCH}")
      appendLine("Commit      : ${BuildInfo.CI_GIT_COMMIT_HASH}")
      appendLine("Android     : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
      appendLine("Device      : ${Build.MANUFACTURER} ${Build.MODEL}")
      appendLine("Thread      : ${thread.name} (id=${thread.id})")
      appendLine("Exception   : ${throwable.javaClass.name}: ${throwable.message ?: ""}")
      appendLine()
      appendLine("Stacktrace:")
      appendLine(ThrowableUtils.getFullStackTrace(throwable))
    }
    return sanitize(raw)
  }

  /**
   * Compact summary shown on the crash card. Structured so the card can show reason,
   * message, best-effort source location, thread, build type and version.
   */
  fun summary(thread: Thread, throwable: Throwable): String {
    val frame = throwable.stackTrace.firstOrNull()
    val location = frame?.let { "${it.fileName}:${it.lineNumber}" } ?: "unknown"
    return buildString {
      appendLine("Crash reason:")
      appendLine(throwable.javaClass.name)
      appendLine("Message:")
      appendLine(throwable.message ?: "")
      appendLine("Location:")
      appendLine(location)
      appendLine("Thread:")
      appendLine(thread.name)
      appendLine("Build:")
      appendLine(BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercase() })
      appendLine("Version:")
      appendLine("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }
  }

  private fun currentProcessName(): String = try {
    java.io.BufferedReader(java.io.FileReader("/proc/self/cmdline")).use { reader ->
      reader.readText().replace('\u0000', ' ').trim()
    }
  } catch (_: Throwable) {
    "unknown"
  }

  private fun sanitize(text: String): String {
    var out = text
    for (pattern in SECRET_PATTERNS) {
      out = pattern.replace(out) { m -> m.groupValues[1] + "<REDACTED>" }
    }
    return out
  }
}
