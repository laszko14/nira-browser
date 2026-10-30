package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** Large-RAM index, scope, deduplication and retirement invariants. */
class VideoSearchPrivateIndexRegressionTest {
    private val scope = VideoSearchScope.Private("default", "epoch-index")
    private val other = VideoSearchScope.Private("other", "epoch-index")

    private fun run(id: String = "run") = VideoSearchRun(
        id = id, scope = scope, query = "film", siteIds = listOf("first", "second"),
        parallelism = VideoSearchParallelism.ONE,
        status = VideoSearchRunState.RUNNING, startedAtMs = 1
    )

    private fun film(site: String, number: Int, id: String = "$site-$number") =
        VideoSearchFilm(
            id = id, runId = "run", siteId = site, scope = scope,
            siteName = site,
            filmPageUrl = "https://example.org/watch?id=$number",
            originalTitle = "Film $number", thumbnailUrl = null,
            durationSeconds = null, titleSource = VideoResultTitleSource.CARD,
            imageSource = VideoResultImageSource.UNKNOWN,
            originalIndex = number, foundAtMs = 1
        )

    @Test fun exactUrlDeduplicatesWithinSiteButNotAcrossSitesAndCountsStayIndexed() {
        val store = VideoSearchPrivateStore()
        store.start(scope)
        store.putRun(run())
        repeat(5_000) { n ->
            assertTrue(store.putFilm(film("first", n)))
        }
        assertEquals(5_000, store.countFilms(scope, "run", "first"))
        assertEquals(0, store.countFilms(scope, "run", "second"))
        assertFalse(store.putFilm(film("first", 1, id = "duplicate")))
        assertEquals(5_000, store.countFilms(scope, "run", "first"))
        assertTrue(store.putFilm(film("second", 1)))
        assertEquals(1, store.countFilms(scope, "run", "second"))
        assertEquals(5_000, store.films(scope, "run", "first").size)
        assertEquals("first-0", store.films(scope, "run", "first").first().id)

        // Update same record/URL preserves indexes, but never permits
        // reassigning its identity to a different site or address.
        assertTrue(store.putFilm(film("first", 0).copy(originalTitle = "Lepszy tytuł")))
        assertEquals(5_000, store.countFilms(scope, "run", "first"))
        assertEquals("Lepszy tytuł", store.films(scope, "run", "first").first().originalTitle)
        try {
            store.putFilm(film("second", 0, id = "first-0"))
            fail("Reassigned private film identity")
        } catch (_: IllegalArgumentException) {}
        assertEquals(5_000, store.countFilms(scope, "run", "first"))
        store.finish(scope)
        try {
            store.countFilms(scope, "run", "first")
            fail("Private index survived session retirement")
        } catch (_: IllegalStateException) {}
    }

    @Test fun trimmingExpiredRunsRemovesTheirIndexesWithoutTouchingLatestRun() {
        val store = VideoSearchPrivateStore()
        store.start(scope)
        for (i in 0..12) {
            val old = run("old-$i").copy(startedAtMs = i.toLong() + 1)
            store.putRun(old)
            store.putFilm(film("first", i).copy(
                id = "old-film-$i", runId = old.id
            ))
        }
        store.trimOldRuns(scope)
        assertEquals(12, store.runs(scope).size)
        assertEquals(0, store.countFilms(scope, "old-0", "first"))
        assertEquals(1, store.countFilms(scope, "old-12", "first"))
        assertEquals(1, store.films(scope, "old-12", "first").size)
        assertEquals(12, store.runs(scope).sumOf {
            store.countFilms(scope, it.id, "first")
        })
    }
}
