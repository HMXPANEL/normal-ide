package com.hmx.ide.ai.context

import java.io.File

object ContextCache {

  private data class Entry(
    val context: ProjectContext,
    val trackedFiles: Map<String, Long>,
  )

  private val cache = mutableMapOf<String, Entry>()

  fun get(projectDir: String): ProjectContext? {
    val entry = cache[projectDir] ?: return null
    if (isStale(projectDir, entry)) {
      cache.remove(projectDir)
      return null
    }
    return entry.context
  }

  fun set(projectDir: String, context: ProjectContext) {
    val root = File(projectDir)
    cache[projectDir] = Entry(
      context = context,
      trackedFiles = mapOf(
        "manifest" to getModStamp(File(root, "app/src/main/AndroidManifest.xml")),
        "gradle_app" to getModStamp(File(root, "app/build.gradle.kts")),
        "gradle_root" to getModStamp(File(root, "build.gradle.kts")),
        "settings" to getModStamp(File(root, "settings.gradle.kts")),
        "gradle_app_groovy" to getModStamp(File(root, "app/build.gradle")),
        "gradle_root_groovy" to getModStamp(File(root, "build.gradle")),
        "settings_groovy" to getModStamp(File(root, "settings.gradle")),
      ),
    )
  }

  fun invalidate(projectDir: String) {
    cache.remove(projectDir)
  }

  fun getOrAnalyze(projectDir: String): ProjectContext {
    val cached = get(projectDir)
    if (cached != null) return cached

    val root = File(projectDir)
    if (!root.isDirectory) return ProjectContext(projectDir = projectDir)

    val scanResult = ProjectScanner.scan(root)
    val context = ProjectAnalyzer.analyze(root, scanResult)
    set(projectDir, context)
    return context
  }

  private fun isStale(projectDir: String, entry: Entry): Boolean {
    for ((key, stamp) in entry.trackedFiles) {
      val file = when (key) {
        "manifest" -> File(projectDir, "app/src/main/AndroidManifest.xml")
        "gradle_app" -> File(projectDir, "app/build.gradle.kts")
        "gradle_root" -> File(projectDir, "build.gradle.kts")
        "settings" -> File(projectDir, "settings.gradle.kts")
        "gradle_app_groovy" -> File(projectDir, "app/build.gradle")
        "gradle_root_groovy" -> File(projectDir, "build.gradle")
        "settings_groovy" -> File(projectDir, "settings.gradle")
        else -> continue
      }
      if (file.exists()) {
        val currentStamp = file.lastModified()
        if (currentStamp != stamp) return true
      }
    }
    return false
  }

  private fun getModStamp(file: File): Long {
    return if (file.exists()) file.lastModified() else -1L
  }
}
