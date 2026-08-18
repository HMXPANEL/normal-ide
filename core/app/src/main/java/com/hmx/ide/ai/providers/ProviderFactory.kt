package com.hmx.ide.ai.providers

import com.hmx.ide.ai.AiProvider
import com.hmx.ide.ai.providers.claude.ClaudeProvider
import com.hmx.ide.ai.providers.gemini.GeminiProvider
import com.hmx.ide.ai.providers.openai.OpenAiProvider
import com.hmx.ide.ai.registry.ProviderRegistry
import com.hmx.ide.ai.storage.ProviderStorage

object ProviderFactory {

  fun createAll(storage: ProviderStorage? = null): List<AiProvider> = listOf(
    OpenAiProvider("openai", "OpenAI", "https://api.openai.com", storage = storage),
    OpenAiProvider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", storage = storage,
      chatEndpointPath = "chat/completions", modelsEndpointPath = "models"),
    OpenAiProvider("deepseek", "DeepSeek", "https://api.deepseek.com", storage = storage),
    OpenAiProvider("nvidia", "NVIDIA NIM", "https://integrate.api.nvidia.com", storage = storage),
    OpenAiProvider("xai", "xAI (Grok)", "https://api.x.ai", storage = storage),
    OpenAiProvider("mistral", "Mistral", "https://api.mistral.ai", storage = storage),
    OpenAiProvider("groq", "Groq", "https://api.groq.com", storage = storage),
    OpenAiProvider("togetherai", "Together AI", "https://api.together.ai", storage = storage),
    OpenAiProvider("fireworks", "Fireworks AI", "https://api.fireworks.ai", storage = storage),
    OpenAiProvider("opencode", "OpenCode", "https://opencode.ai/zen/v1", storage = storage,
      chatEndpointPath = "chat/completions", modelsEndpointPath = "models"),
    GeminiProvider(storage = storage),
    ClaudeProvider(storage = storage),
  )

  fun registerAll(storage: ProviderStorage? = null) {
    createAll(storage).forEach { ProviderRegistry.register(it) }
  }
}
