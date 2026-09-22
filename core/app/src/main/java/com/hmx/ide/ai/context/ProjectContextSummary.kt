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

package com.hmx.ide.ai.context

import java.io.File

/**
 * Indexing state of the project context.
 */
enum class IndexingState {
  /** Index data is available and current. */
  READY,

  /** A scan is currently running; [ProjectContextSummary.progress] may be set. */
  INDEXING,

  /** Index data exists but a refresh is running in the background. */
  UPDATING,

  /** No index data is available for the project. */
  UNAVAILABLE,
}

/**
 * A read-only, display-oriented view of already-indexed project information.
 *
 * This class performs **no scanning, no file IO and no analysis**. It is a projection over the
 * [ProjectIndex] that [ContextCache] / `KnowledgeEngineImpl` have already produced, intended for
 * lightweight UI surfaces (chat header, context indicator, startup message).
 *
 * Fields that cannot be derived from the existing index are left `null` and rendered as
 * `Unknown` by [displayOrUnknown] rather than being invented.
 */
data class ProjectContextSummary(
  val projectDir: String,
  val projectName: String,
  val language: String?,
  val uiFramework: String?,
  val packageName: String?,
  val architecture: String?,
  val buildSystem: String?,
  val moduleCount: Int,
  val totalFiles: Int,
  val lastIndexedAt: Long,
  val state: IndexingState,
  /** Scan progress in the range `0f..1f`, or `null` when not reported. */
  val progress: Float? = null,
) {

  /** `true` when an index is present and usable as AI context. */
  val hasContext: Boolean
    get() = state == IndexingState.READY || state == IndexingState.UPDATING

  companion object {

    private const val UNKNOWN = "Unknown"

    @JvmStatic
    fun displayOrUnknown(value: String?): String = value?.takeIf { it.isNotBlank() } ?: UNKNOWN

    /**
     * Builds a summary from an already-computed [ProjectIndex]. No IO is performed.
     */
    @JvmStatic
    fun from(
      index: ProjectIndex,
      state: IndexingState = IndexingState.READY,
      progress: Float? = null,
    ): ProjectContextSummary {
      val ctx = index.context
      return ProjectContextSummary(
        projectDir = ctx.projectDir,
        projectName = File(ctx.projectDir).name.ifBlank { UNKNOWN },
        language = ctx.language.takeIf { it != Language.UNKNOWN }?.let(::languageLabel),
        uiFramework = ctx.ui.takeIf { it != UIFramework.UNKNOWN }?.let(::uiLabel),
        packageName = ctx.packageName?.takeIf { it.isNotBlank() },
        architecture = ctx.architecture.takeIf { it != Architecture.UNKNOWN }
          ?.let(::architectureLabel),
        buildSystem = ctx.buildSystem.takeIf { it != BuildSystem.UNKNOWN }
          ?.let(::buildSystemLabel),
        moduleCount = ctx.modules.size,
        totalFiles = index.totalSourceFiles,
        lastIndexedAt = ctx.lastAnalyzed,
        state = state,
        progress = progress,
      )
    }

    /**
     * Builds a placeholder summary for a project whose index is not available yet. Used while a
     * scan is in flight or when no project has been indexed.
     */
    @JvmStatic
    fun unavailable(
      projectDir: String,
      state: IndexingState = IndexingState.UNAVAILABLE,
      progress: Float? = null,
    ): ProjectContextSummary = ProjectContextSummary(
      projectDir = projectDir,
      projectName = File(projectDir).name.ifBlank { UNKNOWN },
      language = null,
      uiFramework = null,
      packageName = null,
      architecture = null,
      buildSystem = null,
      moduleCount = 0,
      totalFiles = 0,
      lastIndexedAt = 0L,
      state = state,
      progress = progress,
    )

    private fun languageLabel(value: Language): String = when (value) {
      Language.JAVA -> "Java"
      Language.KOTLIN -> "Kotlin"
      Language.MIXED -> "Java + Kotlin"
      Language.UNKNOWN -> UNKNOWN
    }

    private fun uiLabel(value: UIFramework): String = when (value) {
      UIFramework.XML -> "XML"
      UIFramework.COMPOSE -> "Compose"
      UIFramework.MIXED -> "XML + Compose"
      UIFramework.UNKNOWN -> UNKNOWN
    }

    private fun architectureLabel(value: Architecture): String = when (value) {
      Architecture.MVVM -> "MVVM"
      Architecture.MVP -> "MVP"
      Architecture.MVC -> "MVC"
      Architecture.CLEAN -> "Clean Architecture"
      Architecture.UNKNOWN -> UNKNOWN
    }

    private fun buildSystemLabel(value: BuildSystem): String = when (value) {
      BuildSystem.GROOVY -> "Gradle (Groovy)"
      BuildSystem.KTS -> "Gradle (Kotlin DSL)"
      BuildSystem.UNKNOWN -> UNKNOWN
    }
  }
}
