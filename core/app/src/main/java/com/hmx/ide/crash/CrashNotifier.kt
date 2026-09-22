/*
 *  This file is part of HMX IDE.
 *
 *  HMX IDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package com.hmx.ide.crash

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hmx.ide.activities.CrashHandlerActivity
import com.hmx.ide.resources.R

/**
 * FALLBACK crash channel: a high-priority notification that opens the crash report.
 *
 * Modern Android may block an Activity launch from a crashed/background process, so the
 * report must also be reachable from a system-managed surface. The notification survives
 * process death and is cancelled as soon as [CrashHandlerActivity] actually comes to the
 * foreground, so it is never shown when the crash UI is already visible.
 *
 * The report is carried only in the notification's PendingIntent (in-memory). Nothing is
 * stored, uploaded or persisted.
 */
object CrashNotifier {

  const val CHANNEL_ID = "crash_reports"
  const val NOTIFICATION_ID = 0x44E7A92

  @JvmStatic
  fun show(context: Context, summary: String, report: String) {
    createChannel(context)
    if (Build.VERSION.SDK_INT >= 33 &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      // Can't post without the runtime permission. The Crash Reporting settings screen
      // explains this and lets the user grant it. Never request or bypass silently here.
      return
    }

    val intent = Intent(context, CrashHandlerActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
      putExtra(CrashHandlerActivity.TRACE_KEY, report)
      putExtra(CrashHandlerActivity.SUMMARY_KEY, summary)
    }
    val pending = PendingIntent.getActivity(
      context,
      0,
      intent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_close)
      .setContentTitle(context.getString(R.string.msg_ide_crashed))
      .setContentText(summary.lineSequence().firstOrNull() ?: "")
      .setStyle(NotificationCompat.BigTextStyle().bigText(report))
      .setContentIntent(pending)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_ERROR)
      .build()
    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
  }

  @JvmStatic
  fun cancel(context: Context) {
    NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
  }

  private fun createChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= 26) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(
        NotificationChannel(
          CHANNEL_ID,
          context.getString(R.string.title_crash_reporting),
          NotificationManager.IMPORTANCE_HIGH
        )
      )
    }
  }
}