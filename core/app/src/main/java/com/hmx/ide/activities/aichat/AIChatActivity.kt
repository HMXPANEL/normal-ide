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
import com.hmx.ide.ai.AiFactory
import com.hmx.ide.ai.engine.ChatEngine
import com.hmx.ide.app.BaseIDEActivity
import com.hmx.ide.databinding.ActivityAiChatBinding
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

class AIChatActivity : BaseIDEActivity() {

  private lateinit var binding: ActivityAiChatBinding
  private val adapter = AIChatAdapter()
  private val scope = CoroutineScope(Dispatchers.Main)

  private var projectDir: File? = null
  private var pendingEdits = linkedMapOf<String, String>()

  private val chatEngine by lazy { ChatEngine(AiFactory.engine()) }
  private var systemPrompt: String? = null

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
      systemPrompt = "You are an AI coding assistant for the Android project " +
        "'${projectDir!!.name}'.\n\n" +
        "Project structure:\n${ProjectContext.describe(projectDir!!)}\n\n" +
        "You can read project files when asked. To create or modify a file, " +
        "respond with a fenced block like:\n" +
        "[[WRITE:relative/path/File.kt]]\n<full file content>\n[[END]]\n" +
        "Otherwise just answer conversationally."
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

    adapter.add(ChatMessage("user", text))
    adapter.add(ChatMessage("assistant", "…"))
    binding.send.isEnabled = false

    scope.launch(Dispatchers.Main) {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          val engine = AiFactory.engine()
          val providerId = engine.activeProvider().providerId
          val model = storedModel(providerId)
          val response = chatEngine.send(model, text, systemPrompt)
          response.message.content
        }
      }
      binding.send.isEnabled = true
      result.onSuccess { content ->
        adapter.setLastContent(content)
        collectEdits(content)
      }.onFailure { err ->
        adapter.setLastContent("⚠ ${err.message}")
        flashError(getString(string.msg_ai_chat_error, err.message))
      }
    }
  }

  private fun storedModel(providerId: String): String {
    val model = AiFactory.storage().getModel(providerId)
    if (model.isNotBlank()) return model
    return when (providerId) {
      "ollama" -> "qwen2.5-coder:7b"
      "gemini" -> "gemini-2.0-flash"
      "openai" -> "gpt-4o-mini"
      else -> "gpt-4o-mini"
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
    val provider = AiFactory.engine().activeProvider()
    val model = storedModel(provider.providerId)
    builder.setMessage("Provider: ${provider.displayName}\n\nModel: $model\n\nChange model:")
    builder.setView(bindingInput.root)
    builder.setPositiveButton(android.R.string.ok) { _, _ ->
      val input = bindingInput.name.editText?.text?.toString()?.trim()
      if (!input.isNullOrBlank()) {
        AiFactory.storage().setModel(provider.providerId, input)
      }
    }
    builder.setNegativeButton(android.R.string.cancel, null)
    builder.show()
  }
}
