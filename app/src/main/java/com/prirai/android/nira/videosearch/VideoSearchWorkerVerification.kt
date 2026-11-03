package com.prirai.android.nira.videosearch

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

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

    /**
     * New navigation is not proof of a query. Only a matching known search
     * parameter or new exact film identities relative to the pre-submit
     * snapshot may confirm another Gecko document. Query stays transient.
     */
    fun confirmedAfterQuery(
        originalDocumentId: String,
        currentDocumentId: String?,
        page: VideoSearchExtractedPage?,
        beforePage: VideoSearchExtractedPage?,
        landingPageUrl: String?,
        query: String,
        initialPageUrl: String? = null,
    ): Boolean {
        if (!confirmed(originalDocumentId, currentDocumentId, page, beforePage))
            return false
        if (originalDocumentId == currentDocumentId) return true
        // A same-URL reload is not proof of a new search result generation.
        if (landingPageUrl != initialPageUrl &&
            landingMatchesQuery(landingPageUrl, query)) return true
        if (beforePage?.status != "ok" || page?.status != "ok") return false
        val oldUrls = beforePage.films.mapTo(HashSet()) { it.filmPageUrl }
        return page.films.any { it.filmPageUrl !in oldUrls }
    }

    /** Only explicit search parameters count; never URL substring matches. */
    internal fun landingMatchesQuery(pageUrl: String?, query: String): Boolean {
        if (pageUrl.isNullOrBlank() || pageUrl.length > 2048 ||
            query.isBlank() || query.length > 250) return false
        val page = try { URI(pageUrl) } catch (_: Exception) { return false }
        if (page.scheme?.lowercase(Locale.ROOT) !in setOf("https", "http") ||
            page.userInfo != null) return false
        val raw = page.rawQuery ?: return false
        if (raw.length > 1500) return false
        val normalize: (String) -> String = {
            it.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        }
        val expected = normalize(query)
        val keys = setOf("q", "s", "query", "search", "keyword",
            "keywords", "term", "search_query", "searchterm")
        for (item in raw.split('&').take(32)) {
            val separator = item.indexOf('=')
            if (separator <= 0) continue
            try {
                val key = URLDecoder.decode(item.substring(0, separator),
                    "UTF-8").lowercase(Locale.ROOT)
                if (key !in keys) continue
                val value = URLDecoder.decode(item.substring(separator + 1),
                    "UTF-8")
                if (normalize(value) == expected) return true
            } catch (_: IllegalArgumentException) { return false }
        }
        return false
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
