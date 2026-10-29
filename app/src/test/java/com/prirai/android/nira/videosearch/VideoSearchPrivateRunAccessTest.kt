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

    @Test fun moreThanEightyCardsCannotBeSaved() {
        val f = Fixture()
        assertFalse(f.access.record(f.tab, "default", "site", "q",
            List(81) { f.card("https://example.org/film?id=$it") }))
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
}
