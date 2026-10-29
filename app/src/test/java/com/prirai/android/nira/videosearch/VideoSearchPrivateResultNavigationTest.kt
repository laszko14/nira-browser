package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** RAM boundary/navigation contracts; NOT a Gecko/device navigation test. */
class VideoSearchPrivateResultNavigationTest {
    private class Fixture {
        val store = VideoSearchPrivateStore()
        var epoch = 0
        val registry = VideoSearchPrivateSessionRegistry(store) { "life-${++epoch}" }
        val tab = VideoSearchPrivateTab("anchor", "private", true)
        val scope = VideoSearchScope.Private("personal", "life-1")
        val navigation = VideoSearchPrivateResultNavigation(registry)
        val url = "https://films.example.org/watch?id=7&token=opaque"
        init { registry.reconcile(listOf(tab), "personal") }
        fun seed(runId: String = "run", filmId: String = "film", url: String = this.url) {
            store.putRun(VideoSearchRun(
                id = runId, scope = scope, query = "phrase",
                siteIds = listOf("site"), parallelism = VideoSearchParallelism.ONE,
                status = VideoSearchRunState.STOPPED, startedAtMs = 1,
            ))
            store.putFilm(VideoSearchFilm(
                id = filmId, runId = runId, siteId = "site",
                scope = scope, siteName = "Films",
                filmPageUrl = url, originalIndex = 0, foundAtMs = 1,
            ))
        }
        fun resolve(run: String = "run", film: String = "film") =
            navigation.resolveFilmUrl(tab, "personal", run, film)
    }

    @Test fun opensExactValidatedPrivateFilmUrlWithQueryIntact() {
        val f = Fixture()
        f.seed()
        assertEquals(f.url, f.resolve())
    }

    @Test fun unknownRunCannotOpenCachedFilm() {
        val f = Fixture()
        f.seed()
        assertNull(f.resolve(run = "old-run"))
    }

    @Test fun unknownFilmCannotOpenAnotherFilm() {
        val f = Fixture()
        f.seed()
        assertNull(f.resolve(film = "other-film"))
    }

    @Test fun blankIdsAreRejected() {
        val f = Fixture()
        f.seed()
        assertNull(f.resolve(run = ""))
        assertNull(f.resolve(film = ""))
    }

    @Test fun foreignOrNormalTabsCannotOpenPrivateResult() {
        val f = Fixture()
        f.seed()
        assertNull(f.navigation.resolveFilmUrl(
            VideoSearchPrivateTab("other", "private", true), "personal", "run", "film"))
        assertNull(f.navigation.resolveFilmUrl(
            VideoSearchPrivateTab("anchor", "default", true), "personal", "run", "film"))
        assertNull(f.navigation.resolveFilmUrl(
            VideoSearchPrivateTab("anchor", "private", false), "personal", "run", "film"))
    }

    @Test fun profileSwitchDeniesFilmOpening() {
        val f = Fixture()
        f.seed()
        assertNull(f.navigation.resolveFilmUrl(f.tab, "business", "run", "film"))
    }

    @Test fun lastPrivateTabClosedDeniesFilmOpening() {
        val f = Fixture()
        f.seed()
        f.registry.reconcile(emptyList(), "personal")
        assertNull(f.resolve())
    }

    @Test fun newPrivateEpochCannotReadOldResult() {
        val f = Fixture()
        f.seed()
        f.registry.reconcile(emptyList(), "personal")
        f.registry.reconcile(listOf(f.tab), "personal")
        assertNull(f.resolve())
    }

    @Test fun multipleRunsDoNotCrossIdentifyResultId() {
        val f = Fixture()
        f.seed()
        f.seed("next-run", "other-film", "https://films.example.org/watch?id=8")
        assertNull(f.resolve(run = "run", film = "other-film"))
        assertEquals("https://films.example.org/watch?id=8",
            f.resolve(run = "next-run", film = "other-film"))
    }

    @Test fun runningGroupShowsTrueProgressRatherThanCompletedLabel() {
        val f = Fixture()
        f.seed()
        val run = f.store.runs(f.scope).single().copy(
            status = VideoSearchRunState.RUNNING,
            siteIds = listOf("site", "other")
        )
        val text = VideoSearchPrivateRunPresentation.summary(
            VideoSearchPrivateRunSnapshot(run, emptyList()))
        assertTrue(text.contains("w trakcie"))
        assertTrue(text.contains("0/2"))
        assertFalse(text.contains("2/2"))
    }

    @Test fun incompleteSiteRemainsVisiblyPartialAndNeedsAttention() {
        val f = Fixture()
        f.seed()
        val run = f.store.runs(f.scope).single()
        val snapshot = VideoSearchPrivateRunSnapshot(run, emptyList(), listOf(
            VideoSearchSiteTask(run.id, "site", f.scope,
                status = VideoSearchSiteState.NEEDS_ATTENTION)
        ))
        val text = VideoSearchPrivateRunPresentation.summary(snapshot)
        assertTrue(text.contains("częściowe"))
        assertTrue(text.contains("wymaga uwagi"))
        assertFalse(text.contains("zakończone · 1/1"))
    }

    @Test fun genuinelyCompletedTaskReportsCompletedSource() {
        val f = Fixture()
        f.seed()
        val run = f.store.runs(f.scope).single().copy(
            status = VideoSearchRunState.COMPLETED)
        val snapshot = VideoSearchPrivateRunSnapshot(run, emptyList(), listOf(
            VideoSearchSiteTask(run.id, "site", f.scope,
                status = VideoSearchSiteState.COMPLETED,
                completionReason = VideoSearchCompletionReason.NO_MORE_RESULTS)
        ))
        val text = VideoSearchPrivateRunPresentation.summary(snapshot)
        assertTrue(text.contains("zakończone · 1/1"))
        assertFalse(text.contains("wymaga uwagi"))
    }
}
