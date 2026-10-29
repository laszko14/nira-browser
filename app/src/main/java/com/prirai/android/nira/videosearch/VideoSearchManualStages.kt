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
            site.configurationState == VideoSiteConfigurationState.REPAIR_REQUIRED &&
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
            next.pagination !in setOf(VideoSearchPagination.NEXT_LINK,
                VideoSearchPagination.LOAD_MORE))) return null
        return searchControls.copy(
            resultsCardSelector = cardSelector,
            pagination = next?.pagination ?: VideoSearchPagination.NONE,
            nextPageSelector = next?.selector,
        )
    }
}
