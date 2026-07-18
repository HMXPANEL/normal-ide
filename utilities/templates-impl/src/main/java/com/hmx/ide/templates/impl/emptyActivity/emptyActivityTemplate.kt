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

package com.hmx.ide.templates.impl.emptyActivity

import com.hmx.ide.templates.ProjectTemplate
import com.hmx.ide.templates.base.AndroidModuleTemplateBuilder
import com.hmx.ide.templates.base.modules.android.defaultAppModule
import com.hmx.ide.templates.base.util.AndroidModuleResManager.ResourceType.LAYOUT
import com.hmx.ide.templates.base.util.SourceWriter
import com.hmx.ide.templates.impl.R
import com.hmx.ide.templates.impl.base.createRecipe
import com.hmx.ide.templates.impl.base.emptyThemesAndColors
import com.hmx.ide.templates.impl.base.writeMainActivity
import com.hmx.ide.templates.impl.baseProjectImpl

fun emptyActivityProject(): ProjectTemplate = baseProjectImpl {
  templateName = R.string.template_empty
  thumb = R.drawable.template_empty_activity
  defaultAppModule {
    recipe = createRecipe {
      sources {
        writeEmptyActivity(this)
      }

      res {
        writeEmptyActivity()
      }
    }
  }
}

internal fun AndroidModuleTemplateBuilder.writeEmptyActivity() {
  res.apply {
    // layout/activity_main.xml
    writeXmlResource("activity_main", LAYOUT, source = ::emptyLayoutSrc)
    emptyThemesAndColors()
  }
}

internal fun AndroidModuleTemplateBuilder.writeEmptyActivity(
  writer: SourceWriter
) {
  writeMainActivity(writer, ::emptyActivitySrcKt, ::emptyActivitySrcJava)
}