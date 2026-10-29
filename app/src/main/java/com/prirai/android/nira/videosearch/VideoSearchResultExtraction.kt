package com.prirai.android.nira.videosearch

/**
 * Transient extraction result from ONE visible search-results document.
 * Not a search run and not a persisted VideoSearchFilm until a later stage
 * explicitly creates an owned run/task. No default-page title fallback.
 */
internal data class VideoSearchExtractedFilm(
    val filmPageUrl: String,
    val originalTitle: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val titleSource: VideoResultTitleSource,
    val imageSource: VideoResultImageSource,
    val originalIndex: Int,
) {
    init {
        VideoSearchIntegrity.requirePageUrl(filmPageUrl)
        require(originalTitle == null || originalTitle.isNotBlank())
        require(thumbnailUrl == null || thumbnailUrl.isNotBlank())
        if (thumbnailUrl != null) VideoSearchIntegrity.requirePageUrl(thumbnailUrl)
        require(durationSeconds == null || durationSeconds in 0L..86_400L)
        require(originalIndex in 0..200)
        require(originalTitle != null || titleSource == VideoResultTitleSource.UNKNOWN)
        require(thumbnailUrl != null || imageSource == VideoResultImageSource.UNKNOWN)
    }

    override fun toString(): String = "VideoSearchExtractedFilm(redacted)"
}

internal data class VideoSearchExtractedPage(
    val status: String,
    val films: List<VideoSearchExtractedFilm>,
    val inspectedCards: Int,
    val moreOnPage: Boolean,
) {
    init {
        require(status in setOf("ok", "empty", "stale"))
        require(inspectedCards in 0..80)
        require(films.size <= 16)
        require((status == "ok") == films.isNotEmpty())
        require(status != "stale" || (inspectedCards == 0 && !moreOnPage))
        require(films.map { it.filmPageUrl }.distinct().size == films.size)
        require(films.map { it.originalIndex }.distinct().size == films.size)
    }

    override fun toString(): String = "VideoSearchExtractedPage(redacted)"
}
