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

/** ETAP 7C: compare-and-set cleanup never overwrites a new choice or erases data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchDeletedDefaultGroupRepositoryTest {
    private val normal = VideoSearchScope.Normal("default")
    private val work = VideoSearchScope.Normal("work")
    private fun db() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication<Application>(),
        VideoSearchDatabase::class.java
    ).build()

    @Test fun confirmedDeletedDefaultBecomesUngrouped() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            repo.setDefaultVideoTabGroup(normal, "deleted")
            assertTrue(repo.clearDeletedDefaultVideoTabGroup(normal, "deleted"))
            assertNull(repo.settings(normal).defaultTabGroupId)
        } finally { database.close() }
    }

    @Test fun secondCleanupIsIdempotent() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            repo.setDefaultVideoTabGroup(normal, "deleted")
            assertTrue(repo.clearDeletedDefaultVideoTabGroup(normal, "deleted"))
            assertFalse(repo.clearDeletedDefaultVideoTabGroup(normal, "deleted"))
            assertNull(repo.settings(normal).defaultTabGroupId)
        } finally { database.close() }
    }

    @Test fun staleCleanupCannotEraseNewSelection() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            repo.setDefaultVideoTabGroup(normal, "old")
            repo.setDefaultVideoTabGroup(normal, "new")
            assertFalse(repo.clearDeletedDefaultVideoTabGroup(normal, "old"))
            assertEquals("new", repo.settings(normal).defaultTabGroupId)
        } finally { database.close() }
    }

    @Test fun wrongProfileCannotEraseAnotherProfilesDefault() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            repo.setDefaultVideoTabGroup(normal, "shared-id")
            repo.setDefaultVideoTabGroup(work, "shared-id")
            assertTrue(repo.clearDeletedDefaultVideoTabGroup(work, "shared-id"))
            assertEquals("shared-id", repo.settings(normal).defaultTabGroupId)
            assertNull(repo.settings(work).defaultTabGroupId)
        } finally { database.close() }
    }

    @Test fun cleanupOnMissingSettingsIsNoOp() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            assertFalse(repo.clearDeletedDefaultVideoTabGroup(normal, "missing"))
            assertNull(repo.settings(normal).defaultTabGroupId)
        } finally { database.close() }
    }

    @Test fun blankExpectedGroupIdIsRejected() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            repo.setDefaultVideoTabGroup(normal, "valid")
            try {
                repo.clearDeletedDefaultVideoTabGroup(normal, "")
                fail("Blank group ID should not be accepted")
            } catch (_: IllegalArgumentException) {}
            assertEquals("valid", repo.settings(normal).defaultTabGroupId)
        } finally { database.close() }
    }

    @Test fun cleanupChangesOnlyDefaultFieldAndPreservesOtherSettings() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val site = repo.addSite(
                normal, "https://example.org", newId = "site"
            )
            repo.setSiteSelected(normal, site.id, true)
            repo.setParallelism(normal, VideoSearchParallelism.THREE)
            repo.setResultsView(normal, VideoSearchView.LIST)
            repo.setDefaultVideoTabGroup(normal, "deleted")
            assertTrue(repo.clearDeletedDefaultVideoTabGroup(normal, "deleted"))
            val saved = repo.settings(normal)
            assertNull(saved.defaultTabGroupId)
            assertEquals(setOf("site"), saved.selectedSiteIds)
            assertEquals(VideoSearchParallelism.THREE, saved.parallelism)
            assertEquals(VideoSearchView.LIST, saved.view)
        } finally { database.close() }
    }

    @Test fun cleanupDoesNotDeleteSavedFoldersOrLinks() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            repo.putFolder(VideoSavedFilmFolder(
                id = "saved", scope = normal, name = "Moje filmy",
                createdAtMs = 100L
            ))
            assertTrue(repo.addSavedLink(VideoSavedFilmLink(
                id = "link", folderId = "saved", scope = normal,
                filmPageUrl = "https://example.org/watch?id=1&lang=pl",
                title = "Film", thumbnailUrl = null,
                durationSeconds = 90L, createdAtMs = 100L
            )))
            repo.setDefaultVideoTabGroup(normal, "deleted")
            assertTrue(repo.clearDeletedDefaultVideoTabGroup(normal, "deleted"))
            assertEquals("Moje filmy", repo.folders(normal).single().name)
            assertEquals("link", repo.savedLinks(normal, "saved").single().id)
        } finally { database.close() }
    }

    @Test fun cleanupDoesNotDeleteRecordedSearchRunsAndFilms() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val run = VideoSearchRun(
                id = "history", scope = normal, query = "kino",
                siteIds = listOf("source"), parallelism = VideoSearchParallelism.ONE,
                status = VideoSearchRunState.RUNNING, startedAtMs = 100L
            )
            val task = VideoSearchSiteTask(
                runId = "history", scope = normal, siteId = "source",
                status = VideoSearchSiteState.ACTIVE, uniqueResults = 1,
                resultLimit = 200, visitedPages = 1
            )
            val film = VideoSearchFilm(
                id = "film", runId = "history", scope = normal,
                siteId = "source", siteName = "Witryna",
                filmPageUrl = "https://example.org/film?id=2",
                originalTitle = "Film", originalIndex = 0, foundAtMs = 100L,
                titleSource = VideoResultTitleSource.CARD
            )
            repo.putSnapshot(run, listOf(task), listOf(film))
            repo.setDefaultVideoTabGroup(normal, "deleted")
            assertTrue(repo.clearDeletedDefaultVideoTabGroup(normal, "deleted"))
            val snapshot = repo.resultsSnapshot(normal, "history")
            assertEquals("kino", snapshot?.run?.query)
            assertEquals("film", snapshot?.films?.single()?.id)
        } finally { database.close() }
    }

    @Test fun repeatedDeletionAndManualSelectionRemainIndependent() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            repo.setDefaultVideoTabGroup(normal, "group-1")
            assertTrue(repo.clearDeletedDefaultVideoTabGroup(normal, "group-1"))
            repo.setDefaultVideoTabGroup(normal, "group-2")
            assertFalse(repo.clearDeletedDefaultVideoTabGroup(normal, "group-1"))
            assertEquals("group-2", repo.settings(normal).defaultTabGroupId)
            assertTrue(repo.clearDeletedDefaultVideoTabGroup(normal, "group-2"))
            assertNull(repo.settings(normal).defaultTabGroupId)
        } finally { database.close() }
    }
}
