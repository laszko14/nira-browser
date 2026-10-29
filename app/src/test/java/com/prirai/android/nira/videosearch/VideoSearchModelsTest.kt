package com.prirai.android.nira.videosearch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSearchModelsTest {
    @Test
    fun defaultAndNamedProfilesFollowExistingGeckoPartitions() {
        val default = VideoSearchScope.Normal("default")
        val named = VideoSearchScope.Normal("work")
        assertNull(default.contextId)
        assertEquals("default", default.persistentProfileId)
        assertTrue(default.ownsTab(null, false))
        assertFalse(default.ownsTab("profile_work", false))
        assertEquals("profile_work", named.contextId)
        assertFalse(named.ownsTab(null, false))
        assertTrue(named.ownsTab("profile_work", false))
        assertFalse(named.ownsTab("profile_work", true))
    }

    @Test
    fun privateSearchRequiresMatchingEphemeralLifetimeAndCannotBePersisted() {
        val first = VideoSearchScope.Private("work", "private-epoch-1")
        val second = VideoSearchScope.Private("work", "private-epoch-2")
        assertNull(first.persistentProfileId)
        assertFalse(first.ownsTab("private", true))
        assertFalse(first.ownsTab("private", true, "private-epoch-2"))
        assertTrue(first.ownsTab("private", true, "private-epoch-1"))
        assertFalse(first.ownsTab("profile_work", false, "private-epoch-1"))
        assertFalse(first == second)
    }

    @Test(expected = IllegalArgumentException::class)
    fun privateScopeCannotBeCreatedWithoutLifetimeId() {
        VideoSearchScope.Private("default", "")
    }

    @Test
    fun aSiteExistsBeforeConfigurationAndRetainsItsOwnLimit() {
        val site = VideoSearchSite(
            id = "site-1",
            scope = VideoSearchScope.Normal("default"),
            siteUrl = "https://example.org/",
            displayName = "Przykład",
            position = 0,
            createdAtMs = 5L,
        )
        assertEquals(DEFAULT_VIDEO_SEARCH_SITE_LIMIT, site.resultLimit)
        assertEquals(VideoSiteConfigurationState.NEEDS_CONFIGURATION, site.configurationState)
        assertNull(site.configuration)
        assertEquals(400, site.copy(resultLimit = 400).resultLimit)
    }

    @Test(expected = IllegalArgumentException::class)
    fun siteCannotClaimConfigurationSuccessWithoutSelectors() {
        VideoSearchSite(
            id = "site-2",
            scope = VideoSearchScope.Normal("default"),
            siteUrl = "https://example.org/",
            displayName = "Przykład",
            position = 0,
            configurationState = VideoSiteConfigurationState.CONFIGURED,
            createdAtMs = 5L,
        )
    }

    @Test
    fun selectedSitesAndAllAtOnceAreIndependentSettings() {
        val settings = VideoSearchSettings(
            scope = VideoSearchScope.Normal("default"),
            selectedSiteIds = setOf("a", "b", "c", "d", "e", "f"),
            parallelism = VideoSearchParallelism.ALL,
        )
        assertEquals(6, settings.selectedSiteIds.size)
        assertNull(settings.parallelism.maximumSites)
        assertEquals(5, settings.copy(parallelism = VideoSearchParallelism.FIVE).parallelism.maximumSites)
    }

    @Test
    fun resultKeepsExactLinkAndUnknownMetadataWithoutGuessing() {
        val fullTitle = "Bardzo długi tytuł filmu • ".repeat(50)
        val film = VideoSearchFilm(
            id = "result-1",
            runId = "run-1",
            siteId = "site-1",
            scope = VideoSearchScope.Normal("default"),
            siteName = "Witryna",
            filmPageUrl = "https://example.org/movie/5?episode=2&lang=pl",
            originalTitle = fullTitle,
            originalIndex = 0,
            foundAtMs = 12L,
        )
        assertEquals(fullTitle, film.originalTitle)
        assertEquals("https://example.org/movie/5?episode=2&lang=pl", film.filmPageUrl)
        assertNull(film.thumbnailUrl)
        assertNull(film.durationSeconds)
        assertEquals(VideoResultImageSource.UNKNOWN, film.imageSource)
        assertFalse(film.toString().contains("example.org"))
    }

    @Test
    fun onlyActuallyFinishedSiteHasCompletionReason() {
        val queued = VideoSearchSiteTask(
            runId = "run-1",
            siteId = "site-1",
            scope = VideoSearchScope.Normal("default"),
            uniqueResults = 20,
        )
        val complete = queued.copy(
            status = VideoSearchSiteState.COMPLETED,
            completionReason = VideoSearchCompletionReason.NO_MORE_RESULTS,
        )
        assertNull(queued.completionReason)
        assertEquals(20, complete.uniqueResults)
        assertEquals(VideoSearchCompletionReason.NO_MORE_RESULTS, complete.completionReason)
    }
}
