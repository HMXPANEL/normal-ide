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

package com.hmx.ide.preferences.internal

import androidx.appcompat.app.AppCompatDelegate
import com.hmx.ide.resources.localization.LocaleProvider

/**
 * @author Akash Yadav
 */
@Suppress("MemberVisibilityCanBePrivate")
object GeneralPreferences {

  const val IS_FIRST_PROJECT_BUILD = "project_isFirstBuild"
  const val UI_MODE = "idepref_general_uiMode"
  const val SELECTED_THEME = "idpref_general_theme"
  const val SELECTED_LOCALE = "idpref_general_locale"
  const val OPEN_PROJECTS = "idepref_general_autoOpenProjects"
  const val CONFIRM_PROJECT_OPEN = "idepref_general_confirmProjectOpen"
  const val LAST_OPENED_PROJECT = "ide_last_project"

  const val RECENT_PROJECTS = "ide_recent_projects"

  const val NO_OPENED_PROJECT = "<NO_OPENED_PROJECT>"

  var uiMode: Int
    get() = prefManager.getInt(UI_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    set(value) {
      prefManager.putInt(UI_MODE, value)
    }

  var selectedTheme: String?
    get() = prefManager.getString(SELECTED_THEME, null)
    set(value) {
      prefManager.putString(SELECTED_THEME, value)
    }

  var selectedLocale: String?
    get() = prefManager.getString(SELECTED_LOCALE, null).let { locale ->

      // if the locale is set to a locale key that is not supported,
      // fall back to 'System default'
      if (LocaleProvider.getLocale(locale) == null) {
        null
      } else {
        locale
      }
    }
    set(value) {
      prefManager.putString(SELECTED_LOCALE, value)
    }

  var isFirstBuild: Boolean
    get() = prefManager.getBoolean(IS_FIRST_PROJECT_BUILD, true)
    set(value) {
      prefManager.putBoolean(IS_FIRST_PROJECT_BUILD, value)
    }

  var autoOpenProjects: Boolean
    get() = prefManager.getBoolean(OPEN_PROJECTS, true)
    set(value) {
      prefManager.putBoolean(OPEN_PROJECTS, value)
    }

  var confirmProjectOpen: Boolean
    get() = prefManager.getBoolean(CONFIRM_PROJECT_OPEN, false)
    set(value) {
      prefManager.putBoolean(CONFIRM_PROJECT_OPEN, value)
    }

  var lastOpenedProject: String
    get() = prefManager.getString(LAST_OPENED_PROJECT, NO_OPENED_PROJECT)
    set(value) {
      prefManager.putString(LAST_OPENED_PROJECT, value)
    }

  /**
   * Recently opened projects, most-recent-first. Persisted across app restarts.
   */
  var recentProjects: List<String>
    get() {
      val raw = prefManager.getString(RECENT_PROJECTS, null) ?: return emptyList()
      return raw.split(RECENT_PROJECTS_SEPARATOR).filter { it.isNotBlank() }
    }
    set(value) {
      prefManager.putString(RECENT_PROJECTS, value.joinToString(RECENT_PROJECTS_SEPARATOR))
    }

  /**
   * Add (or move to the top of) a project path in the recent projects list.
   */
  fun addRecentProject(path: String) {
    val updated = buildList {
      add(path)
      recentProjects.forEach { if (it != path) add(it) }
    }.take(MAX_RECENT_PROJECTS)
    recentProjects = updated
    lastOpenedProject = path
  }

  private const val RECENT_PROJECTS_SEPARATOR = "|"

  private const val MAX_RECENT_PROJECTS = 20
}