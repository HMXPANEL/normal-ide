/*
 *  This file is part of HMX IDE.
 *
 *  HMX IDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package com.hmx.ide.crash

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Process
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.blankj.utilcode.util.ClipboardUtils
import com.hmx.ide.R
import com.hmx.ide.activities.CrashHandlerActivity
import com.hmx.ide.resources.R as ResourcesR
import kotlin.system.exitProcess

/**
 * Secondary crash channel: the lightweight "HMX IDE crashed" card drawn over the home
 * screen with WindowManager (requires the user-granted SYSTEM_ALERT_WINDOW permission).
 *
 * The window is owned by the process that adds it, so the crashing process stays alive
 * only while this popup is on screen and kills itself when the popup is dismissed. This is
 * a one-shot crash popup, NOT a permanent floating bubble or monitoring service.
 *
 * Everything here is deliberately tiny and offline: a few views and WindowManager calls.
 */
object CrashOverlay {

  private var view: View? = null

  /** Adds the crash card to the window; returns false if it could not be shown. */
  fun show(context: Context, summary: String, report: String): Boolean {
    if (view != null) {
      return true
    }
    return try {
      val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
      val v = LayoutInflater.from(context).inflate(R.layout.layout_crash_popup, null)
      v.findViewById<TextView>(R.id.crash_summary).text = summary

      v.findViewById<View>(R.id.crash_overlay_view_log).setOnClickListener {
        openReport(context, report, summary)
        dismiss(wm, v)
        Process.killProcess(Process.myPid())
        exitProcess(1)
      }
      v.findViewById<View>(R.id.crash_overlay_copy).setOnClickListener {
        ClipboardUtils.copyText("HMX IDE CrashLog", report)
        Toast.makeText(context, ResourcesR.string.crash_copied, Toast.LENGTH_SHORT).show()
      }
      v.findViewById<View>(R.id.crash_overlay_close).setOnClickListener {
        CrashNotifier.cancel(context)
        dismiss(wm, v)
        Process.killProcess(Process.myPid())
        exitProcess(1)
      }

      val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
      )
      params.gravity = Gravity.TOP
      wm.addView(v, params)
      view = v
      true
    } catch (_: Throwable) {
      false
    }
  }

  private fun openReport(context: Context, report: String, summary: String) {
    val intent = Intent(context, CrashHandlerActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
      putExtra(CrashHandlerActivity.TRACE_KEY, report)
      putExtra(CrashHandlerActivity.SUMMARY_KEY, summary)
    }
    context.startActivity(intent)
  }

  private fun dismiss(wm: WindowManager, v: View) {
    try {
      wm.removeView(v)
    } catch (_: Throwable) {
      // Ignore: the window may already be gone (e.g. process being killed).
    }
    view = null
  }
}