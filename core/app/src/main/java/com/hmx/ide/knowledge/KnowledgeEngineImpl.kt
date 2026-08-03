package com.hmx.ide.knowledge

import com.hmx.ide.ai.context.ProjectAnalyzer
import com.hmx.ide.ai.context.ProjectIndex
import com.hmx.ide.ai.context.ProjectScanner
import com.hmx.ide.eventbus.events.editor.DocumentChangeEvent
import com.hmx.ide.eventbus.events.editor.DocumentCloseEvent
import com.hmx.ide.eventbus.events.editor.DocumentOpenEvent
import com.hmx.ide.eventbus.events.editor.DocumentSaveEvent
import com.hmx.ide.indexing.UnifiedIndex
import com.hmx.ide.knowledge.model.DeclarationModel
import com.hmx.ide.knowledge.model.FileModel
import com.hmx.ide.knowledge.model.ModuleModel
import com.hmx.ide.knowledge.model.ProjectModel
import com.hmx.ide.knowledge.model.SymbolLocation
import com.hmx.ide.knowledge.index.SymbolIndex
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File

object KnowledgeEngineImpl : KnowledgeEngine {

  private val index = SymbolIndex(maxSize = 5000)
  private var indexer = IncrementalIndexer(index, rootDir = { rootDir })

  @Volatile
  private var _currentProject: ProjectModel? = null

  @Volatile
  private var rootDir: File? = null

  /**
   * The single source of truth for all index data produced by [refresh].
   * Null until the first [refresh] completes.
   */
  var unifiedIndex: UnifiedIndex? = null

  /** Performance counters — reset on each [refresh]. */
  private var _scanCount: Int = 0
  private var _fileReadCount: Int = 0
  private var _refreshDurationMs: Long = 0

  val scanCount get() = _scanCount
  val fileReadCount get() = _fileReadCount
  val refreshDurationMs get() = _refreshDurationMs

  override val currentProject: ProjectModel? get() = _currentProject

  override fun start() {
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }
  }

  @Synchronized
  override fun refresh(projectDir: File) {
    _scanCount = 0
    _fileReadCount = 0
    val start = System.currentTimeMillis()
    rootDir = projectDir
    indexer.destroy()
    indexer = IncrementalIndexer(index, rootDir = { rootDir })
    index.clear()
    val root = projectDir
    _scanCount++
    val scan = ProjectScanner.scan(root)
    val analysis = ProjectAnalyzer.analyze(root, scan)
    unifiedIndex = UnifiedIndex(symbols = index, project = analysis)
    val modules = analysis.context.modules.map { name ->
      ModuleModel(name = name, basePath = "$root/$name", files = emptyList())
    }
    _currentProject = ProjectModel(
      projectDir = root.absolutePath,
      modules = modules,
    )
    for (file in scan.javaFiles + scan.kotlinFiles) {
      _fileReadCount++
      val content = runCatching { file.readText() }.getOrNull() ?: continue
      val model = FileParser.parse(file, root, content)
      if (model.packageName != null) {
        for (decl in model.declarations) {
          index.add(decl.fqn, SymbolLocation(file.absolutePath, decl.line, decl.column), file.absolutePath, decl)
        }
      }
      index.addFile(file.relativeTo(root).path, model)
    }
    _refreshDurationMs = System.currentTimeMillis() - start
  }

  @Synchronized
  override fun getFile(file: File): FileModel? {
    val root = rootDir ?: return null
    val content = runCatching { file.readText() }.getOrNull() ?: return null
    return FileParser.parse(file, root, content)
  }

  @Synchronized
  override fun searchSymbol(fqn: String): SymbolLocation? = index.lookup(fqn)

  @Synchronized
  override fun searchSymbols(prefix: String): List<SymbolLocation> = index.search(prefix)

  @Synchronized
  override fun findDeclarationsInFile(file: File): List<DeclarationModel> {
    return index.fileDeclarations(file.absolutePath)
  }

  @Synchronized
  override fun invalidateFile(file: File) {
    indexer.forceReindex(file)
  }

  @Synchronized
  override fun invalidateAll() {
    index.clear()
    unifiedIndex = null
    val root = rootDir ?: return
    refresh(root)
  }

  @Synchronized
  fun destroy() {
    indexer.destroy()
    index.clear()
    unifiedIndex = null
    _currentProject = null
    rootDir = null
    EventBus.getDefault().unregister(this)
  }

  @Synchronized
  @Subscribe(threadMode = ThreadMode.ASYNC)
  fun onDocumentOpen(event: DocumentOpenEvent) {
    val file = File(event.openedFile.toUri())
    if (file.extension in setOf("java", "kt")) {
      indexer.onFileOpen(file, event.text)
    }
  }

  @Synchronized
  @Subscribe(threadMode = ThreadMode.ASYNC)
  fun onDocumentChange(event: DocumentChangeEvent) {
    val file = File(event.changedFile.toUri())
    if (file.extension in setOf("java", "kt")) {
      indexer.onFileChanged(file, event)
    }
  }

  @Synchronized
  @Subscribe(threadMode = ThreadMode.ASYNC)
  fun onDocumentSave(event: DocumentSaveEvent) {
    val file = File(event.savedFile.toUri())
    if (file.extension in setOf("java", "kt")) {
      indexer.onFileSaved(file)
    }
  }

  @Synchronized
  @Subscribe(threadMode = ThreadMode.ASYNC)
  fun onDocumentClose(event: DocumentCloseEvent) {
    val file = File(event.closedFile.toUri())
    indexer.onFileClosed(file)
  }
}
