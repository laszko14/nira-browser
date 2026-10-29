package com.prirai.android.nira.videosearch

/**
 * Keys are identical between LazyColumn and LazyVerticalGrid. Keeping a
 * logical anchor across a view change avoids resetting to the first film.
 * When a film disappears, prefer its source header; never cross runs.
 */
internal object VideoSearchScrollKeys {
    fun film(siteId: String, id: String) = "film:$siteId:$id"
    fun site(siteId: String) = "site:$siteId"

    fun visible(
        display: VideoSearchResultsDisplay,
        groups: List<VideoSearchResultsGroup>,
        grouped: Boolean,
        expanded: Set<String>?,
    ): List<String> {
        if (!grouped) return display.visibleFilms.map { film(it.siteId, it.id) }
        return buildList {
            groups.forEach { group ->
                add(site(group.source.siteId))
                if (VideoSearchGroupExpansion.isExpanded(expanded, group.source.siteId)) {
                    group.films.forEach { add(film(it.siteId, it.id)) }
                }
            }
        }
    }

    fun indexForKey(keys: List<String>, anchor: String?): Int {
        if (keys.isEmpty() || anchor == null) return 0
        val exact = keys.indexOf(anchor)
        if (exact >= 0) return exact
        // A removed film has no exact key after a refresh. A source header is
        // a safer landmark than some other site's film.
        if (anchor.startsWith("film:")) {
            val source = anchor.substringAfter("film:").substringBefore(':')
            val header = keys.indexOf(site(source))
            if (header >= 0) return header
        }
        return 0
    }
}

/**
 * A child Compose horizontal scroller may ask the View parent to stop
 * intercepting. The parent must retain an edge swipe initiated inside the
 * user's configured LEFT-panel zone, but vertical/multitouch gestures are
 * always owned by the child. Only enabled on the internal search results tab.
 */
internal object VideoSearchEdgeGesturePolicy {
    fun keepParentEligible(
        disallowIntercept: Boolean, isResultsTab: Boolean,
        swipeEnabled: Boolean, edgeStart: Boolean,
        verticalOrMultitouch: Boolean,
    ): Boolean = disallowIntercept && isResultsTab && swipeEnabled &&
        edgeStart && !verticalOrMultitouch

    fun startedAtLeftEdge(x: Float, configuredWidthPx: Float): Boolean =
        configuredWidthPx > 0f && x >= 0f && x <= configuredWidthPx
}
