package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/**
 * Stage 10D: the same bounded collector used by the private Gecko bridge.
 * These are pure unit tests; real Gecko navigation still needs device tests.
 */
class VideoSearchPrivatePaginationContractTest {
    private fun film(index: Int, page: Int = 1, host: String = "example.org") =
        VideoSearchExtractedFilm(
            filmPageUrl = "https://$host/movie?pg=$page&id=$index",
            originalTitle = "Film $index",
            thumbnailUrl = null, durationSeconds = null,
            titleSource = VideoResultTitleSource.CARD,
            imageSource = VideoResultImageSource.UNKNOWN,
            originalIndex = index,
        )

    private fun window(
        from: Int, count: Int, page: Int = 1,
        more: Boolean = false,
    ) = VideoSearchExtractedPage(
        status = "ok",
        films = (from until from + count).map { film(it, page) },
        inspectedCards = count,
        moreOnPage = more,
    )

    @Test fun twoHundredDistinctFilmsAreBoundedAcrossMultiplePages() {
        val collector = VideoSearchPaginationCollector("https://example.org/search", 200)
        assertTrue(collector.beginDocument("https://example.org/results?page=1"))
        // Thirteen 16-card windows fit inside twelve document visits.
        repeat(2) {
            assertEquals(16, collector.appendWindow(
                collector.nextCardIndex,
                window(collector.nextCardIndex, 16, page = 1, more = it == 0),
            ))
        }
        for (page in 2..12) {
            if (collector.reachedLimit) break
            assertTrue(collector.beginDocument("https://example.org/results?page=$page"))
            val count = minOf(16, 200 - collector.uniqueCount)
            assertEquals(count, collector.appendWindow(
                0, window(0, count, page),
            ))
        }
        assertEquals(200, collector.uniqueCount)
        assertTrue(collector.reachedLimit)
        assertEquals(200, collector.snapshot(VideoSearchPaginationStop.LIMIT_REACHED).films.size)
    }

    @Test fun differentPagesCannotEscapeConfiguredSiteOrigin() {
        val collector = VideoSearchPaginationCollector("https://example.org/search", 200)
        assertTrue(collector.beginDocument("https://example.org/results"))
        assertFalse(collector.beginDocument("https://evil.example/results"))
    }

    @Test fun browserPageCannotBeVisitedTwice() {
        val collector = VideoSearchPaginationCollector("https://example.org/search", 200)
        assertTrue(collector.beginDocument("https://example.org/results?pg=1"))
        assertFalse(collector.beginDocument("https://example.org/results?pg=1"))
    }

    @Test fun forgedFilmFromAnotherOriginDoesNotMutatePreviousResults() {
        val collector = VideoSearchPaginationCollector("https://example.org/search", 200)
        assertTrue(collector.beginDocument("https://example.org/results"))
        collector.appendWindow(0, window(0, 1, more = true))
        val before = collector.uniqueCount
        val poisoned = VideoSearchExtractedPage(
            status = "ok", films = listOf(film(1, host = "evil.example")),
            inspectedCards = 1, moreOnPage = false,
        )
        try {
            collector.appendWindow(1, poisoned)
            fail("Off-origin result accepted")
        } catch (_: IllegalArgumentException) {}
        assertEquals(before, collector.uniqueCount)
    }

    @Test fun actionLimitPreventsUnboundedPrivateNavigation() {
        val collector = VideoSearchPaginationCollector("https://example.org/search", 200)
        assertTrue(collector.beginDocument("https://example.org/results"))
        repeat(20) { assertTrue(collector.recordAction()) }
        assertFalse(collector.recordAction())
        assertTrue(collector.isAtActionLimit)
    }

    @Test fun collectorStopsAfterTwelveDistinctDocuments() {
        val collector = VideoSearchPaginationCollector("https://example.org/search", 200)
        repeat(12) { i ->
            assertTrue(collector.beginDocument("https://example.org/results?pg=$i"))
        }
        assertTrue(collector.isAtDocumentLimit)
        assertFalse(collector.beginDocument("https://example.org/results?pg=12"))
    }

    @Test fun sameDocumentContinuationDoesNotRescanPreviousCards() {
        val collector = VideoSearchPaginationCollector("https://example.org/search", 200)
        assertTrue(collector.beginDocument("https://example.org/results"))
        assertEquals(16, collector.appendWindow(0, window(0, 16, more = false)))
        assertFalse(collector.shouldReadCardWindow)
        collector.armSameDocumentContinuation()
        assertTrue(collector.shouldReadCardWindow)
        assertEquals(16, collector.nextCardIndex)
        assertEquals(8, collector.appendWindow(16, window(16, 8)))
        assertEquals(24, collector.uniqueCount)
    }
}
