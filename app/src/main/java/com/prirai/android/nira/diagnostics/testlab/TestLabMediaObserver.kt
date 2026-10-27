package com.prirai.android.nira.diagnostics.testlab

/** Passive sanitized Gecko media detector statistics, no video renderer or playback bridge. */
internal class TestLabMediaObserver(private val capture:LabCapture) {
    @Volatile private var closed=false
    fun discovery(code:String,count:Int) {
        if(closed || count !in 1..100000 || !capture.allows(LabDomain.VIDEO))return
        val safe=discoveryCodes[code]?:return
        capture.note(safe,count=count)
    }
    fun close(){closed=true}
    private companion object {
        val discoveryCodes=mapOf(
            "before_play" to LabCode.DETECTOR_BEFORE_PLAY,
            "after_play" to LabCode.DETECTOR_AFTER_PLAY,
            "duplicate" to LabCode.DETECTOR_DUPLICATE,
            "native_rejected" to LabCode.DETECTOR_REJECTED,
            "queue_limit" to LabCode.DETECTOR_QUEUE_LIMIT,
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
            "manifest_valid" to LabCode.DETECTOR_MANIFEST_VALID
        )
    }
}
