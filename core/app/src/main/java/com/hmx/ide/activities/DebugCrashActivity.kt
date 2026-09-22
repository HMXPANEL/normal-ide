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
 *   You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.hmx.ide.activities

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.hmx.ide.BuildConfig
import com.hmx.ide.app.IDEActivity

/**
 * Debug-only controlled crash trigger. Gated by [BuildConfig.DEBUG] so it can never fire in a
 * release build. Throwing here routes through the global [Thread.getDefaultUncaughtExceptionHandler]
 * set in [com.hmx.ide.app.IDEApplication], letting a developer verify the in-app crash reporter.
 */
class DebugCrashActivity : IDEActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (BuildConfig.DEBUG) {
      throw NullPointerException(
        "Controlled debug crash triggered from HMX IDE (BuildConfig.DEBUG == true)"
      )
    }
    finish()
  }

  override fun bindLayout(): View = FrameLayout(this)
}
