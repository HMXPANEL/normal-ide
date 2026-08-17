package com.hmx.ide.build

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Secure storage for the GitHub personal access token used to trigger and
 * monitor remote builds.
 *
 * The token is kept in [EncryptedSharedPreferences] (AES256-GCM values,
 * AES256-SIV keys). It is NEVER written to plain SharedPreferences, never
 * logged, and never exposed to AI providers.
 */
class GitHubTokenStorage(context: Context) {

  private val prefs: SharedPreferences =
    EncryptedSharedPreferences.create(
      "hmx_github_encrypted",
      MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
      context,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

  fun getToken(): String = prefs.getString(KEY_TOKEN, "") ?: ""

  fun hasToken(): Boolean = getToken().isNotBlank()

  fun setToken(token: String) {
    prefs.edit().putString(KEY_TOKEN, token).apply()
  }

  fun clear() {
    prefs.edit().clear().apply()
  }

  companion object {
    private const val KEY_TOKEN = "github_token"
  }
}
