package com.hmx.ide.activities.aichat

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.hmx.ide.ai.AiFactory
import com.hmx.ide.ai.context.ContextCache
import com.hmx.ide.ai.context.IndexingState
import com.hmx.ide.ai.context.ProjectContextSummary
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

  /**
   * System prompt derived from the project index. Only sent when [useProjectContext] is `true`;
   * it is never discarded on toggle-off so the toggle stays free.
   */
  private var systemPrompt: String? = null

  /** Latest known summary. Never triggers a scan by itself. */
  private var contextSummary: ProjectContextSummary? = null

  /** Toggle state. `true` => the existing system prompt is attached to requests. */
  private var useProjectContext: Boolean = true

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

    binding.contextToggle.setOnCheckedChangeListener { _, isChecked ->
      useProjectContext = isChecked
      renderContextIndicator()
      renderSuggestions()
    }

    if (projectDir != null) {
      startProjectContext(projectDir!!)
    } else {
      contextSummary = null
      renderContextIndicator()
      renderSuggestions()
      adapter.add(ChatMessage("assistant",
        getString(string.msg_ai_chat_project_required)))
    }
  }

  /**
   * Renders the startup message using already-indexed data.
   *
   * A full scan is only started when neither [ContextCache] nor the knowledge engine hold an
   * index; the cached path costs a single map lookup and never blocks the UI thread.
   */
  private fun startProjectContext(root: File) {
    val path = root.absolutePath
    val currentFileRel = currentFile?.let { f ->
      runCatching { File(f).toRelativeString(root) }.getOrDefault(f)
    }

    val cached = ContextCache.getSummary(path)
    if (cached.hasContext) {
      contextSummary = cached
      renderContextIndicator()
      renderSuggestions()
      adapter.add(ChatMessage("assistant",
        PromptBuilder.buildStartupMessage(cached, currentFileRel)))
      // The system prompt needs the full index; resolve it off the UI thread.
      scope.launch {
        val index = withContext(Dispatchers.IO) { ContextCache.getOrAnalyze(path) }
        systemPrompt = PromptBuilder.build(index, currentFileRel ?: currentFile)
      }
      return
    }

    // No index available yet — show progress and scan once.
    contextSummary = ProjectContextSummary.unavailable(path, IndexingState.INDEXING)
    renderContextIndicator()
    renderSuggestions()
    adapter.add(ChatMessage("assistant", "Scanning project…"))

    scope.launch {
      val index = withContext(Dispatchers.IO) {
        ContextCache.getOrAnalyze(path) { msg ->
          scope.launch { adapter.setLastContent(msg) }
        }
      }
      systemPrompt = PromptBuilder.build(index, currentFileRel ?: currentFile)
      contextSummary = ProjectContextSummary.from(index, IndexingState.READY)
      renderContextIndicator()
      renderSuggestions()
      adapter.setLastContent(
        PromptBuilder.buildStartupMessage(contextSummary!!, currentFileRel))
    }
  }

  /** Updates the indicator row. Cheap; safe to call on every state change. */
  private fun renderContextIndicator() {
    val summary = contextSummary
    if (summary == null) {
      binding.contextIndicator.visibility = View.GONE
      return
    }

    binding.contextIndicator.visibility = View.VISIBLE

    if (!useProjectContext) {
      binding.contextStatus.text = getString(string.msg_ai_chat_context_off)
      return
    }

    val statusLabel = when (summary.state) {
      IndexingState.READY -> getString(string.msg_ai_chat_context_ready)
      IndexingState.UPDATING -> getString(string.msg_ai_chat_context_updating)
      IndexingState.INDEXING -> summary.progress
        ?.let { "${getString(string.msg_ai_chat_context_indexing)} ${(it * 100).toInt()}%" }
        ?: getString(string.msg_ai_chat_context_indexing)
      IndexingState.UNAVAILABLE -> getString(string.msg_ai_chat_context_unavailable)
    }

    binding.contextStatus.text = buildString {
      append(getString(string.msg_ai_chat_context_on))
      if (summary.totalFiles > 0) {
        append(" • ")
        append(getString(string.msg_ai_chat_context_files, summary.totalFiles))
      }
      append(" • ")
      append(statusLabel)
    }
  }

  /** Rebuilds the chip row for the current context state. */
  private fun renderSuggestions() {
    val hasContext = useProjectContext && contextSummary?.hasContext == true
    val suggestions = ChatSuggestion.forState(hasContext)

    binding.suggestionChips.removeAllViews()
    if (suggestions.isEmpty()) {
      binding.suggestionsScroll.visibility = View.GONE
      return
    }

    binding.suggestionsScroll.visibility = View.VISIBLE
    suggestions.forEach { suggestion ->
      val chip = Chip(this).apply {
        text = suggestion.label
        isCheckable = false
        isClickable = true
        setOnClickListener { applySuggestion(suggestion) }
      }
      binding.suggestionChips.addView(chip)
    }
  }

  /**
   * Fills the existing input with the chip prompt and reuses the normal send flow. Prompts that
   * end with a space expect the user to complete them, so they are not auto-sent.
   */
  private fun applySuggestion(suggestion: ChatSuggestion) {
    binding.messageInput.setText(suggestion.prompt)
    binding.messageInput.setSelection(suggestion.prompt.length)
    if (!suggestion.prompt.endsWith(" ")) {
      sendMessage()
    } else {
      binding.messageInput.requestFocus()
    }
  }

  private fun sendMessage() {
    val text = binding.messageInput.text?.toString()?.trim().orEmpty()
    if (text.isBlank()) return
    binding.messageInput.text?.clear()

    val isAnalysis = useProjectContext && text.lowercase().startsWith("analyze")
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
          // Provider-agnostic: the same request shape is used for every provider; only the
          // optional system prompt varies with the toggle.
          val prompt = systemPrompt.takeIf { useProjectContext }
          val response = chatEngine.send(model, text, prompt)
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
    val canonicalRoot = runCatching { root.canonicalPath }.getOrNull() ?: return
    var count = 0
    pendingEdits.forEach { (rel, content) ->
      runCatching {
        val file = File(root, rel)
        val canonicalFile = file.canonicalPath
        if (!canonicalFile.startsWith(canonicalRoot + File.separator) && canonicalFile != canonicalRoot) {
          return@forEach
        }
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
