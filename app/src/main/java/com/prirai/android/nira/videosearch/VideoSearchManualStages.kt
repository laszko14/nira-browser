package com.prirai.android.nira.videosearch

/**
 * CFG-02: explicit form -> results configuration with no invented
 * cross-document selector or session. The bridge checks the current
 * normal owner, tab/session and document at each picker boundary.
 */
internal object VideoSearchManualStages {
    /** Only incomplete, preserved search controls are eligible for results-only picking. */
    fun pendingResults(site: VideoSearchSite): VideoSiteConfiguration? =
        site.configuration?.takeIf { config ->
            site.configurationState in setOf(VideoSiteConfigurationState.REPAIR_REQUIRED,
                    VideoSiteConfigurationState.SEARCH_READY_RESULTS_PENDING) &&
                config.resultsCardSelector == null &&
                VideoSearchBridgeProtocol.validSelector(config.searchInputSelector) &&
                (config.submission != VideoSearchSubmission.CLICK_SUBMIT ||
                    VideoSearchBridgeProtocol.validSelector(config.submitSelector))
        }

    /** Preserves previously confirmed search selectors exactly, even across /results. */
    fun withResults(
        searchControls: VideoSiteConfiguration,
        cardSelector: String?,
        next: VideoSearchBridgeMessage.Picked?,
    ): VideoSiteConfiguration? {
        if (cardSelector != null && !VideoSearchBridgeProtocol.validSelector(cardSelector))
            return null
        if (next != null && (next.pickKind != "next" ||
            !VideoSearchBridgeProtocol.validSelector(next.selector) ||
            (next.pagination == VideoSearchPagination.INFINITE_SCROLL &&
                next.selector != "html") ||
            next.pagination !in setOf(VideoSearchPagination.NEXT_LINK,
                VideoSearchPagination.LOAD_MORE, VideoSearchPagination.INFINITE_SCROLL))) return null
        return searchControls.copy(
            resultsCardSelector = cardSelector,
            pagination = next?.pagination ?: VideoSearchPagination.NONE,
            // "html" is an explicit transport sentinel, not a clickable locator.
            nextPageSelector = if (next?.pagination == VideoSearchPagination.INFINITE_SCROLL)
                null else next?.selector,
        )
    }
}

/**
 * Auto-discovery must never trust homepage cards as proof of a new query.
 * Only the bounded search form is a safe persisted discovery result.
 */
internal object VideoSearchAutoDiscoveryPolicy {
    fun pendingForm(
        report: VideoSearchBridgeMessage.Analysis?,
    ): VideoSearchBridgeMessage.Inspection? {
        if (report == null || report.confidence < 60 ||
            report.status !in setOf(VideoSearchAnalysisStatus.READY,
                VideoSearchAnalysisStatus.PARTIAL)) return null
        val form = report.formInspection() ?: return null
        return VideoSearchBridgeMessage.Inspection(
            form.documentId, form.requestId, "partial",
            form.inputSelector, null, form.submission, form.submitSelector,
            VideoSearchPagination.NONE, null)
    }
}
