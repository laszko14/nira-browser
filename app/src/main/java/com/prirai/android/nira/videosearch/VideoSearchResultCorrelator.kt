package com.prirai.android.nira.videosearch

/**
 * Metadata correlation is keyed strictly by the EXACT film-page URL.
 * Never merge by a similar title, poster, host, or a normalized query.
 *
 * Higher provenance can improve a field independently; ties keep the first
 * non-null value, avoiding silent oscillation on conflicting cards.
 * This utility is pure and does not persist results or start navigation.
 */
internal object VideoSearchResultCorrelator {
    private fun titlePriority(value: VideoResultTitleSource): Int = when (value) {
        VideoResultTitleSource.STRUCTURED -> 3
        VideoResultTitleSource.CARD -> 2
        VideoResultTitleSource.LINK -> 1
        VideoResultTitleSource.PLAYER -> 0 // not used by result-card extraction
        VideoResultTitleSource.UNKNOWN -> 0
    }

    private fun imagePriority(value: VideoResultImageSource): Int = when (value) {
        VideoResultImageSource.STRUCTURED -> 3
        VideoResultImageSource.CARD -> 2
        VideoResultImageSource.PLAYER -> 0 // not used by result-card extraction
        VideoResultImageSource.UNKNOWN -> 0
    }

    fun merge(first: VideoSearchExtractedFilm, later: VideoSearchExtractedFilm)
        : VideoSearchExtractedFilm {
        require(first.filmPageUrl == later.filmPageUrl) {
            "Do not correlate distinct film-page URLs"
        }
        val newerTitle = later.originalTitle != null &&
            (first.originalTitle == null ||
                titlePriority(later.titleSource) > titlePriority(first.titleSource))
        val newerImage = later.thumbnailUrl != null &&
            (first.thumbnailUrl == null ||
                imagePriority(later.imageSource) > imagePriority(first.imageSource))
        return first.copy(
            originalTitle = if (newerTitle) later.originalTitle else first.originalTitle,
            titleSource = if (newerTitle) later.titleSource else first.titleSource,
            thumbnailUrl = if (newerImage) later.thumbnailUrl else first.thumbnailUrl,
            imageSource = if (newerImage) later.imageSource else first.imageSource,
            // Duration source is not typed in the persistent model: keep first
            // valid duration; never replace it with a conflicting guess.
            durationSeconds = first.durationSeconds ?: later.durationSeconds,
            originalIndex = minOf(first.originalIndex, later.originalIndex),
        )
    }

    fun correlate(cards: List<VideoSearchExtractedFilm>): List<VideoSearchExtractedFilm> {
        require(cards.size <= 80) { "Only one bounded result-document batch is supported" }
        val byUrl = LinkedHashMap<String, VideoSearchExtractedFilm>()
        for (card in cards) {
            val previous = byUrl[card.filmPageUrl]
            if (previous == null) byUrl[card.filmPageUrl] = card
            else byUrl[card.filmPageUrl] = merge(previous, card)
        }
        return byUrl.values.toList()
    }
}
