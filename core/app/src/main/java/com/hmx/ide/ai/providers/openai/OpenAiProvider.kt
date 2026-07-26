package com.hmx.ide.ai.providers.openai

import com.hmx.ide.ai.AiProvider
import com.hmx.ide.ai.errors.AiException
import com.hmx.ide.ai.errors.AuthenticationException
import com.hmx.ide.ai.errors.ModelNotFoundException
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
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

open class OpenAiProvider(
  override val providerId: String,
  override val displayName: String,
  private val defaultBaseUrl: String,
  private val storage: ProviderStorage? = null,
  private val client: AiHttpClient = AiHttpClient(),
  private val chatEndpointPath: String = "v1/chat/completions",
  private val modelsEndpointPath: String = "v1/models",
) : AiProvider {

  override val capabilities: Set<Capability> = setOf(Capability.streaming)

  private fun resolveBaseUrl(): String {
    val userBaseUrl = storage?.getBaseUrl(providerId) ?: ""
    return userBaseUrl.ifBlank { defaultBaseUrl }.trimEnd('/')
  }

  private fun buildUrl(baseUrl: String, path: String): String =
    "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"

  override suspend fun chat(request: ChatRequest): ChatResponse {
    val base = resolveBaseUrl()
    val url = buildUrl(base, chatEndpointPath)
    val headers = authHeaders()
    val body = buildRequestBody(request)
    val config = HttpConfig(url = url, method = "POST", headers = headers)
    val response = client.execute(config, body = body)
    if (response.code !in 200..299) throw mapError(response)
    return parseChatResponse(response.body)
  }

  override fun stream(request: ChatRequest): Flow<Chunk> {
    val base = resolveBaseUrl()
    val url = buildUrl(base, chatEndpointPath)
    val headers = authHeaders()
    val streamRequest = request.copy(stream = true)
    val body = buildRequestBody(streamRequest)
    val config = HttpConfig(url = url, method = "POST", headers = headers)
    return client.stream(config, body = body).map { line ->
      val json = JSONObject(line)
      val delta = json.optJSONObject("choices")?.optJSONObject("delta")
      val content = delta?.optString("content", "") ?: ""
      val finish = json.optJSONObject("choices")?.optString("finish_reason")
      Chunk(content = content, finishReason = finish)
    }
  }

  override suspend fun listModels(): List<AiModel> {
    val base = resolveBaseUrl()
    val url = buildUrl(base, modelsEndpointPath)
    val headers = authHeaders()
    val config = HttpConfig(url = url, headers = headers)
    val response = client.execute(config)
    if (response.code !in 200..299) return emptyList()
    return parseModels(response.body)
  }

  override suspend fun testConnection(): Boolean {
    val base = resolveBaseUrl()
    val url = buildUrl(base, modelsEndpointPath)
    val headers = authHeaders()
    val config = HttpConfig(url = url, headers = headers)
    val response = client.execute(config)
    return response.code in 200..299
  }

  protected open fun authHeaders(): Map<String, String> {
    val key = storage?.getApiKey(providerId) ?: ""
    return if (key.isNotBlank()) mapOf("Authorization" to "Bearer $key") else emptyMap()
  }

  protected open fun buildRequestBody(request: ChatRequest): String {
    val arr = JSONArray()
    if (request.systemPrompt != null) {
      arr.put(JSONObject().put("role", "system").put("content", request.systemPrompt))
    }
    request.messages.forEach { m ->
      arr.put(JSONObject().put("role", m.role.name).put("content", m.content))
    }
    val json = JSONObject().apply {
      put("model", request.model)
      put("messages", arr)
      put("stream", request.stream)
      request.maxTokens?.let { put("max_tokens", it) }
      request.temperature?.let { put("temperature", it) }
    }
    return json.toString()
  }

  protected open fun parseChatResponse(body: String): ChatResponse {
    val json = JSONObject(body)
    val choice = json.getJSONArray("choices").getJSONObject(0)
    val msg = choice.getJSONObject("message")
    val role = Role.valueOf(msg.optString("role", "assistant"))
    val content = msg.optString("content", "")
    val usageJson = json.optJSONObject("usage")
    val usage = usageJson?.let {
      Usage(it.optInt("prompt_tokens"), it.optInt("completion_tokens"), it.optInt("total_tokens"))
    }
    return ChatResponse(
      message = ChatMessage(role = role, content = content),
      model = json.optString("model"),
      usage = usage,
    )
  }

  protected open fun parseModels(body: String): List<AiModel> {
    val list = mutableListOf<AiModel>()
    val json = JSONObject(body)
    val data = json.optJSONArray("data") ?: return emptyList()
    for (i in 0 until data.length()) {
      val model = data.getJSONObject(i)
      list.add(AiModel(id = model.getString("id")))
    }
    return list
  }

  protected open fun mapError(response: HttpResponse): AiException {
    return when (response.code) {
      401 -> AuthenticationException("Invalid API key")
      403 -> AuthenticationException("Forbidden — check API key permissions")
      429 -> RateLimitException("Rate limited")
      404 -> ModelNotFoundException("Model not found")
      else -> NetworkException("HTTP ${response.code}: ${response.body}")
    }
  }
}
