package com.prirai.android.nira.videosearch

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** JVM coroutine state contracts. These tests are authored, not yet run. */
class VideoSearchPrivateJobGateTest {
    @Test fun nullJobPermitsLaunch() {
        assertTrue(VideoSearchPrivateJobGate.canStart(null))
    }

    @Test fun activeJobBlocksSecondSingleOrGroupedLaunch() {
        val running = Job()
        assertFalse(VideoSearchPrivateJobGate.canStart(running))
        running.complete()
    }

    @Test fun completedJobPermitsNextLaunch() {
        val finished = Job().apply { complete() }
        assertTrue(VideoSearchPrivateJobGate.canStart(finished))
    }

    @Test fun cancelledAndCompletedJobPermitsNextLaunch() {
        val finished = Job().apply { cancel() }
        assertTrue(finished.isCompleted)
        assertTrue(VideoSearchPrivateJobGate.canStart(finished))
    }

    @Test fun cancellationWithSuspendedFinallyStillBlocksNewLaunch() = runBlocking {
        val parent = Job()
        val release = CompletableDeferred<Unit>()
        launch(parent + Dispatchers.Unconfined) {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { release.await() }
            }
        }
        parent.cancel()
        assertFalse(parent.isCompleted)
        assertFalse(VideoSearchPrivateJobGate.canStart(parent))
        release.complete(Unit)
        parent.join()
        assertTrue(VideoSearchPrivateJobGate.canStart(parent))
    }
}
