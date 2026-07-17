/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.services.builder

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.projects.internal.ProjectManagerImpl
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.services.ToolingServerNotStartedException
import com.itsaky.androidide.services.builder.ToolingServerRunner.OnServerStartListener
import com.itsaky.androidide.tasks.ifCancelledOrInterrupted
import com.itsaky.androidide.tooling.api.ForwardingToolingApiClient
import com.itsaky.androidide.tooling.api.IProject
import com.itsaky.androidide.tooling.api.IToolingApiClient
import com.itsaky.androidide.tooling.api.IToolingApiServer
import com.itsaky.androidide.tooling.api.LogSenderConfig.PROPERTY_LOGSENDER_ENABLED
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.api.messages.result.InitializeResult
import com.itsaky.androidide.tooling.api.models.ToolingServerMetadata
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.utils.IdeShellEnvironment
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.concurrent.CompletableFuture

/**
 * A service that manages the Gradle Tooling API server for
 * read-only project sync, indexing, dependency resolution, autocomplete, symbol resolution,
 * and error detection.
 *
 * @author Akash Yadav
 */
class GradleBuildService : Service(), BuildService, IToolingApiClient,
  ToolingServerRunner.Observer {

  private var mBinder: GradleServiceBinder? = null
  private var isToolingServerStarted = false
  private var _toolingApiClient: ForwardingToolingApiClient? = null
  private var toolingServerRunner: ToolingServerRunner? = null
  private var outputReaderJob: Job? = null
  private var server: IToolingApiServer? = null

  private val buildServiceScope = CoroutineScope(
    Dispatchers.Default + CoroutineName("GradleBuildService"))

  companion object {
    private val log = LoggerFactory.getLogger(GradleBuildService::class.java)
  }

  override fun onCreate() {
    Lookup.getDefault().update(BuildService.KEY_BUILD_SERVICE, this)
  }

  override fun isToolingServerStarted(): Boolean {
    return isToolingServerStarted && server != null
  }

  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    mBinder?.release()
    mBinder = null

    val lookup = Lookup.getDefault()
    lookup.unregister(BuildService.KEY_BUILD_SERVICE)
    lookup.unregister(BuildService.KEY_PROJECT_PROXY)

    server?.also { server ->
      try {
        log.info("Shutting down Tooling API server...")
        server.shutdown().get(1, java.util.concurrent.TimeUnit.SECONDS)
      } catch (e: Throwable) {
        log.error("Failed to shutdown Tooling API server", e)
      }
    }

    log.debug("Cancelling tooling server runner...")
    toolingServerRunner?.release()
    toolingServerRunner = null

    _toolingApiClient?.client = null
    _toolingApiClient = null

    log.debug("Cancelling tooling server output reader job...")
    outputReaderJob?.cancel()
    outputReaderJob = null

    isToolingServerStarted = false
  }

  override fun onBind(intent: Intent): IBinder? {
    if (mBinder == null) {
      mBinder = GradleServiceBinder(this)
    }
    return mBinder
  }

  override fun onListenerStarted(
    server: IToolingApiServer,
    projectProxy: IProject,
    errorStream: InputStream
  ) {
    startServerOutputReader(errorStream)
    this.server = server
    Lookup.getDefault().update(BuildService.KEY_PROJECT_PROXY, projectProxy)
    isToolingServerStarted = true
  }

  override fun onServerExited(exitCode: Int) {
    log.warn("Tooling API process terminated with exit code: {}", exitCode)
    isToolingServerStarted = false
  }

  override fun getClient(): IToolingApiClient {
    if (_toolingApiClient == null) {
      _toolingApiClient = ForwardingToolingApiClient(this)
    }
    return checkNotNull(_toolingApiClient)
  }

  override fun logMessage(params: com.itsaky.androidide.tooling.api.messages.LogMessageParams) {
    val logger = LoggerFactory.getLogger(params.tag)
    when (params.level) {
      'D' -> logger.debug(params.message)
      'W' -> logger.warn(params.message)
      'E' -> logger.error(params.message)
      'I' -> logger.info(params.message)
      else -> logger.trace(params.message)
    }
  }

  override fun initializeProject(params: InitializeProjectParams): CompletableFuture<InitializeResult> {
    val srv = server ?: return CompletableFuture.completedFuture(
      InitializeResult(false, InitializeResult.Failure.CONNECTION_ERROR))
    return srv.initialize(params)
  }

  override fun metadata(): CompletableFuture<ToolingServerMetadata> {
    return server?.metadata() ?: CompletableFuture.completedFuture(ToolingServerMetadata(-1))
  }

  override fun getBuildArguments(): CompletableFuture<List<String>> {
    val extraArgs = ArrayList<String>()
    extraArgs.add("--init-script")
    extraArgs.add(Environment.INIT_SCRIPT.absolutePath)

    // Override AAPT2 binary
    // The one downloaded from Maven is not built for Android
    extraArgs.add("-Pandroid.aapt2FromMavenOverride=" + Environment.AAPT2.absolutePath)
    extraArgs.add("-P${PROPERTY_LOGSENDER_ENABLED}=${com.itsaky.androidide.preferences.internal.DevOpsPreferences.logsenderEnabled}")

    return CompletableFuture.completedFuture(extraArgs)
  }

  internal fun setServerListener(listener: OnServerStartListener?) {
    if (toolingServerRunner != null) {
      toolingServerRunner!!.setListener(listener)
    }
  }

  internal fun startToolingServer(listener: OnServerStartListener?) {
    if (toolingServerRunner?.isStarted != true) {
      val envs = IdeShellEnvironment.getEnvironment(this, false)
      toolingServerRunner = ToolingServerRunner(listener, this).also { it.startAsync(envs) }
      return
    }

    if (toolingServerRunner!!.isStarted && listener != null) {
      listener.onServerStarted(toolingServerRunner!!.pid!!)
    } else {
      setServerListener(listener)
    }
  }

  private fun startServerOutputReader(input: InputStream) {
    if (outputReaderJob?.isActive == true) {
      return
    }

    outputReaderJob = buildServiceScope.launch(
      Dispatchers.IO + CoroutineName("ToolingServerErrorReader")) {
      val reader = input.bufferedReader()
      try {
        reader.forEachLine { line ->
          log.error(line)
        }
      } catch (e: Throwable) {
        e.ifCancelledOrInterrupted(suppress = true) {
          return@launch
        }
        log.error("Failed to read tooling server output", e)
      }
    }
  }

  private val isGradleWrapperAvailable: Boolean
    get() {
      val projectManager = ProjectManagerImpl.getInstance()
      val projectDir = projectManager.projectDirPath
      if (projectDir.isNullOrEmpty()) {
        return false
      }

      val projectRoot = projectManager.projectDir
      if (!projectRoot.exists()) {
        return false
      }

      val gradlew = java.io.File(projectRoot, "gradlew")
      val gradleWrapperJar = java.io.File(projectRoot, "gradle/wrapper/gradle-wrapper.jar")
      val gradleWrapperProps = java.io.File(projectRoot, "gradle/wrapper/gradle-wrapper.properties")
      return gradlew.exists() && gradleWrapperJar.exists() && gradleWrapperProps.exists()
    }
}