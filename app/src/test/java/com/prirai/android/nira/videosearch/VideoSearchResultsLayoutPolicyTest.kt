package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** Unit policy tests; physical portrait/landscape UI remains NOT_TESTED. */
class VideoSearchResultsLayoutPolicyTest {
    @Test fun portraitAndWideLandscapeChooseResponsivePosterWidths() {
        assertEquals(140, VideoSearchResultsLayoutPolicy.tileMinWidthDp(320, false))
        assertEquals(152, VideoSearchResultsLayoutPolicy.tileMinWidthDp(393, false))
        assertEquals(190, VideoSearchResultsLayoutPolicy.tileMinWidthDp(840, true))
    }

    @Test fun tabletPortraitAndNarrowLandscapeReflowWithoutChangingViewMode() {
        assertEquals(180, VideoSearchResultsLayoutPolicy.tileMinWidthDp(720, false))
        assertEquals(140, VideoSearchResultsLayoutPolicy.tileMinWidthDp(320, true))
        assertEquals(168, VideoSearchResultsLayoutPolicy.tileMinWidthDp(680, true))
    }

    @Test fun portraitAndLandscapeFiltersAreBoundedEvenOnShortScreens() {
        assertEquals(380, VideoSearchResultsLayoutPolicy.filterPanelMaxHeightDp(860, false))
        assertEquals(329, VideoSearchResultsLayoutPolicy.filterPanelMaxHeightDp(700, false))
        assertEquals(154, VideoSearchResultsLayoutPolicy.filterPanelMaxHeightDp(360, true))
        assertEquals(167, VideoSearchResultsLayoutPolicy.filterPanelMaxHeightDp(390, true))
        assertEquals(110, VideoSearchResultsLayoutPolicy.filterPanelMaxHeightDp(0, false))
        assertEquals(103, VideoSearchResultsLayoutPolicy.filterPanelMaxHeightDp(0, true))
    }
}
