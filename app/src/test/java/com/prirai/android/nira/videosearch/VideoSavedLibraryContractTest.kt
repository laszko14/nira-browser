package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** Stage 8A: scope, routing and saved-folder model invariants. */
class VideoSavedLibraryContractTest {
    private val normal = VideoSearchScope.Normal("default")
    private val work = VideoSearchScope.Normal("work")
    private fun folder(scope: VideoSearchScope.Normal = normal, id: String = "one") =
        VideoSavedFilmFolder(
            id = id, scope = scope, name = "Fraza filmów",
            createdAtMs = 100L
        )
    private fun link(scope: VideoSearchScope.Normal = normal, id: String = "film",
                     folderId: String = "one",
                     url: String = "https://example.org/watch?id=1&lang=pl") =
        VideoSavedFilmLink(
            id = id, scope = scope, folderId = folderId,
            filmPageUrl = url, title = "Pełny tytuł", thumbnailUrl = null,
            durationSeconds = 42L, createdAtMs = 101L
        )

    @Test fun normalDefaultProfileHasNullGeckoContext() {
        assertEquals("default", VideoSavedFoldersTabRoute.normalOwner(
            VIDEO_SAVED_FOLDERS_TAB_URL, null, false, "default"
        ))
    }

    @Test fun otherProfileUsesItsOwnGeckoContext() {
        assertEquals("work", VideoSavedFoldersTabRoute.normalOwner(
            VIDEO_SAVED_FOLDERS_TAB_URL, "profile_work", false, "work"
        ))
        assertNull(VideoSavedFoldersTabRoute.normalOwner(
            VIDEO_SAVED_FOLDERS_TAB_URL, null, false, "work"
        ))
    }

    @Test fun privateAndForeignProfilesCannotAccessSavedLibrary() {
        assertNull(VideoSavedFoldersTabRoute.normalOwner(
            VIDEO_SAVED_FOLDERS_TAB_URL, "private", true, "default"
        ))
        assertNull(VideoSavedFoldersTabRoute.normalOwner(
            VIDEO_SAVED_FOLDERS_TAB_URL, "profile_other", false, "work"
        ))
        assertNull(VideoSavedFoldersTabRoute.normalOwner(
            VIDEO_SAVED_FOLDERS_TAB_URL, "profile_work", false, "default"
        ))
    }

    @Test fun onlyExactInternalFolderUrlAccepted() {
        assertNull(VideoSavedFoldersTabRoute.normalOwner(
            "about:zavro-video-search", null, false, "default"
        ))
        assertNull(VideoSavedFoldersTabRoute.normalOwner(
            "about:zavro-video-folders?other=1", null, false, "default"
        ))
        assertNull(VideoSavedFoldersTabRoute.normalOwner(
            "https://example.org/about:zavro-video-folders", null, false, "default"
        ))
    }

    @Test fun emptyLibraryHasNoLinksOrFolder() {
        val model = VideoSavedLibrarySnapshot(emptyList(), null, emptyList())
        assertTrue(model.folders.isEmpty())
        assertTrue(model.links.isEmpty())
    }

    @Test fun openedFolderLinksPreserveTheirExactUrlsAndMetadata() {
        val film = link()
        val model = VideoSavedLibrarySnapshot(
            listOf(VideoSavedFolderSummary(folder(), 1L)),
            folder(), listOf(film),
        )
        assertEquals("https://example.org/watch?id=1&lang=pl",
            model.links.single().filmPageUrl)
        assertEquals("Pełny tytuł", model.links.single().title)
        assertEquals(42L, model.links.single().durationSeconds)
    }

    @Test fun foreignFolderLinkIsRejected() {
        try {
            VideoSavedLibrarySnapshot(
                listOf(VideoSavedFolderSummary(folder(), 1L)),
                folder(), listOf(link(work)),
            )
            fail("Other profile link must be rejected")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun wrongFolderIdCannotShowLinksInAnotherFolder() {
        try {
            VideoSavedLibrarySnapshot(
                listOf(VideoSavedFolderSummary(folder(), 1L)),
                folder(), listOf(link(folderId = "two")),
            )
            fail("Wrong folder link must be rejected")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun folderRootMustNotExposeAnyLink() {
        try {
            VideoSavedLibrarySnapshot(
                listOf(VideoSavedFolderSummary(folder(), 1L)),
                null, listOf(link()),
            )
            fail("A closed folder cannot expose link rows")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun aForeignOrMissingOpenedFolderIsRejected() {
        try {
            VideoSavedLibrarySnapshot(
                listOf(VideoSavedFolderSummary(folder(), 1L)),
                folder(work), emptyList(),
            )
            fail("A foreign folder cannot open")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun negativeCountAndDuplicateFolderIdsAreRejected() {
        try {
            VideoSavedFolderSummary(folder(), -1)
            fail("A negative result count was accepted")
        } catch (_: IllegalArgumentException) {}
        try {
            VideoSavedLibrarySnapshot(
                listOf(VideoSavedFolderSummary(folder(), 1),
                    VideoSavedFolderSummary(folder(), 1)), null, emptyList()
            )
            fail("Duplicate folder id")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun twoNormalProfilesCannotShareOneLibrarySnapshot() {
        try {
            VideoSavedLibrarySnapshot(
                listOf(VideoSavedFolderSummary(folder(), 1),
                    VideoSavedFolderSummary(folder(work, "two"), 1)),
                null, emptyList()
            )
            fail("Foreign profile summary mixed in")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun libraryDebugStringDoesNotRevealSavedFilmUrl() {
        val model = VideoSavedLibrarySnapshot(
            listOf(VideoSavedFolderSummary(folder(), 1)),
            folder(), listOf(link()),
        )
        assertFalse(model.toString().contains("https:"))
        assertFalse(model.toString().contains("Pełny tytuł"))
    }
}
