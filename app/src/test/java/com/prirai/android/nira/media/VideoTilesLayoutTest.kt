package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

class VideoTilesLayoutTest {
    @Test fun settingsClampWithoutLosingTenRows() {
        assertEquals(VideoTilesSettings(10, 280, 16), VideoTilesSettings.bounded(20, 900, 99))
        assertEquals(VideoTilesSettings(1, 100, 2), VideoTilesSettings.bounded(0, 20, 0))
        for (rows in 1..10) assertEquals(rows, VideoTilesSettings.bounded(rows, 160).rows)
    }

    @Test fun tenRowsKeepUsableHeightInBothOrientations() {
        for ((landscape, viewportHeight) in listOf(false to 680f, true to 320f)) {
            val geometry = VideoTilesGeometry(VideoTilesSettings(10, 160), 1f, landscape)
            assertTrue(geometry.contentHeightDp > viewportHeight)
            assertTrue(geometry.thumbnailHeightDp >= 48f)
            assertEquals(10, geometry.rows)
            val scroll = geometry.contentHeightDp - viewportHeight
            assertTrue(geometry.rowTopDp(9) >= scroll)
            assertTrue(geometry.rowTopDp(9) + geometry.rowHeightDp <= scroll + viewportHeight + 0.1f)
        }
    }

    @Test fun largeTextGetsMoreSpaceInsteadOfClippedMetadata() {
        val normal = VideoTilesGeometry(VideoTilesSettings(4, 120), 1f)
        val large = VideoTilesGeometry(VideoTilesSettings(4, 120), 2f)
        assertTrue(large.rowHeightDp > normal.rowHeightDp)
        assertEquals(4 * large.rowHeightDp + 3 * 8f, large.contentHeightDp, 0.1f)
    }

    @Test fun selectedGapChangesBothRowSpacingAndFiniteContentHeight() {
        val tight = VideoTilesGeometry(VideoTilesSettings(10, 100, 2), 1f)
        val wide = VideoTilesGeometry(VideoTilesSettings(10, 100, 16), 1f)
        assertEquals(9 * 14f, wide.contentHeightDp - tight.contentHeightDp, 0.1f)
        assertEquals(tight.rowHeightDp + 2f, tight.rowTopDp(1), 0.1f)
        assertEquals(wide.rowHeightDp + 16f, wide.rowTopDp(1), 0.1f)
    }

    @Test fun hidingEveryOptionalElementKeepsPlayAndTenRealRows() {
        val settings = VideoTilesSettings(10, 100, 2, false, false, false, false, false, false)
        val geometry = VideoTilesGeometry(settings, 1f)
        assertEquals(48f, geometry.thumbnailHeightDp, 0f)
        assertEquals(0f, geometry.titleHeightDp, 0f)
        assertEquals(0f, geometry.badgeHeightDp, 0f)
        assertEquals(10, geometry.rows)
        assertTrue(geometry.rowHeightDp >= 48f)
    }

    @Test fun normalizationPreservesVisibilityAndUsesScreenshotProportions() {
        val value = VideoTilesSettings(99, 900, 99, showQuality = false, showActions = false).normalized()
        assertFalse(value.showQuality)
        assertFalse(value.showActions)
        assertEquals(120f, VideoTilesGeometry(VideoTilesSettings(widthDp = 160), 1f).thumbnailHeightDp, 0f)
        assertEquals(100f, VideoTilesGeometry(VideoTilesSettings(widthDp = 160), 1f, true).thumbnailHeightDp, 0f)
    }
}
