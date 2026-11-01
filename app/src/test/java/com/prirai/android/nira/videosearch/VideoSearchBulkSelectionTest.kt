package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

class VideoSearchBulkSelectionTest {
    private val scope = VideoSearchScope.Normal("default")
    private val run = VideoSearchRun(
        id = "bulk-run", scope = scope, query = "filmy",
        siteIds = listOf("site-a", "site-b"),
        parallelism = VideoSearchParallelism.TWO,
        status = VideoSearchRunState.COMPLETED, startedAtMs = 100,
    )
    private fun film(site: String, id: String, url: String =
        "https://example.org/watch?id=" + id + "&token=exact"
    ): VideoSearchFilm = VideoSearchFilm(
        id = id, runId = "bulk-run", siteId = site, scope = scope,
        siteName = site, filmPageUrl = url,
        originalIndex = 0, foundAtMs = 200,
    )
    private fun snapshot(films: List<VideoSearchFilm>): VideoSearchSavedResults =
        VideoSearchSavedResults(run = run, tasks = emptyList(),
            films = films, siteNames = emptyMap())

    @Test fun selectingAcrossFirst150DoesNotDropUnrenderedRows() {
        val films = (0 until 365).map { film("site-a", "film-" + it) }
        val selected = VideoSearchBulkSelection.select(emptySet(), films)
        assertEquals(365, selected.size)
        assertEquals(365, VideoSearchBulkSelection.resolve(snapshot(films), selected).size)
    }

    @Test fun sourceIdentitiesRemainDistinctEvenWhenTitlesOrLinksMatch() {
        val shared = "https://example.org/watch?token=123"
        val a = film("site-a", "same-id", shared)
        val b = film("site-b", "same-id", shared)
        val selected = VideoSearchBulkSelection.select(emptySet(), listOf(a, b))
        assertEquals(2, VideoSearchBulkSelection.resolve(snapshot(listOf(a, b)), selected).size)
        assertEquals(listOf(shared), VideoSearchBulkSelection.uniqueLinks(listOf(a, b)))
        assertEquals(setOf(VideoSearchBulkSelection.key(b)),
            VideoSearchBulkSelection.toggle(selected, a))
    }

    @Test fun invertVisibleSetKeepsSelectionsFromOtherSites() {
        val a = film("site-a", "a")
        val b = film("site-a", "b")
        val hidden = film("site-b", "hidden")
        val initial = setOf(VideoSearchBulkSelection.key(a), VideoSearchBulkSelection.key(hidden))
        val after = VideoSearchBulkSelection.invert(initial, listOf(a, b))
        assertEquals(setOf(VideoSearchBulkSelection.key(b),
            VideoSearchBulkSelection.key(hidden)), after)
    }

    @Test fun staleRunOrForeignProfileFilmsCannotBeResolved() {
        val valid = film("site-a", "valid")
        val anotherRun = valid.copy(id = "bad-run", runId = "other")
        val anotherProfile = valid.copy(id = "bad-profile",
            scope = VideoSearchScope.Normal("work"))
        val unknownSite = valid.copy(id = "bad-site", siteId = "other-site")
        val chosen = VideoSearchBulkSelection.select(emptySet(),
            listOf(valid, anotherRun, anotherProfile, unknownSite))
        assertEquals(listOf(valid), VideoSearchBulkSelection.resolve(
            snapshot(listOf(valid, anotherRun, anotherProfile, unknownSite)), chosen))
    }

    @Test fun groupNamesRejectControlsAndOversize() {
        assertNull(VideoSearchBulkSelection.name("\n"))
        assertNull(VideoSearchBulkSelection.name("a\nb"))
        assertNull(VideoSearchBulkSelection.name("x".repeat(121)))
        assertEquals("Grupa", VideoSearchBulkSelection.name(" Grupa "))
    }

    @Test fun allNonDestructiveActionsAreRepresented() {
        assertEquals(11, VideoSearchBulkAction.values().size)
        assertFalse(VideoSearchBulkAction.values().any {
            it.name.contains("DELETE") || it.name.contains("REMOVE")
        })
    }
}
