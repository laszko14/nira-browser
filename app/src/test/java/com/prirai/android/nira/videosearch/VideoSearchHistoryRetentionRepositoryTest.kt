package com.prirai.android.nira.videosearch

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Stage 9A: Room owns the all-or-nothing 30-day cleanup, not saved folders. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchHistoryRetentionRepositoryTest {
    private val day = 24L * 60L * 60L * 1000L
    private val now = 50L * day
    private val cutoff = now - 30L * day
    private val normal = VideoSearchScope.Normal("default")
    private val work = VideoSearchScope.Normal("work")
    private fun database() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication<Application>(),
        VideoSearchDatabase::class.java,
    ).build()

    private suspend fun addRun(
        repo: VideoSearchRepository,
        scope: VideoSearchScope.Normal,
        id: String,
        status: VideoSearchRunState = VideoSearchRunState.COMPLETED,
        finishedAt: Long? = day,
        updatedAt: Long = finishedAt ?: day,
        withDetails: Boolean = true,
    ) {
        val run = VideoSearchRun(
            id = id, scope = scope, query = "query", siteIds = listOf("site"),
            parallelism = VideoSearchParallelism.ONE, status = status,
            startedAtMs = day, updatedAtMs = updatedAt,
            completedAtMs = finishedAt,
        )
        val task = VideoSearchSiteTask(
            runId = id, siteId = "site", scope = scope,
            status = VideoSearchSiteState.STOPPED,
        )
        val film = VideoSearchFilm(
            id = "$id-film", runId = id, siteId = "site", scope = scope,
            siteName = "Test site",
            filmPageUrl = "https://example.org/film?id=$id&quality=hd",
            originalIndex = 0, foundAtMs = day,
        )
        repo.putSnapshot(run,
            if (withDetails) listOf(task) else emptyList(),
            if (withDetails) listOf(film) else emptyList())
    }

    @Test fun expiredRunDeletesItsTaskAndFilmTogether() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addRun(repo, normal, "old")
            assertEquals(1, repo.pruneExpiredHistory(normal, now))
            assertTrue(repo.runs(normal).isEmpty())
            assertTrue(db.dao().getTasks(normal.profileId, "old").isEmpty())
            assertTrue(db.dao().getRunFilms(normal.profileId, "old").isEmpty())
            assertNull(repo.resultsSnapshot(normal, "old"))
        } finally { db.close() }
    }

    @Test fun savedFolderAndExactFullUrlSurviveAfterHistoryExpires() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addRun(repo, normal, "old")
            repo.putFolder(VideoSavedFilmFolder("folder", normal, "Filmy", day))
            val link = VideoSavedFilmLink(
                id = "saved", folderId = "folder", scope = normal,
                filmPageUrl = "https://example.org/film?id=old&quality=hd",
                title = "Tytuł", thumbnailUrl = "https://example.org/thumb.jpg",
                durationSeconds = 120L, createdAtMs = day,
            )
            assertTrue(repo.addSavedLink(link))
            assertEquals(1, repo.pruneExpiredHistory(normal, now))
            assertEquals(link, repo.savedLinks(normal, "folder").single())
            assertEquals("Filmy", repo.folders(normal).single().name)
        } finally { db.close() }
    }

    @Test fun activeAndPausedSearchesNeverExpireEvenWhenVeryOld() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            for (status in listOf(
                VideoSearchRunState.QUEUED, VideoSearchRunState.RUNNING,
                VideoSearchRunState.PAUSED,
            )) addRun(repo, normal, status.name, status,
                finishedAt = day, updatedAt = day)
            assertEquals(0, repo.pruneExpiredHistory(normal, now))
            assertEquals(3, repo.runs(normal).size)
            assertEquals(3, repo.runs(normal).sumOf {
                repo.films(normal, it.id, "site").size
            })
        } finally { db.close() }
    }

    @Test fun terminalRunWithoutCompletionTimestampIsProtected() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addRun(repo, normal, "legacy", VideoSearchRunState.FAILED, null)
            assertEquals(0, repo.pruneExpiredHistory(normal, now))
            assertNotNull(repo.run(normal, "legacy"))
        } finally { db.close() }
    }

    @Test fun runExactlyAtCutoffExpiresButNewerRunSurvives() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addRun(repo, normal, "boundary", finishedAt = cutoff)
            addRun(repo, normal, "new", finishedAt = cutoff + 1)
            assertEquals(1, repo.pruneExpiredHistory(normal, now))
            assertNull(repo.run(normal, "boundary"))
            assertNotNull(repo.run(normal, "new"))
        } finally { db.close() }
    }

    @Test fun recentlyUpdatedTerminalRunIsNotPurgedEarly() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addRun(repo, normal, "updated", finishedAt = day,
                updatedAt = cutoff + 1)
            assertEquals(0, repo.pruneExpiredHistory(normal, now))
            assertNotNull(repo.run(normal, "updated"))
        } finally { db.close() }
    }

    @Test fun foreignProfileAndIdenticalIdsAreNeverDeleted() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addRun(repo, normal, "same")
            addRun(repo, work, "same")
            assertEquals(1, repo.pruneExpiredHistory(normal, now))
            assertNull(repo.run(normal, "same"))
            assertNotNull(repo.run(work, "same"))
            assertEquals(1, db.dao().getTasks(work.profileId, "same").size)
            assertEquals(1, db.dao().getRunFilms(work.profileId, "same").size)
        } finally { db.close() }
    }

    @Test fun stoppedAndFailedRunExpireButSuspendedAreNotTouched() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addRun(repo, normal, "stopped", VideoSearchRunState.STOPPED)
            addRun(repo, normal, "failed", VideoSearchRunState.FAILED)
            addRun(repo, normal, "paused", VideoSearchRunState.PAUSED)
            assertEquals(2, repo.pruneExpiredHistory(normal, now))
            assertEquals(listOf("paused"), repo.runs(normal).map { it.id })
        } finally { db.close() }
    }

    @Test fun repeatCleanupIsIdempotentAndHistoryFlowReflectsDeletion() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addRun(repo, normal, "old")
            assertEquals(1, repo.observeRuns(normal).first().size)
            assertEquals(1, repo.pruneExpiredHistory(normal, now))
            assertEquals(0, repo.pruneExpiredHistory(normal, now))
            assertTrue(repo.observeRuns(normal).first().isEmpty())
        } finally { db.close() }
    }

    @Test fun siteSelectionAndPreferencesStayUnchanged() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val site = repo.addSite(normal, "https://example.org", newId = "site")
            repo.setSiteSelected(normal, site.id, true)
            repo.setParallelism(normal, VideoSearchParallelism.FIVE)
            repo.setResultsView(normal, VideoSearchView.LIST)
            addRun(repo, normal, "old")
            assertEquals(1, repo.pruneExpiredHistory(normal, now))
            assertEquals(listOf("site"), repo.sites(normal).map { it.id })
            assertEquals(setOf("site"), repo.settings(normal).selectedSiteIds)
            assertEquals(VideoSearchParallelism.FIVE, repo.settings(normal).parallelism)
            assertEquals(VideoSearchView.LIST, repo.settings(normal).view)
        } finally { db.close() }
    }

    @Test fun recentOrInvalidClockDoesNotDeleteAnyRun() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addRun(repo, normal, "old")
            assertEquals(0, repo.pruneExpiredHistory(normal, -3L))
            assertEquals(0, repo.pruneExpiredHistory(normal, day))
            assertEquals(1, repo.runs(normal).size)
        } finally { db.close() }
    }

    @Test fun onlyNormalProfileCanInvokePersistedRetention() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            addRun(repo, normal, "old")
            // Public API only accepts VideoSearchScope.Normal, never a Private scope.
            assertEquals(1, repo.pruneExpiredHistory(normal, now))
            assertEquals(0, repo.runs(normal).size)
        } finally { db.close() }
    }
}
