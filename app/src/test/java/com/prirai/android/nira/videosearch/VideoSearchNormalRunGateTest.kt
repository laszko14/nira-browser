package com.prirai.android.nira.videosearch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

/** LC-03: cancellation must not admit a new normal run during finally. */
class VideoSearchNormalRunGateTest {
    private val owner = VideoSearchScope.Normal("default")

    private fun queue(scope: VideoSearchScope.Normal = owner): VideoSearchDispatchQueue {
        val site = VideoSearchSite(
            id = "one", scope = scope, siteUrl = "https://example.org/",
            displayName = "Example", position = 0, createdAtMs = 1,
            configurationState = VideoSiteConfigurationState.CONFIGURED,
            configuration = VideoSiteConfiguration(
                "input.search", ".item", submission = VideoSearchSubmission.FORM_SUBMIT
            ),
        )
        return VideoSearchDispatchPlan.create(
            scope, "film", VideoSearchSettings(
                scope, selectedSiteIds = setOf(site.id)
            ), listOf(site)
        ).createQueue()
    }

    @Test fun cancelledJobWithSuspendedNonCancellableFinallyStillBlocksStart() = runBlocking {
        val gate = VideoSearchNormalRunGate()
        val q = queue()
        val enteredFinally = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        val work = launch(start = CoroutineStart.LAZY) {
            try {
                CompletableDeferred<Unit>().await()
            } finally {
                withContext(NonCancellable) {
                    enteredFinally.complete(Unit)
                    releaseCleanup.await()
                }
            }
        }
        assertTrue(gate.begin(work, q))
        work.start()
        assertTrue(gate.stop(owner))
        enteredFinally.await()
        assertFalse(work.isActive)
        assertFalse(work.isCompleted)
        assertFalse(gate.canStart())
        assertTrue(gate.owns(work, q))
        assertNotNull(gate.liveQueue(owner))
        assertTrue(q.snapshot().stopping)
        val refused = Job()
        assertFalse(gate.begin(refused, queue()))
        refused.cancel()
        releaseCleanup.complete(Unit)
        work.join()
        assertTrue(work.isCompleted)
        assertTrue(gate.canStart())
    }

    @Test fun oldCompletedGenerationCannotPublishIntoNewRun() {
        val gate = VideoSearchNormalRunGate()
        val old = Job()
        val oldQueue = queue()
        assertTrue(gate.begin(old, oldQueue))
        assertTrue(gate.owns(old, oldQueue))
        old.complete()
        assertTrue(old.isCompleted)
        val fresh = Job()
        val freshQueue = queue()
        assertTrue(gate.begin(fresh, freshQueue))
        assertFalse(gate.owns(old, oldQueue))
        assertFalse(gate.owns(old, freshQueue))
        assertTrue(gate.owns(fresh, freshQueue))
        assertSame(freshQueue, gate.liveQueue(owner))
        fresh.cancel()
    }

    @Test fun cancelledJobCannotBeReusedForNewRun() {
        val gate = VideoSearchNormalRunGate()
        val cancelled = Job().also { it.cancel() }
        assertFalse(gate.begin(cancelled, queue()))
        assertTrue(gate.canStart())
    }

    @Test fun aDifferentNormalProfileCannotStopTheOwnersRun() {
        val gate = VideoSearchNormalRunGate()
        val running = Job()
        val q = queue()
        assertTrue(gate.begin(running, q))
        assertFalse(gate.stop(VideoSearchScope.Normal("work")))
        assertFalse(q.snapshot().stopping)
        assertNull(gate.liveQueue(VideoSearchScope.Normal("work")))
        assertSame(q, gate.liveQueue(owner))
        running.cancel()
    }

    @Test fun stopPreventsNewLeasesAndReturnsNoActiveQueueAfterCompletion() {
        val gate = VideoSearchNormalRunGate()
        val running = Job()
        val q = queue()
        assertTrue(gate.begin(running, q))
        assertTrue(gate.stop(owner))
        assertEquals(1, q.snapshot().stopped)
        assertTrue(running.isCompleted)
        assertTrue(gate.canStart())
        assertNull(gate.liveQueue(owner))
        assertFalse(gate.stop(owner))
    }

    @Test fun completionIsRequiredEvenIfJobIsNotActive() {
        val gate = VideoSearchNormalRunGate()
        val waiting = Job()
        val unfinishedChild = Job(waiting)
        val q = queue()
        assertTrue(gate.begin(waiting, q))
        waiting.cancel()
        assertFalse(waiting.isActive)
        assertFalse(waiting.isCompleted)
        assertFalse(gate.canStart())
        assertFalse(gate.begin(Job(), queue()))
        unfinishedChild.complete()
        assertTrue(waiting.isCompleted)
        assertTrue(gate.canStart())
    }

    @Test fun onlyJobAndQueueIdentityCanPublishRunOutcome() {
        val gate = VideoSearchNormalRunGate()
        val job = Job()
        val q = queue()
        assertTrue(gate.begin(job, q))
        assertFalse(gate.owns(Job(), q))
        assertFalse(gate.owns(job, queue()))
        assertTrue(gate.owns(job, q))
        job.cancel()
    }
}
