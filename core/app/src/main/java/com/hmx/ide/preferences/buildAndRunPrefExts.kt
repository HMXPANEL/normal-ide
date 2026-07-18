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

import android.content.Context
import androidx.preference.Preference
import com.hmx.ide.app.configuration.IJdkDistributionProvider
import com.hmx.ide.models.JdkDistribution
import com.hmx.ide.preferences.internal.BuildPreferences.PREF_JAVA_HOME
import com.hmx.ide.preferences.internal.BuildPreferences.javaHome
import com.hmx.ide.resources.R.drawable
import com.hmx.ide.resources.R.string
import kotlinx.parcelize.Parcelize

@Parcelize
class BuildAndRunPreferences(
  override val key: String = "idepref_build_n_run",
  override val title: Int = string.idepref_build_title,
  override val summary: Int? = string.idepref_buildnrun_summary,
  override val children: List<IPreference> = mutableListOf(),
) : IPreferenceScreen() {

  init {
    addPreference(GradleJDKVersionPreference())
  }
}

@Parcelize
class GradleJDKVersionPreference(
  override val key: String = PREF_JAVA_HOME,
  override val title: Int = string.idepref_jdkVersion_title,
  override val icon: Int? = drawable.ic_language_java,
) : SingleChoicePreference() {

  override fun getEntries(preference: Preference): Array<PreferenceChoices.Entry> {
    val distributions = IJdkDistributionProvider.getInstance().installedDistributions
    check(distributions.isNotEmpty()) {
      "No JDK installations are available."
    }

    return distributions.map { dist ->
      PreferenceChoices.Entry(dist.javaVersion, javaHome == dist.javaHome, dist)
    }.toTypedArray()
  }

  override fun onChoiceConfirmed(
    preference: Preference,
    entry: PreferenceChoices.Entry?,
    position: Int
  ) {
    super.onChoiceConfirmed(preference, entry, position)
    javaHome = (entry?.data as? JdkDistribution?)?.javaHome ?: ""
    updatePreference(preference)
  }

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).also { preference ->
      updatePreference(preference)
    }
  }

  private fun updatePreference(preference: Preference) {
    val jdkDistProvider = IJdkDistributionProvider.getInstance()
    val javaVersion = jdkDistProvider.forJavaHome(javaHome)?.javaVersion
      ?: "<unknown>"

    preference.summary = preference.context.getString(string.idepref_jdkVersion_summary,
      javaVersion)
    preference.isEnabled = jdkDistProvider.installedDistributions.size > 1
  }
}
