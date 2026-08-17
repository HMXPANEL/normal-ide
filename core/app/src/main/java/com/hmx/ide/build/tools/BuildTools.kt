package com.hmx.ide.build.tools

import android.content.Context
import com.hmx.ide.build.BuildState
import com.hmx.ide.build.ProjectGitInfo
import com.hmx.ide.build.RemoteBuildManager
import com.hmx.ide.projects.internal.ProjectManagerImpl
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File

/**
 * Common build/tool layer for AI providers.
 *
 * Every AI provider (OpenAI, Gemini, Claude, custom) reaches GitHub Actions
 * through these functions — there are intentionally NO `if (provider == ...)`
 * branches here. The build backend is the application's responsibility, not the
 * provider's.
 *
 * These tools are exposed to the AI as:
 *   inspect_project, search_project, read_file, write_file, apply_patch,
 *   git_status, git_commit, git_push, github_trigger_build,
 *   github_build_status, github_build_logs, github_download_artifact
 */
object BuildTools {

  private lateinit var manager: RemoteBuildManager
  private lateinit var appContext: Context

  fun init(context: Context) {
    appContext = context.applicationContext
    manager = RemoteBuildManager(appContext)
  }

  private fun projectDir(): File? {
    val path = ProjectManagerImpl.getInstance().projectDirPath ?: return null
    val f = File(path)
    return if (f.exists()) f else null
  }

  fun gitStatus(): ToolResult {
    val dir = projectDir() ?: return ToolResult(false, "No open project.")
    val repo = ProjectGitInfo.detect(dir)
      ?: return ToolResult(false, "No GitHub remote detected for this project.")
    return ToolResult(true, "Repository ${repo.owner}/${repo.repo} @ ${repo.branch}", mapOf(
      "owner" to repo.owner, "repo" to repo.repo, "branch" to repo.branch,
    ))
  }

  fun gitCommitPush(message: String): ToolResult {
    val dir = projectDir() ?: return ToolResult(false, "No open project.")
    val repo = ProjectGitInfo.detect(dir)
      ?: return ToolResult(false, "No GitHub remote detected for this project.")
    val pushed = commitPushWithStoredToken(dir, message)
    return if (pushed) {
      ToolResult(true, "Pushed to ${repo.owner}/${repo.repo} (${repo.branch}).", emptyMap())
    } else {
      ToolResult(false, "Commit/push failed. Check git state and GitHub token.", emptyMap())
    }
  }

  private fun commitPushWithStoredToken(dir: File, message: String): Boolean {
    // Delegated through ProjectGitInfo using the securely stored token.
    val token = (manager as? RemoteBuildManager)?.let { storedToken() } ?: return false
    return ProjectGitInfo.commitAndPush(dir, token, message)
  }

  fun githubTriggerBuild(buildType: String = "debug", message: String = "AI: Save project changes"): ToolResult {
    val dir = projectDir() ?: return ToolResult(false, "No open project.")
    manager.build(dir, buildType, message)
    return ToolResult(true, "Remote build triggered ($buildType). Monitor github_build_status.", emptyMap())
  }

  fun githubBuildStatus(): ToolResult {
    return when (val s = manager.currentState()) {
      is BuildState.Succeeded -> ToolResult(true, "Build succeeded: ${s.apkName} (${s.apkSizeBytes} bytes).", mapOf(
        "status" to "succeeded", "apk" to s.apkName, "run" to s.runNumber,
        "run_url" to s.runUrl, "download_url" to s.downloadUrl,
      ))
      is BuildState.Failed -> ToolResult(false, "Build failed: ${s.message}", mapOf(
        "status" to "failed", "job" to (s.failedJob ?: ""), "run_url" to s.runUrl,
      ))
      is BuildState.Unavailable -> ToolResult(false, s.reason, mapOf("status" to "unavailable"))
      is BuildState.Cancelled -> ToolResult(false, "Build cancelled.", mapOf("status" to "cancelled"))
      else -> ToolResult(true, "Build in progress: ${s::class.simpleName}.", mapOf("status" to (s::class.simpleName ?: "unknown")))
    }
  }

  fun githubBuildLogs(): ToolResult {
    val dir = projectDir() ?: return ToolResult(false, "No open project.")
    val repo = ProjectGitInfo.detect(dir) ?: return ToolResult(false, "No GitHub remote.")
    val s = manager.currentState()
    if (s !is BuildState.Failed) return ToolResult(false, "No failed run to read logs from.")
    val runId = s.runUrl.substringAfterLast("/").toLongOrNull() ?: return ToolResult(false, "Unknown run.")
    val log = runBlocking { manager.fetchFailureLog(repo, JSONObject().apply { put("id", runId) }) }
    return if (log != null) ToolResult(true, log, mapOf("status" to "logs"))
    else ToolResult(false, "Unable to fetch logs.", emptyMap())
  }

  fun githubDownloadArtifact(destPath: String): ToolResult {
    val s = manager.currentState()
    if (s !is BuildState.Succeeded) return ToolResult(false, "No successful build artifact available.")
    val dest = File(destPath)
    manager.downloadApk(dest, s.downloadUrl)
    return ToolResult(true, "Downloading ${s.apkName} to $destPath.", emptyMap())
  }

  // Accessor for the securely-stored token (used by gitCommitPush).
  // Implemented by reflecting through RemoteBuildManager's storage.
  private fun storedToken(): String = com.hmx.ide.build.GitHubTokenStorage(appContext).getToken()

  data class ToolResult(
    val ok: Boolean,
    val message: String,
    val data: Map<String, Any>,
  )
}
