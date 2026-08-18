package com.hmx.ide.ai.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.hmx.ide.ai.registry.ProviderRegistry

class ProviderStorage(context: Context) {

  private val prefs: SharedPreferences =
    EncryptedSharedPreferences.create(
      "hmx_ai_providers_encrypted",
      MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
      context,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

  init {
    getActiveProviderId()?.let { ProviderRegistry.activeProviderId = it }
  }

  fun getApiKey(providerId: String): String =
    prefs.getString(key(providerId, "api_key"), "") ?: ""

  fun setApiKey(providerId: String, key: String) {
    prefs.edit().putString(key(providerId, "api_key"), key).apply()
  }

  fun getBaseUrl(providerId: String): String =
    prefs.getString(key(providerId, "base_url"), "") ?: ""

  fun setBaseUrl(providerId: String, url: String) {
    prefs.edit().putString(key(providerId, "base_url"), url).apply()
  }

  fun getModel(providerId: String): String =
    prefs.getString(key(providerId, "model"), "") ?: ""

  fun setModel(providerId: String, model: String) {
    prefs.edit().putString(key(providerId, "model"), model).apply()
  }

  fun getActiveProviderId(): String? =
    prefs.getString("active_provider_id", null)

  fun setActiveProviderId(providerId: String?) {
    prefs.edit().putString("active_provider_id", providerId).apply()
    ProviderRegistry.activeProviderId = providerId
  }

  fun clear() {
    prefs.edit().clear().apply()
  }

  private fun key(providerId: String, field: String): String =
    "provider_${providerId}_$field"
}