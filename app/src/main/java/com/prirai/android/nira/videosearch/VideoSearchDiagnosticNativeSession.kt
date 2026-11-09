package com.prirai.android.nira.videosearch

/**
 * Native-only correlation. JS can describe the page, never choose its owner,
 * site, worker or run. No UI/session is started by construction.
 */
internal class VideoSearchDiagnosticNativeSession(
    val recorder: VideoSearchDiagnosticRecorder,
) {
    val owner: VideoSearchScope get() = recorder.owner
    val sessionId: String get() = recorder.sessionId

    private data class Source(
        val tabId: String, val documentId: String,
        val trace: VideoSearchDiagnosticTrace,
    )
    private val sources = LinkedHashMap<String, Source>()
    private val dropCounters = LinkedHashMap<String, Long>()
    private var retired = false

    fun registerRequest(
        tabId: String, documentId: String, requestId: String,
        siteId: String, workerTabId: String?, runId: String? = null,
        batchId: String? = null,
    ) {
        if (retired || !VideoSearchBridgeProtocol.validToken(requestId) ||
            !VideoSearchBridgeProtocol.validToken(documentId)) return
        sources[requestId] = Source(tabId, documentId, VideoSearchDiagnosticTrace(
            runId = runId, siteId = siteId, documentId = documentId,
            requestId = requestId, workerId = workerTabId, batchId = batchId))
        while (sources.size > 512) {
            val oldest = sources.keys.first()
            sources.remove(oldest)
            recordLoss("TRACE_WINDOW_EVICTED", tabId, documentId)
        }
    }

    private fun recordLoss(
        reason: String, tabId: String, documentId: String,
        count: Int = 1,
    ) {
        if (retired) return
        recorder.record(VideoSearchDiagnosticEvent(
            VideoSearchDiagnosticStage.BRIDGE,
            VideoSearchDiagnosticClaim.OBSERVATION,
            VideoSearchDiagnosticDecision.INTERRUPTED, reason,
            VideoSearchDiagnosticTrace(documentId = documentId),
            counts = mapOf(VideoSearchDiagnosticCount.REQUESTS to count),
        ))
    }

    /** A new Gecko port has a fresh JS RAM buffer even if the tab ID survives. */
    fun newPageBuffer(tabId: String) {
        dropCounters.remove(tabId)
    }

    /** Native queue status, not a JS-inferred completion. */
    fun queueStatus(runId: String, siteId: String, status: VideoSearchQueueStatus) {
        if (retired) return
        val decision = when (status) {
            VideoSearchQueueStatus.DONE -> VideoSearchDiagnosticDecision.ACCEPTED
            VideoSearchQueueStatus.FAILED,
            VideoSearchQueueStatus.NEEDS_ATTENTION -> VideoSearchDiagnosticDecision.FAILED
            VideoSearchQueueStatus.STOPPED -> VideoSearchDiagnosticDecision.CANCELLED
            else -> VideoSearchDiagnosticDecision.WAITING
        }
        recorder.record(VideoSearchDiagnosticEvent(
            VideoSearchDiagnosticStage.QUEUE, VideoSearchDiagnosticClaim.OBSERVATION,
            decision, "QUEUE_" + status.name,
            VideoSearchDiagnosticTrace(runId = runId, siteId = siteId),
        ))
    }

    fun interrupted(tabId: String, documentId: String, reason: String) {
        if (!retired) recordLoss(reason, tabId, documentId)
    }

    /** No inferred success from a missing native reply or failed worker. */
    fun nativeOutcome(
        tabId: String, documentId: String, requestId: String, reason: String,
        decision: VideoSearchDiagnosticDecision,
        accepted: Int? = null,
    ) {
        if (retired) return
        val trace = sources[requestId]?.takeIf {
            it.tabId == tabId && it.documentId == documentId
        }?.trace ?: VideoSearchDiagnosticTrace(documentId = documentId)
        recorder.record(VideoSearchDiagnosticEvent(
            VideoSearchDiagnosticStage.BRIDGE,
            VideoSearchDiagnosticClaim.OBSERVATION, decision, reason.take(128),
            trace,
            counts = if (accepted == null) emptyMap()
                else mapOf(VideoSearchDiagnosticCount.ACCEPTED_FILMS to accepted.coerceAtLeast(0)),
        ))
    }

    /**
     * Returns a new cursor only if all observations pass ownership,
     * document and sequence verification. Never partially accept a packet.
     * The caller verifies the current Gecko port and pending read request.
     */
    fun accept(
        tabId: String, allowedDocuments: Set<String>, cursor: Long,
        batch: VideoSearchDiagnosticWire.Reply.Batch,
    ): Long? {
        if (retired || batch.sessionId != sessionId || allowedDocuments.isEmpty() ||
            batch.documentId !in allowedDocuments || cursor < 0 ||
            batch.observations.any { it.documentId !in allowedDocuments }) return null
        if (batch.observations.any { it.sequence <= cursor }) return null
        if (batch.oldestSequence != null && batch.oldestSequence > cursor + 1L &&
            cursor != 0L) recordLoss("JS_CURSOR_GAP", tabId, batch.documentId,
                (batch.oldestSequence - cursor - 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val previousDrops = dropCounters[tabId] ?: 0L
        if (batch.dropped < previousDrops) return null
        if (batch.dropped > previousDrops) {
            recordLoss("JS_EVENTS_DROPPED", tabId, batch.documentId,
                (batch.dropped - previousDrops).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            dropCounters[tabId] = batch.dropped
        }
        for (row in batch.observations) {
            val source = row.requestId?.let(sources::get)?.takeIf {
                it.tabId == tabId && it.documentId == row.documentId
            }
            val trace = source?.trace ?: VideoSearchDiagnosticTrace(
                documentId = row.documentId)
            recorder.record(VideoSearchDiagnosticEvent(
                row.stage, VideoSearchDiagnosticClaim.OBSERVATION,
                row.decision, row.reason, trace, row.counts, row.fields))
        }
        return batch.observations.lastOrNull()?.sequence ?: cursor
    }

    fun stop(reason: String) {
        if (retired) return
        recorder.stop(reason)
        sources.clear()
        dropCounters.clear()
    }

    /** Idempotent: the only way private evidence can outlive the last tab
     * is if the registry still explicitly owns its exact private lifetime. */
    fun retirePrivate(scope: VideoSearchScope.Private): Boolean {
        if (owner != scope) return false
        retired = true
        sources.clear()
        dropCounters.clear()
        return recorder.retirePrivateLifetime(scope)
    }
}
