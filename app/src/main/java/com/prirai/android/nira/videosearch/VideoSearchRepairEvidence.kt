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
) {
    fun confirms(candidateCards: String?): Boolean {
        val selected = VideoSearchRecognitionPolicy.repairCandidate(
            recognition, analyzedCards)
        if (candidateCards == null || candidateCards != selected ||
            recognition?.documentId != currentDocumentId ||
            afterPage?.status != "ok" || afterPage.films.isEmpty()) return false
        return VideoSearchWorkerVerification.confirmedAfterQuery(
            originalDocumentId, currentDocumentId, afterPage, beforePage,
            landingPageUrl, query, initialPageUrl)
    }

    override fun toString() = "VideoSearchRepairEvidence(redacted)"
}
