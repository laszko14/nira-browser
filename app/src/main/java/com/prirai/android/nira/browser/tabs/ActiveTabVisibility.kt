package com.prirai.android.nira.browser.tabs

import kotlin.math.abs

/** Unclipped horizontal bounds in the same coordinate space as the viewport. */
data class TabStripBounds(val left: Float, val right: Float)

/** Shared policy; adapters measure actual views and clamp using their scroll container. */
object ActiveTabVisibility {
    fun scrollDelta(
        tabCount: Int,
        viewportLeft: Float,
        viewportRight: Float,
        target: TabStripBounds,
        expandedGroup: TabStripBounds?,
        canScrollBackward: Boolean,
        canScrollForward: Boolean,
        edgeMargin: Float
    ): Float {
        if (tabCount <= 1 || viewportRight <= viewportLeft ||
            (!canScrollBackward && !canScrollForward)) return 0f
        val tolerance = 1f
        if (expandedGroup != null && expandedGroup.left >= viewportLeft - tolerance &&
            expandedGroup.right <= viewportRight + tolerance) return 0f
        val margin = edgeMargin.coerceIn(0f, (viewportRight - viewportLeft) / 8f)
        val safeLeft = viewportLeft + if (canScrollBackward) margin else 0f
        val safeRight = viewportRight - if (canScrollForward) margin else 0f
        if (target.left >= safeLeft - tolerance && target.right <= safeRight + tolerance) return 0f
        val delta = (target.left + target.right - viewportLeft - viewportRight) / 2f
        return if (abs(delta) <= tolerance) 0f else delta
    }
}
