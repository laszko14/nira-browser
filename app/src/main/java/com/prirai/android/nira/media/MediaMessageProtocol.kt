package com.prirai.android.nira.media

import java.net.URI
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

internal sealed interface MediaDetectorMessage {
    class Candidate(val url: String, val contentType: String? = null,
                    val source: MediaSource = MediaSource.DOM) : MediaDetectorMessage {
        override fun toString() = "MediaCandidate(redacted)"
    }
    data object NetworkProbe : MediaDetectorMessage
    class NetworkUnassigned(val isPrivate: Boolean) : MediaDetectorMessage
    class NetworkSegments(val isPrivate: Boolean, val count: Int) : MediaDetectorMessage
}

internal object MediaMessageProtocol {
    private const val MAX_MESSAGE_BYTES = 16 * 1024
    private const val MAX_URL_BYTES = 8 * 1024
    private val validMime = Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+(?:\\s*;[ -~]*)?")

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
        return when (string("event")) {
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
            "media_candidate" -> {
                if (fields.keys != setOf("schemaVersion", "event", "source", "url") &&
                    fields.keys != setOf("schemaVersion", "event", "source", "url", "contentType")) return null
                val source = when (string("source")) {
                    "dom" -> MediaSource.DOM
                    "network" -> MediaSource.NETWORK
                    else -> return null
                }
                val contentType = if ("contentType" in fields) string("contentType") ?: return null else null
                if (contentType != null && (contentType.toByteArray(Charsets.UTF_8).size > 128 ||
                    !validMime.matches(contentType))) return null
                val url = string("url") ?: return null
                if (url.toByteArray(Charsets.UTF_8).size > MAX_URL_BYTES ||
                    url.any { it.isWhitespace() || it.isISOControl() }) return null
                val uri = try { URI(url) } catch (_: Exception) { return null }
                if (uri.scheme?.lowercase() !in setOf("https", "http") ||
                    uri.host.isNullOrEmpty() || uri.rawUserInfo != null) return null
                MediaDetectorMessage.Candidate(url, contentType, source)
            }
            else -> null
        }
    }
}
