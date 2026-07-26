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
  val fileInfos: List<ProjectFileInfo> = emptyList(),
)

object ProjectScanner {

  private val SKIP_DIRS = setOf("build", ".git", ".gradle", "bin", "obj", "node_modules", "cmake-build-debug", ".idea")
  private val MANIFEST_NAMES = setOf("AndroidManifest.xml")
  private val GRADLE_NAMES = setOf("build.gradle", "build.gradle.kts", "settings.gradle.kts", "settings.gradle")

  private val RELEVANT_EXTENSIONS = setOf("kt", "java", "kts", "xml", "gradle", "properties", "toml", "pro")

  fun scan(root: File, onProgress: ((String) -> Unit)? = null): ScanResult {
    val kotlinFiles = mutableListOf<File>()
    val javaFiles = mutableListOf<File>()
    val xmlLayoutFiles = mutableListOf<File>()
    val manifestFiles = mutableListOf<File>()
    val ktsFiles = mutableListOf<File>()
    val groovyGradleFiles = mutableListOf<File>()
    val allSourceFiles = mutableListOf<File>()
    val fileInfos = mutableListOf<ProjectFileInfo>()

    val allFiles = mutableListOf<File>()
    walkProject(root) { allFiles.add(it) }
    val total = allFiles.size
    var count = 0

    for (file in allFiles) {
      count++
      if (total > 100 && count % (total / 10) == 0) {
        onProgress?.invoke("Scanning... ${(count * 100 / total)}%")
      }
      when {
        file.name in MANIFEST_NAMES -> {
          manifestFiles.add(file)
          onProgress?.invoke("✓ AndroidManifest.xml")
        }
        file.name in GRADLE_NAMES -> {
          allSourceFiles.add(file)
          fileInfos.add(buildFileInfo(file, root))
          if (file.name.endsWith(".kts")) ktsFiles.add(file)
          else groovyGradleFiles.add(file)
          onProgress?.invoke("✓ ${file.name}")
        }
        file.name == "gradle.properties" || file.name == "libs.versions.toml" -> {
          allSourceFiles.add(file)
          fileInfos.add(buildFileInfo(file, root))
        }
        file.extension == "kt" -> {
          kotlinFiles.add(file)
          allSourceFiles.add(file)
          fileInfos.add(buildFileInfo(file, root))
        }
        file.extension == "java" -> {
          javaFiles.add(file)
          allSourceFiles.add(file)
          fileInfos.add(buildFileInfo(file, root))
        }
        file.extension == "xml" -> {
          if (isLayoutXml(file)) xmlLayoutFiles.add(file)
        }
        file.extension in RELEVANT_EXTENSIONS -> {
          allSourceFiles.add(file)
        }
      }
    }

    onProgress?.invoke("✓ Indexed ${allSourceFiles.size} files")

    return ScanResult(
      kotlinFiles = kotlinFiles,
      javaFiles = javaFiles,
      xmlLayoutFiles = xmlLayoutFiles,
      manifestFiles = manifestFiles,
      ktsFiles = ktsFiles,
      groovyGradleFiles = groovyGradleFiles,
      allSourceFiles = allSourceFiles,
      fileInfos = fileInfos,
    )
  }

  private fun buildFileInfo(file: File, root: File): ProjectFileInfo {
    val content = runCatching { file.readText() }.getOrNull() ?: return ProjectFileInfo(
      path = file.absolutePath,
      relativePath = file.relativeTo(root).path,
    )
    val packageName = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)
      .find(content)?.groupValues?.getOrNull(1)
    val imports = Regex("""^import\s+([\w.*]+)""", RegexOption.MULTILINE)
      .findAll(content).map { it.groupValues[1] }.toList()
    val classes = Regex("""\b(?:class|interface|object|enum class|data class|sealed class|abstract class)\s+(\w+)""")
      .findAll(content).map { it.groupValues[1] }.toList()
    return ProjectFileInfo(
      path = file.absolutePath,
      relativePath = file.relativeTo(root).path,
      packageName = packageName,
      imports = imports,
      classes = classes,
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
