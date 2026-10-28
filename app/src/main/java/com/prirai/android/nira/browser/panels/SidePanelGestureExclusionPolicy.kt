package com.prirai.android.nira.browser.panels

import android.graphics.Rect
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Small opt-out zones for an edge-opening swipe on gesture-navigation devices.
 *
 * Does not suppress Back globally. The remaining edge, including most of the
 * screen height, continues to belong to Android. The system may shrink or ignore
 * these rects (mandatory gesture insets and OEM policy take precedence).
 */
internal object SidePanelGestureExclusionPolicy {
    const val MAX_ZONE_HEIGHT_DP = 160f // below Android's 200dp per-side budget
    const val MAX_ZONE_WIDTH_DP = 24f

    fun regions(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        requestedEdgeDp: Int,
        systemGestureLeftPx: Int,
        systemGestureRightPx: Int,
        statusBarTopPx: Int,
        enabled: Boolean,
        imeVisible: Boolean
    ): List<Rect> {
        if (!enabled || imeVisible || widthPx <= 0 || heightPx <= 0 ||
            !density.isFinite() || density <= 0f || requestedEdgeDp <= 0) return emptyList()
        if (systemGestureLeftPx <= 0 && systemGestureRightPx <= 0) return emptyList()

        // Keep it in the upper third, away from Android's Home/Recents gesture.
        // This is a narrow priority affordance, not exclusion of an entire edge.
        val bandEnd = heightPx / 3
        val insetMargin = (8f * density).roundToInt()
        val top = maxOf(statusBarTopPx.coerceAtLeast(0) + insetMargin, heightPx / 10)
        val available = bandEnd - top
        if (available < (24f * density).roundToInt()) return emptyList()
        val bandHeight = min((MAX_ZONE_HEIGHT_DP * density).roundToInt(), available)
        val bottom = top + bandHeight
        val edgeWidth = min(
            min((MAX_ZONE_WIDTH_DP * density).roundToInt(), (requestedEdgeDp * density).roundToInt()),
            widthPx / 2
        ).coerceAtLeast(0)
        if (edgeWidth <= 0) return emptyList()

        val rects = ArrayList<Rect>(2)
        if (systemGestureLeftPx > 0) rects.add(Rect(0, top, edgeWidth, bottom))
        if (systemGestureRightPx > 0) rects.add(Rect(widthPx - edgeWidth, top, widthPx, bottom))
        return rects
    }
}
