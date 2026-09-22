package com.hmx.ide.ai.memory

import android.content.Context
import java.io.File

class ProjectKnowledgeManager(
  private val projectDir: File,
  private val appContext: Context,
) {
  private lateinit var repo: ProjectKnowledgeRepository
  private lateinit var db: MemoryDatabase
  private val dbFile: File = HmxFolder.getMemoryDb(HmxFolder.getHmxDir(projectDir))
  private val projectPath get() = projectDir.absolutePath

  fun open() {
    db = MemoryDatabase(appContext, dbFile.absolutePath)
    repo = ProjectKnowledgeRepository(db)
  }

  fun close() {
    if (::db.isInitialized) db.close()
  }

  fun save(
    category: KnowledgeCategory,
    key: String,
    value: String,
    title: String = "",
    tags: List<String> = emptyList(),
  ): Long {
    return repo.insert(KnowledgeEntry(
      projectDir = projectPath, category = category, key = key,
      value = value, title = title, tags = tags,
    ))
  }

  fun get(category: KnowledgeCategory, key: String): KnowledgeEntry? {
    return repo.get(projectPath, category, key)
  }

  fun getAll(category: KnowledgeCategory? = null): List<KnowledgeEntry> {
    return repo.getAll(projectPath, category)
  }

  fun search(query: String): List<KnowledgeEntry> {
    return repo.search(projectPath, query)
  }

  fun delete(category: KnowledgeCategory, key: String) {
    repo.delete(projectPath, category, key)
  }

  fun deleteAll() {
    repo.deleteAll(projectPath)
  }

  fun count(): Int {
    val count = db.readableDatabase.compileStatement(
      "SELECT COUNT(*) FROM ${MemoryContract.Tables.ProjectKnowledge} " +
        "WHERE ${MemoryContract.ProjectKnowledge.PROJECT_DIR} = ?"
    ).apply { bindString(1, projectPath) }.simpleQueryForLong()
    return count.toInt()
  }
}
