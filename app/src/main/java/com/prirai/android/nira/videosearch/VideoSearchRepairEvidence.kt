package com.prirai.android.nira.videosearch

/**
 * Ephemeral proof from the current bound Gecko search. Not stored: the phrase
 * and page URLs must never be copied into durable recognition metadata.
 */
internal data class VideoSearchRepairEvidence(
    val originalDocumentId: String,
    val currentDocumentId: String?,
    val beforePage: VideoSearchExtractedPage?,
    val afterPage: VideoSearchExtractedPage?,
    val initialPageUrl: String?,
    val landingPageUrl: String?,
    val query: String,
    val recognition: VideoSearchBridgeMessage.Recognition?,
    val analyzedCards: String?,
    /** Only for a previously verified, unique Room backup selector. */
    val verifiedFallbackSelector: String? = null,
) {
    fun confirms(candidateCards: String?): Boolean {
        val selected = VideoSearchRecognitionPolicy.repairCandidate(
            recognition, analyzedCards)
        val fallbackAllowed = candidateCards != null &&
            candidateCards == verifiedFallbackSelector &&
            recognition?.status == "missing" &&
            recognition?.reason == "CARDS_NOT_FOUND" && analyzedCards == null
        if (candidateCards == null ||
            (candidateCards != selected && !fallbackAllowed) ||
            recognition?.documentId != currentDocumentId ||
            afterPage?.status != "ok" || afterPage.films.isEmpty()) return false
        return VideoSearchWorkerVerification.confirmedAfterQuery(
            originalDocumentId, currentDocumentId, afterPage, beforePage,
            landingPageUrl, query, initialPageUrl)
    }

    override fun toString() = "VideoSearchRepairEvidence(redacted)"
}
