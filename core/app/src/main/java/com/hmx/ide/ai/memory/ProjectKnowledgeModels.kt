package com.hmx.ide.ai.memory

data class KnowledgeEntry(
  val id: Long = 0,
  val projectDir: String,
  val category: KnowledgeCategory,
  val key: String,
  val value: String,
  val title: String = "",
  val tags: List<String> = emptyList(),
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
)

enum class KnowledgeCategory(val label: String) {
  ARCHITECTURE("architecture"),
  FIX("fix"),
  IMPORTANT_FILE("important_file"),
  DOC("doc"),
  PREFERENCE("preference");
}
