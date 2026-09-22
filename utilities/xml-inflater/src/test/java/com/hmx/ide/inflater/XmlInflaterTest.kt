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

package com.hmx.ide.inflater

import androidx.appcompat.app.AppCompatActivity
import com.google.common.truth.Truth.assertThat
import com.hmx.ide.inflater.utils.endParse
import com.hmx.ide.inflater.utils.startParse
import com.hmx.ide.lookup.Lookup
import com.hmx.ide.projects.IProjectManager
import com.hmx.ide.projects.android.AndroidModule
import com.hmx.ide.projects.builder.BuildService
import com.hmx.ide.projects.util.findAppModule
import com.hmx.ide.testing.tooling.ToolingApiTestLauncher
import com.hmx.ide.testing.tooling.models.ToolingApiTestLauncherParams
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.robolectric.Robolectric
import java.util.concurrent.atomic.AtomicBoolean

@Ignore("Test utility provider")
object XmlInflaterTest {

  private var init = AtomicBoolean(false)
  internal val activity by lazy { Robolectric.buildActivity(AppCompatActivity::class.java).get() }

  fun initIfNeeded() {
    if (init.get()) {
      return
    }

    val params = ToolingApiTestLauncherParams()
    ToolingApiTestLauncher.launchServer(params) {
      assertThat(result?.isSuccessful).isTrue()

      Lookup.getDefault().register(BuildService.KEY_PROJECT_PROXY, project)

      val projectManager = IProjectManager.getInstance()
      projectManager.openProject(params.projectDir.toFile())

      runBlocking { projectManager.setupProject(project) }

      init.set(true)
    }
  }
}

fun inflaterTest(block: (AndroidModule) -> Unit) {
  XmlInflaterTest.initIfNeeded()
  val app = findAppModule()!!
  startParse(app)
  block(app)
  endParse()
}

fun requiresActivity(block: AppCompatActivity.() -> Unit) {
  XmlInflaterTest.activity.block()
}
