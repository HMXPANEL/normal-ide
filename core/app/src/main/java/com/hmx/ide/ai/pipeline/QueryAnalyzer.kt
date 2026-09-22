package com.hmx.ide.ai.pipeline

enum class ContextNeed {
  CURRENT_FILE,
  PROJECT_STRUCTURE,
  PROJECT_MEMORY,
}

object QueryAnalyzer {
  fun analyze(query: String): Set<ContextNeed> {
    val q = query.lowercase()
    val needs = mutableSetOf<ContextNeed>()

    if (q.containsAny("bug", "fix", "error", "issue", "crash", "broken", "wrong", "fail"))
      needs.addAll(listOf(ContextNeed.CURRENT_FILE, ContextNeed.PROJECT_MEMORY))
    if (q.containsAny("class", "method", "function", "explain", "what does", "code"))
      needs.add(ContextNeed.CURRENT_FILE)
    if (q.containsAny("architecture", "structure", "overview", "module", "dependency", "how is", "gradle", "build", "compile", "sdk", "version"))
      needs.addAll(listOf(ContextNeed.PROJECT_STRUCTURE, ContextNeed.PROJECT_MEMORY))
    if (q.containsAny("refactor", "change", "modify", "add", "remove", "update", "rewrite"))
      needs.addAll(listOf(ContextNeed.CURRENT_FILE, ContextNeed.PROJECT_STRUCTURE))
    if (q.containsAny("remember", "previous", "before", "last time", "what did we"))
      needs.add(ContextNeed.PROJECT_MEMORY)

    if (needs.isEmpty())
      needs.addAll(listOf(ContextNeed.CURRENT_FILE, ContextNeed.PROJECT_STRUCTURE))
    return needs
  }

  private fun String.containsAny(vararg words: String): Boolean =
    words.any { this.contains(it) }
}
