package com.hmx.ide.ai.context

import java.io.File
import kotlin.math.min

object ProjectAnalyzer {

  private val LIBRARY_SIGNATURES = mapOf(
    "androidx.room" to "Room",
    "dagger.hilt" to "Hilt",
    "retrofit2" to "Retrofit",
    "okhttp3" to "OkHttp",
    "com.google.firebase" to "Firebase",
    "kotlinx.coroutines" to "Coroutines",
    "androidx.work" to "WorkManager",
    "androidx.navigation" to "Navigation",
    "com.google.android.material" to "Material Components",
    "com.bumptech.glide" to "Glide",
    "coil" to "Coil",
    "com.squareup.moshi" to "Moshi",
    "com.google.gson" to "Gson",
    "androidx.datastore" to "DataStore",
    "androidx.compose" to "Jetpack Compose",
    "io.reactivex" to "RxJava",
    "org.koin" to "Koin",
    "androidx.lifecycle" to "Lifecycle",
    "androidx.paging" to "Paging",
    "androidx.hilt" to "Hilt",
  )

  private val ARCHIVE_PATTERNS = mapOf(
    Architecture.MVVM to listOf("ViewModel", "LiveData", "StateFlow", "MutableStateFlow"),
    Architecture.MVP to listOf("Presenter", "Contract", "Mvp"),
    Architecture.MVC to listOf("Controller", "Mvc"),
    Architecture.CLEAN to listOf("UseCase", "Repository", "domain", "data.repository"),
  )

  fun analyze(root: File, scanResult: ScanResult): ProjectIndex {
    val language = detectLanguage(scanResult)
    val ui = detectUi(scanResult)
    val buildSystem = detectBuildSystem(scanResult)
    val manifestInfo = parseManifests(scanResult.manifestFiles, root)
    val libraries = detectLibraries(scanResult, manifestInfo.gradleText)
    val architecture = detectArchitecture(scanResult)
    val modules = detectModules(scanResult)

    val context = ProjectContext(
      language = language,
      ui = ui,
      architecture = architecture,
      buildSystem = buildSystem,
      packageName = manifestInfo.packageName,
      modules = modules,
      libraries = libraries,
      activities = manifestInfo.activities,
      fragments = manifestInfo.fragments,
      services = manifestInfo.services,
      broadcastReceivers = manifestInfo.receivers,
      contentProviders = manifestInfo.providers,
      minSdk = manifestInfo.minSdk,
      targetSdk = manifestInfo.targetSdk,
      compileSdk = manifestInfo.compileSdk,
      hasApplicationClass = manifestInfo.hasApplicationClass,
      projectDir = root.absolutePath,
    )

    return ProjectIndex(
      context = context,
      files = scanResult.fileInfos,
      totalSourceFiles = scanResult.allSourceFiles.size,
      totalFiles = scanResult.kotlinFiles.size + scanResult.javaFiles.size,
    )
  }

  private fun detectLanguage(scan: ScanResult): Language {
    val ktCount = scan.kotlinFiles.size
    val javaCount = scan.javaFiles.size
    return when {
      ktCount > 0 && javaCount > 0 -> Language.MIXED
      ktCount > 0 -> Language.KOTLIN
      javaCount > 0 -> Language.JAVA
      else -> Language.UNKNOWN
    }
  }

  private fun detectUi(scan: ScanResult): UIFramework {
    val hasXmlLayouts = scan.xmlLayoutFiles.isNotEmpty()
    val hasCompose = scan.fileInfos.any { f ->
      f.imports.any { it.startsWith("androidx.compose") } ||
      f.imports.any { it.startsWith("androidx.compose.material") }
    }
    return when {
      hasXmlLayouts && hasCompose -> UIFramework.MIXED
      hasCompose -> UIFramework.COMPOSE
      hasXmlLayouts -> UIFramework.XML
      else -> UIFramework.UNKNOWN
    }
  }

  private fun detectBuildSystem(scan: ScanResult): BuildSystem {
    val hasKts = scan.ktsFiles.isNotEmpty()
    val hasGroovy = scan.groovyGradleFiles.isNotEmpty()
    return when {
      hasKts && hasGroovy -> BuildSystem.KTS
      hasKts -> BuildSystem.KTS
      hasGroovy -> BuildSystem.GROOVY
      else -> BuildSystem.UNKNOWN
    }
  }

  private fun detectLibraries(scan: ScanResult, gradleText: String): Set<String> {
    val found = mutableSetOf<String>()
    val allImports = scan.fileInfos.flatMap { it.imports }.joinToString("\n")
    val allText = "$gradleText\n$allImports"
    for ((sig, name) in LIBRARY_SIGNATURES) {
      if (allText.contains(sig)) found.add(name)
    }
    return found
  }

  private fun detectArchitecture(scan: ScanResult): Architecture {
    val sourceText = scan.fileInfos.flatMap { it.imports }.joinToString("\n")
    for ((arch, patterns) in ARCHIVE_PATTERNS) {
      if (patterns.any { sourceText.contains(it) }) return arch
    }
    return Architecture.UNKNOWN
  }

  private fun detectModules(scan: ScanResult): List<String> {
    val moduleSet = mutableSetOf<String>()
    for (f in scan.allSourceFiles) {
      val path = f.absolutePath
      val parts = path.split("/")
      val srcIdx = parts.indexOf("src")
      if (srcIdx > 1) {
        moduleSet.add(parts[srcIdx - 1])
      }
    }
    return moduleSet.toList().sorted()
  }

  private fun parseManifests(manifestFiles: List<File>, root: File): ManifestInfo {
    if (manifestFiles.isEmpty()) return ManifestInfo()
    val manifestFile = manifestFiles.firstOrNull { it.name == "AndroidManifest.xml" && it.parentFile?.name == "main" }
      ?: manifestFiles.firstOrNull()
      ?: return ManifestInfo()

    val content = runCatching { manifestFile.readText() }.getOrNull() ?: return ManifestInfo()

    val packageName = Regex("package=\"([^\"]+)\"").find(content)?.groupValues?.getOrNull(1)
    val appClass = Regex("android:name=\"([^\"]*Application[^\"]*)\"").find(content)?.groupValues?.getOrNull(1)
    val hasApplicationClass = appClass != null && appClass != "android.app.Application"

    val activities = Regex("<activity[^>]+android:name=\"([^\"]+)\"").findAll(content).map { it.groupValues[1] }.toList()
    val fragments = mutableListOf<String>()
    val services = Regex("<service[^>]+android:name=\"([^\"]+)\"").findAll(content).map { it.groupValues[1] }.toList()
    val receivers = Regex("<receiver[^>]+android:name=\"([^\"]+)\"").findAll(content).map { it.groupValues[1] }.toList()
    val providers = Regex("<provider[^>]+android:name=\"([^\"]+)\"").findAll(content).map { it.groupValues[1] }.toList()

    val minSdk = Regex("minSdk(?:Version)?[=\"]+([0-9]+)").find(content)?.groupValues?.getOrNull(1)?.toIntOrNull()
    val targetSdk = Regex("targetSdk(?:Version)?[=\"]+([0-9]+)").find(content)?.groupValues?.getOrNull(1)?.toIntOrNull()
    val compileSdk = Regex("compileSdk(?:Version)?[=\"]+([0-9]+)").find(content)?.groupValues?.getOrNull(1)?.toIntOrNull()

    val gradleText = scanGradleForSdks(root)

    return ManifestInfo(
      packageName = packageName,
      hasApplicationClass = hasApplicationClass,
      activities = activities,
      fragments = fragments,
      services = services,
      receivers = receivers,
      providers = providers,
      minSdk = minSdk ?: extractSdkFromGradle(root, "minSdk"),
      targetSdk = targetSdk ?: extractSdkFromGradle(root, "targetSdk"),
      compileSdk = compileSdk ?: extractSdkFromGradle(root, "compileSdk"),
      gradleText = gradleText,
    )
  }

  private fun scanGradleForSdks(root: File): String {
    val files = listOf("build.gradle", "build.gradle.kts", "app/build.gradle", "app/build.gradle.kts")
    val sb = StringBuilder()
    for (name in files) {
      val f = File(root, name)
      if (f.exists()) {
        val text = runCatching { f.readText() }.getOrNull() ?: continue
        sb.append(text)
      }
    }
    return sb.toString()
  }

  private fun extractSdkFromGradle(root: File, key: String): Int? {
    val text = scanGradleForSdks(root)
    val regex = Regex("$key\\s*[=:]\\s*(\\d+)")
    return regex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
  }
}

private data class ManifestInfo(
  val packageName: String? = null,
  val hasApplicationClass: Boolean = false,
  val activities: List<String> = emptyList(),
  val fragments: List<String> = emptyList(),
  val services: List<String> = emptyList(),
  val receivers: List<String> = emptyList(),
  val providers: List<String> = emptyList(),
  val minSdk: Int? = null,
  val targetSdk: Int? = null,
  val compileSdk: Int? = null,
  val gradleText: String = "",
)
