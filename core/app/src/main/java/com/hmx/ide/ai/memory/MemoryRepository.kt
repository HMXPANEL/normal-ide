package com.hmx.ide.ai.memory

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class MemoryRepository(private val db: MemoryDatabase) {
  private val dbw get() = db.writableDatabase
  private val dbr get() = db.readableDatabase

  fun insertConversation(entry: ConversationEntry): Long {
    val values = ContentValues().apply {
      put(MemoryContract.Conversation.PROJECT_DIR, entry.projectDir)
      put(MemoryContract.Conversation.SESSION_ID, entry.sessionId)
      put(MemoryContract.Conversation.ROLE, entry.role)
      put(MemoryContract.Conversation.CONTENT, entry.content)
      put(MemoryContract.Conversation.TIMESTAMP, entry.createdAt)
      put(MemoryContract.Conversation.TOKEN_COUNT, entry.tokenCount)
    }
    return dbw.insert(MemoryContract.Tables.Conversation, null, values)
  }

  fun getConversations(projectDir: String, sessionId: String? = null): List<ConversationEntry> {
    val sel = "${MemoryContract.Conversation.PROJECT_DIR} = ?" +
      (if (sessionId != null) " AND ${MemoryContract.Conversation.SESSION_ID} = ?" else "")
    val args = if (sessionId != null) arrayOf(projectDir, sessionId) else arrayOf(projectDir)
    val c = dbr.query(MemoryContract.Tables.Conversation, null, sel, args, null, null,
      "${MemoryContract.Conversation.TIMESTAMP} DESC", "500")
    return c.use { it.mapToConversations() }
  }

  fun deleteConversations(projectDir: String, olderThan: Long): Int {
    return dbw.delete(MemoryContract.Tables.Conversation,
      "${MemoryContract.Conversation.PROJECT_DIR} = ? AND ${MemoryContract.Conversation.TIMESTAMP} < ?",
      arrayOf(projectDir, olderThan.toString()))
  }

  fun insertSummary(entry: SummaryEntry): Long {
    val values = ContentValues().apply {
      put(MemoryContract.Summary.PROJECT_DIR, entry.projectDir)
      put(MemoryContract.Summary.TYPE, entry.type)
      put(MemoryContract.Summary.TITLE, entry.title)
      put(MemoryContract.Summary.CONTENT, entry.content)
      put(MemoryContract.Summary.SOURCE_COUNT, entry.sourceCount)
      put(MemoryContract.Summary.CHAT_COUNT, entry.chatCount)
      put(MemoryContract.Summary.CREATED_AT, entry.createdAt)
      put(MemoryContract.Summary.UPDATED_AT, entry.updatedAt)
    }
    return dbw.insert(MemoryContract.Tables.Summary, null, values)
  }

  fun getSummaries(projectDir: String, type: String? = null): List<SummaryEntry> {
    val sel = "${MemoryContract.Summary.PROJECT_DIR} = ?" +
      (if (type != null) " AND ${MemoryContract.Summary.TYPE} = ?" else "")
    val args = if (type != null) arrayOf(projectDir, type) else arrayOf(projectDir)
    val c = dbr.query(MemoryContract.Tables.Summary, null, sel, args, null, null,
      "${MemoryContract.Summary.UPDATED_AT} DESC")
    return c.use { it.mapToSummaries() }
  }

  fun insertDecision(entry: DecisionEntry): Long {
    val values = ContentValues().apply {
      put(MemoryContract.Decision.PROJECT_DIR, entry.projectDir)
      put(MemoryContract.Decision.TITLE, entry.title)
      put(MemoryContract.Decision.CONTENT, entry.content)
      put(MemoryContract.Decision.STATUS, entry.status)
      put(MemoryContract.Decision.CREATED_AT, entry.createdAt)
    }
    return dbw.insert(MemoryContract.Tables.Decision, null, values)
  }

  fun getDecisions(projectDir: String, status: String? = null): List<DecisionEntry> {
    val sel = "${MemoryContract.Decision.PROJECT_DIR} = ?" +
      (if (status != null) " AND ${MemoryContract.Decision.STATUS} = ?" else "")
    val args = if (status != null) arrayOf(projectDir, status) else arrayOf(projectDir)
    val c = dbr.query(MemoryContract.Tables.Decision, null, sel, args, null, null,
      "${MemoryContract.Decision.CREATED_AT} DESC")
    return c.use { it.mapToDecisions() }
  }

  fun insertTodo(entry: TodoEntry): Long {
    val values = ContentValues().apply {
      put(MemoryContract.Todo.PROJECT_DIR, entry.projectDir)
      put(MemoryContract.Todo.TITLE, entry.title)
      put(MemoryContract.Todo.CONTENT, entry.content)
      put(MemoryContract.Todo.DONE, if (entry.done) 1 else 0)
      put(MemoryContract.Todo.PRIORITY, entry.priority)
      put(MemoryContract.Todo.CREATED_AT, entry.createdAt)
    }
    return dbw.insert(MemoryContract.Tables.Todo, null, values)
  }

  fun getTodos(projectDir: String): List<TodoEntry> {
    val c = dbr.query(MemoryContract.Tables.Todo, null,
      "${MemoryContract.Todo.PROJECT_DIR} = ?", arrayOf(projectDir),
      null, null, "${MemoryContract.Todo.PRIORITY} DESC, ${MemoryContract.Todo.CREATED_AT} ASC")
    return c.use { it.mapToTodos() }
  }

  fun markTodoDone(id: Long): Int {
    val cv = ContentValues().apply { put(MemoryContract.Todo.DONE, 1) }
    return dbw.update(MemoryContract.Tables.Todo, cv,
      "${MemoryContract.Todo.ID} = ?", arrayOf(id.toString()))
  }

  fun insertPreference(entry: PreferenceEntry): Long {
    val values = ContentValues().apply {
      put(MemoryContract.Preference.PROJECT_DIR, entry.projectDir)
      put(MemoryContract.Preference.KEY, entry.key)
      put(MemoryContract.Preference.VALUE, entry.value)
      put(MemoryContract.Preference.UPDATED_AT, entry.updatedAt)
    }
    return dbw.insertWithOnConflict(MemoryContract.Tables.Preference, null, values,
      SQLiteDatabase.CONFLICT_REPLACE)
  }

  fun getPreference(projectDir: String, key: String): String? {
    val c = dbr.query(MemoryContract.Tables.Preference, arrayOf(MemoryContract.Preference.VALUE),
      "${MemoryContract.Preference.PROJECT_DIR} = ? AND ${MemoryContract.Preference.KEY} = ?",
      arrayOf(projectDir, key), null, null, null)
    c.use { if (it.moveToFirst()) return it.getString(0) }
    return null
  }

  fun getAllPreferences(projectDir: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    val c = dbr.query(MemoryContract.Tables.Preference,
      arrayOf(MemoryContract.Preference.KEY, MemoryContract.Preference.VALUE),
      "${MemoryContract.Preference.PROJECT_DIR} = ?", arrayOf(projectDir),
      null, null, null)
    c.use { while (it.moveToNext()) result[it.getString(0)] = it.getString(1) }
    return result
  }

  fun insertSession(entry: SessionEntry): Long {
    val values = ContentValues().apply {
      put(MemoryContract.Session.PROJECT_DIR, entry.projectDir)
      put(MemoryContract.Session.NAME, entry.name)
      put(MemoryContract.Session.STARTED_AT, entry.startedAt)
      put(MemoryContract.Session.MESSAGE_COUNT, entry.messageCount)
    }
    return dbw.insert(MemoryContract.Tables.Session, null, values)
  }

  fun updateSession(id: Long, endedAt: Long, messageCount: Int): Int {
    val values = ContentValues().apply {
      put(MemoryContract.Session.ENDED_AT, endedAt)
      put(MemoryContract.Session.MESSAGE_COUNT, messageCount)
    }
    return dbw.update(MemoryContract.Tables.Session, values,
      "${MemoryContract.Session.ID} = ?", arrayOf(id.toString()))
  }

  fun getSessions(projectDir: String): List<SessionEntry> {
    val c = dbr.query(MemoryContract.Tables.Session, null,
      "${MemoryContract.Session.PROJECT_DIR} = ?", arrayOf(projectDir),
      null, null, "${MemoryContract.Session.STARTED_AT} DESC")
    return c.use { it.mapToSessions() }
  }

  fun insertFact(entry: FactEntry): Long {
    val values = ContentValues().apply {
      put(MemoryContract.Fact.PROJECT_DIR, entry.projectDir)
      put(MemoryContract.Fact.CONTENT, entry.content)
      put(MemoryContract.Fact.SOURCE, entry.source)
      put(MemoryContract.Fact.CONFIDENCE, entry.confidence)
      put(MemoryContract.Fact.CREATED_AT, entry.createdAt)
    }
    return dbw.insert(MemoryContract.Tables.Fact, null, values)
  }

  fun getFacts(projectDir: String): List<FactEntry> {
    val c = dbr.query(MemoryContract.Tables.Fact, null,
      "${MemoryContract.Fact.PROJECT_DIR} = ?", arrayOf(projectDir),
      null, null, "${MemoryContract.Fact.CREATED_AT} DESC")
    return c.use { it.mapToFacts() }
  }

  fun insertNote(entry: NoteEntry): Long {
    val values = ContentValues().apply {
      put(MemoryContract.Note.PROJECT_DIR, entry.projectDir)
      put(MemoryContract.Note.TITLE, entry.title)
      put(MemoryContract.Note.CONTENT, entry.content)
      put(MemoryContract.Note.CREATED_AT, entry.createdAt)
    }
    return dbw.insert(MemoryContract.Tables.Note, null, values)
  }

  fun getNotes(projectDir: String): List<NoteEntry> {
    val c = dbr.query(MemoryContract.Tables.Note, null,
      "${MemoryContract.Note.PROJECT_DIR} = ?", arrayOf(projectDir),
      null, null, "${MemoryContract.Note.CREATED_AT} DESC")
    return c.use { it.mapToNotes() }
  }

  fun insertCache(entry: CacheEntry): Long {
    val values = ContentValues().apply {
      put(MemoryContract.Cache.PROJECT_DIR, entry.projectDir)
      put(MemoryContract.Cache.KEY, entry.key)
      put(MemoryContract.Cache.VALUE, entry.value)
      put(MemoryContract.Cache.EXPIRES_AT, entry.expiresAt)
    }
    return dbw.insertWithOnConflict(MemoryContract.Tables.Cache, null, values,
      SQLiteDatabase.CONFLICT_REPLACE)
  }

  fun getCache(projectDir: String, key: String): String? {
    val c = dbr.query(MemoryContract.Tables.Cache, arrayOf(MemoryContract.Cache.VALUE),
      "${MemoryContract.Cache.PROJECT_DIR} = ? AND ${MemoryContract.Cache.KEY} = ? " +
        "AND ${MemoryContract.Cache.EXPIRES_AT} > ?",
      arrayOf(projectDir, key, System.currentTimeMillis().toString()),
      null, null, null)
    c.use { if (it.moveToFirst()) return it.getString(0) }
    return null
  }

  fun cleanupExpiredCache(): Int {
    return dbw.delete(MemoryContract.Tables.Cache,
      "${MemoryContract.Cache.EXPIRES_AT} > 0 AND ${MemoryContract.Cache.EXPIRES_AT} < ?",
      arrayOf(System.currentTimeMillis().toString()))
  }

  fun search(projectDir: String, query: String): List<MemorySearchResult> {
    val results = mutableListOf<MemorySearchResult>()
    val lq = "%$query%"

    var c = dbr.query(MemoryContract.Tables.Conversation, null,
      "${MemoryContract.Conversation.PROJECT_DIR} = ? AND ${MemoryContract.Conversation.CONTENT} LIKE ?",
      arrayOf(projectDir, lq), null, null,
      "${MemoryContract.Conversation.TIMESTAMP} DESC", "100")
    c.use { while (it.moveToNext())
      results.add(MemorySearchResult("conversation", null,
        it.getString(it.getColumnIndexOrThrow(MemoryContract.Conversation.CONTENT)),
        it.getLong(it.getColumnIndexOrThrow(MemoryContract.Conversation.TIMESTAMP)))) }

    c = dbr.query(MemoryContract.Tables.Summary, null,
      "${MemoryContract.Summary.PROJECT_DIR} = ? AND ${MemoryContract.Summary.CONTENT} LIKE ?",
      arrayOf(projectDir, lq), null, null,
      "${MemoryContract.Summary.UPDATED_AT} DESC", "50")
    c.use { while (it.moveToNext())
      results.add(MemorySearchResult("summary",
        it.getString(it.getColumnIndexOrThrow(MemoryContract.Summary.TITLE)),
        it.getString(it.getColumnIndexOrThrow(MemoryContract.Summary.CONTENT)),
        it.getLong(it.getColumnIndexOrThrow(MemoryContract.Summary.UPDATED_AT)))) }

    c = dbr.query(MemoryContract.Tables.Decision, null,
      "${MemoryContract.Decision.PROJECT_DIR} = ? AND (${MemoryContract.Decision.TITLE} LIKE ? OR ${MemoryContract.Decision.CONTENT} LIKE ?)",
      arrayOf(projectDir, lq, lq), null, null,
      "${MemoryContract.Decision.CREATED_AT} DESC", "50")
    c.use { while (it.moveToNext())
      results.add(MemorySearchResult("decision",
        it.getString(it.getColumnIndexOrThrow(MemoryContract.Decision.TITLE)),
        it.getString(it.getColumnIndexOrThrow(MemoryContract.Decision.CONTENT)),
        it.getLong(it.getColumnIndexOrThrow(MemoryContract.Decision.CREATED_AT)))) }

    c = dbr.query(MemoryContract.Tables.Todo, null,
      "${MemoryContract.Todo.PROJECT_DIR} = ? AND ${MemoryContract.Todo.TITLE} LIKE ?",
      arrayOf(projectDir, lq), null, null,
      "${MemoryContract.Todo.CREATED_AT} DESC", "50")
    c.use { while (it.moveToNext())
      results.add(MemorySearchResult("todo",
        it.getString(it.getColumnIndexOrThrow(MemoryContract.Todo.TITLE)),
        it.getString(it.getColumnIndexOrThrow(MemoryContract.Todo.CONTENT)),
        it.getLong(it.getColumnIndexOrThrow(MemoryContract.Todo.CREATED_AT)))) }

    return results
  }

  private fun Cursor.mapToConversations(): List<ConversationEntry> {
    val list = mutableListOf<ConversationEntry>()
    while (moveToNext()) {
      list.add(ConversationEntry(
        id = getLong(getColumnIndexOrThrow(MemoryContract.Conversation.ID)),
        projectDir = getString(getColumnIndexOrThrow(MemoryContract.Conversation.PROJECT_DIR)),
        sessionId = getString(getColumnIndexOrThrow(MemoryContract.Conversation.SESSION_ID)),
        role = getString(getColumnIndexOrThrow(MemoryContract.Conversation.ROLE)),
        content = getString(getColumnIndexOrThrow(MemoryContract.Conversation.CONTENT)),
        tokenCount = getInt(getColumnIndexOrThrow(MemoryContract.Conversation.TOKEN_COUNT)),
        createdAt = getLong(getColumnIndexOrThrow(MemoryContract.Conversation.TIMESTAMP)),
      ))
    }
    return list
  }

  private fun Cursor.mapToSummaries(): List<SummaryEntry> {
    val list = mutableListOf<SummaryEntry>()
    while (moveToNext()) {
      list.add(SummaryEntry(
        id = getLong(getColumnIndexOrThrow(MemoryContract.Summary.ID)),
        projectDir = getString(getColumnIndexOrThrow(MemoryContract.Summary.PROJECT_DIR)),
        type = getString(getColumnIndexOrThrow(MemoryContract.Summary.TYPE)),
        title = getString(getColumnIndexOrThrow(MemoryContract.Summary.TITLE)),
        content = getString(getColumnIndexOrThrow(MemoryContract.Summary.CONTENT)),
        sourceCount = getInt(getColumnIndexOrThrow(MemoryContract.Summary.SOURCE_COUNT)),
        chatCount = getInt(getColumnIndexOrThrow(MemoryContract.Summary.CHAT_COUNT)),
        createdAt = getLong(getColumnIndexOrThrow(MemoryContract.Summary.CREATED_AT)),
        updatedAt = getLong(getColumnIndexOrThrow(MemoryContract.Summary.UPDATED_AT)),
      ))
    }
    return list
  }

  private fun Cursor.mapToDecisions(): List<DecisionEntry> {
    val list = mutableListOf<DecisionEntry>()
    while (moveToNext()) {
      list.add(DecisionEntry(
        id = getLong(getColumnIndexOrThrow(MemoryContract.Decision.ID)),
        projectDir = getString(getColumnIndexOrThrow(MemoryContract.Decision.PROJECT_DIR)),
        title = getString(getColumnIndexOrThrow(MemoryContract.Decision.TITLE)),
        content = getString(getColumnIndexOrThrow(MemoryContract.Decision.CONTENT)),
        status = getString(getColumnIndexOrThrow(MemoryContract.Decision.STATUS)),
        createdAt = getLong(getColumnIndexOrThrow(MemoryContract.Decision.CREATED_AT)),
      ))
    }
    return list
  }

  private fun Cursor.mapToTodos(): List<TodoEntry> {
    val list = mutableListOf<TodoEntry>()
    while (moveToNext()) {
      list.add(TodoEntry(
        id = getLong(getColumnIndexOrThrow(MemoryContract.Todo.ID)),
        projectDir = getString(getColumnIndexOrThrow(MemoryContract.Todo.PROJECT_DIR)),
        title = getString(getColumnIndexOrThrow(MemoryContract.Todo.TITLE)),
        content = getString(getColumnIndexOrThrow(MemoryContract.Todo.CONTENT)),
        done = getInt(getColumnIndexOrThrow(MemoryContract.Todo.DONE)) == 1,
        priority = getInt(getColumnIndexOrThrow(MemoryContract.Todo.PRIORITY)),
        createdAt = getLong(getColumnIndexOrThrow(MemoryContract.Todo.CREATED_AT)),
      ))
    }
    return list
  }

  private fun Cursor.mapToSessions(): List<SessionEntry> {
    val list = mutableListOf<SessionEntry>()
    while (moveToNext()) {
      list.add(SessionEntry(
        id = getLong(getColumnIndexOrThrow(MemoryContract.Session.ID)),
        projectDir = getString(getColumnIndexOrThrow(MemoryContract.Session.PROJECT_DIR)),
        name = getString(getColumnIndexOrThrow(MemoryContract.Session.NAME)),
        startedAt = getLong(getColumnIndexOrThrow(MemoryContract.Session.STARTED_AT)),
        endedAt = getLong(getColumnIndexOrThrow(MemoryContract.Session.ENDED_AT)),
        messageCount = getInt(getColumnIndexOrThrow(MemoryContract.Session.MESSAGE_COUNT)),
      ))
    }
    return list
  }

  private fun Cursor.mapToFacts(): List<FactEntry> {
    val list = mutableListOf<FactEntry>()
    while (moveToNext()) {
      list.add(FactEntry(
        id = getLong(getColumnIndexOrThrow(MemoryContract.Fact.ID)),
        projectDir = getString(getColumnIndexOrThrow(MemoryContract.Fact.PROJECT_DIR)),
        content = getString(getColumnIndexOrThrow(MemoryContract.Fact.CONTENT)),
        source = getString(getColumnIndexOrThrow(MemoryContract.Fact.SOURCE)),
        confidence = getFloat(getColumnIndexOrThrow(MemoryContract.Fact.CONFIDENCE)),
        createdAt = getLong(getColumnIndexOrThrow(MemoryContract.Fact.CREATED_AT)),
      ))
    }
    return list
  }

  private fun Cursor.mapToNotes(): List<NoteEntry> {
    val list = mutableListOf<NoteEntry>()
    while (moveToNext()) {
      list.add(NoteEntry(
        id = getLong(getColumnIndexOrThrow(MemoryContract.Note.ID)),
        projectDir = getString(getColumnIndexOrThrow(MemoryContract.Note.PROJECT_DIR)),
        title = getString(getColumnIndexOrThrow(MemoryContract.Note.TITLE)),
        content = getString(getColumnIndexOrThrow(MemoryContract.Note.CONTENT)),
        createdAt = getLong(getColumnIndexOrThrow(MemoryContract.Note.CREATED_AT)),
      ))
    }
    return list
  }
}