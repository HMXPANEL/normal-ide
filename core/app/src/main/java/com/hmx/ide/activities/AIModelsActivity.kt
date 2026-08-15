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
import com.hmx.ide.ai.storage.ProviderStorage
import com.hmx.ide.preferences.internal.AIModelsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class AIModelsActivity : EdgeToEdgeIDEActivity() {

  private var _binding: ActivityAiModelsBinding? = null
  private val binding: ActivityAiModelsBinding
    get() = checkNotNull(_binding) { "Activity has been destroyed" }

  override fun bindLayout(): View {
    _binding = ActivityAiModelsBinding.inflate(layoutInflater)
    return checkNotNull(_binding) { "Binding inflation failed" }.root
  }

  private val log = LoggerFactory.getLogger(AIModelsActivity::class.java)

  private val providers = listOf(
    AiProvider("gemini", R.string.idepref_ai_provider_gemini, R.drawable.ic_provider_gemini, "https://generativelanguage.googleapis.com"),
    AiProvider("claude", R.string.idepref_ai_provider_claude, R.drawable.ic_provider_claude, "https://api.anthropic.com"),
    AiProvider("openai", R.string.idepref_ai_provider_openai, R.drawable.ic_provider_openai, "https://api.openai.com"),
    AiProvider("openrouter", R.string.idepref_ai_provider_openrouter, R.drawable.ic_provider_openrouter, "https://openrouter.ai/api/v1"),
    AiProvider("nvidia", R.string.idepref_ai_provider_nvidia, R.drawable.ic_provider_nvidia, "https://integrate.api.nvidia.com"),
    AiProvider("groq", R.string.idepref_ai_provider_groq, R.drawable.ic_provider_groq, "https://api.groq.com"),
    AiProvider("deepseek", R.string.idepref_ai_provider_deepseek, R.drawable.ic_provider_deepseek, "https://api.deepseek.com"),
    AiProvider("mistral", R.string.idepref_ai_provider_mistral, R.drawable.ic_provider_mistral, "https://api.mistral.ai"),
    AiProvider("togetherai", R.string.idepref_ai_provider_togetherai, R.drawable.ic_provider_togetherai, "https://api.together.ai"),
    AiProvider("fireworks", R.string.idepref_ai_provider_fireworks, R.drawable.ic_provider_fireworks, "https://api.fireworks.ai"),
    AiProvider("xai", R.string.idepref_ai_provider_xai, R.drawable.ic_provider_xai, "https://api.x.ai"),
    AiProvider("opencode", R.string.idepref_ai_provider_opencode, R.drawable.ic_provider_opencode, "https://opencode.ai/zen/v1", needsBaseUrl = true),
    AiProvider("custom", R.string.idepref_ai_provider_custom, R.drawable.ic_provider_custom, "", needsApiKey = true, needsBaseUrl = true),
  )

  private var previousProviderId: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding.apply {
      setSupportActionBar(toolbar)
      checkNotNull(supportActionBar) { "Action bar not available" }.setDisplayHomeAsUpEnabled(true)
      checkNotNull(supportActionBar) { "Action bar not available" }.setTitle(R.string.idepref_ai_models_title)
      toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

      setupProviderDropdown()
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
    binding.connectionStatus.visibility = View.GONE

    // Load saved settings for the selected provider
    binding.apiKeyInput.setText(AIModelsPreferences.getApiKey(provider.id))
    binding.baseUrlInput.setText(AIModelsPreferences.getBaseUrl(provider.id))
    binding.endpointInput.setText(AIModelsPreferences.getEndpoint(provider.id))
    binding.modelDropdown.setText(AIModelsPreferences.getModel(provider.id), false)
    binding.systemPromptInput.setText(AIModelsPreferences.getSystemPrompt(provider.id))

    updateApiFieldsVisibility()
  }

  private fun setupProviderDropdown() {
    val adapter = ProviderAdapter(this, providers)
    binding.providerDropdown.setAdapter(adapter)
  }

  private fun loadSavedPreferences() {
    val savedProvider = AIModelsPreferences.currentProvider
    val provider = providers.find { it.id == savedProvider }
    if (provider != null) {
      previousProviderId = provider.id
      binding.providerDropdown.setText(provider.getTitle(this), false)
      binding.apiKeyInput.setText(AIModelsPreferences.getApiKey(provider.id))
      binding.baseUrlInput.setText(AIModelsPreferences.getBaseUrl(provider.id))
      binding.endpointInput.setText(AIModelsPreferences.getEndpoint(provider.id))
      binding.modelDropdown.setText(AIModelsPreferences.getModel(provider.id), false)
      binding.systemPromptInput.setText(AIModelsPreferences.getSystemPrompt(provider.id))
    }
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

  private fun testConnection() {
    val provider = getSelectedProvider()
    if (provider == null) {
      showStatus(getString(R.string.idepref_ai_select_provider), false)
      return
    }

    val apiKey = binding.apiKeyInput.text?.toString()?.trim() ?: ""
    val baseUrl = binding.baseUrlInput.text?.toString()?.trim() ?: ""
    val endpoint = binding.endpointInput.text?.toString()?.trim() ?: ""

    if (provider.needsApiKey && apiKey.isEmpty()) {
      showStatus(getString(R.string.idepref_ai_enter_api_key), false)
      return
    }
    if (provider.needsBaseUrl && baseUrl.isEmpty()) {
      showStatus(getString(R.string.idepref_ai_enter_base_url), false)
      return
    }
    if (provider.needsEndpoint && endpoint.isEmpty()) {
      showStatus(getString(R.string.idepref_ai_enter_endpoint), false)
      return
    }

    binding.testConnectionBtn.isEnabled = false
    binding.testConnectionBtn.text = getString(R.string.please_wait)

    CoroutineScope(Dispatchers.IO).launch {
      val result = performConnectionTest(provider, apiKey, baseUrl, endpoint)
      withContext(Dispatchers.Main) {
        binding.testConnectionBtn.isEnabled = true
        binding.testConnectionBtn.text = getString(R.string.idepref_ai_test_connection)
        when (result) {
          is ConnectionResult.Success -> {
            showStatus(getString(R.string.idepref_ai_connected_successfully), true)
            fetchModels(provider, apiKey, baseUrl, endpoint)
          }
          is ConnectionResult.Failure -> {
            showStatus(result.reason, false)
          }
        }
      }
    }
  }

  private fun performConnectionTest(
    provider: AiProvider, apiKey: String, baseUrl: String, endpoint: String
  ): ConnectionResult {
    val handler = providerHandler(provider.id)
    val config = handler.testConnectionConfig(apiKey, baseUrl, endpoint)

    log.info("Testing connection for {}: {} {} headers={}",
      provider.id, config.method, config.url, config.headers.keys)

    return try {
      val (code, _) = executeHttp(config, connectTimeout = 8000, readTimeout = 8000)
      log.info("Connection test response for {}: HTTP {}", provider.id, code)

      when (code) {
        in 200..299 -> ConnectionResult.Success(code)
        401 -> ConnectionResult.Failure(getString(R.string.idepref_ai_error_unauthorized))
        403 -> ConnectionResult.Failure(getString(R.string.idepref_ai_error_forbidden))
        404 -> ConnectionResult.Failure(getString(R.string.idepref_ai_error_invalid_url))
        429 -> ConnectionResult.Failure(getString(R.string.idepref_ai_error_unknown,
          "Rate limited (429)"))
        in 400..499 -> ConnectionResult.Failure(
          getString(R.string.idepref_ai_error_unknown, "HTTP $code"))
        in 500..599 -> ConnectionResult.Failure(
          getString(R.string.idepref_ai_error_unknown, "Server error ($code)"))
        else -> ConnectionResult.Failure(
          getString(R.string.idepref_ai_error_unknown, "HTTP $code"))
      }
    } catch (e: UnknownHostException) {
      log.error("DNS resolution failed for {}: {}", provider.id, e.message)
      ConnectionResult.Failure(getString(R.string.idepref_ai_error_dns, e.localizedMessage ?: ""))
    } catch (e: SocketTimeoutException) {
      log.error("Connection timed out for {}: {}", provider.id, e.message)
      ConnectionResult.Failure(getString(R.string.idepref_ai_error_timeout))
    } catch (e: SSLException) {
      log.error("SSL error for {}: {}", provider.id, e.message)
      ConnectionResult.Failure(getString(R.string.idepref_ai_error_ssl, e.localizedMessage ?: ""))
    } catch (e: ConnectException) {
      log.error("Connection refused for {}: {}", provider.id, e.message)
      ConnectionResult.Failure(getString(R.string.idepref_ai_error_refused, e.localizedMessage ?: ""))
    } catch (e: IllegalArgumentException) {
      log.error("Invalid URL for {}: {}", provider.id, e.message)
      ConnectionResult.Failure(getString(R.string.idepref_ai_error_invalid_url))
    } catch (e: SecurityException) {
      log.error("Security exception for {}: {}", provider.id, e.message)
      ConnectionResult.Failure(getString(R.string.idepref_ai_error_blocked, e.localizedMessage ?: ""))
    } catch (e: Exception) {
      log.error("Connection test failed for {}: {}: {}",
        provider.id, e.javaClass.simpleName, e.localizedMessage)
      ConnectionResult.Failure(
        getString(R.string.idepref_ai_error_unknown,
          "${e.javaClass.simpleName}: ${e.localizedMessage}"))
    }
  }

  private fun fetchModels(
    provider: AiProvider, apiKey: String, baseUrl: String, endpoint: String
  ) {
    binding.modelDropdown.setText("")
    val loadingMsg = getString(R.string.idepref_ai_fetching_models)
    binding.modelDropdown.setAdapter(ArrayAdapter(this,
      android.R.layout.simple_dropdown_item_1line, listOf(loadingMsg)))
    binding.modelDropdown.dismissDropDown()

    CoroutineScope(Dispatchers.IO).launch {
      val models = try {
        fetchModelsFromProvider(provider, apiKey, baseUrl, endpoint)
      } catch (e: Exception) {
        log.error("Model fetch failed for {}: {}: {}",
          provider.id, e.javaClass.simpleName, e.localizedMessage)
        emptyList()
      }
      withContext(Dispatchers.Main) {
        if (models.isNotEmpty()) {
          binding.modelDropdown.setAdapter(ArrayAdapter(this@AIModelsActivity,
            android.R.layout.simple_dropdown_item_1line, models))
          val savedModel = AIModelsPreferences.getModel(provider.id)
          binding.modelDropdown.setText(
            savedModel.takeIf { it in models } ?: models.first(), false)
        } else {
          binding.modelDropdown.setAdapter(ArrayAdapter(this@AIModelsActivity,
            android.R.layout.simple_dropdown_item_1line,
            listOf(getString(R.string.idepref_ai_no_models))))
          showStatus(getString(R.string.idepref_ai_fetch_models_failed), false)
        }
      }
    }
  }

  private fun fetchModelsFromProvider(
    provider: AiProvider, apiKey: String, baseUrl: String, endpoint: String
  ): List<String> {
    val handler = providerHandler(provider.id)
    val config = handler.fetchModelsConfig(apiKey, baseUrl, endpoint)

    log.info("Fetching models for {}: {} {} headers={}",
      provider.id, config.method, config.url, config.headers.keys)

    val (code, response) = executeHttp(config, connectTimeout = 10000, readTimeout = 10000)
    if (code !in 200..299) {
      log.warn("Model fetch for {} returned HTTP {}", provider.id, code)
      return emptyList()
    }

    return handler.parseModels(response)
  }

  private sealed class ConnectionResult {
    data class Success(val responseCode: Int) : ConnectionResult()
    data class Failure(val reason: String) : ConnectionResult()
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

    // Save per-provider — no global keys, each provider is independent
    AIModelsPreferences.currentProvider = provider.id
    AIModelsPreferences.setApiKey(provider.id, apiKey)
    AIModelsPreferences.setBaseUrl(provider.id, baseUrl)
    AIModelsPreferences.setEndpoint(provider.id, endpoint)
    AIModelsPreferences.setModel(provider.id, model)
    AIModelsPreferences.setSystemPrompt(provider.id, systemPrompt)

    // Temporary dual-write to new ProviderStorage during migration
    val ps = ProviderStorage(this)
    ps.setApiKey(provider.id, apiKey)
    ps.setBaseUrl(provider.id, baseUrl)
    ps.setModel(provider.id, model)
    ps.setActiveProviderId(provider.id)
    // endpoint and systemPrompt not migrated — new system uses baseUrl + per-chat prompt

    log.info("Saved preferences for provider {}", provider.id)
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
      return _title ?: context.getString(titleRes)
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
