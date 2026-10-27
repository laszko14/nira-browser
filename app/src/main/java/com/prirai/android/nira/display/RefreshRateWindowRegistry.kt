package com.prirai.android.nira.display

import android.view.View
import android.view.Window
import java.util.WeakHashMap

/** Owns no Dialog/Activity; closed windows are detached and abandoned owners can be collected. */
internal class RefreshRateWindowRegistry {
    private val windows = WeakHashMap<Window, Unit>()

    fun attach(window: Window) {
        windows[window] = Unit
        BrowserRefreshRate.apply(window)
    }

    fun detach(window: Window) {
        windows.remove(window)
    }

    fun detachRoot(root: View) {
        windows.keys.removeAll { it.decorView === root }
    }

    fun refresh() {
        windows.keys.toList().forEach { BrowserRefreshRate.apply(it) }
    }

    fun containsRoot(root: View): Boolean = windows.keys.any { it.decorView === root }
}
