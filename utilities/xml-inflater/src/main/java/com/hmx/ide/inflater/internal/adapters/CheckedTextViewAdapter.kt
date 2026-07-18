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

package com.hmx.ide.inflater.internal.adapters

import android.widget.CheckedTextView
import com.hmx.ide.annotations.inflater.ViewAdapter
import com.hmx.ide.annotations.uidesigner.IncludeInDesigner
import com.hmx.ide.annotations.uidesigner.IncludeInDesigner.Group.WIDGETS
import com.hmx.ide.inflater.AttributeHandlerScope
import com.hmx.ide.inflater.models.UiWidget
import com.hmx.ide.resources.R.drawable
import com.hmx.ide.resources.R.string

/**
 * Attribute adapter for [CheckedTextView].
 *
 * @author Akash Yadav
 */
@ViewAdapter(CheckedTextView::class)
@IncludeInDesigner(group = WIDGETS)
open class CheckedTextViewAdapter<T : CheckedTextView> : TextViewAdapter<T>() {

  override fun createAttrHandlers(create: (String, AttributeHandlerScope<T>.() -> Unit) -> Unit) {
    super.createAttrHandlers(create)
    create("checkMarkTintMode") { view.checkMarkTintMode = parsePorterDuffMode(value) }
    create("checkMarkTint") { view.checkMarkTintList = parseColorStateList(context, value) }
  }

  override fun createUiWidgets(): List<UiWidget> {
    return listOf(
      UiWidget(
        CheckedTextView::class.java,
        string.widget_checked_textview,
        drawable.ic_widget_checked_textview
      )
    )
  }
}
