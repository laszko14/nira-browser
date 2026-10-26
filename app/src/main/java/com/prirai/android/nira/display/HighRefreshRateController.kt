package com.prirai.android.nira.display

import android.app.Activity
import android.app.Application
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.WindowManager
import com.prirai.android.nira.BuildConfig
import java.lang.ref.WeakReference
import kotlin.math.abs

/**
 * A window-level 120 Hz preference, NOT a 120 FPS claim.
 *
 * The OS remains responsible for choosing refresh rate (power saver, thermal
 * limits, other visible surfaces). Do not set Surface.setFrameRate on GeckoView
 * or libmpv: their compositor/video cadence is independent of the browser UI.
 */
internal class HighRefreshRateController(
    application: Application,
) : Application.ActivityLifecycleCallbacks, DisplayManager.DisplayListener {

    private val displayManager = application.getSystemService(DisplayManager::class.java)
    private var resumedActivity: WeakReference<Activity>? = null

    init {
        // One registration per process, covering browser, settings, PWA and MPV
        // windows without per-frame work or a new per-Activity observer.
        application.registerActivityLifecycleCallbacks(this)
        displayManager?.registerDisplayListener(this, Handler(Looper.getMainLooper()))
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        applyPreference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivity = WeakReference(activity)
        applyPreference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumedActivity?.get() === activity) resumedActivity = null
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (resumedActivity?.get() === activity) resumedActivity = null
    }

    // The current display/modes can change after rotation, docking or an OS
    // policy change. This callback does not run once per frame.
    override fun onDisplayChanged(displayId: Int) {
        val activity = resumedActivity?.get() ?: return
        val display = activityDisplay(activity) ?: return
        if (display.displayId == displayId) applyPreference(activity)
    }

    override fun onDisplayAdded(displayId: Int) = Unit
    override fun onDisplayRemoved(displayId: Int) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    fun reapplyActiveWindow() {
        resumedActivity?.get()?.let(::applyPreference)
    }

    private fun applyPreference(activity: Activity) {
        val display = activityDisplay(activity) ?: return
        val mode = display.mode
        // Do not accidentally change resolution to obtain a different Hz.
        val supportedRates = display.supportedModes
            .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            .map { it.refreshRate }
        val target = choose120Hz(supportedRates) ?: return

        try {
            val params = activity.window.attributes
            // A mode ID has priority over preferredRefreshRate, so clear a
            // previous mode selection only when setting this window's 120 Hz.
            if (params.preferredDisplayModeId != 0 || abs(params.preferredRefreshRate - target) > 0.05f) {
                params.preferredDisplayModeId = 0
                params.preferredRefreshRate = target
                activity.window.attributes = params
                if (BuildConfig.DEBUG) {
                    Log.d("Zavro120Hz", "Window requests ${target}Hz; display reports ${display.refreshRate}Hz")
                }
            }
        } catch (e: RuntimeException) {
            // Window hints are optional; unsupported OEM/window policies must
            // never prevent navigation, playback or starting an Activity.
            Log.w("Zavro120Hz", "Window refresh preference unavailable", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun activityDisplay(activity: Activity): Display? =
        activity.window.decorView.display ?: activity.windowManager.defaultDisplay

    companion object {
        private const val TARGET_HZ = 120f
        private const val TOLERANCE_HZ = 1f

        /** Returns null when the current-resolution modes do not provide 120 Hz. */
        internal fun choose120Hz(supportedRates: List<Float>): Float? =
            supportedRates
                .filter { it.isFinite() && abs(it - TARGET_HZ) <= TOLERANCE_HZ }
                .minByOrNull { abs(it - TARGET_HZ) }
    }
}
