package com.hmx.ide.ai.context

import java.io.File

object PromptBuilder {

  fun build(context: ProjectContext): String {
    val sb = StringBuilder()

    sb.appendLine("You are an AI coding assistant for an Android project.")
    sb.appendLine()

    sb.appendLine("=== PROJECT ANALYSIS ===")
    sb.appendLine("Project: ${File(context.projectDir).name}")
    context.packageName?.let { sb.appendLine("Package: $it") }
    sb.appendLine("Language: ${context.language.display}")
    sb.appendLine("UI Framework: ${context.ui.display}")
    sb.appendLine("Architecture: ${context.architecture.display}")
    sb.appendLine("Build System: ${context.buildSystem.display}")
    if (context.modules.isNotEmpty()) {
      sb.appendLine("Modules: ${context.modules.joinToString(", ")}")
    }
    if (context.libraries.isNotEmpty()) {
      sb.appendLine("Libraries: ${context.libraries.joinToString(", ")}")
    }
    if (context.activities.isNotEmpty()) {
      sb.appendLine("Activities: ${context.activities.joinToString(", ")}")
    }
    if (context.fragments.isNotEmpty()) {
      sb.appendLine("Fragments: ${context.fragments.joinToString(", ")}")
    }
    if (context.minSdk != null) sb.appendLine("minSdk: ${context.minSdk}")
    if (context.targetSdk != null) sb.appendLine("targetSdk: ${context.targetSdk}")
    if (context.compileSdk != null) sb.appendLine("compileSdk: ${context.compileSdk}")
    sb.appendLine()

    sb.appendLine("=== RULES ===")
    sb.appendLine("1. Always match the existing project's language. ")
    sb.appendLine("   The project uses ${context.language.display}. Generate code in ${context.language.display} only.")
    sb.appendLine("2. Continue using the existing UI framework. ")
    sb.appendLine("   The project uses ${context.ui.display}. Never migrate to a different UI framework.")
    sb.appendLine("3. Respect the existing architecture (${context.architecture.display}).")
    sb.appendLine("4. Use the project's package structure (${context.packageName ?: "the existing structure"}).")
    sb.appendLine("5. Never change the project architecture unless the user explicitly requests it.")
    sb.appendLine("6. Never migrate Java to Kotlin or Kotlin to Java.")
    sb.appendLine("7. Never migrate XML layouts to Jetpack Compose or Compose to XML.")
    sb.appendLine("8. When generating a new file, place it in the correct module and package.")
    sb.appendLine("9. Do NOT generate generic templates. Every response must be tailored to this specific project.")
    sb.appendLine()

    sb.appendLine("=== FILE MODIFICATION ===")
    sb.appendLine("To create or modify a file, respond with a fenced block:")
    sb.appendLine("")
    sb.appendLine("[[WRITE:relative/path/File.kt]]")
    sb.appendLine("<full file content>")
    sb.appendLine("[[END]]")
    sb.appendLine("")
    sb.appendLine("To read a file, ask me and I will read it for you.")
    sb.appendLine("Otherwise just answer conversationally.")

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
