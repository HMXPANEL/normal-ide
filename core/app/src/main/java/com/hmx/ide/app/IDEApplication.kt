/*
 * This file is part of AndroidIDE.
 *
 * AndroidIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndroidIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.hmx.ide.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.StrictMode
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Observer
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hmx.ide.crash.CrashReport
import com.hmx.ide.crash.CrashNotifier
import com.hmx.ide.crash.CrashOverlay
import com.google.android.material.color.DynamicColors
import com.hmx.ide.BuildConfig
import com.hmx.ide.activities.CrashHandlerActivity
import com.hmx.ide.activities.editor.IDELogcatReader
import com.hmx.ide.buildinfo.BuildInfo
import com.hmx.ide.ai.AiFactory
import com.hmx.ide.ai.context.ContextManager
import com.hmx.ide.ai.memory.MemoryService
import com.hmx.ide.knowledge.KnowledgeEngineImpl
import com.hmx.ide.editor.schemes.IDEColorSchemeProvider
import com.hmx.ide.eventbus.events.preferences.PreferenceChangeEvent
import com.hmx.ide.events.AppEventsIndex
import com.hmx.ide.events.EditorEventsIndex
import com.hmx.ide.events.LspApiEventsIndex
import com.hmx.ide.events.LspJavaEventsIndex
import com.hmx.ide.preferences.internal.DevOpsPreferences
import com.hmx.ide.preferences.internal.GeneralPreferences
import com.hmx.ide.preferences.internal.StatPreferences
import com.hmx.ide.resources.localization.LocaleProvider
import com.hmx.ide.stats.AndroidIDEStats
import com.hmx.ide.stats.StatUploadWorker
import com.hmx.ide.syntax.colorschemes.SchemeAndroidIDE
import com.itsaky.androidide.treesitter.TreeSitter
import com.hmx.ide.ui.themes.IDETheme
import com.hmx.ide.ui.themes.IThemeManager
import com.hmx.ide.utils.RecyclableObjectPool
import com.hmx.ide.utils.VMUtils
import com.hmx.ide.utils.flashError
import com.hmx.ide.utils.IdeReflectionUtils
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.slf4j.LoggerFactory
import java.lang.Thread.UncaughtExceptionHandler
import java.time.Duration
import kotlin.system.exitProcess


class IDEApplication : BaseApplication() {

  private var uncaughtExceptionHandler: UncaughtExceptionHandler? = null
  private var ideLogcatReader: IDELogcatReader? = null

  private val applicationScope = MainScope()

  init {
    if (!VMUtils.isJvm() && !isCrashProcess()) {
      TreeSitter.loadLibrary()
    }

    RecyclableObjectPool.DEBUG = BuildConfig.DEBUG
  }

  override fun onCreate() {
    instance = this
    if (isCrashProcess()) {
      // The crash reporter runs in the isolated ':crash' process. Keep it independent from the
      // systems that may have caused the crash: skip all heavy IDE initialization here.
      super.onCreate()
      return
    }
    uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, th -> handleCrash(thread, th) }

    super.onCreate()

    if (BuildConfig.DEBUG) {
      StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy()).penaltyLog().detectAll().build()
      )
      if (DevOpsPreferences.dumpLogs) {
        startLogcatReader()
      }
    }

    EventBus.builder()
      .addIndex(AppEventsIndex())
      .addIndex(EditorEventsIndex())
      .addIndex(LspApiEventsIndex())
      .addIndex(LspJavaEventsIndex())
      .installDefaultEventBus(true)

    EventBus.getDefault().register(this)

    AppCompatDelegate.setDefaultNightMode(GeneralPreferences.uiMode)

    if (IThemeManager.getInstance().getCurrentTheme() == IDETheme.MATERIAL_YOU) {
      DynamicColors.applyToActivitiesIfAvailable(this)
    }

    EditorColorScheme.setDefault(SchemeAndroidIDE.newInstance(null))

    IdeReflectionUtils.bypassHiddenAPIReflectionRestrictions()
    applicationScope.launch {
      IDEColorSchemeProvider.init()
    }

    AiFactory.init(this)
    com.hmx.ide.build.tools.BuildTools.init(this)
    MemoryService.init(this)
    ContextManager.init()
    KnowledgeEngineImpl.start()
    registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
      override fun onTrimMemory(level: Int) {
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
          KnowledgeEngineImpl.destroy()
        }
      }
      override fun onConfigurationChanged(cfg: android.content.res.Configuration) {}
      override fun onLowMemory() {
        KnowledgeEngineImpl.destroy()
      }
    })
  }

  fun showChangelog() {
    val intent = Intent(Intent.ACTION_VIEW)
    var version = BuildInfo.VERSION_NAME_SIMPLE
    if (!version.startsWith('v')) {
      version = "v${version}"
    }
    intent.data = Uri.parse("${BuildInfo.REPO_URL}/releases/tag/${version}")
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
      startActivity(intent)
    } catch (th: Throwable) {
      log.error("Unable to start activity to show changelog", th)
      flashError("Unable to start activity")
    }
  }

  fun reportStatsIfNecessary() {

    if (!StatPreferences.statOptIn) {
      log.info("Stat collection is disabled.")
      return
    }

    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    val request = PeriodicWorkRequestBuilder<StatUploadWorker>(Duration.ofHours(24)).setInputData(
      AndroidIDEStats.statData.toInputData()
    ).setConstraints(constraints)
      .addTag(StatUploadWorker.WORKER_WORK_NAME).build()

    val workManager = WorkManager.getInstance(this)

    log.info("reportStatsIfNecessary: Enqueuing StatUploadWorker...")
    val operation = workManager.enqueueUniquePeriodicWork(
      StatUploadWorker.WORKER_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE, request
    )

    operation.state.observeForever(object : Observer<Operation.State> {
      override fun onChanged(value: Operation.State) {
        operation.state.removeObserver(this)
        log.debug("reportStatsIfNecessary: WorkManager enqueue result: {}", value)
      }
    })
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onPrefChanged(event: PreferenceChangeEvent) {
    val enabled = event.value as? Boolean?
    if (event.key == StatPreferences.STAT_OPT_IN) {
      if (enabled == true) {
        reportStatsIfNecessary()
      } else {
        cancelStatUploadWorker()
      }
    } else if (event.key == DevOpsPreferences.KEY_DEVOPTS_DEBUGGING_DUMPLOGS) {
      if (enabled == true) {
        startLogcatReader()
      } else {
        stopLogcatReader()
      }
    } else if (event.key == GeneralPreferences.UI_MODE && GeneralPreferences.uiMode != AppCompatDelegate.getDefaultNightMode()) {
      AppCompatDelegate.setDefaultNightMode(GeneralPreferences.uiMode)
    } else if (event.key == GeneralPreferences.SELECTED_LOCALE) {

      // Use empty locale list if the locale has been reset to 'System Default'
      val selectedLocale = GeneralPreferences.selectedLocale
      val localeListCompat = selectedLocale?.let {
        LocaleListCompat.create(LocaleProvider.getLocale(selectedLocale))
      } ?: LocaleListCompat.getEmptyLocaleList()

      AppCompatDelegate.setApplicationLocales(localeListCompat)
    }
  }

  private fun handleCrash(thread: Thread, th: Throwable) {
    try {
      val report = CrashReport.build(thread, th)
      val summary = CrashReport.summary(thread, th)

      // SECONDARY: if the user granted the overlay permission, draw the crash card over the
      // home screen. The overlay window is owned by this process, so it persists only while
      // this process survives — which is exactly the background-thread-crash case where a
      // direct Activity launch is most likely blocked. We therefore do NOT terminate the
      // process here; CrashOverlay kills it when the popup is dismissed. For a main-thread
      // crash the process dies anyway and the fallback notification below still covers it.
      val overlayShown = canDrawOverlays() && runCatching {
        CrashOverlay.show(this, summary, report)
      }.getOrDefault(false)

      if (overlayShown) {
        // Safety net: the notification survives process death, so it is posted even though
        // the popup is visible. It is cancelled when the crash UI is opened or dismissed.
        runCatching { CrashNotifier.show(this, summary, report) }
        return
      }

      // PRIMARY: attempt the crash UI directly in the isolated ':crash' process.
      try {
        startActivity(Intent(this, CrashHandlerActivity::class.java).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
          putExtra(CrashHandlerActivity.TRACE_KEY, report)
          putExtra(CrashHandlerActivity.SUMMARY_KEY, summary)
        })
      } catch (error: Throwable) {
        log.error("Unable to show crash handler activity", error)
      }

      // FALLBACK: modern Android may block that launch; post a high-priority notification
      // that opens the report. It self-cancels when the activity actually appears.
      try {
        CrashNotifier.show(this, summary, report)
      } catch (_: Throwable) {
        // Best-effort only; the process still terminates below.
      }

      // The report is shown by CrashHandlerActivity, which runs in the isolated ':crash'
      // process. Terminate this (broken) process so the crash UI survives and the app does
      // not keep running in a broken state.
      try {
        uncaughtExceptionHandler?.uncaughtException(thread, th)
      } finally {
        exitProcess(1)
      }
    } catch (error: Throwable) {
      log.error("Unable to show crash handler", error)
      th.printStackTrace()
      try {
        uncaughtExceptionHandler?.uncaughtException(thread, th)
      } finally {
        exitProcess(1)
      }
    }
  }

  private fun canDrawOverlays(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)
  }

  /**
   * Whether the current process is the isolated crash reporter process (declared with
   * `android:process=":crash"` in the manifest). Read from `/proc/self/cmdline` so it works on
   * every Android version without relying on APIs introduced in newer releases.
   */
  private fun isCrashProcess(): Boolean {
    val name = currentProcessName()
    return name != null && name.endsWith(":crash")
  }

  private fun currentProcessName(): String? = try {
    java.io.BufferedReader(java.io.FileReader("/proc/self/cmdline")).use { reader ->
      reader.readText().replace('\u0000', ' ').trim().takeIf { it.isNotEmpty() }
    }
  } catch (_: Throwable) {
    null
  }

  private fun cancelStatUploadWorker() {
    log.info("Opted-out of stat collection. Cancelling StatUploadWorker if enqueued...")
    val operation = WorkManager.getInstance(this)
      .cancelUniqueWork(StatUploadWorker.WORKER_WORK_NAME)
    operation.state.observeForever(object : Observer<Operation.State> {
      override fun onChanged(value: Operation.State) {
        operation.state.removeObserver(this)
        log.info("StatUploadWorker: Cancellation result state: {}", value)
      }
    })
  }

  private fun startLogcatReader() {
    if (ideLogcatReader != null) {
      // already started
      return
    }

    log.info("Starting logcat reader...")
    ideLogcatReader = IDELogcatReader().also { it.start() }
  }

  private fun stopLogcatReader() {
    log.info("Stopping logcat reader...")
    ideLogcatReader?.stop()
    ideLogcatReader = null
  }

  companion object {

    private val log = LoggerFactory.getLogger(IDEApplication::class.java)

    @JvmStatic
    lateinit var instance: IDEApplication
      private set
  }
}
