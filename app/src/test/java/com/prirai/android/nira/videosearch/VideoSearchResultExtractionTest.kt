package com.prirai.android.nira.videosearch

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stage 4A contract: one read-only results page; no run or DB writes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchResultExtractionTest {
    private val document = "0123456789abcdef0123456789abcdef"
    private val request = "fedcba9876543210fedcba9876543210"

    private fun film(
        page: String = "https://example.org/film?id=37&lang=pl",
        title: Any = "Pełny tytuł pierwszego filmu",
        thumbnail: Any = "https://images.example.net/posters/37.webp",
        duration: Any = 536,
        source: String = "CARD",
        imageSource: String = "CARD",
        index: Int = 0,
    ) = JSONObject().apply {
        put("filmPageUrl", page)
        put("originalTitle", title)
        put("thumbnailUrl", thumbnail)
        put("durationSeconds", duration)
        put("titleSource", source)
        put("imageSource", imageSource)
        put("originalIndex", index)
    }

    private fun result(vararg films: JSONObject) = JSONObject().apply {
        put("schemaVersion", 1)
        put("event", "results")
        put("documentId", document)
        put("requestId", request)
        put("status", if (films.isEmpty()) "empty" else "ok")
        put("films", JSONArray().apply { films.forEach { put(it) } })
        put("inspectedCards", films.size)
        put("moreOnPage", false)
    }

    private fun decode(json: JSONObject) =
        VideoSearchBridgeProtocol.decode(json) as? VideoSearchBridgeMessage.Results

    @Test fun extractsExactFilmPageQueryOriginalCardTitleImageAndDuration() {
        val parsed = decode(result(film()))!!
        val first = parsed.page.films.single()
        assertEquals("https://example.org/film?id=37&lang=pl", first.filmPageUrl)
        assertEquals("Pełny tytuł pierwszego filmu", first.originalTitle)
        assertEquals("https://images.example.net/posters/37.webp", first.thumbnailUrl)
        assertEquals(536L, first.durationSeconds)
        assertEquals(VideoResultTitleSource.CARD, first.titleSource)
        assertEquals(VideoResultImageSource.CARD, first.imageSource)
        assertEquals(0, first.originalIndex)
    }

    @Test fun doesNotInventFallbackPageTitleOrThumbnail() {
        val missing = film(title = JSONObject.NULL, thumbnail = JSONObject.NULL,
            duration = JSONObject.NULL, source = "UNKNOWN", imageSource = "UNKNOWN")
        val parsed = decode(result(missing))!!.page.films.single()
        assertNull(parsed.originalTitle)
        assertNull(parsed.thumbnailUrl)
        assertNull(parsed.durationSeconds)
        assertEquals(VideoResultTitleSource.UNKNOWN, parsed.titleSource)
        assertEquals(VideoResultImageSource.UNKNOWN, parsed.imageSource)
    }

    @Test fun acceptsLinkTitleProvenanceAndDistinctCards() {
        val a = film(source = "LINK", thumbnail = JSONObject.NULL,
            imageSource = "UNKNOWN")
        val b = film(page = "https://example.org/film?id=38&lang=pl",
            title = "Inny indywidualny tytuł", index = 4)
        val parsed = decode(result(a, b))!!.page
        assertEquals(2, parsed.films.size)
        assertEquals(listOf(0, 4), parsed.films.map { it.originalIndex })
        assertEquals(VideoResultTitleSource.LINK, parsed.films[0].titleSource)
        assertNotEquals(parsed.films[0].originalTitle, parsed.films[1].originalTitle)
    }

    @Test fun emptyResultsAreNotAConfigurationFailure() {
        val parsed = decode(result())!!
        assertEquals("empty", parsed.page.status)
        assertTrue(parsed.page.films.isEmpty())
        assertEquals(0, parsed.page.inspectedCards)
    }

    @Test fun staleSelectorIsReportedWithoutInventingFilms() {
        val request = result().put("status", "stale")
        val parsed = decode(request)!!
        assertEquals("stale", parsed.page.status)
        assertTrue(parsed.page.films.isEmpty())
        assertNull(decode(request.put("inspectedCards", 3)))
    }

    @Test fun commandContainsOnlyConfiguredCardSelectorAndIdentifiers() {
        val json = VideoSearchBridgeProtocol.extract(document, request, ".movie-card")
        assertEquals(5, json.length())
        assertEquals("extract", json.getString("event"))
        assertEquals(".movie-card", json.getString("resultsCardSelector"))
        assertFalse(json.has("query"))
        assertFalse(json.has("tabId"))
        assertFalse(json.has("profileId"))
        assertFalse(json.has("cookies"))
    }

    @Test fun rejectsForgedProfileTabAndExtraPayload() {
        val source = result(film())
        for (name in listOf("tabId", "profileId", "isPrivate",
            "siteId", "cookie", "query", "html", "host")) {
            assertNull(name, decode(JSONObject(source.toString()).put(name, "bad")))
        }
    }

    @Test fun rejectsIllegalUrlsJavascriptBlobUserInfoAndControlCharacters() {
        for (bad in listOf("javascript:alert(1)", "data:text/html,abc",
            "blob:https://example.org/id", "https://user:pw@example.org/film",
            "file:///sdcard/film", "https://example.org/bad path")) {
            assertNull(bad, decode(result(film(page = bad))))
        }
        assertNull(decode(result(film(thumbnail = "data:image/png;base64,abc"))))
    }

    @Test fun rejectsTitleAndImageProvenanceInconsistencies() {
        assertNull(decode(result(film(title = JSONObject.NULL, source = "CARD"))))
        assertNull(decode(result(film(thumbnail = JSONObject.NULL, imageSource = "CARD"))))
        assertNull(decode(result(film(source = "PAGE_ONLY"))))
        assertNull(decode(result(film(imageSource = "IMPLICIT"))))
    }

    @Test fun rejectsMalformedDurationAndNegativeIndex() {
        assertNull(decode(result(film(duration = -1))))
        assertNull(decode(result(film(duration = 100_000))))
        assertNull(decode(result(film(duration = 20.5))))
        assertNull(decode(result(film(index = -1))))
        assertNull(decode(result(film(index = 201))))
    }

    @Test fun rejectsDuplicateUrlsOrDuplicateIndexes() {
        val one = film()
        val same = film(index = 1)
        assertNull(decode(result(one, same)))
        val two = film(page = "https://example.org/film?x=38", index = 0)
        assertNull(decode(result(one, two)))
    }

    @Test fun boundsResultArrayAndRejectsMalformedElementTypes() {
        val many = (0..16).map {
            film(page = "https://example.org/film?id=$it", index = it)
        }.toTypedArray()
        assertNull(decode(result(*many)))
        assertNull(decode(result(film()).put("moreOnPage", "false")))
        assertNull(decode(result(film()).put("inspectedCards", 81)))
        assertNull(decode(result(film()).put("films", JSONArray().put("raw"))))
        assertNull(decode(result(film()).put("status", "configured")))
    }
}
