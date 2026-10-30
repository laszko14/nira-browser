package com.prirai.android.nira.videosearch

import org.json.JSONObject

/**
 * Schema 1, strict transport-only protocol. No profile/tab IDs arrive from JS.
 * Input fields and document URLs never enter logs/toString.
 */
internal sealed interface VideoSearchBridgeMessage {
    val documentId: String
    class Hello(
        override val documentId: String, val pageUrl: String,
        val navigationRevision: Int = 0,
    ) : VideoSearchBridgeMessage {
        override fun toString() = "VideoSearchBridgeMessage.Hello(redacted)"
    }
    class Pong(override val documentId: String) : VideoSearchBridgeMessage
    class Inspection(
        override val documentId: String,
        val requestId: String,
        val status: String,
        val inputSelector: String?,
        val resultsCardSelector: String?,
        val submission: VideoSearchSubmission?,
        val submitSelector: String?,
        val pagination: VideoSearchPagination,
        val nextPageSelector: String?,
    ) : VideoSearchBridgeMessage {
        override fun toString() = "VideoSearchBridgeMessage.Inspection(redacted)"
    }
    class Submitted(override val documentId: String, val requestId: String) : VideoSearchBridgeMessage
    class SearchFailed(
        override val documentId: String,
        val requestId: String,
        val reason: String,
    ) : VideoSearchBridgeMessage

    class Picked(
        override val documentId: String,
        val requestId: String,
        val pickKind: String,
        val selector: String,
        val formSafe: Boolean,
        val pagination: VideoSearchPagination,
    ) : VideoSearchBridgeMessage {
        override fun toString() = "VideoSearchBridgeMessage.Picked(redacted)"
    }
    class PickSkipped(
        override val documentId: String,
        val requestId: String,
        val pickKind: String,
    ) : VideoSearchBridgeMessage
    class PickCancelled(
        override val documentId: String,
        val requestId: String,
        val pickKind: String,
    ) : VideoSearchBridgeMessage

    class Results(
        override val documentId: String,
        val requestId: String,
        val page: VideoSearchExtractedPage,
        val sourceEvent: String = "results",
    ) : VideoSearchBridgeMessage {
        override fun toString() = "VideoSearchBridgeMessage.Results(redacted)"
    }

    class PageAction(
        override val documentId: String,
        val requestId: String,
        val pagination: VideoSearchPagination,
        val status: String,
    ) : VideoSearchBridgeMessage {
        override fun toString() = "VideoSearchBridgeMessage.PageAction(redacted)"
    }

    /** Redacted, bounded assessment of the currently open site's selectors. */
    class Validation(
        override val documentId: String,
        val requestId: String,
        val status: String,
        val reason: String,
        val attempts: Int,
        val matchedCards: Int,
    ) : VideoSearchBridgeMessage {
        override fun toString() = "VideoSearchBridgeMessage.Validation(redacted)"
    }
}

internal object VideoSearchBridgeProtocol {
    const val SCHEMA_VERSION = 1
    const val EXTENSION_ID = "zavro-video-search@prirai.android.nira"
    const val NATIVE_APP = "zavro.video.search"
    const val EXTENSION_URL = "resource://android/assets/extensions/zavro-video-search/"
    private const val MAX_MESSAGE_CHARS = 9_000
    private val token = Regex("[0-9a-f]{32}")
    val pickKinds = setOf("input", "submit", "cards", "next")
    private val selectorChars = "#-_.[]=\\\"' >:*+\\\\"

    /** Manual picking may happen on the same site's search-results path. */
    fun sameOrigin(a: String, b: String): Boolean = try {
        VideoSearchIntegrity.requirePageUrl(a)
        VideoSearchIntegrity.requirePageUrl(b)
        val x = java.net.URI(a)
        val y = java.net.URI(b)
        fun port(value: java.net.URI): Int = if (value.port != -1) value.port
            else if (value.scheme.equals("https", true)) 443 else 80
        x.scheme.equals(y.scheme, true) &&
            x.host.equals(y.host, true) && port(x) == port(y)
    } catch (_: IllegalArgumentException) { false } catch (_: java.net.URISyntaxException) { false }

    fun validToken(value: String) = token.matches(value)
    fun validQuery(value: String) =
        value.isNotBlank() && value.length <= 250 &&
            value.none { it.isISOControl() }

    fun validSelector(value: String?): Boolean =
        value != null && value.length in 1..160 &&
            value.all { it.isLetterOrDigit() || it in selectorChars } &&
            !value.contains(":has(") && !value.contains(":not(")

    private fun JSONObject.strict(vararg names: String): Boolean {
        val actual = mutableSetOf<String>()
        val iter = keys()
        while (iter.hasNext()) actual.add(iter.next())
        return actual == names.toSet()
    }

    private fun JSONObject.string(key: String): String? = opt(key) as? String
    private fun JSONObject.optSelector(key: String): String? {
        val value = opt(key)
        return if (value == JSONObject.NULL) null
        else (value as? String)?.takeIf(::validSelector)
    }

    fun decode(message: Any?): VideoSearchBridgeMessage? {
        val json = message as? JSONObject ?: return null
        if (json.toString().length > MAX_MESSAGE_CHARS) return null
        val version = json.opt("schemaVersion") as? Number ?: return null
        if (version.toDouble() != 1.0) return null
        val event = json.string("event") ?: return null
        val id = json.string("documentId")?.takeIf(::validToken) ?: return null
        return when (event) {
            "hello" -> {
                val versioned = json.strict("schemaVersion", "event", "documentId",
                    "pageUrl", "navigationRevision")
                if (!versioned &&
                    !json.strict("schemaVersion", "event", "documentId", "pageUrl"))
                    return null
                val revision = if (versioned)
                    (json.opt("navigationRevision") as? Int)?.takeIf { it in 0..1_000_000 }
                        ?: return null
                    else 0
                val url = json.string("pageUrl") ?: return null
                try { VideoSearchIntegrity.requirePageUrl(url) }
                catch (_: IllegalArgumentException) { return null }
                VideoSearchBridgeMessage.Hello(id, url, revision)
            }
            "pong" -> if (json.strict("schemaVersion", "event", "documentId"))
                VideoSearchBridgeMessage.Pong(id) else null
            "submitted" -> {
                if (!json.strict("schemaVersion", "event", "documentId", "requestId")) return null
                val request = json.string("requestId")?.takeIf(::validToken) ?: return null
                VideoSearchBridgeMessage.Submitted(id, request)
            }
            "search_failed" -> {
                if (!json.strict("schemaVersion", "event", "documentId", "requestId", "reason")) return null
                val request = json.string("requestId")?.takeIf(::validToken) ?: return null
                val reason = json.string("reason")?.takeIf {
                    it in setOf("stale", "unsafe", "rejected")
                } ?: return null
                VideoSearchBridgeMessage.SearchFailed(id, request, reason)
            }
            "picked" -> {
                if (!json.strict("schemaVersion", "event", "documentId", "requestId",
                        "pickKind", "selector", "formSafe", "pagination")) return null
                val request = json.string("requestId")?.takeIf(::validToken) ?: return null
                val kind = json.string("pickKind")?.takeIf { it in pickKinds } ?: return null
                val selector = json.string("selector")?.takeIf(::validSelector) ?: return null
                val formSafe = json.opt("formSafe") as? Boolean ?: return null
                val pagination = when (json.string("pagination")) {
                    "NONE" -> VideoSearchPagination.NONE
                    "NEXT_LINK" -> VideoSearchPagination.NEXT_LINK
                    "LOAD_MORE" -> VideoSearchPagination.LOAD_MORE
                    "INFINITE_SCROLL" -> VideoSearchPagination.INFINITE_SCROLL
                    else -> return null
                }
                if (pagination == VideoSearchPagination.INFINITE_SCROLL &&
                    (kind != "next" || selector != "html")) return null
                if (kind != "input" && formSafe) return null
                if (kind != "next" && pagination != VideoSearchPagination.NONE) return null
                if (kind == "next" && pagination == VideoSearchPagination.NONE) return null
                VideoSearchBridgeMessage.Picked(id, request, kind, selector, formSafe, pagination)
            }
            "pick_skipped", "pick_cancelled" -> {
                if (!json.strict("schemaVersion", "event", "documentId", "requestId",
                        "pickKind")) return null
                val request = json.string("requestId")?.takeIf(::validToken) ?: return null
                val kind = json.string("pickKind")?.takeIf { it in pickKinds } ?: return null
                if (event == "pick_skipped") {
                    if (kind == "input") return null
                    VideoSearchBridgeMessage.PickSkipped(id, request, kind)
                } else VideoSearchBridgeMessage.PickCancelled(id, request, kind)
            }
            "validation" -> {
                if (!json.strict("schemaVersion", "event", "documentId", "requestId",
                        "status", "reason", "attempts", "matchedCards")) return null
                val request = json.string("requestId")?.takeIf(::validToken) ?: return null
                val status = json.string("status")?.takeIf {
                    it in setOf("valid", "unverified", "stale", "unsafe")
                } ?: return null
                val reason = json.string("reason")?.takeIf {
                    it in setOf("ok", "no_results", "pagination_unknown", "input_missing",
                        "input_ambiguous", "invalid_selector", "unsafe_form",
                        "submit_missing", "cards_invalid")
                } ?: return null
                val attempts = (json.opt("attempts") as? Int)?.takeIf { it in 1..3 }
                    ?: return null
                val cards = (json.opt("matchedCards") as? Int)?.takeIf { it in 0..30 }
                    ?: return null
                if ((status == "valid" && (reason != "ok" || cards == 0)) ||
                    (status == "unverified" && reason !in setOf("no_results", "pagination_unknown")) ||
                    (status == "stale" && reason !in setOf("input_missing", "input_ambiguous",
                            "submit_missing", "invalid_selector", "cards_invalid")) ||
                    (status == "unsafe" && reason != "unsafe_form")) return null
                VideoSearchBridgeMessage.Validation(id, request, status, reason, attempts, cards)
            }
            "page_action" -> {
                if (!json.strict("schemaVersion", "event", "documentId", "requestId",
                        "pagination", "status")) return null
                val req = json.string("requestId")?.takeIf(::validToken) ?: return null
                val pagination = when (json.string("pagination")) {
                    "NEXT_LINK" -> VideoSearchPagination.NEXT_LINK
                    "LOAD_MORE" -> VideoSearchPagination.LOAD_MORE
                    "INFINITE_SCROLL" -> VideoSearchPagination.INFINITE_SCROLL
                    else -> return null
                }
                val status = json.string("status")?.takeIf {
                    it in setOf("dispatched", "pending", "end", "unavailable", "unsafe")
                } ?: return null
                VideoSearchBridgeMessage.PageAction(id, req, pagination, status)
            }
            "results", "crawl_results" -> {
                if (!json.strict("schemaVersion", "event", "documentId", "requestId",
                        "status", "films", "inspectedCards", "moreOnPage")) return null
                val request = json.string("requestId")?.takeIf(::validToken) ?: return null
                val status = json.string("status")?.takeIf {
                    it in setOf("ok", "empty", "no_results", "stale")
                } ?: return null
                val raw = json.opt("films") as? org.json.JSONArray ?: return null
                if (raw.length() > 16) return null
                val count = (json.opt("inspectedCards") as? Int)
                    ?.takeIf { it in 0..80 } ?: return null
                val more = json.opt("moreOnPage") as? Boolean ?: return null
                if (status == "no_results" && (raw.length() != 0 || count != 0 || more))
                    return null
                val items = ArrayList<VideoSearchExtractedFilm>(raw.length())
                for (i in 0 until raw.length()) {
                    val row = raw.optJSONObject(i) ?: return null
                    if (!row.strict("filmPageUrl", "originalTitle", "thumbnailUrl",
                            "durationSeconds", "titleSource", "imageSource",
                            "originalIndex")) return null
                    val pageUrl = row.string("filmPageUrl")
                        ?.takeIf { it.length <= 2048 } ?: return null
                    val title = when (val value = row.opt("originalTitle")) {
                        JSONObject.NULL -> null
                        is String -> value.takeIf { it.isNotBlank() && it.length <= 512 }
                            ?: return null
                        else -> return null
                    }
                    val thumbnail = when (val value = row.opt("thumbnailUrl")) {
                        JSONObject.NULL -> null
                        is String -> value.takeIf { it.isNotBlank() && it.length <= 800 }
                            ?: return null
                        else -> return null
                    }
                    val duration = when (val value = row.opt("durationSeconds")) {
                        JSONObject.NULL -> null
                        is Number -> value.toLong().takeIf {
                            it.toDouble() == value.toDouble() && it in 0..86_400L
                        } ?: return null
                        else -> return null
                    }
                    val titleSource = when (row.string("titleSource")) {
                        "CARD" -> VideoResultTitleSource.CARD
                        "STRUCTURED" -> VideoResultTitleSource.STRUCTURED
                        "LINK" -> VideoResultTitleSource.LINK
                        "UNKNOWN" -> VideoResultTitleSource.UNKNOWN
                        else -> return null
                    }
                    val imageSource = when (row.string("imageSource")) {
                        "CARD" -> VideoResultImageSource.CARD
                        "STRUCTURED" -> VideoResultImageSource.STRUCTURED
                        "UNKNOWN" -> VideoResultImageSource.UNKNOWN
                        else -> return null
                    }
                    val index = (row.opt("originalIndex") as? Int)
                        ?.takeIf { it in 0..(if (event == "crawl_results") 319 else 200) }
                        ?: return null
                    val film = try {
                        VideoSearchExtractedFilm(pageUrl, title, thumbnail, duration,
                            titleSource, imageSource, index)
                    } catch (_: IllegalArgumentException) { return null }
                    items.add(film)
                }
                val page = try {
                    VideoSearchExtractedPage(status, items, count, more)
                } catch (_: IllegalArgumentException) { return null }
                VideoSearchBridgeMessage.Results(id, request, page, event)
            }
            "inspection" -> {
                val request = json.string("requestId")?.takeIf(::validToken) ?: return null
                val status = json.string("status")?.takeIf { it in setOf("ready", "partial", "missing") }
                    ?: return null
                val base = setOf("schemaVersion", "event", "documentId", "requestId", "status")
                val fields = setOf("inputSelector", "resultsCardSelector", "submission",
                    "submitSelector", "pagination", "nextPageSelector")
                val actual = mutableSetOf<String>()
                val keys = json.keys()
                while (keys.hasNext()) actual.add(keys.next())
                if (status == "missing" || actual == base) {
                    if (actual != base || status == "ready") return null
                    return VideoSearchBridgeMessage.Inspection(
                        id, request, status, null, null, null, null,
                        VideoSearchPagination.NONE, null
                    )
                }
                if (actual != base + fields) return null
                val input = json.optSelector("inputSelector") ?: return null
                val card = json.optSelector("resultsCardSelector")
                if (json.opt("resultsCardSelector") != JSONObject.NULL && card == null) return null
                val submit = json.optSelector("submitSelector")
                if (json.opt("submitSelector") != JSONObject.NULL && submit == null) return null
                val method = when (json.string("submission")) {
                    "CLICK_SUBMIT" -> VideoSearchSubmission.CLICK_SUBMIT
                    "FORM_SUBMIT" -> VideoSearchSubmission.FORM_SUBMIT
                    else -> return null
                }
                if (method == VideoSearchSubmission.CLICK_SUBMIT && submit == null) return null
                val pagination = when (json.string("pagination")) {
                    "NONE" -> VideoSearchPagination.NONE
                    "NEXT_LINK" -> VideoSearchPagination.NEXT_LINK
                    "LOAD_MORE" -> VideoSearchPagination.LOAD_MORE
                    else -> return null
                }
                val next = json.optSelector("nextPageSelector")
                if (json.opt("nextPageSelector") != JSONObject.NULL && next == null) return null
                if (pagination != VideoSearchPagination.NONE && next == null) return null
                if (pagination == VideoSearchPagination.NONE && next != null) return null
                if (status == "ready" && card == null) return null
                VideoSearchBridgeMessage.Inspection(
                    id, request, status, input, card, method, submit, pagination, next
                )
            }
            else -> null
        }
    }

    private fun base(event: String, documentId: String, requestId: String? = null) =
        JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("event", event)
            put("documentId", documentId)
            if (requestId != null) put("requestId", requestId)
        }

    fun bound(documentId: String): JSONObject = base("bound", documentId)
    fun ping(documentId: String): JSONObject = base("ping", documentId)

    /** Sent only to the existing Gecko port when BrowserStore records a
     * same-origin SPA URL transition; no page-supplied profile/URL metadata. */
    fun syncUrl(): JSONObject = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("event", "url_sync")
    }
    fun inspect(documentId: String, requestId: String): JSONObject =
        base("inspect", documentId, requestId)

    fun pick(documentId: String, requestId: String, kind: String): JSONObject {
        require(kind in pickKinds)
        return base("pick", documentId, requestId).apply { put("pickKind", kind) }
    }

    /** Read-only inspection; values are selectors, never executable source. */
    fun validate(documentId: String, requestId: String,
                 config: VideoSiteConfiguration): JSONObject {
        require(validSelector(config.searchInputSelector))
        require(config.resultsCardSelector == null ||
            validSelector(config.resultsCardSelector))
        require(config.submission in setOf(
            VideoSearchSubmission.CLICK_SUBMIT, VideoSearchSubmission.FORM_SUBMIT))
        if (config.submission == VideoSearchSubmission.CLICK_SUBMIT)
            require(validSelector(config.submitSelector))
        if (config.pagination == VideoSearchPagination.INFINITE_SCROLL)
            require(config.nextPageSelector == null)
        else if (config.pagination != VideoSearchPagination.NONE)
            require(validSelector(config.nextPageSelector))
        return base("validate", documentId, requestId).apply {
            put("inputSelector", config.searchInputSelector)
            put("resultsCardSelector", config.resultsCardSelector ?: JSONObject.NULL)
            put("submission", config.submission.name)
            put("submitSelector", config.submitSelector ?: JSONObject.NULL)
            put("pagination", config.pagination.name)
            put("nextPageSelector", config.nextPageSelector ?: JSONObject.NULL)
        }
    }

    /** No query or page URL is needed in an extraction request. */
    fun extract(documentId: String, requestId: String,
                cardSelector: String): JSONObject {
        require(validSelector(cardSelector))
        return base("extract", documentId, requestId).apply {
            put("resultsCardSelector", cardSelector)
        }
    }

    /** Stage 4B: selectors are scoped inside each individual film card. */
    fun extract(documentId: String, requestId: String,
                config: VideoSiteConfiguration): JSONObject {
        require(validSelector(config.resultsCardSelector))
        val optional = listOf(config.titleSelector, config.linkSelector,
            config.thumbnailSelector, config.durationSelector)
        require(optional.all { it == null || validSelector(it) })
        return base("extract", documentId, requestId).apply {
            put("resultsCardSelector", config.resultsCardSelector)
            put("titleSelector", config.titleSelector ?: JSONObject.NULL)
            put("linkSelector", config.linkSelector ?: JSONObject.NULL)
            put("thumbnailSelector", config.thumbnailSelector ?: JSONObject.NULL)
            put("durationSelector", config.durationSelector ?: JSONObject.NULL)
        }
    }

    /**
     * Continuation is an absolute CARD offset, not a number of films.
     * It is scoped to the current native EngineSession/documentId by the
     * existing pending-request validation and never navigates a page.
     */
    fun extractBatch(
        documentId: String, requestId: String,
        config: VideoSiteConfiguration, startIndex: Int,
    ): JSONObject {
        require(startIndex in 0..79) // shorter packets may split at any card
        return extract(documentId, requestId, config).apply {
            put("event", "extract_batch")
            put("startIndex", startIndex)
        }
    }

    /** A 16-card window in the same DOM, max 320 card positions. */
    fun extractCrawl(documentId: String, requestId: String,
                     config: VideoSiteConfiguration, startIndex: Int): JSONObject {
        require(startIndex in 0..319)
        return extract(documentId, requestId, config).apply {
            put("event", "extract_crawl")
            put("startIndex", startIndex)
        }
    }

    /** User explicitly requested one safe next-page action. */
    fun paginate(documentId: String, requestId: String,
                 config: VideoSiteConfiguration): JSONObject {
        require(config.pagination != VideoSearchPagination.NONE)
        if (config.pagination == VideoSearchPagination.INFINITE_SCROLL) {
            require(config.nextPageSelector == null)
        } else {
            require(validSelector(config.nextPageSelector))
        }
        return base("paginate", documentId, requestId).apply {
            put("pagination", config.pagination.name)
            put("nextPageSelector", config.nextPageSelector ?: JSONObject.NULL)
        }
    }

    /** Query is sent ONLY to the bound content script, never to site APIs directly. */
    fun search(documentId: String, requestId: String, query: String,
               inputSelector: String, submission: VideoSearchSubmission,
               submitSelector: String?): JSONObject {
        require(validQuery(query) && validSelector(inputSelector))
        require(submission in setOf(
            VideoSearchSubmission.CLICK_SUBMIT, VideoSearchSubmission.FORM_SUBMIT))
        if (submission == VideoSearchSubmission.CLICK_SUBMIT)
            require(validSelector(submitSelector))
        return base("search", documentId, requestId).apply {
            put("query", query)
            put("inputSelector", inputSelector)
            put("submission", submission.name)
            put("submitSelector", submitSelector ?: JSONObject.NULL)
        }
    }
}
