package com.prirai.android.nira.media

import com.prirai.android.nira.diagnostics.testlab.DiagnosticSessionGate
import com.prirai.android.nira.diagnostics.testlab.DiagnosticOwner
import com.prirai.android.nira.diagnostics.testlab.TestLab
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast

/**
 * A single one-tap, 40-second on-device test shared by the Gecko browser and
 * embedded libmpv. Does not open arbitrary network URLs, seek, stop, or reload.
 * Captures only allowlisted event codes and finite numeric playback progress.
 * Copies exactly one report when the test ends. Stored in RAM only.
 */
internal object MpvSupertest {
    private const val TEST_MS = 40_000L
    private val main = Handler(Looper.getMainLooper())
    private data class Capture(
        val owner: Any?,
        val mode: String,
        val startMs: Long,
        val appContext: Context,
        var samples: Int = 0,
        var advancedSeconds: Double = 0.0,
        var previousPosition: Double? = null,
        var sawPosition: Boolean = false
    )

    private var active: Capture? = null
    private var lastReport: String? = null

    @Synchronized
    fun start(context: Context, session: Any?, mode: String): Boolean {
        if (active != null) return false
        if (!DiagnosticSessionGate.shared.acquire(DiagnosticOwner.SUPERTEST)) return false
        val capture = Capture(session, mode.take(32), SystemClock.elapsedRealtime(),
            context.applicationContext)
        active = capture
        lastReport = null
        main.postDelayed({ finish(capture) }, TEST_MS)
        return true
    }

    /** Number of seconds of observation remaining, or zero when completed. */
    @Synchronized
    fun remainingSeconds(): Int {
        val capture = active ?: return 0
        return ((TEST_MS - (SystemClock.elapsedRealtime() - capture.startMs))
            .coerceAtLeast(0L) + 999L).div(1000L).toInt()
    }

    @Synchronized
    fun latest(): String? = lastReport

    /** Called only by the active, session-bound native libmpv player. */
    @Synchronized
    fun sample(session: Any?, position: Double) {
        TestLab.mediaPosition(session,position)
        val capture = active ?: return
        if (session == null || capture.owner !== session || !position.isFinite() ||
            position < 0.0) return
        capture.sawPosition = true
        capture.samples++
        val last = capture.previousPosition
        // Ignore jumps caused by seeking and treat only incremental progress
        // as playback time; 3x speed can advance several seconds per tick.
        if (last != null && position > last && position - last <= 5.0)
            capture.advancedSeconds += position - last
        capture.previousPosition = position
    }

    private fun finish(capture: Capture) {
        val report = synchronized(this) {
            if (active !== capture) return
            active = null
            try {
            val result = MpvDebugTrace.superReport(
                capture.owner, capture.mode, capture.startMs,
                SystemClock.elapsedRealtime(), capture.samples,
                capture.advancedSeconds, capture.sawPosition
            )
            lastReport = result
            result
            } finally {DiagnosticSessionGate.shared.release(DiagnosticOwner.SUPERTEST)}
        }
        runCatching {
            val clipboard = capture.appContext.getSystemService(Context.CLIPBOARD_SERVICE)
                as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Zavro x MPV SUPERTEST", report))
            Toast.makeText(capture.appContext,
                "SUPERTEST zakończony • jeden raport skopiowany", Toast.LENGTH_LONG).show()
        }
    }
}
