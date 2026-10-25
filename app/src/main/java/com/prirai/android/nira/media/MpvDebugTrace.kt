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
        "mpv_retry_replacement_end", "mpv_retry_resume_attempt",
        "mpv_retry_resume_failed", "mpv_tls_after_load",
        "mpv_linkage_failed", "mpv_init_failed", "mpv_destroyed",
        "source_mp4", "source_hls", "source_dash", "source_unknown",
        "script_started", "document_visible", "oneshot_received",
        "probe_received", "probe_ack", "probe_ack_oneshot", "oneshot_protocol_received",
        "probe_no_ack", "probe_send_failed", "message_not_json",
        "mpv_start_file", "mpv_video_ready", "mpv_audio_ready", "mpv_playback_started",
        "mpv_cache_wait", "mpv_cache_resume", "mpv_logger_registered",
        "mpv_http_401", "mpv_http_403", "mpv_http_404", "mpv_http_429", "mpv_http_5xx",
        "mpv_tls_error", "mpv_tls_ca_prepared", "mpv_tls_ca_unavailable",
        "mpv_tls_certificate_error", "mpv_tls_handshake_error",
        "inline_eligible", "inline_open", "inline_loaded", "inline_failed",
        "inline_frame_unsupported", "inline_fullscreen", "inline_return", "inline_closed",
        "inline_surface_visible", "inline_surface_hidden", "inline_controls_shown",
        "inline_controls_hidden", "inline_position_preserved", "inline_unexpected_reload",
        "blob_offer", "blob_candidate_found", "blob_candidate_missing",
        "blob_candidate_ambiguous", "blob_frame_lookup", "blob_frame_resolved",
        "blob_frame_failed",
        "mpv_network_timeout", "mpv_dns_failure", "mpv_format_error",
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
        appendLine("Zavro x MPV v1160 — ${section.name}")
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

    /** A single bounded diagnostic report shared by Gecko and native MPV.
     * Includes only hardcoded allowlisted event codes and numeric telemetry.
     * The 30-second pre-roll explains why MPV was already opened before a tap.
     */
    @Synchronized
    fun superReport(
        session: Any?, mode: String, startedMs: Long, finishedMs: Long,
        positionSamples: Int, progressedSeconds: Double, observedMpv: Boolean
    ): String = buildString {
        val before = (startedMs - 30_000L).coerceAtLeast(0L)
        val relevant = events.filter {
            it.elapsedMs in before..finishedMs &&
                (it.session == null || it.session === session)
        }
        val during = relevant.filter { it.elapsedMs >= startedMs }
        fun had(code: String) = relevant.any { it.code == code }
        fun count(code: String) = during.count { it.code == code }
        fun yes(value: Boolean) = if (value) "TAK" else "NIE"
        appendLine("Zavro x MPV v1160 — SUPERTEST 40 s")
        appendLine("Tryb: ${mode}; czas: ${(finishedMs - startedMs).coerceAtLeast(0L) / 1000} s")
        appendLine("JEDEN RAPORT • tylko RAM i kody diagnostyczne; bez adresów, hostów, cookies, tokenów i nagłówków.")
        appendLine()
        appendLine("1. GECKO / PLAY")
        appendLine("Most: ${if (had("probe_ack") || had("probe_ack_oneshot")) "OK (probe ACK)" else "niepotwierdzony w tym oknie"}")
        appendLine("Start strony: ${yes(had("script_started"))}, Play: ${yes(had("video_play"))}, gest: ${yes(had("gesture"))}")
        appendLine("Źródło blob/MSE: ${yes(had("source_blob"))}, DRM: ${yes(had("source_drm"))}")
        appendLine()
        appendLine("2. AUTO / HANDOFF")
        appendLine("Oferta AUTO: ${yes(had("auto_offered") || had("auto_offer_received"))}, prepare: ${yes(had("prepare_sent"))}, ACK: ${yes(had("ack_ok"))}")
        appendLine("Odmowa prepare: ${count("prepare_rejected")}; timeout ACK: ${count("ack_timeout")}; rollback: ${count("mpv_auto_rollback")}")
        if (had("source_drm"))
            appendLine("Wniosek: DRM pozostaje w Gecko.")
        else if (had("source_blob") && !had("blob_candidate_found"))
            appendLine("Wniosek: blob/MSE nie ma jednoznacznego strumienia HLS; Gecko pozostaje aktywne.")
        else if (had("blob_candidate_found"))
            appendLine("Wniosek: rozpoznano strumień HLS dla blob; sprawdź ACK i ustawienie ramki.")
        else if (!had("video_play")) appendLine("Wniosek: w tym oknie nie wykryto Play; nie można ocenić AUTO.")
        else if (!had("gesture")) appendLine("Wniosek: Play bez potwierdzonego gestu; AUTO celowo nie przejmuje.")
        else if (!had("auto_offered") && !had("auto_offer_received"))
            appendLine("Wniosek: brak oferty AUTO; sprawdź, czy źródło ma bezpośredni adres.")
        appendLine()
        appendLine("3. MPV / HLS / TLS")
        appendLine("Otwieranie: ${yes(had("mpv_start_file"))}, załadowanie: ${yes(had("mpv_file_loaded"))}, start playback: ${yes(had("mpv_playback_started"))}")
        appendLine("CA Androida: ${if (had("mpv_tls_ca_prepared")) "przekazano" else if (had("mpv_tls_ca_unavailable")) "niedostępne" else "brak obserwacji"}")
        appendLine("Postęp odtwarzania w czasie testu: ${if (observedMpv) String.format(java.util.Locale.US, "%.1f s (próbki: %d)", progressedSeconds, positionSamples) else "brak próbek MPV"}")
        appendLine("Buforowanie: wait=${count("mpv_cache_wait")}, resume=${count("mpv_cache_resume")}")
        appendLine("Błędy TLS: ogólne=${count("mpv_tls_error")}, cert=${count("mpv_tls_certificate_error")}, handshake=${count("mpv_tls_handshake_error")}, po załadowaniu=${count("mpv_tls_after_load")}")
        appendLine("HTTP 401/403/404/429/5xx: ${listOf("mpv_http_401","mpv_http_403","mpv_http_404","mpv_http_429","mpv_http_5xx").sumOf(::count)}")
        appendLine("Retry=${count("mpv_retry")}, resume seek=${count("mpv_retry_resume_attempt")}, błędy ręczne=${count("mpv_manual_error")}, timeout=${count("mpv_timeout")}")
        appendLine("Wniosek MPV: " + when {
            progressedSeconds >= 3.0 -> "odtwarzanie wykazało rzeczywisty postęp."
            had("mpv_file_loaded") && !observedMpv -> "plik załadowany, ale brak pomiaru pozycji w oknie."
            had("mpv_file_loaded") -> "załadowano plik, lecz za mały postęp; możliwe buforowanie/pauza."
            had("mpv_start_file") -> "rozpoczęto otwieranie, brak FILE_LOADED."
            else -> "nie uruchamiano MPV w czasie tego testu (lub otwarto przed oknem kontekstu)."
        })
        appendLine()
        appendLine("4. MINI MPV / PEŁNY EKRAN")
        appendLine("Mini otwarty: ${yes(had("inline_open"))}; geometria widoczna: ${yes(had("inline_surface_visible"))}; mini załadowany: ${yes(had("inline_loaded"))}")
        appendLine("Pełny ekran: ${yes(had("inline_fullscreen"))}; powrót: ${yes(had("inline_return"))}")
        appendLine("Ciągłość pozycji po powrocie: ${if (had("inline_position_preserved")) "potwierdzona próbką" else "niepotwierdzona w oknie"}")
        appendLine("Ponowne START_FILE po załadowaniu: ${count("inline_unexpected_reload")}")
        appendLine("Widoczność kontrolek: pokazano=${count("inline_controls_shown")}, ukryto=${count("inline_controls_hidden")}")
        appendLine("Przywrócenie Gecko: żądania=${count("restore_requested")}, wysłane=${count("restore_sent")}; inline error=${count("inline_failed")}")
        appendLine("Podwójny dźwięk: wymaga potwierdzenia słuchem; raport nie mierzy torów audio.")
        appendLine("Geometria widoczna nie potwierdza samodzielnie widoczności klatek filmu.")
        appendLine()
        appendLine(com.prirai.android.nira.media.alpha.AlphaLuaDiagnostics.report(session))
        appendLine()
        appendLine(com.prirai.android.nira.media.alpha.audio.AlphaAudioDiagnostics.report(session))
        appendLine(com.prirai.android.nira.media.alpha.audio.AlphaDspDiagnostics.report(session,count("inline_unexpected_reload")))
        appendLine()
        appendLine(com.prirai.android.nira.media.alpha.scripts.CustomScriptsDiagnostics.report(session,
            com.prirai.android.nira.media.alpha.scripts.CustomLuaRepository.importedCount()))
        appendLine()
        appendLine("Ostatni wynik bridge: "+com.prirai.android.nira.media.alpha.AlphaLuaDiagnostics.lastStatus(session))
        appendLine("5. OŚ ZDARZEŃ • test + 30 s kontekstu")
        relevant.takeLast(65).forEach { event ->
            val relative = (event.elapsedMs - startedMs) / 1000.0
            appendLine(String.format(java.util.Locale.US, "%+.1fs | %s | %s", relative, event.section.name, event.code))
        }
        if (relevant.isEmpty()) appendLine("Brak zdarzeń; sprawdź sesję i uruchom Play.")
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
            "certificate" in lower || "x509" in lower ||
                "unknown ca" in lower || "self-signed" in lower ->
                "mpv_tls_certificate_error"
            "handshake" in lower || "protocol version" in lower ||
                "no shared cipher" in lower || "fatal alert" in lower ->
                "mpv_tls_handshake_error"
            "ssl" in lower || "tls" in lower -> "mpv_tls_error"
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
