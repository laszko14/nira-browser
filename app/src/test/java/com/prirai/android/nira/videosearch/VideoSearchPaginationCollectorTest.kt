package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** Stage 4D: bounded single-site crawl, exact-URL merging, honest stop reasons. */
class VideoSearchPaginationCollectorTest {
    private val site = "https://films.example/search?q=test"
    private fun film(index: Int, suffix: String = index.toString(),
                     title: String? = "Film $index",
                     source: VideoResultTitleSource = VideoResultTitleSource.CARD,
                     duration: Long? = null): VideoSearchExtractedFilm =
        VideoSearchExtractedFilm("https://films.example/watch?id=$suffix",
            title, null, duration, source, VideoResultImageSource.UNKNOWN, index)

    private fun window(offset: Int, count: Int = 16, more: Boolean = true,
                       films: List<VideoSearchExtractedFilm> =
                           (offset until offset + count).map { film(it) }) =
        VideoSearchExtractedPage(
            if (films.isEmpty()) "empty" else "ok",
            films, count, more
        )

    @Test fun capsAt200EvenIfUserSets10000() {
        val collector = VideoSearchPaginationCollector(site, 10_000)
        assertEquals(200, collector.limit)
        assertTrue(collector.beginDocument(site))
        for (i in 0..192 step 16) {
            collector.appendWindow(i, window(i, more = true))
            if (collector.reachedLimit) break
        }
        assertEquals(200, collector.uniqueCount)
        assertTrue(collector.reachedLimit)
        assertFalse(collector.shouldReadCardWindow)
        val snap = collector.snapshot(VideoSearchPaginationStop.LIMIT_REACHED)
        assertEquals(200, snap.films.size)
        assertEquals(VideoSearchPaginationStop.LIMIT_REACHED, snap.reason)
    }

    @Test fun respectsSmallerPerSiteLimit() {
        val collector = VideoSearchPaginationCollector(site, 3)
        assertTrue(collector.beginDocument(site))
        collector.appendWindow(0, window(0, more = true))
        assertEquals(3, collector.uniqueCount)
        assertTrue(collector.reachedLimit)
        assertEquals(listOf("0", "1", "2"),
            collector.snapshot(VideoSearchPaginationStop.LIMIT_REACHED)
                .films.map { it.filmPageUrl.substringAfter("id=") })
    }

    @Test fun aggregatesResultsAcrossDifferentPaginationDocuments() {
        val c = VideoSearchPaginationCollector(site, 200)
        assertTrue(c.beginDocument(site))
        assertEquals(16, c.appendWindow(0, window(0, more = false)))
        assertTrue(c.beginDocument("https://films.example/search?page=2"))
        assertEquals(16, c.appendWindow(0, window(0, films =
            (0..15).map { film(it, "second-$it") }, more = false)))
        assertEquals(32, c.uniqueCount)
        assertEquals(2, c.visitedPages)
        assertEquals(32, c.snapshot(VideoSearchPaginationStop.NO_MORE_RESULTS).scannedCards)
    }

    @Test fun repeatsAcrossDocumentsEnrichWithoutConsumingUniqueSlots() {
        val c = VideoSearchPaginationCollector(site, 200)
        c.beginDocument(site)
        c.appendWindow(0, window(0, films = listOf(
            film(0, "same", "Link title", VideoResultTitleSource.LINK)), more = false))
        c.beginDocument("https://films.example/search?page=2")
        c.appendWindow(0, window(0, films = listOf(
            film(0, "same", "Structured title", VideoResultTitleSource.STRUCTURED, 432L),
            film(1, "new")), more = false))
        assertEquals(2, c.uniqueCount)
        assertEquals("Structured title", c.snapshot(VideoSearchPaginationStop.NO_MORE_RESULTS)
            .films.first().originalTitle)
        assertEquals(432L, c.snapshot(VideoSearchPaginationStop.NO_MORE_RESULTS)
            .films.first().durationSeconds)
    }

    @Test fun repeatedOrCrossOriginDocumentRefused() {
        val c = VideoSearchPaginationCollector(site, 200)
        assertTrue(c.beginDocument(site))
        assertFalse(c.beginDocument(site))
        assertFalse(c.beginDocument("https://evil.example/search"))
        assertFalse(c.beginDocument("http://films.example/search"))
        assertEquals(1, c.visitedPages)
    }

    @Test fun stopsAfterTwelveDocuments() {
        val c = VideoSearchPaginationCollector(site, 200)
        for (i in 0..11)
            assertTrue(c.beginDocument("https://films.example/search?page=$i"))
        assertTrue(c.isAtDocumentLimit)
        assertFalse(c.beginDocument("https://films.example/search?page=12"))
        assertEquals(12, c.visitedPages)
    }

    @Test fun stopsAfterTwentyPaginationActions() {
        val c = VideoSearchPaginationCollector(site, 200)
        for (i in 1..20) assertTrue(c.recordAction())
        assertTrue(c.isAtActionLimit)
        assertFalse(c.recordAction())
        assertEquals(20, c.pageActions)
    }

    @Test fun advancesWhenWindowContainsNoValidFilm() {
        val c = VideoSearchPaginationCollector(site, 200)
        c.beginDocument(site)
        assertEquals(0, c.appendWindow(0, window(0, films = emptyList(), more = true)))
        assertEquals(16, c.nextCardIndex)
        assertEquals(1, c.appendWindow(16, window(16,
            films = listOf(film(23)), more = false)))
        assertEquals(32, c.snapshot(VideoSearchPaginationStop.NO_MORE_RESULTS).scannedCards)
    }

    @Test fun permitsPartialCardWindowAfterLoadMore() {
        val c = VideoSearchPaginationCollector(site, 200)
        c.beginDocument(site)
        c.appendWindow(0, window(0, count = 5, more = false))
        assertFalse(c.shouldReadCardWindow)
        c.armSameDocumentContinuation()
        assertEquals(1, c.appendWindow(5, window(5, count = 1, more = false)))
        assertEquals(6, c.nextCardIndex)
        assertEquals(6, c.uniqueCount)
    }

    @Test fun sameDocumentLoadMoreCannotRewindOrDuplicateWindows() {
        val c = VideoSearchPaginationCollector(site, 200)
        c.beginDocument(site)
        c.appendWindow(0, window(0, count = 3, more = false))
        c.armSameDocumentContinuation()
        try {
            c.appendWindow(0, window(0, count = 2, more = false))
            fail("Rewound cursor")
        } catch (_: IllegalArgumentException) {}
        assertEquals(3, c.nextCardIndex)
    }

    @Test fun emptyWindowCannotClaimMoreDomCards() {
        val c = VideoSearchPaginationCollector(site, 200)
        c.beginDocument(site)
        try {
            c.appendWindow(0, window(0, count = 0, more = true, films = emptyList()))
            fail("Contradictory response")
        } catch (_: IllegalArgumentException) {}
        assertEquals(0, c.nextCardIndex)
    }

    @Test fun rejectsOffSiteFilmWithoutPartialStateMutation() {
        val c = VideoSearchPaginationCollector(site, 200)
        c.beginDocument(site)
        c.appendWindow(0, window(0, more = true))
        val old = c.snapshot(VideoSearchPaginationStop.STALLED).films
        val forged = VideoSearchExtractedFilm(
            "https://evil.example/watch?id=2", "Ad", null, null,
            VideoResultTitleSource.CARD, VideoResultImageSource.UNKNOWN, 17
        )
        try {
            c.appendWindow(16, window(16,
                films = listOf(film(16), forged), more = false))
            fail("Cross-site film")
        } catch (_: IllegalArgumentException) {}
        assertEquals(old, c.snapshot(VideoSearchPaginationStop.STALLED).films)
        assertEquals(16, c.nextCardIndex)
    }

    @Test fun rejectsFilmIndexOutsideWindowAndStalePage() {
        val c = VideoSearchPaginationCollector(site, 200)
        c.beginDocument(site)
        try {
            c.appendWindow(0, window(0, films = listOf(film(39)), more = true))
            fail("Index outside card window")
        } catch (_: IllegalArgumentException) {}
        try {
            c.appendWindow(0, VideoSearchExtractedPage(
                "stale", emptyList(), 0, false))
            fail("Stale result")
        } catch (_: IllegalArgumentException) {}
        assertEquals(0, c.nextCardIndex)
    }

    @Test fun boundsDocumentAt320ScannedCards() {
        val c = VideoSearchPaginationCollector(site, 200)
        c.beginDocument(site)
        for (i in 0..304 step 16)
            c.appendWindow(i, window(i, films = emptyList(), more = true))
        assertEquals(320, c.nextCardIndex)
        assertTrue(c.exhaustedCardBudget)
        assertFalse(c.shouldReadCardWindow)
    }

    @Test fun preservesDistinctFilmsWithSameTitleAndDifferentQuery() {
        val c = VideoSearchPaginationCollector(site, 200)
        c.beginDocument(site)
        val a = VideoSearchExtractedFilm(
            "https://films.example/watch?id=2&lang=pl", "Jeden tytuł",
            null, null, VideoResultTitleSource.CARD, VideoResultImageSource.UNKNOWN, 0)
        val b = a.copy(filmPageUrl = "https://films.example/watch?id=2&lang=en",
            originalIndex = 1)
        c.appendWindow(0, window(0, films = listOf(a, b), more = false))
        assertEquals(2, c.uniqueCount)
    }

    @Test fun partialStopReasonsAreNotMisreportedAsCompletion() {
        val c = VideoSearchPaginationCollector(site, 200)
        c.beginDocument(site)
        c.appendWindow(0, window(0, count = 2, more = false))
        for (reason in listOf(
            VideoSearchPaginationStop.STALLED, VideoSearchPaginationStop.UNSAFE,
            VideoSearchPaginationStop.ERROR, VideoSearchPaginationStop.RESOURCE_LIMIT,
            VideoSearchPaginationStop.CONFIGURATION_CHANGED
        )) {
            val snap = c.snapshot(reason)
            assertEquals(reason, snap.reason)
            assertEquals(2, snap.films.size)
            assertFalse(snap.toString().contains("films.example"))
        }
    }
}
