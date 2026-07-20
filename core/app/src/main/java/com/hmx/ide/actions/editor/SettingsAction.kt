/*
 *  This file is part of HMX IDE.
 *
 *  HMX IDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  HMX IDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with HMX IDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.hmx.ide.actions.editor

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.hmx.ide.R
import com.hmx.ide.actions.ActionData
import com.hmx.ide.actions.EditorActivityAction
import com.hmx.ide.activities.PreferencesActivity

/**
 * Opens IDE preferences. Shown in the editor's overflow menu
 * after the sidebar navigation rail was removed.
 */
class SettingsAction(context: Context, override val order: Int) : EditorActivityAction() {

  init {
    label = context.getString(R.string.ide_preferences)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_settings)
  }

  override val id: String = "ide.editor.settings"

  override suspend fun execAction(data: ActionData): Any {
    data.requireActivity().startActivity(Intent(data.requireActivity(), PreferencesActivity::class.java))
    return true
  }
}
