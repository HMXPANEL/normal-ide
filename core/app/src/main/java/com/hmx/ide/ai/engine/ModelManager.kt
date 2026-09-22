package com.hmx.ide.ai.engine

import com.hmx.ide.ai.models.AiModel
import com.hmx.ide.ai.registry.ProviderRegistry
import java.util.concurrent.ConcurrentHashMap

class ModelManager {

  private val cache = ConcurrentHashMap<String, List<AiModel>>()

  suspend fun getModels(providerId: String, forceRefresh: Boolean = false): List<AiModel> {
    if (!forceRefresh) cache[providerId]?.let { return it }
    val models = ProviderRegistry.require(providerId).listModels()
    cache[providerId] = models
    return models
  }

  suspend fun refreshAll() {
    cache.clear()
    ProviderRegistry.all().forEach { provider ->
      try {
        cache[provider.providerId] = provider.listModels()
      } catch (_: Exception) { }
    }
  }

  fun cachedModels(providerId: String): List<AiModel>? = cache[providerId]

  fun clearCache() { cache.clear() }
}
