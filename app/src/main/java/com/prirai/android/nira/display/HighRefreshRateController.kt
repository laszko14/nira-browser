package com.prirai.android.nira.display

import android.app.Activity
import android.app.Application
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Window
import java.lang.ref.WeakReference
import kotlin.math.abs

/**
 * Requests the native 120 Hz mode for Zavro-owned Activity and dialog windows.
 * This is a hint to Android, NOT a guarantee of 120 FPS for Gecko or libmpv.
 * Never touches SurfaceView, Gecko compositor or source-video FPS.
 */
internal class HighRefreshRateController(
    application: Application,
) : Application.ActivityLifecycleCallbacks, DisplayManager.DisplayListener {
    private val displayManager = application.getSystemService(DisplayManager::class.java)
    private val dialogs = mutableListOf<WeakReference<Window>>()
    private var active: WeakReference<Activity>? = null

    init {
        application.registerActivityLifecycleCallbacks(this)
        displayManager?.registerDisplayListener(this, Handler(Looper.getMainLooper()))
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        applyWindow(activity.window, activity.window.decorView.display ?: activity.windowManager.defaultDisplay)
    }

    override fun onActivityResumed(activity: Activity) {
        active = WeakReference(activity)
        reapply()
    }

    override fun onActivityPaused(activity: Activity) {
        if (active?.get() === activity) active = null
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (active?.get() === activity) active = null
    }

    /** BottomSheetDialog windows do not receive Activity lifecycle callbacks. */
    fun attachDialog(window: Window) {
        dialogs.removeAll { it.get() == null || it.get() === window }
        dialogs.add(WeakReference(window))
        val display = active?.get()?.window?.decorView?.display ?: window.decorView.display
        applyWindow(window, display)
    }

    fun detachDialog(window: Window) {
        dialogs.removeAll { it.get() == null || it.get() === window }
    }

    fun reapply() {
        active?.get()?.let {
            @Suppress("DEPRECATION")
            val display = it.window.decorView.display ?: it.windowManager.defaultDisplay
            applyWindow(it.window, display)
            dialogs.removeAll { ref ->
                val window = ref.get()
                if (window == null) true else {
                    applyWindow(window, window.decorView.display ?: display)
                    false
                }
            }
        }
    }

    override fun onDisplayChanged(displayId: Int) {
        val activity = active?.get() ?: return
        @Suppress("DEPRECATION")
        val display = activity.window.decorView.display ?: activity.windowManager.defaultDisplay
        if (display.displayId == displayId) reapply()
    }
    override fun onDisplayAdded(displayId: Int) = Unit
    override fun onDisplayRemoved(displayId: Int) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private fun applyWindow(window: Window, display: Display?) {
        if (display == null) return
        val mode = display.mode
        val rates = display.supportedModes
            .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            .map { it.refreshRate }
        val requested = choose120Hz(rates)
        val attributes = window.attributes
        // When a display no longer supports 120 Hz, never retain a stale hint.
        val target = requested ?: 0f
        if (abs(attributes.preferredRefreshRate - target) < 0.05f &&
            (requested == null || attributes.preferredDisplayModeId == 0)) return
        try {
            if (requested != null) attributes.preferredDisplayModeId = 0
            attributes.preferredRefreshRate = target
            window.attributes = attributes
        } catch (_: RuntimeException) {
            // OEM display/window policy must never crash browser or MPV.
        }
    }

    companion object {
        fun choose120Hz(rates: List<Float>): Float? =
            rates.filter { it.isFinite() && abs(it - 120f) <= 1f }
                .minByOrNull { abs(it - 120f) }
    }
}
