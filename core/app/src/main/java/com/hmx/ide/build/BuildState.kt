package com.hmx.ide.build

/**
 * Lifecycle states for a remote GitHub Actions build, as required by the
 * remote-build specification.
 */
sealed interface BuildState {
  /** Idle / not started. */
  data object Idle : BuildState

  /** Preparing the project locally (collecting changes). */
  data object Preparing : BuildState

  /** Committing and pushing the project to GitHub. */
  data object Uploading : BuildState

  /** Workflow dispatch sent, waiting for GitHub to pick it up. */
  data object Waiting : BuildState

  /** Build has been queued by GitHub Actions. */
  data object Queued : BuildState

  /** Build is currently running on the runner. */
  data object Running : BuildState

  /** Build completed successfully and the APK artifact is available. */
  data class Succeeded(
    val runUrl: String,
    val runNumber: Long,
    val commitSha: String,
    val artifactName: String,
    val apkName: String,
    val apkSizeBytes: Long,
    val downloadUrl: String,
  ) : BuildState

  /** Build failed. */
  data class Failed(
    val runUrl: String,
    val runNumber: Long,
    val failedJob: String?,
    val message: String,
  ) : BuildState

  /** Build was cancelled. */
  data class Cancelled(
    val runUrl: String,
    val runNumber: Long,
  ) : BuildState

  /** Remote build is unavailable and no local fallback will be attempted. */
  data class Unavailable(val reason: String) : BuildState
}

/**
 * Snapshot of the project's GitHub coordinates, detected dynamically from the
 * local git repository. Nothing is hardcoded.
 */
data class RepoInfo(
  val owner: String,
  val repo: String,
  val branch: String,
  val remoteUrl: String,
)
