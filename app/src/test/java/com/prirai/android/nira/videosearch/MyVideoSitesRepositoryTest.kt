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

/**
 * Stage 2A: add first, configure later; only explicit confirmed removal.
 * Requires execution in the project's Robolectric environment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class MyVideoSitesRepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun database() =
        Room.inMemoryDatabaseBuilder(context, VideoSearchDatabase::class.java).build()

    @Test fun addingSitePersistsImmediatelyWithoutAnyConfiguration() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val normal = VideoSearchScope.Normal("default")
            val site = repo.addSite(normal, "example.org/catalog?view=movies", nowMs = 17, newId = "id1")
            assertEquals("https://example.org/catalog?view=movies", site.siteUrl)
            assertEquals(VideoSiteConfigurationState.NEEDS_CONFIGURATION, site.configurationState)
            assertNull(site.configuration)
            assertEquals(200, site.resultLimit)
            assertEquals("example.org", site.displayName)
            assertEquals(site, VideoSearchRepository(db.dao()).sites(normal).single())
        } finally { db.close() }
    }

    @Test fun duplicateSiteDoesNotOverwriteConfigurationOrIdentifier() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val profile = VideoSearchScope.Normal("default")
            val first = repo.addSite(profile, "https://example.org/", newId = "first")
            val configured = first.copy(
                displayName = "Moja nazwa",
                configurationState = VideoSiteConfigurationState.CONFIGURED,
                configuration = VideoSiteConfiguration("input", ".result"),
                resultLimit = 42,
            )
            repo.putSite(configured)
            val duplicate = repo.addSite(profile, "https://example.org/", newId = "second")
            assertEquals(configured, duplicate)
            assertEquals(listOf(configured), repo.sites(profile))
        } finally { db.close() }
    }

    @Test fun failedConfigurationNeverDeletesSite() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val normal = VideoSearchScope.Normal("default")
            val original = repo.addSite(normal, "example.org", newId = "site1")
            val failed = original.copy(configurationState = VideoSiteConfigurationState.REPAIR_REQUIRED)
            repo.putSite(failed)
            assertEquals(1, repo.sites(normal).size)
            assertEquals(VideoSiteConfigurationState.REPAIR_REQUIRED,
                repo.site(normal, "site1")?.configurationState)
        } finally { db.close() }
    }

    @Test fun removalNeedsConfirmationAndDoesNotAffectOtherProfiles() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val a = VideoSearchScope.Normal("default")
            val b = VideoSearchScope.Normal("work")
            repo.addSite(a, "example.org", newId = "shared")
            repo.addSite(b, "example.org", newId = "shared")
            try {
                repo.removeSite(a, "shared", confirmed = false)
                fail("Must not remove without approval")
            } catch (_: IllegalArgumentException) {}
            assertEquals(1, repo.sites(a).size)
            assertTrue(repo.removeSite(a, "shared", confirmed = true))
            assertTrue(repo.sites(a).isEmpty())
            assertEquals(1, repo.sites(b).size)
        } finally { db.close() }
    }

    @Test fun sitesSurviveDatabaseCloseAndReopen() = runBlocking {
        val name = "video-search-stage2a-test.db"
        context.deleteDatabase(name) // temporary Robolectric-only fixture
        try {
            val first = Room.databaseBuilder(context, VideoSearchDatabase::class.java, name).build()
            try {
                VideoSearchRepository(first.dao()).addSite(
                    VideoSearchScope.Normal("default"),
                    "https://example.org/film/search",
                    newId = "site1",
                )
            } finally { first.close() }
            val reopened = Room.databaseBuilder(context, VideoSearchDatabase::class.java, name).build()
            try {
                val entries = VideoSearchRepository(reopened.dao())
                    .sites(VideoSearchScope.Normal("default"))
                assertEquals(1, entries.size)
                assertEquals("site1", entries.single().id)
                assertEquals(VideoSiteConfigurationState.NEEDS_CONFIGURATION,
                    entries.single().configurationState)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun concurrentAddKeepsOneSiteAndMonotonicPositions() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val owner = VideoSearchScope.Normal("default")
            val duplicates = (1..12).map { n ->
                async(Dispatchers.Default) {
                    repo.addSite(owner, "same.example.org", newId = "same$n")
                }
            }.awaitAll()
            assertEquals(1, duplicates.map { it.id }.distinct().size)
            val newSites = (1..8).map { n ->
                async(Dispatchers.Default) {
                    repo.addSite(owner, "https://example$n.org", newId = "different$n")
                }
            }.awaitAll()
            assertEquals(8, newSites.map { it.id }.distinct().size)
            assertEquals((0..8).toList(), repo.sites(owner).map { it.position })
        } finally { db.close() }
    }

    @Test fun rejectsUnsupportedUrlWithoutCreatingRecords() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val owner = VideoSearchScope.Normal("default")
            for (raw in listOf("file:///sdcard/film", "http://user:password@example.org",
                "https://", "not a valid address")) {
                try {
                    repo.addSite(owner, raw)
                    fail("Bad site address was stored")
                } catch (_: IllegalArgumentException) {}
            }
            assertTrue(repo.sites(owner).isEmpty())
        } finally { db.close() }
    }
}
