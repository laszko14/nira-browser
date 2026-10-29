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

/** P5 CFG-03: a same-millisecond write must invalidate older site snapshots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchConfigurationRevisionTest {
    private val scope = VideoSearchScope.Normal("default")
    private val alternate = VideoSearchScope.Normal("work")

    private fun database() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(), VideoSearchDatabase::class.java
    ).build()

    private fun configuration(tag: String) =
        VideoSiteConfiguration("input.$tag", ".cards-$tag")

    private fun inspection(): VideoSearchBridgeMessage.Inspection =
        VideoSearchBridgeProtocol.decode(JSONObject().apply {
            put("schemaVersion", 1)
            put("event", "inspection")
            put("documentId", "abcdef0123456789abcdef0123456789")
            put("requestId", "123456789abcdef0123456789abcdef0")
            put("status", "ready")
            put("inputSelector", "input.search")
            put("resultsCardSelector", ".cards-auto")
            put("submission", "FORM_SUBMIT")
            put("submitSelector", JSONObject.NULL)
            put("pagination", "NONE")
            put("nextPageSelector", JSONObject.NULL)
        }) as VideoSearchBridgeMessage.Inspection

    @Test fun manualSaveAtSameMillisecondInvalidatesOldAutoAndPicker() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val original = repo.addSite(scope, "https://example.org", nowMs = 1000, newId = "a")
            val chosen = configuration("manual")
            assertTrue(repo.applyManualConfiguration(original, chosen, nowMs = 1000))
            val current = repo.site(scope, "a")!!
            assertTrue(current.updatedAtMs > original.updatedAtMs)
            assertFalse(repo.applyAutoConfiguration(original, inspection(),
                nowMs = 1000, confirmedReplace = true))
            assertFalse(repo.applyManualConfiguration(original,
                configuration("stale"), nowMs = 1001))
            assertEquals(chosen, repo.site(scope, "a")!!.configuration)
        } finally { db.close() }
    }

    @Test fun autoSaveAtSameMillisecondInvalidatesOldManualPicker() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val original = repo.addSite(scope, "https://example.org", nowMs = 500, newId = "a")
            assertTrue(repo.applyAutoConfiguration(original, inspection(), nowMs = 500))
            assertFalse(repo.applyManualConfiguration(original,
                configuration("stale"), nowMs = 500))
            assertEquals(".cards-auto", repo.site(scope, "a")!!.configuration?.resultsCardSelector)
        } finally { db.close() }
    }

    @Test fun backwardClockAfterRenameCannotAuthorizeStaleScan() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val original = repo.addSite(scope, "https://example.org", nowMs = 2000, newId = "a")
            assertTrue(repo.renameSite(scope, "a", "New name", nowMs = 300))
            val changed = repo.site(scope, "a")!!
            assertTrue(changed.updatedAtMs > original.updatedAtMs)
            assertFalse(repo.applyAutoConfiguration(original, inspection(), nowMs = 400))
            assertTrue(repo.applyManualConfiguration(changed, configuration("good"), nowMs = 200))
            val latest = repo.site(scope, "a")!!
            assertTrue(latest.updatedAtMs > changed.updatedAtMs)
            assertFalse(repo.applyManualConfiguration(changed, configuration("stale"), nowMs = 600))
            assertEquals(configuration("good"), latest.configuration)
        } finally { db.close() }
    }

    @Test fun disableAndReenableSameMillisecondCannotRevalidateOldSnapshot() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val original = repo.addSite(scope, "https://example.org", nowMs = 10, newId = "a")
            assertTrue(repo.setSiteEnabled(scope, "a", false, nowMs = 10))
            assertTrue(repo.setSiteEnabled(scope, "a", true, nowMs = 10))
            val restored = repo.site(scope, "a")!!
            assertTrue(restored.enabled)
            assertTrue(restored.updatedAtMs >= 12)
            assertFalse(repo.applyAutoConfiguration(original, inspection(), nowMs = 10))
            assertTrue(repo.applyAutoConfiguration(restored, inspection(), nowMs = 10))
        } finally { db.close() }
    }

    @Test fun explicitRepairUsesFreshRevisionAndRejectsStaleValidation() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val original = repo.addSite(scope, "https://example.org", nowMs = 70, newId = "a")
            assertTrue(repo.applyManualConfiguration(original,
                configuration("first"), nowMs = 70))
            val before = repo.site(scope, "a")!!
            assertTrue(repo.applyAutoConfiguration(before, inspection(),
                nowMs = 70, confirmedReplace = true))
            val latest = repo.site(scope, "a")!!
            assertFalse(repo.applyAutoConfiguration(before, inspection(),
                nowMs = 70, confirmedReplace = true))
            assertFalse(repo.applyManualConfiguration(before,
                configuration("stale"), nowMs = 70))
            assertTrue(latest.updatedAtMs > before.updatedAtMs)
            assertEquals(".cards-auto", latest.configuration?.resultsCardSelector)
        } finally { db.close() }
    }

    @Test fun markingIncompleteSameMillisecondInvalidatesPreviousDetectorSnapshot() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val original = repo.addSite(scope, "https://example.org", nowMs = 800, newId = "a")
            assertTrue(repo.markAutoConfigurationIncomplete(original, nowMs = 800))
            val pending = repo.site(scope, "a")!!
            assertTrue(pending.updatedAtMs > original.updatedAtMs)
            assertFalse(repo.applyAutoConfiguration(original, inspection(), nowMs = 800))
            assertFalse(repo.markAutoConfigurationIncomplete(original, nowMs = 800))
            assertTrue(repo.applyManualConfiguration(pending,
                configuration("repair"), nowMs = 800))
        } finally { db.close() }
    }

    @Test fun profileScopedRevisionsAllowSameIdsInSeparateProfiles() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val a = repo.addSite(scope, "https://example.org", nowMs = 100, newId = "shared")
            val b = repo.addSite(alternate, "https://example.org", nowMs = 100, newId = "shared")
            assertTrue(repo.applyManualConfiguration(a,
                configuration("a"), nowMs = 100))
            assertTrue(repo.applyManualConfiguration(b,
                configuration("b"), nowMs = 100))
            assertEquals(configuration("a"), repo.site(scope, "shared")!!.configuration)
            assertEquals(configuration("b"), repo.site(alternate, "shared")!!.configuration)
            assertFalse(repo.applyAutoConfiguration(a, inspection(), nowMs = 100))
        } finally { db.close() }
    }

    @Test fun sameMillisecondDetailsAndLimitMutationsAdvanceToken() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val original = repo.addSite(scope, "https://example.org", nowMs = 90, newId = "a")
            assertTrue(repo.updateSiteDetails(scope, "a", "Name", 120, nowMs = 90))
            val details = repo.site(scope, "a")!!
            assertTrue(details.updatedAtMs > original.updatedAtMs)
            assertTrue(repo.setSiteLimit(scope, "a", 130, nowMs = 90))
            val changed = repo.site(scope, "a")!!
            assertTrue(changed.updatedAtMs > details.updatedAtMs)
            assertFalse(repo.applyAutoConfiguration(details, inspection(), nowMs = 90))
            assertEquals(130, changed.resultLimit)
        } finally { db.close() }
    }

    @Test fun reorderAtSameMillisecondRevokesOldConfigurationSnapshot() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val first = repo.addSite(scope, "https://a.example.org", nowMs = 90, newId = "a")
            val second = repo.addSite(scope, "https://b.example.org", nowMs = 90, newId = "b")
            assertTrue(repo.reorderSites(scope, listOf("a", "b"), listOf("b", "a"), nowMs = 90))
            assertTrue(repo.site(scope, "a")!!.updatedAtMs > first.updatedAtMs)
            assertTrue(repo.site(scope, "b")!!.updatedAtMs > second.updatedAtMs)
            assertFalse(repo.applyAutoConfiguration(first, inspection(), nowMs = 90))
            assertEquals(listOf("b", "a"), repo.sites(scope).map { it.id })
        } finally { db.close() }
    }
}
