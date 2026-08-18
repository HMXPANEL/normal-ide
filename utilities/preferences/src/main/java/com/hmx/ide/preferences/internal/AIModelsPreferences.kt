package com.hmx.ide.preferences.internal

/**
 * Legacy per-provider preference store. This is the old System A persistence
 * layer. New code should use [com.hmx.ide.ai.storage.ProviderStorage] instead.
 *
 * Dual-write between this and ProviderStorage is active during migration.
 * Remove this file once AIChatActivity is fully migrated to the new AI engine
 * and no code reads from AIModelsPreferences.
 *
 * Note: API keys and base URLs are stored in plain SharedPreferences here.
 * Migrate to [com.hmx.ide.ai.storage.ProviderStorage] which uses EncryptedSharedPreferences.
 */
object AIModelsPreferences {

  private const val CURRENT_PROVIDER = "ai_prefs_current_provider"

  fun key(providerId: String, suffix: String) = "ai_prefs_${providerId}_$suffix"

  var currentProvider: String
    get() = prefManager.getString(CURRENT_PROVIDER, "openai") ?: "openai"
    set(value) { prefManager.putString(CURRENT_PROVIDER, value) }

  fun getApiKey(providerId: String): String =
    prefManager.getString(key(providerId, "api_key"), "") ?: ""

  fun setApiKey(providerId: String, value: String) {
    prefManager.putString(key(providerId, "api_key"), value)
  }

  fun getBaseUrl(providerId: String): String =
    prefManager.getString(key(providerId, "base_url"), "") ?: ""

  fun setBaseUrl(providerId: String, value: String) {
    prefManager.putString(key(providerId, "base_url"), value)
  }

  fun getEndpoint(providerId: String): String =
    prefManager.getString(key(providerId, "endpoint"), "") ?: ""

  fun setEndpoint(providerId: String, value: String) {
    prefManager.putString(key(providerId, "endpoint"), value)
  }

  fun getModel(providerId: String): String =
    prefManager.getString(key(providerId, "model"), "") ?: ""

  fun setModel(providerId: String, value: String) {
    prefManager.putString(key(providerId, "model"), value)
  }

  fun getSystemPrompt(providerId: String): String =
    prefManager.getString(key(providerId, "system_prompt"),
      "You are a helpful AI coding assistant.") ?: "You are a helpful AI coding assistant."

  fun setSystemPrompt(providerId: String, value: String) {
    prefManager.putString(key(providerId, "system_prompt"), value)
  }
}