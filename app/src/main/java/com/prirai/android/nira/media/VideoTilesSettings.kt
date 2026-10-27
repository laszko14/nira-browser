package com.prirai.android.nira.media

/** Appearance only; no media, document or profile identifiers are persisted. */
internal data class VideoTilesSettings(
    val rows: Int = 4,
    val widthDp: Int = 120,
    val gapDp: Int = 8,
    val showThumbnail: Boolean = true,
    val showTitle: Boolean = true,
    val showFormat: Boolean = true,
    val showQuality: Boolean = true,
    val showSize: Boolean = true,
    val showActions: Boolean = true,
) {
    fun normalized() = copy(rows = rows.coerceIn(1, 10), widthDp = widthDp.coerceIn(100, 280), gapDp = gapDp.coerceIn(2, 16))

    companion object {
        fun bounded(rows: Int, widthDp: Int, gapDp: Int = 8) = VideoTilesSettings(rows, widthDp, gapDp).normalized()
    }
}

/** Finite dimensions shared by the actual horizontal grid and regression tests. */
internal class VideoTilesGeometry(settings: VideoTilesSettings, fontScale: Float, landscape: Boolean = false) {
    private val bounded = settings.normalized()
    val rows = bounded.rows
    private val textScale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val thumbnailHeightDp = if (bounded.showThumbnail) bounded.widthDp * (if (landscape) 10f / 16f else 3f / 4f) else 48f
    val titleHeightDp = if (bounded.showTitle) 36f * textScale else 0f
    val badgeHeightDp = if (bounded.showFormat || bounded.showQuality || bounded.showSize) 24f * textScale else 0f
    val rowHeightDp = thumbnailHeightDp + titleHeightDp + badgeHeightDp + 8f
    val contentHeightDp = rows * rowHeightDp + (rows - 1) * bounded.gapDp
    fun rowTopDp(row: Int): Float {
        require(row in 0 until rows)
        return row * (rowHeightDp + bounded.gapDp)
    }
}
