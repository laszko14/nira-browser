package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/**
 * Stage 6B: grouping is purely presentational. It never modifies a run,
 * duplicates saved films or silently marks a partial website completed.
 */
class VideoSearchResultsGroupingTest {
    private val scope = VideoSearchScope.Normal("default")
    private val run = VideoSearchRun(
        id = "run-6b", scope = scope, query = "kino",
        siteIds = listOf("z", "a", "empty"), parallelism = VideoSearchParallelism.THREE,
        status = VideoSearchRunState.STOPPED, startedAtMs = 40,
    )
    private fun task(
        id: String,
        count: Int = 0,
        status: VideoSearchSiteState = VideoSearchSiteState.STOPPED,
    ) = VideoSearchSiteTask(
        runId = run.id, scope = scope, siteId = id,
        status = status, uniqueResults = count,
        resultLimit = 200, visitedPages = 2,
    )

    private fun film(
        id: String, index: Int, title: String,
        url: String = "https://$id.example.org/watch?id=$index&lang=pl",
    ) = VideoSearchFilm(
        id = "$id:$index", runId = run.id, siteId = id,
        scope = scope, siteName = "Źródło $id",
        filmPageUrl = url, originalTitle = title, thumbnailUrl = null,
        durationSeconds = 90L + index, titleSource = VideoResultTitleSource.CARD,
        imageSource = VideoResultImageSource.UNKNOWN,
        originalIndex = index, foundAtMs = 100,
    )

    private fun display(
        site: String? = null,
        sort: VideoSearchSort = VideoSearchSort.ORIGINAL,
    ) = VideoSearchResultsPresenter.project(
        run,
        listOf(task("empty", status = VideoSearchSiteState.NEEDS_ATTENTION),
            task("a", 2), task("z", 2)),
        listOf(
            film("a", 2, "Zeta"), film("z", 3, "Beta"),
            film("z", 1, "Alpha"), film("a", 1, "Gamma"),
        ),
        siteNames = mapOf("empty" to "Puste źródło"),
        selectedSiteId = site,
        sort = sort,
    )

    @Test fun groupsKeepSavedSiteOrderNotAlphabetical() {
        val groups = VideoSearchResultsPresenter.groups(display())
        assertEquals(listOf("z", "a", "empty"),
            groups.map { it.source.siteId })
    }

    @Test fun groupsDoNotLoseFilms() {
        val model = display()
        val groups = VideoSearchResultsPresenter.groups(model)
        assertEquals(model.visibleFilms.size, groups.sumOf { it.savedCount })
        assertEquals(model.visibleFilms.map { it.id }.toSet(),
            groups.flatMap { it.films }.map { it.id }.toSet())
    }

    @Test fun noResultsSiteStillHasExpandableHeaderWithCorrectState() {
        val group = VideoSearchResultsPresenter.groups(display()).last()
        assertEquals("empty", group.source.siteId)
        assertEquals("Puste źródło", group.source.name)
        assertEquals(0, group.savedCount)
        assertTrue(group.films.isEmpty())
        assertEquals(VideoSearchSiteState.NEEDS_ATTENTION, group.source.state)
        assertNull(group.source.reason)
    }

    @Test fun groupedSortMaintainsGlobalSortOrderWithinEachSource() {
        val asc = VideoSearchResultsPresenter.groups(display(sort = VideoSearchSort.TITLE_A_Z))
        assertEquals(listOf("Alpha", "Beta"), asc[0].films.map { it.originalTitle })
        assertEquals(listOf("Gamma", "Zeta"), asc[1].films.map { it.originalTitle })
        val desc = VideoSearchResultsPresenter.groups(display(sort = VideoSearchSort.TITLE_Z_A))
        assertEquals(listOf("Beta", "Alpha"), desc[0].films.map { it.originalTitle })
        assertEquals(listOf("Zeta", "Gamma"), desc[1].films.map { it.originalTitle })
    }

    @Test fun filteredSiteReturnsOnlyOneGroupWithoutCrossSiteCards() {
        val groups = VideoSearchResultsPresenter.groups(display(site = "a"), "a")
        assertEquals(listOf("a"), groups.map { it.source.siteId })
        assertEquals(listOf("a:1", "a:2"), groups.single().films.map { it.id })
    }

    @Test fun filteredEmptySiteStillHasAHeader() {
        val groups = VideoSearchResultsPresenter.groups(display(site = "empty"), "empty")
        assertEquals(1, groups.size)
        assertEquals(0, groups.single().savedCount)
        assertEquals("Puste źródło", groups.single().source.name)
    }

    @Test fun unrelatedFilterReturnsNoGroups() {
        assertTrue(VideoSearchResultsPresenter.groups(display(), "not-selected").isEmpty())
    }

    @Test fun displayCountReflectsSavedNotUnprovenTaskCount() {
        val model = display()
        val groups = VideoSearchResultsPresenter.groups(model)
        assertEquals(4, model.totalFilms)
        assertEquals(2, groups.first().savedCount)
        assertEquals(0, groups.last().savedCount)
        assertEquals(0, model.completedSites)
    }

    @Test fun exactUrlsAreNotRewrittenOrDeDuplicatedBetweenSites() {
        val full = "https://example.org/watch?quality=high&lang=pl"
        val model = VideoSearchResultsPresenter.project(
            run, listOf(task("z"), task("a")),
            listOf(film("z", 1, "A", full), film("a", 1, "B", full)),
        )
        val groups = VideoSearchResultsPresenter.groups(model)
        assertEquals(2, groups.sumOf { it.savedCount })
        assertEquals(listOf(full, full), groups.flatMap { it.films }.map { it.filmPageUrl })
    }

    @Test fun allGroupsAreExpandedByDefaultWithoutEnumeratingIds() {
        val sources = display().sources
        assertTrue(sources.all { VideoSearchGroupExpansion.isExpanded(null, it.siteId) })
    }

    @Test fun toggleOneGroupKeepsOthersOpen() {
        val sources = display().sources
        val next = VideoSearchGroupExpansion.toggle(null, sources, "a")
        assertFalse(VideoSearchGroupExpansion.isExpanded(next, "a"))
        assertTrue(VideoSearchGroupExpansion.isExpanded(next, "z"))
        assertTrue(VideoSearchGroupExpansion.isExpanded(next, "empty"))
        val again = VideoSearchGroupExpansion.toggle(next, sources, "a")
        assertNull(again)
    }

    @Test fun collapseAllThenExpandAllUsesNullSentinel() {
        val sources = display().sources
        val collapsed = VideoSearchGroupExpansion.toggleAll(null, sources)
        assertEquals(emptySet<String>(), collapsed)
        assertTrue(sources.none {
            VideoSearchGroupExpansion.isExpanded(collapsed, it.siteId)
        })
        assertNull(VideoSearchGroupExpansion.toggleAll(collapsed, sources))
    }

    @Test fun togglingUnknownSiteCannotModifyKnownExpansionSet() {
        val sources = display().sources
        val initial = setOf("z")
        assertEquals(initial,
            VideoSearchGroupExpansion.toggle(initial, sources, "unknown"))
    }

    @Test fun collapseOneFromNullThenCollapseAllRemainsConsistent() {
        val sources = display().sources
        val partial = VideoSearchGroupExpansion.toggle(null, sources, "empty")
        assertNull(VideoSearchGroupExpansion.toggleAll(partial, sources))
        val collapsed = VideoSearchGroupExpansion.toggleAll(
            VideoSearchGroupExpansion.toggle(null, sources, "a"), sources
        )
        assertNull(collapsed)
    }

    @Test fun emptySourceListDoesNotThrowOnToggleAll() {
        assertNull(VideoSearchGroupExpansion.toggleAll(null, emptyList()))
        assertEquals(emptySet<String>(),
            VideoSearchGroupExpansion.toggleAll(emptySet(), emptyList()))
    }

    @Test fun printedGroupsDoNotLeakFilmPageUrls() {
        val full = VideoSearchResultsPresenter.groups(display()).first()
        assertFalse(full.toString().contains("https://"))
        assertFalse(full.toString().contains("Alpha"))
    }
}
