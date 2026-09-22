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

package com.hmx.ide.projects.builder

import com.hmx.ide.lookup.Lookup
import com.hmx.ide.lookup.Lookup.Key
import com.hmx.ide.tooling.api.IProject
import com.hmx.ide.tooling.api.messages.InitializeProjectParams
import com.hmx.ide.tooling.api.messages.result.InitializeResult
import com.hmx.ide.tooling.api.models.ToolingServerMetadata
import java.util.concurrent.CompletableFuture

/**
 * A build service provides API to initialize project, query project model,
 * and manage the tooling API server for read-only project sync and indexing.
 *
 * @author Akash Yadav
 */
interface BuildService {

  companion object {

    /** Key that can be used to retrieve the [BuildService] instance using the [Lookup] API. */
    @JvmField
    val KEY_BUILD_SERVICE = Key<BuildService>()

    /**
     * Key that can be used to retrieve the instance of Tooling API's [IProject] model using the
     * [Lookup] API.
     */
    @JvmField
    val KEY_PROJECT_PROXY = Key<IProject>()
  }

  /** Returns `true` if and only if the tooling API server has been started, `false` otherwise. */
  fun isToolingServerStarted(): Boolean

  /**
   * Returns the [ToolingServerMetadata] of the tooling API server.
   */
  fun metadata(): CompletableFuture<ToolingServerMetadata>

  /**
   * Initialize the project (sync).
   *
   * @param params Parameters for the project initialization.
   * @return A [CompletableFuture] which returns an [InitializeResult] when the project
   *   initialization process finishes.
   */
  fun initializeProject(params: InitializeProjectParams): CompletableFuture<InitializeResult>
}
