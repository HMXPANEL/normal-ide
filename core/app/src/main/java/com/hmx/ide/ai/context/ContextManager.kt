package com.hmx.ide.ai.context

import java.io.File

data class EditorContext(
  val currentFile: String? = null,
  val selectedCode: String? = null,
  val cursorLine: Int? = null,
  val cursorColumn: Int? = null,
  val openTabs: List<String> = emptyList(),
  val projectDir: String? = null,
)

object ContextManager {

  private var cachedContext: EditorContext? = null
  private var lastUpdate = 0L

  fun collectContext(): EditorContext {
    val now = System.currentTimeMillis()
    if (cachedContext != null && (now - lastUpdate) < 5000) {
      return cachedContext!!
    }

    val projectDir = runCatching {
      IProjectManager.getInstance().projectDir?.absolutePath
    }.getOrNull()

    val ctx = EditorContext(
      currentFile = null,
      selectedCode = null,
      cursorLine = null,
      cursorColumn = null,
      openTabs = emptyList(),
      projectDir = projectDir,
    )

    cachedContext = ctx
    lastUpdate = now
    return ctx
  }

  fun invalidate() {
    cachedContext = null
  }
}