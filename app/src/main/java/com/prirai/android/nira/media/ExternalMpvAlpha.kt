package com.prirai.android.nira.media

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import java.util.Locale

/** MPV Alpha's verified REX application IDs, separate from Zavro's own .mpv package. */
internal object ExternalMpvAlpha {
    enum class Result { OPENED, UNSAFE, UNAVAILABLE, CANCELLED }

    fun open(url: String, canLaunch: () -> Boolean = { true }, launch: (Intent) -> Unit): Result {
        if (!canLaunch()) return Result.CANCELLED
        if (!ExternalMpvSourcePolicy.allowed(url)) return Result.UNSAFE
        val uri = url.toUri()
        val extension = uri.path.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mime = if (extension in setOf("mp4", "mkv", "webm", "mov", "m3u8", "mpd", "m4v")) "video/*" else "video/any"
        // Explicit package intents can launch an installed activity even when package visibility
        // prevents queries on Android11+. No resolveActivity/getInstalledPackages or broad queries.
        for (alphaPackage in listOf("xyz.mpv.rex.debug", "xyz.mpv.rex")) {
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).setPackage(alphaPackage)
            try {
                launch(intent)
                return Result.OPENED
            } catch (_: ActivityNotFoundException) {
                // Only try the other variant of the same Alpha player.
            } catch (_: SecurityException) {
                // Disabled/not exported/inaccessible activity: handled without another player.
            } catch (_: IllegalArgumentException) {
                // Malformed or OEM-rejected intent, no picker fallback.
            }
        }
        return Result.UNAVAILABLE
    }
}
