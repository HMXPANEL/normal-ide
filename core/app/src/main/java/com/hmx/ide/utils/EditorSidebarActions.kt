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
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.core.view.forEach
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.createGraph
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.FragmentNavigatorDestinationBuilder
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.get
import androidx.navigation.navOptions
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.hmx.ide.actions.ActionData
import com.hmx.ide.actions.ActionItem
import com.hmx.ide.actions.ActionsRegistry
import com.hmx.ide.actions.FillMenuParams
import com.hmx.ide.actions.SidebarActionItem
import com.hmx.ide.actions.internal.DefaultActionsRegistry
import com.hmx.ide.actions.sidebar.FileTreeSidebarAction
import com.hmx.ide.fragments.sidebar.EditorSidebarFragment
import java.lang.ref.WeakReference


/**
 * Sets up the actions that are shown in the
 * [EditorActivityKt][com.hmx.ide.activities.editor.EditorActivityKt]'s drawer's sidebar.
 *
 * @author Akash Yadav
 */
internal object EditorSidebarActions {

  @JvmStatic
  fun registerActions(context: Context) {
    val registry = ActionsRegistry.getInstance()
    var order = -1

    // File Tree remains the default content of the navigation drawer (slide-out panel).
    // The rail with its icon buttons was removed; Settings and Close Project are now
    // exposed via the editor toolbar's overflow menu.
    @Suppress("KotlinConstantConditions")
    registry.registerAction(FileTreeSidebarAction(context, ++order))
  }

  @JvmStatic
  fun setup(sidebarFragment: EditorSidebarFragment) {
    val binding = sidebarFragment.getBinding() ?: return
    val controller = binding.fragmentContainer.getFragment<NavHostFragment>().navController
    val context = sidebarFragment.requireContext()

    val registry = ActionsRegistry.getInstance()
    val actions = registry.getActions(ActionItem.Location.EDITOR_SIDEBAR)
    if (actions.isEmpty()) {
      return
    }

    val data = ActionData()
    data.put(Context::class.java, context) // needed for inflating the menu

    controller.graph = controller.createGraph(startDestination = FileTreeSidebarAction.ID) {
      actions.forEach { (actionId, action) ->
        if (action !is SidebarActionItem) {
          throw IllegalStateException(
            "Actions registered at location ${ActionItem.Location.EDITOR_SIDEBAR}" +
                " must implement ${SidebarActionItem::class.java.simpleName}")
        }

        val fragment = action.fragmentClass ?: return@forEach

        val builder = FragmentNavigatorDestinationBuilder(
          provider[FragmentNavigator::class],
          actionId,
          fragment
        )

        builder.apply {
          action.apply { buildNavigation() }
        }

        destination(builder)
      }
    }
  }

  /**
   * Determines whether the given `route` matches the NavDestination. This handles
   * both the default case (the destination's route matches the given route) and the nested case where
   * the given route is a parent/grandparent/etc of the destination.
   */
  @JvmStatic
  internal fun NavDestination.matchDestination(route: String): Boolean =
    hierarchy.any { it.route == route }

  @JvmStatic
  internal fun NavDestination.matchDestination(@IdRes destId: Int): Boolean =
    hierarchy.any { it.id == destId }

  @JvmStatic
  internal fun ShapeAppearanceModel.roundedOnRight(cornerSize: Float = 28f): ShapeAppearanceModel {
    return toBuilder().run {
      setTopRightCorner(CornerFamily.ROUNDED, cornerSize)
      setBottomRightCorner(CornerFamily.ROUNDED, cornerSize)
      build()
    }
  }
}
