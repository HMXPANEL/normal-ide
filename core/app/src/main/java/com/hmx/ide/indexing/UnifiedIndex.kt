package com.hmx.ide.indexing

import com.hmx.ide.ai.context.ProjectIndex
import com.hmx.ide.knowledge.index.SymbolIndex

/**
 * Pure aggregation container for all project index data.
 *
 * This is a data container only. It performs no scanning or analysis; it is produced by an
 * index service and consumed by the Knowledge Engine and the AI Context Pipeline.
 */
data class UnifiedIndex(
  val symbols: SymbolIndex? = null,
  val project: ProjectIndex? = null,
)
