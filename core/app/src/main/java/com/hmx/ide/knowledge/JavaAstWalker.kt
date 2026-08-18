package com.hmx.ide.knowledge

import com.hmx.ide.knowledge.model.DeclarationModel
import com.hmx.ide.knowledge.model.SymbolKind
import com.itsaky.androidide.treesitter.TSNode
import com.itsaky.androidide.treesitter.TSQuery
import com.itsaky.androidide.treesitter.TSQueryCursor
import com.itsaky.androidide.treesitter.java.TSLanguageJava

class JavaAstWalker(private val packageName: String, private val source: String) {

  private val typeQuery = TSQuery.create(
    TSLanguageJava.getInstance(),
    "(class_declaration name: (identifier) @name) @decl\n" +
    "(interface_declaration name: (identifier) @name) @decl\n" +
    "(enum_declaration name: (identifier) @name) @decl"
  )

  private val memberQuery = TSQuery.create(
    TSLanguageJava.getInstance(),
    "(method_declaration name: (identifier) @name) @decl\n" +
    "(constructor_declaration name: (identifier) @name) @decl\n" +
    "(field_declaration declarator: (variable_declarator name: (identifier) @name)) @decl"
  )

  fun walk(rootNode: TSNode, result: MutableList<DeclarationModel>) {
    val cursor = TSQueryCursor.create()
    cursor.exec(typeQuery, rootNode)
    var match = cursor.nextMatch()
    while (match != null) {
      val nameCapture = match.captures.find { typeQuery.getCaptureNameForId(it.index) == "name" }
      if (nameCapture == null) { match = cursor.nextMatch(); continue }
      val declCapture = match.captures.find { typeQuery.getCaptureNameForId(it.index) == "decl" }
      if (declCapture == null) { match = cursor.nextMatch(); continue }
      val nameNode = nameCapture.node
      val declNode = declCapture.node
      if (!nameNode.canAccess() || !declNode.canAccess()) {
        match = cursor.nextMatch(); continue
      }
      val name = extractText(nameNode)
      if (name == null) { match = cursor.nextMatch(); continue }
      val kind = when (declNode.type) {
        "interface_declaration" -> SymbolKind.INTERFACE
        "enum_declaration" -> SymbolKind.ENUM
        else -> SymbolKind.CLASS
      }
      val fqn = if (packageName.isNotEmpty()) "$packageName.$name" else name
      val modifiers = extractModifiers(declNode)
      val superTypes = extractSupertypes(declNode)
      val lineNum = lineOf(nameNode)

      val members = mutableListOf<DeclarationModel>()
      extractMembers(declNode, fqn, members)

      result.add(
        DeclarationModel(
          kind = kind, name = name, fqn = fqn,
          modifiers = modifiers, superTypes = superTypes,
          members = members, line = lineNum,
        )
      )
      match = cursor.nextMatch()
    }
    cursor.close()
  }

  private fun extractMembers(declNode: TSNode, parentFqn: String, out: MutableList<DeclarationModel>) {
    val cursor = TSQueryCursor.create()
    cursor.exec(memberQuery, declNode)
    var match = cursor.nextMatch()
    while (match != null) {
      val nameCapture = match.captures.find { memberQuery.getCaptureNameForId(it.index) == "name" }
      if (nameCapture == null) { match = cursor.nextMatch(); continue }
      if (!nameCapture.node.canAccess()) { match = cursor.nextMatch(); continue }
      val name = extractText(nameCapture.node)
      if (name == null) { match = cursor.nextMatch(); continue }
      out.add(
        DeclarationModel(
          kind = SymbolKind.METHOD, name = name, fqn = "$parentFqn.$name",
          line = lineOf(nameCapture.node),
        )
      )
      match = cursor.nextMatch()
    }
    cursor.close()
  }

  private fun extractModifiers(node: TSNode): List<String> {
    val mods = mutableListOf<String>()
    for (i in 0 until node.childCount) {
      val child = node.getChild(i) ?: continue
      if (child.canAccess() && child.type == "modifier") {
        extractText(child)?.let { mods.add(it) }
      }
    }
    return mods
  }

  private fun extractSupertypes(node: TSNode): List<String> {
    val types = mutableListOf<String>()
    for (i in 0 until node.childCount) {
      val child = node.getChild(i) ?: continue
      if (!child.canAccess()) continue
      when (child.type) {
        "superclass" -> findTypeIdentifiers(child, types)
        "superinterfaces" -> findTypeIdentifiers(child, types)
      }
    }
    return types
  }

  private fun findTypeIdentifiers(node: TSNode, out: MutableList<String>) {
    for (i in 0 until node.childCount) {
      val child = node.getChild(i) ?: continue
      if (child.canAccess() && child.type == "type_identifier") {
        extractText(child)?.let { out.add(it) }
      }
    }
  }

  private fun extractText(node: TSNode): String? {
    val start = node.startByte / 2
    val end = node.endByte / 2
    if (start < 0 || end > source.length || start >= end) return null
    return source.substring(start, end)
  }

  private fun lineOf(node: TSNode): Int {
    val pos = node.startByte / 2
    return source.substring(0, pos.coerceAtMost(source.length)).count { it == '\n' } + 1
  }
}
