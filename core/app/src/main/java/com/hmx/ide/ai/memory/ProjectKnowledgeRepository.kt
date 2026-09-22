package com.hmx.ide.ai.memory

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class ProjectKnowledgeRepository(private val db: MemoryDatabase) {
  private val dbw get() = db.writableDatabase
  private val dbr get() = db.readableDatabase

  fun insert(entry: KnowledgeEntry): Long {
    val existing = get(entry.projectDir, entry.category, entry.key)
    val values = ContentValues().apply {
      put(MemoryContract.ProjectKnowledge.PROJECT_DIR, entry.projectDir)
      put(MemoryContract.ProjectKnowledge.CATEGORY, entry.category.label)
      put(MemoryContract.ProjectKnowledge.KEY, entry.key)
      put(MemoryContract.ProjectKnowledge.VALUE, entry.value)
      put(MemoryContract.ProjectKnowledge.TITLE, entry.title)
      put(MemoryContract.ProjectKnowledge.TAGS, entry.tags.joinToString(","))
      put(MemoryContract.ProjectKnowledge.CREATED_AT, existing?.createdAt ?: entry.createdAt)
      put(MemoryContract.ProjectKnowledge.UPDATED_AT, System.currentTimeMillis())
    }
    return dbw.insertWithOnConflict(
      MemoryContract.Tables.ProjectKnowledge, null, values,
      SQLiteDatabase.CONFLICT_REPLACE)
  }

  fun get(projectDir: String, category: KnowledgeCategory, key: String): KnowledgeEntry? {
    val c = dbr.query(
      MemoryContract.Tables.ProjectKnowledge, null,
      "${MemoryContract.ProjectKnowledge.PROJECT_DIR} = ? AND " +
        "${MemoryContract.ProjectKnowledge.CATEGORY} = ? AND " +
        "${MemoryContract.ProjectKnowledge.KEY} = ?",
      arrayOf(projectDir, category.label, key), null, null, null)
    return c.use { if (it.moveToFirst()) it.toEntry() else null }
  }

  fun getAll(projectDir: String, category: KnowledgeCategory? = null): List<KnowledgeEntry> {
    val sel = "${MemoryContract.ProjectKnowledge.PROJECT_DIR} = ?" +
      (if (category != null) " AND ${MemoryContract.ProjectKnowledge.CATEGORY} = ?" else "")
    val args = if (category != null) arrayOf(projectDir, category.label) else arrayOf(projectDir)
    val c = dbr.query(
      MemoryContract.Tables.ProjectKnowledge, null, sel, args,
      null, null, "${MemoryContract.ProjectKnowledge.UPDATED_AT} DESC")
    return c.use { it.mapToList() }
  }

  fun delete(projectDir: String, category: KnowledgeCategory, key: String): Int {
    return dbw.delete(
      MemoryContract.Tables.ProjectKnowledge,
      "${MemoryContract.ProjectKnowledge.PROJECT_DIR} = ? AND " +
        "${MemoryContract.ProjectKnowledge.CATEGORY} = ? AND " +
        "${MemoryContract.ProjectKnowledge.KEY} = ?",
      arrayOf(projectDir, category.label, key))
  }

  fun deleteAll(projectDir: String) {
    dbw.delete(MemoryContract.Tables.ProjectKnowledge,
      "${MemoryContract.ProjectKnowledge.PROJECT_DIR} = ?", arrayOf(projectDir))
  }

  fun search(projectDir: String, query: String): List<KnowledgeEntry> {
    val lq = "%$query%"
    val c = dbr.query(
      MemoryContract.Tables.ProjectKnowledge, null,
      "${MemoryContract.ProjectKnowledge.PROJECT_DIR} = ? AND (" +
        "${MemoryContract.ProjectKnowledge.VALUE} LIKE ? OR " +
        "${MemoryContract.ProjectKnowledge.TITLE} LIKE ? OR " +
        "${MemoryContract.ProjectKnowledge.TAGS} LIKE ?)",
      arrayOf(projectDir, lq, lq, lq),
      null, null, "${MemoryContract.ProjectKnowledge.UPDATED_AT} DESC", "200")
    return c.use { it.mapToList() }
  }

  private fun Cursor.toEntry(): KnowledgeEntry = KnowledgeEntry(
    id = getLong(getColumnIndexOrThrow(MemoryContract.ProjectKnowledge.ID)),
    projectDir = getString(getColumnIndexOrThrow(MemoryContract.ProjectKnowledge.PROJECT_DIR)),
    category = KnowledgeCategory.valueOf(
      getString(getColumnIndexOrThrow(MemoryContract.ProjectKnowledge.CATEGORY)).uppercase()),
    key = getString(getColumnIndexOrThrow(MemoryContract.ProjectKnowledge.KEY)),
    value = getString(getColumnIndexOrThrow(MemoryContract.ProjectKnowledge.VALUE)),
    title = getString(getColumnIndexOrThrow(MemoryContract.ProjectKnowledge.TITLE)),
    tags = getString(getColumnIndexOrThrow(MemoryContract.ProjectKnowledge.TAGS))
      .split(",").filter { it.isNotBlank() },
    createdAt = getLong(getColumnIndexOrThrow(MemoryContract.ProjectKnowledge.CREATED_AT)),
    updatedAt = getLong(getColumnIndexOrThrow(MemoryContract.ProjectKnowledge.UPDATED_AT)),
  )

  private fun Cursor.mapToList(): List<KnowledgeEntry> {
    val list = mutableListOf<KnowledgeEntry>()
    while (moveToNext()) list.add(toEntry())
    return list
  }
}
