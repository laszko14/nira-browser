package com.prirai.android.nira.videosearch

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** P8 SR-04/LC-04: actual Room transactions, no fake Gecko success. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchRecoveryAndProgressTest {
    private val owner = VideoSearchScope.Normal("p8-normal")
    private fun db() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(), VideoSearchDatabase::class.java
    ).build()

    private fun sites(scope: VideoSearchScope.Normal = owner, n: Int = 1) =
        (1..n).map { i ->
            VideoSearchSite(
                id = "site$i", scope = scope, siteUrl = "https://s$i.example.org/",
                displayName = "Site $i", position = i, createdAtMs = i.toLong(),
                configurationState = VideoSiteConfigurationState.CONFIGURED,
                configuration = VideoSiteConfiguration("input.q", ".card",
                    submission = VideoSearchSubmission.FORM_SUBMIT)
            )
        }

    private fun plan(scope: VideoSearchScope.Normal, sites: List<VideoSearchSite>) =
        VideoSearchDispatchPlan.create(scope, "query",
            VideoSearchSettings(scope, sites.map { it.id }.toSet()), sites)

    private fun snapshot(site: Int = 1, count: Int, pages: Int = 1,
                         reason: VideoSearchPaginationStop =
                             VideoSearchPaginationStop.RESOURCE_LIMIT) =
        VideoSearchPaginationSnapshot(
            films = (0 until count).map { i ->
                VideoSearchExtractedFilm(
                    filmPageUrl = "https://s$site.example.org/watch?v=$i",
                    originalTitle = "Film $i", thumbnailUrl = null,
                    durationSeconds = null, titleSource = VideoResultTitleSource.CARD,
                    imageSource = VideoResultImageSource.UNKNOWN, originalIndex = i
                )
            },
            scannedCards = count, visitedPages = pages, pageActions = 0,
            reason = reason
        )

    @Test fun stoppedWorkerRetainsEveryCheckpointedCardWithoutFalseCompletion() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val p = plan(owner, sites())
            val journal = VideoSearchMultiRunJournal(repo, p, "stop-progress") { 100L }
            journal.initialize()
            val q = p.createQueue()
            q.lease(1)
            journal.progress(p.selectedSites.single(), snapshot(count = 8))
            assertEquals(8, repo.films(owner, journal.runId, "site1").size)
            assertEquals(VideoSearchSiteState.ACTIVE, repo.tasks(owner, journal.runId).single().status)
            q.stopPending()
            q.resolve("site1", VideoSearchQueueStatus.STOPPED)
            journal.finish(q)
            val saved = repo.resultsSnapshot(owner, journal.runId)!!
            assertEquals(8, saved.films.size)
            assertEquals(8, saved.tasks.single().uniqueResults)
            assertEquals(VideoSearchSiteState.STOPPED, saved.tasks.single().status)
            assertNull(saved.tasks.single().completionReason)
            assertEquals(VideoSearchRunState.STOPPED, saved.run.status)
        } finally { db.close() }
    }

    @Test fun progressingSameWindowTwiceNeverCreatesDuplicates() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val p = plan(owner, sites())
            val journal = VideoSearchMultiRunJournal(repo, p, "repeat-progress")
            journal.initialize()
            journal.progress(p.selectedSites.single(), snapshot(count = 4))
            journal.progress(p.selectedSites.single(), snapshot(count = 4))
            journal.progress(p.selectedSites.single(), snapshot(count = 9, pages = 2))
            journal.record(p.selectedSites.single(), VideoSearchQueueStatus.STOPPED)
            val saved = repo.resultsSnapshot(owner, journal.runId)!!
            assertEquals(9, saved.films.size)
            assertEquals(2, saved.tasks.single().visitedPages)
            assertEquals(9, saved.tasks.single().uniqueResults)
            assertEquals(VideoSearchSiteState.STOPPED, saved.tasks.single().status)
            assertNull(saved.tasks.single().completionReason)
        } finally { db.close() }
    }

    @Test fun normalTerminalAfterProgressIsIdempotentAndActuallyComplete() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val p = plan(owner, sites())
            val journal = VideoSearchMultiRunJournal(repo, p, "terminal-progress")
            journal.initialize()
            journal.progress(p.selectedSites.single(), snapshot(count = 4))
            journal.record(p.selectedSites.single(), VideoSearchQueueStatus.DONE,
                snapshot(count = 7, reason = VideoSearchPaginationStop.NO_MORE_RESULTS))
            val task = repo.tasks(owner, journal.runId).single()
            assertEquals(7, repo.films(owner, journal.runId, "site1").size)
            assertEquals(7, task.uniqueResults)
            assertEquals(VideoSearchSiteState.COMPLETED, task.status)
            assertEquals(VideoSearchCompletionReason.NO_MORE_RESULTS, task.completionReason)
        } finally { db.close() }
    }

    @Test fun simultaneousProgressOnDifferentSitesKeepsBothCounts() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val p = plan(owner, sites(n = 3))
            val journal = VideoSearchMultiRunJournal(repo, p, "concurrent-progress")
            journal.initialize()
            p.selectedSites.mapIndexed { i, site ->
                async(Dispatchers.Default) {
                    journal.progress(site, snapshot(site = i + 1, count = 8))
                }
            }.awaitAll()
            val saved = repo.resultsSnapshot(owner, journal.runId)!!
            assertEquals(24, saved.films.size)
            assertEquals(24, saved.tasks.sumOf { it.uniqueResults })
            assertTrue(saved.tasks.all { it.status == VideoSearchSiteState.ACTIVE })
        } finally { db.close() }
    }

    @Test fun orphanRecoveryKeepsFilmsAndStopsOnlyUnfinishedRunAndTasks() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val p = plan(owner, sites(n = 2))
            val orphan = VideoSearchMultiRunJournal(repo, p, "orphan") { 99L }
            orphan.initialize()
            orphan.progress(p.selectedSites.first(), snapshot(count = 12, pages = 2))
            val terminal = VideoSearchMultiRunJournal(repo, p, "terminal") { 110L }
            terminal.initialize()
            val queue = p.createQueue()
            queue.stopPending()
            terminal.finish(queue)
            assertEquals(1, repo.reconcileOrphanedRuns(owner, 130L))
            val run = repo.run(owner, "orphan")!!
            assertEquals(VideoSearchRunState.STOPPED, run.status)
            assertEquals(130L, run.completedAtMs)
            val tasks = repo.tasks(owner, "orphan")
            assertEquals(12, tasks.first { it.siteId == "site1" }.uniqueResults)
            assertEquals(12, repo.films(owner, "orphan", "site1").size)
            assertTrue(tasks.all { it.status == VideoSearchSiteState.STOPPED })
            assertEquals(VideoSearchRunState.STOPPED, repo.run(owner, "terminal")!!.status)
            assertEquals(0, repo.reconcileOrphanedRuns(owner, 180L))
        } finally { db.close() }
    }

    @Test fun recoveringOtherProfileDoesNotStopDifferentProfilesActiveRun() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val p = plan(owner, sites())
            val first = VideoSearchMultiRunJournal(repo, p, "same-run")
            first.initialize()
            val work = VideoSearchScope.Normal("p8-work")
            val workPlan = plan(work, sites(work))
            val second = VideoSearchMultiRunJournal(repo, workPlan, "same-run")
            second.initialize()
            assertEquals(1, repo.reconcileOrphanedRuns(owner, 130L))
            assertEquals(VideoSearchRunState.STOPPED, repo.run(owner, "same-run")!!.status)
            assertEquals(VideoSearchRunState.RUNNING, repo.run(work, "same-run")!!.status)
        } finally { db.close() }
    }

    @Test fun processGatePreventsReconciliationOfNewLiveWork() = runBlocking {
        val db = db()
        try {
            val scope = VideoSearchScope.Normal("p8-gate")
            val repo = VideoSearchRepository(db.dao())
            assertEquals(0, repo.recoverOrphanedRunsOnce(scope))
            val p = plan(scope, sites(scope))
            val alive = VideoSearchMultiRunJournal(repo, p, "alive")
            alive.initialize()
            assertEquals(0, repo.recoverOrphanedRunsOnce(scope))
            assertEquals(VideoSearchRunState.RUNNING, repo.run(scope, "alive")!!.status)
        } finally { db.close() }
    }

    @Test fun processRestartSimulationReconcilesFileBackedHistory() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "video-search-p8-restart.db"
        context.deleteDatabase(name)
        try {
            val scope = VideoSearchScope.Normal("p8-restart")
            val first = Room.databaseBuilder(context, VideoSearchDatabase::class.java, name).build()
            try {
                val repo = VideoSearchRepository(first.dao())
                val p = plan(scope, sites(scope))
                val journal = VideoSearchMultiRunJournal(repo, p, "restart") { 150L }
                journal.initialize()
                journal.progress(p.selectedSites.single(), snapshot(count = 5))
                assertEquals(VideoSearchRunState.RUNNING, repo.run(scope, "restart")!!.status)
            } finally { first.close() }
            val reopened = Room.databaseBuilder(context,
                VideoSearchDatabase::class.java, name).build()
            try {
                val repo = VideoSearchRepository(reopened.dao())
                assertEquals(1, repo.reconcileOrphanedRuns(scope, 200L))
                val result = repo.resultsSnapshot(scope, "restart")!!
                assertEquals(5, result.films.size)
                assertEquals(5, result.tasks.single().uniqueResults)
                assertEquals(VideoSearchRunState.STOPPED, result.run.status)
                assertEquals(VideoSearchSiteState.STOPPED, result.tasks.single().status)
                assertEquals(200L, result.run.completedAtMs)
                assertEquals(0, repo.reconcileOrphanedRuns(scope, 210L))
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
