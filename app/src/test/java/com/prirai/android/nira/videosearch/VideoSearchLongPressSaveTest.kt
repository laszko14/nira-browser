package com.prirai.android.nira.videosearch

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * ETAP 7B: long-press saved links are local, transactional and per profile.
 * No image/video download or cross-profile folder writes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchLongPressSaveTest {
    private val normal = VideoSearchScope.Normal("default")
    private val other = VideoSearchScope.Normal("other")
    private fun db() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication<Application>(),
        VideoSearchDatabase::class.java
    ).build()

    private fun search(scope: VideoSearchScope.Normal, id: String = "run",
                       query: String = "Film & kino") = VideoSearchRun(
        id = id, scope = scope, query = query, siteIds = listOf("site"),
        parallelism = VideoSearchParallelism.ONE,
        status = VideoSearchRunState.RUNNING, startedAtMs = 1L,
    )
    private fun task(scope: VideoSearchScope.Normal, id: String = "run") =
        VideoSearchSiteTask(runId = id, siteId = "site", scope = scope,
            status = VideoSearchSiteState.ACTIVE, resultLimit = 200,
            uniqueResults = 1, visitedPages = 1)
    private fun film(scope: VideoSearchScope.Normal, id: String = "film",
                     run: String = "run") = VideoSearchFilm(
        id = id, runId = run, siteId = "site", scope = scope,
        siteName = "Witryna", filmPageUrl =
            "https://example.org/watch?id=1&lang=pl&quality=high",
        originalTitle = "Pełny oryginalny tytuł filmu",
        thumbnailUrl = "https://cdn.example.org/thumbnail.jpg",
        durationSeconds = 223L,
        titleSource = VideoResultTitleSource.CARD,
        imageSource = VideoResultImageSource.CARD,
        originalIndex = 0, foundAtMs = 2L,
    )
    private suspend fun seed(repo: VideoSearchRepository,
                             scope: VideoSearchScope.Normal = normal) {
        repo.putSnapshot(search(scope), listOf(task(scope)), listOf(film(scope)))
    }

    @Test fun firstSaveCreatesFolderNamedExactlyLikeQuery() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            seed(repo)
            assertTrue(repo.saveFoundFilmToPhraseFolder(normal, "run", "film"))
            val folder = repo.folders(normal).single()
            assertEquals("Film & kino", folder.name)
            assertEquals(1, repo.savedLinks(normal, folder.id).size)
        } finally { database.close() }
    }

    @Test fun savedLinkContainsFullOriginalUrlTitlePosterAndDuration() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            seed(repo)
            repo.saveFoundFilmToPhraseFolder(normal, "run", "film")
            val link = repo.savedLinks(normal, repo.folders(normal).single().id).single()
            assertEquals(film(normal).filmPageUrl, link.filmPageUrl)
            assertEquals(film(normal).originalTitle, link.title)
            assertEquals(film(normal).thumbnailUrl, link.thumbnailUrl)
            assertEquals(223L, link.durationSeconds)
        } finally { database.close() }
    }

    @Test fun duplicatePressDoesNotAddDuplicateFilmToFolder() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            seed(repo)
            assertTrue(repo.saveFoundFilmToPhraseFolder(normal, "run", "film"))
            assertFalse(repo.saveFoundFilmToPhraseFolder(normal, "run", "film"))
            assertEquals(1, repo.folders(normal).size)
            assertEquals(1, repo.savedLinks(normal, repo.folders(normal).single().id).size)
        } finally { database.close() }
    }

    @Test fun concurrentSavesYieldExactlyOneFolderAndOneLink() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            seed(repo)
            val saves = (0 until 5).map {
                async { repo.saveFoundFilmToPhraseFolder(normal, "run", "film") }
            }.awaitAll()
            assertEquals(1, saves.count { it })
            assertEquals(1, repo.folders(normal).size)
            assertEquals(1, repo.savedLinks(normal, repo.folders(normal).single().id).size)
        } finally { database.close() }
    }

    @Test fun existingExactlyNamedPhraseFolderIsReused() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            seed(repo)
            repo.putFolder(VideoSavedFilmFolder(
                id = "existing-folder", scope = normal, name = "Film & kino",
                createdAtMs = 0L
            ))
            repo.saveFoundFilmToPhraseFolder(normal, "run", "film")
            assertEquals(1, repo.folders(normal).size)
            assertEquals("existing-folder", repo.folders(normal).single().id)
            assertEquals(1, repo.savedLinks(normal, "existing-folder").size)
        } finally { database.close() }
    }

    @Test fun differentFolderNameIsNotReusedOrRenamed() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            seed(repo)
            repo.putFolder(VideoSavedFilmFolder(
                id = "other-folder", scope = normal, name = "Film & Kino",
                createdAtMs = 0L
            ))
            repo.saveFoundFilmToPhraseFolder(normal, "run", "film")
            assertEquals(setOf("Film & Kino", "Film & kino"),
                repo.folders(normal).map { it.name }.toSet())
        } finally { database.close() }
    }

    @Test fun foreignProfileCannotReadOrWriteTheNormalRun() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            seed(repo)
            try {
                repo.saveFoundFilmToPhraseFolder(other, "run", "film")
                fail("Saved film from another profile")
            } catch (_: IllegalArgumentException) {}
            assertTrue(repo.folders(other).isEmpty())
            assertTrue(repo.folders(normal).isEmpty())
        } finally { database.close() }
    }

    @Test fun staleFilmIdCannotCreateEmptyFolder() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            seed(repo)
            try {
                repo.saveFoundFilmToPhraseFolder(normal, "run", "missing-film")
                fail("Stale film accepted")
            } catch (_: IllegalArgumentException) {}
            assertTrue(repo.folders(normal).isEmpty())
        } finally { database.close() }
    }

    @Test fun staleRunIdCannotCreateEmptyFolder() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            seed(repo)
            try {
                repo.saveFoundFilmToPhraseFolder(normal, "no-run", "film")
                fail("Missing run accepted")
            } catch (_: IllegalArgumentException) {}
            assertTrue(repo.folders(normal).isEmpty())
        } finally { database.close() }
    }

    @Test fun wrongRunForExistingFilmIsRejectedBeforeCreatingFolder() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            seed(repo)
            repo.putSnapshot(search(normal, "other-run", "Inna fraza"),
                listOf(task(normal, "other-run")), emptyList())
            try {
                repo.saveFoundFilmToPhraseFolder(normal, "other-run", "film")
                fail("Film was attached to an unrelated query")
            } catch (_: IllegalArgumentException) {}
            assertTrue(repo.folders(normal).isEmpty())
        } finally { database.close() }
    }

    @Test fun savedLinksRemainIndependentFromRunLists() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            seed(repo)
            repo.saveFoundFilmToPhraseFolder(normal, "run", "film")
            val folder = repo.folders(normal).single()
            assertEquals(1, repo.savedLinks(normal, folder.id).size)
            assertEquals("run", repo.runs(normal).single().id)
        } finally { database.close() }
    }
}
