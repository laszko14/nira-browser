package com.prirai.android.nira.videosearch

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stage 4B extension-message contract for card-local enriched metadata. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchEnrichedMetadataProtocolTest {
    private val document = "1234567890abcdef1234567890abcdef"
    private val request = "abcdef1234567890abcdef1234567890"

    private fun entry(
        url: String = "https://films.example/watch?id=17&lang=pl",
        title: Any = "Nazwa z JSON-LD przypisanego do karty",
        titleSource: String = "STRUCTURED",
        image: Any = "https://cdn.films.example/poster.webp",
        imageSource: String = "STRUCTURED",
        index: Int = 0,
    ) = JSONObject().apply {
        put("filmPageUrl", url)
        put("originalTitle", title)
        put("thumbnailUrl", image)
        put("durationSeconds", 544)
        put("titleSource", titleSource)
        put("imageSource", imageSource)
        put("originalIndex", index)
    }

    private fun response(vararg entries: JSONObject) = JSONObject().apply {
        put("schemaVersion", 1)
        put("event", "results")
        put("documentId", document)
        put("requestId", request)
        put("status", if (entries.isEmpty()) "empty" else "ok")
        put("films", JSONArray().apply { entries.forEach { put(it) } })
        put("inspectedCards", entries.size)
        put("moreOnPage", false)
    }

    private fun decode(packet: JSONObject) = VideoSearchBridgeProtocol.decode(packet)
        as? VideoSearchBridgeMessage.Results

    @Test fun acceptsStructuredTitleAndPosterProvenance() {
        val result = decode(response(entry()))!!.page.films.single()
        assertEquals(VideoResultTitleSource.STRUCTURED, result.titleSource)
        assertEquals(VideoResultImageSource.STRUCTURED, result.imageSource)
        assertEquals("https://films.example/watch?id=17&lang=pl", result.filmPageUrl)
        assertEquals("Nazwa z JSON-LD przypisanego do karty", result.originalTitle)
        assertEquals(544L, result.durationSeconds)
    }

    @Test fun acceptsMixedIndependentProvenance() {
        val film = decode(response(entry(titleSource = "CARD",
            imageSource = "STRUCTURED")))!!.page.films.single()
        assertEquals(VideoResultTitleSource.CARD, film.titleSource)
        assertEquals(VideoResultImageSource.STRUCTURED, film.imageSource)
    }

    @Test fun rejectsInventedDocumentTitleAndMediaPlayerSources() {
        assertNull(decode(response(entry(titleSource = "PAGE_ONLY"))))
        assertNull(decode(response(entry(titleSource = "PLAYER"))))
        assertNull(decode(response(entry(imageSource = "PLAYER"))))
    }

    @Test fun configuredSelectorCommandContainsOnlyCardLocalFields() {
        val config = VideoSiteConfiguration(
            searchInputSelector = "input[name=\"q\"]",
            resultsCardSelector = ".film-card",
            titleSelector = "h3.name",
            linkSelector = "a.watch",
            thumbnailSelector = "img.poster",
            durationSelector = "time.length",
        )
        val cmd = VideoSearchBridgeProtocol.extract(document, request, config)
        assertEquals("extract", cmd.getString("event"))
        assertEquals(9, cmd.length())
        assertEquals(".film-card", cmd.getString("resultsCardSelector"))
        assertEquals("h3.name", cmd.getString("titleSelector"))
        assertEquals("a.watch", cmd.getString("linkSelector"))
        assertEquals("img.poster", cmd.getString("thumbnailSelector"))
        assertEquals("time.length", cmd.getString("durationSelector"))
        for (forbidden in listOf("query", "profileId", "tabId", "pageUrl", "cookie"))
            assertFalse(cmd.has(forbidden))
    }

    @Test fun missingOptionalSelectorsHaveExplicitJsonNulls() {
        val config = VideoSiteConfiguration("input.search", ".card")
        val cmd = VideoSearchBridgeProtocol.extract(document, request, config)
        assertTrue(cmd.isNull("titleSelector"))
        assertTrue(cmd.isNull("linkSelector"))
        assertTrue(cmd.isNull("thumbnailSelector"))
        assertTrue(cmd.isNull("durationSelector"))
    }

    @Test fun oldCompactExtractionCommandRemainsSupported() {
        val compact = VideoSearchBridgeProtocol.extract(document, request, ".card")
        assertEquals(4, compact.length())
        assertEquals(".card", compact.getString("resultsCardSelector"))
    }

    @Test fun refusesSelectorInjectionBeforeCallingJs() {
        val invalid = VideoSiteConfiguration(
            searchInputSelector = "input.search", resultsCardSelector = ".card",
            titleSelector = "a:has(script)"
        )
        try {
            VideoSearchBridgeProtocol.extract(document, request, invalid)
            fail("Unsafe title selector was accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun refusesExtraFieldsAndUntrustedProfileIdentifiers() {
        val original = response(entry())
        for (name in listOf("profileId", "tabId", "isPrivate", "query",
            "filmName", "documentTitle", "password", "cookie")) {
            assertNull(decode(JSONObject(original.toString()).put(name, "spoofed")))
        }
    }

    @Test fun refusesDuplicateExactUrlsEvenWithDifferentProvenance() {
        val first = entry()
        val duplicate = entry(titleSource = "LINK", imageSource = "CARD", index = 2)
        assertNull(decode(response(first, duplicate)))
    }

    @Test fun acceptsDistinctQueriesWithoutCollapsingFilmPages() {
        val a = entry(index = 0)
        val b = entry(url = "https://films.example/watch?id=17&lang=en", index = 1)
        val c = entry(url = "https://films.example/watch?lang=pl&id=17", index = 2)
        assertEquals(3, decode(response(a, b, c))!!.page.films.size)
    }

    @Test fun cannotTurnNullMetadataIntoStructuredProvenance() {
        assertNull(decode(response(entry(title = JSONObject.NULL))))
        assertNull(decode(response(entry(image = JSONObject.NULL))))
    }

    @Test fun rejectsDirectBlobMediaUrlsAndBrokenPageUrls() {
        for (url in listOf("blob:https://films.example/123",
            "javascript:alert(1)", "https://user:pw@films.example/watch",
            "file:///sdcard/movie.mp4")) {
            assertNull(decode(response(entry(url = url))))
        }
    }
}
