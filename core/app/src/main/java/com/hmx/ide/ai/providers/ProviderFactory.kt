package com.hmx.ide.ai.providers

import com.hmx.ide.ai.AiProvider
import com.hmx.ide.ai.providers.claude.ClaudeProvider
import com.hmx.ide.ai.providers.gemini.GeminiProvider
import com.hmx.ide.ai.providers.ollama.OllamaProvider
import com.hmx.ide.ai.providers.openai.OpenAiProvider
import com.hmx.ide.ai.registry.ProviderRegistry
import com.hmx.ide.ai.storage.ProviderStorage

object ProviderFactory {

  fun createAll(storage: ProviderStorage? = null): List<AiProvider> = listOf(
    OpenAiProvider("openai", "OpenAI",
      storedBaseUrl(storage, "openai", "https://api.openai.com"),
      apiKey = storedApiKey(storage, "openai"),
      defaultModels = listOf("gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini",
        "gpt-4.1-nano", "o3", "o4-mini")),
    OpenAiProvider("openrouter", "OpenRouter",
      storedBaseUrl(storage, "openrouter", "https://openrouter.ai/api/v1"),
      apiKey = storedApiKey(storage, "openrouter")),
    OpenAiProvider("deepseek", "DeepSeek",
      storedBaseUrl(storage, "deepseek", "https://api.deepseek.com"),
      apiKey = storedApiKey(storage, "deepseek"),
      defaultModels = listOf("deepseek-chat", "deepseek-reasoner")),
    OpenAiProvider("nvidia", "NVIDIA NIM",
      storedBaseUrl(storage, "nvidia", "https://integrate.api.nvidia.com"),
      apiKey = storedApiKey(storage, "nvidia"),
      defaultModels = listOf("meta/llama-3.1-405b-instruct", "mistralai/mistral-large-2-instruct")),
    OpenAiProvider("xai", "xAI (Grok)",
      storedBaseUrl(storage, "xai", "https://api.x.ai"),
      apiKey = storedApiKey(storage, "xai"),
      defaultModels = listOf("grok-2-latest", "grok-beta")),
    OpenAiProvider("mistral", "Mistral",
      storedBaseUrl(storage, "mistral", "https://api.mistral.ai"),
      apiKey = storedApiKey(storage, "mistral"),
      defaultModels = listOf("mistral-large-latest", "mistral-medium-latest",
        "mistral-small-latest", "codestral-latest")),
    OpenAiProvider("groq", "Groq",
      storedBaseUrl(storage, "groq", "https://api.groq.com"),
      apiKey = storedApiKey(storage, "groq"),
      defaultModels = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant",
        "mixtral-8x7b-32768", "gemma2-9b-it")),
    OpenAiProvider("togetherai", "Together AI",
      storedBaseUrl(storage, "togetherai", "https://api.together.ai"),
      apiKey = storedApiKey(storage, "togetherai")),
    OpenAiProvider("fireworks", "Fireworks AI",
      storedBaseUrl(storage, "fireworks", "https://api.fireworks.ai"),
      apiKey = storedApiKey(storage, "fireworks")),
    OpenAiProvider("opencode", "OpenCode",
      storedBaseUrl(storage, "opencode", "https://opencode.ai/zen/v1"),
      apiKey = storedApiKey(storage, "opencode")),
    GeminiProvider(apiKey = storedApiKey(storage, "gemini")),
    ClaudeProvider(apiKey = storedApiKey(storage, "claude")),
    OllamaProvider(),
  )

  fun registerAll(storage: ProviderStorage? = null) {
    createAll(storage).forEach { ProviderRegistry.register(it) }
  }

  private fun storedBaseUrl(storage: ProviderStorage?, id: String, default: String): String =
    storage?.getBaseUrl(id)?.ifBlank { default } ?: default

  private fun storedApiKey(storage: ProviderStorage?, id: String): String =
    storage?.getApiKey(id) ?: ""
}
