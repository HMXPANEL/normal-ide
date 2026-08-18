package com.hmx.ide.ai.context

import com.hmx.ide.eventbus.events.editor.DocumentOpenEvent
import com.hmx.ide.eventbus.events.editor.DocumentSelectedEvent
import com.hmx.ide.eventbus.events.editor.DocumentCloseEvent
import com.hmx.ide.projects.IProjectManager
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

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

  private val openFiles = mutableListOf<String>()

  fun init() {
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }
  }

  fun collectContext(): EditorContext {
    val now = System.currentTimeMillis()
    if (cachedContext != null && (now - lastUpdate) < 5000) {
      return cachedContext!!
    }

    cachedContext = EditorContext(
      projectDir = runCatching {
        IProjectManager.getInstance().projectDir?.absolutePath
      }.getOrNull(),
      openTabs = openFiles.toList(),
    )

    lastUpdate = now
    return cachedContext!!
  }

  fun pushEditorState(ctx: EditorContext) {
    cachedContext = ctx
    lastUpdate = System.currentTimeMillis()
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onDocumentOpen(event: DocumentOpenEvent) {
    val path = event.openedFile.toString()
    if (!openFiles.contains(path)) openFiles.add(path)
    cachedContext = EditorContext(
      currentFile = path,
      openTabs = openFiles.toList(),
      projectDir = runCatching {
        IProjectManager.getInstance().projectDir?.absolutePath
      }.getOrNull(),
    )
    lastUpdate = System.currentTimeMillis()
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onDocumentSelected(event: DocumentSelectedEvent) {
    val path = event.selectedFile.toString()
    cachedContext?.let { ctx ->
      cachedContext = ctx.copy(currentFile = path)
    }
    lastUpdate = System.currentTimeMillis()
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onDocumentClose(event: DocumentCloseEvent) {
    val path = event.closedFile.toString()
    openFiles.remove(path)
    cachedContext = EditorContext(
      openTabs = openFiles.toList(),
      projectDir = runCatching {
        IProjectManager.getInstance().projectDir?.absolutePath
      }.getOrNull(),
    )
    lastUpdate = System.currentTimeMillis()
  }

  fun invalidate() {
    cachedContext = null
  }
}