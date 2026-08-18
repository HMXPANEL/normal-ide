package com.hmx.ide.ai.context

import java.io.File

object PromptBuilder {

  fun build(index: ProjectIndex, currentFile: String? = null): String {
    val ctx = index.context
    val sb = StringBuilder()

    sb.appendLine("You are an AI coding assistant for an Android project.")
    sb.appendLine()

    sb.appendLine("=== PROJECT STATUS ===")
    sb.appendLine("Project: ${File(ctx.projectDir).name}")
    sb.appendLine("Indexed Files: ${index.totalSourceFiles}")
    ctx.packageName?.let { sb.appendLine("Package: $it") }
    sb.appendLine("Language: ${ctx.language.display}")
    sb.appendLine("UI: ${ctx.ui.display}")
    sb.appendLine("Architecture: ${ctx.architecture.display}")
    sb.appendLine("Build System: ${ctx.buildSystem.display}")
    if (ctx.modules.isNotEmpty()) {
      sb.appendLine("Modules: ${ctx.modules.joinToString(", ")}")
    }
    if (ctx.libraries.isNotEmpty()) {
      sb.appendLine("Libraries: ${ctx.libraries.joinToString(", ")}")
    }
    if (ctx.activities.isNotEmpty()) {
      sb.appendLine("Activities: ${ctx.activities.size}")
    }
    if (ctx.minSdk != null || ctx.targetSdk != null) {
      sb.appendLine("SDK: min=${ctx.minSdk ?: "?"} target=${ctx.targetSdk ?: "?"}")
    }
    if (currentFile != null) {
      sb.appendLine("Current File: $currentFile")
    }
    sb.appendLine()

    sb.appendLine("=== RULES ===")
    sb.appendLine("1. Always match the existing project's language (${ctx.language.display}).")
    sb.appendLine("2. Continue using the existing UI framework (${ctx.ui.display}).")
    sb.appendLine("3. Respect the existing architecture (${ctx.architecture.display}).")
    sb.appendLine("4. Use the project's package structure (${ctx.packageName ?: "the existing structure"}).")
    sb.appendLine("5. Never change the project architecture unless the user explicitly requests it.")
    sb.appendLine("6. Never migrate Java to Kotlin or Kotlin to Java.")
    sb.appendLine("7. Never migrate XML layouts to Jetpack Compose or Compose to XML.")
    sb.appendLine("8. When generating a new file, place it in the correct module and package.")
    sb.appendLine("9. Do NOT generate generic templates. Every response must be tailored to this project.")
    sb.appendLine("10. Responses must reference the project structure, classes, and packages you see above.")
    sb.appendLine()

    sb.appendLine("=== FILE MODIFICATION ===")
    sb.appendLine("To create or modify a file:")
    sb.appendLine("[[WRITE:relative/path/File.kt]]")
    sb.appendLine("<full file content>")
    sb.appendLine("[[END]]")
    sb.appendLine()
    sb.appendLine("When the user says 'analyze project', provide a full analysis.")
    sb.appendLine("Otherwise just answer conversationally.")

    return sb.toString()
  }

  /**
   * Builds the chat startup message from an already-computed [ProjectContextSummary].
   *
   * Performs no IO. Fields that are unavailable are rendered as `Unknown` rather than guessed.
   */
  fun buildStartupMessage(
    summary: ProjectContextSummary,
    currentFile: String? = null,
  ): String {
    val sb = StringBuilder()

    when (summary.state) {
      IndexingState.READY, IndexingState.UPDATING -> sb.appendLine("Project Ready ✅")
      IndexingState.INDEXING -> sb.appendLine("Scanning project…")
      IndexingState.UNAVAILABLE -> sb.appendLine("Project context unavailable")
    }
    sb.appendLine()

    sb.appendLine("Project: ${summary.projectName}")
    sb.appendLine("Language: ${ProjectContextSummary.displayOrUnknown(summary.language)}")
    summary.uiFramework?.let { sb.appendLine("UI: $it") }
    summary.packageName?.let { sb.appendLine("Package: $it") }
    summary.architecture?.let { sb.appendLine("Architecture: $it") }
    summary.buildSystem?.let { sb.appendLine("Build: $it") }
    if (summary.moduleCount > 0) sb.appendLine("Modules: ${summary.moduleCount}")
    if (summary.totalFiles > 0) sb.appendLine("Files: ${summary.totalFiles}")
    if (summary.lastIndexedAt > 0L) {
      sb.appendLine("Last Indexed: ${formatRelativeTime(summary.lastIndexedAt)}")
    }
    currentFile?.let { sb.appendLine("Current File: $it") }

    sb.appendLine()
    sb.appendLine("Capabilities")
    sb.appendLine("• Explain code • Generate code • Fix errors • Find bugs")
    sb.appendLine("• Refactor • Search project • Analyze architecture • Explain APIs")

    return sb.toString().trimEnd()
  }

  /** Formats a timestamp as a short relative string, e.g. `2 sec ago`. */
  fun formatRelativeTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return "Unknown"
    val deltaMs = System.currentTimeMillis() - timestampMs
    if (deltaMs < 0L) return "just now"
    val seconds = deltaMs / 1000L
    return when {
      seconds < 5L -> "just now"
      seconds < 60L -> "$seconds sec ago"
      seconds < 3_600L -> "${seconds / 60L} min ago"
      seconds < 86_400L -> "${seconds / 3_600L} hr ago"
      else -> "${seconds / 86_400L} day(s) ago"
    }
  }

  fun buildAnalysis(index: ProjectIndex): String {
    val ctx = index.context
    val sb = StringBuilder()

    sb.appendLine("=== PROJECT ANALYSIS ===")
    sb.appendLine()
    sb.appendLine("Project: ${File(ctx.projectDir).name}")
    sb.appendLine("Package: ${ctx.packageName ?: "N/A"}")
    sb.appendLine("Language: ${ctx.language.display}")
    sb.appendLine("UI Framework: ${ctx.ui.display}")
    sb.appendLine("Architecture: ${ctx.architecture.display}")
    sb.appendLine("Build System: ${ctx.buildSystem.display}")
    sb.appendLine("Modules: ${ctx.modules.joinToString(", ").ifEmpty { "N/A" }}")
    sb.appendLine("Source Files: ${index.totalSourceFiles}")
    sb.appendLine("SDK: min=${ctx.minSdk ?: "N/A"} target=${ctx.targetSdk ?: "N/A"} compile=${ctx.compileSdk ?: "N/A"}")
    sb.appendLine()

    if (ctx.libraries.isNotEmpty()) {
      sb.appendLine("=== DETECTED LIBRARIES ===")
      ctx.libraries.forEach { sb.appendLine("- $it") }
      sb.appendLine()
    }

    if (ctx.activities.isNotEmpty()) {
      sb.appendLine("=== ACTIVITIES ===")
      ctx.activities.forEach { sb.appendLine("- $it") }
      sb.appendLine()
    }

    if (index.files.isNotEmpty()) {
      sb.appendLine("=== PACKAGE HIERARCHY ===")
      val packages = index.files.mapNotNull { it.packageName }.distinct().sorted()
      packages.forEach { sb.appendLine("- $it") }
      sb.appendLine()
    }

    sb.appendLine("=== CLASSES & OBJECTS ===")
    val classCount = index.files.sumOf { it.classes.size }
    val importCount = index.files.sumOf { it.imports.size }
    sb.appendLine("Total classes/interfaces: $classCount")
    sb.appendLine("Total imports: $importCount")
    sb.appendLine()

    sb.appendLine("To analyze code quality or find bugs, ask about specific files.")
    return sb.toString()
  }

  private val Language.display: String get() = when (this) {
    Language.JAVA -> "Java"
    Language.KOTLIN -> "Kotlin"
    Language.MIXED -> "Mixed (Java + Kotlin)"
    Language.UNKNOWN -> "Mixed"
  }

  private val UIFramework.display: String get() = when (this) {
    UIFramework.XML -> "XML Layouts"
    UIFramework.COMPOSE -> "Jetpack Compose"
    UIFramework.MIXED -> "Mixed (XML + Compose)"
    UIFramework.UNKNOWN -> "XML Layouts"
  }

  private val Architecture.display: String get() = when (this) {
    Architecture.MVVM -> "MVVM"
    Architecture.MVP -> "MVP"
    Architecture.MVC -> "MVC"
    Architecture.CLEAN -> "Clean Architecture"
    Architecture.UNKNOWN -> "Standard Android"
  }

  private val BuildSystem.display: String get() = when (this) {
    BuildSystem.GROOVY -> "Gradle (Groovy DSL)"
    BuildSystem.KTS -> "Gradle Kotlin DSL"
    BuildSystem.UNKNOWN -> "Gradle"
  }

  private val ProjectType.display: String get() = when (this) {
    ProjectType.ANDROID -> "Android"
    ProjectType.UNKNOWN -> "Android"
  }
}
