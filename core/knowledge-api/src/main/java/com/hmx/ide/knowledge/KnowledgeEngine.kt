package com.hmx.ide.knowledge

import com.hmx.ide.knowledge.model.DeclarationModel
import com.hmx.ide.knowledge.model.FileModel
import com.hmx.ide.knowledge.model.ProjectModel
import com.hmx.ide.knowledge.model.SymbolLocation
import java.io.File

interface KnowledgeEngine {

  val currentProject: ProjectModel?

  /** Register EventBus listeners (safe to call before any project is opened). */
  fun start()
  /** Scan and index the given project. */
  fun refresh(projectDir: File)

  fun getFile(file: File): FileModel?
  fun searchSymbol(fqn: String): SymbolLocation?
  fun searchSymbols(prefix: String): List<SymbolLocation>
  fun findDeclarationsInFile(file: File): List<DeclarationModel>

  fun invalidateFile(file: File)
  fun invalidateAll()
}
