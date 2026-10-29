package com.prirai.android.nira.videosearch

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ETAP 4C: chunk request and legacy result contracts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchPageBatchProtocolTest {
    private val doc = "a1234567890bcdefa1234567890bcdef"
    private val request = "f1234567890bcdeff1234567890bcdef"
    private val config = VideoSiteConfiguration(
        searchInputSelector = "input.search", resultsCardSelector = ".movie-card",
        titleSelector = "h2.title", linkSelector = "a.details",
        thumbnailSelector = "img.poster", durationSelector = "time.duration",
        pagination = VideoSearchPagination.NEXT_LINK, nextPageSelector = "a.next",
    )
    private fun film(index: Int) = JSONObject().apply {
        put("filmPageUrl", "https://films.example/watch?id=$index")
        put("originalTitle", "Film $index")
        put("thumbnailUrl", JSONObject.NULL)
        put("durationSeconds", JSONObject.NULL)
        put("titleSource", "CARD")
        put("imageSource", "UNKNOWN")
        put("originalIndex", index)
    }
    private fun response(index: Int, count: Int = 16,
                         more: Boolean = true) = JSONObject().apply {
        put("schemaVersion", 1)
        put("event", "results")
        put("documentId", doc)
        put("requestId", request)
        put("status", "ok")
        put("films", JSONArray().put(film(index)))
        put("inspectedCards", count)
        put("moreOnPage", more)
    }

    @Test fun commandUsesAbsoluteCardOffsetNotFilmCount() {
        val json = VideoSearchBridgeProtocol.extractBatch(doc, request, config, 32)
        assertEquals("extract_batch", json.getString("event"))
        assertEquals(32, json.getInt("startIndex"))
        assertEquals(".movie-card", json.getString("resultsCardSelector"))
        assertEquals("h2.title", json.getString("titleSelector"))
        assertEquals(10, json.length())
        assertFalse(json.has("filmCount"))
    }

    @Test fun fiveValidWindowCursorsAndNoSixth() {
        for (offset in listOf(0, 16, 32, 48, 64)) {
            assertEquals(offset,
                VideoSearchBridgeProtocol.extractBatch(doc, request, config, offset)
                    .getInt("startIndex"))
        }
        for (invalid in listOf(-1, 1, 15, 17, 65, 80, 1000)) {
            try {
                VideoSearchBridgeProtocol.extractBatch(doc, request, config, invalid)
                fail("Bad cursor accepted: $invalid")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun chunkDoesNotContainUrlQueryTabProfileOrPrivateSession() {
        val json = VideoSearchBridgeProtocol.extractBatch(doc, request, config, 16)
        for (key in listOf("url", "pageUrl", "query", "tabId", "profileId",
            "privateSessionId", "cookies", "searchPhrase", "resultLimit"))
            assertFalse(json.has(key))
    }

    @Test fun legacyExtractCommandStillHasNoStartIndex() {
        val legacy = VideoSearchBridgeProtocol.extract(doc, request, ".movie-card")
        assertEquals("extract", legacy.getString("event"))
        assertEquals(4, legacy.length())
        assertFalse(legacy.has("startIndex"))
    }

    @Test fun continuationResponsePreservesAbsoluteCardIndex() {
        val result = VideoSearchBridgeProtocol.decode(response(34))
            as VideoSearchBridgeMessage.Results
        assertEquals(34, result.page.films.single().originalIndex)
        assertEquals(16, result.page.inspectedCards)
        assertTrue(result.page.moreOnPage)
    }

    @Test fun emptyWindowCanStillIndicateLaterResults() {
        val msg = response(16).apply {
            put("status", "empty")
            put("films", JSONArray())
        }
        val result = VideoSearchBridgeProtocol.decode(msg)
            as VideoSearchBridgeMessage.Results
        assertTrue(result.page.films.isEmpty())
        assertTrue(result.page.moreOnPage)
        assertEquals(16, result.page.inspectedCards)
    }

    @Test fun protocolRejectsMoreThanSixteenFilmsInOneWindow() {
        val payload = response(0)
        val list = JSONArray()
        for (index in 0..16) list.put(film(index))
        payload.put("films", list)
        assertNull(VideoSearchBridgeProtocol.decode(payload))
    }

    @Test fun protocolRejectsInjectedProfileAndTabFields() {
        val base = response(0)
        for (key in listOf("profileId", "tabId", "isPrivate", "session",
            "cookie", "html", "query")) {
            assertNull(VideoSearchBridgeProtocol.decode(
                JSONObject(base.toString()).put(key, "forged")
            ))
        }
    }

    @Test fun paginationChoiceDoesNotEnterChunkCommand() {
        val json = VideoSearchBridgeProtocol.extractBatch(doc, request, config, 0)
        assertFalse(json.has("nextPageSelector"))
        assertFalse(json.has("pagination"))
        assertFalse(json.has("navigate"))
    }

    @Test fun missingMetaFieldsRemainNullWithoutPageTitleFallback() {
        val payload = response(1)
        val item = payload.getJSONArray("films").getJSONObject(0)
        item.put("originalTitle", JSONObject.NULL)
        item.put("titleSource", "UNKNOWN")
        val result = VideoSearchBridgeProtocol.decode(payload)
            as VideoSearchBridgeMessage.Results
        assertNull(result.page.films.single().originalTitle)
    }
}
