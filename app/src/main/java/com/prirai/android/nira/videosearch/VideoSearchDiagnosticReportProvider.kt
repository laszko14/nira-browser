package com.prirai.android.nira.videosearch

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.OpenableColumns
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.ext.components
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/** One temporary, user-authorized report; no automatic uploads or private disk copies. */
internal class VideoSearchDiagnosticShareRegistry(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    data class Grant(
        val token: String,
        val recorder: VideoSearchDiagnosticRecorder,
        val issuedAt: Long,
    )
    companion object { const val TTL_MS = 120_000L }
    private val lock = Any()
    private var latest: Grant? = null

    fun issue(recorder: VideoSearchDiagnosticRecorder): String? = synchronized(lock) {
        val state = recorder.state()
        if (!state.finished || state.retired) return@synchronized null
        val token = UUID.randomUUID().toString().replace("-", "")
        latest = Grant(token, recorder, clock())
        token
    }

    /** The caller must validate the exact current profile/private lifetime. */
    fun resolve(token: String, authorized: Boolean): Grant? = synchronized(lock) {
        val item = latest ?: return@synchronized null
        if (clock() - item.issuedAt >= TTL_MS) {
            latest = null
            return@synchronized null
        }
        if (item.token != token || !authorized ||
            item.recorder.state().retired || !item.recorder.state().finished)
            null else item
    }

    fun revoke(recorder: VideoSearchDiagnosticRecorder) = synchronized(lock) {
        if (latest?.recorder === recorder) latest = null
    }
}

internal object VideoSearchDiagnosticReportAccess {
    private val grants = VideoSearchDiagnosticShareRegistry()
    const val SUFFIX = ".video_diagnostics"
    const val MIME = "text/plain"

    fun authorized(context: Context, owner: VideoSearchScope): Boolean {
        val app = context.applicationContext
        val manager = ProfileManager.getInstance(app)
        if (manager.getActiveProfile().id != owner.profileId) return false
        return when (owner) {
            is VideoSearchScope.Normal -> !manager.isPrivateMode()
            is VideoSearchScope.Private -> {
                if (!manager.isPrivateMode() ||
                    VideoSearchPrivateSessionHost.registry.activeScope() != owner) false
                else app.components.store.state.tabs.any { tab ->
                    tab.content.private && tab.contextId == "private" &&
                        VideoSearchPrivateSessionHost.registry.ownerForTab(
                            VideoSearchPrivateTab(tab.id, tab.contextId, true),
                            owner.profileId) == owner
                }
            }
        }
    }

    /** Must only be called from a visible, explicit share confirmation. */
    fun offer(context: Context, recorder: VideoSearchDiagnosticRecorder): Uri? {
        if (!authorized(context, recorder.owner)) return null
        if (recorder.owner is VideoSearchScope.Normal &&
            !safeNormalFile(context, recorder)) return null
        val token = grants.issue(recorder) ?: return null
        return Uri.Builder().scheme("content")
            .authority(context.packageName + SUFFIX)
            .appendPath("report").appendPath(token).build()
    }

    private fun safeNormalFile(context: Context, recorder: VideoSearchDiagnosticRecorder): Boolean {
        val file = recorder.file ?: return false
        return try {
            val root = File(context.noBackupFilesDir, "zavro-video-diagnostics").canonicalFile
            val resolved = file.canonicalFile
            resolved.isFile && resolved.length() <= 8L * 1024 * 1024 &&
                resolved.name == "session-" + recorder.sessionId + ".jsonl" &&
                resolved.path.startsWith(root.path + File.separator)
        } catch (_: Exception) { false }
    }

    fun resolve(context: Context, uri: Uri): VideoSearchDiagnosticShareRegistry.Grant? {
        if (uri.scheme != "content" ||
            uri.authority != context.packageName + SUFFIX ||
            uri.pathSegments.size != 2 || uri.pathSegments[0] != "report") return null
        val token = uri.pathSegments[1]
        if (!token.matches(Regex("[0-9a-f]{32}"))) return null
        // Fetch grant without trusting the Uri as an owner identifier.
        val candidate = grants.resolve(token, authorized = true) ?: return null
        return grants.resolve(token, authorized(context, candidate.recorder.owner))
            ?.takeIf {
                it.recorder.owner is VideoSearchScope.Private ||
                    safeNormalFile(context, it.recorder)
            }
    }

    fun manifest(recorder: VideoSearchDiagnosticRecorder): String {
        val state = recorder.state()
        val fileState = when (recorder.owner) {
            is VideoSearchScope.Private -> if (state.retired) "RETIRED" else
                if (state.finished && state.failure == null) "CLOSED" else "INTERRUPTED"
            is VideoSearchScope.Normal -> recorder.file?.let {
                VideoSearchDiagnosticRecorder.diskState(it)
            } ?: "INTERRUPTED"
        }
        return JSONObject()
            .put("schemaVersion", 1)
            .put("kind", "export_manifest")
            .put("sessionId", recorder.sessionId)
            .put("private", recorder.owner.isPrivate)
            .put("fileState", fileState)
            .put("attempted", state.attempted)
            .put("recorded", state.recorded)
            .put("dropped", state.dropped)
            .put("interruptions", state.interruptions)
            .put("incomplete", state.dropped > 0 ||
                state.interruptions > 0 || fileState != "CLOSED")
            .put("failure", state.failure ?: JSONObject.NULL)
            .put("note", "LOCAL_OBSERVATIONS_NOT_DEVICE_PROOF")
            .toString()
    }

    /** Streams existing normal file or private in-memory lines; no cache export. */
    fun lines(context: Context, recorder: VideoSearchDiagnosticRecorder): Sequence<String> {
        if (!authorized(context, recorder.owner)) return emptySequence()
        return when (val owner = recorder.owner) {
            is VideoSearchScope.Private -> recorder.privateLines(owner).asSequence()
            is VideoSearchScope.Normal -> {
                val file = recorder.file
                if (file == null || !safeNormalFile(context, recorder)) emptySequence()
                else file.useLinesSequence()
            }
        }
    }

    private fun File.useLinesSequence(): Sequence<String> = sequence {
        bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                yield(line)
            }
        }
    }

    fun isStillAllowed(context: Context, recorder: VideoSearchDiagnosticRecorder): Boolean =
        recorder.state().finished && !recorder.state().retired &&
            authorized(context, recorder.owner) &&
            (recorder.owner is VideoSearchScope.Private || safeNormalFile(context, recorder))
}

/**
 * Bytes flow through a process-local pipe from noBackup or private RAM only.
 * Android enforces the temporary read grant attached to the ACTION_SEND intent.
 */
class VideoSearchDiagnosticReportProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? =
        if (context?.let { VideoSearchDiagnosticReportAccess.resolve(it, uri) } != null)
            VideoSearchDiagnosticReportAccess.MIME else null

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor? {
        val app = context ?: return null
        val grant = VideoSearchDiagnosticReportAccess.resolve(app, uri) ?: return null
        val cols = projection?.toList() ?: listOf(
            OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols.toTypedArray())
        val recorder = grant.recorder
        cursor.addRow(cols.map { col ->
            when (col) {
                OpenableColumns.DISPLAY_NAME ->
                    "zavro-diagnostics-" + recorder.sessionId + ".jsonl"
                OpenableColumns.SIZE -> recorder.state().bytes.toLong() + 512
                else -> null
            }
        })
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only diagnostics")
        val app = context ?: throw FileNotFoundException("No application context")
        val grant = VideoSearchDiagnosticReportAccess.resolve(app, uri)
            ?: throw FileNotFoundException("Expired or unauthorized report")
        val (reader, writer) = ParcelFileDescriptor.createPipe()
        Thread({
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writer).use { output ->
                    val recorder = grant.recorder
                    if (!VideoSearchDiagnosticReportAccess.isStillAllowed(app, recorder))
                        return@use
                    output.write(
                        (VideoSearchDiagnosticReportAccess.manifest(recorder) + "\n")
                            .toByteArray(Charsets.UTF_8))
                    for (line in VideoSearchDiagnosticReportAccess.lines(app, recorder)) {
                        if (!VideoSearchDiagnosticReportAccess.isStillAllowed(app, recorder))
                            break
                        output.write((line + "\n").toByteArray(Charsets.UTF_8))
                    }
                    output.flush()
                }
            } catch (_: Exception) {
                try { writer.close() } catch (_: Exception) { }
            }
        }, "zavro-diagnostic-share").apply { isDaemon = true }.start()
        return reader
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Read-only report")
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read-only report")
    override fun delete(
        uri: Uri, selection: String?, selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read-only report")
}
