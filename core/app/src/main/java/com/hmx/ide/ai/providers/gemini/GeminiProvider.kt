package com.hmx.ide.ai.providers.gemini

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

class GeminiProvider(
  private val storage: ProviderStorage? = null,
  private val client: AiHttpClient = AiHttpClient(),
) : AiProvider {

  override val providerId: String = "gemini"
  override val displayName: String = "Gemini"
  override val capabilities: Set<Capability> = setOf(Capability.streaming, Capability.vision)

  private val baseUrl = "https://generativelanguage.googleapis.com/v1beta"

  private fun headers() = mapOf("x-goog-api-key" to (storage?.getApiKey(providerId) ?: ""))

  override suspend fun chat(request: ChatRequest): ChatResponse {
    val url = "$baseUrl/models/${request.model}:generateContent"
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
    val url = "$baseUrl/models"
    val config = HttpConfig(url = url, headers = headers())
    val response = client.execute(config)
    if (response.code !in 200..299) return emptyList()
    return parseModels(response.body)
  }

  override suspend fun testConnection(): Boolean {
    val url = "$baseUrl/models"
    val config = HttpConfig(url = url, headers = headers())
    val response = client.execute(config)
    return response.code in 200..299
  }

  private fun buildRequestBody(request: ChatRequest): String {
    val contents = JSONArray()
    if (request.systemPrompt != null) {
      contents.put(JSONObject().apply {
        put("role", "user")
        put("parts", JSONArray().put(JSONObject().put("text", request.systemPrompt)))
      })
    }
    request.messages.forEach { m ->
      contents.put(JSONObject().apply {
        put("role", if (m.role == Role.assistant) "model" else "user")
        put("parts", JSONArray().put(JSONObject().put("text", m.content)))
      })
    }
    return JSONObject().apply {
      put("contents", contents)
    }.toString()
  }

  private fun parseChatResponse(body: String): ChatResponse {
    val json = JSONObject(body)
    val candidate = json.getJSONArray("candidates").getJSONObject(0)
    val content = candidate.getJSONObject("content")
    val role = if (content.optString("role") == "model") Role.assistant else Role.user
    val text = content.getJSONArray("parts").getJSONObject(0).optString("text", "")
    return ChatResponse(
      message = ChatMessage(role = role, content = text),
      model = json.optString("model"),
    )
  }

  private fun parseModels(body: String): List<AiModel> {
    val list = mutableListOf<AiModel>()
    val json = JSONObject(body)
    val models = json.optJSONArray("models") ?: return emptyList()
    for (i in 0 until models.length()) {
      val name = models.getJSONObject(i).getString("name")
      list.add(AiModel(id = name.removePrefix("models/")))
    }
    return list
  }

  private fun mapError(response: HttpResponse): AiException {
    return when (response.code) {
      401, 403 -> AuthenticationException("Invalid or missing API key")
      429 -> RateLimitException("Rate limited")
      else -> NetworkException("HTTP ${response.code}: ${response.body}")
    }
  }
}
