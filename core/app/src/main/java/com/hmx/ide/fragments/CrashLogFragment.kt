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
import com.hmx.ide.databinding.LayoutCrashLogBinding
import com.hmx.ide.resources.R

/**
 * Dedicated crash-details screen showing the complete, sanitized crash report in a
 * selectable monospace view, with a Copy Error action.
 */
class CrashLogFragment : Fragment() {

  private var binding: LayoutCrashLogBinding? = null

  companion object {

    const val KEY_TRACE = "crash_trace"

    @JvmStatic
    fun newInstance(trace: String): CrashLogFragment {
      return CrashLogFragment().apply {
        arguments = Bundle().apply { putString(KEY_TRACE, trace) }
      }
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    return LayoutCrashLogBinding.inflate(inflater, container, false).also { binding = it }.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val report: String = arguments?.getString(KEY_TRACE)
      ?: "No stack trace was provided for the report"

    binding!!.apply {
      crashLogText.text = report
      crashLogCopy.setOnClickListener {
        ClipboardUtils.copyText("HMX IDE CrashLog", report)
        Toast.makeText(requireContext(), R.string.crash_copied, Toast.LENGTH_SHORT).show()
      }
      crashLogClose.setOnClickListener {
        requireActivity().finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    binding = null
  }
}
