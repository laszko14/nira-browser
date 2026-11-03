package com.prirai.android.nira.videosearch

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stage 3A protocol boundary tests. No GeckoView runtime is created here. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchBridgeProtocolTest {
    private val validId = "0123456789abcdef0123456789abcdef"
    private val validUrl = "https://example.org/search?query=foo&session=signed-value"

    private fun hello(
        id: String = validId,
        url: String = validUrl,
    ) = JSONObject().apply {
        put("schemaVersion", 1)
        put("event", "hello")
        put("documentId", id)
        put("pageUrl", url)
    }

    @Test fun acceptsExactlyTypedVersionOneHelloWithUnmodifiedUrl() {
        val packet = VideoSearchBridgeProtocol.decode(hello())
        assertTrue(packet is VideoSearchBridgeMessage.Hello)
        val accepted = packet as VideoSearchBridgeMessage.Hello
        assertEquals(validUrl, accepted.pageUrl)
        assertEquals(validId, accepted.documentId)
        assertFalse(accepted.toString().contains("signed-value"))
    }

    @Test fun rejectsSpoofedProfileTabSiteAndPrivateFields() {
        for (key in listOf("profileId", "tabId", "siteId", "privateSessionId",
            "isPrivate", "cookies", "query", "title", "token", "host")) {
            assertNull(key, VideoSearchBridgeProtocol.decode(
                hello().apply { put(key, "forged") }
            ))
        }
    }

    @Test fun rejectsWrongEventVersionFieldTypesAndExtraKeys() {
        assertNull(VideoSearchBridgeProtocol.decode(null))
        assertNull(VideoSearchBridgeProtocol.decode("hello"))
        assertNull(VideoSearchBridgeProtocol.decode(JSONObject()))
        val invalid = listOf(
            hello().apply { put("schemaVersion", 2) },
            hello().apply { put("schemaVersion", "1") },
            hello().apply { put("event", "results") },
            hello().apply { put("pageUrl", 123) },
            hello().apply { put("documentId", false) },
            hello().apply { remove("pageUrl") },
            hello().apply { put("payload", JSONObject().put("a", 1)) },
        )
        for (input in invalid) assertNull(VideoSearchBridgeProtocol.decode(input))
    }

    @Test fun rejectsWeakOrIllFormedDocumentIds() {
        for (bad in listOf("", "0", "0".repeat(31), "0".repeat(33),
            "A".repeat(32), "../private", "g".repeat(32))) {
            assertNull(bad, VideoSearchBridgeProtocol.decode(hello(id = bad)))
        }
    }

    @Test fun rejectsNonWebAndCredentialBearingUrls() {
        for (url in listOf("", "file:///sdcard/video.mp4", "about:blank",
            "blob:https://example.org/123", "javascript:alert(1)", "/search",
            "https://user:password@example.org/", "https://example.org/bad path")) {
            assertNull(url, VideoSearchBridgeProtocol.decode(hello(url = url)))
        }
    }

    @Test fun rejectsOversizedMessagesAndDoesNotLogSensitivePayloads() {
        assertNull(VideoSearchBridgeProtocol.decode(
            hello(url = "https://example.org/" + "z".repeat(10_000))
        ))
    }

    @Test fun acceptsOnlyMinimalPongWithMatchingNonceShape() {
        val packet = JSONObject().apply {
            put("schemaVersion", 1)
            put("event", "pong")
            put("documentId", validId)
        }
        assertTrue(VideoSearchBridgeProtocol.decode(packet) is VideoSearchBridgeMessage.Pong)
        assertNull(VideoSearchBridgeProtocol.decode(packet.apply { put("tabId", "fake") }))
        assertNull(VideoSearchBridgeProtocol.decode(packet.apply { put("pageUrl", validUrl) }))
    }

    @Test fun boundAndPingContainNoTabUrlProfileOrSearchDetails() {
        for (packet in listOf(
            VideoSearchBridgeProtocol.bound(validId),
            VideoSearchBridgeProtocol.ping(validId),
        )) {
            assertEquals(3, packet.length())
            assertEquals(1, packet.getInt("schemaVersion"))
            assertEquals(validId, packet.getString("documentId"))
            assertFalse(packet.toString().contains(validUrl))
            assertFalse(packet.toString().contains("profileId"))
            assertFalse(packet.toString().contains("tabId"))
        }
    }

    @Test fun assetManifestGrantsOnlyNativeTransportPermissions() {
        // Robolectric manifest disabled but raw APK assets remain available.
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val manifest = context.assets.open(
            "extensions/zavro-video-search/manifest.json"
        ).bufferedReader().use { it.readText() }
        val json = JSONObject(manifest)
        assertEquals(2, json.getInt("manifest_version"))
        assertEquals(VideoSearchBridgeProtocol.EXTENSION_ID,
            json.getJSONObject("browser_specific_settings")
                .getJSONObject("gecko").getString("id"))
        val perms = json.getJSONArray("permissions")
        assertEquals(3, perms.length())
        val expected = setOf("nativeMessaging", "nativeMessagingFromContent", "geckoViewAddons")
        assertEquals(expected, (0 until perms.length()).map { perms.getString(it) }.toSet())
        val content = json.getJSONArray("content_scripts").getJSONObject(0)
        assertFalse(content.getBoolean("all_frames"))
        assertEquals("document_idle", content.getString("run_at"))
    }

    private fun siteAnalysisPacket() = JSONObject().apply {
        put("schemaVersion", 1)
        put("event", "analysis")
        put("documentId", validId)
        put("requestId", "a".repeat(32))
        put("status", "partial")
        put("stage", "SEARCH_FORM")
        put("reason", "search_only")
        put("confidence", 75)
        put("evidence", org.json.JSONArray().put("GET_FORM").put("SEARCH_INPUT"))
        put("inputSelector", "input[name=\"q\"]")
        put("resultsCardSelector", JSONObject.NULL)
        put("submission", "FORM_SUBMIT")
        put("submitSelector", JSONObject.NULL)
        put("pagination", "NONE")
        put("nextPageSelector", JSONObject.NULL)
    }

    @Test fun P0AcceptsBoundedRedactedSiteAnalysis() {
        val command = VideoSearchBridgeProtocol.analyze(validId, "a".repeat(32))
        assertEquals("analyze", command.getString("event"))
        assertFalse(command.has("siteUrl"))
        val answer = VideoSearchBridgeProtocol.decode(siteAnalysisPacket())
        assertTrue(answer is VideoSearchBridgeMessage.Analysis)
        val analysis = answer as VideoSearchBridgeMessage.Analysis
        assertEquals(VideoSearchAnalysisStage.SEARCH_FORM, analysis.stage)
        assertEquals(VideoSearchAnalysisStatus.PARTIAL, analysis.status)
        assertEquals("search_only", analysis.reason)
        assertNotNull(analysis.formInspection())
        assertNull(analysis.resultsCardSelector)
        assertFalse(analysis.toString().contains("input[name"))
    }

    @Test fun P0RejectsInjectedOrIncoherentAnalysis() {
        for (invalid in listOf(
            siteAnalysisPacket().put("cookie", "secret"),
            siteAnalysisPacket().put("evidence", org.json.JSONArray().put("RAW_HTML")),
            siteAnalysisPacket().put("confidence", 120),
            siteAnalysisPacket().put("resultsCardSelector", "a[href]").put("status", "partial"),
            siteAnalysisPacket().put("status", "ready"),
            siteAnalysisPacket().put("submission", "POST"),
            siteAnalysisPacket().put("requestId", "wrong"),
        )) assertNull(VideoSearchBridgeProtocol.decode(invalid))
    }

}
