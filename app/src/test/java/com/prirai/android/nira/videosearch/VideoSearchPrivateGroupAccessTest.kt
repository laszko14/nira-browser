package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/**
 * Stage 10F: aggregated private RAM journal. Pure unit contracts, no
 * claim of actual background Gecko/Android scheduling or APK verification.
 */
class VideoSearchPrivateGroupAccessTest {
    private class Fixture(count: Int = 3) {
        val store = VideoSearchPrivateStore()
        var session = 0
        val registry = VideoSearchPrivateSessionRegistry(store) { "epoch-${++session}" }
        val tab = VideoSearchPrivateTab("anchor", "private", true)
        val owner = VideoSearchScope.Private("default", "epoch-1")
        val sites = (1..count).map { i ->
            VideoSearchSite(
                id = "s$i", scope = owner, siteUrl = "https://s$i.example.org/",
                displayName = "Site $i", position = i, createdAtMs = i.toLong(),
            )
        }
        var timestamp = 10L
        val groups = VideoSearchPrivateGroupAccess(registry) { ++timestamp }
        val reader = VideoSearchPrivateRunAccess(registry)
        init {
            registry.reconcile(listOf(tab), "default")
            sites.forEach {
                store.putSite(it)
                store.selectSite(owner, it.id, true)
            }
        }
        val plan get() = reader.prepareMulti(tab, "default", "phrase")!!
        fun start(): String = groups.start(tab, "default", plan)!!
        fun films(site: VideoSearchSite, count: Int = 1) = (0 until count).map { i ->
            VideoSearchExtractedFilm(
                filmPageUrl = "${site.siteUrl}video?id=$i",
                originalTitle = "Video $i",
                thumbnailUrl = null,
                durationSeconds = null,
                titleSource = VideoResultTitleSource.CARD,
                imageSource = VideoResultImageSource.UNKNOWN,
                originalIndex = i.coerceAtMost(319),
            )
        }
        fun snapshot(site: VideoSearchSite, count: Int = 1,
                     reason: VideoSearchPaginationStop =
                         VideoSearchPaginationStop.NO_MORE_RESULTS) =
            VideoSearchPaginationSnapshot(
                films = films(site, count),
                scannedCards = count,
                visitedPages = 1, pageActions = 0, reason = reason,
            )
    }

    @Test fun oneGroupedRunHasAllOriginalSiteIdsAndQueuedTasks() {
        val f = Fixture()
        val runId = f.start()
        val run = f.store.runs(f.owner).single()
        assertEquals(runId, run.id)
        assertEquals(f.sites.map { it.id }, run.siteIds)
        assertEquals(VideoSearchRunState.RUNNING, run.status)
        assertEquals(3, f.store.tasks(f.owner, runId).size)
        assertTrue(f.store.tasks(f.owner, runId).all {
            it.status == VideoSearchSiteState.QUEUED
        })
    }

    @Test fun groupRecordsFilmsForAllSitesWithoutPerSiteRuns() {
        val f = Fixture()
        val id = f.start()
        f.sites.forEach { site ->
            assertTrue(f.groups.checkpoint(f.tab, "default", id, site, f.snapshot(site, 2)))
        }
        assertEquals(1, f.store.runs(f.owner).size)
        assertEquals(6, f.reader.history(f.tab, "default")!!.single().films.size)
    }

    @Test fun groupCompletesOnlyWhenAllSitesReallyCompleted() {
        val f = Fixture()
        val id = f.start()
        f.sites.forEach { site ->
            f.groups.checkpoint(f.tab, "default", id, site, f.snapshot(site))
        }
        assertTrue(f.groups.finish(f.tab, "default", id))
        assertEquals(VideoSearchRunState.COMPLETED, f.store.runs(f.owner).single().status)
        assertEquals(3, f.store.tasks(f.owner, id).count {
            it.completionReason == VideoSearchCompletionReason.NO_MORE_RESULTS
        })
    }

    @Test fun missingWorkerMarksOnlyItsSiteNeedsAttention() {
        val f = Fixture()
        val id = f.start()
        assertTrue(f.groups.checkpoint(f.tab, "default", id, f.sites[0], null))
        f.groups.checkpoint(f.tab, "default", id, f.sites[1], f.snapshot(f.sites[1]))
        assertTrue(f.groups.finish(f.tab, "default", id))
        val tasks = f.store.tasks(f.owner, id)
        assertEquals(VideoSearchSiteState.NEEDS_ATTENTION,
            tasks.single { it.siteId == "s1" }.status)
        assertEquals(VideoSearchSiteState.COMPLETED,
            tasks.single { it.siteId == "s2" }.status)
        assertEquals(VideoSearchSiteState.STOPPED,
            tasks.single { it.siteId == "s3" }.status)
        assertEquals(VideoSearchRunState.STOPPED, f.store.runs(f.owner).single().status)
    }

    @Test fun duplicateCheckpointCannotInsertDuplicateFilms() {
        val f = Fixture()
        val id = f.start()
        val site = f.sites.first()
        assertTrue(f.groups.checkpoint(f.tab, "default", id, site, f.snapshot(site)))
        assertFalse(f.groups.checkpoint(f.tab, "default", id, site, f.snapshot(site)))
        assertEquals(1, f.store.films(f.owner, id, site.id).size)
    }

    @Test fun crossOriginFilmRejectedBeforeAnyWrite() {
        val f = Fixture()
        val id = f.start()
        val poisoned = f.snapshot(f.sites[0]).copy(films = f.films(f.sites[1]))
        assertFalse(f.groups.checkpoint(f.tab, "default", id, f.sites[0], poisoned))
        assertEquals(0, f.store.films(f.owner, id, f.sites[0].id).size)
        assertEquals(VideoSearchSiteState.QUEUED,
            f.store.tasks(f.owner, id).first().status)
    }

    @Test fun foreignProfileCannotReadOrMutateGroup() {
        val f = Fixture()
        val id = f.start()
        assertFalse(f.groups.checkpoint(f.tab, "work", id, f.sites[0],
            f.snapshot(f.sites[0])))
        assertFalse(f.groups.finish(f.tab, "work", id))
        assertEquals(VideoSearchRunState.RUNNING, f.store.runs(f.owner).single().status)
    }

    @Test fun closingLastPrivateTabWipesEntireGroupAndPendingTasks() {
        val f = Fixture()
        val id = f.start()
        f.groups.checkpoint(f.tab, "default", id, f.sites[0], f.snapshot(f.sites[0]))
        f.registry.reconcile(emptyList(), "default")
        assertFalse(f.groups.checkpoint(f.tab, "default", id, f.sites[1],
            f.snapshot(f.sites[1])))
        assertFalse(f.groups.finish(f.tab, "default", id))
        assertNull(f.reader.history(f.tab, "default"))
    }

    @Test fun newPrivateEpochDoesNotInheritGroupedResults() {
        val f = Fixture()
        val id = f.start()
        f.groups.checkpoint(f.tab, "default", id, f.sites[0], f.snapshot(f.sites[0]))
        f.registry.reconcile(emptyList(), "default")
        f.registry.reconcile(listOf(f.tab), "default")
        assertTrue(f.reader.history(f.tab, "default")!!.isEmpty())
    }

    @Test fun changingSiteSelectionBlocksLateWorkerResult() {
        val f = Fixture()
        val id = f.start()
        f.store.selectSite(f.owner, f.sites[0].id, false)
        assertFalse(f.groups.checkpoint(f.tab, "default", id, f.sites[0],
            f.snapshot(f.sites[0])))
        assertTrue(f.store.films(f.owner, id, f.sites[0].id).isEmpty())
    }

    @Test fun completionWithoutEveryTaskIsNotAllowed() {
        val f = Fixture()
        val id = f.start()
        f.groups.checkpoint(f.tab, "default", id, f.sites[0], f.snapshot(f.sites[0]))
        f.groups.finish(f.tab, "default", id)
        assertEquals(VideoSearchRunState.STOPPED, f.store.runs(f.owner).single().status)
        assertFalse(f.groups.finish(f.tab, "default", id))
    }

    @Test fun partialPaginationCannotBecomeCompletedGroupTask() {
        val f = Fixture()
        val id = f.start()
        f.groups.checkpoint(f.tab, "default", id, f.sites[0],
            f.snapshot(f.sites[0], reason = VideoSearchPaginationStop.STALLED))
        val task = f.store.tasks(f.owner, id).single { it.siteId == "s1" }
        assertEquals(VideoSearchSiteState.STOPPED, task.status)
        assertNull(task.completionReason)
    }

    @Test fun foreignTabCannotStartOrFinishGroup() {
        val f = Fixture()
        val foreign = VideoSearchPrivateTab("foreign", "private", true)
        assertNull(f.groups.start(foreign, "default", f.plan))
        val id = f.start()
        assertFalse(f.groups.finish(foreign, "default", id))
    }

    @Test fun snapshotFromAnotherGroupRunIsRejected() {
        val f = Fixture()
        val id = f.start()
        assertFalse(f.groups.checkpoint(f.tab, "default", "unknown", f.sites[0],
            f.snapshot(f.sites[0])))
        assertTrue(f.store.films(f.owner, id, f.sites[0].id).isEmpty())
    }

    @Test fun twoThousandFilmCeilingTruncatesFurtherSitesWithoutFalseCompletion() {
        val f = Fixture(count = 11)
        val id = f.start()
        f.sites.take(10).forEach { site ->
            assertTrue(f.groups.checkpoint(f.tab, "default", id, site,
                f.snapshot(site, 200, VideoSearchPaginationStop.LIMIT_REACHED)))
        }
        val last = f.sites.last()
        assertTrue(f.groups.checkpoint(f.tab, "default", id, last,
            f.snapshot(last, 2, VideoSearchPaginationStop.NO_MORE_RESULTS)))
        assertTrue(f.store.films(f.owner, id, last.id).isEmpty())
        val lastTask = f.store.tasks(f.owner, id).single { it.siteId == last.id }
        assertEquals(VideoSearchSiteState.STOPPED, lastTask.status)
        assertNull(lastTask.completionReason)
        assertEquals(VideoSearchPrivateGroupAccess.MAX_AGGREGATE_FILMS,
            f.reader.history(f.tab, "default")!!.single().films.size)
    }
    @Test fun finishWithoutStartedWorkerStopsAllQueuedTasks() {
        val f = Fixture(count = 4)
        val id = f.start()
        assertTrue(f.groups.finish(f.tab, "default", id))
        assertEquals(VideoSearchRunState.STOPPED, f.store.runs(f.owner).single().status)
        assertTrue(f.store.tasks(f.owner, id).all {
            it.status == VideoSearchSiteState.STOPPED &&
                it.completionReason == null
        })
        assertTrue(f.reader.history(f.tab, "default")!!.single().films.isEmpty())
        assertFalse(f.groups.finish(f.tab, "default", id))
    }

}
