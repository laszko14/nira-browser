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

/** ETAP 5D: one profile-scoped transactional read and view-only prefs update. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchResultsRepositoryTest {
    private val normal = VideoSearchScope.Normal("default")
    private val foreign = VideoSearchScope.Normal("work")
    private fun database() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication<Application>(), VideoSearchDatabase::class.java
    ).build()

    private fun run(owner: VideoSearchScope.Normal = normal, id: String = "r") =
        VideoSearchRun(
            id = id, scope = owner, query = "test",
            siteIds = listOf("a", "b"), parallelism = VideoSearchParallelism.TWO,
            status = VideoSearchRunState.RUNNING, startedAtMs = 100L,
        )

    private fun task(owner: VideoSearchScope.Normal = normal, siteId: String) =
        VideoSearchSiteTask(
            runId = "r", siteId = siteId, scope = owner, uniqueResults = 1,
            resultLimit = 200, visitedPages = 1, status = VideoSearchSiteState.STOPPED,
        )

    private fun film(owner: VideoSearchScope.Normal = normal, siteId: String,
                     index: Int = 0) = VideoSearchFilm(
        id = "$siteId-$index", runId = "r", siteId = siteId, scope = owner,
        siteName = "Snapshot $siteId",
        filmPageUrl = "https://$siteId.example.org/film?id=$index&lang=pl",
        originalTitle = "Film $index", thumbnailUrl = "https://cdn.example.org/$siteId.jpg",
        durationSeconds = 90L, titleSource = VideoResultTitleSource.CARD,
        imageSource = VideoResultImageSource.CARD, originalIndex = index,
        foundAtMs = 101L,
    )

    @Test fun transactionalSnapshotContainsRunTasksAndFilmsFromBothSites() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putSnapshot(run(), listOf(task(siteId = "a"), task(siteId = "b")),
                listOf(film(siteId = "a"), film(siteId = "b")))
            val result = repo.resultsSnapshot(normal, "r")!!
            assertEquals(listOf("a", "b"), result.run.siteIds)
            assertEquals(2, result.tasks.size)
            assertEquals(2, result.films.size)
            assertEquals(setOf("a", "b"), result.films.map { it.siteId }.toSet())
        } finally { db.close() }
    }

    @Test fun crossProfileCannotReadOtherProfilesRunEvenWithMatchingRunId() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putSnapshot(run(), listOf(task(siteId = "a")), listOf(film(siteId = "a")))
            assertNull(repo.resultsSnapshot(foreign, "r"))
            assertNull(repo.resultsSnapshot(normal, "nonexistent"))
        } finally { db.close() }
    }

    @Test fun persistedThumbnailTitleDurationAndFullUrlRemainAvailable() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putSnapshot(run(), listOf(task(siteId = "a")), listOf(film(siteId = "a")))
            val result = repo.resultsSnapshot(normal, "r")!!.films.single()
            assertEquals("https://a.example.org/film?id=0&lang=pl", result.filmPageUrl)
            assertEquals("https://cdn.example.org/a.jpg", result.thumbnailUrl)
            assertEquals("Film 0", result.originalTitle)
            assertEquals(90L, result.durationSeconds)
            assertEquals(VideoResultImageSource.CARD, result.imageSource)
            assertEquals("Snapshot a", result.siteName)
        } finally { db.close() }
    }

    @Test fun changingOnlyViewDoesNotClearSelectedSitesOrParallelism() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val site = repo.addSite(normal, "https://a.example.org", newId = "a")
            repo.setSiteSelected(normal, site.id, true)
            repo.setParallelism(normal, VideoSearchParallelism.FIVE)
            repo.setResultsView(normal, VideoSearchView.LIST)
            val settings = repo.settings(normal)
            assertEquals(VideoSearchView.LIST, settings.view)
            assertEquals(VideoSearchParallelism.FIVE, settings.parallelism)
            assertEquals(setOf("a"), settings.selectedSiteIds)
            repo.setResultsView(normal, VideoSearchView.TILES)
            assertEquals(setOf("a"), repo.settings(normal).selectedSiteIds)
        } finally { db.close() }
    }

    @Test fun newProfileViewPreferenceCanBeCreatedWithoutSiteOrRun() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.setResultsView(normal, VideoSearchView.LIST)
            assertEquals(VideoSearchView.LIST, repo.settings(normal).view)
            assertTrue(repo.selectedSiteIds(normal).isEmpty())
            assertTrue(repo.runs(normal).isEmpty())
        } finally { db.close() }
    }

    @Test fun normalProfileHistoryDoesNotIncludeFilmsFromForeignProfile() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putSnapshot(run(normal), listOf(task(siteId = "a")),
                listOf(film(siteId = "a")))
            repo.putSnapshot(run(foreign), listOf(task(foreign, "b")),
                listOf(film(foreign, "b")))
            assertEquals(listOf("a"),
                repo.resultsSnapshot(normal, "r")!!.films.map { it.siteId })
            assertEquals(listOf("b"),
                repo.resultsSnapshot(foreign, "r")!!.films.map { it.siteId })
        } finally { db.close() }
    }

    @Test fun savedSiteNamesRemainAvailableFromProfileSnapshot() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.addSite(normal, "https://a.example.org", newId = "a")
            repo.putSnapshot(run(), listOf(task(siteId = "a")), emptyList())
            val result = repo.resultsSnapshot(normal, "r")!!
            assertTrue(result.siteNames.containsKey("a"))
            assertFalse(result.siteNames.containsKey("b"))
            assertEquals(0, result.films.size)
            assertEquals(1, result.tasks.size)
        } finally { db.close() }
    }
}
