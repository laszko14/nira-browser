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

/** Stage 8A: real Room saved-folder list and one-folder live contents. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSavedLibraryRepositoryTest {
    private val normal = VideoSearchScope.Normal("default")
    private val other = VideoSearchScope.Normal("work")
    private fun database() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication<Application>(),
        VideoSearchDatabase::class.java,
    ).build()
    private fun folder(
        id: String, scope: VideoSearchScope.Normal = normal,
        name: String = "Hip-hop",
    ) = VideoSavedFilmFolder(
        id = id, scope = scope, name = name, createdAtMs = 100L,
    )
    private fun link(
        folderId: String, id: String, scope: VideoSearchScope.Normal = normal,
        url: String = "https://example.org/film?id=$id&quality=high",
    ) = VideoSavedFilmLink(
        id = id, scope = scope, folderId = folderId,
        filmPageUrl = url, title = "Film $id",
        thumbnailUrl = "https://example.org/thumb?id=$id",
        durationSeconds = 153L, createdAtMs = 200L,
    )

    @Test fun emptyProfileHasEmptySavedFolderLibrary() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val library = repo.observeSavedLibrary(normal, null).first()
            assertTrue(library.folders.isEmpty())
            assertTrue(library.links.isEmpty())
            assertNull(library.openedFolder)
        } finally { db.close() }
    }

    @Test fun listsSavedFoldersWithActualLinkCountsNotSearchResultCounts() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("a"))
            repo.putFolder(folder("b", name = "Rock"))
            repo.addSavedLink(link("a", "1"))
            repo.addSavedLink(link("a", "2"))
            val library = repo.observeSavedLibrary(normal, null).first()
            assertEquals(listOf("a", "b"), library.folders.map { it.folder.id })
            assertEquals(listOf(2L, 0L), library.folders.map { it.savedCount })
            assertTrue(library.links.isEmpty())
        } finally { db.close() }
    }

    @Test fun openingOneFolderRetrievesOnlyItsSavedLinks() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("a"))
            repo.putFolder(folder("b"))
            repo.addSavedLink(link("a", "first"))
            repo.addSavedLink(link("b", "second"))
            val library = repo.observeSavedLibrary(normal, "a").first()
            assertEquals("a", library.openedFolder?.id)
            assertEquals(listOf("first"), library.links.map { it.id })
            assertEquals("https://example.org/film?id=first&quality=high",
                library.links.single().filmPageUrl)
        } finally { db.close() }
    }

    @Test fun absentFolderNeverExposesStaleLinks() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("exists"))
            repo.addSavedLink(link("exists", "first"))
            val library = repo.observeSavedLibrary(normal, "missing").first()
            assertNull(library.openedFolder)
            assertTrue(library.links.isEmpty())
            assertEquals(1, library.folders.size)
        } finally { db.close() }
    }

    @Test fun twoProfilesWithSameFolderIdCannotSeeEachOthersLinks() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("common"))
            repo.putFolder(folder("common", other, "Prywatny profil"))
            repo.addSavedLink(link("common", "a"))
            repo.addSavedLink(link("common", "b", other))
            val root = repo.observeSavedLibrary(normal, null).first()
            assertEquals(1L, root.folders.single().savedCount)
            assertEquals("Hip-hop", root.folders.single().folder.name)
            assertEquals(listOf("a"),
                repo.observeSavedLibrary(normal, "common").first().links.map { it.id })
            assertEquals(listOf("b"),
                repo.observeSavedLibrary(other, "common").first().links.map { it.id })
        } finally { db.close() }
    }

    @Test fun sameExactUrlIsDeduplicatedButDifferentQueriesAreDistinct() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("a"))
            assertTrue(repo.addSavedLink(link("a", "a", url =
                "https://example.org/watch?id=1&lang=pl")))
            assertFalse(repo.addSavedLink(link("a", "b", url =
                "https://example.org/watch?id=1&lang=pl")))
            assertTrue(repo.addSavedLink(link("a", "c", url =
                "https://example.org/watch?id=1&lang=en")))
            assertEquals(2L, repo.observeSavedLibrary(normal, null)
                .first().folders.single().savedCount)
            assertEquals(2, repo.observeSavedLibrary(normal, "a").first().links.size)
        } finally { db.close() }
    }

    @Test fun folderCreationEmitsWithoutPolling() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val awaited = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000) {
                    repo.observeSavedLibrary(normal, null).first {
                        it.folders.any { row -> row.folder.id == "new" }
                    }
                }
            }
            repo.putFolder(folder("new"))
            assertEquals("new", awaited.await().folders.single().folder.id)
        } finally { db.close() }
    }

    @Test fun savingFilmUpdatesFolderCountAndOpenedLinkFlow() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("saved"))
            val awaited = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000) {
                    repo.observeSavedLibrary(normal, "saved").first {
                        it.folders.singleOrNull()?.savedCount == 1L &&
                            it.links.size == 1
                    }
                }
            }
            assertTrue(repo.addSavedLink(link("saved", "video")))
            val latest = awaited.await()
            assertEquals("video", latest.links.single().id)
            assertEquals("Film video", latest.links.single().title)
            assertEquals("https://example.org/thumb?id=video",
                latest.links.single().thumbnailUrl)
            assertEquals(153L, latest.links.single().durationSeconds)
        } finally { db.close() }
    }

    @Test fun dataIsIndependentFromUnrelatedSearchRunHistory() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("saved"))
            repo.addSavedLink(link("saved", "video"))
            assertTrue(repo.runs(normal).isEmpty())
            assertEquals("video",
                repo.observeSavedLibrary(normal, "saved").first().links.single().id)
        } finally { db.close() }
    }

    @Test fun aSecondNormalProfileWithoutFoldersStaysEmpty() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("a"))
            repo.addSavedLink(link("a", "film"))
            assertTrue(repo.observeSavedLibrary(other, null).first().folders.isEmpty())
            assertTrue(repo.observeSavedLibrary(other, "a").first().links.isEmpty())
        } finally { db.close() }
    }
}
