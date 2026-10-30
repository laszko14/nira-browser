package com.prirai.android.nira.videosearch

/**
 * Resource safeguards, not hidden result limits. The requested site limit is
 * honored up to 10_000; the aggregate run quota is the sum of its immutable
 * selected-site limits, not a second 2_000-film ceiling.
 *
 * DOM/time/action budgets still permit a truthful RESOURCE_LIMIT stop.
 */
internal object VideoSearchResultBudgets {
    const val MAX_SITE_RESULTS = 10_000
    const val MAX_SELECTED_SITES = 200
    const val MAX_RUN_RESULTS = MAX_SITE_RESULTS * MAX_SELECTED_SITES

    fun requireSiteLimit(limit: Int): Int {
        require(limit in 1..MAX_SITE_RESULTS) { "Site result limit outside 1..10000" }
        return limit
    }

    fun aggregateLimit(limits: Collection<Int>): Int {
        require(limits.isNotEmpty() && limits.size <= MAX_SELECTED_SITES)
        limits.forEach { requireSiteLimit(it) }
        return limits.sum()
    }

    /** Old 200-film runs keep the former 320-card, 12-page budgets. */
    fun documentCards(limit: Int): Int {
        requireSiteLimit(limit)
        return if (limit <= 200) 320 else (limit * 2).coerceIn(320, 10_000)
    }

    fun pageLimit(limit: Int): Int =
        if (requireSiteLimit(limit) <= 200) 12
        else (12 + (limit + 9) / 10).coerceAtMost(1_000)

    fun actionLimit(limit: Int): Int =
        if (requireSiteLimit(limit) <= 200) 20
        else (pageLimit(limit) + 10).coerceAtMost(1_010)

    fun scanLimit(limit: Int): Int =
        if (requireSiteLimit(limit) <= 200) 3_840
        else (limit * 20).coerceIn(3_840, 200_000)

    /** Time budget rises with larger requested crawls but stays bounded. */
    fun durationMs(limit: Int): Long =
        (120_000L + (requireSiteLimit(limit) - 200).coerceAtLeast(0) * 75L)
            .coerceAtMost(900_000L)
}
