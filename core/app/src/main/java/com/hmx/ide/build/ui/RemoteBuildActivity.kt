package com.hmx.ide.build.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hmx.ide.R
import com.hmx.ide.build.BuildState
import com.hmx.ide.build.GitHubTokenStorage
import com.hmx.ide.build.RemoteBuildViewModel
import com.hmx.ide.databinding.ActivityRemoteBuildBinding
import java.io.File
import java.text.DecimalFormat

class RemoteBuildActivity : AppCompatActivity() {

  private lateinit var binding: ActivityRemoteBuildBinding
  private val viewModel: RemoteBuildViewModel by viewModels()
  private lateinit var tokenStorage: GitHubTokenStorage

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityRemoteBuildBinding.inflate(layoutInflater)
    setContentView(binding.root)
    tokenStorage = GitHubTokenStorage(this)
    setSupportActionBar(binding.toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    binding.btnBuildDebug.setOnClickListener { viewModel.build("debug", "AI: Build debug APK") }
    binding.btnBuildRelease.setOnClickListener { viewModel.build("release", "AI: Build release APK") }
    binding.btnDownload.setOnClickListener { downloadApk() }
    binding.btnRetry.setOnClickListener { viewModel.retry("debug") }
    binding.btnConfigureToken.setOnClickListener { promptForToken() }

    viewModel.state.observe(this, Observer { render(it) })
    viewModel.log.observe(this) { binding.logView.text = it }
  }

  private fun render(state: BuildState) {
    binding.progress.visibility = if (state is BuildState.Preparing ||
      state is BuildState.Uploading || state is BuildState.Waiting ||
      state is BuildState.Queued || state is BuildState.Running
    ) android.view.View.VISIBLE else android.view.View.GONE

    binding.btnDownload.visibility = if (state is BuildState.Succeeded) android.view.View.VISIBLE else android.view.View.GONE
    binding.btnRetry.visibility = if (state is BuildState.Failed) android.view.View.VISIBLE else android.view.View.GONE
    binding.btnConfigureToken.visibility = if (state is BuildState.Unavailable) android.view.View.VISIBLE else android.view.View.GONE

    when (state) {
      is BuildState.Idle -> {
        binding.statusTitle.setText(R.string.build_status_idle)
        binding.statusDetail.text = ""
      }
      is BuildState.Preparing -> {
        binding.statusTitle.setText(R.string.build_status_preparing)
        binding.statusDetail.text = ""
      }
      is BuildState.Uploading -> {
        binding.statusTitle.setText(R.string.build_status_uploading)
        binding.statusDetail.text = ""
      }
      is BuildState.Waiting -> {
        binding.statusTitle.setText(R.string.build_status_waiting)
        binding.statusDetail.text = ""
      }
      is BuildState.Queued -> {
        binding.statusTitle.setText(R.string.build_status_queued)
        binding.statusDetail.text = ""
      }
      is BuildState.Running -> {
        binding.statusTitle.setText(R.string.build_status_running)
        binding.statusDetail.text = ""
      }
      is BuildState.Succeeded -> {
        binding.statusTitle.setText(R.string.build_status_succeeded)
        binding.statusDetail.text = getString(
          R.string.build_detail_succeeded,
          state.apkName,
          state.runNumber,
          state.commitSha.take(8),
          formatSize(state.apkSizeBytes),
        )
      }
      is BuildState.Failed -> {
        binding.statusTitle.setText(R.string.build_status_failed)
        binding.statusDetail.text = getString(
          R.string.build_detail_failed,
          state.failedJob ?: "—",
          state.message,
        )
        viewModel.refreshFailureLog()
      }
      is BuildState.Cancelled -> {
        binding.statusTitle.setText(R.string.build_status_cancelled)
        binding.statusDetail.text = state.runUrl
      }
      is BuildState.Unavailable -> {
        binding.statusTitle.setText(R.string.build_status_unavailable)
        binding.statusDetail.text = state.reason
      }
    }

    viewModel.log.observe(this) { log ->
      binding.logView.text = log
    }
  }

  private fun downloadApk() {
    val dir = File(getExternalFilesDir(null), "apk")
    dir.mkdirs()
    val s = viewModel.state.value
    if (s is BuildState.Succeeded) {
      val dest = File(dir, s.apkName)
      viewModel.downloadApk(dest)
      Toast.makeText(this, "Downloading to ${dest.absolutePath}", Toast.LENGTH_LONG).show()
    }
  }

  private fun promptForToken() {
    val input = android.widget.EditText(this)
    input.hint = "ghp_…"
    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.build_prompt_token)
      .setMessage(R.string.build_prompt_token_msg)
      .setView(input)
      .setPositiveButton(android.R.string.ok) { _, _ ->
        val t = input.text.toString().trim()
        if (t.isNotEmpty()) {
          tokenStorage.setToken(t)
          Toast.makeText(this, "Token saved (encrypted)", Toast.LENGTH_SHORT).show()
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun formatSize(bytes: Long): String {
    val df = DecimalFormat("#.##")
    return when {
      bytes >= 1_048_576 -> "${df.format(bytes / 1_048_576.0)} MB"
      bytes >= 1024 -> "${df.format(bytes / 1024.0)} KB"
      else -> "$bytes B"
    }
  }

  override fun onSupportNavigateUp(): Boolean {
    finish()
    return true
  }
}
