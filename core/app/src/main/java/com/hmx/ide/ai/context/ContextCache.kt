package com.hmx.ide.ai.context

import java.io.File

object ContextCache {

  private data class Entry(
    val index: ProjectIndex,
    val trackedFiles: Map<String, Long>,
  )

  private val cache = mutableMapOf<String, Entry>()

  fun get(projectDir: String): ProjectIndex? {
    val entry = cache[projectDir] ?: return null
    if (isStale(projectDir, entry)) {
      cache.remove(projectDir)
      return null
    }
    return entry.index
  }

  fun getContext(projectDir: String): ProjectContext? = get(projectDir)?.context

  fun set(projectDir: String, index: ProjectIndex) {
    val root = File(projectDir)
    cache[projectDir] = Entry(
      index = index,
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

  fun getOrAnalyze(projectDir: String, onProgress: ((String) -> Unit)? = null): ProjectIndex {
    val cached = get(projectDir)
    if (cached != null) return cached

    val root = File(projectDir)
    if (!root.isDirectory) return ProjectIndex(
      context = ProjectContext(projectDir = projectDir)
    )

    onProgress?.invoke("Scanning project...")
    val scanResult = ProjectScanner.scan(root, onProgress)
    onProgress?.invoke("Analyzing project structure...")
    val index = ProjectAnalyzer.analyze(root, scanResult)
    set(projectDir, index)
    onProgress?.invoke("✓ Project indexing completed")
    return index
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
