package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

class VideoSearchResultsMergeTest {
    private val scope = VideoSearchScope.Normal("default")
    private fun film(id: String, site: String, title: String?, seconds: Long?): VideoSearchFilm =
        VideoSearchFilm(
            id = id, runId = "run", siteId = site, scope = scope,
            siteName = site, filmPageUrl = "https://${site}.example.org/watch/${id}",
            originalTitle = title, durationSeconds = seconds,
            titleSource = if (title == null) VideoResultTitleSource.UNKNOWN
                else VideoResultTitleSource.CARD,
            originalIndex = 0, foundAtMs = 100,
        )
    private val sample = listOf(
        film("1", "a", "Ten Film", 120),
        film("2", "b", " ten   FILM ", 180),
        film("3", "c", "Inny", 180),
        film("4", "a", "Samotny", 240),
    )

    @Test fun disabledKeepsEveryFilmIndependent() {
        assertEquals(sample, VideoSearchResultsMerge.group(sample, false, false).map { it.primary })
    }
    @Test fun nameOnlyMergesAcrossSitesCaseAndSpacingInsensitive() {
        assertEquals(listOf(listOf("1", "2"), listOf("3"), listOf("4")),
            VideoSearchResultsMerge.group(sample, true, false).map { it.films.map { f -> f.id } })
    }
    @Test fun durationOnlyUsesExactSeconds() {
        assertEquals(listOf(listOf("1"), listOf("2", "3"), listOf("4")),
            VideoSearchResultsMerge.group(sample, false, true).map { it.films.map { f -> f.id } })
    }
    @Test fun bothUseOrAndTransitiveGrouping() {
        val groups = VideoSearchResultsMerge.group(sample, true, true)
        assertEquals(2, groups.size)
        assertEquals(listOf("1", "2", "3"), groups.first().films.map { it.id })
    }
    @Test fun preservesAllOriginalUrlsAndDoesNotLogSensitiveResults() {
        val result = VideoSearchResultsMerge.group(sample, true, true)
        assertEquals(sample.map { it.filmPageUrl },
            result.flatMap { it.films }.map { it.filmPageUrl })
        assertFalse(result.first().toString().contains("https://"))
    }
    @Test fun missingAndZeroDurationsNeverMergeWithUnknownTitles() {
        val films = listOf(film("a", "a", null, null), film("b", "b", null, null),
            film("c", "c", null, 0), film("d", "d", "D", 0))
        assertEquals(4, VideoSearchResultsMerge.group(films, true, true).size)
    }
    @Test fun differentSecondsStaySeparate() {
        val films = listOf(film("a", "a", "A", 120), film("b", "b", "B", 121))
        assertEquals(2, VideoSearchResultsMerge.group(films, false, true).size)
    }
    @Test fun rejectsForeignRunOrProfile() {
        val variants = listOf(
            sample[1].copy(scope = VideoSearchScope.Normal("work")),
            sample[1].copy(runId = "foreign"),
        )
        for (foreign in variants) try {
            VideoSearchResultsMerge.group(listOf(sample[0], foreign), true, true)
            fail("Mixed search runs or profiles")
        } catch (_: IllegalArgumentException) {}
    }
    @Test fun largeClusterCrossing150ResultBoundaryIsNotSplit() {
        val many = (0 until 310).map {
            film(it.toString(), if (it % 2 == 0) "a" else "b",
                "Title ${it % 4}", 120)
        }
        assertEquals(310, VideoSearchResultsMerge.group(many, true, true).single().films.size)
    }
}
