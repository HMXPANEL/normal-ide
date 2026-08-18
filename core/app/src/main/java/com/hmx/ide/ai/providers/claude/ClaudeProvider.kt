package com.hmx.ide.ai.providers.claude

import com.hmx.ide.ai.AiProvider
import com.hmx.ide.ai.errors.AiException
import com.hmx.ide.ai.errors.AuthenticationException
import com.hmx.ide.ai.errors.NetworkException
import com.hmx.ide.ai.errors.RateLimitException
import com.hmx.ide.ai.models.AiModel
import com.hmx.ide.ai.models.Capability
import com.hmx.ide.ai.models.ChatMessage
import com.hmx.ide.ai.models.ChatRequest
import com.hmx.ide.ai.models.ChatResponse
import com.hmx.ide.ai.models.Chunk
import com.hmx.ide.ai.models.Role
import com.hmx.ide.ai.models.Usage
import com.hmx.ide.ai.network.AiHttpClient
import com.hmx.ide.ai.network.HttpResponse
import com.hmx.ide.ai.storage.ProviderStorage
import com.hmx.ide.activities.HttpConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject

class ClaudeProvider(
  private val storage: ProviderStorage? = null,
  private val client: AiHttpClient = AiHttpClient(),
) : AiProvider {

  override val providerId: String = "claude"
  override val displayName: String = "Claude"
  override val capabilities: Set<Capability> = setOf(Capability.streaming, Capability.vision)

  private val baseUrl = "https://api.anthropic.com"

  private fun headers() = mapOf(
    "x-api-key" to (storage?.getApiKey(providerId) ?: ""),
    "anthropic-version" to "2023-06-01",
  )

  override suspend fun chat(request: ChatRequest): ChatResponse {
    val url = "$baseUrl/v1/messages"
    val body = buildRequestBody(request)
    val config = HttpConfig(url = url, method = "POST", headers = headers())
    val response = client.execute(config, body = body)
    if (response.code !in 200..299) throw mapError(response)
    return parseChatResponse(response.body)
  }

  override fun stream(request: ChatRequest): Flow<Chunk> = flow {
    val response = chat(request.copy(stream = false))
    emit(Chunk(content = response.message.content, finishReason = "stop"))
  }

  override suspend fun listModels(): List<AiModel> {
    val url = "$baseUrl/v1/models"
    val config = HttpConfig(url = url, headers = headers())
    val response = client.execute(config)
    if (response.code !in 200..299) return emptyList()
    return parseModels(response.body)
  }

  override suspend fun testConnection(): Boolean {
    val url = "$baseUrl/v1/models"
    val config = HttpConfig(url = url, headers = headers())
    val response = client.execute(config)
    return response.code in 200..299
  }

  private fun buildRequestBody(request: ChatRequest): String {
    val messages = JSONArray()
    request.messages.forEach { m ->
      messages.put(JSONObject().put("role", m.role.name).put("content", m.content))
    }
    val json = JSONObject().apply {
      put("model", request.model)
      put("messages", messages)
      put("max_tokens", request.maxTokens ?: 4096)
      request.systemPrompt?.let { put("system", it) }
      request.temperature?.let { put("temperature", it) }
    }
    return json.toString()
  }

  private fun parseChatResponse(body: String): ChatResponse {
    val json = JSONObject(body)
    val contentArray = json.getJSONArray("content")
    val text = if (contentArray.length() > 0) {
      contentArray.getJSONObject(0).optString("text", "")
    } else ""
    val usageJson = json.optJSONObject("usage")
    val usage = usageJson?.let {
      Usage(it.optInt("input_tokens"), it.optInt("output_tokens"))
    }
    return ChatResponse(
      message = ChatMessage(role = Role.assistant, content = text),
      model = json.optString("model"),
      usage = usage,
    )
  }

  private fun parseModels(body: String): List<AiModel> {
    val list = mutableListOf<AiModel>()
    val json = JSONObject(body)
    val data = json.optJSONArray("data") ?: return emptyList()
    for (i in 0 until data.length()) {
      val model = data.getJSONObject(i)
      list.add(AiModel(id = model.getString("id")))
    }
    return list
  }

  private fun mapError(response: HttpResponse): AiException {
    return when (response.code) {
      401 -> AuthenticationException("Invalid API key")
      403 -> AuthenticationException("Forbidden — check API key permissions")
      429 -> RateLimitException("Rate limited")
      else -> NetworkException("HTTP ${response.code}: ${response.body}")
    }
  }
}
