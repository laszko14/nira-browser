package com.prirai.android.nira.diagnostics.testlab

import com.prirai.android.nira.media.MediaEntry
import com.prirai.android.nira.media.MediaSessionBinding

/** Passive, process-local media diagnostics. No title, URL, token or profile exported. */
internal class TestLabMediaObserver(private val capture: LabCapture) {
    @Volatile private var closed = false
    private val ledger = VideoDiscoveryLedger()

    fun discovery(code: String, count: Int) {
        if (closed || count !in 1..100000 || !capture.allows(LabDomain.VIDEO)) return
        val safe = discoveryCodes[code] ?: return
        capture.note(safe, count = count)
    }
    fun entry(binding: MediaSessionBinding, media: MediaEntry) {
        if (!closed && capture.allows(LabDomain.VIDEO)) ledger.observe(binding, media)
    }
    fun navigation(binding: MediaSessionBinding) {
        if (!closed && capture.allows(LabDomain.VIDEO)) ledger.navigation(binding)
    }
    fun rejectedTitle(binding: MediaSessionBinding, different: Boolean) {
        if (!closed && capture.allows(LabDomain.VIDEO)) ledger.rejectedTitle(binding, different)
    }
    fun snapshot(): VideoDiscoverySummary = ledger.snapshot()
    fun close() { closed = true; ledger.clear() }

    private companion object {
        val discoveryCodes = mapOf(
            "before_play" to LabCode.DETECTOR_BEFORE_PLAY,
            "after_play" to LabCode.DETECTOR_AFTER_PLAY,
            "duplicate" to LabCode.DETECTOR_DUPLICATE,
            "native_rejected" to LabCode.DETECTOR_REJECTED,
            "queue_limit" to LabCode.DETECTOR_QUEUE_LIMIT,
            "queue_native_capacity" to LabCode.DETECTOR_QUEUE_NATIVE_CAPACITY,
            "queue_native_stale" to LabCode.DETECTOR_QUEUE_NATIVE_STALE,
            "queue_native_retry" to LabCode.DETECTOR_QUEUE_NATIVE_RETRY,
            "queue_native_reject" to LabCode.DETECTOR_QUEUE_NATIVE_REJECT,
            "queue_dom_roots" to LabCode.DETECTOR_QUEUE_DOM_ROOTS,
            "queue_early_evict" to LabCode.DETECTOR_QUEUE_EARLY_EVICT,
            "queue_network_rate" to LabCode.DETECTOR_QUEUE_NETWORK_RATE,
            "queue_manifest_capacity" to LabCode.DETECTOR_QUEUE_MANIFEST_CAPACITY,
            "queue_android_rate" to LabCode.DETECTOR_QUEUE_ANDROID_RATE,
            "queue_title_candidate_dropped" to LabCode.DETECTOR_QUEUE_TITLE_CANDIDATE_DROPPED,
            "title_individual" to LabCode.DETECTOR_TITLE_INDIVIDUAL,
            "title_structured" to LabCode.DETECTOR_TITLE_STRUCTURED,
            "title_unknown" to LabCode.DETECTOR_TITLE_UNKNOWN,
            "frame_mismatch" to LabCode.DETECTOR_FRAME_MISMATCH,
            "spa_navigation" to LabCode.SPA_NAVIGATION,
            "iframe_about_blank" to LabCode.DETECTOR_ABOUT_BLANK,
            "iframe_srcdoc" to LabCode.DETECTOR_SRCDOC,
            "early_request_seen" to LabCode.DETECTOR_EARLY_REQUEST,
            "early_request_bound" to LabCode.DETECTOR_EARLY_BOUND,
            "early_request_expired" to LabCode.DETECTOR_STALE,
            "early_request_ambiguous" to LabCode.DETECTOR_AMBIGUOUS,
            "no_source" to LabCode.DETECTOR_NO_SOURCE,
            "manifest_invalid" to LabCode.DETECTOR_REJECTED,
            "manifest_valid" to LabCode.DETECTOR_MANIFEST_VALID,
        )
    }
}
