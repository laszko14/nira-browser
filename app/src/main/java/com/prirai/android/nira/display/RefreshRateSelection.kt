package com.prirai.android.nira.display

import kotlin.math.abs

/** Public display capabilities only; no renderer or video-frame-rate state. */
internal data class RefreshRateMode(val width: Int, val height: Int, val rate: Float)

internal object RefreshRateSelection {
    private const val TARGET_HZ = 120f
    private const val TOLERANCE_HZ = 0.5f

    /** Zero means Android chooses. Return the supported float itself, including on API < 34. */
    fun preferredRate(modes: List<RefreshRateMode>, width: Int, height: Int,
                      powerSave: Boolean = false): Float {
        if (powerSave || width <= 0 || height <= 0) return 0f
        return modes.asSequence()
            .filter { it.width == width && it.height == height && it.rate.isFinite() &&
                abs(it.rate - TARGET_HZ) <= TOLERANCE_HZ }
            .minWithOrNull(compareBy<RefreshRateMode> { abs(it.rate - TARGET_HZ) }.thenByDescending { it.rate })
            ?.rate ?: 0f
    }
}
