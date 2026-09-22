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

package com.hmx.ide.utils

import android.content.Context
import com.hmx.ide.utils.Environment.ANDROID_HOME
import java.util.HashMap

/**
 * IDE-native shell environment used to launch Gradle builds and detect JDK installations.
 *
 * Builds the same variables the previous bundled-terminal environment provided, but against the
 * IDE's own prefix under [Environment.PREFIX] (typically `$filesDir/usr`).
 */
object IdeShellEnvironment {

  const val ENV_HOME = "HOME"
  const val ENV_PREFIX = "PREFIX"
  const val ENV_TMPDIR = "TMPDIR"
  const val ENV_PATH = "PATH"
  const val ENV_LD_LIBRARY_PATH = "LD_LIBRARY_PATH"

  /**
   * Build the shell environment for the IDE. When [isFailSafe] is false the IDE prefix `bin`
   * directory and the Android SDK cmdline-tools are prepended to [ENV_PATH] and the system
   * [ENV_LD_LIBRARY_PATH] is dropped so the bundled toolchain is preferred.
   */
  @JvmStatic
  fun getEnvironment(context: Context, isFailSafe: Boolean): HashMap<String, String> {
    val environment = HashMap<String, String>()
    Environment.putEnvironment(environment, isFailSafe)

    environment[ENV_HOME] = Environment.HOME.absolutePath
    environment[ENV_PREFIX] = Environment.PREFIX.absolutePath

    if (!isFailSafe) {
      environment[ENV_TMPDIR] = Environment.TMP_DIR.absolutePath
      val androidTools = "${ANDROID_HOME.absolutePath}/cmdline-tools/latest/bin"
      environment[ENV_PATH] =
        "${Environment.BIN_DIR.absolutePath}:$androidTools:${environment[ENV_PATH] ?: ""}"
      environment.remove(ENV_LD_LIBRARY_PATH)
    }

    return environment
  }
}
