package com.prirai.android.nira.components.toolbar

import android.graphics.Rect
import android.view.View

/** Reservations in the overlay parent's coordinates, clipped to its visible viewport. */
internal object BrowserChromeBounds {
    fun reservations(parent: View, viewport: Rect, topViews: List<View>, bottomViews: List<View>): Pair<Int, Int> {
        val origin = IntArray(2)
        parent.getLocationOnScreen(origin)
        fun visibleBounds(view: View): Rect? {
            if (!view.isShown || view.alpha <= 0f) return null
            val bounds = Rect()
            if (!view.getLocalVisibleRect(bounds)) return null
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            bounds.offset(location[0] - origin[0], location[1] - origin[1])
            return bounds.takeIf { it.intersect(viewport) }
        }
        val top = topViews.mapNotNull(::visibleBounds).maxOfOrNull { it.bottom } ?: 0
        val bottomEdge = bottomViews.mapNotNull(::visibleBounds).minOfOrNull { it.top } ?: viewport.bottom
        return top to (viewport.bottom - bottomEdge).coerceAtLeast(0)
    }
}
