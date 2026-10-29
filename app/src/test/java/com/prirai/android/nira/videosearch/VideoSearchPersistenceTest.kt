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

/** Focused persistence regressions. Execution awaits explicit unit-test run. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchPersistenceTest {
    private fun database() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(), VideoSearchDatabase::class.java
    ).build()

    private fun site(scope: VideoSearchScope, id: String = "same") = VideoSearchSite(
        id = id, scope = scope, siteUrl = "https://example.org/search?q=a&b=1",
        displayName = "Moja witryna", position = 0, createdAtMs = 123L,
    )

    @Test fun equalIdsInDifferentProfilesDoNotOverwriteEachOther() = runBlocking {
        val db = database()
        try {
            val repository = VideoSearchRepository(db.dao())
            val a = VideoSearchScope.Normal("default")
            val b = VideoSearchScope.Normal("work")
            repository.putSite(site(a))
            repository.putSite(site(b).copy(displayName = "Druga"))
            assertEquals("Moja witryna", repository.sites(a).single().displayName)
            assertEquals("Druga", repository.sites(b).single().displayName)
            assertFalse(repository.removeSite(a, "same", confirmed = true).not())
            assertTrue(repository.sites(a).isEmpty())
            assertEquals(1, repository.sites(b).size)
        } finally { db.close() }
    }

    @Test fun unconfiguredSiteAndSelectorsRoundTripWithoutLoss() = runBlocking {
        val db = database()
        try {
            val repository = VideoSearchRepository(db.dao())
            val normal = VideoSearchScope.Normal("default")
            val original = site(normal)
            repository.putSite(original)
            assertEquals(VideoSiteConfigurationState.NEEDS_CONFIGURATION,
                repository.site(normal, "same")?.configurationState)
            val cfg = VideoSiteConfiguration(
                searchInputSelector = "input[name='q']",
                resultsCardSelector = "article.result",
                submission = VideoSearchSubmission.CLICK_SUBMIT,
                submitSelector = "#search",
                titleSelector = ".title",
                linkSelector = "a[href]",
                thumbnailSelector = "img[data-src]",
                durationSelector = ".length",
                pagination = VideoSearchPagination.LOAD_MORE,
                nextPageSelector = "button.more",
            )
            repository.putSite(original.copy(
                configuration = cfg,
                configurationState = VideoSiteConfigurationState.CONFIGURED
            ))
            assertEquals(cfg, repository.site(normal, "same")!!.configuration)
        } finally { db.close() }
    }

    @Test fun settingsAndOriginalUrlAndSavedFoldersSurviveReload() = runBlocking {
        val db = database()
        try {
            val owner = VideoSearchScope.Normal("default")
            val repo1 = VideoSearchRepository(db.dao())
            repo1.putSettings(VideoSearchSettings(
                owner, setOf("s1", "s2", "s3", "s4", "s5", "s6"),
                VideoSearchParallelism.ALL, view = VideoSearchView.LIST,
                sortBySiteId = mapOf("s1" to VideoSearchSort.DURATION_LONGEST),
            ))
            val run = VideoSearchRun("run", owner, "fraza",
                listOf("s1"), VideoSearchParallelism.ALL, VideoSearchRunState.RUNNING, 10)
            repo1.putRun(run)
            val film = VideoSearchFilm(
                "film", "run", "s1", owner, "Witryna",
                "https://example.org/movie?id=1&episode=2",
                "Tytuł bez skracania ".repeat(80), null, null,
                originalIndex = 0, foundAtMs = 15
            )
            repo1.putFilm(film)
            repo1.putFolder(VideoSavedFilmFolder("folder", owner, "fraza", 1))
            val link = VideoSavedFilmLink("link", "folder", owner,
                film.filmPageUrl, film.originalTitle, null, null, 16)
            assertTrue(repo1.addSavedLink(link))
            assertFalse(repo1.addSavedLink(link.copy(id = "different-id")))
            val repo2 = VideoSearchRepository(db.dao())
            assertEquals(6, repo2.settings(owner).selectedSiteIds.size)
            assertEquals(VideoSearchParallelism.ALL, repo2.settings(owner).parallelism)
            assertEquals(film, repo2.films(owner, "run", "s1").single())
            assertEquals(link, repo2.savedLinks(owner, "folder").single())
            assertEquals("fraza", repo2.folders(owner).single().name)
        } finally { db.close() }
    }

    @Test fun privateDataCannotReachRoomAndEndsWithPrivateLifetime() = runBlocking {
        val db = database()
        try {
            val repository = VideoSearchRepository(db.dao())
            val scope = VideoSearchScope.Private("default", "session1")
            try {
                repository.putSite(site(scope))
                fail("Private writes must be rejected")
            } catch (_: IllegalArgumentException) {}
            val memory = VideoSearchPrivateStore()
            memory.start(scope)
            memory.putSite(site(scope))
            assertEquals(1, memory.sites(scope).size)
            memory.finish(scope)
            try {
                memory.sites(scope)
                fail("Finished session must be inaccessible")
            } catch (_: IllegalStateException) {}
            try {
                memory.start(scope)
                fail("Stale callback must not resurrect old session")
            } catch (_: IllegalStateException) {}
            assertTrue(repository.sites(VideoSearchScope.Normal("default")).isEmpty())
        } finally { db.close() }
    }

    @Test fun foreignSiteIdIsRejectedWhenSavingTaskAndResult() = runBlocking {
        val db = database()
        try {
            val repository = VideoSearchRepository(db.dao())
            val owner = VideoSearchScope.Normal("default")
            repository.putRun(VideoSearchRun(
                "run", owner, "q", listOf("a"), VideoSearchParallelism.ONE,
                VideoSearchRunState.RUNNING, 1
            ))
            try {
                repository.putTask(VideoSearchSiteTask("run", "other", owner))
                fail("Task site must be part of original run")
            } catch (_: IllegalArgumentException) {}
            try {
                repository.putFilm(VideoSearchFilm("r", "run", "other", owner,
                    "Witryna", "https://example.org/movie", originalIndex = 0, foundAtMs = 3))
                fail("Result site must be part of original run")
            } catch (_: IllegalArgumentException) {}
        } finally { db.close() }
    }
}
