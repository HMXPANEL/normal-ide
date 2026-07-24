package com.hmx.ide.ai.providers.ollama

import com.hmx.ide.ai.AiProvider
import com.hmx.ide.ai.errors.AiException
import com.hmx.ide.ai.errors.NetworkException
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
import com.hmx.ide.activities.HttpConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class OllamaProvider(
  private val baseUrl: String = "http://localhost:11434",
  private val client: AiHttpClient = AiHttpClient(readTimeout = 120_000),
) : AiProvider {

  override val providerId: String = "ollama"
  override val displayName: String = "Ollama"
  override val capabilities: Set<Capability> = setOf(Capability.streaming)

  override suspend fun chat(request: ChatRequest): ChatResponse {
    val url = "$baseUrl/api/chat"
    val body = buildRequestBody(request)
    val config = HttpConfig(url = url, method = "POST")
    val response = client.execute(config, body = body)
    if (response.code !in 200..299) throw mapError(response)
    return parseChatResponse(response.body)
  }

  override fun stream(request: ChatRequest): Flow<Chunk> {
    val url = "$baseUrl/api/chat"
    val streamRequest = request.copy(stream = true)
    val body = buildRequestBody(streamRequest)
    val config = HttpConfig(url = url, method = "POST")
    return client.stream(config, body = body).map { line ->
      val json = JSONObject(line)
      val msg = json.optJSONObject("message")
      val content = msg?.optString("content", "") ?: ""
      val done = json.optBoolean("done", false)
      Chunk(content = content, finishReason = if (done) "stop" else null)
    }
  }

  override suspend fun listModels(): List<AiModel> {
    val url = "$baseUrl/api/tags"
    val config = HttpConfig(url = url)
    val response = client.execute(config)
    if (response.code !in 200..299) return emptyList()
    return parseModels(response.body)
  }

  override suspend fun testConnection(): Boolean {
    val url = "$baseUrl/api/tags"
    val config = HttpConfig(url = url)
    val response = client.execute(config)
    return response.code in 200..299
  }

  private fun buildRequestBody(request: ChatRequest): String {
    val messages = JSONArray()
    request.messages.forEach { m ->
      messages.put(JSONObject().put("role", m.role.name).put("content", m.content))
    }
    return JSONObject().apply {
      put("model", request.model)
      put("messages", messages)
      put("stream", request.stream)
    }.toString()
  }

  private fun parseChatResponse(body: String): ChatResponse {
    val json = JSONObject(body)
    val msg = json.getJSONObject("message")
    val role = Role.valueOf(msg.optString("role", "assistant"))
    val content = msg.optString("content", "")
    return ChatResponse(
      message = ChatMessage(role = role, content = content),
      model = json.optString("model"),
    )
  }

  private fun parseModels(body: String): List<AiModel> {
    val list = mutableListOf<AiModel>()
    val json = JSONObject(body)
    val models = json.optJSONArray("models") ?: return emptyList()
    for (i in 0 until models.length()) {
      val name = models.getJSONObject(i).getString("name")
      list.add(AiModel(id = name))
    }
    return list
  }

  private fun mapError(response: HttpResponse): AiException {
    return when (response.code) {
      0 -> NetworkException("Connection refused — is Ollama running?")
      else -> NetworkException("HTTP ${response.code}: ${response.body}")
    }
  }
}
