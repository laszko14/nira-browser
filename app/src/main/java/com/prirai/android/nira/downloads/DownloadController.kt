package com.prirai.android.nira.downloads

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.DownloadAction
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.concept.fetch.MutableHeaders
import mozilla.components.concept.fetch.Request
import mozilla.components.feature.downloads.AbstractFetchDownloadService
import mozilla.components.feature.downloads.INTENT_EXTRA_DOWNLOAD_ID
import mozilla.components.support.utils.DefaultDownloadFileUtils

/**
 * Routes a user-initiated download to either [DownloadService] (serial,
 * mozilla-components default) or [ParallelDownloadService]
 * (multi-threaded range downloads).
 *
 * # Routing contract
 * A download is eligible for the parallel path iff ALL of:
 *   1. URL scheme is `http(s)` (ParallelDownloadService uses raw
 *      `Request` fetches; data:, blob:, and file: cannot be range-
 *      fetched).
 *   2. Either `download.contentLength >= PARALLEL_MIN_BYTES` (8 MiB) OR
 *      a sidecar directory from an earlier attempt exists (resume
 *      case).
 *
 * `contentLength` is often null on first attempt because GeckoView only
 * fills it in after the response headers arrive, and [DownloadsFeature]
 * dispatches `AddDownloadAction` right after the request completes - at
 * which point the length may or may not be present. [start] handles the
 * `contentLength == null` case by firing an async HEAD-style
 * `Range: bytes=0-0` probe and re-routing based on the result; while
 * the probe runs we start the serial path so the user never sees a
 * stall.
 */
object DownloadController {

    private const val TAG = "DownloadController"
    private val probes = DownloadProbeGate()

    const val PARALLEL_MIN_BYTES = 8L * 1024 * 1024
    const val PARALLEL_THREADS = 4

    const val ACTION_PAUSE = "com.prirai.android.nira.downloads.PAUSE"
    const val ACTION_RESUME = "com.prirai.android.nira.downloads.RESUME"
    const val ACTION_CANCEL = "com.prirai.android.nira.downloads.CANCEL"

    /**
     * Deterministic routing used when we already know
     * `download.contentLength`. Returns true when the download is a
     * good candidate for [ParallelDownloadService].
     */
    fun shouldUseParallel(context: Context, download: DownloadState): Boolean {
        val length = download.contentLength ?: 0L
        val http = download.url.startsWith("http://") || download.url.startsWith("https://")
        return http && (
            length >= PARALLEL_MIN_BYTES ||
                ParallelDownloadService.hasSidecar(context, download)
            )
    }

    /**
     * Decide whether to use parallel downloads for [download]. If the
     * content length is already known (or a sidecar exists), the
     * routing is deterministic. Otherwise we return [Deferred]-ish
     * `null` so the caller can start the serial path immediately and
     * let [start] probe the server asynchronously.
     */
    fun start(context: Context, incoming: DownloadState) {
        val app = context.applicationContext
        // A retry with the same download ID must invalidate a previously delayed probe.
        probes.invalidate(incoming.id)

        // Guarantee a non-null `fileName` before any service touches
        // the DownloadState. This is normally handled by MozAC's
        // DownloadsFeature pre-dialog pipeline (via
        // `DownloadState.getRealFilenameOrGuessed`), but Nira presents
        // its own confirmation UI in `ComposeDownloadsScreen` and
        // routes directly here, bypassing that step. If we hand a
        // state with `fileName == null` to [ParallelDownloadService]
        // or [DownloadService], MozAC's
        // `DefaultDownloadFileWriter.handleDownloadToDefaultDirectory`
        // early-returns null on its `fileName ?: return null` guard
        // and the outer writer throws
        // `IOException: Failed to create or find a document for the
        // download: null` - the exact symptom reported on APK and ZIP
        // downloads from F-Droid, GitHub releases, etc. The failing
        // path is reproducible against
        // https://f-droid.org/F-Droid.apk which serves no
        // Content-Disposition header; the Cloudflare mirror works
        // because it does.
        val download = ensureFileName(app, incoming).also { resolved ->
            if (resolved !== incoming) {
                app.components.store.dispatch(DownloadAction.UpdateDownloadAction(resolved))
            }
        }

        val http = download.url.startsWith("http://") || download.url.startsWith("https://")

        if (!http) {
            startService(app, DownloadService::class.java, download.id)
            return
        }

        if (shouldUseParallel(app, download)) {
            startService(app, ParallelDownloadService::class.java, download.id)
            return
        }

        // Start serial before scheduling its optional handoff. Both paths share
        // one per-download generation, invalidated synchronously by pause,
        // cancel, resume or a new start for the same ID.
        if (download.contentLength == null) {
            val ticket = probes.open(download.id)
            try {
                if (!probes.runIfCurrent(ticket) {
                        startService(app, DownloadService::class.java, download.id)
                    }) return
            } catch (failure: Throwable) {
                probes.invalidate(download.id)
                throw failure
            }
            val job = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val probed = probeContentLength(app, download)
                    if (probed != null && probed >= PARALLEL_MIN_BYTES) {
                        // Serialized with controller pause/cancel. Never use
                        // captured DownloadState as fallback when Store removed
                        // the record or a terminal status already won.
                        probes.claim(ticket) {
                            val current = app.components.store.state.downloads[download.id]
                            if (!current.isEligibleForProbeHandoff(download)) return@claim
                            Log.i(TAG, "Probe found length=$probed for ${download.id}; switching to parallel")
                            app.sendBroadcast(
                                Intent(AbstractFetchDownloadService.ACTION_CANCEL).apply {
                                    setPackage(app.packageName)
                                    putExtra(INTENT_EXTRA_DOWNLOAD_ID, download.id)
                                }
                            )
                            app.components.store.dispatch(
                                DownloadAction.UpdateDownloadAction(current!!.copy(contentLength = probed))
                            )
                            startService(app, ParallelDownloadService::class.java, download.id)
                        }
                    }
                } finally {
                    probes.release(ticket)
                }
            }
            probes.attach(ticket, job)
            return
        }

        startService(app, DownloadService::class.java, download.id)
    }

    /**
     * Fired from inside [ParallelDownloadService] when the probe inside
     * it (not the one in [start]) determines parallel isn't viable
     * - e.g. the server doesn't support Range requests. Hands the
     * download to the serial path without touching broadcast fan-out.
     */
    fun startFallback(context: Context, downloadId: String) {
        startService(context.applicationContext, DownloadService::class.java, downloadId)
    }

    fun pause(context: Context, downloadId: String) {
        probes.invalidate(downloadId)
        val app = context.applicationContext
        app.sendBroadcast(
            Intent(AbstractFetchDownloadService.ACTION_PAUSE).apply {
                setPackage(app.packageName)
                putExtra(INTENT_EXTRA_DOWNLOAD_ID, downloadId)
            }
        )
        app.sendBroadcast(
            Intent(ACTION_PAUSE).apply {
                setPackage(app.packageName)
                putExtra(INTENT_EXTRA_DOWNLOAD_ID, downloadId)
            }
        )
    }

    fun resume(context: Context, incoming: DownloadState) {
        probes.invalidate(incoming.id)
        val app = context.applicationContext
        val download = ensureFileName(app, incoming).also { resolved ->
            if (resolved !== incoming) {
                app.components.store.dispatch(DownloadAction.UpdateDownloadAction(resolved))
            }
        }
        if (shouldUseParallel(app, download)) {
            app.sendBroadcast(
                Intent(ACTION_RESUME).apply {
                    setPackage(app.packageName)
                    putExtra(INTENT_EXTRA_DOWNLOAD_ID, download.id)
                }
            )
            startService(app, ParallelDownloadService::class.java, download.id)
        } else {
            app.sendBroadcast(
                Intent(AbstractFetchDownloadService.ACTION_RESUME).apply {
                    setPackage(app.packageName)
                    putExtra(INTENT_EXTRA_DOWNLOAD_ID, download.id)
                }
            )
            startService(app, DownloadService::class.java, download.id)
        }
    }

    fun cancel(context: Context, download: DownloadState) {
        probes.invalidate(download.id)
        val app = context.applicationContext
        app.sendBroadcast(
            Intent(AbstractFetchDownloadService.ACTION_CANCEL).apply {
                setPackage(app.packageName)
                putExtra(INTENT_EXTRA_DOWNLOAD_ID, download.id)
            }
        )
        app.sendBroadcast(
            Intent(ACTION_CANCEL).apply {
                setPackage(app.packageName)
                putExtra(INTENT_EXTRA_DOWNLOAD_ID, download.id)
            }
        )
        app.components.store.dispatch(DownloadAction.RemoveDownloadAction(download.id))
    }

    /**
     * HEAD-style probe used by [start] when `contentLength` is unknown.
     * Fetches `Range: bytes=0-0`; a 206 response's `Content-Range`
     * header carries the total size as `bytes 0-0/<total>`. Returns
     * null if the server does not advertise Range support, the probe
     * errors, or the total can't be parsed.
     */
    private fun probeContentLength(context: Context, download: DownloadState): Long? {
        return try {
            val headers = MutableHeaders()
            headers.append("Range", "bytes=0-0")
            val request = Request(
                url = download.url,
                headers = headers,
                private = download.private,
                referrerUrl = download.referrerUrl,
            )
            context.components.client.fetch(request).use { response ->
                if (response.status != 206) return null
                ParallelDownloadService.parseTotal(response.headers["Content-Range"])
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Exception) {
            Log.w(TAG, "Content-length probe failed for download ${download.id}: ${t.javaClass.simpleName}")
            null
        }
    }

    /**
     * Resolve a non-null, usable file name for [download], mirroring
     * `DownloadState.getRealFilenameOrGuessed` in MozAC. Returns the
     * same instance when a name is already set, otherwise a copy with
     * `fileName` derived from the URL path and MIME. We don't have the
     * original `Content-Disposition` by this point (it's not persisted
     * on `DownloadState`), so we pass null for it and let
     * `DefaultDownloadFileUtils.guessFileName` fall through to the URL
     * + MIME-based guess - the same fallback MozAC uses when its own
     * pre-dialog pipeline runs with a missing header.
     */
    private fun ensureFileName(context: Context, download: DownloadState): DownloadState {
        if (!download.fileName.isNullOrBlank()) return download
        val utils = DefaultDownloadFileUtils(
            context = context.applicationContext,
            downloadLocation = { download.directoryPath },
        )
        val guessed = utils.guessFileName(
            contentDisposition = null,
            url = download.url,
            mimeType = download.contentType,
        )
        return download.copy(fileName = guessed)
    }

    private fun startService(context: Context, service: Class<*>, downloadId: String) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, service).apply {
                putExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(INTENT_EXTRA_DOWNLOAD_ID, downloadId)
            }
        )
    }
}
