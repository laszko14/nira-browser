package com.prirai.android.nira.media

import java.net.URI
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal sealed interface MediaDetectorMessage {
    class Candidate(val url: String, val contentType: String? = null) : MediaDetectorMessage {
        override fun toString() = "MediaCandidate(redacted)"
    }
    data object NetworkProbe : MediaDetectorMessage
}

internal object MediaMessageProtocol {
    private const val MAX_MESSAGE_BYTES = 16 * 1024
    private const val MAX_URL_BYTES = 8 * 1024

    fun decode(raw: String): MediaDetectorMessage? {
        if (raw.length > MAX_MESSAGE_BYTES || raw.toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_BYTES) return null
        // Only a flat object of primitives is part of v1. Bound depth before invoking the JSON parser.
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
        }
        val fields = try {
            Json.parseToJsonElement(raw) as? JsonObject ?: return null
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        val version = fields["schemaVersion"] as? JsonPrimitive ?: return null
        if (version.isString || version.content != "1") return null
        fun string(key: String): String? = (fields[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return when (string("event")) {
            "network_probe" -> if (fields.keys == setOf("schemaVersion", "event")) MediaDetectorMessage.NetworkProbe else null
            "media_candidate" -> {
                if (fields.keys != setOf("schemaVersion", "event", "source", "url") &&
                    fields.keys != setOf("schemaVersion", "event", "source", "url", "contentType")) return null
                if (string("source") != "dom") return null
                val contentType = if ("contentType" in fields) string("contentType") ?: return null else null
                if (contentType != null && (contentType.toByteArray(Charsets.UTF_8).size > 128 ||
                    !contentType.matches(Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+(?:\\s*;[ -~]*)?")))) return null
                val url = string("url") ?: return null
                if (url.toByteArray(Charsets.UTF_8).size > MAX_URL_BYTES || url.any { it.isWhitespace() || it.isISOControl() }) return null
                val uri = try { URI(url) } catch (_: IllegalArgumentException) { return null }
                    catch (_: java.net.URISyntaxException) { return null }
                if (uri.scheme?.lowercase() !in setOf("https", "http") || uri.host.isNullOrEmpty() || uri.rawUserInfo != null) return null
                MediaDetectorMessage.Candidate(url, contentType)
            }
            else -> null
        }
    }
}
