package com.hmx.ide.ai.models

enum class Role { system, user, assistant }

data class ChatMessage(
  val role: Role,
  val content: String,
)

data class ChatRequest(
  val model: String,
  val messages: List<ChatMessage>,
  val systemPrompt: String? = null,
  val stream: Boolean = false,
  val maxTokens: Int? = null,
  val temperature: Float? = null,
)

data class ChatResponse(
  val message: ChatMessage,
  val model: String? = null,
  val usage: Usage? = null,
)

data class Chunk(
  val content: String,
  val finishReason: String? = null,
)

data class Usage(
  val promptTokens: Int = 0,
  val completionTokens: Int = 0,
  val totalTokens: Int = 0,
)

data class AiModel(
  val id: String,
  val name: String? = null,
  val providerId: String? = null,
)

enum class Capability {
  streaming, vision, tools, functionCalling
}
