package com.prirai.android.nira.videosearch

/**
 * Search form ACK is never proof of results. A worker may begin harvesting
 * only after a different bound Gecko document yields actual film-page cards.
 * Old static cards and stale protocol responses cannot complete a search.
 */
internal object VideoSearchWorkerVerification {
    fun confirmed(
        originalDocumentId: String,
        currentDocumentId: String?,
        page: VideoSearchExtractedPage?,
    ): Boolean =
        currentDocumentId != null &&
            originalDocumentId != currentDocumentId &&
            page?.status == "ok" && page.films.isNotEmpty()

    fun finalQueueStatus(snapshot: VideoSearchPaginationSnapshot?): VideoSearchQueueStatus =
        when (snapshot?.reason) {
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
