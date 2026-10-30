package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/**
 * Etap 1/10: cross-feature regression gates for the former Codex P1-P16 audit.
 *
 * These are native logic fixtures, not GeckoView/device instrumentation.
 * Individual P1-P16 regressions remain in their dedicated test classes.
 */
class VideoSearchAuditStage1GateTest {
    private val site = "https://films.example.org/search"

    private fun card(index: Int, suffix: String = index.toString()) =
        VideoSearchExtractedFilm(
            "https://films.example.org/watch?id=$suffix&lang=pl",
            "Film $index",
            null,
            null,
            VideoResultTitleSource.CARD,
            VideoResultImageSource.UNKNOWN,
            index,
        )

    @Test fun p3AdoptedNormalWorkerRemainsUserOwnedAfterSelectionChanges() {
        val owner = VideoSearchScope.Normal("default")
        val first = VideoSearchNormalWorkerLease("worker-1", owner)
        val second = VideoSearchNormalWorkerLease("worker-2", owner)
        val registry = VideoSearchNormalWorkerRegistry()
        val originalSession = Any()
        val secondSession = Any()
        registry.register(first, null)
        registry.register(second, null)
        assertTrue(first.bindSession(originalSession, null))
        assertTrue(second.bindSession(secondSession, null))

        registry.observeSelection("worker-1")
        registry.observeSelection("ordinary-user-tab")
        assertFalse(first.canUse(null))
        assertFalse(first.bindSession(originalSession, null))
        assertFalse(first.mayReclaim(
            VideoSearchNormalWorkerTab("worker-1", owner.contextId, false),
            null, owner, originalSession, safeOrigin = true
        ))
        assertTrue(second.canUse(null))
        assertTrue(second.acceptsSession(secondSession))
        registry.release(first)
        assertNull(registry.forTab("worker-1"))
        assertSame(second, registry.forTab("worker-2"))
    }

    @Test fun p7AggregateQuotaIsSumOfConfiguredSitesNotLegacyTwoThousand() {
        assertEquals(2_200, VideoSearchResultBudgets.aggregateLimit(List(11) { 200 }))
        assertEquals(2_000_000,
            VideoSearchResultBudgets.aggregateLimit(List(200) { 10_000 }))
        assertEquals(10_000, VideoSearchResultBudgets.MAX_SITE_RESULTS)
        assertEquals(1_000, VideoSearchResultBudgets.pageLimit(10_000))
        assertEquals(200_000, VideoSearchResultBudgets.scanLimit(10_000))
        assertTrue(VideoSearchResultBudgets.durationMs(10_000) > 180_000L)

        try {
            VideoSearchResultBudgets.aggregateLimit(List(201) { 200 })
            fail("More than 200 sites accepted")
        } catch (_: IllegalArgumentException) {}
        try {
            VideoSearchResultBudgets.requireSiteLimit(10_001)
            fail("Out-of-range site result limit accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun p6CrawlCursorDoesNotRewindAfterLegacyNinetySixCardBoundary() {
        val collector = VideoSearchPaginationCollector(site, 500)
        assertTrue(collector.beginDocument(site))
        for (start in 0 until 352 step 16) {
            val batch = VideoSearchExtractedPage(
                "ok", (start until start + 16).map { card(it) },
                inspectedCards = 16, moreOnPage = true
            )
            assertEquals(16, collector.appendWindow(start, batch))
        }
        assertEquals(352, collector.nextCardIndex)
        assertEquals(352, collector.uniqueCount)

        val next = VideoSearchExtractedPage(
            "ok", (352 until 368).map { card(it) },
            inspectedCards = 16, moreOnPage = false
        )
        assertEquals(16, collector.appendWindow(352, next))
        assertEquals(368, collector.uniqueCount)
        assertFalse(collector.shouldReadCardWindow)

        try {
            collector.appendWindow(0, next)
            fail("Non-contiguous card window was accepted")
        } catch (_: IllegalArgumentException) {}
        assertEquals(368, collector.nextCardIndex)
        assertEquals(368, collector.snapshot(VideoSearchPaginationStop.STALLED).films.size)
    }

    @Test fun p10P11NoOpAckAndCosmeticSpaChangesNeverProveNewSearch() {
        val before = VideoSearchExtractedPage(
            "ok", listOf(card(0), card(1)), inspectedCards = 2, moreOnPage = false
        )
        val cosmetic = before.copy(films = before.films.reversed()
            .map { it.copy(originalTitle = "Updated title") })
        assertFalse(VideoSearchWorkerVerification.confirmed(
            "document-a", "document-a", cosmetic, before
        ))
        assertFalse(VideoSearchWorkerVerification.confirmed(
            "document-a", "document-a", before, null
        ))
        val newResult = before.copy(
            films = listOf(card(0), card(2))
        )
        assertTrue(VideoSearchWorkerVerification.confirmed(
            "document-a", "document-a", newResult, before
        ))
        val explicitEmpty = VideoSearchExtractedPage(
            "no_results", emptyList(), inspectedCards = 0, moreOnPage = false
        )
        assertTrue(VideoSearchWorkerVerification.confirmed(
            "document-a", "document-b", explicitEmpty
        ))
        assertFalse(VideoSearchWorkerVerification.confirmed(
            "document-a", "document-a", explicitEmpty, null
        ))
        assertEquals(VideoSearchQueueStatus.DONE,
            VideoSearchWorkerVerification.finalQueueStatus(
                VideoSearchPaginationSnapshot(emptyList(), 0, 1, 0,
                    VideoSearchPaginationStop.NO_RESULTS)))
        assertEquals(VideoSearchQueueStatus.STOPPED,
            VideoSearchWorkerVerification.finalQueueStatus(
                VideoSearchPaginationSnapshot(before.films, 2, 1, 0,
                    VideoSearchPaginationStop.STALLED)))
    }
}
