package com.hmx.ide.indexing.model

data class SymbolLocation(
  val filePath: String,
  val line: Int,
  val column: Int,
)
