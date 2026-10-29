package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** ETAP 6A — BrowserStore tab identity, default-null context and privacy. */
class VideoSearchResultsTabRouteTest {
    private val url = VIDEO_SEARCH_RESULTS_TAB_URL
    private fun owner(
        tabUrl: String? = url,
        contextId: String? = null,
        isPrivate: Boolean = false,
        activeProfile: String = "default",
    ) = VideoSearchResultsTabRoute.normalOwner(tabUrl, contextId, isPrivate, activeProfile)

    @Test fun defaultProfileUsesNullContextAndOpensResults() {
        assertEquals("default", owner())
    }

    @Test fun defaultProfileRejectsLegacyProfileDefaultContext() {
        assertNull(owner(contextId = "profile_default"))
    }

    @Test fun customProfileMustUseItsMatchingBrowserContext() {
        assertEquals("work", owner(contextId = "profile_work", activeProfile = "work"))
    }

    @Test fun customProfileWithNullContextCannotReadDefaultRoomHistory() {
        assertNull(owner(contextId = null, activeProfile = "work"))
    }

    @Test fun activeProfileMismatchCannotReadAnotherProfilesResults() {
        assertNull(owner(contextId = "profile_work", activeProfile = "personal"))
        assertNull(owner(contextId = "profile_work", activeProfile = "default"))
    }

    @Test fun defaultProfileCannotReadCustomProfileTab() {
        assertNull(owner(contextId = "profile_work"))
    }

    @Test fun privateTabNeverMapsToPersistentHistory() {
        assertNull(owner(contextId = "private", isPrivate = true))
        assertNull(owner(contextId = null, isPrivate = true))
        assertNull(owner(contextId = "profile_work", isPrivate = true,
            activeProfile = "work"))
    }

    @Test fun aNormalTabCannotUsePrivateStorageContext() {
        assertNull(owner(contextId = "private", isPrivate = false))
    }

    @Test fun onlyExactInternalUrlIsAccepted() {
        assertNull(owner(tabUrl = "about:homepage"))
        assertNull(owner(tabUrl = "about:blank"))
        assertNull(owner(tabUrl = "about:zavro-video-search?run=other"))
        assertNull(owner(tabUrl = "https://example.org/about:zavro-video-search"))
        assertNull(owner(tabUrl = "about:zavro-video-search/"))
        assertNull(owner(tabUrl = null))
    }

    @Test fun invalidActiveProfileDoesNotMapToRoom() {
        assertNull(owner(activeProfile = "private"))
        assertNull(owner(activeProfile = ""))
        assertNull(owner(activeProfile = " work "))
    }

    @Test fun changingActiveProfileRevokesPreviousTabOwner() {
        assertEquals("work", owner(contextId = "profile_work", activeProfile = "work"))
        assertNull(owner(contextId = "profile_work", activeProfile = "default"))
        assertEquals("default", owner(contextId = null, activeProfile = "default"))
    }
}
