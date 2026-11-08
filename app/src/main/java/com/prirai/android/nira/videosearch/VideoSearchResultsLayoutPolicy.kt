package com.prirai.android.nira.videosearch

/** P4-E1: responsive presentation sizing only, without changing stored results. */
internal object VideoSearchResultsLayoutPolicy {
    // GridCells.Adaptive reflows against the actual view, including split-screen.
    fun tileMinWidthDp(widthDp: Int, landscape: Boolean): Int = when {
        widthDp in 1..359 -> 140
        landscape && widthDp >= 760 -> 190
        landscape -> 168
        widthDp >= 600 -> 180
        else -> 152
    }

    // Keep filter actions scrollable without consuming the entire film viewport.
    fun filterPanelMaxHeightDp(heightDp: Int, landscape: Boolean): Int {
        val height = heightDp.coerceAtLeast(240)
        val ratio = if (landscape) 0.43 else 0.47
        return (height * ratio).toInt()
            .coerceAtMost(if (landscape) 260 else 380)
            .coerceAtMost(height - 130)
            .coerceAtLeast(96)
    }
}
