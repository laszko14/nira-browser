package com.prirai.android.nira.media

import java.net.URI
import java.util.Locale

/** Transient Stage 3.1 result. Never store or print signed URLs in logs or data-class toString. */
internal class DetectedMedia(
    val url: String,
    val format: MediaFormat,
    val tabId: String,
    val contextId: String?,
    val isPrivate: Boolean,
) {
    override fun toString(): String = "DetectedMedia(format=$format, url=<redacted>)"
}

internal enum class MediaFormat { MP4, WEBM }

internal object MediaClassifier {
    private val nonPlayableExtensions = setOf("ts", "m4s", "m4a", "cmfv", "cmfa", "m3u", "m3u8", "mpd")
    private val likelySegment = Regex("(?i)^(?:init|segment|seg|chunk|frag)[_.-]?[0-9_-]*\\.(?:mp4|webm)$")

    fun fromDom(candidate: MediaDetectorMessage.Candidate, binding: MediaSessionBinding): DetectedMedia? {
        val uri = try { URI(candidate.url) } catch (_: Exception) { return null }
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("https", "http") || uri.host.isNullOrEmpty()) return null
        val file = (uri.path ?: "").substringAfterLast('/')
        val extension = file.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension in nonPlayableExtensions || likelySegment.matches(file)) return null

        // DOM MIME comes only from the selected <source type>, not an HTTP response.
        // Stage 3.2 will implement network response MIME and HLS/DASH separately.
        val format = when (candidate.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)) {
            "video/mp4" -> MediaFormat.MP4
            "video/webm" -> MediaFormat.WEBM
            null -> when (extension) {
                "mp4", "m4v" -> MediaFormat.MP4
                "webm" -> MediaFormat.WEBM
                else -> null
            }
            else -> null
        } ?: return null

        return DetectedMedia(candidate.url, format, binding.tabId, binding.contextId, binding.isPrivate)
    }
}
