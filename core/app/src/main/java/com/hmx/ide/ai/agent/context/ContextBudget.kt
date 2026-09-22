package com.hmx.ide.ai.agent.context

/**
 * Phase 1 contract only: declares how many characters each context category
 * may consume. Retrieval, ranking, truncation, and compaction belong to
 * Phase 4; this type exists now so the loop and tools are budget-aware
 * from the start instead of being retrofitted later.
 */
data class ContextBudget(
  val systemBudget: Int = 2_000,
  val historyBudget: Int = 4_000,
  val fileBudget: Int = 12_000,
  val toolOutputBudget: Int = 6_000,
  val totalBudget: Int = 24_000,
) {
  /** Returns human-readable violations; empty means valid. */
  fun violations(): List<String> {
    val problems = mutableListOf<String>()
    if (systemBudget < 0) problems.add("systemBudget must be >= 0")
    if (historyBudget < 0) problems.add("historyBudget must be >= 0")
    if (fileBudget < 0) problems.add("fileBudget must be >= 0")
    if (toolOutputBudget < 0) problems.add("toolOutputBudget must be >= 0")
    if (totalBudget <= 0) problems.add("totalBudget must be > 0")
    val parts = systemBudget + historyBudget + fileBudget + toolOutputBudget
    if (parts > totalBudget) {
      problems.add("category budgets ($parts) exceed totalBudget ($totalBudget)")
    }
    return problems
  }

  fun isValid(): Boolean = violations().isEmpty()

  fun requireValid() {
    val problems = violations()
    require(problems.isEmpty()) { "Invalid ContextBudget: ${problems.joinToString("; ")}" }
  }

  fun validated(): ContextBudget = copy(
    systemBudget = systemBudget.coerceAtLeast(0),
    historyBudget = historyBudget.coerceAtLeast(0),
    fileBudget = fileBudget.coerceAtLeast(0),
    toolOutputBudget = toolOutputBudget.coerceAtLeast(0),
    totalBudget = totalBudget.coerceAtLeast(1),
  )
}

/**
 * A bounded request for context. Phase 1 producers (tools) honor [maxChars];
 * Phase 4 ranking/compaction consumes this type instead of replacing it.
 */
data class ContextRequest(
  val purpose: String,
  val maxChars: Int = 8_000,
) {
  init {
    require(maxChars > 0) { "maxChars must be > 0" }
  }
}
