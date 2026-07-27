package com.hmx.ide.ai.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.io.FileReader

object MigrationManager {

  private val gson = Gson()

  data class Migration(
    val version: Int,
    val migrate: (SQLiteDatabase) -> Unit,
  )

  private val migrations = listOf<Migration>(
    Migration(1) { db ->
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS ${MemoryContract.Tables.Conversation} (" +
          "${MemoryContract.Conversation.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.Conversation.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.Conversation.SESSION_ID} TEXT NOT NULL, " +
          "${MemoryContract.Conversation.ROLE} TEXT NOT NULL, " +
          "${MemoryContract.Conversation.CONTENT} TEXT NOT NULL, " +
          "${MemoryContract.Conversation.TIMESTAMP} INTEGER NOT NULL, " +
          "${MemoryContract.Conversation.TOKEN_COUNT} INTEGER DEFAULT 0, " +
          "${MemoryContract.Conversation.CREATED_AT} INTEGER NOT NULL)"
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS idx_conversation_project ON ${MemoryContract.Tables.Conversation}(${MemoryContract.Conversation.PROJECT_DIR})"
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS idx_conversation_session ON ${MemoryContract.Tables.Conversation}(${MemoryContract.Conversation.SESSION_ID})"
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS idx_conversation_timestamp ON ${MemoryContract.Tables.Conversation}(${MemoryContract.Conversation.TIMESTAMP})"
      )
    },
    Migration(1) { db ->
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS ${MemoryContract.Tables.Summary} (" +
          "${MemoryContract.Summary.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.Summary.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.Summary.TYPE} TEXT NOT NULL, " +
          "${MemoryContract.Summary.TITLE} TEXT, " +
          "${MemoryContract.Summary.CONTENT} TEXT NOT NULL, " +
          "${MemoryContract.Summary.SOURCE_COUNT} INTEGER DEFAULT 0, " +
          "${MemoryContract.Summary.CHAT_COUNT} INTEGER DEFAULT 0, " +
          "${MemoryContract.Summary.CREATED_AT} INTEGER NOT NULL, " +
          "${MemoryContract.Summary.UPDATED_AT} INTEGER NOT NULL)"
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS idx_summary_project ON ${MemoryContract.Tables.Summary}(${MemoryContract.Summary.PROJECT_DIR})"
      )
    },
    Migration(1) { db ->
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS ${MemoryContract.Tables.Decision} (" +
          "${MemoryContract.Decision.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.Decision.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.Decision.TITLE} TEXT NOT NULL, " +
          "${MemoryContract.Decision.CONTENT} TEXT NOT NULL, " +
          "${MemoryContract.Decision.STATUS} TEXT DEFAULT 'pending', " +
          "${MemoryContract.Decision.CREATED_AT} INTEGER NOT NULL)"
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS idx_decision_project ON ${MemoryContract.Tables.Decision}(${MemoryContract.Decision.PROJECT_DIR})"
      )
    },
    Migration(1) { db ->
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS ${MemoryContract.Tables.Todo} (" +
          "${MemoryContract.Todo.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.Todo.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.Todo.TITLE} TEXT NOT NULL, " +
          "${MemoryContract.Todo.CONTENT} TEXT DEFAULT '', " +
          "${MemoryContract.Todo.DONE} INTEGER DEFAULT 0, " +
          "${MemoryContract.Todo.PRIORITY} INTEGER DEFAULT 0, " +
          "${MemoryContract.Todo.CREATED_AT} INTEGER NOT NULL)"
      )
    },
    Migration(1) { db ->
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS ${MemoryContract.Tables.Preference} (" +
          "${MemoryContract.Preference.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.Preference.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.Preference.KEY} TEXT NOT NULL, " +
          "${MemoryContract.Preference.VALUE} TEXT NOT NULL, " +
          "${MemoryContract.Preference.UPDATED_AT} INTEGER NOT NULL, " +
          "UNIQUE(${MemoryContract.Preference.PROJECT_DIR}, ${MemoryContract.Preference.KEY}))"
      )
    },
    Migration(1) { db ->
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS ${MemoryContract.Tables.Session} (" +
          "${MemoryContract.Session.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.Session.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.Session.NAME} TEXT NOT NULL, " +
          "${MemoryContract.Session.STARTED_AT} INTEGER NOT NULL, " +
          "${MemoryContract.Session.ENDED_AT} INTEGER DEFAULT 0, " +
          "${MemoryContract.Session.MESSAGE_COUNT} INTEGER DEFAULT 0)"
      )
    },
    Migration(1) { db ->
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS ${MemoryContract.Tables.Fact} (" +
          "${MemoryContract.Fact.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.Fact.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.Fact.CONTENT} TEXT NOT NULL, " +
          "${MemoryContract.Fact.SOURCE} TEXT DEFAULT '', " +
          "${MemoryContract.Fact.CONFIDENCE} REAL DEFAULT 0.5, " +
          "${MemoryContract.Fact.CREATED_AT} INTEGER NOT NULL)"
      )
    },
    Migration(1) { db ->
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS ${MemoryContract.Tables.Note} (" +
          "${MemoryContract.Note.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.Note.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.Note.TITLE} TEXT NOT NULL, " +
          "${MemoryContract.Note.CONTENT} TEXT NOT NULL, " +
          "${MemoryContract.Note.CREATED_AT} INTEGER NOT NULL)"
      )
    },
    Migration(1) { db ->
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS ${MemoryContract.Tables.Cache} (" +
          "${MemoryContract.Cache.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.Cache.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.Cache.KEY} TEXT NOT NULL, " +
          "${MemoryContract.Cache.VALUE} TEXT NOT NULL, " +
          "${MemoryContract.Cache.EXPIRES_AT} INTEGER DEFAULT 0, " +
          "${MemoryContract.Cache.CREATED_AT} INTEGER NOT NULL, " +
          "UNIQUE(${MemoryContract.Cache.PROJECT_DIR}, ${MemoryContract.Cache.KEY}))"
      )
    },
  )

  fun getVersion(context: Context): Int {
    val hmxDir = HmxFolder.getHmxDir(File(context.applicationContext.applicationInfo.dataDir.split("/").dropLast(1).joinToString("/")))
    val versionFile = HmxFolder.getVersionFile(hmxDir)
    if (!versionFile.exists()) return 0
    return runCatching {
      val json = gson.fromJson(FileReader(versionFile), JsonObject::class.java)
      json.get("schemaVersion").asInt
    }.getOrDefault(0)
  }

  fun saveVersion(context: Context, version: Int) {
    val hmxDir = HmxFolder.getHmxDir(File(context.applicationContext.applicationInfo.dataDir.split("/").dropLast(1).joinToString("/")))
    val versionFile = HmxFolder.getVersionFile(hmxDir)
    val json = JsonObject().apply {
      addProperty("schemaVersion", version)
      addProperty("lastMigrated", version)
      addProperty("updatedAt", System.currentTimeMillis())
    }
    versionFile.writeText(gson.toJson(json))
  }

  fun applyMigrations(db: SQLiteDatabase, fromVersion: Int, context: Context): Int {
    var currentVersion = fromVersion
    for (migration in migrations) {
      if (migration.version > fromVersion) {
        try {
          db.beginTransaction()
          migration.migrate(db)
          db.setTransactionSuccessful()
          currentVersion = migration.version
        } finally {
          db.endTransaction()
        }
      }
    }
    return currentVersion
  }
}
