package com.prirai.android.nira.media

import java.net.URI
import java.util.Locale

/** Transient media only; never serialize or log signed URLs. Full state belongs to 3.3. */
internal class DetectedMedia(
    val url: String,
    val format: MediaFormat,
    val tabId: String,
    val contextId: String?,
    val isPrivate: Boolean,
) {
    override fun toString(): String = "DetectedMedia(format=$format, url=<redacted>)"
}

internal enum class MediaFormat { MP4, WEBM, OGG, MOV, HLS, DASH }
internal enum class MediaSource { DOM, PAGE, NETWORK }

internal object MediaClassifier {
    private val segments = setOf("ts", "m4s", "m4a", "cmfv", "cmfa")
    private val fragmentName = Regex("(?i)^(?:init|initialization|segment|seg|chunk|frag)[_.-]?[a-z0-9_-]*\\.(?:mp4|webm)$")
    private val direct = mapOf("mp4" to MediaFormat.MP4, "m4v" to MediaFormat.MP4,
        "webm" to MediaFormat.WEBM, "ogv" to MediaFormat.OGG,
        "ogg" to MediaFormat.OGG, "mov" to MediaFormat.MOV)
    private val mimes = mapOf(
        "video/mp4" to MediaFormat.MP4, "video/webm" to MediaFormat.WEBM,
        "video/ogg" to MediaFormat.OGG, "application/ogg" to MediaFormat.OGG,
        "video/quicktime" to MediaFormat.MOV,
        "application/vnd.apple.mpegurl" to MediaFormat.HLS,
        "application/x-mpegurl" to MediaFormat.HLS,
        "audio/mpegurl" to MediaFormat.HLS, "audio/x-mpegurl" to MediaFormat.HLS,
        "application/dash+xml" to MediaFormat.DASH,
    )

    private fun parsed(url: String): URI? = try {
        URI(url).takeIf { it.scheme?.lowercase(Locale.ROOT) in setOf("https", "http") &&
            !it.host.isNullOrEmpty() && it.rawUserInfo == null }
    } catch (_: Exception) { null }

    private fun file(uri: URI) = (uri.path ?: "").substringAfterLast('/')
    private fun extension(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    fun isSegment(url: String): Boolean {
        val uri = parsed(url) ?: return false
        val name = file(uri)
        return extension(name) in segments || fragmentName.matches(name)
    }

    fun fromDom(candidate: MediaDetectorMessage.Candidate, binding: MediaSessionBinding): DetectedMedia? {
        if (candidate.source != MediaSource.DOM) return null
        val uri = parsed(candidate.url) ?: return null
        val name = file(uri)
        val ext = extension(name)
        if (isSegment(candidate.url)) return null
        val mime = candidate.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        // A video element can expose an actual HLS/DASH source before a
        // network request is visible. Do not discard explicit manifests.
        // Conflicting MIME and extension still must not become a false MP4.
        if (ext in setOf("m3u8", "mpd") &&
            mime != null && mime !in setOf("application/octet-stream", "binary/octet-stream",
                "application/vnd.apple.mpegurl", "application/x-mpegurl",
                "audio/mpegurl", "audio/x-mpegurl", "application/dash+xml")) return null
        val format = when {
            mime in setOf("video/mp4", "video/webm",
                "application/vnd.apple.mpegurl", "application/x-mpegurl",
                "audio/mpegurl", "audio/x-mpegurl", "application/dash+xml") -> mimes[mime]
            mime != null && mime !in setOf("application/octet-stream", "binary/octet-stream") -> null
            ext == "m3u8" -> MediaFormat.HLS
            ext == "mpd" -> MediaFormat.DASH
            ext in setOf("mp4", "m4v", "webm") -> direct[ext]
            else -> null
        } ?: return null
        return DetectedMedia(candidate.url, format, binding.tabId, binding.contextId, binding.isPrivate)
    }

    /** Explicit URLs declared in a document, without playback or network confirmation. */
    fun fromPage(candidate: MediaDetectorMessage.Candidate, binding: MediaSessionBinding): DetectedMedia? {
        if (candidate.source != MediaSource.PAGE) return null
        val uri = parsed(candidate.url) ?: return null
        if (isSegment(candidate.url)) return null
        val mime = candidate.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        val ext = extension(file(uri))
        val format = when {
            mime in mimes -> mimes[mime]
            mime != null && mime !in setOf("application/octet-stream", "binary/octet-stream") -> null
            ext == "m3u8" -> MediaFormat.HLS
            ext == "mpd" -> MediaFormat.DASH
            else -> direct[ext]
        } ?: return null
        return DetectedMedia(candidate.url, format, binding.tabId, binding.contextId, binding.isPrivate)
    }

    fun fromNetwork(candidate: MediaDetectorMessage.Candidate, binding: MediaSessionBinding): DetectedMedia? {
        if (candidate.source != MediaSource.NETWORK) return null
        val uri = parsed(candidate.url) ?: return null
        if (isSegment(candidate.url)) return null
        val ext = extension(file(uri))
        val mime = candidate.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        val format = when {
            mime in mimes -> mimes[mime]
            mime != null && mime !in setOf("application/octet-stream", "binary/octet-stream") -> null
            ext == "m3u8" -> MediaFormat.HLS
            ext == "mpd" -> MediaFormat.DASH
            else -> direct[ext]
        } ?: return null
        return DetectedMedia(candidate.url, format, binding.tabId, binding.contextId, binding.isPrivate)
    }
}
