package com.prirai.android.nira.browser

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.FileProvider
import com.prirai.android.nira.BuildConfig
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.diagnostics.testlab.DiagnosticOwner
import com.prirai.android.nira.diagnostics.testlab.DiagnosticSessionGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Stage 1: explicitly started, bounded, in-memory diagnostic session.
 *
 * No site DOM, cookie, token, page URL path or network request is collected here.
 * The native-window snapshot is a fixed app-decor sample, NOT proof of which
 * element rendered a webpage control. Page event hooks come in later stages.
 */
internal class MpvPlayDiagnosticLedger(
    val sessionId: String,
    val ownerProfile: String,
    val ownerTab: String,
    val host: String?,
    val version: String,
    val startedWallMs: Long,
    val startedElapsedMs: Long,
) {
    companion object {
        const val MAX_MARKS = 8
        const val MAX_SNAPSHOT_CHARS = 12_000
        const val MAX_ARCHIVE_BYTES = 512 * 1024
    }

    private data class Entry(val elapsedMs: Long, val kind: String, val note: String)
    private val entries = mutableListOf(Entry(0L, "started", "User-initiated; stage-1 native diagnostics only"))
    private val snapshots = mutableListOf<String>()
    private var result = "RUNNING"
    private var stoppedWallMs: Long? = null

    val active: Boolean get() = result == "RUNNING"
    val markCount: Int get() = snapshots.size

    fun mark(elapsedMs: Long, nativeSnapshot: String): Boolean {
        if (!active || snapshots.size >= MAX_MARKS) return false
        val capped = nativeSnapshot.take(MAX_SNAPSHOT_CHARS)
        snapshots.add("MARK " + snapshots.size + " (" + elapsedMs.coerceAtLeast(0L) +
            "ms; APP_DECOR_ONLY)\n" + capped)
        entries.add(Entry(elapsedMs.coerceAtLeast(0L), "manual_problem_marker",
            "Native app-decor sample; not DOM or site JS"))
        return true
    }

    fun finish(elapsedMs: Long, wallMs: Long, reason: String) {
        if (!active) return
        result = reason
        stoppedWallMs = wallMs
        entries.add(Entry(elapsedMs.coerceAtLeast(0L), "finished", reason))
    }

    fun toZipBytes(): ByteArray {
        check(!active) { "Stop diagnostics before exporting" }
        val summary = JSONObject().apply {
            put("schema", "zavro-mpv-play-stage1-v1")
            put("session", sessionId)
            put("status", result)
            put("started_utc_ms", startedWallMs)
            put("stopped_utc_ms", stoppedWallMs)
            put("app_version", version)
            put("site_host_only", host ?: JSONObject.NULL)
            put("snapshot_scope", "APP_DECOR_ONLY")
            put("dom_capture", "NOT_IMPLEMENTED_STAGE1")
            put("js_capture", "NOT_IMPLEMENTED_STAGE1")
            put("private_tabs", "NOT_SUPPORTED_STAGE1")
            put("automatic_upload", false)
            put("marks", snapshots.size)
        }
        val timeline = JSONArray()
        entries.forEach { entry ->
            timeline.put(JSONObject().apply {
                put("elapsed_ms", entry.elapsedMs)
                put("kind", entry.kind)
                put("note", entry.note)
            })
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun add(name: String, data: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            add("summary.json", summary.toString(2))
            add("timeline.json", timeline.toString(2))
            add("native-overlay.txt", snapshots.joinToString("\n\n--- NEXT MARK ---\n\n"))
        }
        return out.toByteArray().also { check(it.size <= MAX_ARCHIVE_BYTES) }
    }
}

internal object MpvPlayDiagnostics {
    internal enum class Start { STARTED, PRIVATE_TAB, BUSY }
    internal data class State(val running: Boolean, val reportReady: Boolean, val ownerProfile: String?)
    private const val MAX_DURATION_MS = 15 * 60 * 1000L
    private val lock = Any()
    private var recording: MpvPlayDiagnosticLedger? = null
    private var finished: MpvPlayDiagnosticLedger? = null

    private fun expireIfNeeded() {
        val old = recording ?: return
        val elapsed = SystemClock.elapsedRealtime() - old.startedElapsedMs
        if (elapsed < MAX_DURATION_MS) return
        old.finish(elapsed, System.currentTimeMillis(), "TIMED_OUT")
        recording = null
        finished = old
        DiagnosticSessionGate.shared.release(DiagnosticOwner.MPV_PLAY)
    }

    fun state(): State = synchronized(lock) {
        expireIfNeeded()
        val current = recording
        State(current != null, finished != null, current?.ownerProfile ?: finished?.ownerProfile)
    }

    fun start(profile: String, tab: String, host: String?, privateTab: Boolean): Start = synchronized(lock) {
        expireIfNeeded()
        if (privateTab) return@synchronized Start.PRIVATE_TAB
        if (recording != null || !DiagnosticSessionGate.shared.acquire(DiagnosticOwner.MPV_PLAY)) {
            return@synchronized Start.BUSY
        }
        try {
            finished = null
            recording = MpvPlayDiagnosticLedger(
                UUID.randomUUID().toString(), profile, tab,
                host?.takeIf { it.length <= 255 && it.matches(Regex("[a-zA-Z0-9.-]+")) },
                BuildConfig.VERSION_NAME, System.currentTimeMillis(), SystemClock.elapsedRealtime(),
            )
            Start.STARTED
        } catch (error: Exception) {
            DiagnosticSessionGate.shared.release(DiagnosticOwner.MPV_PLAY)
            throw error
        }
    }

    fun mark(profile: String, tab: String, snapshot: String): Boolean = synchronized(lock) {
        expireIfNeeded()
        val ledger = recording ?: return@synchronized false
        if (ledger.ownerProfile != profile || ledger.ownerTab != tab) return@synchronized false
        ledger.mark(SystemClock.elapsedRealtime() - ledger.startedElapsedMs, snapshot)
    }

    fun stop(profile: String): Boolean = synchronized(lock) {
        expireIfNeeded()
        val ledger = recording ?: return@synchronized false
        if (ledger.ownerProfile != profile) return@synchronized false
        ledger.finish(SystemClock.elapsedRealtime() - ledger.startedElapsedMs,
            System.currentTimeMillis(), "STOPPED")
        recording = null
        finished = ledger
        DiagnosticSessionGate.shared.release(DiagnosticOwner.MPV_PLAY)
        true
    }

    /** Only our ephemeral diagnostic memory is discarded, never browser data. */
    fun discard() = synchronized(lock) {
        if (recording != null) DiagnosticSessionGate.shared.release(DiagnosticOwner.MPV_PLAY)
        recording = null
        finished = null
    }

    suspend fun export(context: Context, profile: String): Uri? {
        val ledger = synchronized(lock) {
            expireIfNeeded()
            finished?.takeIf { it.ownerProfile == profile }
        } ?: return null
        val manager = ProfileManager.getInstance(context)
        if (manager.isPrivateMode() || manager.getActiveProfile().id != profile) return null
        val bytes = ledger.toZipBytes()
        return withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "mpv_play_diagnostics")
            check(dir.isDirectory || dir.mkdirs()) { "Diagnostic cache unavailable" }
            val file = File(dir, "mpv-play-" + ledger.sessionId + ".zip")
            check(file.canonicalFile.parentFile == dir.canonicalFile)
            // Clean only old files generated by this diagnostic component.
            dir.listFiles()?.filter {
                it.isFile && it.name.startsWith("mpv-play-") &&
                    it.name.endsWith(".zip") && it.name != file.name
            }?.forEach { it.delete() }
            file.outputStream().use { it.write(bytes) }
            if (manager.isPrivateMode() || manager.getActiveProfile().id != profile) {
                file.delete()
                return@withContext null
            }
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        }
    }
}
