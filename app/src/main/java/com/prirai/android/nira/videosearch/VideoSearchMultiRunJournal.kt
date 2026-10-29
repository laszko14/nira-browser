package com.prirai.android.nira.videosearch

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * One normal-profile run journal; every site checkpoint is one Room transaction.
 * Only pagination LIMIT_REACHED/NO_MORE_RESULTS permit completed tasks.
 * The mutex prevents concurrent worker checkpoints from clobbering counters.
 */
internal class VideoSearchMultiRunJournal(
    private val repository: VideoSearchRepository,
    val plan: VideoSearchDispatchPlan,
    private val id: String = UUID.randomUUID().toString(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val initialTime = now()
    private var run = VideoSearchRun(
        id = id, scope = plan.scope, query = plan.query,
        siteIds = plan.selectedSites.map { it.id },
        parallelism = plan.parallelism, status = VideoSearchRunState.RUNNING,
        startedAtMs = initialTime,
    )
    private val tasks = LinkedHashMap<String, VideoSearchSiteTask>().apply {
        val ready = plan.runnableSites.mapTo(HashSet()) { it.id }
        plan.selectedSites.forEach { site ->
            put(site.id, VideoSearchSiteTask(
                runId = id, siteId = site.id, scope = plan.scope,
                resultLimit = site.resultLimit,
                status = if (site.id in ready) VideoSearchSiteState.QUEUED
                    else VideoSearchSiteState.NEEDS_ATTENTION,
            ))
        }
    }
    private var initialized = false
    private var closed = false

    val runId: String get() = id

    suspend fun initialize() = mutex.withLock {
        check(!initialized && !closed)
        repository.putSnapshot(run, tasks.values.toList(), emptyList())
        initialized = true
    }

    /** A blank response or form ACK cannot make a site COMPLETED. */
    suspend fun record(
        site: VideoSearchLaunchSite,
        outcome: VideoSearchQueueStatus,
        snapshot: VideoSearchPaginationSnapshot? = null,
    ) = mutex.withLock {
        check(initialized && !closed)
        val old = tasks[site.id] ?: throw IllegalArgumentException("Unknown site")
        require(site.scope == plan.scope)
        require(outcome !in setOf(VideoSearchQueueStatus.LEASED, VideoSearchQueueStatus.QUEUED))
        val completion = when (snapshot?.reason) {
            VideoSearchPaginationStop.LIMIT_REACHED ->
                if (snapshot.films.isNotEmpty()) VideoSearchCompletionReason.LIMIT_REACHED else null
            VideoSearchPaginationStop.NO_MORE_RESULTS ->
                if (snapshot.films.isNotEmpty()) VideoSearchCompletionReason.NO_MORE_RESULTS else null
            else -> null
        }
        val status = when {
            completion != null && outcome == VideoSearchQueueStatus.DONE ->
                VideoSearchSiteState.COMPLETED
            outcome == VideoSearchQueueStatus.FAILED -> VideoSearchSiteState.FAILED
            outcome == VideoSearchQueueStatus.NEEDS_ATTENTION ->
                VideoSearchSiteState.NEEDS_ATTENTION
            else -> VideoSearchSiteState.STOPPED
        }
        val task = old.copy(
            status = status,
            uniqueResults = snapshot?.films?.size ?: 0,
            visitedPages = snapshot?.visitedPages ?: 0,
            completionReason = if (status == VideoSearchSiteState.COMPLETED) completion else null,
            issue = when {
                status == VideoSearchSiteState.COMPLETED -> null
                snapshot?.reason == VideoSearchPaginationStop.CONFIGURATION_CHANGED ->
                    VideoSearchSiteIssue.CONFIGURATION_CHANGED
                snapshot?.reason == VideoSearchPaginationStop.RESOURCE_LIMIT ->
                    VideoSearchSiteIssue.TIMEOUT
                outcome == VideoSearchQueueStatus.NEEDS_ATTENTION ->
                    VideoSearchSiteIssue.OTHER
                outcome == VideoSearchQueueStatus.FAILED ->
                    VideoSearchSiteIssue.NETWORK_ERROR
                else -> VideoSearchSiteIssue.OTHER
            },
        )
        val stamp = maxOf(now(), run.updatedAtMs + 1)
        val films = snapshot?.films?.take(site.resultLimit)?.mapIndexed { index, film ->
            VideoSearchFilm(
                id = UUID.nameUUIDFromBytes(
                    "$id|${site.id}|${film.filmPageUrl}".toByteArray(Charsets.UTF_8)
                ).toString(),
                runId = id, siteId = site.id, scope = plan.scope,
                siteName = site.displayName, filmPageUrl = film.filmPageUrl,
                originalTitle = film.originalTitle, thumbnailUrl = film.thumbnailUrl,
                durationSeconds = film.durationSeconds, titleSource = film.titleSource,
                imageSource = film.imageSource, originalIndex = index, foundAtMs = stamp,
            )
        } ?: emptyList()
        val nextRun = run.copy(updatedAtMs = stamp)
        // Commit in Room first; never update the journal after a failed DB transaction.
        repository.putSnapshot(nextRun, listOf(task), films)
        tasks[site.id] = task
        run = nextRun
    }

    suspend fun finish(queue: VideoSearchDispatchQueue) = mutex.withLock {
        if (!initialized || closed) return@withLock
        val terminal = tasks.mapValues { (siteId, task) ->
            if (task.status !in setOf(VideoSearchSiteState.QUEUED, VideoSearchSiteState.ACTIVE))
                task
            else task.copy(
                status = when (queue.statusOf(siteId)) {
                    VideoSearchQueueStatus.NEEDS_ATTENTION -> VideoSearchSiteState.NEEDS_ATTENTION
                    VideoSearchQueueStatus.FAILED -> VideoSearchSiteState.FAILED
                    else -> VideoSearchSiteState.STOPPED
                },
                issue = VideoSearchSiteIssue.OTHER,
            )
        }
        val complete = terminal.values.all { it.status == VideoSearchSiteState.COMPLETED }
        val failed = terminal.values.all { it.status == VideoSearchSiteState.FAILED }
        val stamp = maxOf(now(), run.updatedAtMs + 1)
        val final = run.copy(
            status = when {
                complete -> VideoSearchRunState.COMPLETED
                failed -> VideoSearchRunState.FAILED
                else -> VideoSearchRunState.STOPPED
            },
            updatedAtMs = stamp, completedAtMs = stamp,
        )
        repository.putSnapshot(final, terminal.values.toList(), emptyList())
        tasks.clear()
        tasks.putAll(terminal)
        run = final
        closed = true
    }
}
