package com.prirai.android.nira.components.toolbar

/** Layout invalidation must track the Configuration orientation, not only width. */
object ToolbarActionLayoutRefreshPolicy {
    fun needsRefresh(
        width: Int,
        previousLayoutWidth: Int,
        lastWidth: Int,
        orientation: Int,
        lastOrientation: Int
    ): Boolean = width != lastWidth ||
        width != previousLayoutWidth ||
        orientation != lastOrientation
}
