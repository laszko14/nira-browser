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
        "source_mp4", "source_hls", "source_dash", "source_unknown"
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
        appendLine("Zavro x MPV v1134 — ${section.name}")
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
