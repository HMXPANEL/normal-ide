package com.hmx.ide.knowledge

import com.hmx.ide.eventbus.events.editor.ChangeType
import com.hmx.ide.eventbus.events.editor.DocumentChangeEvent
import java.nio.file.Path

class FileContentBuffer {

  private val buffers = mutableMapOf<Path, String>()

  fun open(path: Path, text: String) {
    buffers[path] = text
  }

  fun applyChange(path: Path, event: DocumentChangeEvent) {
    val old = buffers[path] ?: return
    val startIdx = event.changeRange.start.index
    val endIdx = event.changeRange.end.index
    val validIndices = startIdx >= 0 && endIdx >= 0

    val sb = StringBuilder(old)
    when (event.changeType) {
      ChangeType.INSERT -> {
        val pos = if (validIndices) startIdx else toOffset(old, event.changeRange.start)
        sb.insert(pos.coerceIn(0, sb.length), event.changedText)
      }
      ChangeType.DELETE -> {
        val s = if (validIndices) startIdx else toOffset(old, event.changeRange.start)
        val e = if (validIndices) endIdx else toOffset(old, event.changeRange.end)
        sb.delete(s.coerceIn(0, sb.length), e.coerceIn(0, sb.length))
      }
      ChangeType.NEW_TEXT -> {
        val s = if (validIndices) startIdx else toOffset(old, event.changeRange.start)
        val e = if (validIndices) endIdx else toOffset(old, event.changeRange.end)
        val replacement = event.newText ?: event.changedText
        sb.replace(s.coerceIn(0, sb.length), e.coerceIn(0, sb.length), replacement)
      }
    }
    buffers[path] = sb.toString()
  }

  fun close(path: Path) {
    buffers.remove(path)
  }

  fun get(path: Path): String? = buffers[path]

  fun isOpen(path: Path): Boolean = buffers.containsKey(path)

  fun clear() {
    buffers.clear()
  }

  private fun toOffset(text: String, pos: com.hmx.ide.models.Position): Int {
    var line = 0
    var col = 0
    for (i in text.indices) {
      if (line == pos.line && col == pos.column) return i
      if (text[i] == '\n') { line++; col = 0; continue }
      col++
    }
    return text.length
  }
}
