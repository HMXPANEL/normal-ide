package com.hmx.ide.ai.context

data class ProjectFileInfo(
  val path: String,
  val relativePath: String,
  val packageName: String? = null,
  val imports: List<String> = emptyList(),
  val classes: List<String> = emptyList(),
)

data class ProjectIndex(
  val context: ProjectContext,
  val files: List<ProjectFileInfo> = emptyList(),
  val totalSourceFiles: Int = 0,
  val totalFiles: Int = 0,
)
