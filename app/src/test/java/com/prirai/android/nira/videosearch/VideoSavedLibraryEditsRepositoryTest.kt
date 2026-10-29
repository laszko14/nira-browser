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

/** Real in-memory Room transactions; source only, NOT EXECUTED. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSavedLibraryEditsRepositoryTest {
    private val normal = VideoSearchScope.Normal("default")
    private val other = VideoSearchScope.Normal("work")
    private fun database() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        VideoSearchDatabase::class.java,
    ).build()

    private fun folder(id: String, name: String = "Folder",
        owner: VideoSearchScope.Normal = normal) =
        VideoSavedFilmFolder(id, owner, name, 100L)

    private fun film(id: String, folderId: String,
        owner: VideoSearchScope.Normal = normal,
        url: String = "https://example.org/watch?id=$id&quality=high") =
        VideoSavedFilmLink(id, folderId, owner, url, "Film $id",
            "https://example.org/thumb?id=$id", 180L, 200L)

    @Test fun createFolderPersistsAndNormalizesWhitespace() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            assertTrue(repo.editSavedLibrary(normal, VideoSavedLibraryEdit.CreateFolder("  Rap  ")))
            assertEquals("Rap", repo.folders(normal).single().name)
        } finally { db.close() }
    }

    @Test fun caseInsensitiveDuplicateFolderNameDoesNotOverwriteExisting() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("first", "Hip-Hop"))
            assertFalse(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.CreateFolder("  hip-hop ")))
            assertEquals(listOf("first"), repo.folders(normal).map { it.id })
        } finally { db.close() }
    }

    @Test fun sameNameInDifferentNormalProfilesIsAllowed() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            assertTrue(repo.editSavedLibrary(normal, VideoSavedLibraryEdit.CreateFolder("Muzyka")))
            assertTrue(repo.editSavedLibrary(other, VideoSavedLibraryEdit.CreateFolder("Muzyka")))
            assertEquals(1, repo.folders(normal).size)
            assertEquals(1, repo.folders(other).size)
        } finally { db.close() }
    }

    @Test fun renameFolderPreservesLinksAndOriginalUrl() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("a", "Dawna"))
            repo.addSavedLink(film("one", "a"))
            assertTrue(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.RenameFolder("a", " Nowa ")))
            assertEquals("Nowa", repo.folders(normal).single().name)
            assertEquals(film("one", "a").filmPageUrl, repo.savedLinks(normal, "a").single().filmPageUrl)
        } finally { db.close() }
    }

    @Test fun renameCollisionLeavesOldNameAndAllLinks() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("a", "Film"))
            repo.putFolder(folder("b", "Kino"))
            repo.addSavedLink(film("one", "a"))
            assertFalse(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.RenameFolder("a", " kINO ")))
            assertEquals("Film", repo.folders(normal).first { it.id == "a" }.name)
            assertEquals(1, repo.savedLinks(normal, "a").size)
        } finally { db.close() }
    }

    @Test fun copyingFolderCreatesIndependentFolderAndCopiesExactMetadata() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("a"))
            repo.addSavedLink(film("one", "a"))
            repo.addSavedLink(film("two", "a"))
            assertTrue(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.CopyFolder("a", "Kopia")))
            val copy = repo.folders(normal).single { it.name == "Kopia" }
            assertNotEquals("a", copy.id)
            val original = repo.savedLinks(normal, "a")
            val cloned = repo.savedLinks(normal, copy.id)
            assertEquals(2, cloned.size)
            assertEquals(original.map { it.filmPageUrl }.toSet(),
                cloned.map { it.filmPageUrl }.toSet())
            assertEquals(original.map { it.title }.toSet(),
                cloned.map { it.title }.toSet())
            assertEquals(original.map { it.thumbnailUrl }.toSet(),
                cloned.map { it.thumbnailUrl }.toSet())
            assertEquals(original.map { it.durationSeconds }.toSet(),
                cloned.map { it.durationSeconds }.toSet())
        } finally { db.close() }
    }

    @Test fun folderMoveMergesAndRemovesOnlySourceOnSuccess() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("from"))
            repo.putFolder(folder("to", "Docelowy"))
            repo.addSavedLink(film("a", "from"))
            repo.addSavedLink(film("b", "to"))
            assertTrue(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.MoveFolder("from", "to")))
            assertEquals(listOf("to"), repo.folders(normal).map { it.id })
            assertEquals(2, repo.savedLinks(normal, "to").size)
        } finally { db.close() }
    }

    @Test fun folderMoveCollisionDoesNotLoseAnythingOrDeleteSource() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("from"))
            repo.putFolder(folder("to", "Docelowy"))
            repo.addSavedLink(film("a", "from", url = "https://example.org/watch?x=1"))
            repo.addSavedLink(film("b", "to", url = "https://example.org/watch?x=1"))
            assertFalse(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.MoveFolder("from", "to")))
            assertEquals(2, repo.folders(normal).size)
            assertEquals(1, repo.savedLinks(normal, "from").size)
            assertEquals(1, repo.savedLinks(normal, "to").size)
        } finally { db.close() }
    }

    @Test fun copyingLinkPreservesFullUrlAndLeavesOriginalUntouched() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("a"))
            repo.putFolder(folder("b", "Cel"))
            repo.addSavedLink(film("one", "a"))
            assertTrue(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.CopyFilm("a", "one", "b")))
            val original = repo.savedLinks(normal, "a").single()
            val copied = repo.savedLinks(normal, "b").single()
            assertEquals(original.filmPageUrl, copied.filmPageUrl)
            assertEquals(original.title, copied.title)
            assertEquals(original.thumbnailUrl, copied.thumbnailUrl)
            assertEquals(original.durationSeconds, copied.durationSeconds)
            assertNotEquals(original.id, copied.id)
        } finally { db.close() }
    }

    @Test fun movingLinkRemovesSourceOnlyAfterCreatingDestination() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("a"))
            repo.putFolder(folder("b", "Cel"))
            repo.addSavedLink(film("one", "a"))
            assertTrue(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.MoveFilm("a", "one", "b")))
            assertTrue(repo.savedLinks(normal, "a").isEmpty())
            assertEquals(film("one", "a").filmPageUrl,
                repo.savedLinks(normal, "b").single().filmPageUrl)
        } finally { db.close() }
    }

    @Test fun linkCollisionBlocksCopyAndMoveWithoutDeletingSource() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("a"))
            repo.putFolder(folder("b", "Cel"))
            repo.addSavedLink(film("x", "a", url = "https://example.org/watch?id=1&lang=pl"))
            repo.addSavedLink(film("y", "b", url = "https://example.org/watch?id=1&lang=pl"))
            assertFalse(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.CopyFilm("a", "x", "b")))
            assertFalse(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.MoveFilm("a", "x", "b")))
            assertEquals(1, repo.savedLinks(normal, "a").size)
            assertEquals(1, repo.savedLinks(normal, "b").size)
        } finally { db.close() }
    }

    @Test fun differentQueryParametersAreNotConsideredDuplicate() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("a"))
            repo.putFolder(folder("b", "Cel"))
            repo.addSavedLink(film("x", "a", url = "https://example.org/watch?lang=pl"))
            repo.addSavedLink(film("y", "b", url = "https://example.org/watch?lang=en"))
            assertTrue(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.MoveFilm("a", "x", "b")))
            assertEquals(2, repo.savedLinks(normal, "b").size)
        } finally { db.close() }
    }

    @Test fun renameFilmTitlePreservesPageAndThumbnailAndAllowsClearing() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("a"))
            repo.addSavedLink(film("one", "a"))
            assertTrue(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.RenameFilm("a", "one", " Inny tytuł ")))
            val named = repo.savedLinks(normal, "a").single()
            assertEquals("Inny tytuł", named.title)
            assertEquals(film("one", "a").filmPageUrl, named.filmPageUrl)
            assertTrue(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.RenameFilm("a", "one", "")))
            assertNull(repo.savedLinks(normal, "a").single().title)
        } finally { db.close() }
    }

    @Test fun deleteOneLinkDoesNotRemoveFolderOrSiblings() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("a"))
            repo.addSavedLink(film("x", "a"))
            repo.addSavedLink(film("y", "a"))
            assertTrue(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.DeleteFilm("a", "x")))
            assertEquals(listOf("y"), repo.savedLinks(normal, "a").map { it.id })
            assertEquals(1, repo.folders(normal).size)
        } finally { db.close() }
    }

    @Test fun deleteFolderRemovesItsLinksButNotAnotherFolder() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("a"))
            repo.putFolder(folder("b", "Cel"))
            repo.addSavedLink(film("x", "a"))
            repo.addSavedLink(film("y", "b"))
            assertTrue(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.DeleteFolder("a")))
            assertTrue(repo.savedLinks(normal, "a").isEmpty())
            assertEquals(listOf("b"), repo.folders(normal).map { it.id })
            assertEquals("y", repo.savedLinks(normal, "b").single().id)
        } finally { db.close() }
    }

    @Test fun foreignProfileIdsCannotModifyAnotherProfilesData() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("a"))
            repo.addSavedLink(film("x", "a"))
            for (edit in listOf(
                VideoSavedLibraryEdit.RenameFolder("a", "Hacked"),
                VideoSavedLibraryEdit.DeleteFolder("a"),
                VideoSavedLibraryEdit.DeleteFilm("a", "x"),
                VideoSavedLibraryEdit.CopyFilm("a", "x", "b"),
                VideoSavedLibraryEdit.MoveFilm("a", "x", "b"),
            )) assertFalse(repo.editSavedLibrary(other, edit))
            assertEquals("Folder", repo.folders(normal).single().name)
            assertEquals(1, repo.savedLinks(normal, "a").size)
        } finally { db.close() }
    }

    @Test fun nonexistentDestinationsRejectMoveAndCopyWithoutSideEffects() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("a"))
            repo.addSavedLink(film("x", "a"))
            assertFalse(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.MoveFilm("a", "x", "missing")))
            assertFalse(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.CopyFolder("missing", "New")))
            assertFalse(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.MoveFolder("a", "missing")))
            assertEquals(listOf("a"), repo.folders(normal).map { it.id })
            assertEquals(1, repo.savedLinks(normal, "a").size)
        } finally { db.close() }
    }

    @Test fun mutationsUpdateLiveRoomFolderAndLinkCounts() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            repo.putFolder(folder("a"))
            repo.putFolder(folder("b", "Cel"))
            repo.addSavedLink(film("x", "a"))
            assertTrue(repo.editSavedLibrary(normal,
                VideoSavedLibraryEdit.MoveFilm("a", "x", "b")))
            val state = repo.observeSavedLibrary(normal, "b").first {
                it.links.size == 1 && it.folders.first { f -> f.folder.id == "a" }.savedCount == 0L
            }
            assertEquals(1L, state.folders.first { it.folder.id == "b" }.savedCount)
            assertEquals("x", state.links.single().filmPageUrl.substringAfter("id=")
                .substringBefore("&"))
        } finally { db.close() }
    }
}
