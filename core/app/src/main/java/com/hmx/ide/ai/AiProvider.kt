package com.hmx.ide.ai

import com.hmx.ide.ai.errors.AiException
import com.hmx.ide.ai.errors.NetworkException
import com.hmx.ide.ai.models.AiModel
import com.hmx.ide.ai.models.Capability
import com.hmx.ide.ai.models.ChatMessage
import com.hmx.ide.ai.models.ChatRequest
import com.hmx.ide.ai.models.ChatResponse
import com.hmx.ide.ai.models.Chunk
import kotlinx.coroutines.flow.Flow

interface AiProvider {

  val providerId: String
  val displayName: String
  val capabilities: Set<Capability>

  suspend fun chat(request: ChatRequest): ChatResponse

  fun stream(request: ChatRequest): Flow<Chunk>

  suspend fun listModels(): List<AiModel>

  suspend fun testConnection(): Boolean

  fun buildChatRequest(
    model: String,
    messages: List<ChatMessage>,
    systemPrompt: String? = null,
    stream: Boolean = false,
  ): ChatRequest = ChatRequest(
    model = model,
    messages = messages,
    systemPrompt = systemPrompt,
    stream = stream,
  )

  fun handleError(throwable: Throwable): AiException =
    when (throwable) {
      is AiException -> throwable
      else -> NetworkException(throwable.message ?: "Unknown error", throwable)
    }
}
