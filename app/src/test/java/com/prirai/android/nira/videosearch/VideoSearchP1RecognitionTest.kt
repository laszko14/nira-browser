package com.prirai.android.nira.videosearch

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchP1RecognitionTest {
    private fun packet(selector: Any = ".results article.card"): JSONObject =
        JSONObject().put("schemaVersion", 1).put("event", "recognition")
            .put("documentId", "a".repeat(32)).put("requestId", "b".repeat(32))
            .put("status", "found").put("reason", "CANDIDATE_FOUND")
            .put("confidence", 91).put("resultsCardSelector", selector)
            .put("matchedCards", 3).put("scannedElements", 210)
            .put("surface", "MAIN")

    @Test fun acceptsOnlyBoundedRecognitionEvidence() {
        val parsed = VideoSearchBridgeProtocol.decode(packet())
                as VideoSearchBridgeMessage.Recognition
        assertEquals(91, parsed.confidence)
        assertEquals(210, parsed.scannedElements)
        assertEquals(".results article.card", parsed.resultsCardSelector)
        assertFalse(parsed.toString().contains(".results"))
    }

    @Test fun rejectsCrossProfileForgedUnsafeOrUnboundedEvidence() {
        assertNull(VideoSearchBridgeProtocol.decode(packet().put("profileId", "foreign")))
        assertNull(VideoSearchBridgeProtocol.decode(packet().put("scannedElements", 9999)))
        assertNull(VideoSearchBridgeProtocol.decode(packet().put("confidence", 101)))
        assertNull(VideoSearchBridgeProtocol.decode(packet().put("surface", "FRAME")))
        assertNull(VideoSearchBridgeProtocol.decode(packet().put("resultsCardSelector", "a:has(script)")))
        assertNull(VideoSearchBridgeProtocol.decode(packet().put("requestId", "invalid")))
        assertNull(VideoSearchBridgeProtocol.decode(packet().put("reason", "any")))
    }


    @Test fun scopedCardLocatorRequiresSurfaceAndCannotForgeMainCss() {
        // An ASCII base64url locator: S~#playerResults~article.video-card.
        val scoped = "zcs1:U34jcGxheWVyUmVzdWx0c35hcnRpY2xlLnZpZGVvLWNhcmQ"
        assertTrue(VideoSearchBridgeProtocol.validSelector(scoped))
        val parsed = VideoSearchBridgeProtocol.decode(
            packet(scoped).put("surface", "SHADOW")
        ) as VideoSearchBridgeMessage.Recognition
        assertEquals(scoped, parsed.resultsCardSelector)
        assertEquals("SHADOW", parsed.surface)
        assertNull(VideoSearchBridgeProtocol.decode(packet(scoped)))
        assertNull(VideoSearchBridgeProtocol.decode(
            packet().put("surface", "FRAME")
        ))
        assertFalse(VideoSearchBridgeProtocol.validSelector("zcs1:bad/escape"))
        assertFalse(VideoSearchBridgeProtocol.validSelector("zcs1:"))
        assertFalse(VideoSearchBridgeProtocol.validSelector("zcs1:" + "a".repeat(160)))
    }

    @Test fun p2eWeakAndReplacedFrameEvidenceIsNonRepairable() {
        val weak = packet(JSONObject.NULL).put("status", "partial")
            .put("reason", "LOW_CONFIDENCE").put("confidence", 78)
        val parsed = VideoSearchBridgeProtocol.decode(weak)
            as VideoSearchBridgeMessage.Recognition
        assertEquals("LOW_CONFIDENCE", parsed.reason)
        assertNull(parsed.resultsCardSelector)
        assertNull(VideoSearchRecognitionPolicy.repairCandidate(parsed, null))

        val replaced = packet(JSONObject.NULL).put("status", "missing")
            .put("reason", "FRAME_REPLACED").put("confidence", 0)
            .put("matchedCards", 0).put("surface", "FRAME")
        val stale = VideoSearchBridgeProtocol.decode(replaced)
            as VideoSearchBridgeMessage.Recognition
        assertEquals("FRAME_REPLACED", stale.reason)
        assertNull(VideoSearchRecognitionPolicy.repairCandidate(stale, null))
        val limited = VideoSearchBridgeProtocol.decode(
            packet(JSONObject.NULL).put("status", "missing")
                .put("reason", "SCAN_LIMIT").put("confidence", 0)
                .put("matchedCards", 0)
        ) as VideoSearchBridgeMessage.Recognition
        assertNull(VideoSearchRecognitionPolicy.repairCandidate(limited, null))
        assertNull(VideoSearchBridgeProtocol.decode(
            packet().put("reason", "LOW_CONFIDENCE")
        ))
        assertTrue(VideoSearchRecognitionDiagnostics.explain(stale).contains("podmieniona"))
    }

    @Test fun readOnlyOutboundRecognitionIsMinimal() {
        val packet = VideoSearchBridgeProtocol.recognize("a".repeat(32), "b".repeat(32))
        assertEquals(4, packet.length())
        assertEquals("recognize", packet.getString("event"))
        assertFalse(packet.toString().contains("profile"))
        assertFalse(packet.toString().contains("query"))
    }

    @Test fun oldConfigurationJsonRemainsReadable() {
        val legacy = JSONObject().put("input", "#q").put("cards", "article.card")
            .put("submission", "FORM_SUBMIT").put("submit", JSONObject.NULL)
            .put("title", JSONObject.NULL).put("link", JSONObject.NULL)
            .put("image", JSONObject.NULL).put("duration", JSONObject.NULL)
            .put("pagination", "NONE").put("next", JSONObject.NULL).toString()
        val config = VideoSearchRowCodec.readConfiguration(legacy)!!
        assertEquals(VideoSearchRecognitionMemory(), config.recognition)
        assertEquals("article.card", config.resultsCardSelector)
    }

    @Test fun verifiedEvidenceIsRoundTrippedWithoutSchemaMigration() {
        val initial = VideoSiteConfiguration("#q", null, VideoSearchSubmission.FORM_SUBMIT)
        val once = VideoSearchRecognitionPolicy.verified(initial, "article.card", 100)!!
        val twice = VideoSearchRecognitionPolicy.verified(once, "article.card", 110)!!
        assertEquals(2, twice.recognition.confirmations)
        assertEquals(VideoSearchRuleOrigin.VERIFIED, twice.recognition.origin)
        val encoded = VideoSearchRowCodec.configurationJson(twice)
        assertEquals(twice, VideoSearchRowCodec.readConfiguration(encoded))
    }

    @Test fun manualRulesAreNeverSilentlyReplacedByAlternatives() {
        val manual = VideoSiteConfiguration("#q", "article.original",
            recognition = VideoSearchRecognitionMemory(origin = VideoSearchRuleOrigin.MANUAL))
        val verified = VideoSearchRecognitionPolicy.verified(manual, "article.candidate", 100)!!
        assertEquals("article.original", verified.resultsCardSelector)
        assertEquals(VideoSearchRuleOrigin.MANUAL, verified.recognition.origin)
        assertEquals(listOf("article.candidate"),
            VideoSearchRecognitionPolicy.fallbackCandidates(verified))
    }

    @Test fun invalidFallbackMetadataDoesNotDeleteWorkingSelectors() {
        val json = JSONObject(VideoSearchRowCodec.configurationJson(
            VideoSiteConfiguration("#q", "article.card"))!!)
        val malformed = JSONObject().put("origin", "VERIFIED")
            .put("confidence", 90).put("confirmations", 3)
            .put("backups", JSONArray().put("script:has(input)"))
        json.put("recognition", malformed)
        val parsed = VideoSearchRowCodec.readConfiguration(json.toString())!!
        assertEquals("article.card", parsed.resultsCardSelector)
        assertEquals(VideoSearchRecognitionMemory(), parsed.recognition)
    }
    @Test fun confirmedEvidenceIsAtomicAndNeverOverwritesStaleEdits() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), VideoSearchDatabase::class.java
        ).build()
        try {
            val scope = VideoSearchScope.Normal("normal")
            val other = VideoSearchScope.Normal("other")
            val repo = VideoSearchRepository(db.dao())
            val first = repo.addSite(scope, "https://films.example", newId="site", nowMs=1)
            val config = VideoSiteConfiguration("#q", "article.original",
                VideoSearchSubmission.FORM_SUBMIT)
            assertTrue(repo.applyManualConfiguration(first, config, nowMs=2))
            val saved = repo.site(scope, "site")!!
            assertTrue(repo.rememberConfirmedResults(saved, "article.candidate", nowMs=3))
            val new = repo.site(scope, "site")!!
            assertEquals("article.original", new.configuration?.resultsCardSelector)
            assertEquals(listOf("article.candidate"),
                new.configuration?.recognition?.backupResultsSelectors)
            assertFalse(repo.rememberConfirmedResults(saved, "article.malicious", nowMs=4))
            assertEquals(new, repo.site(scope, "site"))
            assertNull(repo.site(other, "site"))
        } finally { db.close() }
    }

    @Test fun bareInspectionDoesNotFabricateVerifiedSearchEvidence() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), VideoSearchDatabase::class.java
        ).build()
        try {
            val owner = VideoSearchScope.Normal("normal")
            val repo = VideoSearchRepository(db.dao())
            val saved = repo.addSite(owner, "https://films.example", newId = "raw", nowMs = 1)
            val seen = VideoSearchBridgeMessage.Inspection(
                "a".repeat(32), "b".repeat(32), "ready", "#q", "article.card",
                VideoSearchSubmission.FORM_SUBMIT, null, VideoSearchPagination.NONE, null
            )
            assertTrue(repo.applyAutoConfiguration(saved, seen, nowMs = 2))
            assertEquals(0, repo.site(owner, saved.id)?.configuration?.recognition?.confirmations)
            val next = repo.site(owner, saved.id)!!
            assertTrue(repo.rememberConfirmedResults(next, "article.card", nowMs = 3))
            assertEquals(1, repo.site(owner, saved.id)?.configuration?.recognition?.confirmations)
        } finally { db.close() }
    }
}
