package com.prirai.android.nira.media

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoTilesSettingsTest {
    @Test fun allTenRowCountsAreRetained() {
        for (rowCount in 1..10) {
            assertEquals(rowCount, TileLayout(rowCount, 160, 8).safe().rows)
        }
    }

    @Test fun extremeConfigurationsUseValidBounds() {
        assertEquals(TileLayout(1, 100, 2), TileLayout(0, 5, 0).safe())
        assertEquals(TileLayout(10, 280, 16), TileLayout(100, 900, 90).safe())
    }
}
