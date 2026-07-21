package com.hmx.ide.activities

data class HttpConfig(
  val url: String,
  val method: String = "GET",
  val headers: Map<String, String> = emptyMap()
)

interface ProviderHandler {
  fun testConnectionConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig
  fun fetchModelsConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig
  fun parseModels(response: String): List<String>
  fun fallbackModels(): List<String>
}

private fun bearerAuth(apiKey: String) = "Bearer $apiKey"

// ---- OpenAI-compatible ----
private class OpenAICompatibleHandler(
  private val defaultBaseUrl: String,
  private val path: String = "/v1/models",
  private val fallback: List<String> = emptyList()
) : ProviderHandler {
  override fun testConnectionConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = resolveUrl(baseUrl, defaultBaseUrl, endpoint, path)
    return HttpConfig(url, headers = mapOf("Authorization" to bearerAuth(apiKey)))
  }
  override fun fetchModelsConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = resolveUrl(baseUrl, defaultBaseUrl, endpoint, path)
    return HttpConfig(url, headers = mapOf("Authorization" to bearerAuth(apiKey)))
  }
  override fun parseModels(response: String): List<String> = parseOpenAiModels(response)
  override fun fallbackModels(): List<String> = fallback
}

// ---- Gemini ----
private class GeminiHandler : ProviderHandler {
  override fun testConnectionConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = "https://generativelanguage.googleapis.com/v1beta/models"
    return HttpConfig(url, headers = mapOf("x-goog-api-key" to apiKey))
  }
  override fun fetchModelsConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = "https://generativelanguage.googleapis.com/v1beta/models"
    return HttpConfig(url, headers = mapOf("x-goog-api-key" to apiKey))
  }
  override fun parseModels(response: String): List<String> = parseGeminiModels(response)
  override fun fallbackModels(): List<String> = listOf(
    "gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-pro", "gemini-1.5-flash"
  )
}

// ---- Claude (Anthropic) ----
private class ClaudeHandler : ProviderHandler {
  override fun testConnectionConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = "https://api.anthropic.com/v1/models"
    return HttpConfig(url, headers = mapOf(
      "x-api-key" to apiKey,
      "anthropic-version" to "2023-06-01"
    ))
  }
  override fun fetchModelsConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = "https://api.anthropic.com/v1/models"
    return HttpConfig(url, headers = mapOf(
      "x-api-key" to apiKey,
      "anthropic-version" to "2023-06-01"
    ))
  }
  override fun parseModels(response: String): List<String> = parseOpenAiModels(response)
  override fun fallbackModels(): List<String> = listOf(
    "claude-sonnet-4-20250514", "claude-3-5-sonnet-latest",
    "claude-3-5-haiku-latest", "claude-3-opus-latest"
  )
}

// ---- Groq ----
private class GroqHandler : ProviderHandler {
  override fun testConnectionConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = "https://api.groq.com/openai/v1/models"
    return HttpConfig(url, headers = mapOf("Authorization" to bearerAuth(apiKey)))
  }
  override fun fetchModelsConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = "https://api.groq.com/openai/v1/models"
    return HttpConfig(url, headers = mapOf("Authorization" to bearerAuth(apiKey)))
  }
  override fun parseModels(response: String): List<String> = parseOpenAiModels(response)
  override fun fallbackModels(): List<String> = listOf(
    "llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768", "gemma2-9b-it"
  )
}

// ---- Together AI ----
private class TogetherAIHandler : ProviderHandler {
  override fun testConnectionConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = "https://api.together.ai/v1/models"
    return HttpConfig(url, headers = mapOf("Authorization" to bearerAuth(apiKey)))
  }
  override fun fetchModelsConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = "https://api.together.ai/v1/models"
    return HttpConfig(url, headers = mapOf("Authorization" to bearerAuth(apiKey)))
  }
  override fun parseModels(response: String): List<String> = parseOpenAiModels(response)
  override fun fallbackModels(): List<String> = emptyList()
}

// ---- OpenCode ----
private class OpenCodeHandler : ProviderHandler {
  override fun testConnectionConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    // baseUrl is already "https://opencode.ai/zen/v1", no extra /v1
    val url = resolveUrl(baseUrl, "https://opencode.ai/zen/v1", endpoint, "/models")
    return HttpConfig(url, headers = mapOf("Authorization" to bearerAuth(apiKey)))
  }
  override fun fetchModelsConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = resolveUrl(baseUrl, "https://opencode.ai/zen/v1", endpoint, "/models")
    return HttpConfig(url, headers = mapOf("Authorization" to bearerAuth(apiKey)))
  }
  override fun parseModels(response: String): List<String> = parseOpenAiModels(response)
  override fun fallbackModels(): List<String> = emptyList()
}

// ---- Fireworks AI ----
private class FireworksHandler : ProviderHandler {
  // Fireworks uses /v1/accounts/{account_id}/models — not easily discoverable.
  // We hit /v1/models for connectivity test; model listing requires account_id.
  override fun testConnectionConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = resolveUrl(baseUrl, "https://api.fireworks.ai", endpoint, "/v1/models")
    return HttpConfig(url, headers = mapOf("Authorization" to bearerAuth(apiKey)))
  }
  override fun fetchModelsConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    // Try the generic endpoint; if it fails fallback will be used
    val url = resolveUrl(baseUrl, "https://api.fireworks.ai", endpoint, "/v1/models")
    return HttpConfig(url, headers = mapOf("Authorization" to bearerAuth(apiKey)))
  }
  override fun parseModels(response: String): List<String> = parseOpenAiModels(response)
  override fun fallbackModels(): List<String> = emptyList()
}

// ---- Ollama ----
private class OllamaHandler : ProviderHandler {
  override fun testConnectionConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = resolveUrl(baseUrl, "http://localhost:11434", endpoint, "/api/tags")
    return HttpConfig(url, headers = emptyMap())
  }
  override fun fetchModelsConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = resolveUrl(baseUrl, "http://localhost:11434", endpoint, "/api/tags")
    return HttpConfig(url, headers = emptyMap())
  }
  override fun parseModels(response: String): List<String> = parseOllamaModels(response)
  override fun fallbackModels(): List<String> = emptyList()
}

// ---- DeepSeek ----
private class DeepSeekHandler : OpenAICompatibleHandler(
  defaultBaseUrl = "https://api.deepseek.com",
  fallback = listOf("deepseek-chat", "deepseek-reasoner")
)

// ---- NVIDIA NIM ----
private class NvidiaHandler : OpenAICompatibleHandler(
  defaultBaseUrl = "https://integrate.api.nvidia.com",
  path = "/v1/models",
  fallback = listOf("meta/llama-3.1-405b-instruct", "mistralai/mistral-large-2-instruct")
)

// ---- xAI ----
private class XaiHandler : OpenAICompatibleHandler(
  defaultBaseUrl = "https://api.x.ai",
  fallback = listOf("grok-2-latest", "grok-beta")
)

// ---- Mistral ----
private class MistralHandler : OpenAICompatibleHandler(
  defaultBaseUrl = "https://api.mistral.ai",
  fallback = listOf("mistral-large-latest", "mistral-medium-latest",
    "mistral-small-latest", "codestral-latest")
)

// ---- Custom (OpenAI Compatible) ----
private class CustomHandler : OpenAICompatibleHandler(
  defaultBaseUrl = "",
  fallback = emptyList()
)

// ---- Resolve URL ----
private fun resolveUrl(
  userBaseUrl: String,
  defaultBaseUrl: String,
  userEndpoint: String,
  defaultPath: String
): String {
  val base = when {
    userBaseUrl.isNotBlank() -> userBaseUrl.trimEnd('/')
    defaultBaseUrl.isNotBlank() -> defaultBaseUrl.trimEnd('/')
    else -> ""
  }
  val path = when {
    userEndpoint.isNotBlank() -> "/${userEndpoint.trimStart('/')}"
    else -> defaultPath
  }
  return "$base$path"
}

// ---- Parsing ----
internal fun parseOpenAiModels(response: String): List<String> {
  val models = mutableListOf<String>()
  val regex = "\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
  regex.findAll(response).forEach { models.add(it.groupValues[1]) }
  return models
}

internal fun parseOllamaModels(response: String): List<String> {
  val models = mutableListOf<String>()
  val regex = "\"name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
  regex.findAll(response).forEach { models.add(it.groupValues[1]) }
  return models
}

internal fun parseGeminiModels(response: String): List<String> {
  // Gemini returns {"models": [{"name": "models/gemini-2.0-flash", ...}]}
  // Extract name and strip "models/" prefix
  val models = mutableListOf<String>()
  val regex = "\"name\"\\s*:\\s*\"models/([^\"]+)\"".toRegex()
  regex.findAll(response).forEach { models.add(it.groupValues[1]) }
  return models
}

// ---- Provider registry ----
internal fun providerHandler(providerId: String): ProviderHandler = when (providerId) {
  "gemini" -> GeminiHandler()
  "claude" -> ClaudeHandler()
  "openai" -> OpenAICompatibleHandler("https://api.openai.com",
    fallback = listOf("gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini",
      "gpt-4.1-nano", "o3", "o4-mini"))
  "openrouter" -> OpenAICompatibleHandler("https://openrouter.ai/api/v1",
    fallback = emptyList())
  "nvidia" -> NvidiaHandler()
  "groq" -> GroqHandler()
  "deepseek" -> DeepSeekHandler()
  "mistral" -> MistralHandler()
  "togetherai" -> TogetherAIHandler()
  "fireworks" -> FireworksHandler()
  "xai" -> XaiHandler()
  "ollama" -> OllamaHandler()
  "opencode" -> OpenCodeHandler()
  "custom" -> CustomHandler()
  else -> OpenAICompatibleHandler("")
}
