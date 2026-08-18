package com.hmx.ide.ai.memory

data class MemoryEntry(
  val id: Long = 0,
  val projectDir: String,
  val type: String,
  val title: String? = null,
  val content: String,
  val metadata: String = "",
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
)

data class ConversationEntry(
  val id: Long = 0,
  val projectDir: String,
  val sessionId: String,
  val role: String,
  val content: String,
  val tokenCount: Int = 0,
  val createdAt: Long = System.currentTimeMillis(),
)

data class SummaryEntry(
  val id: Long = 0,
  val projectDir: String,
  val type: String,
  val title: String,
  val content: String,
  val sourceCount: Int = 0,
  val chatCount: Int = 0,
  val createdAt: Long = 0,
  val updatedAt: Long = 0,
)

data class DecisionEntry(
  val id: Long = 0,
  val projectDir: String,
  val title: String,
  val content: String,
  val status: String = "pending",
  val createdAt: Long = System.currentTimeMillis(),
)

data class TodoEntry(
  val id: Long = 0,
  val projectDir: String,
  val title: String,
  val content: String = "",
  val done: Boolean = false,
  val priority: Int = 0,
  val createdAt: Long = System.currentTimeMillis(),
)

data class PreferenceEntry(
  val id: Long = 0,
  val projectDir: String,
  val key: String,
  val value: String,
  val updatedAt: Long = System.currentTimeMillis(),
)

data class SessionEntry(
  val id: Long = 0,
  val projectDir: String,
  val name: String,
  val startedAt: Long = System.currentTimeMillis(),
  val endedAt: Long = 0,
  val messageCount: Int = 0,
)

data class FactEntry(
  val id: Long = 0,
  val projectDir: String,
  val content: String,
  val source: String = "",
  val confidence: Float = 0.5f,
  val createdAt: Long = System.currentTimeMillis(),
)

data class NoteEntry(
  val id: Long = 0,
  val projectDir: String,
  val title: String,
  val content: String,
  val createdAt: Long = System.currentTimeMillis(),
)

data class CacheEntry(
  val id: Long = 0,
  val projectDir: String,
  val key: String,
  val value: String,
  val expiresAt: Long = 0,
  val createdAt: Long = System.currentTimeMillis(),
)

data class MemorySearchResult(
  val type: String,
  val title: String?,
  val content: String,
  val createdAt: Long,
  val relevance: Float = 0f,
)