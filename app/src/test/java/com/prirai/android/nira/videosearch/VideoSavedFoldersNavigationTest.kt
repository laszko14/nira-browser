package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** Stage 8C: shared normal-route reuse and view-scoped Android Back. */
class VideoSavedFoldersNavigationTest {
    private val default = VideoSearchScope.Normal("default")
    private val work = VideoSearchScope.Normal("work")
    private fun tab(
        id: String,
        url: String? = VIDEO_SAVED_FOLDERS_TAB_URL,
        contextId: String? = null,
        isPrivate: Boolean = false,
    ) = VideoSavedFolderTabCandidate(id, url, contextId, isPrivate)

    @Test fun normalDefaultLibraryReusesItsExactTab() {
        assertEquals("library", VideoSavedFoldersTabOpener.reusableTabId(
            default, listOf(tab("library"))
        ))
    }

    @Test fun additionalNormalProfileUsesOnlyItsOwnPartition() {
        assertEquals("own", VideoSavedFoldersTabOpener.reusableTabId(
            work, listOf(tab("foreign"), tab("own", contextId = "profile_work"))
        ))
    }

    @Test fun privateLibraryTabMustNeverBeReused() {
        assertNull(VideoSavedFoldersTabOpener.reusableTabId(
            default, listOf(tab("private", isPrivate = true))
        ))
    }

    @Test fun privateContextMustNeverBeReusedAsNormal() {
        assertNull(VideoSavedFoldersTabOpener.reusableTabId(
            default, listOf(tab("private", contextId = "private"))
        ))
    }

    @Test fun lookalikeUrlAndSearchResultsAreNotFolders() {
        assertNull(VideoSavedFoldersTabOpener.reusableTabId(default, listOf(
            tab("search", url = VIDEO_SEARCH_RESULTS_TAB_URL),
            tab("spoof", url = "about:zavro-video-folders?q=1"),
            tab("web", url = "https://example.org/about:zavro-video-folders"),
        )))
    }

    @Test fun foreignPartitionCannotBeReusedEvenForDefaultProfile() {
        assertNull(VideoSavedFoldersTabOpener.reusableTabId(
            default, listOf(tab("foreign", contextId = "profile_work"))
        ))
    }

    @Test fun firstEligibleTabIsReusedAndNotBlankId() {
        assertEquals("first", VideoSavedFoldersTabOpener.reusableTabId(default, listOf(
            tab(""), tab("first"), tab("second")
        )))
    }

    @Test fun emptyCandidatesRequireCreatingANewTab() {
        assertNull(VideoSavedFoldersTabOpener.reusableTabId(default, emptyList()))
    }

    @Test fun backIsNotConsumedWhenNoFolderRegistered() {
        assertFalse(VideoSavedFoldersBackDispatcher().handle("tab", "default"))
    }

    @Test fun backReturnsToFolderRootBeforeClosingBrowserTab() {
        val back = VideoSavedFoldersBackDispatcher()
        var folderOpen = true
        back.register("tab", "default") {
            if (!folderOpen) false else { folderOpen = false; true }
        }
        assertTrue(back.handle("tab", "default"))
        assertFalse(folderOpen)
        assertFalse(back.handle("tab", "default"))
    }

    @Test fun backCannotNavigateForeignTabOrProfile() {
        val back = VideoSavedFoldersBackDispatcher()
        var calls = 0
        back.register("tab", "default") { calls++; true }
        assertFalse(back.handle("other", "default"))
        assertFalse(back.handle("tab", "work"))
        assertFalse(back.handle("tab", null))
        assertEquals(0, calls)
        assertTrue(back.handle("tab", "default"))
        assertEquals(1, calls)
    }

    @Test fun disposedCompositionCannotContinueConsumingBack() {
        val back = VideoSavedFoldersBackDispatcher()
        val unregister = back.register("tab", "default") { true }
        unregister()
        assertFalse(back.handle("tab", "default"))
    }

    @Test fun staleCompositionCleanupDoesNotRemoveCurrentHandler() {
        val back = VideoSavedFoldersBackDispatcher()
        val old = back.register("tab-old", "default") { true }
        back.register("tab-new", "default") { true }
        old()
        assertTrue(back.handle("tab-new", "default"))
        assertFalse(back.handle("tab-old", "default"))
    }

    @Test fun fragmentViewTeardownClearsRegisteredHandler() {
        val back = VideoSavedFoldersBackDispatcher()
        back.register("tab", "default") { true }
        back.clear()
        assertFalse(back.handle("tab", "default"))
    }

    @Test fun blankTabOrProfileCannotRegister() {
        val back = VideoSavedFoldersBackDispatcher()
        try {
            back.register("", "default") { true }
            fail("Blank tab accepted")
        } catch (_: IllegalArgumentException) {}
        try {
            back.register("tab", "") { true }
            fail("Blank profile accepted")
        } catch (_: IllegalArgumentException) {}
    }
}
