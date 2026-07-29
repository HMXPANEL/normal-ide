package com.hmx.ide.knowledge

import com.hmx.ide.knowledge.index.SymbolIndex
import com.hmx.ide.knowledge.model.DeclarationModel
import com.hmx.ide.knowledge.model.FileModel
import com.hmx.ide.knowledge.model.SymbolLocation
import com.hmx.ide.lsp.java.parser.ts.TSJavaParser
import jdkx.tools.JavaFileObject
import jdkx.tools.SimpleJavaFileObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CancellationException

class IncrementalIndexer(
  private val index: SymbolIndex,
  private val rootDir: () -> File?,
) {
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private val pendingJobs = mutableMapOf<String, Job>()
  private val fileBuffer = FileContentBuffer()

  private val log = LoggerFactory.getLogger(IncrementalIndexer::class.java)

  companion object {
    private const val DEBOUNCE_MS = 800L
  }

  fun onFileOpen(file: File, content: String) {
    fileBuffer.open(file.toPath(), content)
    scheduleImmediate(file, fromMemory = true)
  }

  fun onFileChanged(file: File, event: com.hmx.ide.eventbus.events.editor.DocumentChangeEvent) {
    fileBuffer.applyChange(file.toPath(), event)
    scheduleDebounced(file)
  }

  fun onFileSaved(file: File) {
    val path = file.toPath()
    if (fileBuffer.isOpen(path)) {
      fileBuffer.open(path, file.readText())
    }
    scheduleImmediate(file, fromMemory = fileBuffer.isOpen(path))
  }

  fun onFileClosed(file: File) {
    pendingJobs[file.absolutePath]?.cancel()
    pendingJobs.remove(file.absolutePath)
    fileBuffer.close(file.toPath())
    index.removeFile(file.absolutePath)
  }

  fun forceReindex(file: File) {
    if (fileBuffer.isOpen(file.toPath())) {
      scheduleImmediate(file, fromMemory = true)
    } else {
      scheduleImmediate(file, fromMemory = false)
    }
  }

  fun isFileOpen(file: File): Boolean = fileBuffer.isOpen(file.toPath())

  fun destroy() {
    pendingJobs.values.forEach { it.cancel() }
    pendingJobs.clear()
    fileBuffer.clear()
    scope.cancel(CancellationException("Indexer destroyed"))
  }

  private fun scheduleDebounced(file: File) {
    val key = file.absolutePath
    pendingJobs[key]?.cancel()
    pendingJobs[key] = scope.launch {
      delay(DEBOUNCE_MS)
      if (!isActive) return@launch
      reindexFile(file, fromMemory = true)
    }
  }

  private fun scheduleImmediate(file: File, fromMemory: Boolean) {
    val key = file.absolutePath
    pendingJobs[key]?.cancel()
    pendingJobs[key] = scope.launch {
      reindexFile(file, fromMemory)
    }
  }

  private fun reindexFile(file: File, fromMemory: Boolean) {
    val root = rootDir() ?: return
    val content = if (fromMemory) {
      fileBuffer.get(file.toPath()) ?: return
    } else {
      runCatching { file.readText() }.getOrNull() ?: return
    }

    parseAndIndex(file, root, content)
  }

  private fun parseAndIndex(file: File, root: File, content: String) {
    val ext = file.extension
    val newDecls = mutableListOf<DeclarationModel>()
    val pkg: String?

    when (ext) {
      "java" -> {
        pkg = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)
          .find(content)?.groupValues?.getOrNull(1)
        try {
          val javaFile = object : SimpleJavaFileObject(file.toURI(), JavaFileObject.Kind.SOURCE) {
            override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = content
          }
          val result = TSJavaParser.parse(javaFile)
          val walker = JavaAstWalker(pkg ?: "", content)
          walker.walk(result.tree.rootNode, newDecls)
          result.close()
        } catch (_: Exception) {
          fallbackJavaParse(content, pkg, newDecls)
        }
      }
      "kt" -> {
        pkg = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)
          .find(content)?.groupValues?.getOrNull(1)
        fallbackKotlinParse(content, pkg, newDecls)
      }
      else -> return
    }

    val relPath = file.relativeTo(root).path
    val model = FileModel(
      path = file.absolutePath,
      relativePath = relPath,
      packageName = pkg,
      declarations = newDecls,
    )

    index.removeFile(file.absolutePath)
    if (pkg != null) {
      for (decl in newDecls) {
        index.add(decl.fqn, SymbolLocation(file.absolutePath, decl.line, decl.column), file.absolutePath, decl)
      }
    }
    index.addFile(relPath, model)

    log.debug("Indexed {}: {} declarations", relPath, newDecls.size)
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
}
