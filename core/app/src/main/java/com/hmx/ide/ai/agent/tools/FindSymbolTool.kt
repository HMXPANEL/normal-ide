package com.hmx.ide.ai.agent.tools

import com.hmx.ide.ai.agent.events.FindSymbolInput
import com.hmx.ide.ai.agent.events.SymbolHit
import com.hmx.ide.ai.agent.events.SymbolOutput
import com.hmx.ide.ai.agent.events.ToolFailure
import com.hmx.ide.ai.agent.events.ToolInput
import com.hmx.ide.ai.agent.events.ToolOutput
import com.hmx.ide.ai.agent.permissions.PermissionLevel
import com.hmx.ide.knowledge.KnowledgeEngine

/**
 * READ-only symbol lookup reusing the existing index. No new index is built:
 * exact FQN lookup first, then prefix search. [KnowledgeEngine] is injected
 * (defaults to [com.hmx.ide.knowledge.KnowledgeEngineImpl]) so unit tests
 * use a fake and never touch the singleton or EventBus.
 */
class FindSymbolTool(
  private val engine: KnowledgeEngine,
  private val maxHits: Int = DEFAULT_MAX_HITS,
) : AgentTool {

  override val name: String = NAME
  override val description: String =
    "Find a class/interface/object symbol by fully-qualified name (prefix search as fallback). Returns file locations."
  override val permission: PermissionLevel = PermissionLevel.READ

  override suspend fun execute(input: ToolInput): ToolOutput {
    if (input !is FindSymbolInput) {
      return ToolFailure("find_symbol expects a symbol input")
    }
    if (input.symbol.isBlank()) return ToolFailure("Symbol must not be blank")
    return try {
      val hits = mutableListOf<SymbolHit>()
      val exact = engine.searchSymbol(input.symbol)
      if (exact != null) {
        hits.add(
          SymbolHit(
            symbol = input.symbol,
            filePath = exact.filePath,
            line = exact.line,
            column = exact.column,
          ),
        )
      }
      if (hits.size < maxHits) {
        for (location in engine.searchSymbols(input.symbol)) {
          if (hits.size >= maxHits) break
          if (hits.any { it.filePath == location.filePath && it.line == location.line }) continue
          hits.add(
            SymbolHit(
              symbol = input.symbol,
              filePath = location.filePath,
              line = location.line,
              column = location.column,
            ),
          )
        }
      }
      SymbolOutput(symbol = input.symbol, hits = hits.take(maxHits))
    } catch (t: Throwable) {
      ToolFailure("Symbol search failed: ${t.message}")
    }
  }

  companion object {
    const val NAME = "find_symbol"
    const val DEFAULT_MAX_HITS = 50
  }
}
