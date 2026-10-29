package com.prirai.android.nira.videosearch

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Stage 5A Room contract: one coherent per-profile plan, no run persistence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchDispatchRepositoryTest {
    private val normal = VideoSearchScope.Normal("default")
    private val other = VideoSearchScope.Normal("work")

    private fun db() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        VideoSearchDatabase::class.java
    ).build()

    private suspend fun add(
        repo: VideoSearchRepository, owner: VideoSearchScope.Normal,
        id: String, timestamp: Long = 100L,
    ) = repo.addSite(owner, "https://$id.example.org/search",
        nowMs = timestamp, newId = id)

    private suspend fun configured(
        repo: VideoSearchRepository, site: VideoSearchSite, at: Long = 101L,
    ) = repo.applyManualConfiguration(site,
        VideoSiteConfiguration("input.search", ".film-card",
            submission = VideoSearchSubmission.FORM_SUBMIT),
        nowMs = at)

    @Test fun userSelectionAndParallelModeAreIndependentAndPersisted() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            repeat(7) {
                val id = "s$it"
                val site = add(repo, normal, id, (100 + it).toLong())
                assertTrue(configured(repo, site, (200 + it).toLong()))
                assertTrue(repo.setSiteSelected(normal, id, true))
            }
            repo.setParallelism(normal, VideoSearchParallelism.TWO)
            val plan = repo.prepareDispatch(normal, "komedia")
            assertEquals(7, plan.selectedCount)
            assertEquals(VideoSearchParallelism.TWO, plan.parallelism)
            assertEquals(2, plan.createQueue().lease(10).size)
            assertEquals(7, repo.selectedSiteIds(normal).size)
        } finally { database.close() }
    }

    @Test fun allIsNotCappedByFiveAndPreservesEverySelection() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            repeat(8) {
                val site = add(repo, normal, "site$it", (100 + it).toLong())
                configured(repo, site, 300L + it)
                repo.setSiteSelected(normal, site.id, true)
            }
            repo.setParallelism(normal, VideoSearchParallelism.ALL)
            val plan = repo.prepareDispatch(normal, "film")
            assertEquals(8, plan.selectedCount)
            assertEquals(8, plan.createQueue().lease(8).size)
            assertEquals(VideoSearchParallelism.ALL, repo.settings(normal).parallelism)
        } finally { database.close() }
    }

    @Test fun changingParallelismDoesNotEraseSelectionOrViewOrGroup() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val a = add(repo, normal, "a")
            repo.setSiteSelected(normal, a.id, true)
            repo.putSettings(VideoSearchSettings(normal,
                selectedSiteIds = setOf("a"),
                parallelism = VideoSearchParallelism.ONE,
                defaultTabGroupId = "group-17",
                view = VideoSearchView.LIST,
                sortBySiteId = mapOf("a" to VideoSearchSort.TITLE_Z_A)
            ))
            repo.setParallelism(normal, VideoSearchParallelism.FIVE)
            val prefs = repo.settings(normal)
            assertEquals(setOf("a"), prefs.selectedSiteIds)
            assertEquals(VideoSearchParallelism.FIVE, prefs.parallelism)
            assertEquals("group-17", prefs.defaultTabGroupId)
            assertEquals(VideoSearchView.LIST, prefs.view)
            assertEquals(VideoSearchSort.TITLE_Z_A, prefs.sortBySiteId["a"])
        } finally { database.close() }
    }

    @Test fun creatingSettingsForModeDoesNotCreateSelection() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            repo.setParallelism(normal, VideoSearchParallelism.THREE)
            assertEquals(VideoSearchParallelism.THREE, repo.settings(normal).parallelism)
            assertTrue(repo.selectedSiteIds(normal).isEmpty())
            assertEquals(VideoSearchView.TILES, repo.settings(normal).view)
        } finally { database.close() }
    }

    @Test fun disabledSelectedSitesExcludedFromLaunchButRemainRemembered() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val a = add(repo, normal, "a")
            val b = add(repo, normal, "b", 101L)
            configured(repo, a)
            configured(repo, b, 103L)
            repo.selectAllEnabledSites(normal)
            repo.setSiteEnabled(normal, b.id, false, nowMs = 120L)
            val plan = repo.prepareDispatch(normal, "komedia")
            assertEquals(listOf("a"), plan.selectedSites.map { it.id })
            assertEquals(setOf("a", "b"), repo.selectedSiteIds(normal))
            repo.setSiteEnabled(normal, b.id, true, nowMs = 121L)
            assertEquals(2, repo.prepareDispatch(normal, "komedia").selectedCount)
        } finally { database.close() }
    }

    @Test fun unconfiguredSelectedSiteBecomesNeedsAttentionNotRunnable() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val ready = add(repo, normal, "ready")
            val pending = add(repo, normal, "pending", 101L)
            configured(repo, ready)
            repo.selectAllEnabledSites(normal)
            val plan = repo.prepareDispatch(normal, "komedia")
            assertEquals(2, plan.selectedCount)
            assertEquals(listOf("ready"), plan.runnableSites.map { it.id })
            assertEquals(listOf("pending"), plan.needsAttention.map { it.id })
            assertEquals(1, plan.createQueue().snapshot().needsAttention)
        } finally { database.close() }
    }

    @Test fun profileScopeCompletelyIsolatedEvenForSameSiteIds() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            for (profile in listOf(normal, other)) {
                val site = add(repo, profile, "same")
                configured(repo, site)
                repo.setSiteSelected(profile, "same", true)
            }
            repo.setParallelism(normal, VideoSearchParallelism.TWO)
            repo.setParallelism(other, VideoSearchParallelism.ALL)
            val first = repo.prepareDispatch(normal, "fraza A")
            val second = repo.prepareDispatch(other, "fraza B")
            assertEquals(normal, first.scope)
            assertEquals(other, second.scope)
            assertEquals(VideoSearchParallelism.TWO, first.parallelism)
            assertEquals(VideoSearchParallelism.ALL, second.parallelism)
            assertNotEquals(first.query, second.query)
        } finally { database.close() }
    }

    @Test fun deletingASiteRemovesItFromNextPreparedPlan() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            add(repo, normal, "a")
            add(repo, normal, "b", 102L)
            repo.selectAllEnabledSites(normal)
            val old = repo.prepareDispatch(normal, "komedia")
            assertTrue(repo.removeSite(normal, "b", confirmed = true))
            val fresh = repo.prepareDispatch(normal, "komedia")
            assertEquals(2, old.selectedCount) // immutable earlier snapshot
            assertEquals(1, fresh.selectedCount)
            assertEquals(listOf("a"), fresh.selectedSites.map { it.id })
        } finally { database.close() }
    }

    @Test fun reorderingIsReflectedAtNextLaunchNotPreviousSnapshot() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            add(repo, normal, "first")
            add(repo, normal, "second", 102L)
            repo.selectAllEnabledSites(normal)
            val old = repo.prepareDispatch(normal, "komedia")
            assertTrue(repo.reorderSites(normal,
                listOf("first", "second"), listOf("second", "first"), nowMs = 110L))
            assertEquals(listOf("first", "second"), old.selectedSites.map { it.id })
            assertEquals(listOf("second", "first"),
                repo.prepareDispatch(normal, "komedia").selectedSites.map { it.id })
        } finally { database.close() }
    }

    @Test fun defaultModeIsOneAndNoSelectionDoesNotCreateRun() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            add(repo, normal, "unselected")
            assertEquals(VideoSearchParallelism.ONE, repo.settings(normal).parallelism)
            try {
                repo.prepareDispatch(normal, "komedia")
                fail("Unselected site unexpectedly launched")
            } catch (_: IllegalArgumentException) {}
            assertTrue(repo.runs(normal).isEmpty())
            assertTrue(repo.tasks(normal, "unknown").isEmpty())
        } finally { database.close() }
    }

    @Test fun planningNeitherCreatesRunsNorSiteTasksOrFilms() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val site = add(repo, normal, "one")
            configured(repo, site)
            repo.setSiteSelected(normal, "one", true)
            repo.prepareDispatch(normal, "film").createQueue().lease(1)
            assertTrue(repo.runs(normal).isEmpty())
            assertTrue(repo.tasks(normal, "no-run").isEmpty())
            assertTrue(repo.films(normal, "no-run", "one").isEmpty())
        } finally { database.close() }
    }
}
