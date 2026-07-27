package com.hmx.ide.ai.pipeline

import com.hmx.ide.ai.context.ContextManager
import com.hmx.ide.ai.context.EditorContext
import com.hmx.ide.ai.context.PromptBuilder
import com.hmx.ide.ai.context.ProjectIndex
import com.hmx.ide.ai.memory.MemoryManager
import com.hmx.ide.ai.memory.MemorySearchResult
import com.hmx.ide.ai.memory.MemoryService
import com.hmx.ide.ai.memory.MemoryService.withProject
import java.io.File

class ContextPipeline(
  private val projectDir: File,
) {

  private val index: ProjectIndex by lazy {
    com.hmx.ide.ai.context.ContextCache.getOrAnalyze(projectDir.absolutePath)
  }

  private val memoryManager: MemoryManager by lazy {
    MemoryService.withProject(projectDir)
  }

  fun buildSystemPrompt(currentFile: String? = null): String {
    return PromptBuilder.build(index, currentFile)
  }

  fun buildSystemPromptWithMemory(currentFile: String? = null): String {
    val base = buildSystemPrompt(currentFile)
    val memoryHint = buildMemoryHint()
    if (memoryHint.isEmpty()) return base
    return "$base\n\n$memoryHint"
  }

  fun buildFullContext(currentFile: String? = null): PipelineContext {
    return PipelineContext(
      projectIndex = index,
      editorContext = ContextManager.collectContext(),
      memorySummary = buildMemorySummary(),
      relevantMemory = emptyList(),
      systemPrompt = buildSystemPromptWithMemory(currentFile),
    )
  }

  fun findRelevantMemory(query: String): List<MemorySearchResult> {
    return memoryManager.search(query)
  }

  private fun buildMemoryHint(): String {
    val summaries = withProject(projectDir).getSummaries()
    if (summaries.isEmpty()) return ""
    val sb = StringBuilder()
    sb.append("=== PROJECT MEMORY ===")
    for (s in summaries.take(5)) {
      val content = s.content.take(200)
      sb.append("- ${s.title ?: "Summary"}: $content")
    }
    return sb.toString()
  }

  private fun buildMemorySummary(): String {
    val mm = withProject(projectDir)
    val stats = mm.getMemoryStats()
    val decisions = mm.getDecisions("done").take(10)
    val todos = mm.getTodos().filter { !it.done }.take(10)

    val sb = StringBuilder()
    sb.append("Project has ${stats.totalEntries} memory entries.")
    if (decisions.isNotEmpty()) {
      sb.append(" Recent decisions:")
      for (d in decisions) {
        sb.append(" - ${d.title}")
      }
    }
    if (todos.isNotEmpty()) {
      sb.append(" Open TODOs:")
      for (t in todos) {
        sb.append(" - ${t.title}")
      }
    }
    return sb.toString()
  }

  fun cleanupMemory() {
    withProject(projectDir).let { mm ->
      mm.cleanupOldConversations()
      mm.cleanupExpiredCache()
      mm.compactDatabase()
    }
  }
}

data class PipelineContext(
  val projectIndex: ProjectIndex,
  val editorContext: EditorContext,
  val memorySummary: String,
  val relevantMemory: List<MemorySearchResult>?,
  val systemPrompt: String,
)