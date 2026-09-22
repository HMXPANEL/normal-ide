/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.hmx.ide.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.blankj.utilcode.util.ClipboardUtils
import com.hmx.ide.databinding.LayoutCrashReportBinding
import com.hmx.ide.resources.R

class CrashReportFragment : Fragment() {

  private var binding: LayoutCrashReportBinding? = null
  private var closeAppOnClick = true

  companion object {

    const val KEY_TITLE = "crash_title"
    const val KEY_MESSAGE = "crash_message"
    const val KEY_TRACE = "crash_trace"
    const val KEY_CLOSE_APP_ON_CLICK = "close_on_app_click"

    @JvmStatic
    fun newInstance(trace: String): CrashReportFragment {
      return newInstance(null, null, trace, true)
    }

    @JvmStatic
    fun newInstance(
      title: String?,
      message: String?,
      trace: String,
      closeAppOnClick: Boolean
    ): CrashReportFragment {
      val frag = CrashReportFragment()
      val args = Bundle().apply {
        putString(KEY_TRACE, trace)
        putBoolean(KEY_CLOSE_APP_ON_CLICK, closeAppOnClick)
        title?.let { putString(KEY_TITLE, it) }
        message?.let { putString(KEY_MESSAGE, it) }
      }
      frag.arguments = args
      return frag
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    return LayoutCrashReportBinding.inflate(inflater, container, false).also { binding = it }.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val args = requireArguments()
    closeAppOnClick = args.getBoolean(KEY_CLOSE_APP_ON_CLICK)
    val title: String = args.getString(KEY_TITLE) ?: getString(R.string.msg_ide_crashed)

    // KEY_MESSAGE carries the compact, already-sanitized summary built by CrashReport.
    // KEY_TRACE carries the full, already-sanitized report. Both are held in memory only.
    val summary: String = args.getString(KEY_MESSAGE) ?: getString(R.string.msg_report_crash)
    val report: String = if (args.containsKey(KEY_TRACE)) {
      args.getString(KEY_TRACE)!!
    } else {
      "No stack trace was provided for the report"
    }

    binding!!.apply {
      crashTitle.text = title
      crashSummary.text = summary

      viewLogButton.setOnClickListener { openCrashLog(report) }
      copyButton.setOnClickListener { copyReport(report) }
      closeButton.setOnClickListener {
        requireActivity().finishAffinity()
        // This activity lives in the isolated ':crash' process; end it explicitly.
        android.os.Process.killProcess(android.os.Process.myPid())
      }
    }
  }

  private fun openCrashLog(report: String) {
    val container = (requireView().parent as? ViewGroup) ?: return
    requireActivity().supportFragmentManager
      .beginTransaction()
      .replace(container.id, CrashLogFragment.newInstance(report))
      .addToBackStack(null)
      .commit()
  }

  private fun copyReport(report: String) {
    ClipboardUtils.copyText("HMX IDE CrashLog", report)
    Toast.makeText(requireContext(), R.string.crash_copied, Toast.LENGTH_SHORT).show()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    binding = null
  }
}
