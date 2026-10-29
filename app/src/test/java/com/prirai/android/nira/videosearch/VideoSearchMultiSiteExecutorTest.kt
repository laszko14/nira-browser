package com.prirai.android.nira.videosearch

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Stage 5B: fake callbacks prove admission and submission semantics. */
class VideoSearchMultiSiteExecutorTest {
    private val owner = VideoSearchScope.Normal("default")

    private fun site(i: Int, configured: Boolean = true) = VideoSearchSite(
        id = "site$i", scope = owner, siteUrl = "https://site$i.example.org/",
        displayName = "Site $i", position = i, createdAtMs = i.toLong(),
        configurationState = if (configured) VideoSiteConfigurationState.CONFIGURED
            else VideoSiteConfigurationState.NEEDS_CONFIGURATION,
        configuration = if (configured)
            VideoSiteConfiguration("input.search", ".item",
                submission = VideoSearchSubmission.FORM_SUBMIT) else null
    )

    private fun queue(
        n: Int, mode: VideoSearchParallelism = VideoSearchParallelism.ALL,
    ): VideoSearchDispatchQueue {
        val sites = (1..n).map(::site)
        return VideoSearchDispatchPlan.create(
            owner, "film",
            VideoSearchSettings(owner, selectedSiteIds = sites.map { it.id }.toSet(),
                parallelism = mode), sites,
        ).createQueue()
    }

    @Test fun multiSiteUsesEachSiteExactlyOnceAndPreservesFifo() = runBlocking {
        val q = queue(7, VideoSearchParallelism.ONE)
        val visited = mutableListOf<String>()
        val result = VideoSearchMultiSiteExecutor.dispatch(q, { 2 }, { true }) {
            visited.add(it.id)
            VideoSearchQueueStatus.SUBMITTED
        }
        assertEquals((1..7).map { "site$it" }, visited)
        assertEquals(7, result.submitted)
        assertEquals(0, result.finished)
        assertEquals(0, result.active)
    }

    @Test fun allModeHasNoLogicalFiveSiteCap() = runBlocking {
        val q = queue(11)
        val result = VideoSearchMultiSiteExecutor.dispatch(q, { 8 }, { true }) {
            VideoSearchQueueStatus.SUBMITTED
        }
        assertEquals(11, result.submitted)
        assertEquals(0, result.queued)
        assertEquals(0, result.finished)
    }

    @Test fun physicalTwoWorkerBudgetLimitsPeakConcurrency() = runBlocking {
        val q = queue(8, VideoSearchParallelism.ALL)
        val running = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val result = VideoSearchMultiSiteExecutor.dispatch(q, { 2 }, { true }) {
            val concurrent = running.incrementAndGet()
            peak.updateAndGet { previous -> maxOf(previous, concurrent) }
            delay(12L)
            running.decrementAndGet()
            VideoSearchQueueStatus.SUBMITTED
        }
        assertEquals(8, result.submitted)
        assertTrue(peak.get() <= 2)
        assertTrue(peak.get() >= 1)
    }

    @Test fun logicalOneWinsOverLargeResourceBudget() = runBlocking {
        val q = queue(6, VideoSearchParallelism.ONE)
        val running = AtomicInteger(0)
        val peak = AtomicInteger(0)
        VideoSearchMultiSiteExecutor.dispatch(q, { 8 }, { true }) {
            peak.updateAndGet { prev -> maxOf(prev, running.incrementAndGet()) }
            delay(3L)
            running.decrementAndGet()
            VideoSearchQueueStatus.SUBMITTED
        }
        assertEquals(1, peak.get())
    }

    @Test fun zeroMemoryBudgetStopsPendingWithoutRunningThem() = runBlocking {
        val q = queue(4)
        val result = VideoSearchMultiSiteExecutor.dispatch(q, { 0 }, { true }) {
            fail("A worker was started despite memory pressure")
            VideoSearchQueueStatus.SUBMITTED
        }
        assertEquals(4, result.stopped)
        assertEquals(0, result.submitted)
    }

    @Test fun permissionReductionStopsFurtherWavesWithoutOvercommit() = runBlocking {
        val q = queue(5)
        var calls = 0
        val result = VideoSearchMultiSiteExecutor.dispatch(q, {
            calls++
            if (calls == 1) 2 else 0
        }, { true }) {
            VideoSearchQueueStatus.SUBMITTED
        }
        assertEquals(2, result.submitted)
        assertEquals(3, result.stopped)
    }

    @Test fun changingProfileStopsPendingJobsWithoutFalseCompletion() = runBlocking {
        val q = queue(6)
        var valid = true
        val result = VideoSearchMultiSiteExecutor.dispatch(q, { 2 }, { valid }) {
            valid = false
            VideoSearchQueueStatus.SUBMITTED
        }
        assertEquals(6, result.selected)
        assertTrue(result.stopped > 0)
        assertEquals(0, result.finished)
    }

    @Test fun invalidSiteReturnsAttentionNotCompleted() = runBlocking {
        val q = queue(3)
        val result = VideoSearchMultiSiteExecutor.dispatch(q, { 2 }, { true }) {
            if (it.id == "site2") VideoSearchQueueStatus.NEEDS_ATTENTION
            else VideoSearchQueueStatus.SUBMITTED
        }
        assertEquals(1, result.needsAttention)
        assertEquals(2, result.submitted)
        assertEquals(0, result.finished)
    }

    @Test fun exceptionMarksFailedButOtherSitesContinue() = runBlocking {
        val q = queue(4)
        val result = VideoSearchMultiSiteExecutor.dispatch(q, { 1 }, { true }) {
            if (it.id == "site2") throw IllegalStateException("remote form error")
            VideoSearchQueueStatus.SUBMITTED
        }
        assertEquals(1, result.failed)
        assertEquals(3, result.submitted)
        assertEquals(0, result.active)
    }

    @Test fun workerMayReportStoppedWithoutLyingAboutSearchSuccess() = runBlocking {
        val q = queue(2)
        val result = VideoSearchMultiSiteExecutor.dispatch(q, { 2 }, { true }) {
            if (it.id == "site1") VideoSearchQueueStatus.STOPPED
            else VideoSearchQueueStatus.SUBMITTED
        }
        assertEquals(1, result.stopped)
        assertEquals(1, result.submitted)
    }

    @Test fun queueSubmittedStatusCanBeSetOnlyFromAnActiveLease() {
        val q = queue(1)
        assertFalse(q.resolve("site1", VideoSearchQueueStatus.SUBMITTED))
        assertEquals(1, q.lease(1).size)
        assertTrue(q.resolve("site1", VideoSearchQueueStatus.SUBMITTED))
        assertFalse(q.resolve("site1", VideoSearchQueueStatus.SUBMITTED))
        assertEquals(1, q.snapshot().submitted)
        assertEquals(0, q.snapshot().finished)
    }

    @Test fun budgetPolicyRefusesMemoryPressure() {
        assertEquals(0, VideoSearchWorkerBudget.permits(10_000_000_000L, true))
        assertEquals(0, VideoSearchWorkerBudget.permits(299_999_999L, false))
        assertEquals(0, VideoSearchWorkerBudget.permits(0L, false))
    }

    @Test fun budgetPolicyScalesFromOneToFive() {
        assertEquals(1, VideoSearchWorkerBudget.permits(300_000_000L, false))
        assertEquals(2, VideoSearchWorkerBudget.permits(700_000_000L, false))
        assertEquals(3, VideoSearchWorkerBudget.permits(1_500_000_000L, false))
        assertEquals(4, VideoSearchWorkerBudget.permits(2_500_000_000L, false))
        assertEquals(5, VideoSearchWorkerBudget.permits(3_500_000_000L, false))
    }
}
