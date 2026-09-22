package com.hmx.ide.knowledge

import com.hmx.ide.knowledge.model.DeclarationModel
import com.hmx.ide.knowledge.model.FileModel
import com.hmx.ide.knowledge.model.SymbolKind
import jdkx.tools.JavaFileObject
import jdkx.tools.SimpleJavaFileObject
import java.io.File

object FileParser {

  fun parse(file: File, root: File, content: String): FileModel {
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
      val javaFile = object : SimpleJavaFileObject(file.toURI(), JavaFileObject.Kind.SOURCE) {
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

  fun fallbackJavaParse(content: String, pkg: String?, decls: MutableList<DeclarationModel>) {
    val classRegex = Regex(
      """((public|private|protected|static|abstract|final)\s+)*(class|interface|enum|@interface)\s+(\w+)(?:\s+extends\s+(\w+))?(?:\s+implements\s+([\w,\s]+))?"""
    )
    for (m in classRegex.findAll(content)) {
      val kind = when (m.groupValues[3]) {
        "interface" -> SymbolKind.INTERFACE
        "enum" -> SymbolKind.ENUM
        "@interface" -> SymbolKind.ANNOTATION
        else -> SymbolKind.CLASS
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

  fun fallbackKotlinParse(content: String, pkg: String?, decls: MutableList<DeclarationModel>) {
    val classRegex = Regex(
      """((public|private|protected|internal|open|abstract|final|data|sealed|value)\s+)*(class|interface|enum class|enum|object|annotation class|data class|sealed class|abstract class)\s+(\w+)"""
    )
    for (m in classRegex.findAll(content)) {
      val rawKind = m.groupValues[3]
      val name = m.groupValues[4]
      val fqn = if (pkg != null) "$pkg.$name" else name
      val kind = when {
        rawKind.contains("interface") -> SymbolKind.INTERFACE
        rawKind.contains("enum") -> SymbolKind.ENUM
        rawKind.contains("annotation") -> SymbolKind.ANNOTATION
        rawKind == "object" -> SymbolKind.OBJECT
        else -> SymbolKind.CLASS
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