package com.hmx.ide.knowledge.model

data class ProjectModel(
  val projectDir: String,
  val modules: List<ModuleModel> = emptyList(),
  val files: Map<String, FileModel> = emptyMap(),
)

data class ModuleModel(
  val name: String,
  val basePath: String,
  val files: List<String> = emptyList(),
)

data class FileModel(
  val path: String,
  val relativePath: String,
  val packageName: String? = null,
  val imports: List<String> = emptyList(),
  val declarations: List<DeclarationModel> = emptyList(),
)

data class DeclarationModel(
  val kind: SymbolKind,
  val name: String,
  val fqn: String,
  val modifiers: List<String> = emptyList(),
  val superTypes: List<String> = emptyList(),
  val members: List<DeclarationModel> = emptyList(),
  val parameters: List<String> = emptyList(),
  val returnType: String? = null,
  val line: Int = 0,
  val column: Int = 0,
)

enum class SymbolKind {
  CLASS, INTERFACE, ENUM, ANNOTATION, OBJECT,
  METHOD, CONSTRUCTOR, FIELD, PROPERTY, ENUM_CONSTANT,
}

typealias SymbolLocation = com.hmx.ide.indexing.model.SymbolLocation
