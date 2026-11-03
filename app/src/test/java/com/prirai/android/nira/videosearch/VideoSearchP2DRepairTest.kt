package com.prirai.android.nira.videosearch

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** P2-D: search proof, one-row CAS, provenance, privacy and reversible repairs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchP2DRepairTest {
    private val doc = "a".repeat(32)
    private val query = "Example movie"
    private fun page(id: String) = VideoSearchExtractedPage("ok", listOf(
        VideoSearchExtractedFilm("https://films.example/film?id=$id",
            "Film $id", null, null, VideoResultTitleSource.CARD,
            VideoResultImageSource.UNKNOWN, 0)), 1, false)
    private fun recognition(selector: String = "article.new", status: String = "found",
                            reason: String = "CANDIDATE_FOUND", score: Int = 96) =
        VideoSearchBridgeMessage.Recognition(doc, "b".repeat(32), status,
            reason, score, if (status == "found") selector else null, 3, 60, "MAIN")
    private fun proof(
        before: VideoSearchExtractedPage? = page("1"),
        after: VideoSearchExtractedPage? = page("2"),
        detected: VideoSearchBridgeMessage.Recognition? = recognition(),
        observed: String? = "article.new",
    ) = VideoSearchRepairEvidence(doc, doc, before, after,
        "https://films.example/", "https://films.example/", query,
        detected, observed)
    private fun db() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(), VideoSearchDatabase::class.java).build()
    private suspend fun configured(
        repo: VideoSearchRepository, owner: VideoSearchScope.Normal, id: String,
    ): VideoSearchSite {
        val pending = repo.addSite(owner, "https://films.example/", nowMs = 1, newId = id)
        assertTrue(repo.applyManualConfiguration(pending,
            VideoSiteConfiguration("#search", "article.old",
                VideoSearchSubmission.FORM_SUBMIT), nowMs = 2))
        return repo.site(owner, id)!!
    }

    @Test fun inspectionOnlyOrWeakEvidenceNeverOverwritesManualRules() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val owner = VideoSearchScope.Normal("normal")
            val site = configured(repo, owner, "one")
            val proposed = site.configuration!!.copy(
                searchInputSelector = "#new", resultsCardSelector = "article.new")
            val inspection = VideoSearchBridgeMessage.Inspection(doc, "b".repeat(32),
                "ready", "#new", "article.new", VideoSearchSubmission.FORM_SUBMIT,
                null, VideoSearchPagination.NONE, null)
            assertFalse(repo.applyAutoConfiguration(site, inspection,
                confirmedReplace = true))
            assertFalse(repo.applyVerifiedRepair(site, proposed, proof(), false))
            assertFalse(repo.applyVerifiedRepair(site, proposed, proof(after = page("1")), true))
            assertFalse(repo.applyVerifiedRepair(site, proposed,
                proof(detected = recognition(status = "partial", reason = "AMBIGUOUS_CARDS")), true))
            assertFalse(repo.applyVerifiedRepair(site, proposed,
                proof(detected = recognition(score = 70)), true))
            assertFalse(repo.applyVerifiedRepair(site, proposed,
                proof(observed = "article.another"), true))
            assertFalse(repo.applyVerifiedRepair(site, proposed,
                proof(after = null), true))
            assertEquals(site, repo.site(owner, site.id))
            assertEquals(VideoSearchRuleOrigin.MANUAL,
                site.configuration?.recognition?.origin)
        } finally { database.close() }
    }

    @Test fun verifiedSearchRepairsThenRestoresFullManualConfiguration() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val owner = VideoSearchScope.Normal("normal")
            val site = configured(repo, owner, "repair")
            assertTrue(repo.rememberConfirmedResults(site, "article.backup", nowMs = 3))
            val confirmed = repo.site(owner, site.id)!!
            val previous = confirmed.configuration!!
            assertEquals(1, previous.recognition.confirmations)
            val proposed = previous.copy(
                searchInputSelector = "#new",
                resultsCardSelector = "article.new",
                submission = VideoSearchSubmission.CLICK_SUBMIT,
                submitSelector = "#search-button")
            assertTrue(repo.applyVerifiedRepair(confirmed, proposed, proof(),
                explicitConsent = true, nowMs = 4))
            val repaired = repo.site(owner, site.id)!!
            assertEquals("article.new", repaired.configuration?.resultsCardSelector)
            assertEquals("#new", repaired.configuration?.searchInputSelector)
            assertEquals(VideoSearchRuleOrigin.VERIFIED,
                repaired.configuration?.recognition?.origin)
            assertEquals(2, repaired.configuration?.recognition?.confirmations)
            assertTrue(repaired.configuration!!.recognition.backupResultsSelectors
                .contains("article.old"))
            assertNotNull(repaired.configuration?.recognition?.rollbackConfigurationJson)
            assertEquals(repaired, repo.site(owner, site.id))
            assertFalse(repo.rollbackVerifiedRepair(confirmed, true))
            assertFalse(repo.rollbackVerifiedRepair(repaired, false))
            assertTrue(repo.rollbackVerifiedRepair(repaired, true, nowMs = 5))
            val restored = repo.site(owner, site.id)!!
            assertEquals(previous, restored.configuration)
            assertEquals(VideoSearchRuleOrigin.MANUAL,
                restored.configuration?.recognition?.origin)
            assertNull(restored.configuration?.recognition?.rollbackConfigurationJson)
            assertFalse(repo.rollbackVerifiedRepair(restored, true))
        } finally { database.close() }
    }

    @Test fun deleteDisableProfileAndConcurrentUpdatesInvalidateRepair() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val a = VideoSearchScope.Normal("a")
            val b = VideoSearchScope.Normal("b")
            val site = configured(repo, a, "shared")
            val other = configured(repo, b, "shared")
            val proposed = site.configuration!!.copy(resultsCardSelector = "article.new")
            assertTrue(repo.setSiteEnabled(a, site.id, false, nowMs = 6))
            assertFalse(repo.applyVerifiedRepair(site, proposed, proof(), true))
            assertEquals(other, repo.site(b, other.id))
            val edited = repo.site(a, site.id)!!
            assertTrue(repo.setSiteEnabled(a, site.id, true, nowMs = 7))
            assertFalse(repo.applyVerifiedRepair(edited, proposed, proof(), true))
            val current = repo.site(a, site.id)!!
            val a1 = async(Dispatchers.Default) {
                repo.applyVerifiedRepair(current, proposed, proof(), true, nowMs = 8)
            }
            val a2 = async(Dispatchers.Default) {
                repo.applyVerifiedRepair(current, proposed, proof(), true, nowMs = 8)
            }
            assertEquals(1, listOf(a1.await(), a2.await()).count { it })
            assertEquals(other, repo.site(b, other.id))
            val old = repo.site(a, site.id)!!
            assertTrue(repo.removeSite(a, site.id, true))
            assertFalse(repo.rollbackVerifiedRepair(old, true))
            assertNull(repo.site(a, site.id))
        } finally { database.close() }
    }

    @Test fun newDocumentNeedsExactQueryNotRecommendationsOrCaptchaBypass() {
        val moved = VideoSearchRepairEvidence(
            doc, "c".repeat(32), null, page("10"),
            "https://films.example/", "https://films.example/recommendations",
            query, recognition(), "article.new")
        assertFalse(moved.confirms("article.new"))
        assertFalse(proof(detected = null).confirms("article.new"))
        assertFalse(proof(detected = recognition(reason = "FRAME_RESULTS")).confirms("article.new"))
        assertFalse(proof(detected = recognition(reason = "SHADOW_RESULTS")).confirms("article.new"))
        assertFalse(proof(before = null).confirms("article.new"))
        assertFalse(proof(after = VideoSearchExtractedPage("no_results",
            emptyList(), 0, false)).confirms("article.new"))
        assertFalse(proof().toString().contains(query))
    }

    @Test fun brokenRollbackMetadataNeverDestroysCurrentSelectors() {
        val old = VideoSiteConfiguration("#search", "article.old",
            recognition = VideoSearchRecognitionMemory(origin = VideoSearchRuleOrigin.MANUAL))
        val next = VideoSearchRecognitionPolicy.repaired(old,
            old.copy(resultsCardSelector = "article.new"), 42)!!
        val encoded = VideoSearchRowCodec.configurationJson(next)
        assertEquals(next, VideoSearchRowCodec.readConfiguration(encoded))
        assertEquals(old, VideoSearchRecognitionPolicy.rollback(next))
        assertNull(VideoSearchRecognitionPolicy.rollback(old))
        val corrupt = next.copy(recognition = next.recognition.copy(
            rollbackConfigurationJson = "{bad-json"))
        assertNull(VideoSearchRecognitionPolicy.rollback(corrupt))
        assertEquals("article.new", corrupt.resultsCardSelector)
    }
}
