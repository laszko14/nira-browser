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

/** P7/SR-03: exercise real Room transactions and the normal journal. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchAggregateBudgetTest {
    private val owner = VideoSearchScope.Normal("default")
    private fun db() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(), VideoSearchDatabase::class.java
    ).build()

    private fun sites(n: Int, scope: VideoSearchScope.Normal = owner) =
        (1..n).map { i ->
            VideoSearchSite(
                id = "s$i", scope = scope, siteUrl = "https://s$i.example.org/",
                displayName = "S$i", position = i, createdAtMs = i.toLong(),
                configurationState = VideoSiteConfigurationState.CONFIGURED,
                configuration = VideoSiteConfiguration("input.query", ".card",
                    submission = VideoSearchSubmission.FORM_SUBMIT),
            )
        }

    private fun plan(scope: VideoSearchScope.Normal, sites: List<VideoSearchSite>) =
        VideoSearchDispatchPlan.create(
            scope, "film", VideoSearchSettings(scope,
                selectedSiteIds = sites.map { it.id }.toSet(),
                parallelism = VideoSearchParallelism.ALL), sites
        )

    private fun snapshot(site: Int, count: Int = 200) =
        VideoSearchPaginationSnapshot(
            films = (0 until count).map { idx ->
                VideoSearchExtractedFilm(
                    filmPageUrl = "https://s$site.example.org/watch?v=$idx",
                    originalTitle = "Film $idx", thumbnailUrl = null,
                    durationSeconds = null, titleSource = VideoResultTitleSource.CARD,
                    imageSource = VideoResultImageSource.UNKNOWN, originalIndex = idx,
                )
            },
            scannedCards = count, visitedPages = 1, pageActions = 0,
            reason = if (count == 200) VideoSearchPaginationStop.LIMIT_REACHED
                else VideoSearchPaginationStop.NO_MORE_RESULTS,
        )

    private suspend fun fill(
        journal: VideoSearchMultiRunJournal,
        plan: VideoSearchDispatchPlan,
        counts: List<Int>,
    ) {
        plan.selectedSites.take(counts.size).forEachIndexed { i, site ->
            journal.record(site, VideoSearchQueueStatus.DONE, snapshot(i + 1, counts[i]))
        }
    }

    @Test fun tenFullSitesCompleteAtExactlyTwoThousand() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val p = plan(owner, sites(10))
            val journal = VideoSearchMultiRunJournal(repo, p, "ten")
            journal.initialize()
            fill(journal, p, List(10) { 200 })
            val queue = p.createQueue()
            queue.lease(10)
            p.selectedSites.forEach { queue.resolve(it.id, VideoSearchQueueStatus.DONE) }
            journal.finish(queue)
            val saved = repo.resultsSnapshot(owner, "ten")!!
            assertEquals(2_000, saved.films.size)
            assertEquals(VideoSearchRunState.COMPLETED, saved.run.status)
            assertTrue(saved.tasks.all { it.uniqueResults == 200 &&
                it.status == VideoSearchSiteState.COMPLETED })
            assertFalse(journal.hasCapacity())
        } finally { db.close() }
    }

    @Test fun elevenFullSitesStopTruthfullyAndNeverDeleteEarlierResults() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val p = plan(owner, sites(11))
            val journal = VideoSearchMultiRunJournal(repo, p, "eleven")
            journal.initialize()
            fill(journal, p, List(10) { 200 })
            val earlier = repo.films(owner, "eleven", "s1").map { it.id }
            journal.record(p.selectedSites.last(), VideoSearchQueueStatus.DONE, snapshot(11))
            val queue = p.createQueue()
            queue.lease(11)
            p.selectedSites.forEach { queue.resolve(it.id, VideoSearchQueueStatus.DONE) }
            journal.finish(queue)
            val saved = repo.resultsSnapshot(owner, "eleven")!!
            assertEquals(2_000, saved.films.size)
            assertEquals(earlier, repo.films(owner, "eleven", "s1").map { it.id })
            assertEquals(0, saved.films.count { it.siteId == "s11" })
            val last = saved.tasks.single { it.siteId == "s11" }
            assertEquals(0, last.uniqueResults)
            assertEquals(VideoSearchSiteState.STOPPED, last.status)
            assertEquals(VideoSearchSiteIssue.RESULT_BUDGET_REACHED, last.issue)
            assertNull(last.completionReason)
            assertEquals(VideoSearchRunState.STOPPED, saved.run.status)
        } finally { db.close() }
    }

    @Test fun lastSingleSlotIsAtomicAcrossConcurrentRoomWriters() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val p = plan(owner, sites(11))
            val journal = VideoSearchMultiRunJournal(repo, p, "race")
            journal.initialize()
            fill(journal, p, List(9) { 200 } + 199)
            assertEquals(1999, repo.resultsSnapshot(owner, "race")!!.films.size)
            val outcomes = (0..2).map { idx ->
                async(Dispatchers.Default) {
                    try {
                        repo.putFilm(VideoSearchFilm(
                            id = "race-$idx", runId = "race", siteId = "s11",
                            scope = owner, siteName = "S11",
                            filmPageUrl = "https://s11.example.org/watch?race=$idx",
                            originalIndex = idx, foundAtMs = 200,
                        ))
                    } catch (_: IllegalArgumentException) { false }
                }
            }.awaitAll()
            assertEquals(1, outcomes.count { it })
            assertEquals(2_000, repo.resultsSnapshot(owner, "race")!!.films.size)
            assertEquals(1, repo.films(owner, "race", "s11").size)
            assertEquals(200, repo.films(owner, "race", "s1").size)
        } finally { db.close() }
    }

    @Test fun parallelSiteCheckpointsCannotOverflowBudget() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val p = plan(owner, sites(11))
            val journal = VideoSearchMultiRunJournal(repo, p, "parallel")
            journal.initialize()
            p.selectedSites.mapIndexed { i, site ->
                async(Dispatchers.Default) {
                    journal.record(site, VideoSearchQueueStatus.DONE, snapshot(i + 1))
                }
            }.awaitAll()
            val saved = repo.resultsSnapshot(owner, "parallel")!!
            assertEquals(2_000, saved.films.size)
            assertEquals(2_000, saved.tasks.sumOf { it.uniqueResults })
            assertEquals(1, saved.tasks.count {
                it.status == VideoSearchSiteState.STOPPED &&
                    it.issue == VideoSearchSiteIssue.RESULT_BUDGET_REACHED
            })
            assertFalse(journal.hasCapacity())
        } finally { db.close() }
    }

    @Test fun partialLastSiteKeepsEarlierCardsAndDoesNotClaimCompletion() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val p = plan(owner, sites(11))
            val journal = VideoSearchMultiRunJournal(repo, p, "partial")
            journal.initialize()
            fill(journal, p, List(9) { 200 } + 199)
            val earlier = repo.films(owner, "partial", "s10").map { it.filmPageUrl }
            journal.record(p.selectedSites.last(), VideoSearchQueueStatus.DONE, snapshot(11, 20))
            val saved = repo.resultsSnapshot(owner, "partial")!!
            assertEquals(2_000, saved.films.size)
            assertEquals(earlier, repo.films(owner, "partial", "s10").map { it.filmPageUrl })
            val last = saved.tasks.single { it.siteId == "s11" }
            assertEquals(1, last.uniqueResults)
            assertEquals(VideoSearchSiteState.STOPPED, last.status)
            assertEquals(VideoSearchSiteIssue.RESULT_BUDGET_REACHED, last.issue)
            assertNull(last.completionReason)
        } finally { db.close() }
    }

    @Test fun duplicatesDoNotConsumeCapacityAndDifferentSitesStayDistinct() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val p = plan(owner, sites(2))
            val journal = VideoSearchMultiRunJournal(repo, p, "duplicates")
            journal.initialize()
            val sameUrl = "https://shared.example.org/watch?x=1"
            val first = VideoSearchFilm("one", "duplicates", "s1", owner,
                "Site 1", sameUrl, originalIndex = 0, foundAtMs = 5)
            assertTrue(repo.putFilm(first))
            assertFalse(repo.putFilm(first.copy(id = "same-url")))
            assertTrue(repo.putFilm(first.copy(
                id = "other-site", siteId = "s2", siteName = "Site 2")))
            assertTrue(repo.putFilm(first.copy(originalIndex = 4)))
            assertEquals(2, repo.resultsSnapshot(owner, "duplicates")!!.films.size)
            assertEquals(1, repo.films(owner, "duplicates", "s1").size)
            assertEquals(1, repo.films(owner, "duplicates", "s2").size)
        } finally { db.close() }
    }

    @Test fun otherRunsAndProfilesHaveIndependentCapacity() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val p = plan(owner, sites(10))
            val full = VideoSearchMultiRunJournal(repo, p, "same")
            full.initialize()
            fill(full, p, List(10) { 200 })
            val otherRun = VideoSearchMultiRunJournal(repo, p, "other")
            otherRun.initialize()
            otherRun.record(p.selectedSites.first(), VideoSearchQueueStatus.DONE, snapshot(1))
            val work = VideoSearchScope.Normal("work")
            val wp = plan(work, sites(1, work))
            val otherProfile = VideoSearchMultiRunJournal(repo, wp, "same")
            otherProfile.initialize()
            otherProfile.record(wp.selectedSites.single(), VideoSearchQueueStatus.DONE, snapshot(1))
            assertEquals(2_000, repo.resultsSnapshot(owner, "same")!!.films.size)
            assertEquals(200, repo.resultsSnapshot(owner, "other")!!.films.size)
            assertEquals(200, repo.resultsSnapshot(work, "same")!!.films.size)
        } finally { db.close() }
    }

    @Test fun privateRunCannotEnterNormalRoomJournal() = runBlocking {
        val db = db()
        try {
            val repo = VideoSearchRepository(db.dao())
            val scope = VideoSearchScope.Private("default", "private-epoch")
            val run = VideoSearchRun("private-run", scope, "film", listOf("s1"),
                VideoSearchParallelism.ONE, VideoSearchRunState.RUNNING, 1L)
            val task = VideoSearchSiteTask("private-run", "s1", scope)
            try {
                repo.putJournalCheckpoint(run, task, emptyList())
                fail("Private run persisted to Room")
            } catch (_: IllegalArgumentException) {}
            assertTrue(repo.runs(owner).isEmpty())
        } finally { db.close() }
    }
}
