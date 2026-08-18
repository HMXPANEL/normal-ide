package com.hmx.ide.ai.memory

import java.io.File

object HmxFolder {

  const val HMX_DIR_NAME = ".hmx"
  const val HMX_IGNORE_FILE = ".hmxignore"
  const val MEMORY_DB_NAME = "memory.db"
  const val VERSION_JSON = "version.json"

  fun getHmxDir(projectDir: File): File {
    val hmxDir = File(projectDir, HMX_DIR_NAME)
    if (!hmxDir.exists()) {
      hmxDir.mkdirs()
      createDefaultFiles(hmxDir)
    }
    return hmxDir
  }

  fun getMemoryDb(hmxDir: File): File {
    return File(hmxDir, MEMORY_DB_NAME)
  }

  fun getCacheDir(hmxDir: File): File {
    val cacheDir = File(hmxDir, "cache")
    if (!cacheDir.exists()) cacheDir.mkdirs()
    return cacheDir
  }

  fun getSessionsDir(hmxDir: File): File {
    val sessionsDir = File(hmxDir, "sessions")
    if (!sessionsDir.exists()) sessionsDir.mkdirs()
    return sessionsDir
  }

  fun getVersionFile(hmxDir: File): File {
    return File(hmxDir, VERSION_JSON)
  }

  fun getHmxIgnoreFile(projectDir: File): File {
    return File(projectDir, HMX_IGNORE_FILE)
  }

  fun createHmxIgnore(projectDir: File): File {
    val file = getHmxIgnoreFile(projectDir)
    if (!file.exists()) {
      file.writeText(
        "# HMX IDE memory folder\n" +
        "${HMX_DIR_NAME}/\n"
      )
    }
    return file
  }

  private fun createDefaultFiles(hmxDir: File) {
    val versionFile = File(hmxDir, VERSION_JSON)
    if (!versionFile.exists()) {
      versionFile.writeText(
        "{\"schemaVersion\": 1," +
        "\"createdAt\": \"${System.currentTimeMillis()}\"," +
        "\"lastMigrated\": 1}"
      )
    }
    getCacheDir(hmxDir)
    getSessionsDir(hmxDir)
  }

  fun ensureSetup(projectDir: File): File {
    val hmxDir = getHmxDir(projectDir)
    createHmxIgnore(projectDir)
    syncHmxIgnoreToGit(projectDir)
    return hmxDir
  }

  fun syncHmxIgnoreToGit(projectDir: File) {
    val ignoreFile = getHmxIgnoreFile(projectDir)
    if (!ignoreFile.exists()) return
    val gitDir = File(projectDir, ".git")
    if (!gitDir.isDirectory) return
    val excludeFile = File(gitDir, "info/exclude")
    if (!excludeFile.exists()) return
    val existing = excludeFile.readLines()
    val newLines = ignoreFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
    val toAdd = newLines.filterNot { line -> existing.any { it.trim() == line.trim() } }
    if (toAdd.isEmpty()) return
    excludeFile.appendText("\n" + toAdd.joinToString("\n") + "\n")
  }
}
