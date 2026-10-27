package com.prirai.android.nira.display

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.lifecycle.findViewTreeLifecycleOwner
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import java.util.WeakHashMap

/**
 * App UI preference, not a claim about display FPS or Gecko/MPV frames.
 * Never selects a display mode ID and never accesses a renderer's Surface.
 */
object BrowserRefreshRate {
    private var installed: Runtime? = null

    /** Call once from the main-process Application.onCreate, before any Activity exists. */
    fun install(application: Application) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (installed != null) return
        installed = Runtime(application).also { it.start() }
    }

    /** Register a standalone Dialog Window after show; detach when dismissed on every API. */
    fun attach(window: Window) {
        check(Looper.myLooper() == Looper.getMainLooper())
        installed?.attach(window) ?: apply(window)
    }

    fun detach(window: Window) {
        check(Looper.myLooper() == Looper.getMainLooper())
        installed?.detach(window)
    }

    /** True only for an actual write; this remains a UI preference, not measured FPS. */
    fun apply(window: Window): Boolean {
        val display = window.decorView.display ?: @Suppress("DEPRECATION") window.windowManager.defaultDisplay
        val rate = preferredRate(display, window.context)
        val attributes = window.attributes
        if (attributes.preferredRefreshRate == rate) return false
        attributes.preferredRefreshRate = rate
        window.attributes = attributes
        return true
    }

    internal fun applyRoot(root: View): Boolean {
        val attributes = root.layoutParams as? WindowManager.LayoutParams ?: return false
        val manager = root.context.getSystemService(WindowManager::class.java) ?: return false
        val display = root.display ?: @Suppress("DEPRECATION") manager.defaultDisplay
        val rate = preferredRate(display, root.context)
        if (attributes.preferredRefreshRate == rate) return false
        // The original params may also be Window.attributes. Preserve that identity:
        // a copied replacement leaves the owner stale and its next flag change reverts Hz.
        attributes.preferredRefreshRate = rate
        try {
            manager.updateViewLayout(root, attributes)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return true
    }

    /** ComponentDialog exposes its real Window through the public lifecycle-owner tree. */
    internal fun dialogWindow(root: View): Window? =
        (root.findViewTreeLifecycleOwner() as? Dialog)?.window?.takeIf { it.decorView === root }

    private fun preferredRate(display: Display?, context: Context): Float {
        if (display == null || !display.isValid) return 0f
        val current = display.mode
        val saving = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        val modes = display.supportedModes.map { RefreshRateMode(it.physicalWidth, it.physicalHeight, it.refreshRate) }
        return RefreshRateSelection.preferredRate(modes, current.physicalWidth, current.physicalHeight, saving)
    }

    private class Runtime(private val application: Application) : Application.ActivityLifecycleCallbacks,
        DisplayManager.DisplayListener, ComponentCallbacks {
        private val main = Handler(Looper.getMainLooper())
        private val displays = application.getSystemService(DisplayManager::class.java)
        private val activities = IdentityHashMap<Activity, Unit>()
        private val roots = WeakHashMap<View, RootProbe>()
        private val standaloneWindows = RefreshRateWindowRegistry()
        private var scanPending = false
        private val powerChanges = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = scheduleRefresh()
        }

        fun start() {
            application.registerActivityLifecycleCallbacks(this)
            application.registerComponentCallbacks(this)
            displays?.registerDisplayListener(this, main)
            val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            if (Build.VERSION.SDK_INT >= 33) {
                application.registerReceiver(powerChanges, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                application.registerReceiver(powerChanges, filter)
            }
        }

        fun attach(window: Window) {
            standaloneWindows.attach(window)
            observe(window.decorView)
            scheduleRefresh()
        }

        fun detach(window: Window) {
            standaloneWindows.detach(window)
            forget(window.decorView)
        }

        private fun scheduleRefresh() {
            if (scanPending) return
            scanPending = true
            main.post {
                scanPending = false
                activities.keys.toList().forEach { activity ->
                    if (!activity.isDestroyed) {
                        apply(activity.window)
                        observe(activity.window.decorView)
                    }
                }
                standaloneWindows.refresh()
                if (Build.VERSION.SDK_INT >= 29) refreshOwnedRoots()
            }
        }

        /** Official API: returns only this process's top-level roots, including dialogs/popups. */
        @RequiresApi(29)
        private fun refreshOwnedRoots() {
            android.view.inspector.WindowInspector.getGlobalWindowViews().forEach { root ->
                if (!root.isAttachedToWindow || standaloneWindows.containsRoot(root) ||
                    activities.keys.any { it.window.decorView === root }) return@forEach
                observe(root)
                val dialog = dialogWindow(root)
                if (dialog != null) standaloneWindows.attach(dialog) else applyRoot(root)
            }
        }

        private data class RootProbe(
            val focus: ViewTreeObserver.OnWindowFocusChangeListener,
            val attach: View.OnAttachStateChangeListener,
        )

        private fun observe(root: View) {
            if (roots.containsKey(root)) return
            val reference = WeakReference(root)
            val focus = ViewTreeObserver.OnWindowFocusChangeListener { scheduleRefresh() }
            val attach = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = scheduleRefresh()
                override fun onViewDetachedFromWindow(view: View) {
                    reference.get()?.let(::forget)
                }
            }
            roots[root] = RootProbe(focus, attach)
            root.viewTreeObserver.addOnWindowFocusChangeListener(focus)
            root.addOnAttachStateChangeListener(attach)
        }

        private fun forget(root: View) {
            standaloneWindows.detachRoot(root)
            val probe = roots.remove(root) ?: return
            if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnWindowFocusChangeListener(probe.focus)
            root.removeOnAttachStateChangeListener(probe.attach)
        }

        override fun onActivityCreated(activity: Activity, state: Bundle?) {
            activities[activity] = Unit
            apply(activity.window)
            observe(activity.window.decorView)
        }
        override fun onActivityStarted(activity: Activity) = scheduleRefresh()
        override fun onActivityResumed(activity: Activity) {
            apply(activity.window)
            observe(activity.window.decorView)
            scheduleRefresh()
        }
        override fun onActivityDestroyed(activity: Activity) {
            activities.remove(activity)
            forget(activity.window.decorView)
        }
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        override fun onDisplayAdded(displayId: Int) = scheduleRefresh()
        override fun onDisplayChanged(displayId: Int) = scheduleRefresh()
        override fun onDisplayRemoved(displayId: Int) = scheduleRefresh()
        override fun onConfigurationChanged(configuration: Configuration) = scheduleRefresh()
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onLowMemory() = Unit
    }
}
