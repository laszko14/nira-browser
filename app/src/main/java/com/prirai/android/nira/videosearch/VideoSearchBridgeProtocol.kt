package com.prirai.android.nira.videosearch

import org.json.JSONObject

/**
 * Strict protocol for the separate video SEARCH extension, not the existing
 * media detector. Untrusted extension input never selects a tab or profile.
 * No query/title/cookie/credentials, and no logging of page URLs.
 */
internal sealed interface VideoSearchBridgeMessage {
    val documentId: String
    class Hello(override val documentId: String, val pageUrl: String) : VideoSearchBridgeMessage {
        override fun toString() = "VideoSearchBridgeMessage.Hello(redacted)"
    }
    class Pong(override val documentId: String) : VideoSearchBridgeMessage
}

internal object VideoSearchBridgeProtocol {
    const val SCHEMA_VERSION = 1
    const val EXTENSION_ID = "zavro-video-search@prirai.android.nira"
    const val NATIVE_APP = "zavro.video.search"
    const val EXTENSION_URL = "resource://android/assets/extensions/zavro-video-search/"
    private const val MAX_MESSAGE_CHARS = 9_000
    private val documentIdPattern = Regex("[0-9a-f]{32}")

    fun decode(message: Any?): VideoSearchBridgeMessage? {
        val json = message as? JSONObject ?: return null
        if (json.toString().length > MAX_MESSAGE_CHARS) return null
        val version = json.opt("schemaVersion") as? Number ?: return null
        if (version.toDouble() != SCHEMA_VERSION.toDouble()) return null
        val event = json.opt("event") as? String ?: return null
        val id = (json.opt("documentId") as? String)
            ?.takeIf { documentIdPattern.matches(it) } ?: return null
        val keys = buildSet {
            val it = json.keys()
            while (it.hasNext()) add(it.next())
        }
        return when (event) {
            "hello" -> {
                if (keys != setOf("schemaVersion", "event", "documentId", "pageUrl")) return null
                val url = json.opt("pageUrl") as? String ?: return null
                try { VideoSearchIntegrity.requirePageUrl(url) }
                catch (_: IllegalArgumentException) { return null }
                VideoSearchBridgeMessage.Hello(id, url)
            }
            "pong" -> if (keys == setOf("schemaVersion", "event", "documentId"))
                VideoSearchBridgeMessage.Pong(id) else null
            else -> null
        }
    }

    fun bound(documentId: String): JSONObject = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("event", "bound")
        put("documentId", documentId)
    }

    fun ping(documentId: String): JSONObject = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("event", "ping")
        put("documentId", documentId)
    }
}
