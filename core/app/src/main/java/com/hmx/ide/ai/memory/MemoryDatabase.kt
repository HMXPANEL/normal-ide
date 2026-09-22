package com.hmx.ide.ai.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.hmx.ide.ai.memory.MemoryContract.ALL_TABLES
import com.hmx.ide.ai.memory.MemoryContract.DATABASE_NAME
import com.hmx.ide.ai.memory.MemoryContract.DATABASE_VERSION

class MemoryDatabase(private val appContext: Context, private val dbPath: String) :
    SQLiteOpenHelper(appContext, dbPath, null, DATABASE_VERSION) {

  override fun onCreate(db: SQLiteDatabase) {
    for (table in ALL_TABLES) {
      db.execSQL(getCreateTableSql(table))
    }
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    MigrationManager.applyMigrations(db, oldVersion, appContext)
  }

  override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    onUpgrade(db, oldVersion, newVersion)
  }

  private fun getCreateTableSql(table: String): String {
    return when (table) {
      MemoryContract.Tables.Conversation -> {
        "CREATE TABLE ${MemoryContract.Tables.Conversation} (" +
          "${MemoryContract.Conversation.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.Conversation.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.Conversation.SESSION_ID} TEXT NOT NULL, " +
          "${MemoryContract.Conversation.ROLE} TEXT NOT NULL, " +
          "${MemoryContract.Conversation.CONTENT} TEXT NOT NULL, " +
          "${MemoryContract.Conversation.TIMESTAMP} INTEGER NOT NULL, " +
          "${MemoryContract.Conversation.TOKEN_COUNT} INTEGER DEFAULT 0, " +
          "${MemoryContract.Conversation.CREATED_AT} INTEGER NOT NULL)"
      }
      MemoryContract.Tables.Summary -> {
        "CREATE TABLE ${MemoryContract.Tables.Summary} (" +
          "${MemoryContract.Summary.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.Summary.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.Summary.TYPE} TEXT NOT NULL, " +
          "${MemoryContract.Summary.TITLE} TEXT, " +
          "${MemoryContract.Summary.CONTENT} TEXT NOT NULL, " +
          "${MemoryContract.Summary.SOURCE_COUNT} INTEGER DEFAULT 0, " +
          "${MemoryContract.Summary.CHAT_COUNT} INTEGER DEFAULT 0, " +
          "${MemoryContract.Summary.CREATED_AT} INTEGER NOT NULL, " +
          "${MemoryContract.Summary.UPDATED_AT} INTEGER NOT NULL)"
      }
      MemoryContract.Tables.Decision -> {
        "CREATE TABLE ${MemoryContract.Tables.Decision} (" +
          "${MemoryContract.Decision.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.Decision.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.Decision.TITLE} TEXT NOT NULL, " +
          "${MemoryContract.Decision.CONTENT} TEXT NOT NULL, " +
          "${MemoryContract.Decision.STATUS} TEXT DEFAULT 'pending', " +
          "${MemoryContract.Decision.CREATED_AT} INTEGER NOT NULL)"
      }
      MemoryContract.Tables.Todo -> {
        "CREATE TABLE ${MemoryContract.Tables.Todo} (" +
          "${MemoryContract.Todo.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.Todo.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.Todo.TITLE} TEXT NOT NULL, " +
          "${MemoryContract.Todo.CONTENT} TEXT DEFAULT '', " +
          "${MemoryContract.Todo.DONE} INTEGER DEFAULT 0, " +
          "${MemoryContract.Todo.PRIORITY} INTEGER DEFAULT 0, " +
          "${MemoryContract.Todo.CREATED_AT} INTEGER NOT NULL)"
      }
      MemoryContract.Tables.Preference -> {
        "CREATE TABLE ${MemoryContract.Tables.Preference} (" +
          "${MemoryContract.Preference.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.Preference.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.Preference.KEY} TEXT NOT NULL, " +
          "${MemoryContract.Preference.VALUE} TEXT NOT NULL, " +
          "${MemoryContract.Preference.UPDATED_AT} INTEGER NOT NULL, " +
          "UNIQUE(${MemoryContract.Preference.PROJECT_DIR}, ${MemoryContract.Preference.KEY}))"
      }
      MemoryContract.Tables.Session -> {
        "CREATE TABLE ${MemoryContract.Tables.Session} (" +
          "${MemoryContract.Session.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.Session.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.Session.NAME} TEXT NOT NULL, " +
          "${MemoryContract.Session.STARTED_AT} INTEGER NOT NULL, " +
          "${MemoryContract.Session.ENDED_AT} INTEGER DEFAULT 0, " +
          "${MemoryContract.Session.MESSAGE_COUNT} INTEGER DEFAULT 0)"
      }
      MemoryContract.Tables.Fact -> {
        "CREATE TABLE ${MemoryContract.Tables.Fact} (" +
          "${MemoryContract.Fact.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.Fact.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.Fact.CONTENT} TEXT NOT NULL, " +
          "${MemoryContract.Fact.SOURCE} TEXT DEFAULT '', " +
          "${MemoryContract.Fact.CONFIDENCE} REAL DEFAULT 0.5, " +
          "${MemoryContract.Fact.CREATED_AT} INTEGER NOT NULL)"
      }
      MemoryContract.Tables.Note -> {
        "CREATE TABLE ${MemoryContract.Tables.Note} (" +
          "${MemoryContract.Note.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.Note.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.Note.TITLE} TEXT NOT NULL, " +
          "${MemoryContract.Note.CONTENT} TEXT NOT NULL, " +
          "${MemoryContract.Note.CREATED_AT} INTEGER NOT NULL)"
      }
      MemoryContract.Tables.ProjectKnowledge -> {
        "CREATE TABLE ${MemoryContract.Tables.ProjectKnowledge} (" +
          "${MemoryContract.ProjectKnowledge.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.ProjectKnowledge.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.ProjectKnowledge.CATEGORY} TEXT NOT NULL, " +
          "${MemoryContract.ProjectKnowledge.KEY} TEXT NOT NULL, " +
          "${MemoryContract.ProjectKnowledge.VALUE} TEXT NOT NULL, " +
          "${MemoryContract.ProjectKnowledge.TITLE} TEXT DEFAULT '', " +
          "${MemoryContract.ProjectKnowledge.TAGS} TEXT DEFAULT '', " +
          "${MemoryContract.ProjectKnowledge.CREATED_AT} INTEGER NOT NULL, " +
          "${MemoryContract.ProjectKnowledge.UPDATED_AT} INTEGER NOT NULL, " +
          "UNIQUE(${MemoryContract.ProjectKnowledge.PROJECT_DIR}, " +
            "${MemoryContract.ProjectKnowledge.CATEGORY}, " +
            "${MemoryContract.ProjectKnowledge.KEY}))"
      }
      MemoryContract.Tables.Cache -> {
        "CREATE TABLE ${MemoryContract.Tables.Cache} (" +
          "${MemoryContract.Cache.ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "${MemoryContract.Cache.PROJECT_DIR} TEXT NOT NULL, " +
          "${MemoryContract.Cache.KEY} TEXT NOT NULL, " +
          "${MemoryContract.Cache.VALUE} TEXT NOT NULL, " +
          "${MemoryContract.Cache.EXPIRES_AT} INTEGER DEFAULT 0, " +
          "${MemoryContract.Cache.CREATED_AT} INTEGER NOT NULL, " +
          "UNIQUE(${MemoryContract.Cache.PROJECT_DIR}, ${MemoryContract.Cache.KEY}))"
      }
      else -> ""
    }
  }
}