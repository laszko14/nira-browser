package com.prirai.android.nira.videosearch

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Stage 6C — Room emits progress when a worker checkpoints a normal run.
 * No timer and no scan of an unrelated profile's stored films.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchResultsLiveUpdatesTest {
    private val normal = VideoSearchScope.Normal("default")
    private val foreign = VideoSearchScope.Normal("work")
    private fun db() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        VideoSearchDatabase::class.java
    ).build()

    private fun run(scope: VideoSearchScope.Normal, updated: Long = 100L) =
        VideoSearchRun(
            id = "run-live", scope = scope, query = "film",
            siteIds = listOf("site"), parallelism = VideoSearchParallelism.ONE,
            status = VideoSearchRunState.RUNNING,
            startedAtMs = 100L, updatedAtMs = updated,
        )

    private fun task(scope: VideoSearchScope.Normal, results: Int = 0) =
        VideoSearchSiteTask(
            runId = "run-live", siteId = "site", scope = scope,
            resultLimit = 200, status = VideoSearchSiteState.ACTIVE,
            uniqueResults = results, visitedPages = if (results == 0) 0 else 1,
        )

    private fun film(scope: VideoSearchScope.Normal) = VideoSearchFilm(
        id = "unique-film", runId = "run-live", siteId = "site", scope = scope,
        siteName = "Moja witryna", filmPageUrl = "https://site.example.org/watch?id=1",
        originalTitle = "Film 1", thumbnailUrl = null, durationSeconds = null,
        titleSource = VideoResultTitleSource.CARD,
        imageSource = VideoResultImageSource.UNKNOWN,
        originalIndex = 0, foundAtMs = 105L,
    )

    @Test fun newlyCreatedRunIsObservedWithoutAnyTimer() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val awaited = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000) {
                    repo.observeRuns(normal).first { list ->
                        list.any { it.id == "run-live" }
                    }
                }
            }
            repo.putSnapshot(run(normal), listOf(task(normal)), emptyList())
            assertEquals("run-live", awaited.await().single().id)
        } finally { database.close() }
    }

    @Test fun everyWorkerCheckpointInvalidatesActiveRunList() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            repo.putSnapshot(run(normal), listOf(task(normal)), emptyList())
            val awaited = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000) {
                    repo.observeRuns(normal).first { runs ->
                        runs.any { it.id == "run-live" && it.updatedAtMs == 110L }
                    }
                }
            }
            repo.putSnapshot(
                run(normal, updated = 110L), listOf(task(normal, results = 1)),
                listOf(film(normal)),
            )
            assertEquals(110L, awaited.await().single().updatedAtMs)
            val latest = repo.resultsSnapshot(normal, "run-live")!!
            assertEquals(1, latest.films.size)
            assertEquals("Film 1", latest.films.single().originalTitle)
        } finally { database.close() }
    }

    @Test fun aRunCannotBeReadFromAnotherNormalProfile() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            repo.putSnapshot(run(normal), listOf(task(normal)), emptyList())
            assertTrue(repo.observeRuns(foreign).first().isEmpty())
            assertNull(repo.resultsSnapshot(foreign, "run-live"))
        } finally { database.close() }
    }

    @Test fun viewSettingUpdateDoesNotRemoveSavedRunOrFilm() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            repo.putSnapshot(run(normal), listOf(task(normal, 1)),
                listOf(film(normal)))
            repo.setResultsView(normal, VideoSearchView.LIST)
            assertEquals(1, repo.resultsSnapshot(normal, "run-live")!!.films.size)
            assertEquals(VideoSearchView.LIST, repo.settings(normal).view)
        } finally { database.close() }
    }
}
