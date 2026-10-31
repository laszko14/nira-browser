package com.prirai.android.nira

/** Invalidates queued UI restoration after any fullscreen or view transition. */
internal class FullscreenChromeRestoreGate {
    private var generation = 0L
    private var viewActive = false
    var isFullscreen = false
        private set

    fun newView() {
        generation++
        viewActive = true
        isFullscreen = false
    }

    fun modeChanged(fullscreen: Boolean): Long {
        generation++
        isFullscreen = fullscreen
        return generation
    }

    fun canRestore(ticket: Long): Boolean =
        viewActive && !isFullscreen && ticket == generation

    fun invalidateView() {
        generation++
        viewActive = false
        isFullscreen = false
    }
}
