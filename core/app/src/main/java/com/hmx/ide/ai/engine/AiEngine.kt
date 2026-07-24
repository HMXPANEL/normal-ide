package com.hmx.ide.ai.engine

import com.hmx.ide.ai.AiProvider
import com.hmx.ide.ai.errors.ProviderException
import com.hmx.ide.ai.models.AiModel
import com.hmx.ide.ai.models.ChatMessage
import com.hmx.ide.ai.models.ChatRequest
import com.hmx.ide.ai.models.ChatResponse
import com.hmx.ide.ai.models.Chunk
import com.hmx.ide.ai.registry.ProviderRegistry
import com.hmx.ide.ai.storage.ProviderStorage
import kotlinx.coroutines.flow.Flow

class AiEngine(
  private val storage: ProviderStorage? = null,
) {

  fun activeProvider(): AiProvider =
    ProviderRegistry.active() ?: throw ProviderException("No active provider selected")

  val providers: List<AiProvider>
    get() = ProviderRegistry.all()

  suspend fun chat(request: ChatRequest): ChatResponse =
    activeProvider().chat(request)

  fun stream(request: ChatRequest): Flow<Chunk> =
    activeProvider().stream(request)

  suspend fun listModels(): List<AiModel> =
    activeProvider().listModels()

  suspend fun testConnection(): Boolean =
    activeProvider().testConnection()

  fun switchProvider(providerId: String) {
    ProviderRegistry.get(providerId)
      ?: throw ProviderException("Provider '$providerId' not registered")
    storage?.setActiveProviderId(providerId)
  }

  fun buildChatRequest(
    model: String,
    messages: List<ChatMessage>,
    systemPrompt: String? = null,
    stream: Boolean = false,
  ): ChatRequest = activeProvider().buildChatRequest(model, messages, systemPrompt, stream)
}
