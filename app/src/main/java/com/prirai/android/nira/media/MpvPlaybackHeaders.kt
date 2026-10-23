package com.prirai.android.nira.media

internal object MpvPlaybackHeaders {
    fun build(userAgent: String, referrer: String?): Array<String>? {
        if (!safe(userAgent)) return null
        if (referrer != null && !safe(referrer)) return null
        return if (referrer == null) {
            arrayOf("User-Agent", userAgent)
        } else {
            arrayOf("User-Agent", userAgent, "Referer", referrer)
        }
    }

    private fun safe(value: String): Boolean =
        value.isNotBlank() && value.length <= 2048 && value.none { it == '\r' || it == '\n' || it.isISOControl() }
}
