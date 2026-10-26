package com.prirai.android.nira.media

/**
 * An HTMLVideoElement with blob: has NO URL libmpv can use directly.
 * Use an already observed native network HLS source only when it is the
 * sole unambiguous candidate in the exact same frame and Gecko session.
 * Avoid unsupported, expired, authentication-blocked and multi-quality
 * ambiguous candidates. No network probing or URL rewriting.
 */
internal object BlobHlsSelector {
    fun select(entries: List<MediaEntry>, frameUrl: String): MediaEntry? {
        val viable=entries.filter {
            it.frameUrl == frameUrl &&
                it.format == MediaFormat.HLS &&
                MediaSource.NETWORK in it.sources &&
                it.playbackStatus !in setOf(MediaPlaybackStatus.EXPIRED,
                    MediaPlaybackStatus.AUTH_ERROR,MediaPlaybackStatus.UNPLAYABLE) &&
                runCatching {
                    val parsed=android.net.Uri.parse(it.url)
                    parsed.scheme in listOf("https","http") &&
                        !parsed.host.isNullOrBlank() &&
                        parsed.userInfo == null
                }.getOrDefault(false)
        }
        // A quality variant group can have multiple entries. Never silently
        // pick a random stream, an advertisement, or a playlist from elsewhere.
        val groups=viable.groupBy { it.groupId ?: it.url }
        if (groups.size != 1) return null
        val variants=groups.values.single()
        // A network-confirmed entry outranks observed-but-not-verified data.
        return variants.filter { it.playbackStatus == MediaPlaybackStatus.CONFIRMED }
            .singleOrNull() ?: variants.singleOrNull()
    }
}
