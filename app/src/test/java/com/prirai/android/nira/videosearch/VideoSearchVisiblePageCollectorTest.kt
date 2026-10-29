package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** ETAP 4C: in-document continuation, strict cursor and bounded correlation. */
class VideoSearchVisiblePageCollectorTest {
    private val origin = "https://films.example/results?q=test"
    private fun film(index: Int, url: String = "https://films.example/watch?id=$index",
        title: String? = "Film $index",
        titleSource: VideoResultTitleSource = VideoResultTitleSource.CARD,
        image: String? = null,
        imageSource: VideoResultImageSource = VideoResultImageSource.UNKNOWN,
        duration: Long? = null) =
        VideoSearchExtractedFilm(url, title, image, duration, titleSource, imageSource, index)

    private fun batch(start: Int, size: Int = 16,
                      films: List<VideoSearchExtractedFilm> =
                          (start until start + size).map { film(it) },
                      more: Boolean = true) =
        VideoSearchExtractedPage(
            status = if (films.isEmpty()) "empty" else "ok",
            films = films, inspectedCards = size, moreOnPage = more
        )

    @Test fun fiveBatchesCollectEightyDistinctFilmsInOriginalCardOrder() {
        val collector = VideoSearchVisiblePageCollector(origin, 200, VideoSearchPagination.NEXT_LINK)
        for (start in listOf(0, 16, 32, 48, 64)) {
            assertEquals(start, collector.nextCardIndex)
            collector.append(start, batch(start, more = start < 64))
        }
        assertFalse(collector.shouldReadAnotherBatch)
        val page = collector.snapshot()
        assertEquals(80, page.films.size)
        assertEquals(80, page.scannedCards)
        assertEquals((0 until 80).toList(), page.films.map { it.originalIndex })
        assertFalse(page.moreOnPage)
        assertEquals(VideoSearchPagination.NEXT_LINK, page.paginationStrategy)
    }

    @Test fun repeatsAcrossWindowsMergeOnlyByExactFilmUrl() {
        val collector = VideoSearchVisiblePageCollector(origin, 200, VideoSearchPagination.NONE)
        val url = "https://films.example/watch?id=42&lang=pl"
        collector.append(0, batch(0, films = listOf(
            film(0, url, "Title from link", VideoResultTitleSource.LINK)), more = true))
        collector.append(16, batch(16, films = listOf(
            film(16, url, "Title from JSON-LD", VideoResultTitleSource.STRUCTURED,
                "https://cdn.example/poster.webp", VideoResultImageSource.STRUCTURED, 456L)
        ), more = false))
        val chosen = collector.snapshot().films.single()
        assertEquals(url, chosen.filmPageUrl)
        assertEquals("Title from JSON-LD", chosen.originalTitle)
        assertEquals("https://cdn.example/poster.webp", chosen.thumbnailUrl)
        assertEquals(456L, chosen.durationSeconds)
        assertEquals(0, chosen.originalIndex)
    }

    @Test fun differentQueryOrderAndLanguageNeverMerge() {
        val collector = VideoSearchVisiblePageCollector(origin, 80, VideoSearchPagination.NONE)
        val a = film(0, "https://films.example/watch?id=1&lang=pl")
        val b = film(16, "https://films.example/watch?lang=pl&id=1")
        val c = film(17, "https://films.example/watch?id=1&lang=en")
        collector.append(0, batch(0, films = listOf(a), more = true))
        collector.append(16, batch(16, films = listOf(b, c), more = false))
        assertEquals(listOf(a.filmPageUrl, b.filmPageUrl, c.filmPageUrl),
            collector.snapshot().films.map { it.filmPageUrl })
    }

    @Test fun emptyWindowStillAdvancesAbsoluteCardCursor() {
        val collector = VideoSearchVisiblePageCollector(origin, 80, VideoSearchPagination.NONE)
        collector.append(0, batch(0, films = emptyList(), more = true))
        assertEquals(16, collector.nextCardIndex)
        collector.append(16, batch(16, films = listOf(film(19)), more = false))
        assertEquals(1, collector.snapshot().films.size)
        assertEquals(32, collector.snapshot().scannedCards)
    }

    @Test fun completelyEmptyPageTerminatesWithoutRetry() {
        val collector = VideoSearchVisiblePageCollector(origin, 200, VideoSearchPagination.NONE)
        collector.append(0, batch(0, size = 0, films = emptyList(), more = false))
        assertEquals(0, collector.snapshot().scannedCards)
        assertTrue(collector.snapshot().films.isEmpty())
        assertFalse(collector.shouldReadAnotherBatch)
        assertFalse(collector.snapshot().moreOnPage)
    }

    @Test fun rejectsNonContiguousOrRepeatedWindowCursor() {
        val collector = VideoSearchVisiblePageCollector(origin, 200, VideoSearchPagination.NONE)
        collector.append(0, batch(0, more = true))
        for (wrong in listOf(0, 8, 32, 64)) {
            try {
                collector.append(wrong, batch(wrong, more = true))
                fail("Incorrect cursor accepted: $wrong")
            } catch (_: IllegalArgumentException) {}
        }
        assertEquals(16, collector.nextCardIndex)
    }

    @Test fun rejectsOffSiteFilmAndPreservesPreviouslyReadItems() {
        val collector = VideoSearchVisiblePageCollector(origin, 200, VideoSearchPagination.NONE)
        collector.append(0, batch(0, films = listOf(film(0)), more = true))
        try {
            collector.append(16, batch(16, films = listOf(
                film(16, "https://advertiser.example/watch?id=16")), more = false))
            fail("Off-site film should be rejected")
        } catch (_: IllegalArgumentException) {}
        assertEquals(16, collector.nextCardIndex)
        assertEquals(listOf(0), collector.snapshot().films.map { it.originalIndex })
    }

    @Test fun rejectsFilmWhoseIndexBelongsToAnotherWindow() {
        val collector = VideoSearchVisiblePageCollector(origin, 200, VideoSearchPagination.NONE)
        try {
            collector.append(0, batch(0, films = listOf(film(50)), more = true))
            fail("Index outside window accepted")
        } catch (_: IllegalArgumentException) {}
        assertEquals(0, collector.nextCardIndex)
        assertTrue(collector.snapshot().films.isEmpty())
    }

    @Test fun userLimitStopsAfterUniqueFilmCountNotPageCount() {
        val collector = VideoSearchVisiblePageCollector(origin, 3, VideoSearchPagination.LOAD_MORE)
        collector.append(0, batch(0, films = listOf(film(0), film(1), film(2)), more = true))
        assertFalse(collector.shouldReadAnotherBatch)
        assertEquals(3, collector.snapshot().films.size)
        assertTrue(collector.snapshot().moreOnPage)
        assertEquals(16, collector.snapshot().scannedCards)
    }

    @Test fun duplicateFilmDoesNotConsumeAnotherUniqueSlot() {
        val collector = VideoSearchVisiblePageCollector(origin, 2, VideoSearchPagination.NONE)
        val same = "https://films.example/watch?id=same"
        collector.append(0, batch(0, films = listOf(
            film(0, same)
        ), more = true))
        assertEquals(1, collector.snapshot().films.size)
        assertTrue(collector.shouldReadAnotherBatch)
        collector.append(16, batch(16, films = listOf(
            film(16, same, "Better", VideoResultTitleSource.STRUCTURED),
            film(17)
        ), more = false))
        assertEquals(2, collector.snapshot().films.size)
    }

    @Test fun finalBatchCanBeShortAndStopAtActualEndOfDocument() {
        val collector = VideoSearchVisiblePageCollector(origin, 200, VideoSearchPagination.NONE)
        collector.append(0, batch(0, more = true))
        collector.append(16, batch(16, size = 3, more = false))
        assertFalse(collector.shouldReadAnotherBatch)
        assertEquals(19, collector.snapshot().scannedCards)
        assertEquals(19, collector.snapshot().films.size)
    }

    @Test fun maximumOfEightyCardsNeverStartsSixthRequest() {
        val collector = VideoSearchVisiblePageCollector(origin, 200, VideoSearchPagination.INFINITE_SCROLL)
        for (start in 0..64 step 16)
            collector.append(start, batch(start, more = true))
        assertEquals(80, collector.nextCardIndex)
        assertFalse(collector.shouldReadAnotherBatch)
        assertTrue(collector.snapshot().moreOnPage)
        assertEquals(VideoSearchPagination.INFINITE_SCROLL,
            collector.snapshot().paginationStrategy)
    }

    @Test fun invalidWindowSizeOrClaimedContinuationIsRejected() {
        val collector = VideoSearchVisiblePageCollector(origin, 200, VideoSearchPagination.NONE)
        try {
            collector.append(0, batch(0, size = 0, films = emptyList(), more = true))
            fail("Zero-card window cannot claim more cards")
        } catch (_: IllegalArgumentException) {}
        try {
            collector.append(0, batch(0, size = 17,
                films = listOf(film(0)), more = true))
            fail("Oversized window accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun previewNeverClaimsPaginationWasExecuted() {
        val collector = VideoSearchVisiblePageCollector(origin, 200, VideoSearchPagination.NEXT_LINK)
        collector.append(0, batch(0, size = 1, more = false))
        val snapshot = collector.snapshot()
        assertEquals(VideoSearchPagination.NEXT_LINK, snapshot.paginationStrategy)
        assertEquals(1, snapshot.scannedCards)
        assertFalse(snapshot.moreOnPage)
    }

    @Test fun noMetadataDoesNotCreateSharedDocumentTitle() {
        val collector = VideoSearchVisiblePageCollector(origin, 80, VideoSearchPagination.NONE)
        collector.append(0, batch(0, films = listOf(film(0, title = null,
            titleSource = VideoResultTitleSource.UNKNOWN)), more = false))
        assertNull(collector.snapshot().films.single().originalTitle)
    }
}
