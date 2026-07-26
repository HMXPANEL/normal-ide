package com.hmx.ide.activities.aichat

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.hmx.ide.ai.AiFactory
import com.hmx.ide.ai.context.ContextCache
import com.hmx.ide.ai.context.PromptBuilder
import com.hmx.ide.ai.engine.ChatEngine
import com.hmx.ide.ai.errors.ProviderConfigurationException
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

  companion object {
    const val EXTRA_CURRENT_FILE = "current_file"
  }

  private lateinit var binding: ActivityAiChatBinding
  private val adapter = AIChatAdapter()
  private val scope = CoroutineScope(Dispatchers.Main)

  private var projectDir: File? = null
  private var currentFile: String? = null
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
    currentFile = intent.getStringExtra(EXTRA_CURRENT_FILE)

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
      scope.launch {
        adapter.add(ChatMessage("assistant", "Scanning project..."))
        val ctx = withContext(Dispatchers.IO) {
          ContextCache.getOrAnalyze(projectDir!!.absolutePath) { msg ->
            scope.launch { adapter.setLastContent(msg) }
          }
        }
        val currentFileRel = currentFile?.let { f ->
          runCatching { File(f).toRelativeString(projectDir!!) }.getOrDefault(f)
        }
        systemPrompt = PromptBuilder.build(ctx, currentFileRel ?: currentFile)
        val fileCount = ctx.totalSourceFiles
        adapter.setLastContent(
          "Hi! I can see the '${projectDir!!.name}' project. " +
          "Indexed $fileCount files." +
          (if (currentFileRel != null) "\n\nCurrent File:\n$currentFileRel" else "") +
          "\n\nAsk me to explain code, generate files, fix errors, or analyze the project.")
      }
    } else {
      adapter.add(ChatMessage("assistant",
        getString(string.msg_ai_chat_project_required)))
    }
  }

  private fun sendMessage() {
    val text = binding.messageInput.text?.toString()?.trim().orEmpty()
    if (text.isBlank()) return
    binding.messageInput.text?.clear()

    val isAnalysis = text.lowercase().startsWith("analyze")
    adapter.add(ChatMessage("user", text))

    if (isAnalysis && projectDir != null) {
      scope.launch {
        adapter.add(ChatMessage("assistant", "…"))
        binding.send.isEnabled = false
        val analysis = withContext(Dispatchers.IO) {
          val idx = ContextCache.getOrAnalyze(projectDir!!.absolutePath)
          PromptBuilder.buildAnalysis(idx)
        }
        adapter.setLastContent(analysis)
        binding.send.isEnabled = true
      }
      return
    }

    adapter.add(ChatMessage("assistant", "…"))
    binding.send.isEnabled = false

    scope.launch(Dispatchers.Main) {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          val engine = AiFactory.engine()
          val providerId = engine.activeProvider().providerId
          val model = AiFactory.storage().getModel(providerId)
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
    val model = AiFactory.storage().getModel(provider.providerId).ifBlank { "(not configured)" }
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
