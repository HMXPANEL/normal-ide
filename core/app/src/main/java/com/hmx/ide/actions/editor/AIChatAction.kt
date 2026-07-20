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
import com.hmx.ide.activities.aichat.AIChatActivity

/**
 * Opens the project-aware AI Chat in a full-screen activity.
 * Only available when a project is opened.
 */
class AIChatAction(context: Context, override val order: Int) : EditorActivityAction() {

  init {
    label = context.getString(R.string.title_ai_chat)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_ai_chat)
  }

  override val id: String = "ide.editor.aiChat"

  override suspend fun execAction(data: ActionData): Any {
    val activity = data.requireActivity()
    activity.startActivity(Intent(activity, AIChatActivity::class.java))
    return true
  }
}
