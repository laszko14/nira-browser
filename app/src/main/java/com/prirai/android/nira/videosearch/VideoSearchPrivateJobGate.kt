package com.prirai.android.nira.videosearch

import kotlinx.coroutines.Job

/**
 * Do not start another private Gecko search during cancellation/finally.
 * Job.isActive becomes false at cancellation request, before cleanup is done.
 * Only a fully completed Job (or no Job) releases the single-worker lease.
 */
internal object VideoSearchPrivateJobGate {
    fun canStart(previous: Job?): Boolean =
        previous == null || previous.isCompleted
}
