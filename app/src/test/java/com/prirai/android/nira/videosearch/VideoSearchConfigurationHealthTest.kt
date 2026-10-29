package com.prirai.android.nira.videosearch

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Stage 3D: saved-selector validation and opt-in, non-destructive repair. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchConfigurationHealthTest {
    private val owner = VideoSearchScope.Normal("default")
    private val foreign = VideoSearchScope.Normal("work")
    private val doc = "0123456789abcdef0123456789abcdef"
    private val request = "fedcba9876543210fedcba9876543210"

    private fun database() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(), VideoSearchDatabase::class.java
    ).build()

    private fun validation(
        status: String = "valid", reason: String = "ok",
        attempts: Int = 1, matched: Int = 2
    ): VideoSearchBridgeMessage.Validation =
        VideoSearchBridgeMessage.Validation(doc, request, status, reason, attempts, matched)

    private fun parsed(
        status: String = "valid", reason: String = "ok",
        attempts: Int = 1, cards: Int = 2
    ): VideoSearchBridgeMessage? =
        VideoSearchBridgeProtocol.decode(JSONObject().apply {
            put("schemaVersion", 1)
            put("event", "validation")
            put("documentId", doc)
            put("requestId", request)
            put("status", status)
            put("reason", reason)
            put("attempts", attempts)
            put("matchedCards", cards)
        })

    private fun inspection(): VideoSearchBridgeMessage.Inspection =
        VideoSearchBridgeMessage.Inspection(
            doc, request, "ready", "input.search2", ".card-new",
            VideoSearchSubmission.FORM_SUBMIT, null, VideoSearchPagination.NONE, null
        )

    private fun configured() = VideoSiteConfiguration("input.search", ".card-old",
        submission = VideoSearchSubmission.FORM_SUBMIT)

    @Test fun validationMessageIsTypedBoundedAndRedacted() {
        val value = parsed() as VideoSearchBridgeMessage.Validation
        assertEquals("valid", value.status)
        assertEquals("ok", value.reason)
        assertEquals(1, value.attempts)
        assertEquals(2, value.matchedCards)
        assertFalse(value.toString().contains(doc))
    }

    @Test fun unverifiedMissingResultsCannotClaimStaleOrValid() {
        val value = parsed("unverified", "no_results", 3, 0)
        assertTrue(value is VideoSearchBridgeMessage.Validation)
        assertNull(parsed("valid", "no_results", 2, 0))
        assertNull(parsed("unverified", "input_missing", 3, 0))
        assertNull(parsed("stale", "no_results", 3, 0))
    }

    @Test fun protocolRejectsSpoofedFieldsAndInvalidAttempts() {
        val packet = JSONObject().apply {
            put("schemaVersion", 1)
            put("event", "validation")
            put("documentId", doc)
            put("requestId", request)
            put("status", "valid")
            put("reason", "ok")
            put("attempts", 1)
            put("matchedCards", 2)
        }
        for (field in listOf("tabId", "profileId", "isPrivate", "cookie",
            "url", "title", "query", "token", "selector")) {
            assertNull(VideoSearchBridgeProtocol.decode(
                JSONObject(packet.toString()).put(field, "forged")
            ))
        }
        assertNull(VideoSearchBridgeProtocol.decode(
            JSONObject(packet.toString()).put("attempts", 0)))
        assertNull(VideoSearchBridgeProtocol.decode(
            JSONObject(packet.toString()).put("attempts", 4)))
        assertNull(VideoSearchBridgeProtocol.decode(
            JSONObject(packet.toString()).put("attempts", "3")))
        assertNull(VideoSearchBridgeProtocol.decode(
            JSONObject(packet.toString()).put("matchedCards", 31)))
        assertNull(VideoSearchBridgeProtocol.decode(
            JSONObject(packet.toString()).put("matchedCards", -1)))
    }

    @Test fun acceptsOnlyConsistentReasonAndStatusPairs() {
        assertNotNull(parsed("stale", "input_missing", 3, 0))
        assertNotNull(parsed("stale", "input_ambiguous", 1, 0))
        assertNotNull(parsed("unsafe", "unsafe_form", 1, 0))
        assertNotNull(parsed("unverified", "pagination_unknown", 2, 1))
        assertNull(parsed("unsafe", "ok", 1, 0))
        assertNull(parsed("valid", "unsafe_form", 1, 2))
    }

    @Test fun outboundReadOnlyValidationContainsNoQueryOrProfile() {
        val command = VideoSearchBridgeProtocol.validate(doc, request, configured())
        assertEquals("validate", command.getString("event"))
        assertEquals("input.search", command.getString("inputSelector"))
        assertEquals(".card-old", command.getString("resultsCardSelector"))
        assertFalse(command.has("query"))
        assertFalse(command.has("profileId"))
        assertFalse(command.has("tabId"))
    }

    @Test fun outboundPartialConfigurationUsesExplicitNull() {
        val partial = VideoSiteConfiguration(
            "input.search", resultsCardSelector = null,
            submission = VideoSearchSubmission.FORM_SUBMIT)
        val command = VideoSearchBridgeProtocol.validate(doc, request, partial)
        assertTrue(command.isNull("resultsCardSelector"))
        assertTrue(command.isNull("nextPageSelector"))
        assertTrue(command.isNull("submitSelector"))
        assertEquals("NONE", command.getString("pagination"))
    }

    @Test fun normalAutoDetectionCannotOverrideExistingGoodSelectors() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val site = repo.addSite(owner, "https://example.org", nowMs = 1, newId = "a")
            assertTrue(repo.applyManualConfiguration(site, configured(), nowMs = 2))
            val saved = repo.site(owner, "a")!!
            assertFalse(repo.applyAutoConfiguration(saved, inspection(), nowMs = 3))
            assertEquals(configured(), repo.site(owner, "a")?.configuration)
            assertEquals(VideoSiteConfigurationState.CONFIGURED,
                repo.site(owner, "a")?.configurationState)
        } finally { db.close() }
    }

    @Test fun explicitConfirmedRepairReplacesOnlyCurrentVersion() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val site = repo.addSite(owner, "https://example.org", nowMs = 1, newId = "a")
            assertTrue(repo.applyManualConfiguration(site, configured(), nowMs = 2))
            val before = repo.site(owner, "a")!!
            assertTrue(repo.applyAutoConfiguration(before, inspection(),
                nowMs = 3, confirmedReplace = true))
            assertEquals("input.search2",
                repo.site(owner, "a")?.configuration?.searchInputSelector)
            assertFalse(repo.applyAutoConfiguration(before, inspection(),
                nowMs = 4, confirmedReplace = true))
        } finally { db.close() }
    }

    @Test fun noResultsAndNoConsentCannotFlagAHealthySite() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val site = repo.addSite(owner, "https://example.org", nowMs = 1, newId = "a")
            assertTrue(repo.applyManualConfiguration(site, configured(), nowMs = 2))
            val configuredSite = repo.site(owner, "a")!!
            assertFalse(repo.flagValidatedBrokenConfiguration(configuredSite,
                validation("unverified", "no_results", 3, 0), true, nowMs = 3))
            assertFalse(repo.flagValidatedBrokenConfiguration(configuredSite,
                validation("stale", "input_missing", 3, 0), false, nowMs = 3))
            assertEquals(configured(), repo.site(owner, "a")?.configuration)
            assertEquals(VideoSiteConfigurationState.CONFIGURED,
                repo.site(owner, "a")?.configurationState)
        } finally { db.close() }
    }

    @Test fun explicitlyMarkedBrokenKeepsOriginalSelectorsAndSite() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val site = repo.addSite(owner, "https://example.org", nowMs = 1, newId = "a")
            repo.applyManualConfiguration(site, configured(), nowMs = 2)
            val good = repo.site(owner, "a")!!
            assertTrue(repo.flagValidatedBrokenConfiguration(good,
                validation("stale", "input_missing", 3, 0), userConfirmed = true, nowMs = 3))
            val broken = repo.site(owner, "a")!!
            assertEquals(VideoSiteConfigurationState.REPAIR_REQUIRED, broken.configurationState)
            assertEquals(configured(), broken.configuration)
            assertEquals(1, repo.sites(owner).size)
        } finally { db.close() }
    }

    @Test fun concurrentEditDeletionAndOtherProfileRemainProtected() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val a = repo.addSite(owner, "https://example.org", nowMs = 1, newId = "same")
            val other = repo.addSite(foreign, "https://example.org", nowMs = 1, newId = "same")
            repo.applyManualConfiguration(a, configured(), nowMs = 2)
            repo.applyManualConfiguration(other, configured(), nowMs = 2)
            val stale = repo.site(owner, "same")!!
            repo.updateSiteDetails(owner, "same", "Zmieniona", 360, nowMs = 3)
            assertFalse(repo.flagValidatedBrokenConfiguration(stale,
                validation("unsafe", "unsafe_form", 1, 0), true, nowMs = 4))
            assertFalse(repo.applyAutoConfiguration(stale, inspection(),
                nowMs = 4, confirmedReplace = true))
            assertEquals(VideoSiteConfigurationState.CONFIGURED,
                repo.site(foreign, "same")?.configurationState)
            assertTrue(repo.removeSite(owner, "same", confirmed = true))
            assertFalse(repo.flagValidatedBrokenConfiguration(stale,
                validation("stale", "input_missing", 3, 0), true, nowMs = 4))
        } finally { db.close() }
    }

    @Test fun alreadyPartialManualConfigurationIsNotSilentlyReplaced() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val site = repo.addSite(owner, "https://example.org", nowMs = 1, newId = "a")
            val partial = VideoSiteConfiguration("input.search", null)
            assertTrue(repo.applyManualConfiguration(site, partial, nowMs = 2))
            val current = repo.site(owner, "a")!!
            assertFalse(repo.applyAutoConfiguration(current, inspection(), nowMs = 3))
            assertEquals(partial, repo.site(owner, "a")?.configuration)
        } finally { db.close() }
    }
}
