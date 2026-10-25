package com.prirai.android.nira.media

import android.os.SystemClock
import java.util.UUID

/**
 * Native player handoff contains no URL or credential in an Intent. This
 * single-use, RAM-only registry binds a stream to the exact Gecko EngineSession
 * and document observed in the trusted BrowserStore at click time.
 */
internal object PlaybackSourceRegistry {
    internal class Source(
        val url: String,
        val userAgent: String?,
        val referrer: String?,
        val owner: MediaSessionBinding,
        val pageUrl: String,
        val autoHandoff: Boolean = false,
    ) {
        val isPrivate: Boolean get() = owner.isPrivate

        override fun toString(): String =
            "PlaybackSource(url=<redacted>, headers=<redacted>, tab=<redacted>)"
    }

    private data class Pending(val id: String, val source: Source, val createdMs: Long)
    private const val TTL_MS = 30_000L
    private var pending: Pending? = null

    @Synchronized
    fun issue(source: Source): String {
        val ticket = UUID.randomUUID().toString()
        // A second click supersedes the first; there is only one native player.
        pending = Pending(ticket, source, SystemClock.elapsedRealtime())
        return ticket
    }

    @Synchronized
    fun take(ticket: String?, current: MediaTabState?): Source? {
        val value = pending
        pending = null
        if (ticket == null || value == null || value.id != ticket) return null
        if (SystemClock.elapsedRealtime() - value.createdMs !in 0L..TTL_MS) return null
        val owner = value.source.owner
        val fresh = current ?: return null
        if (fresh.binding.session !== owner.session ||
            fresh.binding.tabId != owner.tabId ||
            fresh.binding.contextId != owner.contextId ||
            fresh.binding.isPrivate != owner.isPrivate ||
            fresh.pageUrl != value.source.pageUrl) return null
        return value.source
    }

    @Synchronized
    fun clear() {
        pending = null
    }
}
