/*
 *  This file is part of HMX IDE.
 *
 *  HMX IDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  HMX IDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with HMX IDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.hmx.ide.activities.aichat

import com.hmx.ide.activities.HttpConfig
import com.hmx.ide.activities.executeHttp
import org.json.JSONArray
import org.json.JSONObject

/**
 * LEGACY — replaced by ChatEngine + AiProvider implementations.
 *
 * Minimal OpenAI-compatible chat client (also works with Ollama's /api/chat
 * and LM Studio). Uses [HttpURLConnection] so no extra dependency is needed.
 *
 * The assistant may return file edits wrapped as:
 *   [[WRITE:relative/path]]\n<file content>\n[[END]]
 * which the activity detects and offers to apply to the open project.
 *
 * Remove this file once all callers use [com.hmx.ide.ai.engine.ChatEngine].
 */
object AIChatClient {

  /**
   * @return the assistant's message content.
   * @throws Throwable if the request fails.
   */
  fun chat(endpoint: String, model: String, messages: List<ChatMessage>): String {
    val body = JSONObject().apply {
      put("model", model)
      put("stream", false)
      put("messages", JSONArray().also { arr ->
        messages.forEach { m ->
          arr.put(JSONObject().put("role", m.role).put("content", m.content))
        }
      })
    }

    val config = HttpConfig(url = endpoint, method = "POST")
    val (code, raw) = executeHttp(config, body = body.toString(),
      connectTimeout = 30_000, readTimeout = 300_000)

    if (code !in 200..299) {
      throw RuntimeException("HTTP $code: $raw")
    }

    // Ollama /api/chat shape: { message: { content: "..." } }
    // OpenAI shape: { choices: [ { message: { content: "..." } } ] }
    val json = JSONObject(raw)
    if (json.has("message")) {
      return json.getJSONObject("message").getString("content")
    }
    val choices = json.optJSONArray("choices")
    if (choices != null && choices.length() > 0) {
      return choices.getJSONObject(0).getJSONObject("message").getString("content")
    }
    throw RuntimeException("Unexpected response: $raw")
  }
}
