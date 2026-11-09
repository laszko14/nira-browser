package com.prirai.android.nira.videosearch

import org.json.JSONObject

/**
 * Isolated diagnostics wire v1. Never route these messages through normal
 * search result completion. A JS-supplied owner, tab, site or run is invalid.
 */
internal object VideoSearchDiagnosticWire {
    private val nonce = Regex("[0-9a-f]{32}")
    private val session = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private fun JSONObject.exact(vararg fields: String): Boolean {
        val keys = mutableSetOf<String>()
        val iterator = this.keys()
        while (iterator.hasNext()) keys.add(iterator.next())
        return keys == fields.toSet()
    }
    private fun JSONObject.text(name: String) = opt(name) as? String
    private fun JSONObject.number(name: String, max: Long): Long? {
        val n = opt(name) as? Number ?: return null
        val value = n.toLong()
        return if (value >= 0 && value <= max && n.toDouble() == value.toDouble()) value
            else null
    }
    private fun JSONObject.keysValid(names: Set<String>): Boolean {
        val it = keys()
        while (it.hasNext()) if (it.next() !in names) return false
        return true
    }
    private val counts = VideoSearchDiagnosticCount.values().associateBy { it.name }
    private val fields = VideoSearchDiagnosticField.values().associateBy { it.name }
    private val stages = VideoSearchDiagnosticStage.values().associateBy { it.name }
    private val decisions = VideoSearchDiagnosticDecision.values().associateBy { it.name }
    private const val MAX_PACKET = 9_000

    internal sealed interface Reply {
        val documentId: String
        val requestId: String
        data class Ack(
            override val documentId: String, override val requestId: String,
            val status: String,
        ) : Reply
        data class Batch(
            override val documentId: String, override val requestId: String,
            val sessionId: String, val attempted: Long, val captured: Int,
            val dropped: Long, val limited: Boolean,
            val oldestSequence: Long?, val latestSequence: Long,
            val stopped: Boolean, val stopReason: String?,
            val observations: List<Observation>,
        ) : Reply {
            override fun toString() = "VideoSearchDiagnosticWire.Batch(redacted)"
        }
    }
    internal data class Observation(
        val sequence: Long, val timestamp: Long,
        val documentId: String, val revision: Int, val requestId: String?,
        val stage: VideoSearchDiagnosticStage,
        val decision: VideoSearchDiagnosticDecision, val reason: String,
        val counts: Map<VideoSearchDiagnosticCount, Int>,
        val fields: Map<VideoSearchDiagnosticField, String>,
    ) {
        override fun toString() = "VideoSearchDiagnosticWire.Observation(redacted)"
    }

    fun decode(input: Any?): Reply? {
        val root = input as? JSONObject ?: return null
        if (root.toString().length > MAX_PACKET ||
            root.number("schemaVersion", 1) != 1L) return null
        val doc = root.text("documentId")?.takeIf(nonce::matches) ?: return null
        val request = root.text("requestId")?.takeIf(nonce::matches) ?: return null
        return when (root.text("event")) {
            "diag_ack" -> {
                if (!root.exact("schemaVersion", "event", "documentId",
                        "requestId", "status")) return null
                val status = root.text("status")?.takeIf {
                    it in setOf("active", "rejected", "sampled", "stopped")
                } ?: return null
                Reply.Ack(doc, request, status)
            }
            "diag_events" -> {
                if (!root.exact("schemaVersion", "event", "documentId",
                        "requestId", "batch")) return null
                val batch = root.optJSONObject("batch") ?: return null
                if (!batch.exact("schemaVersion", "sessionId", "attempted",
                        "captured", "dropped", "limited", "oldestSequence",
                        "latestSequence", "stopped", "stopReason", "events") ||
                    batch.number("schemaVersion", 1) != 1L) return null
                val sessionId = batch.text("sessionId")?.takeIf(session::matches)
                    ?: return null
                val attempted = batch.number("attempted", 20_000) ?: return null
                val captured = batch.number("captured", 256)?.toInt() ?: return null
                val dropped = batch.number("dropped", 20_000) ?: return null
                val latest = batch.number("latestSequence", 20_000) ?: return null
                if (attempted != captured.toLong() + dropped || latest != attempted)
                    return null
                val limited = batch.opt("limited") as? Boolean ?: return null
                if (limited != (dropped > 0L)) return null
                val stopped = batch.opt("stopped") as? Boolean ?: return null
                val stopReason = when (val reason = batch.opt("stopReason")) {
                    JSONObject.NULL -> null
                    is String -> reason.takeIf { it in setOf("NATIVE_STOP", "TIME_LIMIT") }
                        ?: return null
                    else -> return null
                }
                if (stopped != (stopReason != null)) return null
                val oldest = when (batch.opt("oldestSequence")) {
                    JSONObject.NULL -> null
                    else -> batch.number("oldestSequence", 20_000) ?: return null
                }
                if ((captured == 0) != (oldest == null) ||
                    oldest != null && (oldest < 1 || oldest > latest))
                    return null
                val raw = batch.optJSONArray("events") ?: return null
                if (raw.length() > 4 || raw.length() > captured) return null
                val events = ArrayList<Observation>(raw.length())
                var prior = 0L
                for (i in 0 until raw.length()) {
                    val row = raw.optJSONObject(i) ?: return null
                    if (!row.exact("schemaVersion", "sequence", "timeMs",
                            "documentId", "navigationRevision", "requestId",
                            "stage", "claim", "decision", "reason",
                            "counts", "fields") ||
                        row.number("schemaVersion", 1) != 1L ||
                        row.text("claim") != "OBSERVATION") return null
                    val seq = row.number("sequence", 20_000)?.takeIf {
                        it > prior && it <= latest
                    } ?: return null
                    prior = seq
                    val time = row.number("timeMs", 4_102_444_800_000L) ?: return null
                    val rowDoc = row.text("documentId")?.takeIf(nonce::matches)
                        ?: return null
                    val revision = row.number("navigationRevision", 1_000_000)
                        ?.toInt() ?: return null
                    val rowReq = when (val r = row.opt("requestId")) {
                        JSONObject.NULL -> null
                        is String -> r.takeIf(nonce::matches) ?: return null
                        else -> return null
                    }
                    val stage = stages[row.text("stage")] ?: return null
                    val decision = decisions[row.text("decision")] ?: return null
                    val reason = row.text("reason")?.takeIf { it.length <= 80 }
                        ?: return null
                    val cs = row.optJSONObject("counts") ?: return null
                    if (cs.length() > 8 || !cs.keysValid(counts.keys)) return null
                    val parsedCounts = LinkedHashMap<VideoSearchDiagnosticCount, Int>()
                    val names = cs.keys()
                    while (names.hasNext()) {
                        val name = names.next()
                        parsedCounts[counts.getValue(name)] =
                            cs.number(name, 100_000)?.toInt() ?: return null
                    }
                    val fs = row.optJSONObject("fields") ?: return null
                    if (fs.length() > 9 || !fs.keysValid(fields.keys)) return null
                    val parsedFields = LinkedHashMap<VideoSearchDiagnosticField, String>()
                    val fieldNames = fs.keys()
                    while (fieldNames.hasNext()) {
                        val name = fieldNames.next()
                        val field = fields.getValue(name)
                        val max = when (field) {
                            VideoSearchDiagnosticField.DOM_FRAGMENT -> 768
                            VideoSearchDiagnosticField.PAGE_URL,
                            VideoSearchDiagnosticField.FILM_URL -> 1200
                            else -> 300
                        }
                        parsedFields[field] = fs.text(name)?.takeIf { it.length <= max }
                            ?: return null
                    }
                    events.add(Observation(seq, time, rowDoc, revision, rowReq,
                        stage, decision, reason, parsedCounts, parsedFields))
                }
                Reply.Batch(doc, request, sessionId, attempted, captured,
                    dropped, limited, oldest, latest, stopped, stopReason, events)
            }
            else -> null
        }
    }

    private fun base(event: String, documentId: String, requestId: String): JSONObject {
        require(nonce.matches(documentId) && nonce.matches(requestId))
        return JSONObject().put("schemaVersion", 1).put("event", event)
            .put("documentId", documentId).put("requestId", requestId)
    }
    fun start(doc: String, id: String, sessionId: String): JSONObject {
        require(session.matches(sessionId))
        return base("diag_start", doc, id).put("sessionId", sessionId)
    }
    fun read(doc: String, id: String, cursor: Long): JSONObject {
        require(cursor in 0..20_000)
        return base("diag_read", doc, id).put("cursor", cursor)
    }
    fun stop(doc: String, id: String, sessionId: String): JSONObject {
        require(session.matches(sessionId))
        return base("diag_stop", doc, id).put("sessionId", sessionId)
    }
    fun snapshot(doc: String, id: String): JSONObject =
        base("diag_snapshot", doc, id)
}
