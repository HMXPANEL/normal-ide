package com.hmx.ide.preferences.internal

object AIModelsPreferences {

  const val PROVIDER = "idepref_ai_provider"
  const val API_KEY = "idepref_ai_api_key"
  const val BASE_URL = "idepref_ai_base_url"
  const val ENDPOINT = "idepref_ai_endpoint"
  const val MODEL = "idepref_ai_model"
  const val SYSTEM_PROMPT = "idepref_ai_system_prompt"

  var provider: String
    get() = prefManager.getString(PROVIDER, "ollama")
    set(value) {
      prefManager.putString(PROVIDER, value)
    }

  var apiKey: String
    get() = prefManager.getString(API_KEY, "")
    set(value) {
      prefManager.putString(API_KEY, value)
    }

  var baseUrl: String
    get() = prefManager.getString(BASE_URL, "")
    set(value) {
      prefManager.putString(BASE_URL, value)
    }

  var endpoint: String
    get() = prefManager.getString(ENDPOINT, "")
    set(value) {
      prefManager.putString(ENDPOINT, value)
    }

  var model: String
    get() = prefManager.getString(MODEL, "")
    set(value) {
      prefManager.putString(MODEL, value)
    }

  var systemPrompt: String
    get() = prefManager.getString(SYSTEM_PROMPT, "You are a helpful AI coding assistant.")
    set(value) {
      prefManager.putString(SYSTEM_PROMPT, value)
    }
}
