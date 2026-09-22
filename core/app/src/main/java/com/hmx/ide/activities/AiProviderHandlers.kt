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
}

private fun bearerAuth(apiKey: String) = "Bearer $apiKey"

// ---- OpenAI-compatible ----
private open class OpenAICompatibleHandler(
  private val defaultBaseUrl: String,
  private val path: String = "/v1/models"
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
}

// ---- Gemini ----
private class GeminiHandler : ProviderHandler {
  override fun testConnectionConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = resolveUrl(baseUrl, "https://generativelanguage.googleapis.com", endpoint, "/v1beta/models")
    return HttpConfig(url, headers = mapOf("x-goog-api-key" to apiKey))
  }
  override fun fetchModelsConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = resolveUrl(baseUrl, "https://generativelanguage.googleapis.com", endpoint, "/v1beta/models")
    return HttpConfig(url, headers = mapOf("x-goog-api-key" to apiKey))
  }
  override fun parseModels(response: String): List<String> = parseGeminiModels(response)
}

// ---- Claude (Anthropic) ----
private class ClaudeHandler : ProviderHandler {
  override fun testConnectionConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = resolveUrl(baseUrl, "https://api.anthropic.com", endpoint, "/v1/models")
    return HttpConfig(url, headers = mapOf(
      "x-api-key" to apiKey,
      "anthropic-version" to "2023-06-01"
    ))
  }
  override fun fetchModelsConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = resolveUrl(baseUrl, "https://api.anthropic.com", endpoint, "/v1/models")
    return HttpConfig(url, headers = mapOf(
      "x-api-key" to apiKey,
      "anthropic-version" to "2023-06-01"
    ))
  }
  override fun parseModels(response: String): List<String> = parseOpenAiModels(response)
}

// ---- Groq ----
private class GroqHandler : ProviderHandler {
  override fun testConnectionConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = resolveUrl(baseUrl, "https://api.groq.com", endpoint, "/openai/v1/models")
    return HttpConfig(url, headers = mapOf("Authorization" to bearerAuth(apiKey)))
  }
  override fun fetchModelsConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = resolveUrl(baseUrl, "https://api.groq.com", endpoint, "/openai/v1/models")
    return HttpConfig(url, headers = mapOf("Authorization" to bearerAuth(apiKey)))
  }
  override fun parseModels(response: String): List<String> = parseOpenAiModels(response)
}

// ---- Together AI ----
private class TogetherAIHandler : ProviderHandler {
  override fun testConnectionConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = resolveUrl(baseUrl, "https://api.together.ai", endpoint, "/v1/models")
    return HttpConfig(url, headers = mapOf("Authorization" to bearerAuth(apiKey)))
  }
  override fun fetchModelsConfig(apiKey: String, baseUrl: String, endpoint: String): HttpConfig {
    val url = resolveUrl(baseUrl, "https://api.together.ai", endpoint, "/v1/models")
    return HttpConfig(url, headers = mapOf("Authorization" to bearerAuth(apiKey)))
  }
  override fun parseModels(response: String): List<String> = parseOpenAiModels(response)
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
    val url = resolveUrl(baseUrl, "https://api.fireworks.ai", endpoint, "/v1/models")
    return HttpConfig(url, headers = mapOf("Authorization" to bearerAuth(apiKey)))
  }
  override fun parseModels(response: String): List<String> = parseOpenAiModels(response)
}

// ---- DeepSeek ----
private class DeepSeekHandler : OpenAICompatibleHandler(
  defaultBaseUrl = "https://api.deepseek.com"
)

// ---- NVIDIA NIM ----
private class NvidiaHandler : OpenAICompatibleHandler(
  defaultBaseUrl = "https://integrate.api.nvidia.com"
)

// ---- xAI ----
private class XaiHandler : OpenAICompatibleHandler(
  defaultBaseUrl = "https://api.x.ai"
)

// ---- Mistral ----
private class MistralHandler : OpenAICompatibleHandler(
  defaultBaseUrl = "https://api.mistral.ai"
)

// ---- Custom (OpenAI Compatible) ----
private class CustomHandler : OpenAICompatibleHandler(
  defaultBaseUrl = ""
)

// ---- Shared HTTP helper ----
internal fun executeHttp(
  config: HttpConfig,
  body: String? = null,
  connectTimeout: Int = 10_000,
  readTimeout: Int = 10_000,
): Pair<Int, String> {
  val connection = java.net.URL(config.url).openConnection() as java.net.HttpURLConnection
  connection.connectTimeout = connectTimeout
  connection.readTimeout = readTimeout
  connection.requestMethod = config.method
  connection.setRequestProperty("Content-Type", "application/json")
  connection.setRequestProperty("User-Agent", "HMX-IDE/1.0")
  for ((name, value) in config.headers) {
    if (value.isNotEmpty()) connection.setRequestProperty(name, value)
  }
  if (body != null) {
    connection.doOutput = true
    java.io.OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
  }
  val code = connection.responseCode
  val response = if (code in 200..299) {
    connection.inputStream.bufferedReader().readText()
  } else {
    connection.errorStream?.bufferedReader()?.readText().orEmpty()
  }
  return code to response
}

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
  "openai" -> OpenAICompatibleHandler("https://api.openai.com")
  "openrouter" -> OpenAICompatibleHandler("https://openrouter.ai/api/v1")
  "nvidia" -> NvidiaHandler()
  "groq" -> GroqHandler()
  "deepseek" -> DeepSeekHandler()
  "mistral" -> MistralHandler()
  "togetherai" -> TogetherAIHandler()
  "fireworks" -> FireworksHandler()
  "xai" -> XaiHandler()
  "opencode" -> OpenCodeHandler()
  "custom" -> CustomHandler()
  else -> OpenAICompatibleHandler("")
}
