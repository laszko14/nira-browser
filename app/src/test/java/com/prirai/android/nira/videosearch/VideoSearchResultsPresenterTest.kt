package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** ETAP 5D: aggregation is by run, site and exact URL, never document title. */
class VideoSearchResultsPresenterTest {
    private val owner = VideoSearchScope.Normal("default")
    private val run = VideoSearchRun(
        id = "run", scope = owner, query = "komedia",
        siteIds = listOf("a", "b"), parallelism = VideoSearchParallelism.TWO,
        status = VideoSearchRunState.STOPPED, startedAtMs = 100,
    )
    private fun task(
        site: String, status: VideoSearchSiteState = VideoSearchSiteState.STOPPED,
        count: Int = 2, reason: VideoSearchCompletionReason? = null,
    ) = VideoSearchSiteTask(
        runId = "run", siteId = site, scope = owner,
        status = status, uniqueResults = count, resultLimit = 200,
        visitedPages = 2, completionReason = reason,
    )
    private fun film(
        site: String, idx: Int, title: String? = "Film $idx",
        duration: Long? = 120L + idx,
        url: String = "https://$site.example.org/watch?id=$idx&lang=pl",
    ) = VideoSearchFilm(
        id = "$site-$idx", runId = "run", siteId = site, scope = owner,
        siteName = if (site == "a") "Kino A" else "Kino B",
        filmPageUrl = url, originalTitle = title, thumbnailUrl = null,
        durationSeconds = duration, titleSource = if (title != null)
            VideoResultTitleSource.CARD else VideoResultTitleSource.UNKNOWN,
        imageSource = VideoResultImageSource.UNKNOWN,
        originalIndex = idx, foundAtMs = 1000,
    )

    @Test fun defaultOrderUsesSavedSiteOrderThenOriginalCardIndex() {
        val result = VideoSearchResultsPresenter.project(
            run, listOf(task("b"), task("a")),
            listOf(film("b", 4), film("a", 8), film("b", 1), film("a", 1)),
        )
        assertEquals(listOf("a-1", "a-8", "b-1", "b-4"),
            result.visibleFilms.map { it.id })
    }

    @Test fun filteredSiteShowsOnlyItsOwnFilmCards() {
        val result = VideoSearchResultsPresenter.project(
            run, listOf(task("a"), task("b")),
            listOf(film("a", 0), film("b", 0)), selectedSiteId = "b",
        )
        assertEquals(listOf("b-0"), result.visibleFilms.map { it.id })
        assertEquals(2, result.totalFilms)
        assertEquals(2, result.sources.size)
    }

    @Test fun sameFilmUrlFromTwoSitesStillKeepsTwoAttributions() {
        val shared = "https://a.example.org/watch?id=123&lang=pl"
        val result = VideoSearchResultsPresenter.project(
            run, listOf(task("a"), task("b")),
            listOf(film("a", 0, url = shared), film("b", 0, url = shared)),
        )
        assertEquals(2, result.totalFilms)
        assertEquals(setOf("a", "b"), result.visibleFilms.map { it.siteId }.toSet())
    }

    @Test fun exactDuplicateWithinOneSourceIsNotShownTwice() {
        val result = VideoSearchResultsPresenter.project(
            run, listOf(task("a")), listOf(film("a", 1),
                film("a", 99, url = film("a", 1).filmPageUrl))
        )
        assertEquals(1, result.totalFilms)
        assertEquals("a-1", result.visibleFilms.single().id)
    }

    @Test fun differentQueryParametersAreDifferentFilmPages() {
        val result = VideoSearchResultsPresenter.project(
            run, listOf(task("a")), listOf(
                film("a", 0, url = "https://a.example.org/watch?id=1&lang=en"),
                film("a", 1, url = "https://a.example.org/watch?id=1&lang=pl"),
            ),
        )
        assertEquals(2, result.totalFilms)
    }

    @Test fun titleSortIgnoresCaseAndKeepsUnknownTitlesLast() {
        val films = listOf(
            film("a", 0, "zebra"),
            film("a", 1, null),
            film("a", 2, "Alpha"),
            film("a", 3, "beta"),
        )
        val tasks = listOf(task("a", count = 4))
        assertEquals(listOf("Alpha", "beta", "zebra", null),
            VideoSearchResultsPresenter.project(run, tasks, films,
                sort = VideoSearchSort.TITLE_A_Z).visibleFilms.map { it.originalTitle })
        assertEquals(listOf("zebra", "beta", "Alpha", null),
            VideoSearchResultsPresenter.project(run, tasks, films,
                sort = VideoSearchSort.TITLE_Z_A).visibleFilms.map { it.originalTitle })
    }

    @Test fun durationSortKeepsUnknownLastInBothDirections() {
        val films = listOf(
            film("a", 0, duration = 120L),
            film("a", 1, duration = null),
            film("a", 2, duration = 20L),
            film("a", 3, duration = 300L),
        )
        for ((sort, expected) in listOf(
            VideoSearchSort.DURATION_SHORTEST to listOf(20L,120L,300L,null),
            VideoSearchSort.DURATION_LONGEST to listOf(300L,120L,20L,null)
        )) assertEquals(expected,
            VideoSearchResultsPresenter.project(run, listOf(task("a")), films,
                sort = sort).visibleFilms.map { it.durationSeconds })
    }

    @Test fun partialSiteIsNotClaimedToBeCompleted() {
        val result = VideoSearchResultsPresenter.project(
            run, listOf(
                task("a", VideoSearchSiteState.COMPLETED, 2,
                    VideoSearchCompletionReason.NO_MORE_RESULTS),
                task("b", VideoSearchSiteState.STOPPED, 1),
            ), listOf(film("a", 0), film("b", 0)),
        )
        assertEquals(1, result.completedSites)
        assertEquals(1, result.partialSites)
        assertEquals("Zakończono", VideoSearchResultsPresenter.statusLabel(
            result.sources.first().state))
        assertEquals("Częściowe / zatrzymane",
            VideoSearchResultsPresenter.statusLabel(result.sources.last().state))
    }

    @Test fun siteSnapshotNameSurvivesRenamingOrDeletion() {
        val first = VideoSearchResultsPresenter.project(
            run, listOf(task("a")), listOf(film("a", 0)),
            siteNames = mapOf("a" to "Nowa nazwa")
        )
        assertEquals("Kino A", first.sources.single().name)
        val second = VideoSearchResultsPresenter.project(
            run, listOf(task("a")), emptyList(),
            siteNames = mapOf("a" to "Aktualna nazwa")
        )
        assertEquals("Aktualna nazwa", second.sources.single().name)
    }

    @Test fun noSharedDocumentTitleIsInventedForNullNames() {
        val display = VideoSearchResultsPresenter.project(run, listOf(task("a")),
            listOf(film("a", 0, title = null)))
        assertNull(display.visibleFilms.single().originalTitle)
    }

    @Test fun rejectsForeignProfileBeforeDisplayingAnyRecords() {
        val foreign = VideoSearchScope.Normal("work")
        try {
            VideoSearchResultsPresenter.project(run,
                listOf(task("a").copy(scope = foreign)),
                listOf(film("a", 0)))
            fail("Cross-profile task was displayed")
        } catch (_: IllegalArgumentException) {}
        try {
            VideoSearchResultsPresenter.project(run, listOf(task("a")),
                listOf(film("a", 0).copy(scope = foreign)))
            fail("Cross-profile film was displayed")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun rejectsFilmFromOtherRunOrUnselectedSite() {
        try {
            VideoSearchResultsPresenter.project(run, listOf(task("a")),
                listOf(film("a", 0).copy(runId = "other-run")))
            fail("Foreign run film accepted")
        } catch (_: IllegalArgumentException) {}
        try {
            VideoSearchResultsPresenter.project(run, listOf(task("a")),
                listOf(film("b", 0)))
            fail("Site without a task accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun durationLabelHoursMinutesAndUnknown() {
        assertEquals("0:00", VideoSearchResultsPresenter.durationLabel(0L))
        assertEquals("1:09", VideoSearchResultsPresenter.durationLabel(69L))
        assertEquals("1:02:03", VideoSearchResultsPresenter.durationLabel(3723L))
        assertNull(VideoSearchResultsPresenter.durationLabel(null))
    }

    @Test fun missingMetadataOrEmptyFiltersAreSafe() {
        val result = VideoSearchResultsPresenter.project(
            run, listOf(task("a", count = 0)), emptyList(), selectedSiteId = "unknown",
        )
        assertTrue(result.visibleFilms.isEmpty())
        assertEquals(0, result.totalFilms)
        assertEquals("Witryna usunięta", result.sources.single().name)
    }

    @Test fun progressNeverUsesPercentOf200OrFakesCompletion() {
        val display = VideoSearchResultsPresenter.project(
            run, listOf(task("a", count = 1)), listOf(film("a", 0))
        )
        assertEquals(1, display.sources.single().count)
        assertEquals(200, display.sources.single().limit)
        assertNull(display.sources.single().reason)
        assertEquals("Częściowe / zatrzymane",
            VideoSearchResultsPresenter.statusLabel(display.sources.single().state))
    }
}
