package com.prirai.android.nira.media

import java.net.URI
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal sealed interface MediaDetectorMessage {
    class Candidate(val url: String, val contentType: String? = null,
                    val source: MediaSource = MediaSource.DOM,
                    val privateHint: Boolean? = null,
                    val pageUrl: String? = null,
                    val documentKey: Long? = null,
                    val frameUrl: String? = null,
                    val frameKey: Long? = null,
                    val groupId: String? = null,
                    val title: String? = null,
                    val titleKind: String? = null,
                    val durationSeconds: Int? = null,
                    val sizeBytes: Long? = null,
                    val posterUrl: String? = null,
                    val quality: String? = null,
                    val bitrate: Long? = null,
                    val codecs: String? = null,
                    val provenance: String? = null,
                    val verification: String? = null,
                    val httpStatus: Int? = null,
                    val requestSequence: Long? = null) : MediaDetectorMessage {
        override fun toString() = "MediaCandidate(redacted)"
    }
    class Document(val pageUrl: String, val documentKey: Long, val frameTracking: Boolean = false) : MediaDetectorMessage {
        override fun toString() = "MediaDocument(redacted)"
    }
    class Frame(val pageUrl: String, val documentKey: Long, val frameUrl: String,
                val frameId: Long, val frameKey: Long, val active: Boolean) : MediaDetectorMessage {
        override fun toString() = "MediaFrame(redacted)"
    }
    class DiscoveryStat(val code: String, val count: Int, val documentKey: Long?,
                        val isPrivate: Boolean?) : MediaDetectorMessage
    data object NetworkProbe : MediaDetectorMessage
    class NetworkUnassigned(val isPrivate: Boolean) : MediaDetectorMessage
    class NetworkSegments(val isPrivate: Boolean, val count: Int) : MediaDetectorMessage
}

internal object MediaMessageProtocol {
    internal val discoveryCodes = setOf("scan_elements", "scan_yield", "queue_limit",
        "duplicate", "native_rejected", "before_play", "after_play", "iframe",
        "no_source", "no_candidate", "manifest_invalid", "manifest_valid",
        "frame_mismatch", "unavailable", "drm", "early_request_seen", "early_request_bound",
        "early_request_expired", "early_request_ambiguous", "frame_registered",
        "iframe_about_blank", "iframe_srcdoc", "spa_navigation", "player_config_detected",
        "early_uuid_mismatch", "early_url_context_match", "early_url_mismatch",
        "early_parent_mismatch", "early_partition_mismatch", "early_after_registration",
        "title_individual", "title_structured", "title_unknown")
    private const val MAX_MESSAGE_BYTES = 16 * 1024
    private const val MAX_URL_BYTES = 8 * 1024
    private val validMime = Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+(?:\\s*;[ -~]*)?")
    private fun validTitle(value: String): Boolean = value.isNotBlank() &&
        value.length <= 300 && value.toByteArray(Charsets.UTF_8).size <= 900 &&
        value.none { it.isISOControl() }

    private fun validUrl(url: String): Boolean {
        if (url.toByteArray(Charsets.UTF_8).size > MAX_URL_BYTES ||
            url.any { it.isWhitespace() || it.isISOControl() }) return false
        val uri = try { URI(url) } catch (_: Exception) { return false }
        return uri.scheme?.lowercase() in setOf("https", "http") &&
            !uri.host.isNullOrEmpty() && uri.rawUserInfo == null
    }

    fun decode(raw: String): MediaDetectorMessage? {
        if (raw.length > MAX_MESSAGE_BYTES || raw.toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_BYTES) return null
        var quoted = false
        var escaped = false
        var depth = 0
        for (character in raw) {
            if (quoted) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') quoted = false
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> if (++depth > 1) return null
                '}', ']' -> depth--
            }
            if (depth < 0) return null
        }
        if (quoted || depth != 0) return null
        val fields = try {
            Json.parseToJsonElement(raw) as? JsonObject ?: return null
        } catch (_: SerializationException) { return null }
          catch (_: IllegalArgumentException) { return null }
        val version = fields["schemaVersion"] as? JsonPrimitive ?: return null
        if (version.isString || version.content != "1") return null
        fun string(key: String): String? = (fields[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        fun privacy(): Boolean? = (fields["isPrivate"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
        fun documentKey(): Long? = (fields["documentKey"] as? JsonPrimitive)
            ?.takeIf { !it.isString }?.longOrNull?.takeIf { it in 1..9007199254740991L }
        return when (string("event")) {
            "discovery_stats" -> {
                val bound = "documentKey" in fields
                if (fields.keys != setOf("schemaVersion", "event", "code", "count",
                        if (bound) "documentKey" else "isPrivate")) return null
                val code = string("code")?.takeIf { it in discoveryCodes } ?: return null
                val count = (fields["count"] as? JsonPrimitive)?.takeIf { !it.isString }
                    ?.intOrNull?.takeIf { it in 1..100000 } ?: return null
                val key = if (bound) documentKey() ?: return null else null
                val privateMode = if (!bound) privacy() ?: return null else null
                MediaDetectorMessage.DiscoveryStat(code, count, key, privateMode)
            }
            "network_probe" -> if (fields.keys == setOf("schemaVersion", "event"))
                MediaDetectorMessage.NetworkProbe else null
            "network_unassigned" -> if (fields.keys == setOf("schemaVersion", "event", "isPrivate"))
                privacy()?.let { MediaDetectorMessage.NetworkUnassigned(it) } else null
            "network_segments" -> {
                if (fields.keys != setOf("schemaVersion", "event", "isPrivate", "count")) return null
                val count = (fields["count"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
                if (count == null || count !in 1..100000) return null
                privacy()?.let { MediaDetectorMessage.NetworkSegments(it, count) }
            }
            "media_document" -> {
                val tracking = "frameTracking" in fields
                if (fields.keys != setOf("schemaVersion", "event", "pageUrl", "documentKey") +
                        (if (tracking) setOf("frameTracking") else emptySet())) return null
                val page = string("pageUrl")?.takeIf(::validUrl) ?: return null
                val flag = if (tracking) (fields["frameTracking"] as? JsonPrimitive)
                    ?.takeIf { !it.isString }?.booleanOrNull ?: return null else false
                MediaDetectorMessage.Document(page, documentKey() ?: return null, flag)
            }
            "media_frame" -> {
                if (fields.keys != setOf("schemaVersion", "event", "pageUrl", "documentKey",
                        "frameUrl", "frameId", "frameKey", "active")) return null
                val page = string("pageUrl")?.takeIf(::validUrl) ?: return null
                val frame = string("frameUrl")?.takeIf { validUrl(it) || it == "about:blank" || it == "about:srcdoc" } ?: return null
                fun positive(name: String) = (fields[name] as? JsonPrimitive)?.takeIf { !it.isString }
                    ?.longOrNull?.takeIf { it in 1..9007199254740991L }
                val active = (fields["active"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: return null
                MediaDetectorMessage.Frame(page, documentKey() ?: return null, frame,
                    positive("frameId") ?: return null, positive("frameKey") ?: return null, active)
            }
            "media_candidate" -> {
                val expected = setOf("schemaVersion", "event", "source", "url")
                val hasMime = "contentType" in fields
                val fromNetwork = string("source") == "network"
                val hasDoc = "documentKey" in fields || "pageUrl" in fields
                val hasFrame = "frameUrl" in fields || "frameKey" in fields
                val hasGroup = "groupId" in fields
                val hasTitle = "title" in fields || "titleKind" in fields
                val hasDuration = "durationSeconds" in fields
                val hasSize = "sizeBytes" in fields
                val hasPoster = "posterUrl" in fields
                val hasQuality = "quality" in fields
                val hasBitrate = "bitrate" in fields
                val hasCodecs = "codecs" in fields
                val hasProvenance = "provenance" in fields
                val hasVerification = "verification" in fields
                val hasHttpStatus = "httpStatus" in fields
                val hasRequestSequence = "requestSequence" in fields
                if (hasProvenance && !fromNetwork) return null
                if ((hasVerification || hasHttpStatus || hasRequestSequence) && !fromNetwork) return null
                if (hasDoc && !("documentKey" in fields && "pageUrl" in fields)) return null
                if (hasFrame && (!hasDoc ||
                        !("frameUrl" in fields && "frameKey" in fields))) return null
                if (hasTitle && !("title" in fields && "titleKind" in fields)) return null
                if (hasDuration && fromNetwork || hasSize && !fromNetwork ||
                    hasPoster && fromNetwork) return null
                val acceptedKeys = expected + (if (hasMime) setOf("contentType") else emptySet()) +
                    (if (fromNetwork) setOf("isPrivate") else emptySet()) +
                    (if (hasDoc) setOf("documentKey", "pageUrl") else emptySet()) +
                    (if (hasFrame) setOf("frameUrl", "frameKey") else emptySet()) +
                    (if (hasGroup) setOf("groupId") else emptySet()) +
                    (if (hasTitle) setOf("title", "titleKind") else emptySet()) +
                    (if (hasDuration) setOf("durationSeconds") else emptySet()) +
                    (if (hasSize) setOf("sizeBytes") else emptySet()) +
                    (if (hasPoster) setOf("posterUrl") else emptySet()) +
                    (if (hasQuality) setOf("quality") else emptySet()) +
                    (if (hasBitrate) setOf("bitrate") else emptySet()) +
                    (if (hasCodecs) setOf("codecs") else emptySet()) +
                    (if (hasProvenance) setOf("provenance") else emptySet()) +
                    (if (hasVerification) setOf("verification") else emptySet()) +
                    (if (hasHttpStatus) setOf("httpStatus") else emptySet()) +
                    (if (hasRequestSequence) setOf("requestSequence") else emptySet())
                if (fields.keys != acceptedKeys) return null
                val source = when (string("source")) {
                    "dom" -> MediaSource.DOM
                    "page" -> MediaSource.PAGE
                    "network" -> MediaSource.NETWORK
                    else -> return null
                }
                val contentType = if (hasMime) string("contentType") ?: return null else null
                if (contentType != null && (contentType.toByteArray(Charsets.UTF_8).size > 128 ||
                    !validMime.matches(contentType))) return null
                val url = string("url")?.takeIf(::validUrl) ?: return null
                val pageUrl = if (hasDoc) string("pageUrl")?.takeIf(::validUrl) ?: return null else null
                val key = if (hasDoc) documentKey() ?: return null else null
                val privateHint = if (source == MediaSource.NETWORK) privacy() ?: return null else null
                val frameUrl = if (hasFrame) string("frameUrl")?.takeIf {
                    validUrl(it) || it == "about:blank" || it == "about:srcdoc"
                } ?: return null else null
                val frameKey = if (hasFrame)
                    (fields["frameKey"] as? JsonPrimitive)?.takeIf { !it.isString }
                        ?.longOrNull?.takeIf { it in 1..9007199254740991L } ?: return null
                    else null
                val groupId = if (hasGroup) string("groupId")?.takeIf {
                    it.length in 1..80 && it.matches(Regex("[A-Za-z0-9_-]+"))
                } ?: return null else null
                val title = if (hasTitle)
                    string("title")?.takeIf(::validTitle) ?: return null
                    else null
                val titleKind = if (hasTitle)
                    string("titleKind")?.takeIf {
                        it in setOf("player", "structured", "video", "link", "page") &&
                            (source != MediaSource.NETWORK || it == "page")
                    } ?: return null else null
                val duration = if (hasDuration)
                    (fields["durationSeconds"] as? JsonPrimitive)?.takeIf { !it.isString }
                        ?.intOrNull?.takeIf { it in 1..604800 } ?: return null
                    else null
                val size = if (hasSize)
                    (fields["sizeBytes"] as? JsonPrimitive)?.takeIf { !it.isString }
                        ?.longOrNull?.takeIf { it in 1..1000000000000L } ?: return null
                    else null
                val poster = if (hasPoster) string("posterUrl")?.takeIf(::validUrl)
                    ?: return null else null
                val quality = if (hasQuality) string("quality")?.takeIf {
                    it.matches(Regex("[0-9]{2,4}p"))
                } ?: return null else null
                val bitrate = if (hasBitrate)
                    (fields["bitrate"] as? JsonPrimitive)?.takeIf { !it.isString }
                        ?.longOrNull?.takeIf { it in 1..1_000_000_000L } ?: return null
                    else null
                val codecs = if (hasCodecs) string("codecs")?.takeIf {
                    it.length in 1..160 && it.none(Char::isISOControl)
                } ?: return null else null
                val provenance = if (hasProvenance) string("provenance")?.takeIf {
                    it == "observed" || it == "derived"
                } ?: return null else null
                val verification = if (hasVerification) string("verification")?.takeIf {
                    it in setOf("manifest_observed", "manifest_verified", "stream_verified",
                        "expired", "auth_error", "unplayable")
                } ?: return null else null
                val httpStatus = if (hasHttpStatus)
                    (fields["httpStatus"] as? JsonPrimitive)?.takeIf { !it.isString }
                        ?.intOrNull?.takeIf { it in 100..599 } ?: return null
                    else null
                val requestSequence = if (hasRequestSequence)
                    (fields["requestSequence"] as? JsonPrimitive)?.takeIf { !it.isString }
                        ?.longOrNull?.takeIf { it in 1..9007199254740991L } ?: return null
                    else null
                MediaDetectorMessage.Candidate(url, contentType, source, privateHint, pageUrl, key,
                    frameUrl, frameKey, groupId, title, titleKind, duration, size, poster,
                    quality, bitrate, codecs, provenance, verification, httpStatus, requestSequence)
            }
            else -> null
        }
    }
}
