package com.hmx.ide.ai.context

import java.io.File

data class ScanResult(
  val kotlinFiles: List<File> = emptyList(),
  val javaFiles: List<File> = emptyList(),
  val xmlLayoutFiles: List<File> = emptyList(),
  val manifestFiles: List<File> = emptyList(),
  val ktsFiles: List<File> = emptyList(),
  val groovyGradleFiles: List<File> = emptyList(),
  val allSourceFiles: List<File> = emptyList(),
)

object ProjectScanner {

  private val SKIP_DIRS = setOf("build", ".git", ".gradle", "bin", "obj", "node_modules", "cmake-build-debug", ".idea")
  private val MANIFEST_NAMES = setOf("AndroidManifest.xml")
  private val GRADLE_NAMES = setOf("build.gradle", "build.gradle.kts", "settings.gradle.kts", "settings.gradle")

  fun scan(root: File): ScanResult {
    val kotlinFiles = mutableListOf<File>()
    val javaFiles = mutableListOf<File>()
    val xmlLayoutFiles = mutableListOf<File>()
    val manifestFiles = mutableListOf<File>()
    val ktsFiles = mutableListOf<File>()
    val groovyGradleFiles = mutableListOf<File>()
    val allSourceFiles = mutableListOf<File>()

    walkProject(root) { file ->
      when {
        file.name in MANIFEST_NAMES -> manifestFiles.add(file)
        file.name in GRADLE_NAMES -> {
          allSourceFiles.add(file)
          if (file.name.endsWith(".kts")) ktsFiles.add(file)
          else groovyGradleFiles.add(file)
        }
        file.extension == "kt" -> { kotlinFiles.add(file); allSourceFiles.add(file) }
        file.extension == "java" -> { javaFiles.add(file); allSourceFiles.add(file) }
        file.extension == "xml" -> {
          if (isLayoutXml(file)) xmlLayoutFiles.add(file)
        }
      }
    }

    return ScanResult(
      kotlinFiles = kotlinFiles,
      javaFiles = javaFiles,
      xmlLayoutFiles = xmlLayoutFiles,
      manifestFiles = manifestFiles,
      ktsFiles = ktsFiles,
      groovyGradleFiles = groovyGradleFiles,
      allSourceFiles = allSourceFiles,
    )
  }

  private fun walkProject(root: File, action: (File) -> Unit) {
    val queue = ArrayDeque<File>().apply { add(root) }
    while (queue.isNotEmpty()) {
      val dir = queue.removeFirst()
      val children = dir.listFiles() ?: continue
      for (child in children) {
        if (child.isDirectory) {
          if (child.name !in SKIP_DIRS) queue.add(child)
        } else {
          action(child)
        }
      }
    }
  }

  private fun isLayoutXml(file: File): Boolean {
    val path = file.absolutePath
    return path.contains("/res/layout") || path.contains("/res/layout-")
  }

}
