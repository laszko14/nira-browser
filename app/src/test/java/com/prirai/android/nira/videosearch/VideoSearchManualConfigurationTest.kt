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

/** ETAP 3C — protocol and durable partial/complete manual selector fixtures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchManualConfigurationTest {
    private val doc = "0123456789abcdef0123456789abcdef"
    private val request = "fedcba9876543210fedcba9876543210"
    private val owner = VideoSearchScope.Normal("default")

    private fun reply(event: String, kind: String) = JSONObject().apply {
        put("schemaVersion", 1)
        put("event", event)
        put("documentId", doc)
        put("requestId", request)
        put("pickKind", kind)
    }

    private fun picked(
        kind: String = "input",
        selector: String = "input[name=\"q\"]",
        formSafe: Boolean = kind == "input",
        pagination: String = "NONE",
    ) = reply("picked", kind).apply {
        put("selector", selector)
        put("formSafe", formSafe)
        put("pagination", pagination)
    }

    private fun db() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(), VideoSearchDatabase::class.java
    ).build()

    @Test fun validInputPickContainsOnlySelectorAndSafeFormFlag() {
        val parsed = VideoSearchBridgeProtocol.decode(picked()) as VideoSearchBridgeMessage.Picked
        assertEquals("input", parsed.pickKind)
        assertEquals("input[name=\"q\"]", parsed.selector)
        assertTrue(parsed.formSafe)
        assertEquals(VideoSearchPagination.NONE, parsed.pagination)
        assertFalse(parsed.toString().contains("input[name"))
    }

    @Test fun acceptsResultCardsAndNextLinkPickWithoutRunningPagination() {
        val cards = VideoSearchBridgeProtocol.decode(
            picked("cards", "article.movie-card", formSafe = false)
        ) as VideoSearchBridgeMessage.Picked
        assertEquals("article.movie-card", cards.selector)
        val next = VideoSearchBridgeProtocol.decode(
            picked("next", "a.next", formSafe = false, pagination = "NEXT_LINK")
        ) as VideoSearchBridgeMessage.Picked
        assertEquals(VideoSearchPagination.NEXT_LINK, next.pagination)
    }

    @Test fun rejectsForgedProfilesAndUnknownAdditionalKeys() {
        for (name in listOf("profileId", "tabId", "isPrivate", "pageUrl", "cookies",
            "password", "title", "query", "token", "filmUrl")) {
            assertNull(VideoSearchBridgeProtocol.decode(picked().put(name, "forged")))
        }
        assertNull(VideoSearchBridgeProtocol.decode(picked("unknown")))
    }

    @Test fun rejectsUnsafeSelectorsAndInconsistentPickKinds() {
        assertNull(VideoSearchBridgeProtocol.decode(picked(selector = "input:has(script)")))
        assertNull(VideoSearchBridgeProtocol.decode(picked(selector = "a[onclick=alert(1)]")))
        assertNull(VideoSearchBridgeProtocol.decode(picked("submit", "button.find", formSafe = true)))
        assertNull(VideoSearchBridgeProtocol.decode(picked("cards", ".card", pagination = "NEXT_LINK")))
        assertNull(VideoSearchBridgeProtocol.decode(picked("next", "a.next")))
        assertNull(VideoSearchBridgeProtocol.decode(picked("input", "a.next", pagination = "LOAD_MORE")))
    }

    @Test fun skipAllowedOnlyForOptionalStepsAndCancelNeverSelects() {
        assertNull(VideoSearchBridgeProtocol.decode(reply("pick_skipped", "input")))
        for (kind in listOf("submit", "cards", "next")) {
            assertTrue(VideoSearchBridgeProtocol.decode(reply("pick_skipped", kind))
                is VideoSearchBridgeMessage.PickSkipped)
            assertTrue(VideoSearchBridgeProtocol.decode(reply("pick_cancelled", kind))
                is VideoSearchBridgeMessage.PickCancelled)
        }
        assertTrue(VideoSearchBridgeProtocol.decode(reply("pick_cancelled", "input"))
            is VideoSearchBridgeMessage.PickCancelled)
    }

    @Test fun outboundPickCommandIncludesNoUrlOrProfileMetadata() {
        for (kind in VideoSearchBridgeProtocol.pickKinds) {
            val packet = VideoSearchBridgeProtocol.pick(doc, request, kind)
            assertEquals("pick", packet.getString("event"))
            assertEquals(kind, packet.getString("pickKind"))
            assertEquals(5, packet.length())
            assertFalse(packet.has("siteId"))
            assertFalse(packet.has("profileId"))
            assertFalse(packet.has("pageUrl"))
        }
        try {
            VideoSearchBridgeProtocol.pick(doc, request, "password")
            fail("Unexpected pick kind")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun persistsCompletedManualSelectorsWithoutChangingSiteOtherFields() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val site = repo.addSite(owner, "https://example.org/search?view=films",
                nowMs = 10, newId = "s")
            val config = VideoSiteConfiguration(
                "input[name=\"q\"]", "article.movie-card",
                submission = VideoSearchSubmission.CLICK_SUBMIT,
                submitSelector = "button.search",
                pagination = VideoSearchPagination.NEXT_LINK,
                nextPageSelector = "a.next",
            )
            assertTrue(repo.applyManualConfiguration(site, config, nowMs = 11))
            val restored = repo.site(owner, "s")!!
            assertEquals(VideoSiteConfigurationState.CONFIGURED, restored.configurationState)
            assertEquals(config, restored.configuration)
            assertEquals(site.siteUrl, restored.siteUrl)
            assertEquals(site.displayName, restored.displayName)
            assertEquals(site.resultLimit, restored.resultLimit)
            assertEquals(1, repo.sites(owner).size)
        } finally { database.close() }
    }

    @Test fun partialManualSelectorSurvivesRoomRoundTripWithoutClaimingSuccess() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val site = repo.addSite(owner, "https://example.org", nowMs = 10, newId = "s")
            val partial = VideoSiteConfiguration(
                "input[name=\"s\"]", resultsCardSelector = null,
                submission = VideoSearchSubmission.FORM_SUBMIT,
            )
            assertTrue(repo.applyManualConfiguration(site, partial, nowMs = 11))
            val reloaded = VideoSearchRepository(database.dao()).site(owner, "s")!!
            assertEquals(VideoSiteConfigurationState.REPAIR_REQUIRED, reloaded.configurationState)
            assertEquals(partial, reloaded.configuration)
            assertTrue(reloaded.enabled)
            assertEquals(1, repo.sites(owner).size)
        } finally { database.close() }
    }

    @Test fun staleWizardCannotOverwriteRenameDeleteOrAnotherProfile() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val site = repo.addSite(owner, "https://example.org", nowMs = 10, newId = "same")
            val alternate = VideoSearchScope.Normal("work")
            repo.addSite(alternate, "https://example.org", nowMs = 10, newId = "same")
            repo.updateSiteDetails(owner, "same", "Zmiana", 80, nowMs = 12)
            val config = VideoSiteConfiguration("input.search", ".film-card")
            assertFalse(repo.applyManualConfiguration(site, config, nowMs = 13))
            assertEquals("Zmiana", repo.site(owner, "same")?.displayName)
            assertNull(repo.site(alternate, "same")?.configuration)
            assertTrue(repo.removeSite(owner, "same", confirmed = true))
            assertFalse(repo.applyManualConfiguration(site, config, nowMs = 14))
            assertNull(repo.site(owner, "same"))
        } finally { database.close() }
    }

    @Test fun cancellationIsWriteFreeAndOldWorkingConfigurationRemains() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val site = repo.addSite(owner, "https://example.org", nowMs = 1, newId = "id")
            val config = VideoSiteConfiguration("input.search", ".movie-card")
            assertTrue(repo.applyManualConfiguration(site, config, nowMs = 2))
            // The bridge only calls applyManualConfiguration after 4 completed
            // steps. Skip/cancel protocol messages must not persist anything.
            assertTrue(VideoSearchBridgeProtocol.decode(
                reply("pick_cancelled", "cards")) is VideoSearchBridgeMessage.PickCancelled)
            assertEquals(config, repo.site(owner, "id")?.configuration)
            assertEquals(VideoSiteConfigurationState.CONFIGURED,
                repo.site(owner, "id")?.configurationState)
        } finally { database.close() }
    }

    @Test fun manualSelectionAllowsOnlySameOriginIncludingResultPages() {
        assertTrue(VideoSearchBridgeProtocol.sameOrigin(
            "https://example.org/catalog", "https://example.org/results?q=film"))
        assertTrue(VideoSearchBridgeProtocol.sameOrigin(
            "https://example.org:443/catalog", "https://example.org/results"))
        assertFalse(VideoSearchBridgeProtocol.sameOrigin(
            "https://example.org", "http://example.org"))
        assertFalse(VideoSearchBridgeProtocol.sameOrigin(
            "https://example.org", "https://sub.example.org"))
        assertFalse(VideoSearchBridgeProtocol.sameOrigin(
            "https://example.org", "https://other.example.org"))
        assertFalse(VideoSearchBridgeProtocol.sameOrigin(
            "https://example.org", "https://user:pass@example.org/results"))
    }

    @Test fun rejectsIncompleteConfigurationMarkedConfiguredAtModelBoundary() {
        try {
            VideoSearchSite("id", owner, "https://example.org", "Test", 0,
                configurationState = VideoSiteConfigurationState.CONFIGURED,
                configuration = VideoSiteConfiguration("input.search", null),
                createdAtMs = 10)
            fail("Incomplete selector must not be CONFIGURED")
        } catch (_: IllegalArgumentException) {}
    }
}
