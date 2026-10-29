package com.prirai.android.nira.videosearch

/**
 * Stage 4D is a transient, user-triggered, SINGLE SITE crawl.
 * A search run and cross-site scheduler belong to later stages.
 * A partial stop must never be represented as COMPLETED/NO_MORE_RESULTS.
 */
internal enum class VideoSearchPaginationStop {
    LIMIT_REACHED, NO_MORE_RESULTS, UNSUPPORTED,
    STALLED, UNSAFE, RESOURCE_LIMIT, CONFIGURATION_CHANGED, ERROR,
}

internal data class VideoSearchPaginationSnapshot(
    val films: List<VideoSearchExtractedFilm>,
    val scannedCards: Int,
    val visitedPages: Int,
    val pageActions: Int,
    val reason: VideoSearchPaginationStop,
) {
    init {
        require(films.size <= 200 && visitedPages in 1..12 &&
            scannedCards in 0..3840 && pageActions in 0..20)
        require(films.map { it.filmPageUrl }.distinct().size == films.size)
    }
    override fun toString(): String = "VideoSearchPaginationSnapshot(redacted)"
}

/**
 * Pure, bounded aggregation. Cursor = absolute card position in the CURRENT
 * document; it is reset only after verifying a new same-origin document.
 * A load-more/scroll action keeps the cursor, so old cards aren't re-scanned.
 */
internal class VideoSearchPaginationCollector(
    siteUrl: String,
    requestedLimit: Int,
) {
    val limit = requestedLimit.coerceIn(1, 200)
    private val source = siteUrl
    private val byUrl = LinkedHashMap<String, VideoSearchExtractedFilm>()
    private val visited = LinkedHashSet<String>()
    private var cursor = 0
    private var moreCards = true
    private var scanned = 0
    private var actions = 0

    val nextCardIndex: Int get() = cursor
    val visitedPages: Int get() = visited.size
    val pageActions: Int get() = actions
    val uniqueCount: Int get() = byUrl.size
    val reachedLimit: Boolean get() = uniqueCount >= limit
    val exhaustedCardBudget: Boolean get() = cursor >= 320
    val shouldReadCardWindow: Boolean get() = cursor < 320 && moreCards && !reachedLimit
    val isAtActionLimit: Boolean get() = actions >= 20
    val isAtDocumentLimit: Boolean get() = visitedPages >= 12

    fun beginDocument(url: String): Boolean {
        if (!VideoSearchBridgeProtocol.sameOrigin(source, url) ||
            url in visited || visited.size >= 12) return false
        visited.add(url)
        cursor = 0
        moreCards = true
        return true
    }

    /** Returns number of newly discovered unique film URLs. */
    fun appendWindow(startIndex: Int, batch: VideoSearchExtractedPage): Int {
        require(visited.isNotEmpty() && shouldReadCardWindow)
        require(startIndex == cursor && startIndex in 0..304 &&
            startIndex % 16 == 0) { "Non-contiguous card window" }
        require(batch.status != "stale" && batch.inspectedCards in 0..16 &&
            (batch.inspectedCards != 0 || !batch.moreOnPage)) {
            "Invalid crawl window"
        }
        val end = startIndex + batch.inspectedCards
        require(end <= 320 && batch.films.size <= batch.inspectedCards)
        require(batch.films.all { film ->
            film.originalIndex in startIndex until end &&
                VideoSearchBridgeProtocol.sameOrigin(source, film.filmPageUrl)
        }) { "Invalid page index or cross-site film" }

        // Atomic staging: malicious/stale reply cannot partially mutate state.
        val staged = LinkedHashMap(byUrl)
        for (film in batch.films) {
            val existing = staged[film.filmPageUrl]
            if (existing != null) {
                staged[film.filmPageUrl] = VideoSearchResultCorrelator.merge(existing, film)
            } else if (staged.size < limit) {
                staged[film.filmPageUrl] = film
            }
        }
        val added = staged.size - byUrl.size
        byUrl.clear()
        byUrl.putAll(staged)
        cursor = end
        scanned += batch.inspectedCards
        moreCards = batch.moreOnPage
        return added
    }

    /** A successful same-document click/scroll allows new cards at cursor. */
    fun armSameDocumentContinuation() {
        require(visited.isNotEmpty())
        require(!reachedLimit && cursor < 320)
        moreCards = true
    }

    fun recordAction(): Boolean {
        if (actions >= 20) return false
        actions++
        return true
    }

    fun snapshot(reason: VideoSearchPaginationStop) = VideoSearchPaginationSnapshot(
        films = byUrl.values.toList(),
        scannedCards = scanned,
        visitedPages = visitedPages.coerceAtLeast(1),
        pageActions = actions,
        reason = reason,
    )
}
