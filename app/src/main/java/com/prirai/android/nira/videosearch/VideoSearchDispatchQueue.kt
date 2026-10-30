package com.prirai.android.nira.videosearch

/**
 * Stage 5A: pure preparation + admission control, NOT a worker/executor.
 * Never create a Gecko tab, query the network or persist a run from here.
 *
 * ALL means "all selected sites are eligible"; it is NOT FIVE. A future
 * worker host must pass its currently safe resource permit count each time
 * it asks for jobs, and may pass 0 when under memory/thermal pressure.
 */
internal data class VideoSearchLaunchSite(
    val id: String,
    val scope: VideoSearchScope.Normal,
    val siteUrl: String,
    val displayName: String,
    val configuration: VideoSiteConfiguration?,
    val configurationState: VideoSiteConfigurationState,
    val resultLimit: Int,
    val updatedAtMs: Long,
) {
    init {
        require(id.isNotBlank() && displayName.isNotBlank())
        VideoSearchIntegrity.requirePageUrl(siteUrl)
        VideoSearchResultBudgets.requireSiteLimit(resultLimit)
    }
    override fun toString(): String = "VideoSearchLaunchSite(redacted)"
}

internal enum class VideoSearchQueueStatus {
    QUEUED, LEASED, SUBMITTED, NEEDS_ATTENTION, DONE, FAILED, STOPPED
}

/** A queue never changes the selection snapshot or the configured worker mode. */
internal class VideoSearchDispatchPlan private constructor(
    val scope: VideoSearchScope.Normal,
    val query: String,
    val parallelism: VideoSearchParallelism,
    private val entries: List<VideoSearchLaunchSite>,
) {
    val selectedSites: List<VideoSearchLaunchSite> get() = entries.toList()
    val selectedCount: Int get() = entries.size
    val runnableSites: List<VideoSearchLaunchSite>
        get() = entries.filter { Companion.isRunnable(it) }
    val needsAttention: List<VideoSearchLaunchSite>
        get() = entries.filterNot { Companion.isRunnable(it) }

    fun createQueue() = VideoSearchDispatchQueue(this)

    override fun toString() = "VideoSearchDispatchPlan(redacted)"

    companion object {
        private fun isRunnable(site: VideoSearchLaunchSite): Boolean {
            val config = site.configuration ?: return false
            if (site.configurationState != VideoSiteConfigurationState.CONFIGURED ||
                !VideoSearchBridgeProtocol.validSelector(config.searchInputSelector) ||
                !VideoSearchBridgeProtocol.validSelector(config.resultsCardSelector))
                return false
            if (config.submission !in setOf(
                    VideoSearchSubmission.FORM_SUBMIT, VideoSearchSubmission.CLICK_SUBMIT))
                return false
            if (config.submission == VideoSearchSubmission.CLICK_SUBMIT &&
                !VideoSearchBridgeProtocol.validSelector(config.submitSelector)) return false
            if (config.pagination in setOf(
                    VideoSearchPagination.NEXT_LINK, VideoSearchPagination.LOAD_MORE) &&
                !VideoSearchBridgeProtocol.validSelector(config.nextPageSelector)) return false
            return true
        }

        /**
         * The caller supplies sites/settings from ONE Room transaction. Site
         * order is its persisted drag-and-drop position, never a hash-set order.
         * Selection count and parallelism are independent dimensions.
         */
        fun create(
            scope: VideoSearchScope.Normal,
            phrase: String,
            settings: VideoSearchSettings,
            sites: List<VideoSearchSite>,
        ): VideoSearchDispatchPlan {
            require(settings.scope == scope && phrase.isNotBlank() &&
                phrase.length <= 250 && phrase.none { it.isISOControl() }) {
                "Invalid search plan"
            }
            require(sites.all { it.scope == scope } &&
                sites.map { it.id }.distinct().size == sites.size) {
                "The launch snapshot must contain one profile and unique site IDs"
            }
            val selected = sites.asSequence()
                .filter { it.enabled && it.id in settings.selectedSiteIds }
                .sortedWith(compareBy<VideoSearchSite>({ it.position }, { it.createdAtMs }, { it.id }))
                .map {
                    VideoSearchLaunchSite(
                        id = it.id,
                        scope = scope,
                        siteUrl = it.siteUrl,
                        displayName = it.displayName,
                        configuration = it.configuration,
                        configurationState = it.configurationState,
                        resultLimit = VideoSearchResultBudgets.requireSiteLimit(it.resultLimit),
                        updatedAtMs = it.updatedAtMs,
                    )
                }.toList()
            require(selected.isNotEmpty()) { "No enabled selected sites" }
            return VideoSearchDispatchPlan(scope, phrase, settings.parallelism, selected.toList())
        }
    }
}

internal data class VideoSearchQueueSnapshot(
    val selected: Int,
    val queued: Int,
    val active: Int,
    val submitted: Int,
    val needsAttention: Int,
    val finished: Int,
    val failed: Int,
    val stopped: Int,
    val paused: Boolean,
    val stopping: Boolean,
) {
    init {
        require(selected == queued + active + submitted +
            needsAttention + finished + failed + stopped)
    }
    override fun toString() = "VideoSearchQueueSnapshot(redacted)"
}

/**
 * Synchronized FIFO admission controller; only obtains *leases*.
 * No work is started, no worker is created and no completion is presumed.
 * The host provides its TOTAL current permitted active-worker budget,
 * including existing leases; memory pressure may reduce it to zero.
 */
internal class VideoSearchDispatchQueue internal constructor(
    val plan: VideoSearchDispatchPlan,
) {
    private val gate = Any()
    private val ordered = plan.selectedSites
    private val runnableIds = plan.runnableSites.mapTo(HashSet()) { it.id }
    private val states = LinkedHashMap<String, VideoSearchQueueStatus>().apply {
        ordered.forEach { site ->
            put(site.id, if (site.id in runnableIds)
                VideoSearchQueueStatus.QUEUED else VideoSearchQueueStatus.NEEDS_ATTENTION)
        }
    }
    private var paused = false
    private var stopping = false

    fun lease(permittedActiveWorkers: Int): List<VideoSearchLaunchSite> = synchronized(gate) {
        require(permittedActiveWorkers >= 0)
        if (paused || stopping || permittedActiveWorkers == 0) return@synchronized emptyList()
        val logicalLimit = plan.parallelism.maximumSites ?: ordered.size
        val active = states.values.count { it == VideoSearchQueueStatus.LEASED }
        val available = (minOf(permittedActiveWorkers, logicalLimit) - active).coerceAtLeast(0)
        if (available == 0) return@synchronized emptyList()
        val next = ordered.filter { states[it.id] == VideoSearchQueueStatus.QUEUED }
            .take(available)
        next.forEach { states[it.id] = VideoSearchQueueStatus.LEASED }
        next
    }

    /** Only a leased task can be resolved, and it can be resolved only once. */
    fun resolve(siteId: String, outcome: VideoSearchQueueStatus): Boolean = synchronized(gate) {
        require(outcome in setOf(
            VideoSearchQueueStatus.SUBMITTED, VideoSearchQueueStatus.DONE,
            VideoSearchQueueStatus.FAILED,
            VideoSearchQueueStatus.NEEDS_ATTENTION, VideoSearchQueueStatus.STOPPED))
        if (states[siteId] != VideoSearchQueueStatus.LEASED) return@synchronized false
        states[siteId] = outcome
        true
    }

    fun pause() = synchronized(gate) { paused = true; Unit }
    fun resume() = synchronized(gate) { if (!stopping) paused = false; Unit }

    /**
     * Stops issuing leases and marks only not-yet-dispatched sites STOPPED.
     * Already leased workers must be cancelled/acknowledged by a future host;
     * this class cannot falsely declare that an external job stopped.
     */
    fun stopPending() = synchronized(gate) {
        stopping = true
        states.entries.forEach { if (it.value == VideoSearchQueueStatus.QUEUED)
            it.setValue(VideoSearchQueueStatus.STOPPED) }
        Unit
    }

    fun statusOf(siteId: String): VideoSearchQueueStatus? = synchronized(gate) { states[siteId] }

    fun snapshot(): VideoSearchQueueSnapshot = synchronized(gate) {
        fun count(status: VideoSearchQueueStatus) = states.values.count { it == status }
        VideoSearchQueueSnapshot(
            selected = ordered.size,
            queued = count(VideoSearchQueueStatus.QUEUED),
            active = count(VideoSearchQueueStatus.LEASED),
            submitted = count(VideoSearchQueueStatus.SUBMITTED),
            needsAttention = count(VideoSearchQueueStatus.NEEDS_ATTENTION),
            finished = count(VideoSearchQueueStatus.DONE),
            failed = count(VideoSearchQueueStatus.FAILED),
            stopped = count(VideoSearchQueueStatus.STOPPED),
            paused = paused,
            stopping = stopping,
        )
    }

    override fun toString() = "VideoSearchDispatchQueue(redacted)"
}
