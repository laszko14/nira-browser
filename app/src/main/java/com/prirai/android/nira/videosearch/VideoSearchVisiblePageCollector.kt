package com.prirai.android.nira.videosearch

/**
 * Bounded read of ONE already-open document, before any cross-page crawling.
 * MoreOnPage is an uncertainty indicator; NOT a completion decision.
 * Configured pagination hints are not evidence that a next page exists.
 */
internal data class VideoSearchVisiblePage(
    val films: List<VideoSearchExtractedFilm>,
    val scannedCards: Int,
    val moreOnPage: Boolean,
    val paginationStrategy: VideoSearchPagination,
) {
    init {
        require(scannedCards in 0..80)
        require(films.size <= scannedCards)
        require(films.size <= 80)
    }

    override fun toString(): String = "VideoSearchVisiblePage(redacted)"
}

/**
 * Aggregates 16-card document windows. Cursor is a card offset rather than
 * a film count: duplicates and cards without valid links never stall progress.
 * No page navigation, Room writes, timers, or lasting session state.
 */
internal class VideoSearchVisiblePageCollector(
    private val pageOrigin: String,
    limit: Int,
    private val pagination: VideoSearchPagination,
) {
    private val maximum = limit.coerceIn(1, 80)
    private val records = LinkedHashMap<String, VideoSearchExtractedFilm>()
    private var cursor = 0
    private var remaining = true
    private var stoppedByLimit = false

    val nextCardIndex: Int get() = cursor
    val shouldReadAnotherBatch: Boolean get() =
        remaining && !stoppedByLimit && cursor < 80

    fun append(startIndex: Int, page: VideoSearchExtractedPage) {
        require(shouldReadAnotherBatch) { "No further page batches permitted" }
        require(startIndex == cursor && startIndex in 0..64 && startIndex % 16 == 0) {
            "Non-contiguous page batch"
        }
        require(page.status != "stale" && page.inspectedCards in 0..16 &&
            (page.inspectedCards != 0 || (!page.moreOnPage && page.films.isEmpty()))) {
            "Stale or inconsistent document window"
        }
        val endExclusive = startIndex + page.inspectedCards
        require(endExclusive <= 80)
        require(page.films.all {
            it.originalIndex in startIndex until endExclusive &&
                VideoSearchBridgeProtocol.sameOrigin(it.filmPageUrl, pageOrigin)
        }) { "Cross-window or off-site film" }
        val next = LinkedHashMap(records)
        page.films.forEach { film ->
            val previous = next[film.filmPageUrl]
            if (previous != null) {
                next[film.filmPageUrl] = VideoSearchResultCorrelator.merge(previous, film)
            } else if (next.size < maximum) {
                next[film.filmPageUrl] = film
            } else {
                stoppedByLimit = true
            }
        }
        // Commit only after validating the entire batch.
        records.clear()
        records.putAll(next)
        cursor = endExclusive
        remaining = page.moreOnPage
        if (records.size == maximum && remaining) stoppedByLimit = true
    }

    fun snapshot(): VideoSearchVisiblePage = VideoSearchVisiblePage(
        films = records.values.toList(),
        scannedCards = cursor,
        moreOnPage = remaining || stoppedByLimit,
        paginationStrategy = pagination,
    )
}
