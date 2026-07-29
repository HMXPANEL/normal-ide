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
  private val prefixIndex = mutableMapOf<String, MutableSet<String>>()

  fun add(FQN: String, location: SymbolLocation, filePath: String, decl: DeclarationModel) {
    fqnIndex.put(FQN, Entry(location, decl))
    val decls = fileDecls.getOrPut(filePath) { mutableListOf() }
    decls.add(decl)
    for (i in 1..FQN.length) {
      prefixIndex.getOrPut(FQN.substring(0, i)) { mutableSetOf() }.add(FQN)
    }
  }

  fun addFile(relPath: String, model: FileModel) {
    fileIndex[relPath] = model
  }

  fun lookup(fqn: String): SymbolLocation? = fqnIndex.get(fqn)?.location

  fun search(prefix: String): List<SymbolLocation> {
    return prefixIndex[prefix]
      ?.mapNotNull { fqnIndex.get(it)?.location }
      .orEmpty()
  }

  fun fileDeclarations(filePath: String): List<DeclarationModel> {
    return fileDecls[filePath].orEmpty()
  }

  fun removeFile(filePath: String) {
    val decls = fileDecls.remove(filePath) ?: return
    for (decl in decls) {
      val fqn = decl.fqn
      fqnIndex.remove(fqn)
      for (i in 1..fqn.length) {
        prefixIndex[fqn.substring(0, i)]?.remove(fqn)
      }
    }
    fileIndex.entries.removeAll { it.value.path == filePath }
  }

  fun clear() {
    fqnIndex.evictAll()
    fileIndex.clear()
    fileDecls.clear()
    prefixIndex.clear()
  }
}
