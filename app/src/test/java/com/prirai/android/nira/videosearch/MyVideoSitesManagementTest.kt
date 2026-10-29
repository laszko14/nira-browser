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

/** Stage 2B repository and Room regressions; not run until allowed build/test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class MyVideoSitesManagementTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun db() = Room.inMemoryDatabaseBuilder(context, VideoSearchDatabase::class.java).build()
    private val owner get() = VideoSearchScope.Normal("default")
    private val other get() = VideoSearchScope.Normal("work")

    private suspend fun seed(repo: VideoSearchRepository, scope: VideoSearchScope.Normal = owner) {
        for (i in 1..3) repo.addSite(scope, "https://example$i.org", newId = "site$i", nowMs = i.toLong())
    }

    private suspend fun rejects(block: suspend () -> Unit) {
        try {
            block()
            fail("Invalid operation unexpectedly succeeded")
        } catch (_: IllegalArgumentException) {
            // Explicit validation failed.
        }
    }

    @Test fun editingNameAndLimitKeepsSelectorsUrlAndEnableState() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val first = repo.addSite(owner, "https://example.org/path?ep=1", newId = "s")
            val withConfig = first.copy(
                configurationState = VideoSiteConfigurationState.CONFIGURED,
                configuration = VideoSiteConfiguration("input[name=q]", "article.card"),
                enabled = false,
            )
            repo.putSite(withConfig)
            assertTrue(repo.updateSiteDetails(owner, "s", "  Mój katalog  ", 350, nowMs = 10))
            val saved = repo.site(owner, "s")!!
            assertEquals("Mój katalog", saved.displayName)
            assertEquals(350, saved.resultLimit)
            assertEquals(withConfig.configuration, saved.configuration)
            assertEquals(withConfig.configurationState, saved.configurationState)
            assertEquals(withConfig.siteUrl, saved.siteUrl)
            assertFalse(saved.enabled)
            assertEquals(10L, saved.updatedAtMs)
        } finally { database.close() }
    }

    @Test fun siteActiveTogglePersistsAndDoesNotRemoveSite() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            seed(repo)
            assertTrue(repo.setSiteEnabled(owner, "site2", false))
            assertFalse(VideoSearchRepository(database.dao()).site(owner, "site2")!!.enabled)
            assertEquals(3, repo.sites(owner).size)
            assertTrue(repo.setSiteEnabled(owner, "site2", true))
            assertTrue(repo.site(owner, "site2")!!.enabled)
        } finally { database.close() }
    }

    @Test fun invalidLimitsAndNamesLeaveStoredRowUnchanged() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val site = repo.addSite(owner, "https://example.org", newId = "s")
            for (limit in listOf(0, -1, 10001)) {
                rejects { repo.updateSiteDetails(owner, "s", "New", limit) }
                rejects { repo.setSiteLimit(owner, "s", limit) }
            }
            rejects { repo.updateSiteDetails(owner, "s", "  ", 50) }
            rejects { repo.updateSiteDetails(owner, "s", "x".repeat(121), 50) }
            assertEquals(site, repo.site(owner, "s"))
        } finally { database.close() }
    }

    @Test fun dragOrderIsStoredAsDensePositionsAndSurvivesRepositoryReload() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            seed(repo)
            val before = repo.sites(owner).map { it.id }
            val after = listOf("site3", "site1", "site2")
            assertTrue(repo.reorderSites(owner, before, after, nowMs = 50))
            val reloaded = VideoSearchRepository(database.dao()).sites(owner)
            assertEquals(after, reloaded.map { it.id })
            assertEquals(listOf(0, 1, 2), reloaded.map { it.position })
        } finally { database.close() }
    }

    @Test fun staleOrIncompleteDropDoesNotChangeCurrentOrder() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            seed(repo)
            val before = repo.sites(owner).map { it.id }
            repo.addSite(owner, "https://example4.org", newId = "site4")
            assertFalse(repo.reorderSites(owner, before, before.reversed()))
            val actual = repo.sites(owner).map { it.id }
            rejects { repo.reorderSites(owner, actual, listOf("site1")) }
            assertEquals(actual, repo.sites(owner).map { it.id })
        } finally { database.close() }
    }

    @Test fun sqlFailureDuringDragRollsBackEveryPosition() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            seed(repo)
            val before = repo.sites(owner).map { it.id }
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER refuse_position BEFORE UPDATE OF position ON video_search_sites " +
                    "WHEN NEW.id = 'site3' AND NEW.position = 0 " +
                    "BEGIN SELECT RAISE(ABORT, 'reorder denied'); END"
            )
            try {
                repo.reorderSites(owner, before, listOf("site3", "site2", "site1"))
                fail("Expected transactional failure")
            } catch (_: android.database.SQLException) {}
            assertEquals(before, repo.sites(owner).map { it.id })
            assertEquals(listOf(0, 1, 2), repo.sites(owner).map { it.position })
        } finally { database.close() }
    }

    @Test fun profileSpecificEditAndDragCannotAlterOtherProfile() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            seed(repo, owner)
            seed(repo, other)
            assertTrue(repo.updateSiteDetails(owner, "site1", "Inna", 5))
            assertTrue(repo.setSiteEnabled(owner, "site2", false))
            assertTrue(repo.reorderSites(owner, listOf("site1", "site2", "site3"),
                listOf("site3", "site1", "site2")))
            assertEquals(listOf("site1", "site2", "site3"), repo.sites(other).map { it.id })
            assertEquals("example1.org", repo.site(other, "site1")!!.displayName)
            assertEquals(200, repo.site(other, "site1")!!.resultLimit)
            assertTrue(repo.site(other, "site2")!!.enabled)
        } finally { database.close() }
    }

    @Test fun namesLimitsEnabledFlagsAndOrderSurviveDatabaseReopen() = runBlocking {
        val name = "video-sites-stage2b-restore.db"
        context.deleteDatabase(name) // isolated Robolectric fixture only
        try {
            val first = Room.databaseBuilder(context, VideoSearchDatabase::class.java, name).build()
            try {
                val repo = VideoSearchRepository(first.dao())
                seed(repo)
                assertTrue(repo.updateSiteDetails(owner, "site2", "Moja witryna", 570))
                assertTrue(repo.setSiteEnabled(owner, "site2", false))
                assertTrue(repo.reorderSites(owner, listOf("site1", "site2", "site3"),
                    listOf("site3", "site2", "site1")))
            } finally { first.close() }

            val reopened = Room.databaseBuilder(context, VideoSearchDatabase::class.java, name).build()
            try {
                val repo = VideoSearchRepository(reopened.dao())
                assertEquals(listOf("site3", "site2", "site1"),
                    repo.sites(owner).map { it.id })
                val changed = repo.site(owner, "site2")!!
                assertEquals("Moja witryna", changed.displayName)
                assertEquals(570, changed.resultLimit)
                assertFalse(changed.enabled)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun editingRemovedRowNeverRecreatesIt() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            seed(repo)
            assertTrue(repo.removeSite(owner, "site2", confirmed = true))
            assertFalse(repo.renameSite(owner, "site2", "No resurrection"))
            assertFalse(repo.updateSiteDetails(owner, "site2", "No resurrection", 30))
            assertFalse(repo.setSiteEnabled(owner, "site2", false))
            assertEquals(2, repo.sites(owner).size)
        } finally { database.close() }
    }
}
