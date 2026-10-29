package com.prirai.android.nira.videosearch

import java.util.UUID

/**
 * One aggregated private multi-site search, stored only in the current RAM
 * lifetime. All writes are guarded by the tab registry's same session lock.
 * This is intentionally separate from the normal Room-backed run journal.
 */
internal class VideoSearchPrivateGroupAccess(
    private val registry: VideoSearchPrivateSessionRegistry =
        VideoSearchPrivateSessionHost.registry,
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        /** Memory ceiling across ALL sites of one private grouped search. */
        const val MAX_AGGREGATE_FILMS = 2_000
    }

    /** Private per-run remaining budget, checked only in this session RAM. */
    fun hasCapacity(anchor: VideoSearchPrivateTab, profileId: String, runId: String): Boolean =
        registry.withAuthorizedTab(anchor, profileId) { store, scope ->
            val run = store.runs(scope).singleOrNull {
                it.id == runId && it.status == VideoSearchRunState.RUNNING
            } ?: return@withAuthorizedTab false
            run.siteIds.sumOf { store.films(scope, runId, it).size } < MAX_AGGREGATE_FILMS
        } ?: false

    fun start(
        anchor: VideoSearchPrivateTab, profileId: String,
        plan: VideoSearchPrivateMultiPlan,
    ): String? = registry.withAuthorizedTab(anchor, profileId) { store, scope ->
        if (scope != plan.owner ||
            store.settings(scope).selectedSiteIds.let { selected ->
                plan.sites.any { it.id !in selected }
            } ||
            plan.sites.any { planned ->
                store.sites(scope).none { it == planned && it.enabled }
            }) return@withAuthorizedTab null
        val id = UUID.randomUUID().toString()
        val stamp = now()
        val run = VideoSearchRun(
            id = id, scope = scope, query = plan.query,
            siteIds = plan.sites.map { it.id }, parallelism = VideoSearchParallelism.ONE,
            status = VideoSearchRunState.RUNNING, startedAtMs = stamp,
        )
        store.putRun(run)
        plan.sites.forEach { site ->
            store.putTask(VideoSearchSiteTask(
                runId = id, siteId = site.id, scope = scope, resultLimit = 200,
            ))
        }
        store.trimOldRuns(scope)
        id
    }

    /**
     * Records one site's terminal checkpoint exactly once, including absent
     * workers. A user may deselect or delete a site while it is running: the
     * old result is then rejected rather than silently assigned elsewhere.
     */
    fun checkpoint(
        anchor: VideoSearchPrivateTab, profileId: String,
        runId: String, site: VideoSearchSite,
        snapshot: VideoSearchPaginationSnapshot?,
        issue: VideoSearchSiteIssue = VideoSearchSiteIssue.OTHER,
        terminal: Boolean = true,
    ): Boolean = registry.withAuthorizedTab(anchor, profileId) { store, scope ->
        val run = store.runs(scope).singleOrNull {
            it.id == runId && it.status == VideoSearchRunState.RUNNING
        } ?: return@withAuthorizedTab false
        if (run.scope != scope || site.scope != scope ||
            site.id !in run.siteIds) return@withAuthorizedTab false
        val old = store.tasks(scope, runId).singleOrNull {
            it.siteId == site.id && it.status in setOf(
                VideoSearchSiteState.QUEUED, VideoSearchSiteState.ACTIVE)
        } ?: return@withAuthorizedTab false

        // Invalidated selections cannot write their stale search payload.
        val stillSelected = store.settings(scope).selectedSiteIds.contains(site.id) &&
            store.sites(scope).any { it == site && it.enabled }
        if (snapshot != null && !stillSelected) return@withAuthorizedTab false
        if (snapshot != null && (snapshot.films.size > 200 ||
                snapshot.visitedPages !in 1..12 ||
                snapshot.films.any {
                    !VideoSearchBridgeProtocol.sameOrigin(site.siteUrl, it.filmPageUrl)
                })) return@withAuthorizedTab false

        val consumed = run.siteIds.sumOf { id ->
            store.films(scope, runId, id).size
        }
        val already = store.films(scope, runId, site.id)
        val previousUrls = already.mapTo(HashSet()) { it.filmPageUrl }
        val space = minOf((MAX_AGGREGATE_FILMS - consumed).coerceAtLeast(0),
            (200 - already.size).coerceAtLeast(0))
        val unseen = snapshot?.films?.distinctBy { it.filmPageUrl }
            ?.filterNot { it.filmPageUrl in previousUrls }.orEmpty()
        val truncated = unseen.size > space
        val retained = unseen.take(space)
        val newCount = already.size + retained.size
        val complete = terminal && !truncated && when (snapshot?.reason) {
            VideoSearchPaginationStop.NO_MORE_RESULTS -> true
            VideoSearchPaginationStop.LIMIT_REACHED -> newCount == 200
            else -> false
        }
        val reason = when (snapshot?.reason) {
            VideoSearchPaginationStop.NO_MORE_RESULTS ->
                if (complete) VideoSearchCompletionReason.NO_MORE_RESULTS else null
            VideoSearchPaginationStop.LIMIT_REACHED ->
                if (complete) VideoSearchCompletionReason.LIMIT_REACHED else null
            else -> null
        }
        val status = when {
            complete -> VideoSearchSiteState.COMPLETED
            truncated -> VideoSearchSiteState.STOPPED
            !terminal -> VideoSearchSiteState.ACTIVE
            snapshot == null && newCount == 0 -> VideoSearchSiteState.NEEDS_ATTENTION
            else -> VideoSearchSiteState.STOPPED
        }
        val stamp = maxOf(now(), run.updatedAtMs + 1)
        retained.forEachIndexed { index, film ->
            store.putFilm(VideoSearchFilm(
                id = UUID.nameUUIDFromBytes(
                    (runId + "|" + site.id + "|" + film.filmPageUrl).toByteArray(Charsets.UTF_8)
                ).toString(),
                runId = runId, siteId = site.id, scope = scope,
                siteName = site.displayName, filmPageUrl = film.filmPageUrl,
                originalTitle = film.originalTitle, thumbnailUrl = film.thumbnailUrl,
                durationSeconds = film.durationSeconds, titleSource = film.titleSource,
                imageSource = film.imageSource, originalIndex = already.size + index,
                foundAtMs = stamp,
            ))
        }
        store.putTask(old.copy(
            status = status, uniqueResults = newCount,
            visitedPages = maxOf(old.visitedPages, snapshot?.visitedPages ?: 0),
            issue = if (complete || status == VideoSearchSiteState.ACTIVE) null else issue,
            completionReason = reason,
        ))
        store.putRun(run.copy(updatedAtMs = stamp))
        true
    } ?: false

    /** Finalize all still-queued sites as stopped without inventing results. */
    fun finish(
        anchor: VideoSearchPrivateTab, profileId: String, runId: String,
    ): Boolean = registry.withAuthorizedTab(anchor, profileId) { store, scope ->
        val run = store.runs(scope).singleOrNull { it.id == runId }
            ?: return@withAuthorizedTab false
        if (run.status != VideoSearchRunState.RUNNING) return@withAuthorizedTab false
        store.tasks(scope, runId).filter {
            it.status == VideoSearchSiteState.QUEUED ||
                it.status == VideoSearchSiteState.ACTIVE
        }.forEach { queued ->
            store.putTask(queued.copy(
                status = VideoSearchSiteState.STOPPED,
                issue = VideoSearchSiteIssue.OTHER,
            ))
        }
        val terminal = store.tasks(scope, runId)
        val completed = terminal.size == run.siteIds.size &&
            terminal.all { it.status == VideoSearchSiteState.COMPLETED }
        val stamp = maxOf(now(), run.updatedAtMs + 1)
        store.putRun(run.copy(
            status = if (completed) VideoSearchRunState.COMPLETED
                else VideoSearchRunState.STOPPED,
            updatedAtMs = stamp, completedAtMs = stamp,
        ))
        true
    } ?: false
}
