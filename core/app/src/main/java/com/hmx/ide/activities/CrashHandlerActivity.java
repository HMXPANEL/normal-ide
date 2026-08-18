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

package com.hmx.ide.activities;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import com.hmx.ide.app.IDEActivity;
import com.hmx.ide.crash.CrashNotifier;
import com.hmx.ide.databinding.ActivityCrashHandlerBinding;
import com.hmx.ide.fragments.CrashReportFragment;

public class CrashHandlerActivity extends IDEActivity {

  public static final String REPORT_ACTION = "com.hmx.ide.REPORT_CRASH";
  public static final String TRACE_KEY = "crash_trace";
  public static final String SUMMARY_KEY = "crash_summary";
  private ActivityCrashHandlerBinding binding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    final var extra = getIntent().getExtras();
    if (extra == null) {
      finishAffinity();
      return;
    }

    final var report = extra.getString(TRACE_KEY, "Unable to get logs.");
    final var summary = extra.getString(SUMMARY_KEY);
    final var fragment = CrashReportFragment.newInstance(null, summary, report, true);

    getSupportFragmentManager()
        .beginTransaction()
        .replace(binding.getRoot().getId(), fragment, "crash_report_fragment")
        .addToBackStack(null)
        .commit();
  }

  @Override
  protected void onResume() {
    super.onResume();
    // The crash UI is actually visible now; the fallback notification is no longer needed.
    CrashNotifier.cancel(this);
  }

  @Override
  @NonNull
  protected View bindLayout() {
    binding = ActivityCrashHandlerBinding.inflate(getLayoutInflater());
    return binding.getRoot();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    binding = null;
  }
}
