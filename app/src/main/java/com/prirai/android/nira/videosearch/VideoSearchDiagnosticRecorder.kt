package com.prirai.android.nira.videosearch

import android.content.Context
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Diagnostics schema v1. Host-owned profile and private lifetime are NOT
 * supplied by Gecko/JS. A missing observation never means success.
 */
internal enum class VideoSearchDiagnosticStage {
    SESSION, FORM, RESULTS_REGION, CARDS, METADATA, SPA, PAGINATION,
    BRIDGE, QUEUE, EXTRACTION, DEDUP, STORAGE, DISPLAY
}
internal enum class VideoSearchDiagnosticClaim { OBSERVATION, INFERENCE }
internal enum class VideoSearchDiagnosticDecision {
    SEEN, ACCEPTED, REJECTED, UNCERTAIN, WAITING, FAILED, CANCELLED, INTERRUPTED
}
internal enum class VideoSearchDiagnosticCount {
    VISIBLE_CARDS, CANDIDATES, ACCEPTED_FILMS, UNIQUE_FILMS,
    SAVED_FILMS, DISPLAYED_FILMS, PAGES, REQUESTS
}
internal enum class VideoSearchDiagnosticField {
    PAGE_URL, FILM_URL, SEARCH_QUERY, TITLE, DURATION, DATE, SELECTOR, DOM_FRAGMENT, DETAIL
}
internal data class VideoSearchDiagnosticTrace(
    val runId: String? = null, val siteId: String? = null,
    val documentId: String? = null, val requestId: String? = null,
    val workerId: String? = null, val batchId: String? = null,
)
internal data class VideoSearchDiagnosticEvent(
    val stage: VideoSearchDiagnosticStage,
    val claim: VideoSearchDiagnosticClaim,
    val decision: VideoSearchDiagnosticDecision,
    val reason: String,
    val trace: VideoSearchDiagnosticTrace = VideoSearchDiagnosticTrace(),
    val counts: Map<VideoSearchDiagnosticCount, Int> = emptyMap(),
    /** Finite, approved field names; never raw header/cookie/form maps. */
    val fields: Map<VideoSearchDiagnosticField, String> = emptyMap(),
) {
    override fun toString() = "VideoSearchDiagnosticEvent(redacted)"
}

/** Defense-in-depth; stage 2 must additionally sanitize DOM at its source. */
internal object VideoSearchDiagnosticCodec {
    const val VERSION = 1
    private val sensitiveName = Regex(
        "(?i)(password|passwd|pwd|secret|token|session|auth|cookie|api.?key|csrf|jwt|sid|code|state)"
    )
    private val secretPairs = Regex(
        """(?i)\b(password|passwd|pwd|secret|access_token|refresh_token|id_token|api_key|authorization|cookie|set-cookie|session(?:id)?|csrf(?:token)?)\s*[:=]\s*(?:"[^"]*"|'[^']*'|[^\s&;<>]+)"""
    )
    private val sensitiveHeaders = Regex(
        "(?im)^\\s*(?:authorization|cookie|set-cookie)\\s*:\\s*[^\\r\\n]*"
    )
    private val bearer = Regex("(?i)\\bBearer\\s+[^\\s\"'<>]+")
    private val jwt = Regex("""\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}(?:\.[A-Za-z0-9_-]+)?\b""")
    private val domNodes = Regex(
        "(?is)<script\\b[^>]*>.*?</script\\s*>|<style\\b[^>]*>.*?</style\\s*>|" +
            "<textarea\\b[^>]*>.*?</textarea\\s*>|<input\\b[^>]*>|<meta\\b[^>]*>"
    )
    private val domAttributes = Regex(
        """(?i)\b(?:value|data-token|data-secret|data-password|nonce|on\w+)\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)"""
    )
    private fun redact(raw: String): String = raw.replace(sensitiveHeaders, "[REDACTED_HEADER]")
        .replace(secretPairs) { it.groupValues[1] + "=[REDACTED]" }
        .replace(bearer, "Bearer [REDACTED]").replace(jwt, "[REDACTED_JWT]")
    private fun url(raw: String): String = try {
        val uri = URI(raw.trim())
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host == null)
            "[INVALID_URL]"
        else {
            // URI user-info and fragments are never recorded.
            val authority = uri.rawAuthority.substringAfterLast('@')
            val query = uri.rawQuery?.split('&')?.joinToString("&") { segment ->
                val key = segment.substringBefore('=')
                val decoded = runCatching { URLDecoder.decode(key, "UTF-8") }.getOrDefault(key)
                if (sensitiveName.containsMatchIn(decoded)) key + "=[REDACTED]" else segment
            }
            redact(buildString {
                append(uri.scheme).append("://").append(authority).append(uri.rawPath ?: "")
                if (query != null) append('?').append(query)
            }).take(2048)
        }
    } catch (_: Exception) { "[INVALID_URL]" }

    private fun clean(field: VideoSearchDiagnosticField, raw: String): String {
        if (field == VideoSearchDiagnosticField.PAGE_URL ||
            field == VideoSearchDiagnosticField.FILM_URL) return url(raw)
        val filtered = if (field == VideoSearchDiagnosticField.DOM_FRAGMENT)
            raw.replace(domNodes, "[REDACTED_ELEMENT]")
                .replace(domAttributes, "[REDACTED_ATTRIBUTE]")
        else raw
        return redact(filtered).take(if (field == VideoSearchDiagnosticField.DOM_FRAGMENT) 2048 else 1024)
    }
    private fun id(raw: String?): Any = if (raw == null) JSONObject.NULL else redact(raw).take(128)
    fun event(session: String, ownerId: String, isPrivate: Boolean,
              sequence: Long, timeMs: Long, value: VideoSearchDiagnosticEvent): String {
        val trace = JSONObject().put("runId", id(value.trace.runId))
            .put("siteId", id(value.trace.siteId))
            .put("documentId", id(value.trace.documentId))
            .put("requestId", id(value.trace.requestId))
            .put("workerId", id(value.trace.workerId))
            .put("batchId", id(value.trace.batchId))
        val counts = JSONObject()
        value.counts.forEach { (name, count) ->
            if (count >= 0) counts.put(name.name, count)
        }
        val fields = JSONObject()
        value.fields.forEach { (name, text) -> fields.put(name.name, clean(name, text)) }
        return JSONObject().put("schemaVersion", VERSION).put("kind", "event")
            .put("sessionId", session).put("ownerId", ownerId)
            .put("private", isPrivate).put("sequence", sequence).put("timeMs", timeMs)
            .put("stage", value.stage.name).put("claim", value.claim.name)
            .put("decision", value.decision.name).put("reason", id(value.reason))
            .put("trace", trace).put("counts", counts).put("fields", fields).toString()
    }
}

/**
 * Stage 1 only: bounded, opt-in recording foundation, not wired to search/UI.
 * Producers perform a short lock + non-blocking offer; all encoding and file
 * I/O happen on one daemon worker. Private lifetime: RAM only, no File.
 */
internal class VideoSearchDiagnosticRecorder internal constructor(
    val owner: VideoSearchScope,
    private val directory: File?,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxEvents: Int = 20_000,
    private val maxBytes: Int = 8 * 1024 * 1024,
    queueCapacity: Int = 256,
    private val maxDurationMs: Long = 45 * 60 * 1000L,
    val sessionId: String = UUID.randomUUID().toString(),
) {
    companion object {
        private const val RESERVED_END_BYTES = 1024
        private const val RETAIN_FILES = 4
        private const val RETAIN_MS = 7 * 24 * 60 * 60 * 1000L
        private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).take(16)
            .joinToString("") { "%02x".format(it) }
        fun create(context: Context, owner: VideoSearchScope): VideoSearchDiagnosticRecorder {
            val directory = if (owner is VideoSearchScope.Normal)
                File(context.noBackupFilesDir, "zavro-video-diagnostics/" + digest(owner.profileId))
            else null
            return VideoSearchDiagnosticRecorder(owner, directory)
        }
        /** Read off main. A missing/corrupt end marker is NOT completion. */
        fun diskState(file: File): String = try {
            var tail: String? = null
            file.bufferedReader().useLines { it.forEach { line -> tail = line } }
            if (tail != null && JSONObject(tail!!).optString("kind") == "session_end")
                "CLOSED" else "INTERRUPTED"
        } catch (_: Exception) { "INTERRUPTED" }
    }
    init {
        require(maxEvents in 1..20_000 && maxBytes in 2048..8 * 1024 * 1024)
        require(queueCapacity in 1..4096 && maxDurationMs in 1L..2_700_000L)
        require(sessionId.matches(Regex("[0-9a-fA-F-]{8,64}")))
        require((owner is VideoSearchScope.Private) == (directory == null))
    }
    private data class Pending(val seq: Long, val time: Long, val value: VideoSearchDiagnosticEvent)
    private val gate = Any()
    private val queue = ArrayBlockingQueue<Pending>(queueCapacity)
    private val privateBuffer = ArrayList<String>()
    private val startedAt = now()
    private val ownerId = digest(owner.profileId +
        if (owner is VideoSearchScope.Private) ":" + owner.sessionId else "")
    val file: File? = directory?.let { File(it, "session-" + sessionId + ".jsonl") }
    private var active = true
    private var finished = false
    private var retired = false
    private var reason = "USER_STOP"
    private var attempted = 0L
    private var queued = 0L
    private var recorded = 0L
    private var dropped = 0L
    private var byteCount = 0
    private var error: String? = null
    private val worker = Thread({ drain() }, "zavro-video-diagnostics").apply {
        isDaemon = true
        start()
    }

    /** False is a diagnostic drop, never a claim about result quality. */
    fun record(event: VideoSearchDiagnosticEvent): Boolean = synchronized(gate) {
        if (!active || retired || finished) return@synchronized false
        attempted++
        if (now() - startedAt >= maxDurationMs) {
            active = false
            reason = "TIME_LIMIT"
            dropped++
            return@synchronized false
        }
        if (queued >= maxEvents) {
            dropped++
            return@synchronized false
        }
        val accepted = queue.offer(Pending(attempted, now(), event.copy(
            counts = event.counts.toMap(), fields = event.fields.toMap())))
        if (accepted) queued++ else dropped++
        accepted
    }

    fun stop(stopReason: String = "USER_STOP") = synchronized(gate) {
        if (active) {
            active = false
            reason = stopReason.take(40).replace(Regex("[^A-Za-z0-9_]"), "_")
        }
        Unit
    }

    /** Called on the final closing of the exact Gecko private lifetime. */
    fun retirePrivateLifetime(scope: VideoSearchScope.Private): Boolean = synchronized(gate) {
        if (scope != owner || directory != null) return@synchronized false
        retired = true
        active = false
        reason = "PRIVATE_LIFETIME_ENDED"
        queue.clear()
        privateBuffer.clear()
        true
    }

    data class State(
        val active: Boolean, val finished: Boolean, val retired: Boolean,
        val attempted: Long, val recorded: Long, val dropped: Long,
        val bytes: Int, val failure: String?,
    )
    fun state(): State = synchronized(gate) {
        State(active, finished, retired, attempted, recorded, dropped, byteCount, error)
    }
    fun privateLines(scope: VideoSearchScope.Private): List<String> = synchronized(gate) {
        if (scope != owner || directory != null || retired) emptyList()
        else privateBuffer.toList()
    }
    /** Only from tests/IO dispatcher; do not block the Android UI thread. */
    fun awaitStopped(timeoutMs: Long = 5000): Boolean {
        worker.join(timeoutMs)
        return state().finished
    }

    /** File output happens outside the producer lock; RAM is owner-guarded. */
    private fun append(out: BufferedWriter?, line: String, reserve: Int = RESERVED_END_BYTES): Boolean {
        val size = (line + "\n").toByteArray(Charsets.UTF_8).size
        synchronized(gate) {
            if (retired) return false
            if (byteCount.toLong() + size + reserve > maxBytes) {
                dropped++
                return false
            }
            byteCount += size
            if (out == null) {
                privateBuffer.add(line)
                return true
            }
        }
        out.write(line)
        out.newLine()
        return true
    }

    private fun prune() {
        val folder = directory ?: return
        folder.listFiles()?.filter {
            it.isFile && it != file &&
                it.name.matches(Regex("session-[0-9a-fA-F-]{8,64}\\.jsonl"))
        }?.sortedByDescending { it.lastModified() }?.forEachIndexed { index, old ->
            if (index >= RETAIN_FILES - 1 || now() - old.lastModified() > RETAIN_MS)
                old.delete() // Only this module's older diagnostics, never user data.
        }
    }

    private fun drain() {
        var out: BufferedWriter? = null
        try {
            if (directory != null) {
                check(directory.isDirectory || directory.mkdirs())
                prune()
                out = checkNotNull(file).outputStream().bufferedWriter(Charsets.UTF_8)
            }
            append(out, JSONObject().put("schemaVersion", 1)
                .put("kind", "session_start").put("sessionId", sessionId)
                .put("ownerId", ownerId).put("private", owner.isPrivate)
                .put("timeMs", startedAt).toString())
            while (true) {
                val entry = queue.poll(30, TimeUnit.MILLISECONDS)
                if (entry == null) {
                    val keepRunning = synchronized(gate) {
                        if (active && now() - startedAt >= maxDurationMs) {
                            active = false
                            reason = "TIME_LIMIT"
                        }
                        active || queue.isNotEmpty()
                    }
                    if (!keepRunning) break
                    continue
                }
                val line = VideoSearchDiagnosticCodec.event(
                    sessionId, ownerId, owner.isPrivate, entry.seq, entry.time, entry.value)
                if (append(out, line)) {
                    val n = synchronized(gate) { recorded++; recorded }
                    if (n % 16L == 0L) out?.flush()
                }
            }
            val end = synchronized(gate) {
                JSONObject().put("schemaVersion", 1).put("kind", "session_end")
                    .put("sessionId", sessionId).put("reason", reason)
                    .put("timeMs", now()).put("attempted", attempted)
                    .put("recorded", recorded).put("dropped", dropped)
                    .put("limited", dropped > 0L).toString()
            }
            append(out, end, reserve = 0)
            out?.flush()
        } catch (_: Exception) {
            synchronized(gate) {
                error = "IO_OR_ENCODING_FAILURE"
                dropped += queue.size.toLong()
            }
        } finally {
            try { out?.close() } catch (_: Exception) {
                synchronized(gate) { error = "IO_CLOSE_FAILURE" }
            }
            synchronized(gate) {
                active = false
                finished = true
                queue.clear()
            }
        }
    }
}
