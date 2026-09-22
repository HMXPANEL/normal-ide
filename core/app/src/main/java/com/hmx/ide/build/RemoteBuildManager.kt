package com.hmx.ide.build

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Orchestrates the remote GitHub Actions build pipeline.
 *
 * Flow: prepare -> commit/push -> dispatch workflow -> poll run status ->
 * surface artifact (or failure). There is NO local APK compilation fallback;
 * if GitHub Actions is unreachable the build is reported as [BuildState.Unavailable].
 *
 * Provider-agnostic: this class lives in the common build layer and is used by
 * any AI provider through [com.hmx.ide.build.tools.BuildTools].
 */
class RemoteBuildManager(private val context: Context) {

  private val tokenStorage = GitHubTokenStorage(context)
  private val _state = MutableStateFlow<BuildState>(BuildState.Idle)
  val state: StateFlow<BuildState> = _state.asStateFlow()

  private var pollJob: Job? = null

  fun currentState(): BuildState = _state.value

  /**
   * Runs the full remote build for the given project directory.
   * [commitMessage] is used for the push (meaningful messages only).
   */
  fun build(projectDir: File, buildType: String = "debug", commitMessage: String) {
    pollJob?.cancel()
    pollJob = CoroutineScope(Dispatchers.IO).launch {
      runBuild(projectDir, buildType, commitMessage)
    }
  }

  fun cancel() {
    pollJob?.cancel()
  }

  private suspend fun runBuild(projectDir: File, buildType: String, commitMessage: String) {
    if (!isOnline()) {
      _state.value = BuildState.Unavailable("Device is offline. Remote build requires GitHub reachability.")
      return
    }
    if (!tokenStorage.hasToken()) {
      _state.value = BuildState.Unavailable("GitHub token is not configured. Add it in Settings.")
      return
    }
    val token = tokenStorage.getToken()

    _state.value = BuildState.Preparing
    val repo = withContext(Dispatchers.IO) { ProjectGitInfo.detect(projectDir) }
    if (repo == null) {
      _state.value = BuildState.Unavailable("Could not detect a git repository / GitHub remote for this project.")
      return
    }

    _state.value = BuildState.Uploading
    val pushed = withContext(Dispatchers.IO) {
      ProjectGitInfo.commitAndPush(projectDir, token, commitMessage)
    }
    if (!pushed) {
      _state.value = BuildState.Unavailable("Failed to commit/push project to GitHub. Build not triggered.")
      return
    }

    val client = GitHubBuildClient(token)
    _state.value = BuildState.Waiting
    val dispatched = client.dispatchWorkflow(repo.owner, repo.repo, WORKFLOW_FILE, repo.branch, buildType)
    if (!dispatched) {
      _state.value = BuildState.Unavailable("Failed to trigger GitHub Actions workflow (check token permissions).")
      return
    }

    pollRun(client, repo, buildType)
  }

  private suspend fun pollRun(client: GitHubBuildClient, repo: RepoInfo, buildType: String) {
    // Poll with a reasonable interval; stop at a terminal state.
    var attempts = 0
    while (attempts < MAX_POLL_ATTEMPTS) {
      attempts++
      delay(POLL_INTERVAL_MS)
      val run = client.latestRun(repo.owner, repo.repo, repo.branch) ?: continue
      val status = run.optString("status")
      val htmlUrl = run.optString("html_url")
      val runNumber = run.optLong("run_number")
      val headSha = run.optJSONObject("head_sha")?.optString("sha") ?: run.optString("head_sha")

      when (status) {
        "queued" -> _state.value = BuildState.Queued
        "in_progress" -> _state.value = BuildState.Running
        "completed" -> {
          handleCompleted(client, repo, run, htmlUrl, runNumber, headSha, buildType)
          return
        }
        else -> _state.value = BuildState.Waiting
      }
    }
    _state.value = BuildState.Unavailable("Timed out waiting for the GitHub Actions run to finish.")
  }

  private suspend fun handleCompleted(
    client: GitHubBuildClient,
    repo: RepoInfo,
    run: JSONObject,
    htmlUrl: String,
    runNumber: Long,
    headSha: String,
    buildType: String,
  ) {
    val conclusion = run.optString("conclusion")
    when (conclusion) {
      "success" -> {
        val artifact = fetchArtifact(client, repo, run)
        if (artifact != null) {
          _state.value = BuildState.Succeeded(
            runUrl = htmlUrl,
            runNumber = runNumber,
            commitSha = headSha,
            artifactName = artifact.first,
            apkName = artifact.second,
            apkSizeBytes = artifact.third,
            downloadUrl = artifact.fourth,
          )
        } else {
          _state.value = BuildState.Failed(htmlUrl, runNumber, null, "Build succeeded but no APK artifact was found.")
        }
      }
      "cancelled" -> _state.value = BuildState.Cancelled(htmlUrl, runNumber)
      else -> {
        val failedJob = fetchFailedJob(client, repo, run)
        _state.value = BuildState.Failed(
          runUrl = htmlUrl,
          runNumber = runNumber,
          failedJob = failedJob,
          message = "GitHub Actions run concluded with conclusion: $conclusion.",
        )
      }
    }
  }

  private suspend fun fetchArtifact(
    client: GitHubBuildClient,
    repo: RepoInfo,
    run: JSONObject,
  ): Quad? = withContext(Dispatchers.IO) {
    val runId = run.optLong("id")
    val (code, text) = client.http("GET", "/repos/${repo.owner}/${repo.repo}/actions/runs/$runId/artifacts")
    if (code != 200) return@withContext null
    val arr = JSONObject(text).optJSONArray("artifacts") ?: return@withContext null
    for (i in 0 until arr.length()) {
      val a = arr.getJSONObject(i)
      val name = a.optString("name")
      if (name.endsWith("-apk")) {
        return@withContext Quad(
          name,
          a.optString("name"),
          a.optLong("size_in_bytes"),
          a.optString("archive_download_url"),
        )
      }
    }
    null
  }

  private suspend fun fetchFailedJob(
    client: GitHubBuildClient,
    repo: RepoInfo,
    run: JSONObject,
  ): String? = withContext(Dispatchers.IO) {
    val runId = run.optLong("id")
    val (code, text) = client.http("GET", "/repos/${repo.owner}/${repo.repo}/actions/runs/$runId/jobs")
    if (code != 200) return@withContext null
    GitHubBuildClient.parseJobs(text).firstOrNull {
      it.optString("conclusion") == "failure"
    }?.optString("name")
  }

  /** Fetches the log text of the first failed job (for AI analysis). */
  suspend fun fetchFailureLog(repo: RepoInfo, run: JSONObject): String? = withContext(Dispatchers.IO) {
    if (!tokenStorage.hasToken()) return@withContext null
    val client = GitHubBuildClient(tokenStorage.getToken())
    val runId = run.optLong("id")
    val (code, text) = client.http("GET", "/repos/${repo.owner}/${repo.repo}/actions/runs/$runId/jobs")
    if (code != 200) return@withContext null
    val job = GitHubBuildClient.parseJobs(text).firstOrNull { it.optString("conclusion") == "failure" }
      ?: return@withContext null
    val jobId = job.optLong("id")
    val (logCode, logText) = client.http("GET", "/repos/${repo.owner}/${repo.repo}/actions/jobs/$jobId/logs")
    if (logCode == 200) logText else null
  }

  fun downloadApk(destination: File, downloadUrl: String) {
    if (!tokenStorage.hasToken()) return
    val client = GitHubBuildClient(tokenStorage.getToken())
    CoroutineScope(Dispatchers.IO).launch {
      client.downloadArtifact(destination, downloadUrl)
    }
  }

  private fun isOnline(): Boolean {
    return try {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
      val net = cm?.activeNetworkInfo
      net?.isConnectedOrConnecting == true
    } catch (_: Exception) {
      true
    }
  }

  companion object {
    const val WORKFLOW_FILE = "android-build.yml"
    private const val POLL_INTERVAL_MS = 10_000L
    private const val MAX_POLL_ATTEMPTS = 180 // ~30 minutes
    private data class Quad(val first: String, val second: String, val third: Long, val fourth: String)
  }
}
