package com.prirai.android.nira.videosearch

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** P12/16 CFG-01 — persisted URL corrections, not simulated redirects or real Gecko. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchAddressCorrectionTest {
    private val normal = VideoSearchScope.Normal("default")
    private val foreign = VideoSearchScope.Normal("work")
    private fun db() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(), VideoSearchDatabase::class.java
    ).build()
    private suspend fun configured(repo: VideoSearchRepository, scope: VideoSearchScope.Normal,
                                   id: String = "s", address: String = "http://films.example.org/"): VideoSearchSite {
        val original = repo.addSite(scope, address, nowMs = 10, newId = id)
        assertTrue(repo.applyManualConfiguration(original, VideoSiteConfiguration(
            "input.search", ".film-card", submission = VideoSearchSubmission.FORM_SUBMIT
        ), nowMs = 11))
        return repo.site(scope, id)!!
    }

    @Test fun explicitHttpsRedirectPreservesIdentitySettingsAndHistoricalFilm() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val old = configured(repo, normal)
            assertTrue(repo.setSiteSelected(normal, old.id, true))
            repo.putRun(VideoSearchRun("previous", normal, "comedy", listOf(old.id),
                VideoSearchParallelism.ONE, VideoSearchRunState.COMPLETED, 30L,
                completedAtMs = 40L))
            val card = VideoSearchFilm(
                id = "card", runId = "previous", siteId = old.id, scope = normal,
                siteName = old.displayName,
                filmPageUrl = "http://films.example.org/watch?id=7&lang=pl",
                originalTitle = "Film", originalIndex = 0, foundAtMs = 30L,
            )
            assertTrue(repo.putFilm(card))
            val destination = "https://films.example.org/pl/search?q=%C5%82%C3%B3d%C5%BA&src=a%2Fb"
            assertEquals(VideoSearchSiteAddressEdit.UPDATED, repo.correctSiteAddress(
                old, destination, "Moja witryna", 135, true, 20L))
            val saved = repo.site(normal, old.id)!!
            assertEquals(old.id, saved.id)
            assertEquals(destination, saved.siteUrl)
            assertEquals("Moja witryna", saved.displayName)
            assertEquals(135, saved.resultLimit)
            assertEquals(VideoSiteConfigurationState.REPAIR_REQUIRED, saved.configurationState)
            assertEquals(old.configuration, saved.configuration)
            assertTrue(saved.updatedAtMs > old.updatedAtMs)
            assertEquals(setOf(old.id), repo.selectedSiteIds(normal))
            assertEquals(listOf(card), repo.films(normal, "previous", old.id))
            assertEquals("previous", repo.run(normal, "previous")!!.id)
            assertEquals(1, repo.sites(normal).size)
            assertTrue(VideoSearchBridgeProtocol.sameOrigin(
                destination, "https://films.example.org/results?page=2"))
            assertFalse(VideoSearchBridgeProtocol.sameOrigin(
                destination, "http://films.example.org/results?page=2"))
        } finally { db.close() }
    }

    @Test fun correctingWwwAndLanguagePathKeepsExactQueryWithoutNormalization() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val old = configured(repo, normal, "s", "https://example.org/")
            val finalUrl = "https://www.example.org/pl/filmy?sid=KeepCase&x=1&x=2"
            assertEquals(VideoSearchSiteAddressEdit.UPDATED, repo.correctSiteAddress(
                old, finalUrl, old.displayName, old.resultLimit, true))
            assertEquals(finalUrl, repo.site(normal, "s")!!.siteUrl)
            assertEquals(VideoSearchSiteAddressEdit.UNCHANGED, repo.correctSiteAddress(
                repo.site(normal, "s")!!, finalUrl, old.displayName, old.resultLimit, false))
            assertEquals(VideoSearchSiteAddressEdit.STALE,
                repo.correctSiteAddress(old, "https://example.org/again", "Old", 200, true))
        } finally { db.close() }
    }

    @Test fun noUserApprovalCannotUpdateAnyUrl() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val old = configured(repo, normal)
            try {
                repo.correctSiteAddress(old, "https://films.example.org/", "Title", 50, false)
                fail("Address update without explicit user approval")
            } catch (_: IllegalArgumentException) {}
            assertEquals(old, repo.site(normal, old.id))
        } finally { db.close() }
    }

    @Test fun duplicateUrlIsBlockedWithoutLosingOriginalSiteOrSelection() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val first = configured(repo, normal)
            val second = repo.addSite(normal, "https://www.example.org/pl", newId = "other")
            repo.setSiteSelected(normal, first.id, true)
            assertEquals(VideoSearchSiteAddressEdit.DUPLICATE_URL, repo.correctSiteAddress(
                first, second.siteUrl, "Collision", 45, true))
            assertEquals(first, repo.site(normal, first.id))
            assertEquals(second, repo.site(normal, second.id))
            assertEquals(setOf(first.id), repo.selectedSiteIds(normal))
        } finally { db.close() }
    }

    @Test fun profileAndRevisionIsolationSurviveRace() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val old = configured(repo, normal)
            val someoneElse = configured(repo, foreign)
            repo.renameSite(normal, old.id, "Newer", nowMs = 100)
            assertEquals(VideoSearchSiteAddressEdit.STALE, repo.correctSiteAddress(
                old, "https://films.example.org/", "Stale", 200, true, 99))
            assertEquals(someoneElse, repo.site(foreign, someoneElse.id))
            assertEquals("Newer", repo.site(normal, old.id)!!.displayName)
        } finally { db.close() }
    }

    @Test fun atomicConcurrentCorrectionsHaveOnlyOneWinner() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val first = configured(repo, normal)
            val results = listOf("https://films.example.org/a", "https://films.example.org/b")
                .mapIndexed { index, url ->
                    async(Dispatchers.Default) {
                        repo.correctSiteAddress(first, url, "Name$index", 200, true, 20L)
                    }
                }.awaitAll()
            assertEquals(1, results.count { it == VideoSearchSiteAddressEdit.UPDATED })
            assertEquals(1, results.count { it == VideoSearchSiteAddressEdit.STALE })
            assertEquals(1, repo.sites(normal).size)
            assertTrue(repo.site(normal, first.id)!!.updatedAtMs > first.updatedAtMs)
        } finally { db.close() }
    }

    @Test fun verifiedConfigRestoresOnlyAfterPositiveBoundPageCheck() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val old = configured(repo, normal)
            repo.correctSiteAddress(old, "https://films.example.org/pl", old.displayName,
                old.resultLimit, true)
            val changed = repo.site(normal, old.id)!!
            val protocol = VideoSearchBridgeProtocol
            val valid = org.json.JSONObject().apply {
                put("schemaVersion", 1)
                put("event", "validation")
                put("documentId", "a".repeat(32))
                put("requestId", "b".repeat(32))
                put("status", "valid")
                put("reason", "ok")
                put("attempts", 1)
                put("matchedCards", 3)
            }
            val report = protocol.decode(valid) as? VideoSearchBridgeMessage.Validation
            assertNotNull(report)
            assertTrue(repo.confirmRepairedConfiguration(changed, report!!))
            assertEquals(VideoSiteConfigurationState.CONFIGURED,
                repo.site(normal, old.id)!!.configurationState)
            assertEquals(old.configuration, repo.site(normal, old.id)!!.configuration)
        } finally { db.close() }
    }
}
