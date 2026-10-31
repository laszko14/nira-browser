package com.prirai.android.nira.browser.toolbar

import kotlin.math.abs

/** Physical X velocity is unchanged from GestureDetector for both toolbar hosts. */
internal object ToolbarSwipeFinishPolicy {
    private const val FINISH_FRACTION = 0.25

    fun completes(
        swipingLeft: Boolean,
        velocityX: Float,
        minimumFlingVelocity: Int,
        visibleFraction: Double
    ): Boolean {
        val strongFling = abs(velocityX) >= minimumFlingVelocity
        val reverseFling = strongFling &&
            (if (swipingLeft) velocityX > 0f else velocityX < 0f)
        return !reverseFling && (visibleFraction >= FINISH_FRACTION || strongFling)
    }
}
