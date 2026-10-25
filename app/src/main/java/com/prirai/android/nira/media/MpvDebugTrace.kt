package com.prirai.android.nira.media

import android.os.SystemClock

/**
 * Volatile diagnostic breadcrumbs for the user's three on-device reports.
 *
 * There is deliberately no free-form logging API: all codes must belong to the
 * allowlist, therefore a signed URL, header, cookie, hostname, tab ID, profile
 * name, or DRM token can never be recorded or copied by this component.
 */
internal object MpvDebugTrace {
    enum class Section { WEB, HANDOFF, MPV }

    private data class Entry(
        val elapsedMs: Long,
        val section: Section,
        val code: String,
        val session: Any?,
    )

    private val events = ArrayDeque<Entry>()
    private const val MAX_EVENTS = 240

    private val allowed = setOf(
        "extension_install_ok", "extension_install_failed", "extension_attach_failed",
        "session_handler_attached", "session_handler_failed",
        "port_connected", "port_disconnected", "port_rejected", "bridge_ready",
        "gesture", "video_play", "metadata", "no_gesture", "no_direct_url",
        "source_blob", "source_drm", "source_stream", "source_invalid",
        "source_short", "source_muted", "source_small", "multiple_players",
        "auto_offered", "auto_offer_received", "auto_busy", "auto_mode_not_enabled",
        "auto_wrong_session", "auto_navigation", "auto_launch_request",
        "prepare_sent", "prepare_rejected", "ack_ok", "ack_failed", "ack_timeout",
        "restore_requested", "restore_sent", "lease_discarded",
        "mpv_ticket_missing", "mpv_ticket_valid", "mpv_audio_focus_failed",
        "mpv_surface_attached", "mpv_loading", "mpv_file_loaded",
        "mpv_end_before_load", "mpv_end_after_load", "mpv_timeout",
        "mpv_auto_rollback", "mpv_manual_error", "mpv_retry",
        "mpv_linkage_failed", "mpv_init_failed", "mpv_destroyed",
        "source_mp4", "source_hls", "source_dash", "source_unknown",
        "script_started", "document_visible", "oneshot_received",
        "probe_received", "probe_ack", "probe_ack_oneshot", "oneshot_protocol_received",
        "probe_no_ack", "probe_send_failed", "message_not_json",
        "mpv_start_file", "mpv_video_ready", "mpv_audio_ready", "mpv_playback_started",
        "mpv_cache_wait", "mpv_cache_resume", "mpv_logger_registered",
        "mpv_http_401", "mpv_http_403", "mpv_http_404", "mpv_http_429", "mpv_http_5xx",
        "mpv_tls_error", "mpv_network_timeout", "mpv_dns_failure", "mpv_format_error",
        "mpv_open_failed", "mpv_network_other"
    )

    @Synchronized
    fun note(section: Section, code: String, session: Any? = null) {
        if (code !in allowed) return
        // Drop repeated polling/metadata spam without losing a new decision.
        val last = events.lastOrNull()
        val now = SystemClock.elapsedRealtime()
        if (last?.section == section && last.code == code &&
            last.session === session && now - last.elapsedMs < 800L) return
        events.addLast(Entry(now, section, code, session))
        while (events.size > MAX_EVENTS) events.removeFirst()
    }

    @Synchronized
    fun report(section: Section, session: Any?, playerMode: String? = null): String = buildString {
        appendLine("Zavro x MPV v1137 — ${section.name}")
        appendLine("Tryb odtwarzania: ${playerMode ?: "nie dotyczy"}")
        appendLine("Sesja Gecko: ${if (session == null) "BRAK" else "AKTYWNA"}")
        appendLine("Zapis tylko w RAM; brak URL, hostów, cookies, nagłówków i tokenów.")
        appendLine("Zdarzenia chronologicznie (czas względem uruchomienia Androida):")
        val visible = events.filter {
            it.section == section && (it.session == null || it.session === session)
        }.takeLast(90)
        if (visible.isEmpty()) appendLine("Brak zdarzeń. Otwórz stronę, naciśnij Play i odśwież raport.")
        visible.forEach { appendLine("${it.elapsedMs / 1000}s | ${it.code}") }
        appendLine("Uwaga: brak zdarzeń może oznaczać brak portu Gecko lub MSE/blob.")
    }

    /** Discard full libmpv logs immediately. Only fixed failure categories survive. */
    fun classifyMpvLog(prefix: String, level: Int, message: String): String? {
        // Only warnings/errors from known mpv network/demuxer layers.
        if (level > 30 || message.length > 4096 ||
            prefix.length > 80) return null
        val component = prefix.lowercase(java.util.Locale.ROOT)
        if (listOf("ffmpeg", "stream", "demux", "http", "tls", "network",
                "lavf", "player").none { component.contains(it) }) return null
        val lower = message.lowercase(java.util.Locale.ROOT)
        return when {
            Regex("""\b(401|unauthorized)\b""").containsMatchIn(lower) -> "mpv_http_401"
            Regex("""\b(403|forbidden)\b""").containsMatchIn(lower) -> "mpv_http_403"
            Regex("""\b404\b""").containsMatchIn(lower) -> "mpv_http_404"
            Regex("""\b429\b""").containsMatchIn(lower) -> "mpv_http_429"
            Regex("""\b5[0-9]{2}\b""").containsMatchIn(lower) -> "mpv_http_5xx"
            "certificate" in lower || "ssl" in lower || "tls" in lower -> "mpv_tls_error"
            "timed out" in lower || "timeout" in lower -> "mpv_network_timeout"
            "resolve" in lower || "dns" in lower -> "mpv_dns_failure"
            "unsupported" in lower || "unrecognized" in lower ||
                "invalid data" in lower -> "mpv_format_error"
            "failed to open" in lower || "error opening" in lower -> "mpv_open_failed"
            "network" in lower && "error" in lower -> "mpv_network_other"
            else -> null
        }
    }

    fun classifySource(url: String): String {
        val path = runCatching { android.net.Uri.parse(url).path?.lowercase() }.getOrNull().orEmpty()
        return when {
            path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".webm") -> "source_mp4"
            path.endsWith(".m3u8") -> "source_hls"
            path.endsWith(".mpd") -> "source_dash"
            else -> "source_unknown"
        }
    }
}
