package com.hmx.ide.ai.context

data class ProjectContext(
  val projectType: ProjectType = ProjectType.ANDROID,
  val language: Language = Language.UNKNOWN,
  val ui: UIFramework = UIFramework.UNKNOWN,
  val architecture: Architecture = Architecture.UNKNOWN,
  val buildSystem: BuildSystem = BuildSystem.UNKNOWN,
  val packageName: String? = null,
  val modules: List<String> = emptyList(),
  val libraries: Set<String> = emptySet(),
  val activities: List<String> = emptyList(),
  val fragments: List<String> = emptyList(),
  val services: List<String> = emptyList(),
  val broadcastReceivers: List<String> = emptyList(),
  val contentProviders: List<String> = emptyList(),
  val minSdk: Int? = null,
  val targetSdk: Int? = null,
  val compileSdk: Int? = null,
  val hasApplicationClass: Boolean = false,
  val lastAnalyzed: Long = System.currentTimeMillis(),
  val projectDir: String = "",
)

enum class ProjectType { ANDROID, UNKNOWN }
enum class Language { JAVA, KOTLIN, MIXED, UNKNOWN }
enum class UIFramework { XML, COMPOSE, MIXED, UNKNOWN }
enum class Architecture { MVVM, MVP, MVC, CLEAN, UNKNOWN }
enum class BuildSystem { GROOVY, KTS, UNKNOWN }
