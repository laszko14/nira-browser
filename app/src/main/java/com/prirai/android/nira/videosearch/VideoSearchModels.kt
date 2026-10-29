package com.prirai.android.nira.videosearch

/** Film PAGE links; these models never represent downloadable media streams. */
internal const val DEFAULT_VIDEO_SEARCH_SITE_LIMIT = 200

internal enum class VideoSiteConfigurationState {
    NEEDS_CONFIGURATION, AUTO_CONFIGURING, CONFIGURED, REPAIR_REQUIRED
}

internal enum class VideoSearchSubmission { ENTER, CLICK_SUBMIT, FORM_SUBMIT }
internal enum class VideoSearchPagination { NONE, NEXT_LINK, LOAD_MORE, INFINITE_SCROLL }

/**
 * Site-specific selectors, chosen automatically or by user selection on the page.
 * Selectors are DATA, never executable JavaScript supplied by the site.
 * Verification that the actual search and results work belongs to stage 3.
 */
internal data class VideoSiteConfiguration(
    val searchInputSelector: String,
    val resultsCardSelector: String,
    val submission: VideoSearchSubmission = VideoSearchSubmission.ENTER,
    val submitSelector: String? = null,
    val titleSelector: String? = null,
    val linkSelector: String? = null,
    val thumbnailSelector: String? = null,
    val durationSelector: String? = null,
    val pagination: VideoSearchPagination = VideoSearchPagination.NONE,
    val nextPageSelector: String? = null,
) {
    init {
        require(searchInputSelector.isNotBlank() && resultsCardSelector.isNotBlank())
        require(submission != VideoSearchSubmission.CLICK_SUBMIT || !submitSelector.isNullOrBlank())
        require(pagination !in setOf(VideoSearchPagination.NEXT_LINK, VideoSearchPagination.LOAD_MORE) ||
            !nextPageSelector.isNullOrBlank())
    }

    override fun toString(): String = "VideoSiteConfiguration(redacted)"
}

/** A site is saved BEFORE configuring it; failure never deletes the site. */
internal data class VideoSearchSite(
    val id: String,
    val scope: VideoSearchScope,
    val siteUrl: String,
    val displayName: String,
    val position: Int,
    val enabled: Boolean = true,
    val resultLimit: Int = DEFAULT_VIDEO_SEARCH_SITE_LIMIT,
    val configurationState: VideoSiteConfigurationState = VideoSiteConfigurationState.NEEDS_CONFIGURATION,
    val configuration: VideoSiteConfiguration? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long = createdAtMs,
) {
    init {
        require(id.isNotBlank() && siteUrl.isNotBlank() && displayName.isNotBlank())
        require(position >= 0 && resultLimit > 0)
        require(configurationState != VideoSiteConfigurationState.CONFIGURED || configuration != null)
    }

    override fun toString(): String = "VideoSearchSite(redacted)"
}

/** ALL has no fixed limit of five; resource backpressure is a separate concern. */
internal enum class VideoSearchParallelism(val maximumSites: Int?) {
    ONE(1), TWO(2), THREE(3), FOUR(4), FIVE(5), ALL(null)
}

internal enum class VideoSearchView { TILES, LIST }
internal enum class VideoSearchSort {
    ORIGINAL, TITLE_A_Z, TITLE_Z_A, DURATION_SHORTEST, DURATION_LONGEST
}

/** Separate per-profile preferences; selection is NOT the parallelism limit. */
internal data class VideoSearchSettings(
    val scope: VideoSearchScope,
    val selectedSiteIds: Set<String> = emptySet(),
    val parallelism: VideoSearchParallelism = VideoSearchParallelism.ONE,
    val defaultTabGroupId: String? = null,
    val view: VideoSearchView = VideoSearchView.TILES,
    val sortBySiteId: Map<String, VideoSearchSort> = emptyMap(),
)

/** Completion does not imply 200 results; do not calculate percent from a limit. */
internal enum class VideoSearchRunState { QUEUED, RUNNING, PAUSED, COMPLETED, STOPPED, FAILED }
internal enum class VideoSearchSiteState {
    QUEUED, ACTIVE, PAUSED, NEEDS_ATTENTION, COMPLETED, STOPPED, FAILED
}
internal enum class VideoSearchSiteIssue {
    LOGIN_REQUIRED, CAPTCHA_REQUIRED, NETWORK_ERROR, TIMEOUT, CONFIGURATION_CHANGED, OTHER
}
internal enum class VideoSearchCompletionReason { LIMIT_REACHED, NO_MORE_RESULTS }

internal data class VideoSearchRun(
    val id: String,
    val scope: VideoSearchScope,
    val query: String,
    /** Immutable snapshot of the explicitly selected sites at launch. */
    val siteIds: List<String>,
    val parallelism: VideoSearchParallelism,
    val status: VideoSearchRunState,
    val startedAtMs: Long,
    val updatedAtMs: Long = startedAtMs,
    val completedAtMs: Long? = null,
    val resultTabId: String? = null,
) {
    init {
        require(id.isNotBlank() && query.isNotBlank())
        require(siteIds.isNotEmpty() && siteIds.all { it.isNotBlank() })
        require(siteIds.size == siteIds.distinct().size)
    }

    override fun toString(): String = "VideoSearchRun(redacted)"
}

internal data class VideoSearchSiteTask(
    val runId: String,
    val siteId: String,
    val scope: VideoSearchScope,
    val status: VideoSearchSiteState = VideoSearchSiteState.QUEUED,
    val uniqueResults: Int = 0,
    val resultLimit: Int = DEFAULT_VIDEO_SEARCH_SITE_LIMIT,
    val visitedPages: Int = 0,
    val issue: VideoSearchSiteIssue? = null,
    /** Only populated for an actually completed site; user stop is not COMPLETE. */
    val completionReason: VideoSearchCompletionReason? = null,
) {
    init {
        require(runId.isNotBlank() && siteId.isNotBlank())
        require(resultLimit > 0 && uniqueResults in 0..resultLimit && visitedPages >= 0)
        require((status == VideoSearchSiteState.COMPLETED) == (completionReason != null))
    }
}

/** Provenance is attached to ONE result card/link, not a shared document title. */
internal enum class VideoResultTitleSource { CARD, STRUCTURED, LINK, PLAYER, UNKNOWN }
internal enum class VideoResultImageSource { CARD, STRUCTURED, PLAYER, UNKNOWN }

internal data class VideoSearchFilm(
    val id: String,
    val runId: String,
    val siteId: String,
    val scope: VideoSearchScope,
    /** Name snapshot survives manual removal or renaming of a source site. */
    val siteName: String,
    /** Exact film page URL: never remove query parameters during modelling. */
    val filmPageUrl: String,
    val originalTitle: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long? = null,
    val titleSource: VideoResultTitleSource = VideoResultTitleSource.UNKNOWN,
    val imageSource: VideoResultImageSource = VideoResultImageSource.UNKNOWN,
    val originalIndex: Int,
    val foundAtMs: Long,
) {
    init {
        require(id.isNotBlank() && runId.isNotBlank() && siteId.isNotBlank())
        require(siteName.isNotBlank() && filmPageUrl.isNotBlank() && originalIndex >= 0)
        require(durationSeconds == null || durationSeconds >= 0)
        require(originalTitle == null || originalTitle.isNotBlank())
        require(thumbnailUrl == null || thumbnailUrl.isNotBlank())
        require(originalTitle != null || titleSource == VideoResultTitleSource.UNKNOWN)
        require(thumbnailUrl != null || imageSource == VideoResultImageSource.UNKNOWN)
    }

    override fun toString(): String = "VideoSearchFilm(redacted)"
}

/**
 * Saved folders are durable and independent of the 30-day run history.
 * Normal scope is required by type: private results cannot be saved here.
 */
internal data class VideoSavedFilmFolder(
    val id: String,
    val scope: VideoSearchScope.Normal,
    val name: String,
    val createdAtMs: Long,
    val updatedAtMs: Long = createdAtMs,
) {
    init {
        require(id.isNotBlank() && name.isNotBlank())
    }

    override fun toString(): String = "VideoSavedFilmFolder(redacted)"
}

internal data class VideoSavedFilmLink(
    val id: String,
    val folderId: String,
    val scope: VideoSearchScope.Normal,
    val filmPageUrl: String,
    val title: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val createdAtMs: Long,
) {
    init {
        require(id.isNotBlank() && folderId.isNotBlank() && filmPageUrl.isNotBlank())
        require(title == null || title.isNotBlank())
        require(thumbnailUrl == null || thumbnailUrl.isNotBlank())
        require(durationSeconds == null || durationSeconds >= 0)
    }

    override fun toString(): String = "VideoSavedFilmLink(redacted)"
}
