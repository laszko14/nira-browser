package com.prirai.android.nira.browser

/**
 * Single viewport margin policy. The host activity already reserves navigation bars;
 * IME insets here exclude that system-bar reservation. Browser chrome and IME occupy
 * the same lower edge, so using their maximum avoids both erasure and double-counting.
 */
internal class WebViewportReservation {
    private var topChrome = 0
    private var bottomChrome = 0
    private var ime = 0
    private var fullscreen = false

    fun setChrome(top: Int, bottom: Int) {
        topChrome = top.coerceAtLeast(0)
        bottomChrome = bottom.coerceAtLeast(0)
    }

    fun setIme(bottom: Int) {
        ime = bottom.coerceAtLeast(0)
    }

    fun setFullscreen(enabled: Boolean) {
        fullscreen = enabled
    }

    fun margins(): Margins = if (fullscreen) Margins(0, 0)
        else Margins(topChrome, maxOf(bottomChrome, ime))

    data class Margins(val top: Int, val bottom: Int)
}
