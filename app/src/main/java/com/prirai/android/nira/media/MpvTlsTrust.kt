package com.prirai.android.nira.media

import android.content.Context
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.cert.X509Certificate

/**
 * libmpv's bundled FFmpeg TLS backend does not necessarily use AndroidCAStore.
 * Write ONLY system-trusted public CA certificates (not user-installed CAs)
 * to app-private PEM, then pass it through mpv's tls-ca-file option.
 *
 * No certificate verification bypass, no network request, no secret material,
 * no third-party certificate bundle, and no diagnostics containing paths.
 */
internal object MpvTlsTrust {
    fun prepare(context: Context): String? {
        val target = File(context.filesDir, "mpv-system-ca.pem")
        val staging = File(context.filesDir, "mpv-system-ca.pem.tmp")
        return try {
            val store = KeyStore.getInstance("AndroidCAStore")
            store.load(null)
            var roots = 0
            OutputStreamWriter(FileOutputStream(staging), StandardCharsets.US_ASCII)
                .buffered().use { output ->
                    val aliases = store.aliases()
                    while (aliases.hasMoreElements()) {
                        val alias = aliases.nextElement()
                        // The AndroidCAStore also contains "user:" roots. Do not
                        // expand mpv's trust boundary beyond OS system roots.
                        if (!alias.startsWith("system:")) continue
                        val certificate = store.getCertificate(alias) as? X509Certificate ?: continue
                        val pem = Base64.encodeToString(certificate.encoded, Base64.NO_WRAP)
                        output.appendLine("-----BEGIN CERTIFICATE-----")
                        pem.chunked(64).forEach(output::appendLine)
                        output.appendLine("-----END CERTIFICATE-----")
                        roots++
                    }
                }
            if (roots == 0) {
                staging.delete()
                return null
            }
            // The files directory is accessible to this app only. Replace the
            // bundle on each playback to pick up Android trust store updates.
            Files.move(staging.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            target.absolutePath
        } catch (_: Exception) {
            staging.delete()
            null // Never fall back to tls-verify=no.
        }
    }
}
