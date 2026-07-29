package com.hmx.ide.knowledge

import com.hmx.ide.ai.context.ProjectAnalyzer
import com.hmx.ide.ai.context.ProjectScanner
import com.hmx.ide.eventbus.events.editor.DocumentChangeEvent
import com.hmx.ide.eventbus.events.editor.DocumentCloseEvent
import com.hmx.ide.eventbus.events.editor.DocumentOpenEvent
import com.hmx.ide.eventbus.events.editor.DocumentSaveEvent
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
  private val indexer = IncrementalIndexer(index, rootDir = { rootDir })

  private var _currentProject: ProjectModel? = null
  private var rootDir: File? = null

  override val currentProject: ProjectModel? get() = _currentProject

  override fun start() {
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }
  }

  override fun refresh(projectDir: File) {
    rootDir = projectDir
    val root = projectDir
    val scan = ProjectScanner.scan(root)
    val analysis = ProjectAnalyzer.analyze(root, scan)
    val modules = analysis.context.modules.map { name ->
      ModuleModel(name = name, basePath = "$root/$name", files = emptyList())
    }
    _currentProject = ProjectModel(
      projectDir = root.absolutePath,
      modules = modules,
    )
    index.clear()
    scan.javaFiles.forEach { indexFile(it) }
    scan.kotlinFiles.forEach { indexFile(it) }
  }

  override fun getFile(file: File): FileModel? {
    val root = rootDir ?: return null
    val content = runCatching { file.readText() }.getOrNull() ?: return null
    return buildFileModel(file, root, content)
  }

  override fun searchSymbol(fqn: String): SymbolLocation? = index.lookup(fqn)

  override fun searchSymbols(prefix: String): List<SymbolLocation> = index.search(prefix)

  override fun findDeclarationsInFile(file: File): List<DeclarationModel> {
    return index.fileDeclarations(file.absolutePath)
  }

  override fun invalidateFile(file: File) {
    indexer.forceReindex(file)
  }

  override fun invalidateAll() {
    index.clear()
    val root = rootDir ?: return
    refresh(root)
  }

  fun destroy() {
    indexer.destroy()
    EventBus.getDefault().unregister(this)
  }

  private fun indexFile(file: File) {
    val root = rootDir ?: return
    val content = runCatching { file.readText() }.getOrNull() ?: return
    val model = buildFileModel(file, root, content)
    if (model.packageName != null) {
      for (decl in model.declarations) {
        index.add(decl.fqn, SymbolLocation(file.absolutePath, decl.line, decl.column), file.absolutePath, decl)
      }
    }
    val relPath = file.relativeTo(root).path
    index.addFile(relPath, model)
  }

  private fun buildFileModel(file: File, root: File, content: String): FileModel {
    val ext = file.extension
    return when {
      ext == "java" -> parseJavaFile(file, root, content)
      ext == "kt" -> parseKotlinFile(file, root, content)
      else -> FileModel(
        path = file.absolutePath,
        relativePath = file.relativeTo(root).path,
      )
    }
  }

  private fun parseJavaFile(file: File, root: File, content: String): FileModel {
    val pkg = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)
      .find(content)?.groupValues?.getOrNull(1)
    val imports = Regex("""^import\s+([\w.*]+)""", RegexOption.MULTILINE)
      .findAll(content).map { it.groupValues[1] }.toList()
    val declarations = mutableListOf<DeclarationModel>()

    try {
      val javaFile = object : jdkx.tools.SimpleJavaFileObject(file.toURI(), jdkx.tools.JavaFileObject.Kind.SOURCE) {
        override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = content
      }
      val result = com.hmx.ide.lsp.java.parser.ts.TSJavaParser.parse(javaFile)
      val walker = JavaAstWalker(pkg ?: "", content)
      walker.walk(result.tree.rootNode, declarations)
      result.close()
    } catch (_: Exception) {
      fallbackJavaParse(content, pkg, declarations)
    }

    return FileModel(
      path = file.absolutePath,
      relativePath = file.relativeTo(root).path,
      packageName = pkg,
      imports = imports,
      declarations = declarations,
    )
  }

  private fun fallbackJavaParse(content: String, pkg: String?, decls: MutableList<DeclarationModel>) {
    val classRegex = Regex(
      """((public|private|protected|static|abstract|final)\s+)*(class|interface|enum|@interface)\s+(\w+)(?:\s+extends\s+(\w+))?(?:\s+implements\s+([\w,\s]+))?"""
    )
    for (m in classRegex.findAll(content)) {
      val kind = when (m.groupValues[3]) {
        "interface" -> com.hmx.ide.knowledge.model.SymbolKind.INTERFACE
        "enum" -> com.hmx.ide.knowledge.model.SymbolKind.ENUM
        "@interface" -> com.hmx.ide.knowledge.model.SymbolKind.ANNOTATION
        else -> com.hmx.ide.knowledge.model.SymbolKind.CLASS
      }
      val name = m.groupValues[4]
      val fqn = if (pkg != null) "$pkg.$name" else name
      decls.add(
        DeclarationModel(
          kind = kind, name = name, fqn = fqn,
          superTypes = listOfNotNull(m.groupValues[5]) +
            m.groupValues[6].takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }.orEmpty(),
          line = content.substring(0, m.range.first).count { it == '\n' } + 1,
        )
      )
    }
  }

  private fun parseKotlinFile(file: File, root: File, content: String): FileModel {
    val pkg = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)
      .find(content)?.groupValues?.getOrNull(1)
    val imports = Regex("""^import\s+([\w.*]+)""", RegexOption.MULTILINE)
      .findAll(content).map { it.groupValues[1] }.toList()
    val declarations = mutableListOf<DeclarationModel>()
    fallbackKotlinParse(content, pkg, declarations)
    return FileModel(
      path = file.absolutePath,
      relativePath = file.relativeTo(root).path,
      packageName = pkg,
      imports = imports,
      declarations = declarations,
    )
  }

  private fun fallbackKotlinParse(content: String, pkg: String?, decls: MutableList<DeclarationModel>) {
    val classRegex = Regex(
      """((public|private|protected|internal|open|abstract|final|data|sealed|value)\s+)*(class|interface|enum class|enum|object|annotation class|data class|sealed class|abstract class)\s+(\w+)"""
    )
    for (m in classRegex.findAll(content)) {
      val rawKind = m.groupValues[3]
      val name = m.groupValues[4]
      val fqn = if (pkg != null) "$pkg.$name" else name
      val kind = when {
        rawKind.contains("interface") -> com.hmx.ide.knowledge.model.SymbolKind.INTERFACE
        rawKind.contains("enum") -> com.hmx.ide.knowledge.model.SymbolKind.ENUM
        rawKind.contains("annotation") -> com.hmx.ide.knowledge.model.SymbolKind.ANNOTATION
        rawKind == "object" -> com.hmx.ide.knowledge.model.SymbolKind.OBJECT
        else -> com.hmx.ide.knowledge.model.SymbolKind.CLASS
      }
      decls.add(
        DeclarationModel(
          kind = kind, name = name, fqn = fqn,
          line = content.substring(0, m.range.first).count { it == '\n' } + 1,
        )
      )
    }
  }

  @Subscribe(threadMode = ThreadMode.ASYNC)
  fun onDocumentOpen(event: DocumentOpenEvent) {
    val file = File(event.openedFile.toUri())
    if (file.extension in setOf("java", "kt")) {
      indexer.onFileOpen(file, event.text)
    }
  }

  @Subscribe(threadMode = ThreadMode.ASYNC)
  fun onDocumentChange(event: DocumentChangeEvent) {
    val file = File(event.changedFile.toUri())
    if (file.extension in setOf("java", "kt")) {
      indexer.onFileChanged(file, event)
    }
  }

  @Subscribe(threadMode = ThreadMode.ASYNC)
  fun onDocumentSave(event: DocumentSaveEvent) {
    val file = File(event.savedFile.toUri())
    if (file.extension in setOf("java", "kt")) {
      indexer.onFileSaved(file)
    }
  }

  @Subscribe(threadMode = ThreadMode.ASYNC)
  fun onDocumentClose(event: DocumentCloseEvent) {
    val file = File(event.closedFile.toUri())
    indexer.onFileClosed(file)
  }
}
