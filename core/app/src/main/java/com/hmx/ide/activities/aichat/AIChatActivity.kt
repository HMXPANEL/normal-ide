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

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.hmx.ide.app.BaseIDEActivity
import com.hmx.ide.databinding.ActivityAiChatBinding
import com.hmx.ide.preferences.internal.GeneralPreferences
import com.hmx.ide.projects.IProjectManager
import com.hmx.ide.R
import com.hmx.ide.resources.R.string
import com.hmx.ide.utils.DialogUtils
import com.hmx.ide.utils.flashError
import com.hmx.ide.utils.flashSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Full-screen, project-aware AI Chat. The AI always knows which project is
 * currently open and can read its files, explain/ generate code, and (via
 * [[WRITE:path]] blocks) create or modify project files.
 */
class AIChatActivity : BaseIDEActivity() {

  private lateinit var binding: ActivityAiChatBinding
  private val adapter = AIChatAdapter()
  private val scope = CoroutineScope(Dispatchers.Main)

  private var projectDir: File? = null
  private val messages = mutableListOf<ChatMessage>()

  // Pending file edits parsed from the last assistant message: path -> content
  private var pendingEdits = linkedMapOf<String, String>()

  override fun bindLayout(): View {
    binding = ActivityAiChatBinding.inflate(layoutInflater)
    return binding.root
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    projectDir = runCatching { IProjectManager.getInstance().projectDir }.getOrNull()

    binding.messages.adapter = adapter
    binding.messages.layoutManager = LinearLayoutManager(this).apply {
      stackFromEnd = true
    }

    binding.toolbar.setNavigationOnClickListener { finish() }
    binding.toolbar.setOnMenuItemClickListener { item ->
      if (item.itemId == R.id.action_config) {
        showConfigDialog()
        true
      } else false
    }

    binding.send.setOnClickListener { sendMessage() }

    if (projectDir != null) {
      val welcome = "You are an AI coding assistant for the Android project " +
        "'${projectDir!!.name}'.\n\n" +
        "Project structure:\n${ProjectContext.describe(projectDir!!)}\n\n" +
        "You can read project files when asked. To create or modify a file, " +
        "respond with a fenced block like:\n" +
        "[[WRITE:relative/path/File.kt]]\n<full file content>\n[[END]]\n" +
        "Otherwise just answer conversationally."
      messages.add(ChatMessage("system", welcome))
      adapter.add(
        ChatMessage("assistant",
          "Hi! I can see the '${projectDir!!.name}' project. " +
            "Ask me to explain code, generate files, or fix errors. " +
            "I can read and modify your project files."))
    } else {
      adapter.add(ChatMessage("assistant",
        getString(string.msg_ai_chat_project_required)))
    }
  }

  private fun sendMessage() {
    val text = binding.messageInput.text?.toString()?.trim().orEmpty()
    if (text.isBlank()) return
    binding.messageInput.text?.clear()

    messages.add(ChatMessage("user", text))
    adapter.add(ChatMessage("user", text))

    adapter.add(ChatMessage("assistant", "…"))
    binding.send.isEnabled = false

    val requestMessages = messages.toList()
    val endpoint = GeneralPreferences.aiChatEndpoint
    val model = GeneralPreferences.aiChatModel

    scope.launch(Dispatchers.Main) {
      val result = withContext(Dispatchers.IO) {
        runCatching { AIChatClient.chat(endpoint, model, requestMessages) }
      }
      binding.send.isEnabled = true
      result.onSuccess { content ->
        adapter.setLastContent(content)
        messages[messages.lastIndex] = ChatMessage("assistant", content)
        collectEdits(content)
      }.onFailure { err ->
        adapter.setLastContent("⚠ ${err.message}")
        messages[messages.lastIndex] = ChatMessage("assistant", "⚠ ${err.message}")
        flashError(getString(string.msg_ai_chat_error, err.message))
      }
    }
  }

  private fun collectEdits(content: String) {
    pendingEdits.clear()
    val regex = Regex("\\[\\[WRITE:(.+?)\\]\\](.*?)\\[\\[END\\]\\]", RegexOption.DOT_MATCHES_ALL)
    regex.findAll(content).forEach {
      pendingEdits[it.groupValues[1].trim()] = it.groupValues[2].trim('\n', '\r')
    }
    binding.applyChanges.visibility = if (pendingEdits.isNotEmpty()) View.VISIBLE else View.GONE
    binding.applyChanges.setOnClickListener { applyEdits() }
  }

  private fun applyEdits() {
    val root = projectDir ?: return
    var count = 0
    pendingEdits.forEach { (rel, content) ->
      runCatching {
        val file = File(root, rel)
        file.parentFile?.mkdirs()
        file.writeText(content)
        count++
      }
    }
    pendingEdits.clear()
    binding.applyChanges.visibility = View.GONE
    flashSuccess(getString(string.msg_ai_chat_applied, count))
  }

  private fun showConfigDialog() {
    val bindingInput = com.hmx.ide.preferences.databinding.LayoutDialogTextInputBinding.inflate(
      layoutInflater)
    val builder = DialogUtils.newMaterialDialogBuilder(this)
    builder.setTitle(string.title_ai_chat_config)
    builder.setView(bindingInput.root)
    builder.setMessage("Endpoint:\n${GeneralPreferences.aiChatEndpoint}\n\nModel:\n${GeneralPreferences.aiChatModel}")
    builder.setPositiveButton(android.R.string.ok) { _, _ ->
      val input = bindingInput.name.editText?.text?.toString()?.trim()
      if (!input.isNullOrBlank()) {
        // Accept "endpoint|model" on one line for simplicity
        val parts = input.split("|")
        GeneralPreferences.aiChatEndpoint = parts[0].trim()
        if (parts.size > 1) GeneralPreferences.aiChatModel = parts[1].trim()
      }
    }
    builder.setNegativeButton(android.R.string.cancel, null)
    builder.show()
  }
}
