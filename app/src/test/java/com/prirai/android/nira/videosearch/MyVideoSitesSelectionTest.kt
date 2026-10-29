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

/** Selection is remembered per normal profile, independently of worker count. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class MyVideoSitesSelectionTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun database() =
        Room.inMemoryDatabaseBuilder(context, VideoSearchDatabase::class.java).build()
    private val regular get() = VideoSearchScope.Normal("default")
    private val work get() = VideoSearchScope.Normal("work")

    private suspend fun addSites(
        repo: VideoSearchRepository, scope: VideoSearchScope.Normal, count: Int,
    ) {
        (1..count).forEach { i ->
            repo.addSite(scope, "https://site$i.example.org/path?q=$i", newId = "site$i")
        }
    }

    @Test fun newProfileHasNoImplicitSelectionAndNothingIsDeleted() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addSites(repo, regular, 3)
            assertTrue(repo.selectedSiteIds(regular).isEmpty())
            assertTrue(repo.selectedActiveSites(regular).isEmpty())
            assertEquals(3, repo.sites(regular).size)
        } finally { db.close() }
    }

    @Test fun selectAllAllowsMoreThanFiveWithOnlyOneParallelWorker() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addSites(repo, regular, 9)
            assertEquals(9, repo.selectAllEnabledSites(regular))
            assertEquals(9, repo.selectedSiteIds(regular).size)
            assertEquals(9, repo.selectedActiveSites(regular).size)
            assertEquals(VideoSearchParallelism.ONE, repo.settings(regular).parallelism)
        } finally { db.close() }
    }

    @Test fun oneSiteToggleKeepsOtherSelectionsAndRejectsUnknownSite() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addSites(repo, regular, 3)
            assertFalse(repo.setSiteSelected(regular, "missing", true))
            assertTrue(repo.selectedSiteIds(regular).isEmpty())
            assertTrue(repo.setSiteSelected(regular, "site1", true))
            assertTrue(repo.setSiteSelected(regular, "site2", true))
            assertTrue(repo.setSiteSelected(regular, "site1", false))
            assertEquals(setOf("site2"), repo.selectedSiteIds(regular))
            assertEquals(3, repo.sites(regular).size)
        } finally { db.close() }
    }

    @Test fun disabledSiteKeepsRememberedChoiceButCannotBeLaunched() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addSites(repo, regular, 2)
            assertTrue(repo.setSiteSelected(regular, "site1", true))
            assertTrue(repo.setSiteEnabled(regular, "site1", false))
            assertEquals(setOf("site1"), repo.selectedSiteIds(regular))
            assertTrue(repo.selectedActiveSites(regular).isEmpty())
            assertFalse(repo.setSiteSelected(regular, "site1", false))
            assertTrue(repo.setSiteEnabled(regular, "site1", true))
            assertEquals(listOf("site1"), repo.selectedActiveSites(regular).map { it.id })
        } finally { db.close() }
    }

    @Test fun selectAllSkipsDisabledButClearRemovesEveryRememberedChoice() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addSites(repo, regular, 4)
            repo.setSiteEnabled(regular, "site2", false)
            assertEquals(3, repo.selectAllEnabledSites(regular))
            assertEquals(setOf("site1", "site3", "site4"), repo.selectedSiteIds(regular))
            repo.setSiteEnabled(regular, "site2", true)
            assertEquals(4, repo.selectAllEnabledSites(regular))
            repo.setSiteEnabled(regular, "site3", false)
            assertEquals(3, repo.selectedActiveSites(regular).size)
            repo.clearSelectedSites(regular)
            assertTrue(repo.selectedSiteIds(regular).isEmpty())
            assertEquals(4, repo.sites(regular).size)
        } finally { db.close() }
    }

    @Test fun selectionIsolatedFromAnotherProfileEvenWithIdenticalSiteIds() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addSites(repo, regular, 2)
            addSites(repo, work, 2)
            repo.setSiteSelected(regular, "site1", true)
            repo.setSiteSelected(work, "site2", true)
            assertEquals(setOf("site1"), repo.selectedSiteIds(regular))
            assertEquals(setOf("site2"), repo.selectedSiteIds(work))
            repo.selectAllEnabledSites(regular)
            assertEquals(setOf("site2"), repo.selectedSiteIds(work))
            repo.clearSelectedSites(work)
            assertEquals(2, repo.selectedSiteIds(regular).size)
        } finally { db.close() }
    }

    @Test fun changingSelectionDoesNotOverwriteParallelismViewOrTabGroup() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addSites(repo, regular, 2)
            val initial = VideoSearchSettings(
                scope = regular, parallelism = VideoSearchParallelism.FIVE,
                view = VideoSearchView.LIST, defaultTabGroupId = "tabs-7",
                sortBySiteId = mapOf("site1" to VideoSearchSort.DURATION_LONGEST),
            )
            repo.putSettings(initial)
            repo.setSiteSelected(regular, "site1", true)
            val actual = repo.settings(regular)
            assertEquals(initial.copy(selectedSiteIds = setOf("site1")), actual)
            repo.selectAllEnabledSites(regular)
            assertEquals(initial.parallelism, repo.settings(regular).parallelism)
            assertEquals(initial.defaultTabGroupId, repo.settings(regular).defaultTabGroupId)
        } finally { db.close() }
    }

    @Test fun deletingConfirmedSitePrunesSelectionOnlyForItsProfile() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addSites(repo, regular, 2)
            addSites(repo, work, 2)
            repo.selectAllEnabledSites(regular)
            repo.selectAllEnabledSites(work)
            assertTrue(repo.removeSite(regular, "site1", confirmed = true))
            assertEquals(setOf("site2"), repo.selectedSiteIds(regular))
            assertEquals(setOf("site1", "site2"), repo.selectedSiteIds(work))
            assertEquals(1, repo.sites(regular).size)
        } finally { db.close() }
    }

    @Test fun concurrentSelectionUpdatesDoNotLoseAnyChanges() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addSites(repo, regular, 12)
            val updated = (1..12).map { i ->
                async(Dispatchers.Default) { repo.setSiteSelected(regular, "site$i", true) }
            }.awaitAll()
            assertTrue(updated.all { it })
            assertEquals(12, repo.selectedSiteIds(regular).size)
            assertEquals(12, repo.selectedActiveSites(regular).size)
        } finally { db.close() }
    }

    @Test fun selectionsPersistAcrossDatabaseCloseAndReopen() = runBlocking {
        val name = "video-search-stage2c-test.db"
        context.deleteDatabase(name) // dedicated Robolectric fixture, not app DB
        try {
            val first = Room.databaseBuilder(context, VideoSearchDatabase::class.java, name).build()
            try {
                val repo = VideoSearchRepository(first.dao())
                addSites(repo, regular, 7)
                repo.setSiteSelected(regular, "site1", true)
                repo.setSiteSelected(regular, "site7", true)
                repo.selectAllEnabledSites(work)
            } finally { first.close() }
            val second = Room.databaseBuilder(context, VideoSearchDatabase::class.java, name).build()
            try {
                val repo = VideoSearchRepository(second.dao())
                assertEquals(setOf("site1", "site7"), repo.selectedSiteIds(regular))
                assertEquals(listOf("site1", "site7"),
                    repo.selectedActiveSites(regular).map { it.id })
                assertTrue(repo.selectedSiteIds(work).isEmpty())
            } finally { second.close() }
        } finally { context.deleteDatabase(name) }
    }
}
