package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** P8 RAM-only single-site checkpointing and lifetime invalidation. */
class VideoSearchPrivateSingleProgressTest {
    private class Fixture {
        val store = VideoSearchPrivateStore()
        private var sequence = 0
        val registry = VideoSearchPrivateSessionRegistry(store) { "epoch-" + (++sequence) }
        val tab = VideoSearchPrivateTab("tab", "private", true)
        val scope = VideoSearchScope.Private("default", "epoch-1")
        val site = VideoSearchSite(
            "site", scope, "https://films.example.org/", "Filmy", 0, createdAtMs = 1L
        )
        val access = VideoSearchPrivateRunAccess(registry)
        init {
            registry.reconcile(listOf(tab), "default")
            store.putSite(site)
            store.selectSite(scope, site.id, true)
        }
        fun begin() = access.beginSingle(tab, "default", "site", "serial")!!
        fun snapshot(count: Int, reason: VideoSearchPaginationStop =
            VideoSearchPaginationStop.RESOURCE_LIMIT) =
            VideoSearchPaginationSnapshot(
                films = (0 until count).map { i ->
                    VideoSearchExtractedFilm(
                        "https://films.example.org/watch?v=$i", "Film $i",
                        null, null, VideoResultTitleSource.CARD,
                        VideoResultImageSource.UNKNOWN, i
                    )
                }, scannedCards = count, visitedPages = 1,
                pageActions = 0, reason = reason
            )
    }

    @Test fun userStopKeepsPrivatePartialsOnlyInSessionRam() {
        val f = Fixture()
        val id = f.begin()
        assertTrue(f.access.checkpointSingle(f.tab, "default", id, "site", f.snapshot(4)))
        assertTrue(f.access.finishSingle(f.tab, "default", id))
        val stored = f.access.history(f.tab, "default")!!.single()
        assertEquals(4, stored.films.size)
        assertEquals(VideoSearchRunState.STOPPED, stored.run.status)
        assertEquals(VideoSearchSiteState.STOPPED, stored.tasks.single().status)
        assertNull(stored.tasks.single().completionReason)
    }

    @Test fun privateProgressThenCompletedSnapshotIsDeduplicated() {
        val f = Fixture()
        val id = f.begin()
        assertTrue(f.access.checkpointSingle(f.tab, "default", id, "site", f.snapshot(3)))
        assertTrue(f.access.checkpointSingle(f.tab, "default", id, "site", f.snapshot(3)))
        assertTrue(f.access.checkpointSingle(f.tab, "default", id, "site",
            f.snapshot(9, VideoSearchPaginationStop.NO_MORE_RESULTS), terminal = true))
        val result = f.access.history(f.tab, "default")!!.single()
        assertEquals(9, result.films.size)
        assertEquals(9, result.tasks.single().uniqueResults)
        assertEquals(VideoSearchRunState.COMPLETED, result.run.status)
        assertEquals(VideoSearchCompletionReason.NO_MORE_RESULTS,
            result.tasks.single().completionReason)
        assertFalse(f.access.finishSingle(f.tab, "default", id))
    }

    @Test fun closingLifetimeClearsPrivatePartialsAndBlocksLateWrites() {
        val f = Fixture()
        val id = f.begin()
        assertTrue(f.access.checkpointSingle(f.tab, "default", id, "site", f.snapshot(5)))
        f.registry.reconcile(emptyList(), "default")
        assertFalse(f.access.checkpointSingle(f.tab, "default", id, "site", f.snapshot(6)))
        assertFalse(f.access.finishSingle(f.tab, "default", id))
        assertNull(f.access.history(f.tab, "default"))
        f.registry.reconcile(listOf(f.tab), "default")
        assertTrue(f.access.history(f.tab, "default")!!.isEmpty())
    }

    @Test fun foreignProfileOrUnsafeOriginNeverMutatesPrivateRun() {
        val f = Fixture()
        val id = f.begin()
        assertFalse(f.access.checkpointSingle(f.tab, "work", id, "site", f.snapshot(4)))
        val poisoned = f.snapshot(4).copy(films = listOf(VideoSearchExtractedFilm(
            "https://other.example.org/watch", "Film", null, null,
            VideoResultTitleSource.CARD, VideoResultImageSource.UNKNOWN, 0
        )))
        assertFalse(f.access.checkpointSingle(f.tab, "default", id, "site", poisoned))
        assertTrue(f.access.history(f.tab, "default")!!.single().films.isEmpty())
    }
}
