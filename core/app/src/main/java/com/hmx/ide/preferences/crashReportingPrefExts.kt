/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package com.hmx.ide.preferences

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hmx.ide.resources.R
import kotlinx.parcelize.Parcelize

/**
 * Crash Reporting settings. Explains how crash popups/notifications work and opens the
 * system permission screens (overlay + notifications) — permissions are never requested
 * or granted silently.
 */
@Parcelize
internal class CrashReportingPreferences(
  override val key: String = "idepref_crash_reporting",
  override val title: Int = R.string.title_crash_reporting,
  override val summary: Int? = R.string.idepref_crash_reporting_summary,
  override val children: List<IPreference> = mutableListOf()
) : IPreferenceGroup() {

  init {
    if (children.isEmpty()) {
      addPreference(crashOverlayPreference)
      addPreference(crashNotificationPreference)
    }
  }
}

private val crashOverlayPreference =
  SimpleClickablePreference(
    key = "idepref_crash_overlay",
    title = R.string.idepref_crash_overlay_title,
    summary = R.string.idepref_crash_overlay_summary
  ) {
    val context = it.context
    try {
      context.startActivity(
        Intent(
          Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
          Uri.parse("package:${context.packageName}")
        )
      )
    } catch (_: Throwable) {
      context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
    }
    true
  }

private val crashNotificationPreference =
  SimpleClickablePreference(
    key = "idepref_crash_notifications",
    title = R.string.idepref_crash_notifications_title,
    summary = R.string.idepref_crash_notifications_summary
  ) {
    val context = it.context
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) {
        (context as? android.app.Activity)?.let { activity ->
          ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_POST_NOTIFICATIONS
          )
        }
      } else {
        openAppNotificationSettings(context)
      }
    } else {
      openAppNotificationSettings(context)
    }
    true
  }

private const val REQUEST_POST_NOTIFICATIONS = 1001

private fun openAppNotificationSettings(context: Context) {
  try {
    context.startActivity(
      Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )
  } catch (_: Throwable) {
    context.startActivity(Intent(Settings.ACTION_SETTINGS))
  }
}
