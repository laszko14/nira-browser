package com.prirai.android.nira.videosearch

/**
 * Stage 4D is a transient, user-triggered, SINGLE SITE crawl.
 * A search run and cross-site scheduler belong to later stages.
 * A partial stop must never be represented as COMPLETED/NO_MORE_RESULTS.
 */
internal enum class VideoSearchPaginationStop {
    LIMIT_REACHED, NO_MORE_RESULTS, NO_RESULTS, UNSUPPORTED,
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
        require(films.size <= VideoSearchResultBudgets.MAX_SITE_RESULTS &&
            visitedPages in 1..1_000 && scannedCards in 0..200_000 &&
            pageActions in 0..1_010)
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
    val limit = VideoSearchResultBudgets.requireSiteLimit(requestedLimit)
    private val maxCards = VideoSearchResultBudgets.documentCards(limit)
    private val maxPages = VideoSearchResultBudgets.pageLimit(limit)
    private val maxActions = VideoSearchResultBudgets.actionLimit(limit)
    private val maxScanned = VideoSearchResultBudgets.scanLimit(limit)
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
    fun containsFilmUrl(url: String): Boolean = byUrl.containsKey(url)
    val reachedLimit: Boolean get() = uniqueCount >= limit
    val exhaustedCardBudget: Boolean get() = cursor >= maxCards || scanned >= maxScanned
    val shouldReadCardWindow: Boolean get() = cursor < maxCards &&
        scanned < maxScanned && moreCards && !reachedLimit
    val isAtActionLimit: Boolean get() = actions >= maxActions
    val isAtDocumentLimit: Boolean get() = visitedPages >= maxPages

    fun beginDocument(url: String): Boolean {
        if (!VideoSearchBridgeProtocol.sameOrigin(source, url) ||
            url in visited || visited.size >= maxPages) return false
        visited.add(url)
        cursor = 0
        moreCards = true
        return true
    }

    /** Returns number of newly discovered unique film URLs. */
    fun appendWindow(startIndex: Int, batch: VideoSearchExtractedPage): Int {
        require(visited.isNotEmpty() && shouldReadCardWindow)
        require(startIndex == cursor && startIndex in 0 until maxCards) {
            "Non-contiguous card window"
        }
        require(batch.status !in setOf("stale", "no_results") &&
            batch.inspectedCards in 0..16 &&
            (batch.inspectedCards != 0 || !batch.moreOnPage)) {
            "Invalid crawl window"
        }
        val end = startIndex + batch.inspectedCards
        require(end <= maxCards && scanned + batch.inspectedCards <= maxScanned &&
            batch.films.size <= batch.inspectedCards)
        require(batch.films.all { film ->
            film.originalIndex in startIndex until end &&
                VideoSearchBridgeProtocol.sameOrigin(source, film.filmPageUrl)
        }) { "Invalid page index or cross-site film" }

        // Atomic staging: malicious/stale reply cannot partially mutate state.
        // Stage only the small validated incoming window: copying all 10,000
        // previously collected rows every 16 cards would be quadratic.
        val updates = LinkedHashMap<String, VideoSearchExtractedFilm>()
        var newCount = 0
        for (film in batch.films) {
            val existing = updates[film.filmPageUrl] ?: byUrl[film.filmPageUrl]
            if (existing != null) {
                updates[film.filmPageUrl] = VideoSearchResultCorrelator.merge(existing, film)
            } else if (byUrl.size + newCount < limit) {
                updates[film.filmPageUrl] = film
                newCount++
            }
        }
        byUrl.putAll(updates)
        val added = newCount
        cursor = end
        scanned += batch.inspectedCards
        moreCards = batch.moreOnPage
        return added
    }

    /**
     * SPA pages sometimes REPLACE their cards instead of appending them.
     * Accept an initial window of a replacement only when a previously unseen
     * exact on-site film URL proves that a new generation is visible. All
     * validation happens before changing the previous absolute cursor.
     * No synthetic page completion or reset to stale cards is permitted.
     */
    fun appendVerifiedReplacement(batch: VideoSearchExtractedPage): Int {
        require(visited.isNotEmpty() && actions > 0 && !reachedLimit)
        require(batch.status == "ok" && batch.inspectedCards in 1..16 &&
            batch.films.isNotEmpty() && batch.films.size <= batch.inspectedCards)
        require(batch.films.all {
            it.originalIndex in 0 until batch.inspectedCards &&
                VideoSearchBridgeProtocol.sameOrigin(source, it.filmPageUrl)
        })
        require(batch.films.any { it.filmPageUrl !in byUrl }) {
            "No verified new generation"
        }
        require(scanned + batch.inspectedCards <= maxScanned) {
            "Card scan budget exceeded"
        }
        val previousCursor = cursor
        val previousMore = moreCards
        cursor = 0
        moreCards = true
        return try {
            appendWindow(0, batch)
        } catch (failure: IllegalArgumentException) {
            cursor = previousCursor
            moreCards = previousMore
            throw failure
        }
    }

    /** A successful same-document click/scroll allows new cards at cursor. */
    fun armSameDocumentContinuation() {
        require(visited.isNotEmpty())
        require(!reachedLimit && cursor < maxCards && scanned < maxScanned)
        moreCards = true
    }

    fun recordAction(): Boolean {
        if (actions >= maxActions) return false
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
