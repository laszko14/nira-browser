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
    @Test fun mergeChoicesPersistOnReentryButNeverLeakToAnotherRunTabOrProfile() {
        val first = VideoSearchResultsTabUiMemory.forTab("merge-tab", "default",
            setOf("merge-tab", "other-tab")).forRun("run")
        first.mergeByName.value = true
        first.mergeByDuration.value = true
        first.byWebsite.value = false
        first.pageSizeFor(null, VideoSearchSort.ORIGINAL, true, true, false)
            .intValue = 300
        val restored = VideoSearchResultsTabUiMemory.forTab("merge-tab", "default",
            setOf("merge-tab", "other-tab")).forRun("run")
        assertTrue(restored.mergeByName.value)
        assertTrue(restored.mergeByDuration.value)
        assertEquals(300, restored.pageSizeFor(
            null, VideoSearchSort.ORIGINAL, true, true, false).intValue)
        assertEquals(150, restored.pageSizeFor(
            null, VideoSearchSort.ORIGINAL, false, false, true).intValue)
        assertFalse(VideoSearchResultsTabUiMemory.forTab("merge-tab", "default",
            setOf("merge-tab", "other-tab")).forRun("another").mergeByName.value)
        assertFalse(VideoSearchResultsTabUiMemory.forTab("other-tab", "default",
            setOf("merge-tab", "other-tab")).forRun("run").mergeByDuration.value)
        assertFalse(VideoSearchResultsTabUiMemory.forTab("merge-tab", "work",
            setOf("merge-tab", "other-tab")).forRun("run").mergeByName.value)
    }

    @Test fun p3dSmartMergeRemainsOnlyInThisTabProfileAndRun() {
        val tabs = setOf("smart-tab", "other-smart-tab")
        val owner = VideoSearchResultsTabUiMemory.forTab("smart-tab", "default", tabs)
            .forRun("run-one")
        owner.mergeSmart.value = true
        owner.byWebsite.value = false
        owner.pageSizeFor(null, VideoSearchSort.ORIGINAL,
            byWebsite = false, mergeSmart = true).intValue = 450
        val retained = VideoSearchResultsTabUiMemory.forTab(
            "smart-tab", "default", tabs).forRun("run-one")
        assertTrue(retained.mergeSmart.value)
        assertEquals(450, retained.pageSizeFor(null, VideoSearchSort.ORIGINAL,
            byWebsite = false, mergeSmart = true).intValue)
        assertEquals(150, retained.pageSizeFor(null, VideoSearchSort.ORIGINAL,
            byWebsite = false, mergeSmart = false).intValue)
        assertFalse(owner.mergeByName.value)
        assertFalse(owner.mergeByDuration.value)
        assertFalse(VideoSearchResultsTabUiMemory.forTab(
            "smart-tab", "default", tabs).forRun("run-two").mergeSmart.value)
        assertFalse(VideoSearchResultsTabUiMemory.forTab(
            "other-smart-tab", "default", tabs).forRun("run-one").mergeSmart.value)
        assertFalse(VideoSearchResultsTabUiMemory.forTab(
            "smart-tab", "work", tabs).forRun("run-one").mergeSmart.value)
    }

    @Test fun bulkSelectionsSurviveFilmNavigationButRemainScopedToRunTabAndProfile() {
        val tab = "bulk-tab"
        val first = VideoSearchResultsTabUiMemory.forTab(tab, "default", setOf(tab))
            .forRun("search-a")
        first.selecting.value = true
        first.selectedFilmKeys.value = setOf("source:film-1", "source:film-2")
        val restored = VideoSearchResultsTabUiMemory.forTab(tab, "default",
            setOf(tab, "another-film")).forRun("search-a")
        assertTrue(restored.selecting.value)
        assertEquals(first.selectedFilmKeys.value, restored.selectedFilmKeys.value)
        assertFalse(VideoSearchResultsTabUiMemory.forTab(tab, "default",
            setOf(tab, "another-film")).forRun("search-b").selecting.value)
        assertTrue(VideoSearchResultsTabUiMemory.forTab("other-bulk", "default",
            setOf(tab, "other-bulk")).forRun("search-a").selectedFilmKeys.value.isEmpty())
        assertTrue(VideoSearchResultsTabUiMemory.forTab(tab, "work",
            setOf(tab, "other-bulk")).forRun("search-a").selectedFilmKeys.value.isEmpty())
    }

    @Test fun collapsedFiltersSurviveFilmNavigationWithoutLeakingAcrossRunsOrProfiles() {
        val activeTabs = setOf("results-a", "results-b")
        val firstTab = VideoSearchResultsTabUiMemory.forTab(
            "results-a", "profile-a", activeTabs).forRun("run-a")
        firstTab.controlsCollapsed.value = true
        firstTab.controlsCollapsedTouched.value = true
        assertTrue("returning to results retains the compact controls state",
            VideoSearchResultsTabUiMemory.forTab(
                "results-a", "profile-a", activeTabs).forRun("run-a")
                .controlsCollapsed.value)
        assertTrue(VideoSearchResultsTabUiMemory.forTab(
            "results-a", "profile-a", activeTabs).forRun("run-a")
            .controlsCollapsedTouched.value)
        assertFalse("a different run starts with its own full filter panel",
            firstTabForDifferentRun(activeTabs).controlsCollapsed.value)
        assertFalse("another browser tab/profile has independent controls",
            VideoSearchResultsTabUiMemory.forTab(
                "results-b", "profile-b", activeTabs).forRun("run-a")
                .controlsCollapsed.value)
    }

    private fun firstTabForDifferentRun(activeTabs: Set<String>) =
        VideoSearchResultsTabUiMemory.forTab(
            "results-a", "profile-a", activeTabs).forRun("run-b")

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
