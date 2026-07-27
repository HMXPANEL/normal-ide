package com.hmx.ide.ai.memory

object MemoryContract {

  const val DATABASE_VERSION = 1
  const val DATABASE_NAME = "memory.db"

  object Tables {
    const val CONVERSATION = "conversation"
    const val SUMMARY = "summary"
    const val DECISION = "decision"
    const val TODO = "todo"
    const val PREFERENCE = "preference"
    const val SESSION = "session"
    const val FACT = "fact"
    const val NOTE = "note"
    const val CACHE = "cache"
  }

  object Conversation {
    const val TABLE = "conversation"
    const val ID = "_id"
    const val PROJECT_DIR = "project_dir"
    const val SESSION_ID = "session_id"
    const val ROLE = "role"
    const val CONTENT = "content"
    const val TIMESTAMP = "timestamp"
    const val TOKEN_COUNT = "token_count"
    const val CREATED_AT = "created_at"
  }

  object Summary {
    const val TABLE = "summary"
    const val ID = "_id"
    const val PROJECT_DIR = "project_dir"
    const val TYPE = "type"
    const val TITLE = "title"
    const val CONTENT = "content"
    const val SOURCE_COUNT = "source_count"
    const val CHAT_COUNT = "chat_count"
    const val CREATED_AT = "created_at"
    const val UPDATED_AT = "updated_at"
  }

  object Decision {
    const val TABLE = "decision"
    const val ID = "_id"
    const val PROJECT_DIR = "project_dir"
    const val TITLE = "title"
    const val CONTENT = "content"
    const val STATUS = "status"
    const val CREATED_AT = "created_at"
  }

  object Todo {
    const val TABLE = "todo"
    const val ID = "_id"
    const val PROJECT_DIR = "project_dir"
    const val TITLE = "title"
    const val CONTENT = "content"
    const val DONE = "done"
    const val PRIORITY = "priority"
    const val CREATED_AT = "created_at"
  }

  object Preference {
    const val TABLE = "preference"
    const val ID = "_id"
    const val PROJECT_DIR = "project_dir"
    const val KEY = "key"
    const val VALUE = "value"
    const val UPDATED_AT = "updated_at"
  }

  object Session {
    const val TABLE = "session"
    const val ID = "_id"
    const val PROJECT_DIR = "project_dir"
    const val NAME = "name"
    const val STARTED_AT = "started_at"
    const val ENDED_AT = "ended_at"
    const val MESSAGE_COUNT = "message_count"
  }

  object Fact {
    const val TABLE = "fact"
    const val ID = "_id"
    const val PROJECT_DIR = "project_dir"
    const val CONTENT = "content"
    const val SOURCE = "source"
    const val CONFIDENCE = "confidence"
    const val CREATED_AT = "created_at"
  }

  object Note {
    const val TABLE = "note"
    const val ID = "_id"
    const val PROJECT_DIR = "project_dir"
    const val TITLE = "title"
    const val CONTENT = "content"
    const val CREATED_AT = "created_at"
  }

  object Cache {
    const val TABLE = "cache"
    const val ID = "_id"
    const val PROJECT_DIR = "project_dir"
    const val KEY = "key"
    const val VALUE = "value"
    const val EXPIRES_AT = "expires_at"
    const val CREATED_AT = "created_at"
  }

  val ALL_TABLES = arrayOf(
    Conversation.TABLE,
    Summary.TABLE,
    Decision.TABLE,
    Todo.TABLE,
    Preference.TABLE,
    Session.TABLE,
    Fact.TABLE,
    Note.TABLE,
    Cache.TABLE,
  )
}
