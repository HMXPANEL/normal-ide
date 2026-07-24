package com.hmx.ide.ai.capabilities

import com.hmx.ide.ai.AiProvider
import com.hmx.ide.ai.models.Capability
import com.hmx.ide.ai.registry.ProviderRegistry

object Capabilities {

  fun providersWith(capability: Capability): List<AiProvider> =
    ProviderRegistry.all().filter { it.supports(capability) }

  fun activeSupports(capability: Capability): Boolean =
    ProviderRegistry.active()?.supports(capability) ?: false
}

fun AiProvider.supports(capability: Capability): Boolean = capability in capabilities
