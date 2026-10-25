package com.prirai.android.nira.media

/**
 * Only UI-thread callbacks may access this state. Replacing an mpv playlist
 * entry produces END_FILE for the previous entry before FILE_LOADED for the next.
 * An expected replacement END_FILE must not be mistaken for an open failure.
 */
internal class MpvRetryController {
    private var expectedReplacementEnd = false
    private var resumeAtSeconds: Double? = null

    fun prepare(previousLoaded: Boolean, positionSeconds: Double, durationSeconds: Double) {
        expectedReplacementEnd = previousLoaded
        // Only VOD with a known finite duration can safely resume at an
        // absolute timestamp. Live streams and unknown-duration files restart.
        resumeAtSeconds =
            if (previousLoaded && positionSeconds.isFinite() && durationSeconds.isFinite() &&
                durationSeconds > 30.0 && positionSeconds >= 5.0 &&
                positionSeconds < durationSeconds - 5.0) positionSeconds else null
    }

    fun consumeReplacementEnd(): Boolean {
        if (!expectedReplacementEnd) return false
        expectedReplacementEnd = false
        return true
    }

    fun onFileLoaded(): Double? {
        expectedReplacementEnd = false
        return resumeAtSeconds.also { resumeAtSeconds = null }
    }

    fun clear() {
        expectedReplacementEnd = false
        resumeAtSeconds = null
    }
}
