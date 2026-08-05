package com.hmx.ide.knowledge

import com.hmx.ide.knowledge.index.SymbolIndex
import com.hmx.ide.knowledge.model.DeclarationModel
import com.hmx.ide.knowledge.model.SymbolLocation
import kotlinx.coroutines.CancellationException
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

class IncrementalIndexer(
  private val index: SymbolIndex,
  private val rootDir: () -> File?,
) {
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private val pendingJobs = mutableMapOf<String, Job>()
  private val fileBuffer = FileContentBuffer()

  private val lock = Any()

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
    synchronized(lock) {
      pendingJobs[file.absolutePath]?.cancel()
      pendingJobs.remove(file.absolutePath)
    }
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
    synchronized(lock) {
      pendingJobs.values.forEach { it.cancel() }
      pendingJobs.clear()
    }
    fileBuffer.clear()
    scope.cancel(CancellationException("Indexer destroyed"))
  }

  private fun scheduleDebounced(file: File) {
    val key = file.absolutePath
    synchronized(lock) {
      pendingJobs[key]?.cancel()
      pendingJobs[key] = scope.launch {
        delay(DEBOUNCE_MS)
        if (!isActive) return@launch
        reindexFile(file, fromMemory = true)
      }
    }
  }

  private fun scheduleImmediate(file: File, fromMemory: Boolean) {
    val key = file.absolutePath
    synchronized(lock) {
      pendingJobs[key]?.cancel()
      pendingJobs[key] = scope.launch {
        reindexFile(file, fromMemory)
      }
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
    if (ext != "java" && ext != "kt") return

    val newDecls = mutableListOf<DeclarationModel>()
    val model = FileParser.parse(file, root, content)
    newDecls.addAll(model.declarations)

    val relPath = file.relativeTo(root).path
    index.removeFile(file.absolutePath)
    if (model.packageName != null) {
      for (decl in newDecls) {
        index.add(decl.fqn, SymbolLocation(file.absolutePath, decl.line, decl.column), file.absolutePath, decl)
      }
    }
    index.addFile(relPath, model)

    log.debug("Indexed {}: {} declarations", relPath, newDecls.size)
  }
}
