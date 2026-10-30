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

/** Stage 1C safety regression tests; these still need actual execution. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchTransactionalTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun memoryDb() =
        Room.inMemoryDatabaseBuilder(context, VideoSearchDatabase::class.java).build()
    private val owner get() = VideoSearchScope.Normal("default")

    private fun newRun(profile: VideoSearchScope = owner, id: String = "r") =
        VideoSearchRun(id, profile, "test movie", listOf("a", "b"),
            VideoSearchParallelism.TWO, VideoSearchRunState.RUNNING, 100L)

    private fun newFilm(
        scope: VideoSearchScope = owner,
        id: String = "film1",
        url: String = "https://example.org/watch?id=1&episode=1",
        runId: String = "r",
        siteId: String = "a",
    ) = VideoSearchFilm(
        id = id, runId = runId, siteId = siteId, scope = scope, siteName = "Filmy",
        filmPageUrl = url, originalTitle = "pełny tytuł", originalIndex = 0,
        foundAtMs = 101L
    )

    private suspend fun rejects(block: suspend () -> Unit) {
        try {
            block()
            fail("Invalid data was accepted")
        } catch (_: IllegalArgumentException) {
            // Expected; inspect the DB separately to detect partial writes.
        }
    }

    @Test fun runSnapshotCannotChangeAfterFirstTask() = runBlocking {
        val db = memoryDb()
        try {
            val repo = VideoSearchRepository(db.dao())
            val first = newRun()
            repo.putRun(first)
            repo.putTask(VideoSearchSiteTask("r", "a", owner, uniqueResults = 3))
            rejects { repo.putRun(first.copy(siteIds = listOf("b"))) }
            rejects { repo.putRun(first.copy(query = "different")) }
            rejects { repo.putRun(first.copy(startedAtMs = 9)) }
            assertEquals(first, repo.run(owner, "r"))
            assertEquals(3, repo.tasks(owner, "r").single().uniqueResults)
            repo.putRun(first.copy(status = VideoSearchRunState.COMPLETED,
                updatedAtMs = 102L, completedAtMs = 102L))
            assertEquals(VideoSearchRunState.COMPLETED, repo.run(owner, "r")?.status)
            rejects { repo.putRun(first) }
        } finally { db.close() }
    }

    @Test fun taskCountersCannotBeRolledBack() = runBlocking {
        val db = memoryDb()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putRun(newRun())
            val task = VideoSearchSiteTask("r", "a", owner, uniqueResults = 5,
                visitedPages = 3, resultLimit = 200)
            repo.putTask(task)
            rejects { repo.putTask(task.copy(uniqueResults = 4)) }
            rejects { repo.putTask(task.copy(visitedPages = 2)) }
            rejects { repo.putTask(task.copy(resultLimit = 201)) }
            assertEquals(task, repo.tasks(owner, "r").single())
        } finally { db.close() }
    }

    @Test fun filmIdentityIsStableAndExactUrlIsDeduplicated() = runBlocking {
        val db = memoryDb()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putRun(newRun())
            val first = newFilm()
            assertTrue(repo.putFilm(first))
            assertFalse(repo.putFilm(first.copy(id = "duplicate", originalIndex = 1)))
            assertTrue(repo.putFilm(first.copy(id = "different-film",
                filmPageUrl = "https://example.org/watch?id=1&episode=2",
                originalIndex = 1)))
            rejects {
                repo.putFilm(first.copy(siteId = "b", filmPageUrl = first.filmPageUrl))
            }
            assertEquals(2, repo.films(owner, "r", "a").size)
            assertTrue(repo.films(owner, "r", "b").isEmpty())
        } finally { db.close() }
    }

    @Test fun concurrentSameUrlOnlyOneDistinctIdIsInserted() = runBlocking {
        val db = memoryDb()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putRun(newRun())
            val results = (1..12).map { index ->
                async(Dispatchers.Default) { repo.putFilm(newFilm(id = "f$index")) }
            }.awaitAll()
            assertEquals(1, results.count { it })
            assertEquals(1, repo.films(owner, "r", "a").size)
        } finally { db.close() }
    }

    @Test fun failedTransactionNeverCreatesAnOrphanTask() = runBlocking {
        val db = memoryDb()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putRun(newRun())
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_task BEFORE INSERT ON video_search_tasks " +
                    "BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END"
            )
            try {
                repo.putTask(VideoSearchSiteTask("r", "a", owner))
                fail("Expected SQL rollback")
            } catch (_: android.database.SQLException) {
                // Expected from the trigger.
            }
            assertTrue(repo.tasks(owner, "r").isEmpty())
            assertNotNull(repo.run(owner, "r"))
        } finally { db.close() }
    }

    @Test fun failedBatchRollsBackRunCountersAndFilms() = runBlocking {
        val db = memoryDb()
        try {
            val repo = VideoSearchRepository(db.dao())
            val run = newRun()
            val oldTask = VideoSearchSiteTask("r", "a", owner, uniqueResults = 1)
            repo.putSnapshot(run, listOf(oldTask), listOf(newFilm()))
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_batch_film BEFORE INSERT ON video_search_films " +
                    "BEGIN SELECT RAISE(ABORT, 'batch failure'); END"
            )
            val nextRun = run.copy(status = VideoSearchRunState.COMPLETED,
                updatedAtMs = 120L, completedAtMs = 120L)
            val newTask = oldTask.copy(uniqueResults = 2)
            try {
                repo.putSnapshot(nextRun, listOf(newTask),
                    listOf(newFilm(id = "film2",
                        url = "https://example.org/watch?id=2&episode=1")))
                fail("Expected SQL failure")
            } catch (_: android.database.SQLException) {
                // The prior run/task updates must be rolled back too.
            }
            assertEquals(run, repo.run(owner, "r"))
            assertEquals(oldTask, repo.tasks(owner, "r").single())
            assertEquals(1, repo.films(owner, "r", "a").size)
        } finally { db.close() }
    }

    @Test fun savedFolderCheckAndInsertAreAtomicAndProfileBound() = runBlocking {
        val db = memoryDb()
        try {
            val repo = VideoSearchRepository(db.dao())
            val work = VideoSearchScope.Normal("work")
            val folder = VideoSavedFilmFolder("folder", owner, "Własne", 1L)
            repo.putFolder(folder)
            val link = VideoSavedFilmLink("link", "folder", owner,
                "https://example.org/watch?film=7&language=pl", null, null, null, 2L)
            assertTrue(repo.addSavedLink(link))
            assertFalse(repo.addSavedLink(link.copy(id = "another")))
            rejects { repo.addSavedLink(link.copy(
                scope = work, id = "foreign", filmPageUrl = "https://example.org/watch?film=9"
            )) }
            assertEquals(listOf(link), repo.savedLinks(owner, "folder"))
            assertTrue(repo.savedLinks(work, "folder").isEmpty())
        } finally { db.close() }
    }

    @Test fun onlyWebPagesCanBeStoredWithoutRewritingParameters() = runBlocking {
        val db = memoryDb()
        try {
            val repo = VideoSearchRepository(db.dao())
            val site = VideoSearchSite("site", owner,
                "https://example.org/?tag=drama&lang=pl", "Katalog", 0, createdAtMs = 1)
            rejects { repo.putSite(site.copy(siteUrl = "javascript:alert(1)")) }
            rejects { repo.putSite(site.copy(siteUrl = "https://user:pass@example.org")) }
            assertTrue(repo.sites(owner).isEmpty())
            repo.putSite(site)
            assertEquals(site.siteUrl, repo.sites(owner).single().siteUrl)
            repo.putRun(newRun())
            rejects {
                repo.putFilm(newFilm(url = "file:///sdcard/movie.mp4"))
            }
            assertTrue(repo.films(owner, "r", "a").isEmpty())
        } finally { db.close() }
    }

    @Test fun fileBackedV2DatabaseSurvivesCloseAndReopen() = runBlocking {
        val name = "video-search-stage1c-test.db"
        context.deleteDatabase(name) // isolated Robolectric test DB, not user data
        try {
            val firstDb = Room.databaseBuilder(context, VideoSearchDatabase::class.java, name).build()
            try {
                val repo = VideoSearchRepository(firstDb.dao())
                repo.putFolder(VideoSavedFilmFolder("saved", owner, "Na później", 123))
                repo.putRun(newRun())
                repo.putFilm(newFilm())
                assertEquals(2, firstDb.openHelper.readableDatabase.version)
            } finally { firstDb.close() }
            val openedAgain = Room.databaseBuilder(context,
                VideoSearchDatabase::class.java, name).build()
            try {
                val repo = VideoSearchRepository(openedAgain.dao())
                assertEquals("Na później", repo.folders(owner).single().name)
                assertEquals("test movie", repo.run(owner, "r")?.query)
                assertEquals(1, repo.films(owner, "r", "a").size)
            } finally { openedAgain.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun privateLifetimesAreIsolatedAndClosedOneCannotResume() {
        val store = VideoSearchPrivateStore()
        val a = VideoSearchScope.Private("default", "epoch-A")
        val b = VideoSearchScope.Private("default", "epoch-B")
        store.start(a)
        store.start(b)
        store.putRun(newRun(a))
        store.putRun(newRun(b))
        assertTrue(store.putFilm(newFilm(scope = a)))
        assertFalse(store.putFilm(newFilm(scope = a, id = "dup")))
        assertEquals(1, store.films(a, "r", "a").size)
        assertTrue(store.films(b, "r", "a").isEmpty())
        store.finish(a)
        try {
            store.putFilm(newFilm(scope = a, id = "late"))
            fail("Closed private lifetime accepted late result")
        } catch (_: IllegalStateException) {}
        assertEquals(1, store.runs(b).size)
    }
}
