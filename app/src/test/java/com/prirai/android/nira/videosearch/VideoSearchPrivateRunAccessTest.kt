package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** Stage 10C: contract-level RAM checks, not Gecko runtime verification. */
class VideoSearchPrivateRunAccessTest {
    private class Fixture {
        val store = VideoSearchPrivateStore()
        var nextEpoch = 0
        val registry = VideoSearchPrivateSessionRegistry(store) {
            "epoch-${++nextEpoch}"
        }
        val access = VideoSearchPrivateRunAccess(registry)
        val tab = VideoSearchPrivateTab("tab-1", "private", true)
        val owner = VideoSearchScope.Private("default", "epoch-1")
        val site = VideoSearchSite(
            id = "site", scope = owner, siteUrl = "https://example.org/search",
            displayName = "Example", position = 0, createdAtMs = 1L,
        )
        init {
            registry.reconcile(listOf(tab), "default")
            store.putSite(site)
            store.selectSite(owner, site.id, true)
        }
        fun card(url: String = "https://example.org/film?id=1") =
            VideoSearchExtractedFilm(
                url, "Title", null, null, VideoResultTitleSource.CARD,
                VideoResultImageSource.UNKNOWN, 0,
            )
    }

    @Test fun onlySelectedSiteIsExposedToPrivateSearch() {
        val f = Fixture()
        assertEquals(f.site, f.access.selectedSite(f.tab, "default", "site"))
        f.store.selectSite(f.owner, "site", false)
        assertNull(f.access.selectedSite(f.tab, "default", "site"))
        assertFalse(f.access.record(f.tab, "default", "site", "q", listOf(f.card())))
    }

    @Test fun siteReadRejectsForeignProfileAndWrongTab() {
        val f = Fixture()
        assertNull(f.access.selectedSite(f.tab, "work", "site"))
        assertNull(f.access.selectedSite(VideoSearchPrivateTab("other", "private", true),
            "default", "site"))
    }

    @Test fun privateRunContainsFullUrlAndOnlyPrivateScope() {
        val f = Fixture()
        val url = "https://example.org/film?id=1&episode=2"
        assertTrue(f.access.record(f.tab, "default", "site", "phrase", listOf(f.card(url)), 123))
        val snapshot = f.access.latest(f.tab, "default")!!
        assertEquals("phrase", snapshot.run.query)
        assertEquals(f.owner, snapshot.run.scope)
        assertNull(snapshot.run.scope.persistentProfileId)
        assertEquals(url, snapshot.films.single().filmPageUrl)
    }

    @Test fun oneVisiblePageIsNeverMarkedCompleted() {
        val f = Fixture()
        f.access.record(f.tab, "default", "site", "phrase", listOf(f.card()))
        val latest = f.access.latest(f.tab, "default")!!
        assertEquals(VideoSearchRunState.STOPPED, latest.run.status)
        assertEquals(VideoSearchSiteState.STOPPED,
            f.store.tasks(f.owner, latest.run.id).single().status)
        assertNull(f.store.tasks(f.owner, latest.run.id).single().completionReason)
    }

    @Test fun emptyResultsRemainExplicitlyPartial() {
        val f = Fixture()
        assertTrue(f.access.record(f.tab, "default", "site", "q", emptyList()))
        assertTrue(f.access.latest(f.tab, "default")!!.films.isEmpty())
    }

    @Test fun crossOriginFilmRejectedBeforeRunWrite() {
        val f = Fixture()
        assertFalse(f.access.record(f.tab, "default", "site", "q",
            listOf(f.card("https://other.example/video"))))
        assertTrue(f.store.runs(f.owner).isEmpty())
    }

    @Test fun invalidPhraseCannotBeRecorded() {
        val f = Fixture()
        assertFalse(f.access.record(f.tab, "default", "site", " ", listOf(f.card())))
        assertTrue(f.store.runs(f.owner).isEmpty())
    }

    @Test fun foreignProfileCannotRecordResults() {
        val f = Fixture()
        assertFalse(f.access.record(f.tab, "work", "site", "q", listOf(f.card())))
        assertNull(f.access.latest(f.tab, "work"))
    }

    @Test fun wrongAndNormalTabsCannotRecordPrivateResults() {
        val f = Fixture()
        assertFalse(f.access.record(VideoSearchPrivateTab("wrong", "private", true),
            "default", "site", "q", listOf(f.card())))
        assertFalse(f.access.record(VideoSearchPrivateTab("tab-1", null, false),
            "default", "site", "q", listOf(f.card())))
    }

    @Test fun lastTabClosureWipesRunsAndRejectsLateWrites() {
        val f = Fixture()
        assertTrue(f.access.record(f.tab, "default", "site", "q", listOf(f.card())))
        f.registry.reconcile(emptyList(), "default")
        assertNull(f.access.latest(f.tab, "default"))
        assertFalse(f.access.record(f.tab, "default", "site", "q", listOf(f.card())))
        try {
            f.store.runs(f.owner)
            fail("Expired private session still readable")
        } catch (_: IllegalStateException) {}
    }

    @Test fun duplicateFilmLinkIsDeduplicated() {
        val f = Fixture()
        assertTrue(f.access.record(f.tab, "default", "site", "q",
            listOf(f.card(), f.card())))
        assertEquals(1, f.access.latest(f.tab, "default")!!.films.size)
    }

    @Test fun moreThanTwoHundredCardsCannotBeSaved() {
        val f = Fixture()
        assertFalse(f.access.record(f.tab, "default", "site", "q",
            List(201) { f.card("https://example.org/film?id=$it") }))
        assertTrue(f.store.runs(f.owner).isEmpty())
    }

    @Test fun twoHundredUniqueFilmsAreKeptInPrivateRamOnly() {
        val f = Fixture()
        assertTrue(f.access.record(f.tab, "default", "site", "q",
            List(200) { f.card("https://example.org/film?id=$it") }))
        assertEquals(200, f.access.latest(f.tab, "default")!!.films.size)
        assertEquals(200, f.store.films(
            f.owner, f.store.runs(f.owner).single().id, "site"
        ).size)
    }

    @Test fun explicitNoMoreResultsMarksRealCompletion() {
        val f = Fixture()
        assertTrue(f.access.record(f.tab, "default", "site", "q",
            listOf(f.card()), reason = VideoSearchPaginationStop.NO_MORE_RESULTS,
            visitedPages = 3))
        val run = f.access.latest(f.tab, "default")!!.run
        assertEquals(VideoSearchRunState.COMPLETED, run.status)
        val task = f.store.tasks(f.owner, run.id).single()
        assertEquals(3, task.visitedPages)
        assertEquals(VideoSearchSiteState.COMPLETED, task.status)
        assertEquals(VideoSearchCompletionReason.NO_MORE_RESULTS, task.completionReason)
    }

    @Test fun explicitlyReachedLimitMarksTrueCompletion() {
        val f = Fixture()
        assertTrue(f.access.record(f.tab, "default", "site", "q",
            List(200) { f.card("https://example.org/film?id=$it") },
            reason = VideoSearchPaginationStop.LIMIT_REACHED))
        val run = f.access.latest(f.tab, "default")!!.run
        assertEquals(VideoSearchRunState.COMPLETED, run.status)
        assertEquals(VideoSearchCompletionReason.LIMIT_REACHED,
            f.store.tasks(f.owner, run.id).single().completionReason)
    }

    @Test fun forgedLimitReasonWithOnlyOneResultIsPartial() {
        val f = Fixture()
        assertTrue(f.access.record(f.tab, "default", "site", "q",
            listOf(f.card()), reason = VideoSearchPaginationStop.LIMIT_REACHED))
        val run = f.access.latest(f.tab, "default")!!.run
        assertEquals(VideoSearchRunState.STOPPED, run.status)
        assertNull(f.store.tasks(f.owner, run.id).single().completionReason)
    }

    @Test fun unsafePaginationNeverClaimsCompletion() {
        val f = Fixture()
        assertTrue(f.access.record(f.tab, "default", "site", "q",
            listOf(f.card()), reason = VideoSearchPaginationStop.UNSAFE))
        assertEquals(VideoSearchRunState.STOPPED,
            f.access.latest(f.tab, "default")!!.run.status)
    }

    @Test fun pageBudgetExceedingTwelveIsRejectedBeforeWrites() {
        val f = Fixture()
        assertFalse(f.access.record(f.tab, "default", "site", "q",
            listOf(f.card()), visitedPages = 13))
        assertTrue(f.store.runs(f.owner).isEmpty())
    }

    @Test fun runHistoryAndRelatedFilmsAreBoundedToTwelve() {
        val f = Fixture()
        repeat(15) { i ->
            assertTrue(f.access.record(f.tab, "default", "site", "phrase-$i",
                listOf(f.card()), i + 1L))
        }
        assertEquals(12, f.store.runs(f.owner).size)
        assertEquals(12, f.store.runs(f.owner).sumOf { run ->
            f.store.films(f.owner, run.id, "site").size
        })
        assertEquals("phrase-14", f.access.latest(f.tab, "default")!!.run.query)
    }

    @Test fun newPrivateLifetimeDoesNotInheritFinishedHistory() {
        val f = Fixture()
        assertTrue(f.access.record(f.tab, "default", "site", "q", listOf(f.card())))
        f.registry.reconcile(emptyList(), "default")
        f.registry.reconcile(listOf(f.tab), "default")
        assertNull(f.access.latest(f.tab, "default"))
    }

    @Test fun privateSingleRunStoresTenThousandConfiguredResultsInRamOnly() {
        val f = Fixture()
        f.store.putSite(f.site.copy(resultLimit = 10_000))
        val cards = List(10_000) { i ->
            f.card("https://example.org/film?id=$i").copy(originalIndex = i)
        }
        assertTrue(f.access.record(f.tab, "default", "site", "term", cards,
            reason = VideoSearchPaginationStop.LIMIT_REACHED,
            visitedPages = 30))
        val latest = f.access.latest(f.tab, "default")!!
        assertEquals(10_000, latest.films.size)
        assertEquals(10_000, latest.tasks.single().resultLimit)
        assertEquals(VideoSearchRunState.COMPLETED, latest.run.status)
        f.registry.reconcile(emptyList(), "default")
        assertNull(f.access.latest(f.tab, "default"))
    }

    @Test fun privateSingleProgressHonorsConfiguredHighLimitAndRejectsStaleEpoch() {
        val f = Fixture()
        f.store.putSite(f.site.copy(resultLimit = 5_000))
        val run = f.access.beginSingle(f.tab, "default", "site", "phrase")!!
        val films = (0 until 500).map { i ->
            f.card("https://example.org/film?id=$i").copy(originalIndex = i)
        }
        val snapshot = VideoSearchPaginationSnapshot(films, scannedCards = 500,
            visitedPages = 7, pageActions = 5, reason = VideoSearchPaginationStop.RESOURCE_LIMIT)
        assertTrue(f.access.checkpointSingle(f.tab, "default", run, "site", snapshot))
        assertEquals(500, f.store.films(f.owner, run, "site").size)
        assertEquals(5_000, f.store.tasks(f.owner, run).single().resultLimit)
        f.registry.reconcile(emptyList(), "default")
        assertFalse(f.access.checkpointSingle(f.tab, "default", run, "site", snapshot))
    }

}
