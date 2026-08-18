package com.hmx.ide.ai

import com.google.common.truth.Truth.assertThat
import com.hmx.ide.ai.engine.ChatEngine
import com.hmx.ide.ai.engine.ModelManager
import com.hmx.ide.ai.models.AiModel
import com.hmx.ide.ai.models.Capability
import com.hmx.ide.ai.models.ChatMessage
import com.hmx.ide.ai.models.ChatRequest
import com.hmx.ide.ai.models.ChatResponse
import com.hmx.ide.ai.models.Chunk
import com.hmx.ide.ai.models.Role
import com.hmx.ide.ai.registry.ProviderRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class AiEngineTest {

  private val fakeProvider = FakeProvider()
  private val engine = AiEngine()

  @Before
  fun setUp() {
    ProviderRegistry.register(fakeProvider)
    ProviderRegistry.activeProviderId = "test"
  }

  @After
  fun tearDown() {
    ProviderRegistry.activeProviderId = null
    ProviderRegistry.unregister("test")
  }

  // --- ModelManager ---

  @Test
  fun `model manager caches and refreshes`() = runBlocking {
    val manager = ModelManager()

    val first = manager.getModels("test")
    assertThat(first).containsExactly(AiModel(id = "m1"))

    fakeProvider.models = listOf(AiModel(id = "m2"))
    val cached = manager.getModels("test")
    assertThat(cached).containsExactly(AiModel(id = "m1"))

    val refreshed = manager.getModels("test", forceRefresh = true)
    assertThat(refreshed).containsExactly(AiModel(id = "m2"))

    assertThat(manager.cachedModels("unknown")).isNull()
  }

  @Test
  fun `model manager refreshAll skips failed providers`() = runBlocking {
    val manager = ModelManager()

    fakeProvider.fail = true
    manager.refreshAll()
    assertThat(manager.cachedModels("test")).isNull()
  }

  // --- ChatEngine ---

  @Test
  fun `chat engine send appends user and assistant messages`() = runBlocking {
    val chat = ChatEngine(engine)

    chat.send("m1", "hello")
    val history = chat.history()
    assertThat(history).hasSize(2)
    assertThat(history[0]).isEqualTo(ChatMessage(Role.user, "hello"))
    assertThat(history[1].role).isEqualTo(Role.assistant)
  }

  @Test
  fun `chat engine clear resets history`() = runBlocking {
    val chat = ChatEngine(engine)

    chat.send("m1", "hello")
    assertThat(chat.history()).hasSize(2)
    chat.clear()
    assertThat(chat.history()).isEmpty()
  }

  @Test
  fun `chat engine stream collects content`() = runBlocking {
    val chat = ChatEngine(engine)

    val chunks = mutableListOf<Chunk>()
    val response = chat.stream("m1", "hello", onChunk = { chunks.add(it) })

    assertThat(chunks.map { it.content }.joinToString("")).isEqualTo("response")
    assertThat(response.message.content).isEqualTo("response")
  }

  // --- Capabilities ---

  @Test
  fun `capabilities activeSupports checks active provider`() {
    assertThat(fakeProvider.supports(Capability.streaming)).isTrue()
    assertThat(fakeProvider.supports(Capability.vision)).isFalse()
  }

  private class FakeProvider : AiProvider {
    var models: List<AiModel> = listOf(AiModel(id = "m1"))
    var fail: Boolean = false

    override val providerId: String = "test"
    override val displayName: String = "Test"
    override val capabilities: Set<Capability> = setOf(Capability.streaming)

    override suspend fun chat(request: ChatRequest): ChatResponse =
      ChatResponse(message = ChatMessage(Role.assistant, "response"))

    override fun stream(request: ChatRequest): Flow<Chunk> =
      flowOf(Chunk(content = "response", finishReason = "stop"))

    override suspend fun listModels(): List<AiModel> =
      if (fail) throw RuntimeException("fail") else models

    override suspend fun testConnection(): Boolean = !fail
  }
}
