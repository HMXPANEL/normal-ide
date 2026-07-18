package com.hmx.ide.lsp.java.actions.common

import com.google.googlejavaformat.java.FormatterException
import com.google.googlejavaformat.java.RemoveUnusedImports
import com.hmx.ide.actions.ActionData
import com.hmx.ide.actions.hasRequiredData
import com.hmx.ide.actions.markInvisible
import com.hmx.ide.actions.requireEditor
import com.hmx.ide.lsp.java.actions.BaseJavaCodeAction
import com.hmx.ide.resources.R.string
import io.github.rosemoe.sora.widget.CodeEditor
import org.slf4j.LoggerFactory

class RemoveUnusedImportsAction : BaseJavaCodeAction() {

  override val id: String = "ide.editor.lsp.java.removeUnusedImports"
  override var label: String = ""
  override val titleTextRes: Int = string.action_remove_unused_imports

  companion object {

    private val log = LoggerFactory.getLogger(RemoveUnusedImportsAction::class.java)
  }

  override fun prepare(data: ActionData) {
    super.prepare(data)
    if (!visible) {
      return
    }

    if (!data.hasRequiredData(CodeEditor::class.java)) {
      markInvisible()
      return
    }

    visible = true
    enabled = true
  }

  override suspend fun execAction(data: ActionData): Any {
    val watch = com.hmx.ide.utils.StopWatch("Remove unused imports")
    return try {
      val editor = data.requireEditor()
      val content = editor.text
      val output = RemoveUnusedImports.removeUnusedImports(content.toString())
      watch.log()
      output
    } catch (e: FormatterException) {
      log.error("Failed to remove unused imports", e)
      false
    }
  }

  override fun postExec(data: ActionData, result: Any) {
    if (result is String && result.isNotEmpty()) {
      val editor = data.requireEditor()
      editor.setText(result)
    }
  }
}
