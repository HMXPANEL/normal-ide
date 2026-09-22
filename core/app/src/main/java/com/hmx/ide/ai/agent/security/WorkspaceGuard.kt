package com.hmx.ide.ai.agent.security

import java.io.File

/**
 * Reusable workspace-root guard. Every filesystem tool resolves user/model
 * supplied paths through [resolve] before touching disk.
 *
 * Mirrors the canonical-path check already proven in `AIChatActivity`
 * (kept untouched per Phase 1 coexistence rule) so both paths enforce the
 * same boundary.
 */
object WorkspaceGuard {

  fun resolve(root: File, requested: String): File {
    require(requested.isNotBlank()) { "Path must not be blank" }
    val canonicalRoot = root.canonicalFile
    val candidate = File(root, requested).canonicalFile
    if (candidate != canonicalRoot &&
      !candidate.path.startsWith(canonicalRoot.path + File.separator)
    ) {
      throw SecurityException(
        "Path escapes workspace root: '$requested' (root=${canonicalRoot.path})",
      )
    }
    return candidate
  }

  fun isWithin(root: File, requested: String): Boolean =
    runCatching { resolve(root, requested) }.isSuccess
}
