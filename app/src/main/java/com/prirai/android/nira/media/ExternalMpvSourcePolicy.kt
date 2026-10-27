package com.prirai.android.nira.media

import java.net.URI
import java.util.Locale

/** No resolution/probing, persistence or logging of the deliberately selected media URL. */
internal object ExternalMpvSourcePolicy {
    fun allowed(value: String): Boolean {
        if (value.length !in 8..8192 || value.any { it.isISOControl() || it.isWhitespace() }) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || uri.rawUserInfo != null) return false
        val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("[")?.removeSuffix("]") ?: return false
        if (host.isBlank() || host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return false
        if (':' in host) {
            // Public IPv6 only; mapped IPv4 is rejected without DNS or a network request.
            return host != "::" && host != "::1" && !host.startsWith("fc") && !host.startsWith("fd") &&
                !host.startsWith("fe8") && !host.startsWith("fe9") && !host.startsWith("fea") &&
                !host.startsWith("feb") && !host.startsWith("::ffff:") && !host.startsWith("ff")
        }
        if (host.all { it.isDigit() || it == '.' }) {
            val octets = host.split('.').map { it.toIntOrNull() ?: return false }
            if (octets.size != 4 || octets.any { it !in 0..255 }) return false
            val a = octets[0]; val b = octets[1]
            return a !in setOf(0, 10, 127) && a < 224 && !(a == 169 && b == 254) &&
                !(a == 172 && b in 16..31) && !(a == 192 && b == 168) && !(a == 100 && b in 64..127)
        }
        // Avoid single-label intranet names and legacy numeric/hex loopback spellings.
        return '.' in host && !host.startsWith("0x")
    }
}
