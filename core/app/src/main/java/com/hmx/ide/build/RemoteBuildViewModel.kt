package com.hmx.ide.build

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hmx.ide.projects.internal.ProjectManagerImpl
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel exposing remote-build state to the UI. The actual work is delegated
 * to [RemoteBuildManager] in the common build layer.
 */
class RemoteBuildViewModel(application: Application) : AndroidViewModel(application) {

  private val manager = RemoteBuildManager(application)
  private val _state = MutableLiveData<BuildState>(BuildState.Idle)
  val state: LiveData<BuildState> = _state

  private val _log = MutableLiveData<String>("")
  val log: LiveData<String> = _log

  init {
    viewModelScope.launch {
      manager.state.collectLatest { _state.value = it }
    }
  }

  fun build(buildType: String = "debug", commitMessage: String = "AI: Save project changes") {
    val projectDir = currentProjectDir() ?: run {
      _state.value = BuildState.Unavailable("No project is currently open.")
      return
    }
    manager.build(projectDir, buildType, commitMessage)
  }

  fun retry(buildType: String = "debug") {
    build(buildType, "AI: Retry build after fixes")
  }

  fun downloadApk(destFile: File) {
    val s = manager.currentState()
    if (s is BuildState.Succeeded) {
      manager.downloadApk(destFile, s.downloadUrl)
    }
  }

  fun refreshFailureLog() {
    val s = manager.currentState()
    if (s is BuildState.Failed) {
      val repo = currentRepo() ?: return
      viewModelScope.launch {
        val runJson = org.json.JSONObject().apply { put("id", runIdFromUrl(s.runUrl)) }
        val text = manager.fetchFailureLog(repo, runJson)
        _log.postValue(text ?: "Unable to retrieve failure log.")
      }
    }
  }

  private fun runIdFromUrl(url: String): Long {
    // https://github.com/owner/repo/actions/runs/12345
    return url.substringAfterLast("/").toLongOrNull() ?: -1L
  }

  private fun currentProjectDir(): File? {
    val path = ProjectManagerImpl.getInstance().projectDirPath ?: return null
    val f = File(path)
    return if (f.exists()) f else null
  }

  private fun currentRepo(): RepoInfo? {
    val dir = currentProjectDir() ?: return null
    return ProjectGitInfo.detect(dir)
  }
}
