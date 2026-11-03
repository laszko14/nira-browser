package com.prirai.android.nira.videosearch

/**
 * P1: Evidence is attached only to selectors confirmed by a user-initiated
 * search. Scores are heuristic ranks, never probabilities or permissions.
 * This is configuration metadata, not a second persistent database.
 */
internal enum class VideoSearchRuleOrigin { UNVERIFIED, VERIFIED, MANUAL }

internal data class VideoSearchRecognitionMemory(
    val origin: VideoSearchRuleOrigin = VideoSearchRuleOrigin.UNVERIFIED,
    val confidence: Int = 0,
    val confirmations: Int = 0,
    val lastVerifiedAtMs: Long? = null,
    val backupResultsSelectors: List<String> = emptyList(),
) {
    init {
        require(confidence in 0..100 && confirmations in 0..1000)
        require(lastVerifiedAtMs == null || lastVerifiedAtMs >= 0L)
        require(backupResultsSelectors.size <= 3 &&
            backupResultsSelectors.distinct().size == backupResultsSelectors.size &&
            backupResultsSelectors.all(VideoSearchBridgeProtocol::validSelector))
    }
    override fun toString() = "VideoSearchRecognitionMemory(redacted)"
}

/**
 * A fallback is a proposal until an independently authorised search confirms
 * that it is the correct result group. Manual selectors are never replaced.
 */
internal object VideoSearchRecognitionPolicy {
    fun verified(
        current: VideoSiteConfiguration,
        observedResultsSelector: String,
        nowMs: Long,
    ): VideoSiteConfiguration? {
        if (!VideoSearchBridgeProtocol.validSelector(observedResultsSelector) ||
            nowMs < 0L) return null
        val old = current.recognition
        val same = current.resultsCardSelector == observedResultsSelector
        val alternatives = if (!same && current.resultsCardSelector != null) {
            (old.backupResultsSelectors + observedResultsSelector)
                .distinct().takeLast(3)
        } else old.backupResultsSelectors
        return current.copy(
            // A learned selector is never substituted for a user's rule.
            resultsCardSelector = if (current.resultsCardSelector == null)
                observedResultsSelector else current.resultsCardSelector,
            recognition = old.copy(
                origin = if (old.origin == VideoSearchRuleOrigin.MANUAL)
                    VideoSearchRuleOrigin.MANUAL else VideoSearchRuleOrigin.VERIFIED,
                confidence = if (same) 90 else 85,
                confirmations = (old.confirmations + 1).coerceAtMost(1000),
                lastVerifiedAtMs = nowMs,
                backupResultsSelectors = alternatives,
            )
        )
    }

    fun fallbackCandidates(config: VideoSiteConfiguration): List<String> =
        config.recognition.backupResultsSelectors
            .filter { it != config.resultsCardSelector }
            .filter(VideoSearchBridgeProtocol::validSelector)
            .take(3)
}
