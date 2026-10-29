package com.prirai.android.nira.videosearch

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stage 4D: strict pagination commands and extended 320-card crawl results. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchPaginationProtocolTest {
    private val doc = "1234567890abcdef1234567890abcdef"
    private val req = "abcdef1234567890abcdef1234567890"
    private fun config(mode: VideoSearchPagination) = VideoSiteConfiguration(
        "input[name=\"q\"]", ".film-card",
        pagination = mode,
        nextPageSelector = when (mode) {
            VideoSearchPagination.NEXT_LINK -> "a.next"
            VideoSearchPagination.LOAD_MORE -> "button.more"
            else -> null
        },
    )
    private fun action(status: String = "dispatched", mode: String = "NEXT_LINK") =
        JSONObject().apply {
            put("schemaVersion", 1)
            put("event", "page_action")
            put("documentId", doc)
            put("requestId", req)
            put("status", status)
            put("pagination", mode)
        }
    private fun films(index: Int, event: String = "crawl_results") = JSONObject().apply {
        put("schemaVersion", 1)
        put("event", event)
        put("documentId", doc)
        put("requestId", req)
        put("status", "ok")
        put("films", JSONArray().put(JSONObject().apply {
            put("filmPageUrl", "https://films.example/watch?id=$index")
            put("originalTitle", "Film $index")
            put("thumbnailUrl", JSONObject.NULL)
            put("durationSeconds", JSONObject.NULL)
            put("titleSource", "CARD")
            put("imageSource", "UNKNOWN")
            put("originalIndex", index)
        }))
        put("inspectedCards", 1)
        put("moreOnPage", false)
    }

    @Test fun nextLinkCommandHasExactlyExpectedFields() {
        val msg = VideoSearchBridgeProtocol.paginate(doc, req, config(VideoSearchPagination.NEXT_LINK))
        assertEquals("paginate", msg.getString("event"))
        assertEquals("NEXT_LINK", msg.getString("pagination"))
        assertEquals("a.next", msg.getString("nextPageSelector"))
        assertEquals(6, msg.length())
    }

    @Test fun loadMoreUsesTheConfiguredButtonOnly() {
        val msg = VideoSearchBridgeProtocol.paginate(doc, req, config(VideoSearchPagination.LOAD_MORE))
        assertEquals("LOAD_MORE", msg.getString("pagination"))
        assertEquals("button.more", msg.getString("nextPageSelector"))
    }

    @Test fun infiniteScrollDoesNotAcceptAnArbitraryClickSelector() {
        val msg = VideoSearchBridgeProtocol.paginate(doc, req,
            config(VideoSearchPagination.INFINITE_SCROLL))
        assertEquals("INFINITE_SCROLL", msg.getString("pagination"))
        assertTrue(msg.isNull("nextPageSelector"))
        val invalid = config(VideoSearchPagination.INFINITE_SCROLL)
            .copy(nextPageSelector = "a.next")
        try {
            VideoSearchBridgeProtocol.paginate(doc, req, invalid)
            fail("Scroll should not receive a click target")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun paginationNoneCannotTriggerAnyAction() {
        try {
            VideoSearchBridgeProtocol.paginate(doc, req, config(VideoSearchPagination.NONE))
            fail("No pagination configured")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun pageActionAckAcceptsOnlyKnownStatusesAndTypes() {
        for (status in listOf("dispatched", "pending", "end", "unavailable", "unsafe")) {
            val parsed = VideoSearchBridgeProtocol.decode(action(status))
                as VideoSearchBridgeMessage.PageAction
            assertEquals(status, parsed.status)
            assertEquals(VideoSearchPagination.NEXT_LINK, parsed.pagination)
        }
        assertNull(VideoSearchBridgeProtocol.decode(action("completed")))
        assertNull(VideoSearchBridgeProtocol.decode(action(mode = "POST")))
        assertNull(VideoSearchBridgeProtocol.decode(action(mode = "NONE")))
    }

    @Test fun explicitEmptyResultHasStrictZeroCardProtocol() {
        val base = films(0)
        base.put("status", "no_results")
        base.put("films", JSONArray())
        base.put("inspectedCards", 0)
        val page = (VideoSearchBridgeProtocol.decode(base)
            as VideoSearchBridgeMessage.Results).page
        assertEquals("no_results", page.status)
        assertTrue(page.films.isEmpty())
        assertFalse(page.moreOnPage)
        assertNull(VideoSearchBridgeProtocol.decode(
            JSONObject(base.toString()).put("moreOnPage", true)))
        assertNull(VideoSearchBridgeProtocol.decode(
            JSONObject(base.toString()).put("inspectedCards", 1)))
        assertNull(VideoSearchBridgeProtocol.decode(
            JSONObject(base.toString()).put("films", films(0).getJSONArray("films"))))
    }

    @Test fun rejectsPageActionProfileForgeryAndExtraUrls() {
        val valid = action()
        for (key in listOf("profileId", "tabId", "isPrivate",
            "url", "query", "cookie", "password", "host", "html")) {
            assertNull(VideoSearchBridgeProtocol.decode(
                JSONObject(valid.toString()).put(key, "forged")))
        }
    }

    @Test fun crawlCommandUsesExactCardCursorIncludingNonAlignedOffsets() {
        val msg = VideoSearchBridgeProtocol.extractCrawl(doc, req,
            config(VideoSearchPagination.NONE), 19)
        assertEquals("extract_crawl", msg.getString("event"))
        assertEquals(19, msg.getInt("startIndex"))
        assertEquals(10, msg.length())
        assertFalse(msg.has("pageUrl"))
        assertFalse(msg.has("query"))
    }

    @Test fun crawlCursorAccepts319ButRejects320AndNegative() {
        assertEquals(319, VideoSearchBridgeProtocol.extractCrawl(doc, req,
            config(VideoSearchPagination.NONE), 319).getInt("startIndex"))
        for (index in listOf(-1, 320, 512)) {
            try {
                VideoSearchBridgeProtocol.extractCrawl(doc, req,
                    config(VideoSearchPagination.NONE), index)
                fail("Accepted invalid cursor $index")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun crawlReplyCanContainFilmAtIndex319() {
        val result = VideoSearchBridgeProtocol.decode(films(319))
            as VideoSearchBridgeMessage.Results
        assertEquals("crawl_results", result.sourceEvent)
        assertEquals(319, result.page.films.single().originalIndex)
    }

    @Test fun legacyResultsStillRejectIndexOver200() {
        assertNull(VideoSearchBridgeProtocol.decode(films(201, event = "results")))
        val old = VideoSearchBridgeProtocol.decode(films(200, event = "results"))
            as VideoSearchBridgeMessage.Results
        assertEquals("results", old.sourceEvent)
    }

    @Test fun rejectsUnknownExtendedResultFields() {
        val result = films(17)
        assertNull(VideoSearchBridgeProtocol.decode(
            JSONObject(result.toString()).put("origin", "https://other.example")))
        assertNull(VideoSearchBridgeProtocol.decode(
            JSONObject(result.toString()).put("profileId", "work")))
    }

    @Test fun resultEnvelopeCannotClaim200FilmsInOneNativeMessage() {
        val msg = films(0)
        val array = JSONArray()
        for (i in 0..16)
            array.put(msg.getJSONArray("films").getJSONObject(0))
        msg.put("films", array)
        assertNull(VideoSearchBridgeProtocol.decode(msg))
    }

    @Test fun paginationCommandNeverIncludesSearchPhraseOrProfile() {
        val msg = VideoSearchBridgeProtocol.paginate(doc, req, config(VideoSearchPagination.NEXT_LINK))
        for (key in listOf("searchPhrase", "query", "profileId", "tabId", "cookies",
            "privateSessionId", "runId", "password", "siteUrl"))
            assertFalse(msg.has(key))
    }
}
