package com.prirai.android.nira.videosearch

import kotlinx.coroutines.Job

/**
 * Exactly one normal multi-site run at a time. Cancellation is only a request:
 * a cancelled Job keeps its slot until finally/NonCancellable cleanup ends
 * and Job.isCompleted is true. Never use isActive for admission here.
 *
 * Browser controller access is main-thread confined. Retain the completed
 * generation until the next begin, so an older callback can be checked by
 * reference identity and cannot publish into a replacement run.
 */
internal class VideoSearchNormalRunGate {
    private data class Run(val job: Job, val queue: VideoSearchDispatchQueue)
    private var current: Run? = null

    fun canStart(): Boolean = current?.job?.isCompleted != false

    fun begin(job: Job, queue: VideoSearchDispatchQueue): Boolean {
        if (!canStart() || job.isCompleted || job.isCancelled) return false
        current = Run(job, queue)
        return true
    }

    fun owns(job: Job?, queue: VideoSearchDispatchQueue): Boolean =
        current?.let { it.job === job && it.queue === queue } == true

    fun liveQueue(scope: VideoSearchScope.Normal): VideoSearchDispatchQueue? =
        current?.takeIf {
            !it.job.isCompleted && it.queue.plan.scope == scope
        }?.queue

    fun stop(scope: VideoSearchScope.Normal): Boolean {
        val active = current ?: return false
        if (active.job.isCompleted || active.queue.plan.scope != scope) return false
        active.queue.stopPending()
        active.job.cancel()
        return true
    }
}
