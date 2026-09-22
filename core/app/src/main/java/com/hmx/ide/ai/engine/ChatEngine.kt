package com.hmx.ide.ai.engine

import com.hmx.ide.ai.models.ChatMessage
import com.hmx.ide.ai.models.ChatResponse
import com.hmx.ide.ai.models.Chunk
import com.hmx.ide.ai.models.Role
import com.hmx.ide.ai.pipeline.ContextPipeline
import kotlinx.coroutines.flow.collect

class ChatEngine(
  private val engine: AiEngine,
  private val maxHistory: Int = 50,
) {

  private val messages = mutableListOf<ChatMessage>()

  fun history(): List<ChatMessage> = messages.toList()

  suspend fun send(
    model: String,
    content: String,
    systemPrompt: String? = null,
  ): ChatResponse {
    messages.add(ChatMessage(Role.user, content))
    trimHistory()
    val request = engine.buildChatRequest(model, messages, systemPrompt)
    val response = engine.chat(request)
    messages.add(response.message)
    trimHistory()
    return response
  }

  suspend fun stream(
    model: String,
    content: String,
    systemPrompt: String? = null,
    onChunk: (Chunk) -> Unit = {},
  ): ChatResponse {
    messages.add(ChatMessage(Role.user, content))
    trimHistory()
    val request = engine.buildChatRequest(model, messages, systemPrompt, stream = true)
    val fullContent = StringBuilder()
    engine.stream(request).collect { chunk ->
      fullContent.append(chunk.content)
      onChunk(chunk)
    }
    val assistantMsg = ChatMessage(Role.assistant, fullContent.toString())
    messages.add(assistantMsg)
    trimHistory()
    return ChatResponse(message = assistantMsg)
  }

  suspend fun sendQuery(
    model: String,
    query: String,
    pipeline: ContextPipeline,
  ): ChatResponse {
    val pipelineMessages = pipeline.processQuery(query)
    val systemPrompt = pipelineMessages.firstOrNull { it.role == Role.system }?.content
    val userContent = pipelineMessages.firstOrNull { it.role == Role.user }?.content ?: query
    return send(model, userContent, systemPrompt)
  }

  suspend fun streamQuery(
    model: String,
    query: String,
    pipeline: ContextPipeline,
    onChunk: (Chunk) -> Unit = {},
  ): ChatResponse {
    val pipelineMessages = pipeline.processQuery(query)
    val systemPrompt = pipelineMessages.firstOrNull { it.role == Role.system }?.content
    val userContent = pipelineMessages.firstOrNull { it.role == Role.user }?.content ?: query
    return stream(model, userContent, systemPrompt, onChunk)
  }

  fun clear() { messages.clear() }

  private fun trimHistory() {
    while (messages.size > maxHistory) {
      messages.removeAt(0)
    }
  }
}
