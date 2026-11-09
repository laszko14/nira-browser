package com.prirai.android.nira.videosearch

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Stage 4: local, opt-in, read-only diagnosis of saved-to-presented results. */
internal data class VideoSearchResultsDiagnosticFrame(
    val snapshot: VideoSearchSavedResults,
    val projected: VideoSearchResultsDisplay,
    val selectedSiteId: String?,
    val sort: VideoSearchSort,
    val byWebsite: Boolean,
    val mergeByName: Boolean,
    val mergeByDuration: Boolean,
    val mergeSmart: Boolean,
    val mergedEntries: List<VideoSearchMergedFilm>,
    val allEntries: Int,
    val pagedFilms: List<VideoSearchFilm>,
    val eligibleKeys: List<String>,
    val expandedSites: Set<String>?,
    val pageLimit: Int,
    val view: VideoSearchView,
    val selecting: Boolean,
    val selectedCount: Int,
) {
    override fun toString(): String = "VideoSearchResultsDiagnosticFrame(redacted)"
}

internal enum class VideoSearchDiagnosticSiteVerdict {
    OK, PROBLEM, UNCERTAIN, INTERRUPTED
}
internal data class VideoSearchDiagnosticSiteAssessment(
    val siteId: String,
    val verdict: VideoSearchDiagnosticSiteVerdict,
    val reason: String,
    val stored: Int,
    val projected: Int,
    val renderEligible: Int,
    val taskClaimed: Int?,
    val pages: Int?,
) {
    override fun toString(): String = "VideoSearchDiagnosticSiteAssessment(redacted)"
}

internal object VideoSearchResultsDiagnosticAnalyzer {
    fun assess(frame: VideoSearchResultsDiagnosticFrame): List<VideoSearchDiagnosticSiteAssessment> {
        val run = frame.snapshot.run
        require(run.scope is VideoSearchScope.Normal)
        require(frame.selectedSiteId == null || frame.selectedSiteId in run.siteIds)
        require(frame.snapshot.tasks.all {
            it.runId == run.id && it.scope == run.scope && it.siteId in run.siteIds
        })
        require(frame.snapshot.films.all {
            it.runId == run.id && it.scope == run.scope && it.siteId in run.siteIds
        })
        require(frame.projected.visibleFilms.all { it.runId == run.id &&
            it.scope == run.scope && it.siteId in run.siteIds })
        require(frame.pagedFilms.all { it.runId == run.id &&
            it.scope == run.scope && it.siteId in run.siteIds })
        val tasks = frame.snapshot.tasks.associateBy { it.siteId }
        val stored = frame.snapshot.films.distinctBy { it.siteId to it.filmPageUrl }
            .groupingBy { it.siteId }.eachCount()
        val projected = frame.projected.visibleFilms.groupingBy { it.siteId }.eachCount()
        val eligible = frame.eligibleKeys.filter { it.startsWith("film:") }
            .groupingBy { it.removePrefix("film:").substringBefore(':') }.eachCount()
        return run.siteIds.map { siteId ->
            val task = tasks[siteId]
            val saved = stored[siteId] ?: 0
            val projection = projected[siteId] ?: 0
            val renderCount = eligible[siteId] ?: 0
            val expected = if (frame.selectedSiteId == null ||
                frame.selectedSiteId == siteId) saved else 0
            val (verdict, reason) = when {
                task == null -> VideoSearchDiagnosticSiteVerdict.PROBLEM to "MISSING_SITE_TASK"
                task.uniqueResults != saved ->
                    VideoSearchDiagnosticSiteVerdict.PROBLEM to "TASK_ROOM_COUNT_MISMATCH"
                projection != expected ->
                    VideoSearchDiagnosticSiteVerdict.PROBLEM to "ROOM_PROJECTION_DIVERGENCE"
                renderCount > projection ->
                    VideoSearchDiagnosticSiteVerdict.PROBLEM to "RENDER_EXCEEDS_PROJECTION"
                task.status in setOf(VideoSearchSiteState.FAILED,
                    VideoSearchSiteState.STOPPED, VideoSearchSiteState.NEEDS_ATTENTION) ->
                    VideoSearchDiagnosticSiteVerdict.INTERRUPTED to ("SITE_" + task.status.name)
                task.status in setOf(VideoSearchSiteState.QUEUED,
                    VideoSearchSiteState.ACTIVE, VideoSearchSiteState.PAUSED) ->
                    VideoSearchDiagnosticSiteVerdict.UNCERTAIN to ("SITE_" + task.status.name)
                saved == 0 ->
                    VideoSearchDiagnosticSiteVerdict.UNCERTAIN to "ZERO_REQUIRES_PAGE_EVIDENCE"
                else -> VideoSearchDiagnosticSiteVerdict.OK to "LOCAL_SNAPSHOT_CONSISTENT"
            }
            VideoSearchDiagnosticSiteAssessment(siteId, verdict, reason, saved,
                projection, renderCount, task?.uniqueResults, task?.visitedPages)
        }
    }

    fun events(frame: VideoSearchResultsDiagnosticFrame): List<VideoSearchDiagnosticEvent> {
        val run = frame.snapshot.run
        val assessments = assess(frame)
        val unique = frame.snapshot.films.distinctBy { it.siteId to it.filmPageUrl }.size
        val projectedCount = frame.projected.visibleFilms.size
        val isMerging = !frame.byWebsite &&
            (frame.mergeSmart || frame.mergeByName || frame.mergeByDuration)
        val expectedEntries = if (isMerging) frame.mergedEntries.size else projectedCount
        val pagedExpected = minOf(expectedEntries, frame.pageLimit.coerceAtLeast(0))
        val rows = frame.eligibleKeys.count { it.startsWith("film:") }
        val consistent = frame.projected.totalFilms == unique &&
            frame.allEntries == expectedEntries &&
            frame.pagedFilms.size == pagedExpected &&
            rows <= frame.pagedFilms.size
        val reason = when {
            frame.projected.totalFilms != unique -> "STORAGE_TO_PROJECTED_DIVERGENCE"
            frame.allEntries != expectedEntries -> "PROJECTED_TO_MERGE_DIVERGENCE"
            frame.pagedFilms.size != pagedExpected -> "MERGE_TO_PAGE_DIVERGENCE"
            rows > frame.pagedFilms.size -> "PAGE_TO_ELIGIBLE_DIVERGENCE"
            frame.selectedSiteId != null && projectedCount < unique -> "USER_SITE_FILTER"
            isMerging && expectedEntries < projectedCount -> "USER_MERGE_COLLAPSE"
            frame.pagedFilms.size < expectedEntries -> "USER_PAGE_LIMIT"
            rows < frame.pagedFilms.size -> "USER_GROUP_COLLAPSE"
            else -> "LOCAL_DISPLAY_COUNTS_MATCH"
        }
        val detail = "sort=" + frame.sort.name + ";view=" + frame.view.name +
            ";siteFilter=" + (if (frame.selectedSiteId == null) "ALL" else "SITE") +
            ";grouped=" + frame.byWebsite +
            ";mergeName=" + frame.mergeByName + ";mergeDuration=" + frame.mergeByDuration +
            ";mergeSmart=" + frame.mergeSmart + ";mergeActive=" + isMerging +
            ";expanded=" + (frame.expandedSites?.size?.toString() ?: "ALL") +
            ";selecting=" + frame.selecting + ";selected=" + frame.selectedCount +
            ";pageLimit=" + frame.pageLimit + ";projected=" + projectedCount +
            ";entries=" + expectedEntries + ";paged=" + frame.pagedFilms.size +
            ";eligible=" + rows + ";notViewport=true"
        val summary = VideoSearchDiagnosticEvent(
            stage = VideoSearchDiagnosticStage.DISPLAY,
            claim = VideoSearchDiagnosticClaim.INFERENCE,
            decision = if (consistent) VideoSearchDiagnosticDecision.SEEN
                else VideoSearchDiagnosticDecision.UNCERTAIN,
            reason = reason,
            trace = VideoSearchDiagnosticTrace(runId = run.id),
            counts = mapOf(VideoSearchDiagnosticCount.SAVED_FILMS to unique,
                VideoSearchDiagnosticCount.UNIQUE_FILMS to frame.projected.totalFilms,
                VideoSearchDiagnosticCount.CANDIDATES to expectedEntries,
                VideoSearchDiagnosticCount.DISPLAYED_FILMS to rows),
            fields = mapOf(VideoSearchDiagnosticField.DETAIL to detail),
        )
        val perSite = assessments.map { a ->
            val task = frame.snapshot.tasks.firstOrNull { it.siteId == a.siteId }
            val decision = when (a.verdict) {
                VideoSearchDiagnosticSiteVerdict.OK -> VideoSearchDiagnosticDecision.SEEN
                VideoSearchDiagnosticSiteVerdict.PROBLEM -> VideoSearchDiagnosticDecision.FAILED
                VideoSearchDiagnosticSiteVerdict.UNCERTAIN -> VideoSearchDiagnosticDecision.UNCERTAIN
                VideoSearchDiagnosticSiteVerdict.INTERRUPTED ->
                    VideoSearchDiagnosticDecision.INTERRUPTED
            }
            VideoSearchDiagnosticEvent(
                stage = VideoSearchDiagnosticStage.STORAGE,
                claim = VideoSearchDiagnosticClaim.INFERENCE,
                decision = decision,
                reason = a.reason,
                trace = VideoSearchDiagnosticTrace(runId = run.id, siteId = a.siteId),
                counts = mapOf(VideoSearchDiagnosticCount.SAVED_FILMS to a.stored,
                    VideoSearchDiagnosticCount.UNIQUE_FILMS to (a.taskClaimed ?: 0),
                    VideoSearchDiagnosticCount.DISPLAYED_FILMS to a.renderEligible,
                    VideoSearchDiagnosticCount.PAGES to (a.pages ?: 0)),
                fields = mapOf(VideoSearchDiagnosticField.DETAIL to
                    ("siteState=" + (task?.status?.name ?: "MISSING") +
                    ";completion=" + (task?.completionReason?.name ?: "UNKNOWN") +
                    ";issue=" + (task?.issue?.name ?: "NONE") +
                    ";verdict=" + a.verdict.name +
                    ";projected=" + a.projected +
                    ";filter=" + (if (frame.selectedSiteId == null) "ALL"
                        else if (frame.selectedSiteId == a.siteId) "SELECTED" else "EXCLUDED") +
                    ";localOnly=true")),
            )
        }
        // Store exact counters as observation independently of the inferred
        // first-divergence reason. The report must never equate an inference
        // with a verified page/website observation.
        val counterObservation = summary.copy(
            claim = VideoSearchDiagnosticClaim.OBSERVATION,
            reason = "RESULTS_SCREEN_COUNTS",
        )
        return listOf(counterObservation, summary) + perSite
    }
}


/** Stage-6 private RAM -> RecyclerView adapter totals; not website/pixel proof. */
internal object VideoSearchPrivateResultsDiagnosticAnalyzer {
    fun events(
        snapshot: VideoSearchPrivateRunSnapshot,
        displayed: List<VideoSearchFilm>,
    ): List<VideoSearchDiagnosticEvent> {
        val run = snapshot.run
        require(run.scope is VideoSearchScope.Private)
        require(snapshot.tasks.all {
            it.runId == run.id && it.scope == run.scope && it.siteId in run.siteIds
        })
        require(snapshot.tasks.map { it.siteId }.distinct().size == snapshot.tasks.size)
        require(snapshot.films.all {
            it.runId == run.id && it.scope == run.scope && it.siteId in run.siteIds
        })
        require(displayed.size <= snapshot.films.size &&
            displayed == snapshot.films.take(displayed.size))
        val limited = snapshot.films.size >= VideoSearchPrivateGroupAccess.MAX_AGGREGATE_FILMS
        val unique = snapshot.films.distinctBy { it.siteId to it.filmPageUrl }
        val stored = unique.groupingBy { it.siteId }.eachCount()
        val shown = displayed.groupingBy { it.siteId }.eachCount()
        val tasks = snapshot.tasks.associateBy { it.siteId }
        val overview = VideoSearchDiagnosticEvent(
            stage = VideoSearchDiagnosticStage.DISPLAY,
            claim = VideoSearchDiagnosticClaim.OBSERVATION,
            decision = VideoSearchDiagnosticDecision.SEEN,
            reason = "PRIVATE_RESULTS_SCREEN_COUNTS",
            trace = VideoSearchDiagnosticTrace(runId = run.id),
            counts = mapOf(
                VideoSearchDiagnosticCount.SAVED_FILMS to unique.size,
                VideoSearchDiagnosticCount.DISPLAYED_FILMS to displayed.size,
                VideoSearchDiagnosticCount.CANDIDATES to snapshot.films.size,
            ),
            fields = mapOf(VideoSearchDiagnosticField.DETAIL to
                ("sites=" + run.siteIds.size +
                    ";pageLimited=" + (displayed.size < snapshot.films.size) +
                    ";aggregateTruncated=" + limited +
                    ";adapterRowsNotPixels=true;privateRamOnly=true")),
        )
        val perSite = run.siteIds.map { siteId ->
            val task = tasks[siteId]
            val saved = stored[siteId] ?: 0
            val visible = shown[siteId] ?: 0
            val (decision, reason) = when {
                task == null -> VideoSearchDiagnosticDecision.FAILED to
                    "PRIVATE_MISSING_SITE_TASK"
                limited -> VideoSearchDiagnosticDecision.UNCERTAIN to
                    "PRIVATE_AGGREGATE_LIMIT_UNKNOWN"
                task.uniqueResults != saved -> VideoSearchDiagnosticDecision.FAILED to
                    "PRIVATE_TASK_RAM_COUNT_MISMATCH"
                task.status in setOf(VideoSearchSiteState.FAILED,
                    VideoSearchSiteState.STOPPED, VideoSearchSiteState.NEEDS_ATTENTION) ->
                    VideoSearchDiagnosticDecision.INTERRUPTED to ("PRIVATE_SITE_" + task.status.name)
                task.status != VideoSearchSiteState.COMPLETED ||
                    run.status != VideoSearchRunState.COMPLETED ->
                    VideoSearchDiagnosticDecision.UNCERTAIN to "PRIVATE_RUN_IN_PROGRESS"
                saved == 0 -> VideoSearchDiagnosticDecision.UNCERTAIN to
                    "PRIVATE_ZERO_NEEDS_PAGE_EVIDENCE"
                visible < saved -> VideoSearchDiagnosticDecision.SEEN to
                    "PRIVATE_PAGE_LIMIT_NOT_LOSS"
                else -> VideoSearchDiagnosticDecision.SEEN to
                    "PRIVATE_LOCAL_RAM_COUNTS_MATCH"
            }
            VideoSearchDiagnosticEvent(
                stage = VideoSearchDiagnosticStage.STORAGE,
                claim = VideoSearchDiagnosticClaim.INFERENCE,
                decision = decision,
                reason = reason,
                trace = VideoSearchDiagnosticTrace(runId = run.id, siteId = siteId),
                counts = mapOf(
                    VideoSearchDiagnosticCount.SAVED_FILMS to saved,
                    VideoSearchDiagnosticCount.DISPLAYED_FILMS to visible,
                    VideoSearchDiagnosticCount.UNIQUE_FILMS to (task?.uniqueResults ?: 0),
                    VideoSearchDiagnosticCount.PAGES to (task?.visitedPages ?: 0),
                ),
                fields = mapOf(VideoSearchDiagnosticField.DETAIL to
                    ("taskState=" + (task?.status?.name ?: "MISSING") +
                        ";aggregateTruncated=" + limited +
                        ";privateRamOnly=true;notWebsiteProof=true")),
            )
        }
        return listOf(overview) + perSite
    }
}

/** Process-owned, opt-in recorder reference. Never stores URLs/films in RAM. */
internal object VideoSearchDiagnosticResultsHost {
    private val active = AtomicReference<VideoSearchDiagnosticRecorder?>(null)
    private val sessionState = MutableStateFlow<String?>(null)
    val recordingSession: StateFlow<String?> = sessionState
    private val lock = Any()
    private var previousFrame: String? = null
    private var previousViewport: String? = null
    private var previousPrivateFrame: String? = null

    fun attach(recorder: VideoSearchDiagnosticRecorder) = synchronized(lock) {
        active.set(recorder)
        sessionState.value = recorder.sessionId
        previousFrame = null
        previousViewport = null
        previousPrivateFrame = null
    }
    fun detach(recorder: VideoSearchDiagnosticRecorder) = synchronized(lock) {
        if (active.compareAndSet(recorder, null)) {
            sessionState.value = null
            previousFrame = null
            previousViewport = null
            previousPrivateFrame = null
        }
    }
    fun isRecording(owner: VideoSearchScope): Boolean =
        active.get()?.let { it.owner == owner && it.state().active } == true

    fun observe(frame: VideoSearchResultsDiagnosticFrame) {
        val recorder = active.get() ?: return
        if (recorder.owner != frame.snapshot.run.scope || !recorder.state().active) return
        val events = VideoSearchResultsDiagnosticAnalyzer.events(frame)
        // Dedupe consecutive unchanged frames; a filter toggle back is captured.
        val fingerprint = events.joinToString("|") { e ->
            e.reason + e.trace.runId + e.trace.siteId +
                e.counts.toString() + e.fields.toString()
        }
        synchronized(lock) {
            if (active.get() !== recorder || fingerprint == previousFrame) return
            previousFrame = fingerprint
            events.forEach { recorder.record(it) }
        }
    }

    /** Recorders are process-local. The token prevents late jobs from
     * writing into a NEW recording of the same profile/epoch. */
    fun recordingToken(owner: VideoSearchScope): String? =
        active.get()?.takeIf { it.owner == owner && it.state().active }?.sessionId

    /** Computation is done off-main by the caller. Call only after a fresh
     * private tab/profile/epoch check, never using a stale cached permission. */
    fun submitPrivateEvents(
        owner: VideoSearchScope.Private,
        expectedSessionId: String,
        events: List<VideoSearchDiagnosticEvent>,
    ) = synchronized(lock) {
        val recorder = active.get() ?: return@synchronized
        if (recorder.owner != owner || recorder.sessionId != expectedSessionId ||
            !recorder.state().active) return@synchronized
        val fingerprint = events.joinToString("|") { event ->
            event.reason + event.trace.runId + event.trace.siteId +
                event.counts.toString() + event.fields.toString()
        }
        if (previousPrivateFrame == fingerprint) return@synchronized
        previousPrivateFrame = fingerprint
        events.forEach { recorder.record(it) }
    }

    /** Synchronous helper for synthetic tests and non-UI callers only. */
    fun observePrivate(
        snapshot: VideoSearchPrivateRunSnapshot,
        displayed: List<VideoSearchFilm>,
    ) {
        val owner = snapshot.run.scope as? VideoSearchScope.Private ?: return
        val token = recordingToken(owner) ?: return
        val events = VideoSearchPrivateResultsDiagnosticAnalyzer.events(snapshot, displayed)
        submitPrivateEvents(owner, token, events)
    }

    fun viewport(
        owner: VideoSearchScope, runId: String, view: VideoSearchView,
        firstItem: Int, composedFilmRows: Int,
    ) {
        val recorder = active.get() ?: return
        if (recorder.owner != owner || !recorder.state().active ||
            firstItem < 0 || composedFilmRows < 0) return
        val fingerprint = runId + ":" + view.name + ":" + firstItem + ":" + composedFilmRows
        synchronized(lock) {
            if (active.get() !== recorder || previousViewport == fingerprint) return
            previousViewport = fingerprint
            recorder.record(VideoSearchDiagnosticEvent(
                stage = VideoSearchDiagnosticStage.DISPLAY,
                claim = VideoSearchDiagnosticClaim.OBSERVATION,
                decision = VideoSearchDiagnosticDecision.SEEN,
                reason = "VIEWPORT_COMPOSED_FILM_ROWS",
                trace = VideoSearchDiagnosticTrace(runId = runId),
                counts = mapOf(VideoSearchDiagnosticCount.DISPLAYED_FILMS to composedFilmRows),
                fields = mapOf(VideoSearchDiagnosticField.DETAIL to
                    ("view=" + view.name + ";firstItem=" + firstItem +
                    ";virtualized=true;notPixelProof=true")),
            ))
        }
    }
    fun loadFailure(owner: VideoSearchScope, runId: String?) {
        val recorder = active.get() ?: return
        if (recorder.owner != owner || !recorder.state().active) return
        recorder.record(VideoSearchDiagnosticEvent(
            stage = VideoSearchDiagnosticStage.DISPLAY,
            claim = VideoSearchDiagnosticClaim.OBSERVATION,
            decision = VideoSearchDiagnosticDecision.FAILED,
            reason = "RESULTS_SNAPSHOT_LOAD_FAILED",
            trace = VideoSearchDiagnosticTrace(runId = runId),
        ))
    }
}
