package com.prirai.android.nira.videosearch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Stage 5B: bounded admission/execution of search FORM submissions.
 * A positive outcome means only the Gecko content script ACKNOWLEDGED the
 * submission. It never claims result collection, page completion or 200 films.
 *
 * The Gecko host supplies its resource permits, profile/lifetime guard and
 * own-tab worker; this class cannot touch BrowserStore/GeckoView itself.
 */
internal object VideoSearchMultiSiteExecutor {
    suspend fun dispatch(
        queue: VideoSearchDispatchQueue,
        permittedWorkers: () -> Int,
        authorized: () -> Boolean,
        submit: suspend (VideoSearchLaunchSite) -> VideoSearchQueueStatus,
    ): VideoSearchQueueSnapshot = coroutineScope {
        while (true) {
            if (!authorized()) {
                queue.stopPending()
                break
            }
            val snapshot = queue.snapshot()
            if (snapshot.queued == 0) break
            val budget = permittedWorkers().coerceIn(0, 8)
            if (budget == 0) {
                queue.stopPending() // memory pressure; never execute unbounded
                break
            }
            val batch = queue.lease(budget)
            if (batch.isEmpty()) break
            batch.map { site ->
                async {
                    val status = try {
                        if (!authorized()) VideoSearchQueueStatus.STOPPED
                        else submit(site)
                    } catch (cancelled: CancellationException) {
                        queue.resolve(site.id, VideoSearchQueueStatus.STOPPED)
                        throw cancelled
                    } catch (_: Exception) {
                        VideoSearchQueueStatus.FAILED
                    }
                    // SUBMITTED is intentionally NOT DONE: site results have
                    // not yet been extracted or committed to a search run.
                    queue.resolve(site.id, status)
                }
            }.awaitAll()
        }
        queue.snapshot()
    }
}

/** App-level memory backpressure. ALL never implies unlimited Gecko sessions. */
internal object VideoSearchWorkerBudget {
    fun permits(availableBytes: Long, lowMemory: Boolean): Int {
        if (lowMemory || availableBytes < 300_000_000L) return 0
        return when {
            availableBytes < 700_000_000L -> 1
            availableBytes < 1_500_000_000L -> 2
            availableBytes < 2_500_000_000L -> 3
            availableBytes < 3_500_000_000L -> 4
            else -> 5
        }
    }
}
