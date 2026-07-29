package com.hmx.ide.ai.memory

import android.content.Context
import java.io.File

object MemoryService {

  private var appContext: Context? = null
  private val managers = mutableMapOf<String, MemoryManager>()
  private val knowledgeManagers = mutableMapOf<String, ProjectKnowledgeManager>()

  fun init(context: Context) {
    appContext = context.applicationContext
  }

  fun withProject(projectDir: File): MemoryManager {
    val key = projectDir.absolutePath
    return managers.getOrPut(key) {
      val manager = MemoryManager(projectDir, checkNotNull(appContext))
      HmxFolder.ensureSetup(projectDir)
      manager.open()
      manager
    }
  }

  fun withProjectKnowledge(projectDir: File): ProjectKnowledgeManager {
    val key = projectDir.absolutePath
    return knowledgeManagers.getOrPut(key) {
      val manager = ProjectKnowledgeManager(projectDir, checkNotNull(appContext))
      HmxFolder.ensureSetup(projectDir)
      manager.open()
      manager
    }
  }

  fun releaseProject(projectDir: File) {
    val key = projectDir.absolutePath
    managers[key]?.close()
    managers.remove(key)
    knowledgeManagers[key]?.close()
    knowledgeManagers.remove(key)
  }

  fun releaseAll() {
    managers.values.forEach { it.close() }
    managers.clear()
    knowledgeManagers.values.forEach { it.close() }
    knowledgeManagers.clear()
  }
}