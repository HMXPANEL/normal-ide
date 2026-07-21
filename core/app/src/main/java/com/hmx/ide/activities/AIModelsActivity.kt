package com.hmx.ide.activities

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.updatePaddingRelative
import com.google.android.material.snackbar.Snackbar
import com.hmx.ide.R
import com.hmx.ide.app.EdgeToEdgeIDEActivity
import com.hmx.ide.databinding.ActivityAiModelsBinding
import com.hmx.ide.preferences.internal.AIModelsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

class AIModelsActivity : EdgeToEdgeIDEActivity() {

  private var _binding: ActivityAiModelsBinding? = null
  private val binding: ActivityAiModelsBinding
    get() = checkNotNull(_binding) { "Activity has been destroyed" }

  override fun bindLayout(): View {
    _binding = ActivityAiModelsBinding.inflate(layoutInflater)
    return _binding!!.root
  }

  private val providers = listOf(
    AiProvider("gemini", R.string.idepref_ai_provider_gemini, R.drawable.ic_provider_gemini, "https://generativelanguage.googleapis.com"),
    AiProvider("claude", R.string.idepref_ai_provider_claude, R.drawable.ic_provider_claude, "https://api.anthropic.com"),
    AiProvider("openai", R.string.idepref_ai_provider_openai, R.drawable.ic_provider_openai, "https://api.openai.com"),
    AiProvider("openrouter", R.string.idepref_ai_provider_openrouter, R.drawable.ic_provider_openrouter, "https://openrouter.ai/api"),
    AiProvider("nvidia", R.string.idepref_ai_provider_nvidia, R.drawable.ic_provider_nvidia, "https://integrate.api.nvidia.com"),
    AiProvider("groq", R.string.idepref_ai_provider_groq, R.drawable.ic_provider_groq, "https://api.groq.com"),
    AiProvider("deepseek", R.string.idepref_ai_provider_deepseek, R.drawable.ic_provider_deepseek, "https://api.deepseek.com"),
    AiProvider("mistral", R.string.idepref_ai_provider_mistral, R.drawable.ic_provider_mistral, "https://api.mistral.ai"),
    AiProvider("togetherai", R.string.idepref_ai_provider_togetherai, R.drawable.ic_provider_togetherai, "https://api.together.xyz"),
    AiProvider("fireworks", R.string.idepref_ai_provider_fireworks, R.drawable.ic_provider_fireworks, "https://api.fireworks.ai"),
    AiProvider("xai", R.string.idepref_ai_provider_xai, R.drawable.ic_provider_xai, "https://api.x.ai"),
    AiProvider("ollama", R.string.idepref_ai_provider_ollama, R.drawable.ic_provider_ollama, "http://localhost:11434", needsApiKey = false, needsEndpoint = true),
    AiProvider("opencode", R.string.idepref_ai_provider_opencode, R.drawable.ic_provider_opencode, "https://opencode.ai/zen/v1", needsBaseUrl = true),
    AiProvider("custom", R.string.idepref_ai_provider_custom, R.drawable.ic_provider_custom, "", needsApiKey = true, needsBaseUrl = true),
  )

  private var previousProviderId: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding.apply {
      setSupportActionBar(toolbar)
      supportActionBar!!.setDisplayHomeAsUpEnabled(true)
      supportActionBar!!.setTitle(R.string.idepref_ai_models_title)
      toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

      setupProviderDropdown()
      setupModelDropdown()
      loadSavedPreferences()
      updateApiFieldsVisibility()

      providerDropdown.setOnItemClickListener { _, _, position, _ ->
        val provider = (providerDropdown.adapter as ProviderAdapter).getItem(position) as AiProvider
        providerDropdown.setText(provider.getTitle(this@AIModelsActivity), false)
        onProviderChanged(provider)
      }

      testConnectionBtn.setOnClickListener { testConnection() }
      saveBtn.setOnClickListener { savePreferences() }
    }
  }

  private fun onProviderChanged(provider: AiProvider) {
    val changed = provider.id != previousProviderId
    previousProviderId = provider.id
    connectionStatus.visibility = View.GONE
    updateApiFieldsVisibility()
    if (changed && !provider.needsBaseUrl) {
      binding.baseUrlInput.setText(provider.defaultBaseUrl)
    }
  }

  private fun setupProviderDropdown() {
    val adapter = ProviderAdapter(this, providers)
    binding.providerDropdown.setAdapter(adapter)
    binding.providerDropdown.onItemClickListener = null
  }

  private fun setupModelDropdown() {
    binding.modelDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, emptyList()))
  }

  private fun loadSavedPreferences() {
    val savedProvider = AIModelsPreferences.provider
    val provider = providers.find { it.id == savedProvider }
    if (provider != null) {
      previousProviderId = provider.id
      binding.providerDropdown.setText(provider.getTitle(this), false)
    }
    binding.apiKeyInput.setText(AIModelsPreferences.apiKey)
    binding.baseUrlInput.setText(AIModelsPreferences.baseUrl)
    binding.endpointInput.setText(AIModelsPreferences.endpoint)
    binding.modelDropdown.setText(AIModelsPreferences.model, false)
    binding.systemPromptInput.setText(AIModelsPreferences.systemPrompt)
  }

  private fun updateApiFieldsVisibility() {
    val provider = getSelectedProvider()
    if (provider == null) return

    binding.apiKeyLayout.visibility = if (provider.needsApiKey) View.VISIBLE else View.GONE
    binding.baseUrlLayout.visibility = if (provider.needsBaseUrl) View.VISIBLE else View.GONE
    binding.endpointLayout.visibility = if (provider.needsEndpoint) View.VISIBLE else View.GONE
  }

  private fun getSelectedProvider(): AiProvider? {
    val text = binding.providerDropdown.text.toString()
    return providers.find { it.getTitle(this) == text }
  }

  private fun resolveBaseUrl(provider: AiProvider): String {
    return when {
      provider.needsEndpoint && binding.endpointInput.text?.isNotEmpty() == true ->
        binding.endpointInput.text.toString().trim()
      provider.needsBaseUrl && binding.baseUrlInput.text?.isNotEmpty() == true ->
        binding.baseUrlInput.text.toString().trim()
      provider.defaultBaseUrl.isNotEmpty() -> provider.defaultBaseUrl
      else -> ""
    }
  }

  private sealed class ConnectionResult {
    data class Success(val responseCode: Int) : ConnectionResult()
    data class Failure(val reason: String) : ConnectionResult()
  }

  private fun testConnection() {
    val provider = getSelectedProvider()
    if (provider == null) {
      showStatus(getString(R.string.idepref_ai_select_provider), false)
      return
    }

    val apiKey = binding.apiKeyInput.text?.toString()?.trim() ?: ""
    val baseUrl = resolveBaseUrl(provider)

    if (provider.needsApiKey && apiKey.isEmpty()) {
      showStatus(getString(R.string.idepref_ai_enter_api_key), false)
      return
    }
    if (provider.needsBaseUrl && baseUrl.isEmpty()) {
      showStatus(getString(R.string.idepref_ai_enter_base_url), false)
      return
    }
    if (provider.needsEndpoint && baseUrl.isEmpty()) {
      showStatus(getString(R.string.idepref_ai_enter_endpoint), false)
      return
    }

    binding.testConnectionBtn.isEnabled = false
    binding.testConnectionBtn.text = getString(R.string.please_wait)

    CoroutineScope(Dispatchers.IO).launch {
      val result = performConnectionTest(provider, apiKey, baseUrl)
      withContext(Dispatchers.Main) {
        binding.testConnectionBtn.isEnabled = true
        binding.testConnectionBtn.text = getString(R.string.idepref_ai_test_connection)
        when (result) {
          is ConnectionResult.Success -> {
            showStatus(getString(R.string.idepref_ai_connected_successfully), true)
            fetchModels(provider, apiKey, baseUrl)
          }
          is ConnectionResult.Failure -> {
            showStatus(result.reason, false)
          }
        }
      }
    }
  }

  private fun performConnectionTest(provider: AiProvider, apiKey: String, baseUrl: String): ConnectionResult {
    return try {
      val testUrl = when (provider.id) {
        "ollama" -> "$baseUrl/api/tags"
        "gemini" -> "https://generativelanguage.googleapis.com/v1/models?key=$apiKey"
        else -> "$baseUrl/v1/models"
      }

      val connection = URL(testUrl).openConnection() as HttpURLConnection
      connection.connectTimeout = 8000
      connection.readTimeout = 8000
      connection.requestMethod = "GET"
      connection.setRequestProperty("Content-Type", "application/json")
      if (apiKey.isNotEmpty()) {
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
      }
      connection.connect()

      val code = connection.responseCode
      when (code) {
        in 200..299 -> ConnectionResult.Success(code)
        401, 403 -> ConnectionResult.Failure(getString(R.string.idepref_ai_error_unauthorized))
        404 -> ConnectionResult.Failure(getString(R.string.idepref_ai_error_invalid_url))
        429 -> ConnectionResult.Failure(getString(R.string.idepref_ai_error_unknown, "Rate limited (429)"))
        in 400..499 -> ConnectionResult.Failure(getString(R.string.idepref_ai_error_invalid_api_key))
        in 500..599 -> ConnectionResult.Failure(getString(R.string.idepref_ai_error_unknown, "Server error ($code)"))
        else -> ConnectionResult.Failure(getString(R.string.idepref_ai_error_unknown, "HTTP $code"))
      }
    } catch (e: SocketTimeoutException) {
      ConnectionResult.Failure(getString(R.string.idepref_ai_error_timeout))
    } catch (e: UnknownHostException) {
      ConnectionResult.Failure(getString(R.string.idepref_ai_error_network))
    } catch (e: IllegalArgumentException) {
      ConnectionResult.Failure(getString(R.string.idepref_ai_error_invalid_url))
    } catch (e: Exception) {
      ConnectionResult.Failure(getString(R.string.idepref_ai_error_unknown, e.localizedMessage ?: e.javaClass.simpleName))
    }
  }

  private fun fetchModels(provider: AiProvider, apiKey: String, baseUrl: String) {
    binding.modelDropdown.setText("")
    val loadingMsg = getString(R.string.idepref_ai_fetching_models)
    binding.modelDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, listOf(loadingMsg)))
    binding.modelDropdown.dismissDropDown()

    CoroutineScope(Dispatchers.IO).launch {
      val models = try {
        fetchModelsFromProvider(provider, apiKey, baseUrl)
      } catch (_: Exception) {
        emptyList()
      }
      withContext(Dispatchers.Main) {
        val fallback = fallbackModels(provider)
        val allModels = if (models.isNotEmpty()) models else fallback
        if (allModels.isNotEmpty()) {
          binding.modelDropdown.setAdapter(ArrayAdapter(this@AIModelsActivity, android.R.layout.simple_dropdown_item_1line, allModels))
          binding.modelDropdown.setText(AIModelsPreferences.model.takeIf { it in allModels } ?: allModels.first(), false)
        } else {
          binding.modelDropdown.setAdapter(ArrayAdapter(this@AIModelsActivity, android.R.layout.simple_dropdown_item_1line, listOf(getString(R.string.idepref_ai_no_models))))
          showStatus(getString(R.string.idepref_ai_fetch_models_failed), false)
        }
      }
    }
  }

  private fun fetchModelsFromProvider(provider: AiProvider, apiKey: String, baseUrl: String): List<String> {
    val modelsUrl = when (provider.id) {
      "ollama" -> "$baseUrl/api/tags"
      "gemini" -> "https://generativelanguage.googleapis.com/v1/models?key=$apiKey"
      "opencode" -> "$baseUrl/models"
      else -> "$baseUrl/v1/models"
    }

    val connection = URL(modelsUrl).openConnection() as HttpURLConnection
    connection.connectTimeout = 10000
    connection.readTimeout = 10000
    connection.requestMethod = "GET"
    if (apiKey.isNotEmpty()) {
      connection.setRequestProperty("Authorization", "Bearer $apiKey")
    }
    connection.setRequestProperty("Content-Type", "application/json")

    if (connection.responseCode !in 200..299) return emptyList()

    val response = connection.inputStream.bufferedReader().readText()

    return when (provider.id) {
      "ollama" -> parseOllamaModels(response)
      else -> parseOpenAiModels(response)
    }
  }

  private fun parseOllamaModels(response: String): List<String> {
    val models = mutableListOf<String>()
    val regex = "\"name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
    regex.findAll(response).forEach { models.add(it.groupValues[1]) }
    return models
  }

  private fun parseOpenAiModels(response: String): List<String> {
    val models = mutableListOf<String>()
    val regex = "\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
    regex.findAll(response).forEach { models.add(it.groupValues[1]) }
    return models
  }

  private fun fallbackModels(provider: AiProvider): List<String> {
    return when (provider.id) {
      "gemini" -> listOf("gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-pro", "gemini-1.5-flash")
      "claude" -> listOf("claude-sonnet-4-20250514", "claude-3-5-sonnet-latest", "claude-3-5-haiku-latest", "claude-3-opus-latest")
      "openai" -> listOf("gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano", "o3", "o4-mini")
      "nvidia" -> listOf("meta/llama-3.1-405b-instruct", "mistralai/mistral-large-2-instruct")
      "groq" -> listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768", "gemma2-9b-it")
      "deepseek" -> listOf("deepseek-chat", "deepseek-reasoner")
      "mistral" -> listOf("mistral-large-latest", "mistral-medium-latest", "mistral-small-latest", "codestral-latest")
      "xai" -> listOf("grok-2-latest", "grok-beta")
      else -> emptyList()
    }
  }

  private fun showStatus(message: String, isSuccess: Boolean) {
    binding.connectionStatus.apply {
      text = message
      setTextColor(
        if (isSuccess) {
          ContextCompat.getColor(this@AIModelsActivity, android.R.color.holo_green_dark)
        } else {
          ContextCompat.getColor(this@AIModelsActivity, android.R.color.holo_red_dark)
        }
      )
      visibility = View.VISIBLE
    }
  }

  private fun savePreferences() {
    val provider = getSelectedProvider()
    if (provider == null) {
      Snackbar.make(binding.root, R.string.idepref_ai_select_provider, Snackbar.LENGTH_SHORT).show()
      return
    }

    val apiKey = binding.apiKeyInput.text?.toString()?.trim() ?: ""
    val baseUrl = binding.baseUrlInput.text?.toString()?.trim() ?: ""
    val endpoint = binding.endpointInput.text?.toString()?.trim() ?: ""
    val model = binding.modelDropdown.text?.toString()?.trim() ?: ""
    val systemPrompt = binding.systemPromptInput.text?.toString()?.trim() ?: ""

    if (provider.needsApiKey && apiKey.isEmpty()) {
      Snackbar.make(binding.root, R.string.idepref_ai_enter_api_key, Snackbar.LENGTH_SHORT).show()
      return
    }
    if (provider.needsBaseUrl && baseUrl.isEmpty()) {
      Snackbar.make(binding.root, R.string.idepref_ai_enter_base_url, Snackbar.LENGTH_SHORT).show()
      return
    }
    if (provider.needsEndpoint && endpoint.isEmpty()) {
      Snackbar.make(binding.root, R.string.idepref_ai_enter_endpoint, Snackbar.LENGTH_SHORT).show()
      return
    }

    AIModelsPreferences.provider = provider.id
    AIModelsPreferences.apiKey = apiKey
    AIModelsPreferences.baseUrl = baseUrl
    AIModelsPreferences.endpoint = endpoint
    AIModelsPreferences.model = model
    AIModelsPreferences.systemPrompt = systemPrompt

    Snackbar.make(binding.root, R.string.idepref_ai_saved, Snackbar.LENGTH_SHORT).show()
  }

  override fun onApplySystemBarInsets(insets: Insets) {
    binding.toolbar.apply {
      updatePaddingRelative(
        paddingStart + insets.left,
        paddingTop,
        paddingEnd + insets.right,
        paddingBottom
      )
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    _binding = null
  }

  private data class AiProvider(
    val id: String,
    val titleRes: Int,
    val iconRes: Int,
    val defaultBaseUrl: String,
    val needsApiKey: Boolean = true,
    val needsBaseUrl: Boolean = false,
    val needsEndpoint: Boolean = false,
  ) {
    private var _title: String? = null

    fun getTitle(context: Context): String {
      if (_title == null) {
        _title = context.getString(titleRes)
      }
      return _title!!
    }
  }

  private class ProviderAdapter(
    context: Context,
    private val providers: List<AiProvider>
  ) : ArrayAdapter<AiProvider>(context, 0, providers), Filterable {

    private val inflater = LayoutInflater.from(context)
    private val originalList = providers.toList()
    private var filteredList = providers.toMutableList()

    override fun getCount(): Int = filteredList.size

    override fun getItem(position: Int): AiProvider = filteredList[position]

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      val view = convertView ?: inflater.inflate(R.layout.item_provider_dropdown, parent, false)
      val provider = getItem(position)
      view.findViewById<ImageView>(R.id.provider_icon).setImageResource(provider.iconRes)
      view.findViewById<TextView>(R.id.provider_name).text = provider.getTitle(context)
      return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
      return getView(position, convertView, parent)
    }

    override fun getFilter(): Filter {
      return object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
          val query = constraint?.toString()?.lowercase() ?: ""
          filteredList = if (query.isEmpty()) {
            originalList.toMutableList()
          } else {
            originalList.filter {
              it.getTitle(context).lowercase().contains(query)
            }.toMutableList()
          }
          val results = FilterResults()
          results.values = filteredList
          results.count = filteredList.size
          return results
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
          filteredList = (results?.values as? List<AiProvider>)?.toMutableList() ?: originalList.toMutableList()
          notifyDataSetChanged()
        }
      }
    }
  }
}
