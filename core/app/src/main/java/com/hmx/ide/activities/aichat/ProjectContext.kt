package com.hmx.ide.activities.aichat

data class ChatMessage(
  val role: String, // "user" | "assistant" | "system"
  val content: String
)
