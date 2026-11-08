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
    @Test fun p4e2TappingMergedCardSelectsEveryOriginalAcrossSites() {
        val first = film("site-a", "film-a").copy(
            originalTitle = "Mountain Forest Adventure", durationSeconds = 1200)
        val second = film("site-b", "film-b").copy(
            originalTitle = "Mountain Forest Adventure", durationSeconds = 1201)
        val items = listOf(first, second)
        val group = VideoSearchResultsMerge.smartGroup(items).single().films
        val chosen = VideoSearchBulkSelection.toggleMerged(
            snapshot(items), emptySet(), group)
        assertEquals(2, chosen.size)
        assertEquals(2, VideoSearchBulkSelection.selectedCount(chosen, group))
        assertTrue(VideoSearchBulkSelection.allSelected(chosen, group))
        assertEquals(items, VideoSearchBulkSelection.resolve(snapshot(items), chosen))
        assertEquals(items.map { it.filmPageUrl },
            VideoSearchBulkSelection.uniqueLinks(items))
        assertTrue(VideoSearchBulkSelection.toggleMerged(
            snapshot(items), chosen, group).isEmpty())
    }

    @Test fun p4e2PartialSelectionCompletesGroupBeforeUnselectingIt() {
        val a = film("site-a", "a")
        val b = film("site-b", "b")
        val hidden = film("site-b", "hidden")
        val data = snapshot(listOf(a, b, hidden))
        val initial = setOf(VideoSearchBulkSelection.key(a),
            VideoSearchBulkSelection.key(hidden))
        assertFalse(VideoSearchBulkSelection.allSelected(initial, listOf(a, b)))
        val completed = VideoSearchBulkSelection.toggleMerged(data, initial, listOf(a, b))
        assertEquals(3, completed.size)
        assertTrue(VideoSearchBulkSelection.allSelected(completed, listOf(a, b)))
        val removed = VideoSearchBulkSelection.toggleMerged(
            data, completed, listOf(a, b))
        assertEquals(setOf(VideoSearchBulkSelection.key(hidden)), removed)
    }

    @Test fun p4e2ForeignRunProfileSiteOrStaleUrlNeverTogglesRealGroup() {
        val a = film("site-a", "a")
        val b = film("site-b", "b")
        val saved = snapshot(listOf(a, b))
        val initial = setOf(VideoSearchBulkSelection.key(a))
        val invalid = listOf(
            b.copy(runId = "another-run"),
            b.copy(scope = VideoSearchScope.Normal("work")),
            b.copy(siteId = "not-selected"),
            b.copy(filmPageUrl = "https://example.org/other?id=b"),
            b.copy(id = "not-saved"),
        )
        for (candidate in invalid) {
            assertEquals(initial, VideoSearchBulkSelection.toggleMerged(
                saved, initial, listOf(a, candidate)))
        }
        assertEquals(initial, VideoSearchBulkSelection.toggleMerged(
            saved, initial, listOf(a, a)))
        assertEquals(initial, VideoSearchBulkSelection.toggleMerged(
            null, initial, listOf(a, b)))
        assertEquals(initial, VideoSearchBulkSelection.toggleMerged(
            saved, initial, emptyList()))
    }

    @Test fun p4e2LargeMergedGroupKeepsAllLinksBeyondFirstRenderedPage() {
        val many = (0 until 370).map {
            film(if (it % 2 == 0) "site-a" else "site-b", "f-" + it).copy(
                originalTitle = "Beautiful River Documentary",
                durationSeconds = 1250)
        }
        val merged = VideoSearchResultsMerge.smartGroup(many).single().films
        val selected = VideoSearchBulkSelection.toggleMerged(
            snapshot(many), emptySet(), merged)
        assertEquals(370, selected.size)
        assertEquals(370, VideoSearchBulkSelection.resolve(snapshot(many), selected).size)
        assertEquals(370, VideoSearchBulkSelection.uniqueLinks(many).size)
    }

    @Test fun p4e2SameExactUrlAcrossSitesRemainsTwoSelectableSourceCards() {
        val shared = "https://example.org/watch?token=exact"
        val a = film("site-a", "same", shared)
        val b = film("site-b", "same", shared)
        val selected = VideoSearchBulkSelection.toggleMerged(
            snapshot(listOf(a, b)), emptySet(), listOf(a, b))
        assertEquals(2, selected.size)
        assertEquals(2, VideoSearchBulkSelection.resolve(
            snapshot(listOf(a, b)), selected).size)
        assertEquals(listOf(shared), VideoSearchBulkSelection.uniqueLinks(listOf(a, b)))
    }
}
