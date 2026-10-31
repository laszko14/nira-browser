package com.prirai.android.nira.browser

import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import mozilla.components.concept.engine.EngineView

/**
 * Sole writer of SwipeRefreshLayout viewport margins for pinned chrome + IME.
 * The Activity already reserves system navigation bars at the root level.
 */
class WebContentPositionManager(
    private val engineView: EngineView,
    private val swipeRefreshView: View,
    private val rootView: View,
    private val getTopToolbarHeight: () -> Int,
    private val getBottomToolbarHeight: () -> Int
) {
    private val reservation = WebViewportReservation()
    private var isDestroyed = false

    fun initialize() {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            handleWindowInsets(insets)
            insets
        }
        ViewCompat.setWindowInsetsAnimationCallback(
            swipeRefreshView,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: List<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    handleWindowInsets(insets)
                    return insets
                }
            }
        )
        rootView.post {
            if (!isDestroyed) ViewCompat.requestApplyInsets(rootView)
        }
    }

    /** Publish actual pinned toolbar/footer dimensions, never overwrite IME state. */
    fun setChromeReservation(top: Int, bottom: Int) {
        if (isDestroyed) return
        reservation.setChrome(top, bottom)
        applyMargins()
    }

    /** Fullscreen hides both chrome and IME reservations until fullscreen exits. */
    fun setFullscreen(fullscreen: Boolean) {
        if (isDestroyed) return
        reservation.setFullscreen(fullscreen)
        applyMargins()
    }

    private fun updateEnginePadding() {
        val view = engineView as? View ?: return
        // Root already reserves system bars; margins reserve pinned chrome.
        view.setPadding(0, 0, 0, 0)
    }

    private fun handleWindowInsets(insets: WindowInsetsCompat) {
        if (isDestroyed) return
        val navBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        // IME and the navigation inset overlap, rather than add together.
        reservation.setIme((imeBottom - navBottom).coerceAtLeast(0))
        updateEnginePadding()
        applyMargins()
    }

    private fun applyMargins() {
        if (isDestroyed) return
        val lp = swipeRefreshView.layoutParams as? CoordinatorLayout.LayoutParams ?: return
        val wanted = reservation.margins()
        if (lp.topMargin == wanted.top && lp.bottomMargin == wanted.bottom) return
        lp.topMargin = wanted.top
        lp.bottomMargin = wanted.bottom
        swipeRefreshView.layoutParams = lp
    }

    fun requestUpdate() {
        if (isDestroyed) return
        updateEnginePadding()
        applyMargins()
    }

    fun destroy() {
        isDestroyed = true
        ViewCompat.setOnApplyWindowInsetsListener(rootView, null)
        ViewCompat.setWindowInsetsAnimationCallback(swipeRefreshView, null)
    }
}
