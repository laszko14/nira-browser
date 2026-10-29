package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** Stage 6C: stable logical item identity on Room revisions and view toggles. */
class VideoSearchScrollAndEdgePolicyTest {
    private val scope = VideoSearchScope.Normal("default")
    private val run = VideoSearchRun(
        id = "r", scope = scope, query = "kino", siteIds = listOf("a", "b"),
        parallelism = VideoSearchParallelism.TWO,
        status = VideoSearchRunState.RUNNING, startedAtMs = 1L,
    )
    private fun task(site: String) = VideoSearchSiteTask(
        runId = "r", siteId = site, scope = scope, resultLimit = 200,
        status = VideoSearchSiteState.ACTIVE, visitedPages = 1,
    )
    private fun film(site: String, i: Int) = VideoSearchFilm(
        id = "$site-$i", runId = "r", siteId = site, scope = scope,
        siteName = "Źródło $site", filmPageUrl = "https://$site.example.org/?id=$i",
        originalTitle = "Film $i", thumbnailUrl = null,
        durationSeconds = null, titleSource = VideoResultTitleSource.CARD,
        imageSource = VideoResultImageSource.UNKNOWN,
        originalIndex = i, foundAtMs = 100L,
    )
    private fun display(n: Int = 2): VideoSearchResultsDisplay =
        VideoSearchResultsPresenter.project(
            run, listOf(task("a"), task("b")),
            (1..n).map { film("a", it) } + listOf(film("b", 1))
        )
    private fun keys(n: Int = 2, grouped: Boolean = true,
                     expanded: Set<String>? = null): List<String> {
        val model = display(n)
        return VideoSearchScrollKeys.visible(
            model, VideoSearchResultsPresenter.groups(model), grouped, expanded
        )
    }

    @Test fun allSitesHaveHeadersEvenWithZeroResults() {
        val model = VideoSearchResultsPresenter.project(
            run, listOf(task("a"), task("b")), emptyList()
        )
        assertEquals(listOf("site:a", "site:b"),
            VideoSearchScrollKeys.visible(
                model, VideoSearchResultsPresenter.groups(model), true, null))
    }

    @Test fun groupedKeysMatchLazyOrderIncludingHeaders() {
        assertEquals(listOf(
            "site:a", "film:a:a-1", "film:a:a-2",
            "site:b", "film:b:b-1"
        ), keys())
    }

    @Test fun flatKeysAreTheSameFilmKeysAsGrouped() {
        assertEquals(listOf("film:a:a-1", "film:a:a-2", "film:b:b-1"),
            keys(grouped = false))
    }

    @Test fun expandingAndCollapsingDoesNotRenumberOtherSites() {
        assertEquals(listOf("site:a", "site:b", "film:b:b-1"),
            keys(expanded = setOf("b")))
    }

    @Test fun savedAnchorSurvivesIncrementalAppend() {
        val anchor = "film:a:a-2"
        assertEquals(2, VideoSearchScrollKeys.indexForKey(keys(2), anchor))
        assertEquals(2, VideoSearchScrollKeys.indexForKey(keys(8), anchor))
    }

    @Test fun switchingGridToListKeepsOriginalFilmAnchor() {
        val anchor = keys().last()
        val idx = VideoSearchScrollKeys.indexForKey(keys(), anchor)
        assertEquals("film:b:b-1", keys()[idx])
    }

    @Test fun removedFilmFallsBackToItsSiteHeader() {
        val model = VideoSearchResultsPresenter.project(
            run, listOf(task("a"), task("b")),
            listOf(film("a", 1), film("b", 1))
        )
        val remaining = VideoSearchScrollKeys.visible(
            model, VideoSearchResultsPresenter.groups(model), true, null
        )
        assertEquals(0, VideoSearchScrollKeys.indexForKey(
            remaining, "film:a:a-99"))
    }

    @Test fun invalidOrEmptyScrollAnchorsResetSafely() {
        assertEquals(0, VideoSearchScrollKeys.indexForKey(emptyList(), "site:a"))
        assertEquals(0, VideoSearchScrollKeys.indexForKey(keys(), null))
        assertEquals(0, VideoSearchScrollKeys.indexForKey(keys(), "film:other:foreign"))
    }

    @Test fun siteHeaderRestoresTheSameIndexAfterAppend() {
        val anchor = "site:b"
        assertEquals(3, VideoSearchScrollKeys.indexForKey(keys(), anchor))
        assertEquals(9, VideoSearchScrollKeys.indexForKey(keys(8), anchor))
    }

    @Test fun noExternalUrlOrTitleIsUsedAsLazyKey() {
        assertFalse(keys().any { it.startsWith("https:") })
        assertFalse(keys().any { it.contains("Film ") })
    }

    @Test fun leftEdgeIsOnlyConfiguredRegion() {
        assertTrue(VideoSearchEdgeGesturePolicy.startedAtLeftEdge(0f, 44f))
        assertTrue(VideoSearchEdgeGesturePolicy.startedAtLeftEdge(44f, 44f))
        assertFalse(VideoSearchEdgeGesturePolicy.startedAtLeftEdge(45f, 44f))
        assertFalse(VideoSearchEdgeGesturePolicy.startedAtLeftEdge(-1f, 44f))
        assertFalse(VideoSearchEdgeGesturePolicy.startedAtLeftEdge(0f, 0f))
    }

    @Test fun nestedComposeDisallowCannotBlockEnabledLeftPanelGesture() {
        assertTrue(VideoSearchEdgeGesturePolicy.keepParentEligible(
            true, true, true, true, false))
    }

    @Test fun normalWebTabsAndDisabledSidePanelsAreNotAffected() {
        assertFalse(VideoSearchEdgeGesturePolicy.keepParentEligible(
            true, false, true, true, false))
        assertFalse(VideoSearchEdgeGesturePolicy.keepParentEligible(
            true, true, false, true, false))
        assertFalse(VideoSearchEdgeGesturePolicy.keepParentEligible(
            true, true, true, false, false))
    }

    @Test fun verticalAndMultiTouchAlwaysBelongToChildren() {
        assertFalse(VideoSearchEdgeGesturePolicy.keepParentEligible(
            true, true, true, true, true))
        assertFalse(VideoSearchEdgeGesturePolicy.keepParentEligible(
            false, true, true, true, false))
    }
}
