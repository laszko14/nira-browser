package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** ETAP 4B: pure one-page correlation, no Room, Gecko or network. */
class VideoSearchResultCorrelationTest {
    private fun film(
        url: String = "https://catalog.example/film?id=7&lang=pl",
        title: String? = null,
        titleSource: VideoResultTitleSource = VideoResultTitleSource.UNKNOWN,
        image: String? = null,
        imageSource: VideoResultImageSource = VideoResultImageSource.UNKNOWN,
        duration: Long? = null,
        index: Int = 0,
    ) = VideoSearchExtractedFilm(
        filmPageUrl = url, originalTitle = title,
        thumbnailUrl = image, durationSeconds = duration,
        titleSource = titleSource, imageSource = imageSource, originalIndex = index
    )

    @Test fun duplicateFillsMissingFieldsWithoutChangingFilmIdentity() {
        val one = film(title = "Film", titleSource = VideoResultTitleSource.LINK,
            index = 2)
        val two = film(image = "https://cdn.example/poster.webp",
            imageSource = VideoResultImageSource.CARD, duration = 493, index = 9)
        val result = VideoSearchResultCorrelator.correlate(listOf(one, two)).single()
        assertEquals(one.filmPageUrl, result.filmPageUrl)
        assertEquals("Film", result.originalTitle)
        assertEquals("https://cdn.example/poster.webp", result.thumbnailUrl)
        assertEquals(493L, result.durationSeconds)
        assertEquals(2, result.originalIndex)
    }

    @Test fun structuredTitleBeatsCardTitleAndImageRanksIndependently() {
        val card = film(title = "Film z karty", titleSource = VideoResultTitleSource.CARD,
            image = "https://cdn.example/card.webp",
            imageSource = VideoResultImageSource.CARD)
        val structured = film(title = "Pełny tytuł", titleSource = VideoResultTitleSource.STRUCTURED,
            image = "https://cdn.example/schema.webp",
            imageSource = VideoResultImageSource.STRUCTURED, index = 5)
        val result = VideoSearchResultCorrelator.merge(card, structured)
        assertEquals("Pełny tytuł", result.originalTitle)
        assertEquals(VideoResultTitleSource.STRUCTURED, result.titleSource)
        assertEquals("https://cdn.example/schema.webp", result.thumbnailUrl)
        assertEquals(VideoResultImageSource.STRUCTURED, result.imageSource)
    }

    @Test fun cardTitleBeatsLinkButMissingImageCanBeFilledSeparately() {
        val first = film(title = "Film wg linku", titleSource = VideoResultTitleSource.LINK,
            image = "https://cdn.example/image1.webp",
            imageSource = VideoResultImageSource.STRUCTURED)
        val second = film(title = "Film wg karty", titleSource = VideoResultTitleSource.CARD,
            image = "https://cdn.example/image2.webp",
            imageSource = VideoResultImageSource.CARD)
        val result = VideoSearchResultCorrelator.merge(first, second)
        assertEquals("Film wg karty", result.originalTitle)
        assertEquals("https://cdn.example/image1.webp", result.thumbnailUrl)
    }

    @Test fun conflictingEqualQualityTitlesKeepFirstObserved() {
        val first = film(title = "Wersja A", titleSource = VideoResultTitleSource.CARD)
        val second = film(title = "Wersja B", titleSource = VideoResultTitleSource.CARD,
            index = 3)
        assertEquals("Wersja A", VideoSearchResultCorrelator.merge(first, second).originalTitle)
    }

    @Test fun durationKeepsFirstKnownValueRatherThanLatestGuess() {
        val first = film(duration = 100)
        val second = film(duration = 900, index = 6)
        assertEquals(100L, VideoSearchResultCorrelator.merge(first, second).durationSeconds)
    }

    @Test fun originalIndexBecomesEarliestAndOrderRemainsFirstSeen() {
        val url1 = "https://catalog.example/film?id=1"
        val url2 = "https://catalog.example/film?id=2"
        val candidates = listOf(
            film(url = url2, title = "B", titleSource = VideoResultTitleSource.LINK, index = 7),
            film(url = url1, title = "A", titleSource = VideoResultTitleSource.CARD, index = 4),
            film(url = url2, image = "https://cdn.example/b.webp",
                imageSource = VideoResultImageSource.CARD, index = 1)
        )
        val result = VideoSearchResultCorrelator.correlate(candidates)
        assertEquals(listOf(url2, url1), result.map { it.filmPageUrl })
        assertEquals(1, result[0].originalIndex)
    }

    @Test fun differentQueryParametersAreNeverMergedOrRewritten() {
        val a = film(url = "https://catalog.example/film?id=1&lang=pl", title = "Polski",
            titleSource = VideoResultTitleSource.CARD)
        val b = film(url = "https://catalog.example/film?id=1&lang=en", title = "English",
            titleSource = VideoResultTitleSource.CARD)
        val c = film(url = "https://catalog.example/film?lang=pl&id=1",
            title = "Kolejność parametrów", titleSource = VideoResultTitleSource.CARD)
        assertEquals(3, VideoSearchResultCorrelator.correlate(listOf(a, b, c)).size)
    }

    @Test fun sameTitleOnDistinctFilmPagesMustNeverMerge() {
        val a = film(url = "https://catalog.example/a", title = "Wspólny tytuł",
            titleSource = VideoResultTitleSource.CARD)
        val b = film(url = "https://catalog.example/b", title = "Wspólny tytuł",
            titleSource = VideoResultTitleSource.CARD)
        assertEquals(2, VideoSearchResultCorrelator.correlate(listOf(a, b)).size)
    }

    @Test fun mergeRejectsDifferentFilmUrlsWithoutLeakingThem() {
        val a = film(url = "https://catalog.example/a")
        val b = film(url = "https://catalog.example/b")
        try {
            VideoSearchResultCorrelator.merge(a, b)
            fail("Unexpected correlation of two film URLs")
        } catch (e: IllegalArgumentException) {
            assertFalse(e.message.orEmpty().contains("catalog.example"))
        }
    }

    @Test fun correlationDoesNotMutateTheOriginalObjects() {
        val original = film(index = 2)
        val second = film(title = "Tytuł", titleSource = VideoResultTitleSource.CARD, index = 4)
        VideoSearchResultCorrelator.correlate(listOf(original, second))
        assertNull(original.originalTitle)
        assertEquals(2, original.originalIndex)
    }

    @Test fun eightyCardBoundPreventsUnboundedMemory() {
        val batch = List(80) { film(url = "https://catalog.example/film?id=$it", index = it) }
        assertEquals(80, VideoSearchResultCorrelator.correlate(batch).size)
        try {
            VideoSearchResultCorrelator.correlate(batch + batch.first())
            fail("Batch should be bounded")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun resultToStringNeverShowsFilmUrlOrTitle() {
        val film = film(title = "Sekretny tytuł",
            titleSource = VideoResultTitleSource.CARD)
        assertFalse(film.toString().contains("Sekretny"))
        assertFalse(film.toString().contains("catalog.example"))
    }
}
