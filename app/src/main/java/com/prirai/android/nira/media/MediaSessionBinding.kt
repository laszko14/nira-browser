package com.prirai.android.nira.media

/** Native session identity and its current BrowserStore partition; never supplied by JavaScript. */
internal data class MediaSessionBinding(
    val session: Any,
    val tabId: String,
    val contextId: String?,
    val isPrivate: Boolean,
)

internal fun resolveMediaSession(source: Any?, tabs: List<MediaSessionBinding>): MediaSessionBinding? =
    if (source == null) null else tabs.singleOrNull { it.session === source }
