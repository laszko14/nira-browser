package com.prirai.android.nira.browser

import kotlin.math.abs

/**
 * Browser chrome may request an existing ~120 Hz display mode. It does not
 * force webpage animation, Gecko content, or video playback to run at 120 FPS.
 */
internal object HighRefreshRatePolicy {
    fun choose(rates: List<Float>): Float? =
        rates.filter { it.isFinite() && it in 89f..125f }
            .minByOrNull { abs(it - 120f) }
}
