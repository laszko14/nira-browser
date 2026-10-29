package com.prirai.android.nira.videosearch

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 5C: real Room atomic run/task/film persistence, no Gecko needed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchMultiRunJournalTest {
    private val owner = VideoSearchScope.Normal("default")
    private fun db() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        VideoSearchDatabase::class.java
    ).build()

    private fun sites(n: Int = 2): List<VideoSearchSite> =
        (1..n).map { i ->
            VideoSearchSite(
                id = "site$i", scope = owner, siteUrl = "https://s$i.example.org/",
                displayName = "Witryna $i", position = i,
                configurationState = VideoSiteConfigurationState.CONFIGURED,
                configuration = VideoSiteConfiguration(
                    "input.search", ".film", submission = VideoSearchSubmission.FORM_SUBMIT
                ),
                createdAtMs = i.toLong(),
            )
        }

    private fun plan(sites: List<VideoSearchSite>) = VideoSearchDispatchPlan.create(
        owner, "komedia",
        VideoSearchSettings(owner, sites.map { it.id }.toSet(),
            parallelism = VideoSearchParallelism.TWO),
        sites
    )

    private fun snapshot(
        host: String = "s1",
        reason: VideoSearchPaginationStop = VideoSearchPaginationStop.NO_MORE_RESULTS,
        count: Int = 2,
    ) = VideoSearchPaginationSnapshot(
        films = (1..count).map { idx ->
            VideoSearchExtractedFilm(
                filmPageUrl = "https://$host.example.org/watch?id=$idx&lang=pl",
                originalTitle = "Film $idx", thumbnailUrl = null,
                durationSeconds = 120L + idx,
                titleSource = VideoResultTitleSource.CARD,
                imageSource = VideoResultImageSource.UNKNOWN,
                originalIndex = idx - 1,
            )
        },
        scannedCards = count, visitedPages = 1, pageActions = 1,
        reason = reason,
    )

    @Test fun initialTransactionCreatesRunAndTasksWithNoFilms() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val plan = plan(sites())
            val journal = VideoSearchMultiRunJournal(repo, plan, "run-init")
            journal.initialize()
            assertEquals(VideoSearchRunState.RUNNING, repo.run(owner, "run-init")!!.status)
            assertEquals(listOf("site1", "site2"), repo.run(owner, "run-init")!!.siteIds)
            assertEquals(2, repo.tasks(owner, "run-init").size)
            assertTrue(repo.films(owner, "run-init", "site1").isEmpty())
        } finally { db.close() }
    }

    @Test fun verifiedCompletionPersistsFilmUrlQueryAndMetadata() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val plan = plan(sites(1))
            val journal = VideoSearchMultiRunJournal(repo, plan, "run-complete")
            journal.initialize()
            val queue = plan.createQueue()
            queue.lease(1)
            journal.record(plan.selectedSites.single(), VideoSearchQueueStatus.DONE, snapshot())
            queue.resolve("site1", VideoSearchQueueStatus.DONE)
            journal.finish(queue)
            val run = repo.run(owner, "run-complete")!!
            val task = repo.tasks(owner, run.id).single()
            val films = repo.films(owner, run.id, "site1")
            assertEquals(VideoSearchRunState.COMPLETED, run.status)
            assertEquals(VideoSearchSiteState.COMPLETED, task.status)
            assertEquals(VideoSearchCompletionReason.NO_MORE_RESULTS, task.completionReason)
            assertEquals(2, task.uniqueResults)
            assertEquals(2, films.size)
            assertEquals("https://s1.example.org/watch?id=1&lang=pl", films.first().filmPageUrl)
            assertEquals("Film 1", films.first().originalTitle)
            assertEquals(121L, films.first().durationSeconds)
            assertEquals("Witryna 1", films.first().siteName)
        } finally { db.close() }
    }

    @Test fun explicitEmptyEvidenceCompletesNormalRunWithZeroFilms() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val plan = plan(sites(1))
            val journal = VideoSearchMultiRunJournal(repo, plan, "run-empty-confirmed")
            journal.initialize()
            val queue = plan.createQueue()
            queue.lease(1)
            journal.record(plan.selectedSites.single(), VideoSearchQueueStatus.DONE,
                snapshot(reason = VideoSearchPaginationStop.NO_RESULTS, count = 0))
            queue.resolve("site1", VideoSearchQueueStatus.DONE)
            journal.finish(queue)
            val result = repo.resultsSnapshot(owner, "run-empty-confirmed")!!
            assertTrue(result.films.isEmpty())
            assertEquals(VideoSearchRunState.COMPLETED, result.run.status)
            assertEquals(VideoSearchSiteState.COMPLETED, result.tasks.single().status)
            assertEquals(0, result.tasks.single().uniqueResults)
            assertEquals(VideoSearchCompletionReason.NO_MORE_RESULTS,
                result.tasks.single().completionReason)
        } finally { db.close() }
    }

    @Test fun blankOrUnverifiedEndNeverMarksNormalRunComplete() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val plan = plan(sites(1))
            val journal = VideoSearchMultiRunJournal(repo, plan, "run-empty-uncertain")
            journal.initialize()
            journal.record(plan.selectedSites.single(), VideoSearchQueueStatus.NEEDS_ATTENTION,
                snapshot(reason = VideoSearchPaginationStop.NO_MORE_RESULTS, count = 0))
            val task = repo.tasks(owner, journal.runId).single()
            assertEquals(VideoSearchSiteState.NEEDS_ATTENTION, task.status)
            assertNull(task.completionReason)
        } finally { db.close() }
    }

    @Test fun limitReachedIsValidOnlyWithActualFilm() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val plan = plan(sites(1))
            val journal = VideoSearchMultiRunJournal(repo, plan, "run-limit")
            journal.initialize()
            val queue = plan.createQueue()
            queue.lease(1)
            journal.record(plan.selectedSites.single(), VideoSearchQueueStatus.DONE,
                snapshot(reason = VideoSearchPaginationStop.LIMIT_REACHED))
            queue.resolve("site1", VideoSearchQueueStatus.DONE)
            journal.finish(queue)
            assertEquals(VideoSearchCompletionReason.LIMIT_REACHED,
                repo.tasks(owner, "run-limit").single().completionReason)
        } finally { db.close() }
    }

    @Test fun noPaginationCreatesPartialStoppedTaskButKeepsFilms() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val plan = plan(sites(1))
            val journal = VideoSearchMultiRunJournal(repo, plan, "run-partial")
            journal.initialize()
            val queue = plan.createQueue()
            queue.lease(1)
            journal.record(plan.selectedSites.single(), VideoSearchQueueStatus.STOPPED,
                snapshot(reason = VideoSearchPaginationStop.UNSUPPORTED))
            queue.resolve("site1", VideoSearchQueueStatus.STOPPED)
            journal.finish(queue)
            val task = repo.tasks(owner, "run-partial").single()
            assertEquals(VideoSearchSiteState.STOPPED, task.status)
            assertNull(task.completionReason)
            assertEquals(2, task.uniqueResults)
            assertEquals(2, repo.films(owner, "run-partial", "site1").size)
            assertEquals(VideoSearchRunState.STOPPED, repo.run(owner, "run-partial")!!.status)
        } finally { db.close() }
    }

    @Test fun formAckWithoutConfirmedResultsNeverCompletesTask() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val plan = plan(sites(1))
            val journal = VideoSearchMultiRunJournal(repo, plan, "run-ack")
            journal.initialize()
            val queue = plan.createQueue()
            queue.lease(1)
            journal.record(plan.selectedSites.single(), VideoSearchQueueStatus.NEEDS_ATTENTION)
            queue.resolve("site1", VideoSearchQueueStatus.NEEDS_ATTENTION)
            journal.finish(queue)
            val task = repo.tasks(owner, "run-ack").single()
            assertEquals(VideoSearchSiteState.NEEDS_ATTENTION, task.status)
            assertEquals(0, task.uniqueResults)
            assertNull(task.completionReason)
        } finally { db.close() }
    }

    @Test fun multiSiteCheckpointsDoNotOverwriteOtherSitesOrFilms() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val plan = plan(sites())
            val journal = VideoSearchMultiRunJournal(repo, plan, "run-multi")
            journal.initialize()
            val queue = plan.createQueue()
            queue.lease(2)
            journal.record(plan.selectedSites[1], VideoSearchQueueStatus.DONE, snapshot("s2"))
            queue.resolve("site2", VideoSearchQueueStatus.DONE)
            journal.record(plan.selectedSites[0], VideoSearchQueueStatus.DONE, snapshot("s1"))
            queue.resolve("site1", VideoSearchQueueStatus.DONE)
            journal.finish(queue)
            assertEquals(2, repo.films(owner, "run-multi", "site1").size)
            assertEquals(2, repo.films(owner, "run-multi", "site2").size)
            assertEquals(VideoSearchRunState.COMPLETED, repo.run(owner, "run-multi")!!.status)
        } finally { db.close() }
    }

    @Test fun simultaneousWorkersPersistWithoutCounterLoss() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val plan = plan(sites(2))
            val journal = VideoSearchMultiRunJournal(repo, plan, "run-parallel")
            journal.initialize()
            val q = plan.createQueue()
            q.lease(2)
            plan.selectedSites.mapIndexed { idx, site ->
                async {
                    journal.record(site, VideoSearchQueueStatus.DONE,
                        snapshot("s${idx + 1}"))
                    q.resolve(site.id, VideoSearchQueueStatus.DONE)
                }
            }.awaitAll()
            journal.finish(q)
            assertEquals(VideoSearchRunState.COMPLETED,
                repo.run(owner, "run-parallel")!!.status)
            assertEquals(4, repo.tasks(owner, "run-parallel").sumOf { it.uniqueResults })
            assertEquals(2, repo.films(owner, "run-parallel", "site1").size)
            assertEquals(2, repo.films(owner, "run-parallel", "site2").size)
        } finally { db.close() }
    }

    @Test fun stoppedQueueFinalizesPendingTasksTruthfully() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val plan = plan(sites(3))
            val journal = VideoSearchMultiRunJournal(repo, plan, "run-stop")
            journal.initialize()
            val queue = plan.createQueue()
            queue.lease(1)
            queue.stopPending()
            queue.resolve("site1", VideoSearchQueueStatus.STOPPED)
            journal.finish(queue)
            assertEquals(VideoSearchRunState.STOPPED, repo.run(owner, "run-stop")!!.status)
            assertEquals(3, repo.tasks(owner, "run-stop").size)
            assertTrue(repo.tasks(owner, "run-stop").all {
                it.status == VideoSearchSiteState.STOPPED
            })
        } finally { db.close() }
    }

    @Test fun rejectsForeignProfileBeforeWritingAnyFilms() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val plan = plan(sites(1))
            val journal = VideoSearchMultiRunJournal(repo, plan, "run-scope")
            journal.initialize()
            val foreign = plan.selectedSites.single()
                .copy(scope = VideoSearchScope.Normal("work"))
            try {
                journal.record(foreign, VideoSearchQueueStatus.DONE, snapshot())
                fail("Foreign scope was accepted")
            } catch (_: IllegalArgumentException) {}
            assertTrue(repo.films(owner, "run-scope", "site1").isEmpty())
        } finally { db.close() }
    }

    @Test fun cannotRecordUnknownSiteOrQueueStatusAsCompletion() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val plan = plan(sites(1))
            val journal = VideoSearchMultiRunJournal(repo, plan, "run-invalid")
            journal.initialize()
            try {
                journal.record(plan.selectedSites.single().copy(id = "alien"),
                    VideoSearchQueueStatus.DONE, snapshot())
                fail("Unknown site accepted")
            } catch (_: IllegalArgumentException) {}
            try {
                journal.record(plan.selectedSites.single(),
                    VideoSearchQueueStatus.LEASED, snapshot())
                fail("Pending task marked finished")
            } catch (_: IllegalArgumentException) {}
            assertEquals(0, repo.tasks(owner, "run-invalid").single().uniqueResults)
        } finally { db.close() }
    }

    @Test fun duplicateFinalizeIsIdempotentAndDoesNotAlterRunStatus() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val plan = plan(sites(1))
            val journal = VideoSearchMultiRunJournal(repo, plan, "run-once")
            journal.initialize()
            val q = plan.createQueue()
            q.stopPending()
            journal.finish(q)
            val prior = repo.run(owner, "run-once")
            journal.finish(q)
            assertEquals(prior, repo.run(owner, "run-once"))
        } finally { db.close() }
    }

    @Test fun failedWorkerIsFailedAndNeverCompletedOnFinalCheckpoint() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val plan = plan(sites(1))
            val journal = VideoSearchMultiRunJournal(repo, plan, "run-failed")
            journal.initialize()
            val queue = plan.createQueue()
            queue.lease(1)
            queue.resolve("site1", VideoSearchQueueStatus.FAILED)
            journal.finish(queue)
            assertEquals(VideoSearchSiteState.FAILED,
                repo.tasks(owner, "run-failed").single().status)
            assertEquals(VideoSearchRunState.FAILED, repo.run(owner, "run-failed")!!.status)
        } finally { db.close() }
    }
}
