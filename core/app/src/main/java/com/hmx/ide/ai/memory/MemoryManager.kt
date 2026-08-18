package com.hmx.ide.ai.memory

import android.content.Context
import com.hmx.ide.ai.memory.HmxFolder
import java.io.File

class MemoryManager(private val projectDir: File, private val appContext: Context) {

  private lateinit var repo: MemoryRepository
  private lateinit var db: MemoryDatabase
  private val dbFile: File = HmxFolder.getMemoryDb(HmxFolder.getHmxDir(projectDir))

  fun open() {
    db = MemoryDatabase(appContext, dbFile.absolutePath)
    repo = MemoryRepository(db)
  }

  fun close() {
    if (::db.isInitialized) db.close()
  }

  val projectDirPath get() = projectDir.absolutePath

  fun saveConversation(sessionId: String, role: String, content: String, tokenCount: Int = 0): Long {
    val entry = ConversationEntry(
      projectDir = projectDir.absolutePath,
      sessionId = sessionId,
      role = role,
      content = content,
      tokenCount = tokenCount,
      createdAt = System.currentTimeMillis(),
    )
    return repo.insertConversation(entry)
  }

  fun getConversations(sessionId: String? = null): List<ConversationEntry> {
    return repo.getConversations(projectDir.absolutePath, sessionId)
  }

  fun saveSummary(type: String, title: String, content: String, sourceCount: Int = 0, chatCount: Int = 0): Long {
    val entry = SummaryEntry(
      projectDir = projectDir.absolutePath,
      type = type,
      title = title,
      content = content,
      sourceCount = sourceCount,
      chatCount = chatCount,
      createdAt = System.currentTimeMillis(),
      updatedAt = System.currentTimeMillis(),
    )
    return repo.insertSummary(entry)
  }

  fun getSummaries(type: String? = null): List<SummaryEntry> {
    return repo.getSummaries(projectDir.absolutePath, type)
  }

  fun saveDecision(title: String, content: String, status: String = "pending"): Long {
    val entry = DecisionEntry(
      projectDir = projectDir.absolutePath,
      title = title,
      content = content,
      status = status,
      createdAt = System.currentTimeMillis(),
    )
    return repo.insertDecision(entry)
  }

  fun getDecisions(status: String? = null): List<DecisionEntry> {
    return repo.getDecisions(projectDir.absolutePath, status)
  }

  fun saveTodo(title: String, content: String = "", priority: Int = 0): Long {
    val entry = TodoEntry(
      projectDir = projectDir.absolutePath,
      title = title,
      content = content,
      priority = priority,
      createdAt = System.currentTimeMillis(),
    )
    return repo.insertTodo(entry)
  }

  fun getTodos(): List<TodoEntry> {
    return repo.getTodos(projectDir.absolutePath)
  }

  fun markTodoDone(id: Long): Int {
    return repo.markTodoDone(id)
  }

  fun savePreference(key: String, value: String): Long {
    val entry = PreferenceEntry(
      projectDir = projectDir.absolutePath,
      key = key,
      value = value,
      updatedAt = System.currentTimeMillis(),
    )
    return repo.insertPreference(entry)
  }

  fun getPreference(key: String): String? {
    return repo.getPreference(projectDir.absolutePath, key)
  }

  fun getAllPreferences(): Map<String, String> {
    return repo.getAllPreferences(projectDir.absolutePath)
  }

  fun openSession(name: String): Long {
    val entry = SessionEntry(
      projectDir = projectDir.absolutePath,
      name = name,
      startedAt = System.currentTimeMillis(),
    )
    return repo.insertSession(entry)
  }

  fun closeSession(id: Long, messageCount: Int): Int {
    val endedAt = System.currentTimeMillis()
    return repo.updateSession(id, endedAt, messageCount)
  }

  fun getSessions(): List<SessionEntry> {
    return repo.getSessions(projectDir.absolutePath)
  }

  fun saveFact(content: String, source: String = "", confidence: Float = 0.5f): Long {
    val entry = FactEntry(
      projectDir = projectDir.absolutePath,
      content = content,
      source = source,
      confidence = confidence,
      createdAt = System.currentTimeMillis(),
    )
    return repo.insertFact(entry)
  }

  fun getFacts(): List<FactEntry> {
    return repo.getFacts(projectDir.absolutePath)
  }

  fun saveNote(title: String, content: String): Long {
    val entry = NoteEntry(
      projectDir = projectDir.absolutePath,
      title = title,
      content = content,
      createdAt = System.currentTimeMillis(),
    )
    return repo.insertNote(entry)
  }

  fun getNotes(): List<NoteEntry> {
    return repo.getNotes(projectDir.absolutePath)
  }

  fun saveCache(key: String, value: String, ttlMs: Long = 3600_000L): Long {
    val entry = CacheEntry(
      projectDir = projectDir.absolutePath,
      key = key,
      value = value,
      expiresAt = System.currentTimeMillis() + ttlMs,
    )
    return repo.insertCache(entry)
  }

  fun getCache(key: String): String? {
    return repo.getCache(projectDir.absolutePath, key)
  }

  fun search(query: String): List<MemorySearchResult> {
    return repo.search(projectDir.absolutePath, query)
  }

  fun deleteProjectData() {
    if (::repo.isInitialized) {
      MemoryContract.ALL_TABLES.forEach { table ->
        db.writableDatabase.delete(table,
          "${MemoryContract.Conversation.PROJECT_DIR} = ?",
          arrayOf(projectDir.absolutePath))
      }
    }
  }

  fun cleanupOldConversations(olderThanDays: Int = 30, maxConversations: Int = 2000) {
    val cutoff = System.currentTimeMillis() - (olderThanDays * 24L * 60 * 60 * 1000)
    repo.deleteConversations(projectDir.absolutePath, cutoff)
  }

  fun cleanupExpiredCache(): Int {
    return repo.cleanupExpiredCache()
  }

  fun compactDatabase(): Int {
    val dbSizeBefore = dbFile.length()
    db.writableDatabase.execSQL("VACUUM")
    db.writableDatabase.execSQL("REINDEX")
    val dbSizeAfter = dbFile.length()
    return (dbSizeBefore - dbSizeAfter).toInt()
  }

  fun getMemoryStats(): MemoryStats {
    val count = db.readableDatabase.compileStatement(
      "SELECT COUNT(*) FROM ${MemoryContract.Tables.Conversation}"
    ).simpleQueryForLong()
    val size = dbFile.length()
    return MemoryStats(
      totalEntries = count.toInt(),
      dbSizeBytes = size,
      projectDir = projectDir.absolutePath,
      hmxDir = HmxFolder.getHmxDir(projectDir).absolutePath,
    )
  }
}

data class MemoryStats(
  val totalEntries: Int,
  val dbSizeBytes: Long,
  val projectDir: String,
  val hmxDir: String,
)