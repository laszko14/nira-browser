package com.prirai.android.nira.browser.tabs.compose

/** Coordinates are relative to the list viewport, including its scrolled row offsets. */
internal data class DropRow(val key: String, val offset: Int, val size: Int)
internal data class ResolvedDrop(val key: String, val after: Boolean)
internal object DragPlacement {
    fun sideTarget(y: Float, viewportStart: Int, viewportEnd: Int, rows: List<DropRow>, x: Float = 0f, viewportWidth: Float = Float.MAX_VALUE): ResolvedDrop? {
        if (!x.isFinite() || x < 0f || x >= viewportWidth || !y.isFinite() || y < viewportStart || y >= viewportEnd) return null
        val row = rows.firstOrNull { y >= it.offset && y < it.offset + it.size }
            ?: rows.minByOrNull { kotlin.math.abs(y - (it.offset + it.size / 2f)) }
            ?: return null
        return ResolvedDrop(row.key, y >= row.offset + row.size / 2f)
    }
    /** Boundary is measured before removing the dragged element. */
    fun destination(from: Int, boundary: Int, size: Int): Int = (boundary - if (from < boundary) 1 else 0).coerceIn(0, size - 1)
}
