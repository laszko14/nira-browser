package com.prirai.android.nira.videosearch

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** ETAP 7A — scoped field-only preference, without clobbering saved site selection. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchDefaultGroupRepositoryTest {
    private val normal = VideoSearchScope.Normal("default")
    private val work = VideoSearchScope.Normal("work")
    private fun db() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication<Application>(),
        VideoSearchDatabase::class.java,
    ).build()

    @Test fun newProfileDefaultsToNoGroup() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            assertNull(repo.settings(normal).defaultTabGroupId)
        } finally { database.close() }
    }

    @Test fun selectedDefaultGroupPersistsAndCanBeCleared() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            repo.setDefaultVideoTabGroup(normal, "group-1")
            assertEquals("group-1", repo.settings(normal).defaultTabGroupId)
            repo.setDefaultVideoTabGroup(normal, null)
            assertNull(repo.settings(normal).defaultTabGroupId)
        } finally { database.close() }
    }

    @Test fun defaultGroupIsIsolatedBetweenNormalProfiles() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            repo.setDefaultVideoTabGroup(normal, "normal-group")
            repo.setDefaultVideoTabGroup(work, "work-group")
            assertEquals("normal-group", repo.settings(normal).defaultTabGroupId)
            assertEquals("work-group", repo.settings(work).defaultTabGroupId)
            repo.setDefaultVideoTabGroup(normal, null)
            assertEquals("work-group", repo.settings(work).defaultTabGroupId)
        } finally { database.close() }
    }

    @Test fun updatingGroupLeavesSelectionParallelismAndViewUntouched() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val site = repo.addSite(normal, "https://a.example.org", newId = "a")
            repo.setSiteSelected(normal, site.id, true)
            repo.setParallelism(normal, VideoSearchParallelism.FIVE)
            repo.setResultsView(normal, VideoSearchView.LIST)
            repo.setDefaultVideoTabGroup(normal, "group-a")
            val settings = repo.settings(normal)
            assertEquals(setOf("a"), settings.selectedSiteIds)
            assertEquals(VideoSearchParallelism.FIVE, settings.parallelism)
            assertEquals(VideoSearchView.LIST, settings.view)
            assertEquals("group-a", settings.defaultTabGroupId)
        } finally { database.close() }
    }

    @Test fun clearingGroupDoesNotResetExistingSiteSelection() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val site = repo.addSite(normal, "https://site.example.org", newId = "site")
            repo.setSiteSelected(normal, site.id, true)
            repo.setDefaultVideoTabGroup(normal, "group")
            repo.setDefaultVideoTabGroup(normal, null)
            assertEquals(setOf("site"), repo.settings(normal).selectedSiteIds)
        } finally { database.close() }
    }

    @Test fun rejectBlankGroupIdentifiersWithoutModifyingSettings() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            repo.setDefaultVideoTabGroup(normal, "good")
            try {
                repo.setDefaultVideoTabGroup(normal, "")
                fail("Blank group ID accepted")
            } catch (_: IllegalArgumentException) {}
            assertEquals("good", repo.settings(normal).defaultTabGroupId)
        } finally { database.close() }
    }

    @Test fun groupFieldAndParallelismDoNotClobberConcurrentWrites() = runBlocking {
        val database = db()
        try {
            val repo = VideoSearchRepository(database.dao())
            val a = async { repo.setDefaultVideoTabGroup(normal, "group") }
            val b = async { repo.setParallelism(normal, VideoSearchParallelism.THREE) }
            a.await()
            b.await()
            assertEquals("group", repo.settings(normal).defaultTabGroupId)
            assertEquals(VideoSearchParallelism.THREE, repo.settings(normal).parallelism)
        } finally { database.close() }
    }
}
