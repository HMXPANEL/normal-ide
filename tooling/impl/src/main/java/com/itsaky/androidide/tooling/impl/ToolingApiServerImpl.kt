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

package com.itsaky.androidide.tooling.impl

import com.itsaky.androidide.tooling.api.IProject
import com.itsaky.androidide.tooling.api.IToolingApiClient
import com.itsaky.androidide.tooling.api.IToolingApiServer
import com.itsaky.androidide.tooling.api.messages.GradleDistributionParams
import com.itsaky.androidide.tooling.api.messages.GradleDistributionType
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.api.messages.result.InitializeResult
import com.itsaky.androidide.tooling.api.models.ToolingServerMetadata
import com.itsaky.androidide.tooling.impl.internal.ProjectImpl
import com.itsaky.androidide.tooling.impl.sync.ModelBuilderException
import com.itsaky.androidide.tooling.impl.sync.RootModelBuilder
import com.itsaky.androidide.tooling.impl.sync.RootProjectModelBuilderParams
import com.itsaky.androidide.utils.StopWatch
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Implementation for the Gradle Tooling API server.
 * Provides read-only project sync, indexing, dependency resolution, autocomplete,
 * symbol resolution, and error detection.
 *
 * @author Akash Yadav
 */
internal class ToolingApiServerImpl(private val project: ProjectImpl) :
  IToolingApiServer {

  private var client: IToolingApiClient? = null
  private var connector: GradleConnector? = null
  private var connection: org.gradle.tooling.ProjectConnection? = null
  private var lastInitParams: InitializeProjectParams? = null

  /**
   * Whether the project has been initialized or not.
   */
  var isInitialized: Boolean = false
    private set

  /**
   * Whether the server has a live connection to Gradle.
   */
  val isConnected: Boolean
    get() = connector != null || connection != null

  companion object {

    private val log = LoggerFactory.getLogger(ToolingApiServerImpl::class.java)

    /**
     * Time duration for which the the Tooling API server waits after calling
     * [DefaultGradleConnector.close] and before exiting the server's process.
     *
     * This delay should be long enough to let the tooling API stop the daemon but short enough so
     * that the server's process is not kept alive for longer duration.
     */
    const val DELAY_BEFORE_EXIT_MS = 1000L
  }

  override fun metadata(): CompletableFuture<ToolingServerMetadata> {
    return CompletableFuture.supplyAsync {
      ToolingServerMetadata(ProcessHandle.current().pid().toInt())
    }
  }

  override fun initialize(params: InitializeProjectParams): CompletableFuture<InitializeResult> {
    return CompletableFuture.supplyAsync {
      try {
        log.debug("Received project initialization request with params: {}", params)

        val projectDirectory = File(params.directory)
        val failureReason = validateProjectDirectory(projectDirectory)

        if (failureReason != null) {
          log.error("Cannot initialize project: {}", failureReason)
          return@supplyAsync InitializeResult(false, failureReason)
        }

        val stopWatch = StopWatch("Connection to project")
        val isReinitializing = connector != null && connection != null && params == lastInitParams

        if (isReinitializing) {
          log.info("Project is being reinitialized")
          log.info("Reusing connector instance...")
        } else {
          // a new project is being initialized
          // or the project is being initialized with different parameters
          connector?.disconnect()

          connector = GradleConnector.newConnector().forProjectDirectory(projectDirectory)
          setupConnectorForGradleInstallation(this.connector!!, params.gradleDistribution)
          stopWatch.lap("Connector created")
        }

        lastInitParams = params

        val connector = checkNotNull(connector) {
          "Unable to create gradle connector for project directory: ${params.directory}"
        }

        if (isReinitializing) {
          log.info("Reusing project connection...")
        } else {
          connection = connector.connect()
        }

        val connection = checkNotNull(this.connection) {
          "Unable to create project connection for project directory: ${params.directory}"
        }

        stopWatch.lapFromLast("Project connection established")

        val proj = try {
          val modelBuilderParams = RootProjectModelBuilderParams(
            connection,
            org.gradle.tooling.GradleConnector.newCancellationTokenSource().token()
          )
          val impl = RootModelBuilder(params).build(modelBuilderParams) as? ProjectImpl?
            ?: throw ModelBuilderException("Failed to build project model")
          impl
        } catch (err: Throwable) {
          throw err
        }

        stopWatch.lapFromLast("Project read successful")
        stopWatch.log()

        this.project.setFrom(proj)
        this.isInitialized = true

        return@supplyAsync InitializeResult(true)
      } catch (err: Throwable) {
        log.error("Failed to initialize project", err)
        return@supplyAsync InitializeResult(false, getTaskFailureType(err))
      }
    }
  }

  private fun validateProjectDirectory(
    projectDirectory: File
  ) = when {
    !projectDirectory.exists() -> InitializeResult.Failure.PROJECT_NOT_FOUND
    !projectDirectory.isDirectory -> InitializeResult.Failure.PROJECT_NOT_DIRECTORY
    !projectDirectory.canRead() -> InitializeResult.Failure.PROJECT_DIRECTORY_INACCESSIBLE
    else -> null
  }

  override fun isServerInitialized(): CompletableFuture<Boolean> {
    return CompletableFuture.supplyAsync { isInitialized }
  }

  override fun getRootProject(): CompletableFuture<IProject> {
    return CompletableFuture.supplyAsync {
      assertProjectInitialized()
      return@supplyAsync this.project
    }
  }

  private fun setupConnectorForGradleInstallation(
    connector: GradleConnector,
    params: GradleDistributionParams
  ) {
    when (params.type) {
      GradleDistributionType.GRADLE_WRAPPER -> {
        log.info("Using Gradle wrapper for build...")
      }

      GradleDistributionType.GRADLE_INSTALLATION -> {
        val file = File(params.value)
        if (!file.exists() || !file.isDirectory) {
          log.error("Specified Gradle installation does not exist: {}", params)
          return
        }

        log.info("Using Gradle installation: {}", file.canonicalPath)
        connector.useInstallation(file)
      }

      GradleDistributionType.GRADLE_VERSION -> {
        log.info("Using Gradle version '{}'", params.value)
        connector.useGradleVersion(params.value)
      }
    }
  }

  override fun shutdown(): CompletableFuture<Void> {
    return CompletableFuture.supplyAsync {
      log.info("Shutting down Tooling API Server...")

      connection?.close()
      connector?.disconnect()
      connection = null
      connector = null

      // Stop all daemons
      log.info("Stopping all Gradle Daemons...")
      DefaultGradleConnector.close()

      // update the initialization flag before cancelling future
      this.isInitialized = false

      // cancelling this future will finish the Tooling API server process
      // see com.itsaky.androidide.tooling.impl.Main.main(String[])
      Main.future?.cancel(true)

      this.client = null
      this.lastInitParams = null
      Main.future = null
      Main.client = null

      null
    }
  }

  private fun getTaskFailureType(error: Throwable): InitializeResult.Failure =
    when (error) {
      is GradleConnectionException -> InitializeResult.Failure.CONNECTION_ERROR
      is UnsupportedVersionException -> InitializeResult.Failure.UNSUPPORTED_GRADLE_VERSION
      is java.lang.IllegalStateException -> InitializeResult.Failure.CONNECTION_CLOSED
      else -> InitializeResult.Failure.UNKNOWN
    }

  private fun assertProjectInitialized() {
    if (!isServerInitialized().get()) {
      throw CompletionException(IllegalStateException("Project is not initialized!"))
    }
  }

  fun connect(client: IToolingApiClient) {
    this.client = client
  }
}