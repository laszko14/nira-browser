package com.prirai.android.nira.media

import android.os.SystemClock
import java.util.UUID

/**
 * One-shot, process-local handoff to the non-exported native player.
 *
 * Signed URLs and headers must never be written to Intent extras, SavedState,
 * preferences, logs or the browsing history. The ticket is only an opaque key.
 * A process death deliberately invalidates the handoff rather than restoring
 * potentially private stream credentials from persistent storage.
 */
internal object PlaybackSourceRegistry {
    internal data class Source(
        val url: String,
        val userAgent: String?,
        val referrer: String?,
        val isPrivate: Boolean,
    ) {
        override fun toString() = "Source(url=<redacted>, userAgent=<redacted>, referrer=<redacted>)"
    }

    private data class Pending(val source: Source, val createdAtMs: Long)
    private const val TTL_MS = 30_000L
    private var pending: Pair<String, Pending>? = null

    @Synchronized
    fun issue(source: Source): String {
        val ticketId = UUID.randomUUID().toString()
        // There is only one native player per browser process in the MVP.
        pending = ticketId to Pending(source, SystemClock.elapsedRealtime())
        return ticketId
    }

    @Synchronized
    fun take(ticketId: String?): Source? {
        val record = pending
        pending = null
        if (ticketId.isNullOrBlank() || record?.first != ticketId) return null
        val elapsed = SystemClock.elapsedRealtime() - record.second.createdAtMs
        if (elapsed !in 0..TTL_MS) return null
        return record.second.source
    }

    @Synchronized
    fun clear() {
        pending = null
    }
}
