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

package com.hmx.ide.activities

import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.appintro.AppIntro2
import com.github.appintro.AppIntroPageTransformerType
import com.hmx.ide.R
import com.hmx.ide.R.string
import com.hmx.ide.app.configuration.IDEBuildConfigProvider
import com.hmx.ide.fragments.onboarding.GreetingFragment
import com.hmx.ide.fragments.onboarding.OnboardingInfoFragment
import com.hmx.ide.fragments.onboarding.PermissionsFragment
import com.hmx.ide.ui.themes.IThemeManager

class OnboardingActivity : AppIntro2() {

  override fun onCreate(savedInstanceState: Bundle?) {
    IThemeManager.getInstance().applyTheme(this)

    super.onCreate(savedInstanceState)

    if (tryNavigateToMainIfSetupIsCompleted()) {
      return
    }

    setSwipeLock(true)
    setTransformer(AppIntroPageTransformerType.Fade)
    setProgressIndicator()
    showStatusBar(true)
    isIndicatorEnabled = true
    isWizardMode = true

    addSlide(GreetingFragment())

    if (!checkDeviceSupported()) {
      return
    }

    if (!PermissionsFragment.areAllPermissionsGranted(this)) {
      addSlide(PermissionsFragment.newInstance(this))
    }
  }

  override fun onResume() {
    super.onResume()
    tryNavigateToMainIfSetupIsCompleted()
  }

  override fun onDonePressed(currentFragment: Fragment?) {
    if (!IDEBuildConfigProvider.getInstance().supportsCpuAbi()) {
      finishAffinity()
      return
    }

    tryNavigateToMainIfSetupIsCompleted()
  }

  private fun isSetupCompleted(): Boolean {
    return PermissionsFragment.areAllPermissionsGranted(this)
  }

  private fun tryNavigateToMainIfSetupIsCompleted(): Boolean {
    if (isSetupCompleted()) {
      startActivity(Intent(this, MainActivity::class.java))
      finish()
      return true
    }

    return false
  }

  private fun checkDeviceSupported(): Boolean {
    val configProvider = IDEBuildConfigProvider.getInstance()

    if (!configProvider.supportsCpuAbi()) {
      addSlide(OnboardingInfoFragment.newInstance(
        getString(string.title_unsupported_device),
        getString(
          string.msg_unsupported_device,
          configProvider.cpuArch.abi,
          configProvider.deviceArch.abi
        ),
        R.drawable.ic_alert,
        ContextCompat.getColor(this, R.color.color_error)
      ))
      return false
    }

    return true
  }
}
