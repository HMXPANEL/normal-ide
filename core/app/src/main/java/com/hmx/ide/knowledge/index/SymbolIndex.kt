package com.hmx.ide.knowledge.index

import androidx.collection.LruCache
import com.hmx.ide.knowledge.model.DeclarationModel
import com.hmx.ide.knowledge.model.FileModel
import com.hmx.ide.knowledge.model.SymbolLocation

class SymbolIndex(maxSize: Int) {

  private data class Entry(
    val location: SymbolLocation,
    val decl: DeclarationModel,
  )

  private val fqnIndex = LruCache<String, Entry>(maxSize)
  private val fileIndex = mutableMapOf<String, FileModel>()
  private val fileDecls = mutableMapOf<String, MutableList<DeclarationModel>>()

  @Synchronized
  fun add(FQN: String, location: SymbolLocation, filePath: String, decl: DeclarationModel) {
    fqnIndex.put(FQN, Entry(location, decl))
    fileDecls.getOrPut(filePath) { mutableListOf() }.add(decl)
  }

  @Synchronized
  fun addFile(relPath: String, model: FileModel) {
    fileIndex[relPath] = model
  }

  @Synchronized
  fun lookup(fqn: String): SymbolLocation? = fqnIndex.get(fqn)?.location

  @Synchronized
  fun search(prefix: String): List<SymbolLocation> {
    if (prefix.isEmpty()) return emptyList()
    return fqnIndex.snapshot()
      .filterKeys { it.startsWith(prefix) }
      .map { it.value.location }
  }

  @Synchronized
  fun fileDeclarations(filePath: String): List<DeclarationModel> {
    return fileDecls[filePath].orEmpty()
  }

  @Synchronized
  fun removeFile(filePath: String) {
    val decls = fileDecls.remove(filePath) ?: return
    for (decl in decls) {
      fqnIndex.remove(decl.fqn)
    }
    fileIndex.entries.removeAll { it.value.path == filePath }
  }

  @Synchronized
  fun clear() {
    fqnIndex.evictAll()
    fileIndex.clear()
    fileDecls.clear()
  }
}
