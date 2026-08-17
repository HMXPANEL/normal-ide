package com.hmx.ide.build

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thin GitHub REST client used by the remote build system.
 *
 * Uses raw [HttpURLConnection] (consistent with the app's [com.hmx.ide.ai.network.AiHttpClient])
 * so no extra dependency is introduced. The GitHub token is sent ONLY as the
 * `Authorization: Bearer` header and is never logged or persisted in plaintext.
 */
class GitHubBuildClient(private val token: String) {

  private fun request(
    method: String,
    path: String,
    body: JSONObject? = null,
  ): Pair<Int, String> = runCatching {
    val url = URL("https://api.github.com$path")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = method
    conn.connectTimeout = 15_000
    conn.readTimeout = 30_000
    conn.setRequestProperty("Accept", "application/vnd.github+json")
    conn.setRequestProperty("Authorization", "Bearer $token")
    conn.setRequestProperty("User-Agent", "HMX-IDE/1.0")
    conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
    if (body != null) {
      conn.doOutput = true
      conn.setRequestProperty("Content-Type", "application/json")
      conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
    }
    val code = conn.responseCode
    val text = if (code in 200..299) {
      conn.inputStream.bufferedReader().readText()
    } else {
      conn.errorStream?.bufferedReader()?.readText().orEmpty()
    }
    code to text
  }.getOrDefault(0 to "")

  suspend fun getRepo(owner: String, repo: String): JSONObject? = withContext(Dispatchers.IO) {
    val (code, text) = request("GET", "/repos/$owner/$repo")
    if (code == 200) JSONObject(text) else null
  }

  suspend fun dispatchWorkflow(
    owner: String,
    repo: String,
    workflowFile: String,
    ref: String,
    buildType: String,
  ): Boolean = withContext(Dispatchers.IO) {
    val body = JSONObject().apply {
      put("ref", ref)
      put(
        "inputs",
        JSONObject().apply {
          put("build_type", buildType)
          put("module", ":core:app")
        },
      )
    }
    val (code) = request(
      "POST",
      "/repos/$owner/$repo/actions/workflows/${workflowFile}/dispatches",
      body,
    )
    code in 200..299
  }

  suspend fun latestRun(
    owner: String,
    repo: String,
    branch: String,
  ): JSONObject? = withContext(Dispatchers.IO) {
    val (code, text) = request(
      "GET",
      "/repos/$owner/$repo/actions/runs?event=workflow_dispatch&branch=$branch&per_page=1",
    )
    if (code != 200) return@withContext null
    val arr = JSONObject(text).optJSONArray("workflow_runs") ?: JSONArray()
    if (arr.length() == 0) null else arr.getJSONObject(0)
  }

  // Exposed for the manager
  internal fun http(
    method: String,
    path: String,
    body: JSONObject? = null,
  ): Pair<Int, String> = request(method, path, body)

  suspend fun downloadArtifact(destination: File, url: String) = withContext(Dispatchers.IO) {
    var current = url
    repeat(5) {
      val conn = URL(current).openConnection() as HttpURLConnection
      conn.requestMethod = "GET"
      conn.setRequestProperty("Authorization", "Bearer $token")
      conn.instanceFollowRedirects = false
      val code = conn.responseCode
      if (code in 300..399) {
        val next = conn.getHeaderField("Location") ?: return@withContext
        current = next
      } else if (code in 200..299) {
        conn.inputStream.use { input ->
          destination.outputStream().use { out -> input.copyTo(out) }
        }
        return@withContext
      } else {
        return@withContext
      }
    }
  }

  companion object {
    internal fun parseJobs(text: String): List<JSONObject> {
      val arr = JSONObject(text).optJSONArray("jobs") ?: JSONArray()
      return (0 until arr.length()).map { arr.getJSONObject(it) }
    }
  }
}
