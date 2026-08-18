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

package com.hmx.ide.tooling.api

import com.hmx.ide.tooling.api.messages.LogMessageParams
import com.hmx.ide.tooling.api.messages.result.GradleWrapperCheckResult
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment
import java.util.concurrent.*

/**
 * A client consumes services provided by [IToolingApiServer].
 * Used for logging and configuration of the read-only sync/indexing process.
 *
 * @author Akash Yadav
 */
@JsonSegment("client")
interface IToolingApiClient {

  /**
   * Log the given log message.
   *
   * @param params The parameters to log the message.
   */
  @JsonNotification fun logMessage(params: LogMessageParams)

  /**
   * Get the extra build arguments that will be used for every sync.
   *
   * @return The extra build arguments.
   */
  @JsonRequest fun getBuildArguments(): CompletableFuture<List<String>>

  /**
   * Tells the client to check if the Gradle wrapper files are available.
   *
   * @return A [CompletableFuture] which completes when the client is done checking the wrapper
   * availability. The future provides a result which tells if the wrapper is available or not.
   */
  @JsonRequest fun checkGradleWrapperAvailability(): CompletableFuture<GradleWrapperCheckResult>
}
