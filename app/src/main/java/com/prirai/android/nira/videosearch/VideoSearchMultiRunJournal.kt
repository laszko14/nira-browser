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
    // Only ACTIVE site metadata is retained. Completed sites release their
    // cache, keeping RAM bounded even with up to 200 selected websites.
    private val savedFilmMetadata =
        HashMap<String, MutableMap<String, VideoSearchFilm>>()
    private var initialized = false
    private var closed = false
    val runResultBudget: Int = VideoSearchResultBudgets.aggregateLimit(
        plan.selectedSites.map { it.resultLimit })
    @Volatile private var remainingResults = runResultBudget

    /** Advisory worker stop; SQLite remains authoritative. */
    fun hasCapacity(): Boolean = remainingResults > 0

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
        require(snapshot == null || snapshot.films.size >= old.uniqueResults) {
            "Regressive terminal checkpoint"
        }
        val wasBudgetStopped = old.issue == VideoSearchSiteIssue.RESULT_BUDGET_REACHED
        val completion = if (wasBudgetStopped) null else when (snapshot?.reason) {
            VideoSearchPaginationStop.LIMIT_REACHED ->
                if (snapshot.films.size >= site.resultLimit)
                    VideoSearchCompletionReason.LIMIT_REACHED else null
            VideoSearchPaginationStop.NO_MORE_RESULTS ->
                if (snapshot.films.isNotEmpty()) VideoSearchCompletionReason.NO_MORE_RESULTS else null
            VideoSearchPaginationStop.NO_RESULTS ->
                if (snapshot.films.isEmpty()) VideoSearchCompletionReason.NO_MORE_RESULTS else null
            else -> null
        }
        val status = when {
            wasBudgetStopped -> VideoSearchSiteState.STOPPED
            completion != null && outcome == VideoSearchQueueStatus.DONE ->
                VideoSearchSiteState.COMPLETED
            outcome == VideoSearchQueueStatus.FAILED -> VideoSearchSiteState.FAILED
            outcome == VideoSearchQueueStatus.NEEDS_ATTENTION ->
                VideoSearchSiteState.NEEDS_ATTENTION
            else -> VideoSearchSiteState.STOPPED
        }
        val task = old.copy(
            status = status,
            uniqueResults = maxOf(old.uniqueResults, snapshot?.films?.size ?: 0),
            visitedPages = maxOf(old.visitedPages, snapshot?.visitedPages ?: 0),
            completionReason = if (status == VideoSearchSiteState.COMPLETED) completion else null,
            issue = when {
                status == VideoSearchSiteState.COMPLETED -> null
                wasBudgetStopped -> VideoSearchSiteIssue.RESULT_BUDGET_REACHED
                status == VideoSearchSiteState.STOPPED && !hasCapacity() ->
                    VideoSearchSiteIssue.RESULT_BUDGET_REACHED
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
        val films = toFilms(site, snapshot, stamp, old.uniqueResults)
        val nextRun = run.copy(updatedAtMs = stamp)
        // Commit in Room first; never update the journal after a failed DB transaction.
        val saved = repository.putJournalCheckpoint(nextRun, task, films)
        tasks[site.id] = task.copy(
            uniqueResults = saved.siteResults,
            status = if (saved.truncated) VideoSearchSiteState.STOPPED else task.status,
            issue = if (saved.truncated) VideoSearchSiteIssue.RESULT_BUDGET_REACHED else task.issue,
            completionReason = if (saved.truncated) null else task.completionReason,
        )
        savedFilmMetadata.remove(site.id)
        remainingResults = (runResultBudget - saved.totalResults).coerceAtLeast(0)
        run = nextRun
    }

    /** Persist verified windows before the next network action. */
    suspend fun progress(
        site: VideoSearchLaunchSite, snapshot: VideoSearchPaginationSnapshot,
    ) = mutex.withLock {
        check(initialized && !closed)
        require(site.scope == plan.scope && site.id in tasks)
        val prior = tasks.getValue(site.id)
        require(snapshot.films.size >= prior.uniqueResults) {
            "Regressive progress checkpoint"
        }
        if (prior.status !in setOf(VideoSearchSiteState.QUEUED,
                VideoSearchSiteState.ACTIVE) || snapshot.films.isEmpty()) return@withLock
        val stamp = maxOf(now(), run.updatedAtMs + 1)
        val next = run.copy(updatedAtMs = stamp)
        val task = prior.copy(
            status = VideoSearchSiteState.ACTIVE,
            uniqueResults = maxOf(prior.uniqueResults, snapshot.films.size),
            visitedPages = maxOf(prior.visitedPages, snapshot.visitedPages),
            issue = null, completionReason = null,
        )
        val delta = toFilms(site, snapshot, stamp, prior.uniqueResults)
        val saved = repository.putJournalCheckpoint(next, task, delta)
        // Update RAM only after Room confirmed the complete transaction.
        val metadata = savedFilmMetadata.getOrPut(site.id) { HashMap() }
        delta.filter { it.originalIndex < saved.siteResults }
            .forEach { metadata[it.filmPageUrl] = it }
        tasks[site.id] = task.copy(
            uniqueResults = saved.siteResults,
            status = if (saved.truncated) VideoSearchSiteState.STOPPED else VideoSearchSiteState.ACTIVE,
            issue = if (saved.truncated) VideoSearchSiteIssue.RESULT_BUDGET_REACHED else null,
        )
        remainingResults = (runResultBudget - saved.totalResults).coerceAtLeast(0)
        run = next
    }

    private fun toFilms(
        site: VideoSearchLaunchSite, snapshot: VideoSearchPaginationSnapshot?, stamp: Long,
        alreadyPersisted: Int,
    ): List<VideoSearchFilm> {
        val previous = savedFilmMetadata[site.id]
        // Only emit new films or metadata improvements to previously saved
        // URLs. This prevents O(N²) Room rewrites without losing subsequent
        // structured title/poster enrichments.
        return snapshot?.films?.take(site.resultLimit)?.mapIndexedNotNull { index, film ->
            val old = previous?.get(film.filmPageUrl)
            val unchanged = old != null &&
                old.originalTitle == film.originalTitle &&
                old.thumbnailUrl == film.thumbnailUrl &&
                old.durationSeconds == film.durationSeconds &&
                old.titleSource == film.titleSource &&
                old.imageSource == film.imageSource
            if (index < alreadyPersisted && unchanged) null else
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
                issue = if (!hasCapacity() &&
                    queue.statusOf(siteId) == VideoSearchQueueStatus.STOPPED)
                    VideoSearchSiteIssue.RESULT_BUDGET_REACHED
                else VideoSearchSiteIssue.OTHER,
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
