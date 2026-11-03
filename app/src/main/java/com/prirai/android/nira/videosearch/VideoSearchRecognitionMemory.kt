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
    /** One previous verified configuration, kept inside the existing Room JSON. */
    val rollbackConfigurationJson: String? = null,
    /** Restore REPAIR_REQUIRED as REPAIR_REQUIRED, never silently CONFIGURED. */
    val rollbackState: VideoSiteConfigurationState? = null,
) {
    init {
        require(confidence in 0..100 && confirmations in 0..1000)
        require(lastVerifiedAtMs == null || lastVerifiedAtMs >= 0L)
        require(rollbackConfigurationJson == null ||
            rollbackConfigurationJson.length in 1..4096)
        require(rollbackState == null || (rollbackConfigurationJson != null &&
            rollbackState in setOf(VideoSiteConfigurationState.CONFIGURED,
                VideoSiteConfigurationState.REPAIR_REQUIRED)))
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
    /** A discovered DOM group is a candidate, not proof of working search. */
    fun repairCandidate(
        recognition: VideoSearchBridgeMessage.Recognition?,
        analyzedCards: String?,
    ): String? {
        if (recognition?.status != "found" ||
            recognition.reason != "CANDIDATE_FOUND" ||
            recognition.confidence < 85 || recognition.matchedCards < 1) return null
        val candidate = recognition.resultsCardSelector ?: return null
        if (analyzedCards != null && analyzedCards != candidate) return null
        return candidate.takeIf(VideoSearchBridgeProtocol::validSelector)
    }

    /**
     * A fallback may be tried only if exactly ONE independently verified
     * alternative was learned and no competing structural group was found.
     */
    fun uniqueVerifiedFallback(
        previous: VideoSiteConfiguration,
        recognition: VideoSearchBridgeMessage.Recognition?,
        analyzedCards: String?,
    ): String? = if (recognition?.status == "missing" &&
        recognition?.reason == "CARDS_NOT_FOUND" && analyzedCards == null)
        fallbackCandidates(previous).singleOrNull() else null

    /** Only called after search evidence is checked at the repository boundary. */
    fun repaired(
        oldConfig: VideoSiteConfiguration,
        candidate: VideoSiteConfiguration,
        nowMs: Long,
        previousState: VideoSiteConfigurationState = VideoSiteConfigurationState.CONFIGURED,
    ): VideoSiteConfiguration? {
        if (previousState !in setOf(VideoSiteConfigurationState.CONFIGURED,
                VideoSiteConfigurationState.REPAIR_REQUIRED) || nowMs < 0 || !VideoSearchBridgeProtocol.validSelector(candidate.searchInputSelector) ||
            !VideoSearchBridgeProtocol.validSelector(candidate.resultsCardSelector)) return null
        val old = oldConfig.recognition
        // Never recursively nest revisions: the user can undo the LAST repair.
        val previous = VideoSearchRowCodec.configurationJson(oldConfig.copy(
            recognition = old.copy(
                rollbackConfigurationJson = null, rollbackState = null))) ?: return null
        if (previous.length > 4096) return null
        val backups = (old.backupResultsSelectors + listOfNotNull(oldConfig.resultsCardSelector))
            .filter { it != candidate.resultsCardSelector }
            .filter(VideoSearchBridgeProtocol::validSelector).distinct().takeLast(3)
        return candidate.copy(recognition = old.copy(
            origin = VideoSearchRuleOrigin.VERIFIED,
            confidence = 90,
            confirmations = (old.confirmations + 1).coerceAtMost(1000),
            lastVerifiedAtMs = nowMs,
            backupResultsSelectors = backups,
            rollbackConfigurationJson = previous,
            rollbackState = previousState,
        ))
    }

    /** A corrupt or stale backup must never replace a working rule. */
    fun rollback(current: VideoSiteConfiguration): VideoSiteConfiguration? {
        val previous = current.recognition.rollbackConfigurationJson ?: return null
        if (current.recognition.rollbackState == null) return null
        return try {
            VideoSearchRowCodec.readConfiguration(previous)?.takeIf {
                it.recognition.rollbackConfigurationJson == null &&
                    it.recognition.rollbackState == null &&
                    VideoSearchBridgeProtocol.validSelector(it.searchInputSelector) &&
                    (it.resultsCardSelector == null ||
                        VideoSearchBridgeProtocol.validSelector(it.resultsCardSelector))
            }
        } catch (_: Exception) { null }
    }

}
