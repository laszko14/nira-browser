package com.prirai.android.nira.videosearch

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * One end-to-end native contract from manual picker approval through a
 * bounded multi-page Gecko extraction *fixture*, Room checkpoint persistence,
 * immutable launch quotas and results presentation. Live web navigation is
 * deliberately NOT claimed by this JVM test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchV32IntegrationTest {
    private val owner = VideoSearchScope.Normal("default")
    private val other = VideoSearchScope.Normal("other")
    private fun db() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        VideoSearchDatabase::class.java
    ).build()

    private fun film(host: String, n: Int, index: Int = n) =
        VideoSearchExtractedFilm(
            "https://$host.example.org/watch?id=$n&quality=hd",
            "Wspólny tytuł", null, null, VideoResultTitleSource.CARD,
            VideoResultImageSource.UNKNOWN, index,
        )

    @Test fun pickedNextToFiveHundredRoomToResultsWithoutCrossSiteDedupOrProfileLeak() =
        runBlocking {
            val database = db()
            try {
                val repo = VideoSearchRepository(database.dao())
                val first = repo.addSite(owner, "https://first.example.org/search",
                    nowMs = 1, newId = "first")
                val second = repo.addSite(owner, "https://second.example.org/search",
                    nowMs = 2, newId = "second")
                val controls = VideoSiteConfiguration(
                    "input[name=\"q\"]", resultsCardSelector = null,
                    submission = VideoSearchSubmission.CLICK_SUBMIT,
                    submitSelector = "button.search"
                )
                val picker = VideoSearchBridgeMessage.Picked(
                    "0123456789abcdef0123456789abcdef",
                    "fedcba9876543210fedcba9876543210",
                    "next", "nav.pager", false, VideoSearchPagination.NEXT_LINK
                )
                val configured = VideoSearchManualStages.withResults(
                    controls, "article.card", picker
                )!!
                assertEquals("nav.pager", configured.nextPageSelector)
                assertTrue(repo.applyManualConfiguration(first, configured, nowMs = 3))
                assertTrue(repo.applyManualConfiguration(second,
                    configured.copy(pagination = VideoSearchPagination.LOAD_MORE,
                        nextPageSelector = "button.more"), nowMs = 4))
                assertTrue(repo.setSiteLimit(owner, first.id, 500, nowMs = 5))
                assertTrue(repo.setSiteLimit(owner, second.id, 100, nowMs = 6))
                assertTrue(repo.setSiteSelected(owner, first.id, true))
                assertTrue(repo.setSiteSelected(owner, second.id, true))
                // Two controlled worker leases are required to resolve both
                // sites before finalizing the same journal transaction.
                repo.setParallelism(owner, VideoSearchParallelism.TWO)
                val plan = repo.prepareDispatch(owner, "film")
                assertEquals(600, VideoSearchResultBudgets.aggregateLimit(
                    plan.selectedSites.map { it.resultLimit }))
                assertEquals(2, plan.runnableSites.size)
                assertEquals(500, plan.selectedSites.first { it.id == first.id }.resultLimit)
                val journal = VideoSearchMultiRunJournal(repo, plan, "integrated-500")
                journal.initialize()
                val chosen = plan.selectedSites.single { it.id == first.id }
                val collector = VideoSearchPaginationCollector(first.siteUrl, 500)
                assertTrue(collector.beginDocument(first.siteUrl))

                // One 500-card SPA-style document, with verified increments of 16.
                for (start in 0 until 500 step 16) {
                    val size = minOf(16, 500 - start)
                    val batch = VideoSearchExtractedPage(
                        "ok", (0 until size).map { i ->
                            film("first", start + i, start + i)
                        }, size, moreOnPage = start + size < 500
                    )
                    assertEquals(size, collector.appendWindow(start, batch))
                    if (start + size == 240 || start + size == 480) {
                        journal.progress(chosen,
                            collector.snapshot(VideoSearchPaginationStop.RESOURCE_LIMIT))
                        val saved = repo.resultsSnapshot(owner, journal.runId)!!
                        assertEquals(start + size, saved.films.size)
                        assertEquals(VideoSearchRunState.RUNNING, saved.run.status)
                    }
                }
                assertTrue(collector.reachedLimit)
                journal.record(chosen, VideoSearchQueueStatus.DONE,
                    collector.snapshot(VideoSearchPaginationStop.LIMIT_REACHED))

                val otherSite = plan.selectedSites.single { it.id == second.id }
                val otherCollector = VideoSearchPaginationCollector(second.siteUrl, 100)
                assertTrue(otherCollector.beginDocument(second.siteUrl))
                for (start in 0 until 100 step 16) {
                    val size = minOf(16, 100 - start)
                    assertEquals(size, otherCollector.appendWindow(start,
                        VideoSearchExtractedPage("ok", (0 until size).map { i ->
                            film("second", start + i, start + i)
                        }, size, start + size < 100)))
                }
                journal.record(otherSite, VideoSearchQueueStatus.DONE,
                    otherCollector.snapshot(VideoSearchPaginationStop.LIMIT_REACHED))
                val queue = plan.createQueue()
                queue.lease(2)
                plan.selectedSites.forEach {
                    queue.resolve(it.id, VideoSearchQueueStatus.DONE)
                }
                journal.finish(queue)

                val snapshot = repo.resultsSnapshot(owner, journal.runId)!!
                assertEquals(VideoSearchRunState.COMPLETED, snapshot.run.status)
                assertEquals(600, snapshot.films.size)
                assertEquals(600, snapshot.tasks.sumOf { it.uniqueResults })
                assertTrue(snapshot.tasks.all {
                    it.status == VideoSearchSiteState.COMPLETED &&
                        it.completionReason == VideoSearchCompletionReason.LIMIT_REACHED
                })
                assertFalse(journal.hasCapacity())
                assertNull(repo.resultsSnapshot(other, journal.runId))

                val display = VideoSearchResultsPresenter.project(
                    snapshot.run, snapshot.tasks, snapshot.films, snapshot.siteNames)
                assertEquals(600, display.totalFilms)
                assertEquals(500, display.visibleFilms.count { it.siteId == first.id })
                assertEquals(100, display.visibleFilms.count { it.siteId == second.id })
                assertEquals("Wspólny tytuł", display.visibleFilms.first().originalTitle)
                assertEquals(2, VideoSearchResultsPresenter.groups(display).size)
                // Physical list/grid rendering still uses lazy 150-card windows.
                val visible = display.copy(visibleFilms = display.visibleFilms.take(150))
                assertEquals(150, visible.visibleFilms.size)
                assertEquals(600, visible.totalFilms)
                assertEquals(600, display.sources.sumOf { it.count })
            } finally {
                database.close()
            }
        }

    @Test fun configurationAndBudgetCannotCrossPrivateToRoomBoundary() {
        val scope = VideoSearchScope.Private("default", "private-v32")
        val memory = VideoSearchPrivateStore()
        memory.start(scope)
        val site = VideoSearchSite(
            id = "temporary", scope = scope,
            siteUrl = "https://private.example.org/search",
            displayName = "Private", position = 0, createdAtMs = 1
        )
        memory.putSite(site.copy(resultLimit = 10_000))
        assertEquals(10_000, memory.sites(scope).single().resultLimit)
        assertEquals(10_000, VideoSearchResultBudgets.requireSiteLimit(10_000))
        memory.finish(scope)
        try {
            memory.sites(scope)
            fail("Closed private lifetime remained readable")
        } catch (_: IllegalStateException) {
            // Closing the last private tab must retire all RAM state.
        }
    }
}
