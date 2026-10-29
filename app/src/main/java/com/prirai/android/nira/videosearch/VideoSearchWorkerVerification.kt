package com.prirai.android.nira.videosearch

/**
 * Search form ACK is never proof of results. A worker may begin harvesting
 * only after a different bound Gecko document yields actual film-page cards.
 * Old static cards and stale protocol responses cannot complete a search.
 */
internal object VideoSearchWorkerVerification {
    /**
     * Search ACK never establishes a new result generation.
     *
     * A navigated Gecko document is new evidence. On an SPA page, the
     * BEFORE search crawl must have succeeded for the same card selector,
     * and the AFTER crawl must introduce at least one previously absent
     * exact film URL. Input changes, form ACK, reordered old cards and
     * cosmetic title/image changes are insufficient evidence.
     *
     * Callers check the same engine/owner/epoch independently, and never
     * persist or open results before this gate returns true.
     */
    fun confirmed(
        originalDocumentId: String,
        currentDocumentId: String?,
        page: VideoSearchExtractedPage?,
        beforePage: VideoSearchExtractedPage? = null,
    ): Boolean {
        if (currentDocumentId == null || page == null) return false
        val provenEmpty = page.status == "no_results" && page.films.isEmpty() &&
            page.inspectedCards == 0 && !page.moreOnPage
        if (!provenEmpty && (page.status != "ok" || page.films.isEmpty())) return false
        if (originalDocumentId != currentDocumentId) return true
        if (beforePage == null || beforePage.status == "stale") return false
        if (provenEmpty) return beforePage.status == "ok" &&
            beforePage.films.isNotEmpty()
        val previousUrls = beforePage.films.mapTo(HashSet()) { it.filmPageUrl }
        return page.films.any { it.filmPageUrl !in previousUrls }
    }

    fun finalQueueStatus(snapshot: VideoSearchPaginationSnapshot?): VideoSearchQueueStatus =
        when (snapshot?.reason) {
            VideoSearchPaginationStop.NO_RESULTS ->
                if (snapshot.films.isEmpty()) VideoSearchQueueStatus.DONE
                else VideoSearchQueueStatus.NEEDS_ATTENTION
            VideoSearchPaginationStop.LIMIT_REACHED,
            VideoSearchPaginationStop.NO_MORE_RESULTS ->
                if (snapshot.films.isNotEmpty()) VideoSearchQueueStatus.DONE
                else VideoSearchQueueStatus.NEEDS_ATTENTION
            VideoSearchPaginationStop.CONFIGURATION_CHANGED ->
                VideoSearchQueueStatus.NEEDS_ATTENTION
            VideoSearchPaginationStop.ERROR -> VideoSearchQueueStatus.FAILED
            else -> VideoSearchQueueStatus.STOPPED
        }
}
