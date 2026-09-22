package com.hmx.ide.ai.registry

import com.hmx.ide.ai.AiProvider
import com.hmx.ide.ai.errors.ProviderException

object ProviderRegistry {

  private val providers = linkedMapOf<String, AiProvider>()

  fun register(provider: AiProvider) {
    providers[provider.providerId] = provider
  }

  fun unregister(providerId: String) {
    providers.remove(providerId)
  }

  fun get(providerId: String): AiProvider? = providers[providerId]

  fun require(providerId: String): AiProvider =
    providers[providerId] ?: throw ProviderException("No provider registered for '$providerId'")

  fun all(): List<AiProvider> = providers.values.toList()

  fun registeredIds(): Set<String> = providers.keys

  var activeProviderId: String? = null

  fun active(): AiProvider? =
    activeProviderId?.let { providers[it] }
}
