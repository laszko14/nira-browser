package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

class VideoSearchResultsTabUiMemoryTest {
    @Test fun filmTabNavigationKeepsFlatSortLoadedCountAndLazyScrollOwners() {
        val tab = "result-navigation"
        val first = VideoSearchResultsTabUiMemory.forTab(tab, "default", setOf(tab))
        first.chosenRunId.value = "run-a"
        val run = first.forRun("run-a")
        run.byWebsite.value = false
        run.sort.value = VideoSearchSort.DURATION_LONGEST
        run.pageSizeFor(null, run.sort.value).intValue = 450
        val grid = run.gridState
        val list = run.listState
        val again = VideoSearchResultsTabUiMemory.forTab(tab, "default",
            setOf(tab, "film-new-tab"))
        val restored = again.forRun("run-a")
        assertSame(first, again)
        assertEquals("run-a", again.chosenRunId.value)
        assertFalse(restored.byWebsite.value)
        assertEquals(VideoSearchSort.DURATION_LONGEST, restored.sort.value)
        assertEquals(450, restored.pageSizeFor(null, restored.sort.value).intValue)
        assertSame(grid, restored.gridState)
        assertSame(list, restored.listState)
    }
    @Test fun sameRunInDifferentTabsAndProfilesMustNotShareDisplaySettings() {
        val a = VideoSearchResultsTabUiMemory.forTab("result-one", "work",
            setOf("result-one", "result-two"))
        val b = VideoSearchResultsTabUiMemory.forTab("result-two", "work",
            setOf("result-one", "result-two"))
        val c = VideoSearchResultsTabUiMemory.forTab("result-one", "default",
            setOf("result-one", "result-two"))
        a.forRun("run").sort.value = VideoSearchSort.DURATION_SHORTEST
        a.forRun("run").byWebsite.value = false
        assertEquals(VideoSearchSort.ORIGINAL, b.forRun("run").sort.value)
        assertTrue(b.forRun("run").byWebsite.value)
        assertEquals(VideoSearchSort.ORIGINAL, c.forRun("run").sort.value)
        assertNotSame(a.forRun("run").gridState, c.forRun("run").gridState)
    }
    @Test fun runAndFilterChangesPreserveIndependentPageSizesAndExpansion() {
        val tab = VideoSearchResultsTabUiMemory.forTab("result-runs", "default",
            setOf("result-runs"))
        val old = tab.forRun("run-old")
        old.sort.value = VideoSearchSort.DURATION_LONGEST
        old.pageSizeFor(null, old.sort.value).intValue = 750
        old.siteFilter.value = "site-one"
        old.expandedSites.value = setOf("site-one")
        val other = old.pageSizeFor("site-one", old.sort.value)
        assertEquals(150, other.intValue)
        other.intValue = 300
        assertEquals(750, old.pageSizeFor(null, old.sort.value).intValue)
        assertEquals(VideoSearchSort.ORIGINAL, tab.forRun("run-new").sort.value)
        assertSame(old, tab.forRun("run-old"))
        assertEquals(setOf("site-one"), tab.forRun("run-old").expandedSites.value)
    }
    @Test fun pruningClosedTabDoesNotPruneResultsWhileFilmIsOpen() {
        val a = VideoSearchResultsTabUiMemory.forTab("result-closed", "default",
            setOf("result-closed"))
        VideoSearchResultsTabUiMemory.forTab("result-open", "default",
            setOf("result-closed", "result-open", "film"))
        assertSame(a, VideoSearchResultsTabUiMemory.forTab("result-closed",
            "default", setOf("result-closed", "result-open", "film")))
        VideoSearchResultsTabUiMemory.forTab("result-open", "default",
            setOf("result-open", "film"))
        assertNotSame(a, VideoSearchResultsTabUiMemory.forTab("result-closed",
            "default", setOf("result-closed", "result-open")))
    }
}
