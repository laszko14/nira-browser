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

/** Stage 3B: protocol and persisted auto-config tests. Gecko UI NOT executed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchAutoConfigurationTest {
    private val document = "abcdef0123456789abcdef0123456789"
    private val request = "123456789abcdef0123456789abcdef0"
    private val scope = VideoSearchScope.Normal("default")
    private val other = VideoSearchScope.Normal("work")
    private fun packet(event: String) = JSONObject().apply {
        put("schemaVersion", 1)
        put("event", event)
        put("documentId", document)
        put("requestId", request)
    }
    private fun inspection(ready: Boolean = true): VideoSearchBridgeMessage.Inspection {
        val json = packet("inspection")
            .put("status", if (ready) "ready" else "partial")
            .put("inputSelector", "input[name=\"q\"]")
            .put("resultsCardSelector", if (ready) ".movie-card" else JSONObject.NULL)
            .put("submission", "FORM_SUBMIT")
            .put("submitSelector", JSONObject.NULL)
            .put("pagination", "NEXT_LINK")
            .put("nextPageSelector", "a[rel=\"next\"]")
        return VideoSearchBridgeProtocol.decode(json) as VideoSearchBridgeMessage.Inspection
    }

    @Test fun recognizesConservativeSelectorInspectionAndPagination() {
        val result = inspection()
        assertEquals("ready", result.status)
        assertEquals("input[name=\"q\"]", result.inputSelector)
        assertEquals(".movie-card", result.resultsCardSelector)
        assertEquals(VideoSearchSubmission.FORM_SUBMIT, result.submission)
        assertEquals(VideoSearchPagination.NEXT_LINK, result.pagination)
        assertFalse(result.toString().contains("movie-card"))
    }

    @Test fun partialRecognitionDoesNotClaimConfiguredResults() {
        val result = inspection(false)
        assertEquals("partial", result.status)
        assertNull(result.resultsCardSelector)
        assertNotNull(result.inputSelector)
    }

    @Test fun rejectsUnsolicitedKeysOrUntrustedTabAndProfileIds() {
        for (key in listOf("tabId", "profileId", "isPrivate", "token",
            "cookie", "password", "title", "query")) {
            val invalid = packet("inspection").put("status", "missing").put(key, "spoofed")
            assertNull(VideoSearchBridgeProtocol.decode(invalid))
        }
        assertNull(VideoSearchBridgeProtocol.decode(packet("random_action")))
    }

    @Test fun rejectsMalformedInspectionSelectorsOrSubmissionMethods() {
        val base = packet("inspection")
            .put("status", "ready")
            .put("inputSelector", "input[name=\"q\"]")
            .put("resultsCardSelector", ".movie-card")
            .put("submission", "FORM_SUBMIT")
            .put("submitSelector", JSONObject.NULL)
            .put("pagination", "NONE")
            .put("nextPageSelector", JSONObject.NULL)
        assertNull(VideoSearchBridgeProtocol.decode(JSONObject(base.toString())
            .put("inputSelector", "input:has(script)")))
        assertNull(VideoSearchBridgeProtocol.decode(JSONObject(base.toString())
            .put("submission", "POST")))
        assertNull(VideoSearchBridgeProtocol.decode(JSONObject(base.toString())
            .put("resultsCardSelector", JSONObject.NULL)))
        assertNull(VideoSearchBridgeProtocol.decode(JSONObject(base.toString())
            .put("nextPageSelector", "a.next")))
    }

    @Test fun submittedAndFailedAcksHaveStrictShapes() {
        assertTrue(VideoSearchBridgeProtocol.decode(packet("submitted"))
            is VideoSearchBridgeMessage.Submitted)
        assertNull(VideoSearchBridgeProtocol.decode(packet("submitted")
            .put("pageUrl", "https://example.org")))
        assertTrue(VideoSearchBridgeProtocol.decode(packet("search_failed")
            .put("reason", "unsafe")) is VideoSearchBridgeMessage.SearchFailed)
        assertNull(VideoSearchBridgeProtocol.decode(packet("search_failed")
            .put("reason", "login_by_passed")))
    }

    @Test fun nativeSearchCommandKeepsOwnQueryAndNeverContainsProfileOrTabId() {
        val command = VideoSearchBridgeProtocol.search(document, request, "film tytuł",
            "input[name=\"q\"]", VideoSearchSubmission.FORM_SUBMIT, null)
        assertEquals("film tytuł", command.getString("query"))
        assertEquals("FORM_SUBMIT", command.getString("submission"))
        assertFalse(command.has("profileId"))
        assertFalse(command.has("tabId"))
        assertEquals(request, command.getString("requestId"))
    }

    @Test fun rejectsEmptyTooLongOrControlCharacterQueries() {
        assertFalse(VideoSearchBridgeProtocol.validQuery(""))
        assertFalse(VideoSearchBridgeProtocol.validQuery("   "))
        assertFalse(VideoSearchBridgeProtocol.validQuery("x".repeat(251)))
        assertFalse(VideoSearchBridgeProtocol.validQuery("abc\nxyz"))
        assertTrue(VideoSearchBridgeProtocol.validQuery("Łona i Webber"))
    }

    @Test fun autoConfigurationDoesNotRemoveSiteAndPersistsOnlyCompleteResult() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication<Application>(), VideoSearchDatabase::class.java
        ).build()
        try {
            val repo = VideoSearchRepository(db.dao())
            val original = repo.addSite(scope, "https://example.org", newId = "site", nowMs = 1)
            assertFalse(repo.applyAutoConfiguration(original, inspection(false), nowMs = 2))
            assertEquals(VideoSiteConfigurationState.NEEDS_CONFIGURATION,
                repo.site(scope, "site")?.configurationState)
            assertTrue(repo.markAutoConfigurationIncomplete(original, nowMs = 2))
            assertEquals(VideoSiteConfigurationState.REPAIR_REQUIRED,
                repo.site(scope, "site")?.configurationState)
            val updated = repo.site(scope, "site")!!
            assertTrue(repo.applyAutoConfiguration(updated, inspection(), nowMs = 3))
            val configured = repo.site(scope, "site")!!
            assertEquals(VideoSiteConfigurationState.CONFIGURED, configured.configurationState)
            assertEquals("input[name=\"q\"]", configured.configuration?.searchInputSelector)
            assertEquals(".movie-card", configured.configuration?.resultsCardSelector)
            assertEquals(1, repo.sites(scope).size)
        } finally { db.close() }
    }

    @Test fun staleScanCannotOverwriteNewSettingsOrDifferentProfiles() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication<Application>(), VideoSearchDatabase::class.java
        ).build()
        try {
            val repo = VideoSearchRepository(db.dao())
            val a = repo.addSite(scope, "https://example.org", newId = "same", nowMs = 1)
            repo.addSite(other, "https://example.org", newId = "same", nowMs = 1)
            repo.updateSiteDetails(scope, a.id, "Nowa nazwa", 600, nowMs = 4)
            assertFalse(repo.applyAutoConfiguration(a, inspection(), nowMs = 5))
            assertFalse(repo.markAutoConfigurationIncomplete(a, nowMs = 5))
            assertEquals("Nowa nazwa", repo.site(scope, "same")?.displayName)
            assertEquals(600, repo.site(scope, "same")?.resultLimit)
            assertEquals(VideoSiteConfigurationState.NEEDS_CONFIGURATION,
                repo.site(other, "same")?.configurationState)
            assertEquals(1, repo.sites(scope).size)
        } finally { db.close() }
    }

    @Test fun successfulConfigNotOverwrittenOnLaterFailedAutoInspection() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication<Application>(), VideoSearchDatabase::class.java
        ).build()
        try {
            val repo = VideoSearchRepository(db.dao())
            val site = repo.addSite(scope, "https://example.org", newId = "s", nowMs = 1)
            assertTrue(repo.applyAutoConfiguration(site, inspection(), nowMs = 2))
            assertFalse(repo.markAutoConfigurationIncomplete(
                repo.site(scope, "s")!!, nowMs = 3
            ))
            assertEquals(VideoSiteConfigurationState.CONFIGURED,
                repo.site(scope, "s")?.configurationState)
        } finally { db.close() }
    }
}
