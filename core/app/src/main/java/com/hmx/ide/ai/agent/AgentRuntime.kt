package com.hmx.ide.ai.agent

import com.hmx.ide.ai.agent.core.AgentConfig
import com.hmx.ide.ai.agent.core.AgentLoop
import com.hmx.ide.ai.agent.core.AgentModel
import com.hmx.ide.ai.agent.permissions.PermissionManager
import com.hmx.ide.ai.agent.tools.FindSymbolTool
import com.hmx.ide.ai.agent.tools.ReadFileTool
import com.hmx.ide.ai.agent.tools.SearchTool
import com.hmx.ide.ai.agent.tools.ToolExecutor
import com.hmx.ide.ai.agent.tools.ToolRegistry
import com.hmx.ide.ai.agent.trace.AgentTrace
import com.hmx.ide.knowledge.KnowledgeEngine
import com.hmx.ide.knowledge.KnowledgeEngineImpl
import java.io.File

/**
 * Minimal coexistence entry point (Phase 1, §20 of the brief).
 *
 * Builds a READ-only registry + loop without touching `AIChatActivity`,
 * `ChatEngine`, or any provider. Existing chat keeps working; the agent
 * runtime lives alongside it until later phases wire deeper integration.
 */
object AgentRuntime {

  fun defaultRegistry(
    projectRoot: File,
    engine: KnowledgeEngine = KnowledgeEngineImpl,
  ): ToolRegistry = ToolRegistry().apply {
    register(ReadFileTool(projectRoot))
    register(SearchTool(projectRoot))
    register(FindSymbolTool(engine))
  }

  fun createLoop(
    model: AgentModel,
    projectRoot: File,
    engine: KnowledgeEngine = KnowledgeEngineImpl,
    config: AgentConfig = AgentConfig(),
    trace: AgentTrace = AgentTrace(),
  ): AgentLoop {
    val registry = defaultRegistry(projectRoot, engine)
    return AgentLoop(
      model = model,
      registry = registry,
      executorFactory = { loopTrace, sessionId ->
        val effective = config.validated()
        ToolExecutor(
          registry = registry,
          permissions = PermissionManager(effective.permissionPolicy),
          trace = if (effective.traceEnabled) loopTrace else null,
          sessionId = sessionId,
        )
      },
      config = config,
      trace = trace,
    )
  }
}
