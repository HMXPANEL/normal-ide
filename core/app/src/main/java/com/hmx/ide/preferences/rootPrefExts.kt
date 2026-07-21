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

package com.hmx.ide.preferences

import com.hmx.ide.resources.R.string
import kotlinx.parcelize.Parcelize

internal fun IDEPreferences.addRootPreferences() {
  addPreference(ConfigurationPreferences())
  addPreference(DeveloperOptionsPreferences())
  addPreference(AboutPreferences())
}

@Parcelize
class ConfigurationPreferences(
  override val key: String = "idepref_configure",
  override val title: Int = string.configure,
  override val children: List<IPreference> = mutableListOf()
) : IPreferenceGroup() {

  init {
    if (children.isEmpty()) {
      addPreference(GeneralPreferencesScreen())
      addPreference(EditorPreferencesScreen())
      addPreference(aiModelsPreference)
      addPreference(BuildAndRunPreferences())
    }
  }
}

@Parcelize
class DeveloperOptionsPreferences(
  override val key: String = "idepref_devOpts",
  override val title: Int = string.title_developer_options,
  override val children: List<IPreference> = mutableListOf()
) : IPreferenceGroup() {

  init {
    if (children.isEmpty()) {
      addPreference(DeveloperOptionsScreen())
    }
  }
}

@Parcelize
class AboutPreferences(
  override val key: String = "idepref_category_about",
  override val title: Int = string.about,
  override val children: List<IPreference> = mutableListOf()
) : IPreferenceGroup() {

  init {
    if (children.isEmpty()) {
      addPreference(changelog)
      addPreference(about)
    }
  }
}
