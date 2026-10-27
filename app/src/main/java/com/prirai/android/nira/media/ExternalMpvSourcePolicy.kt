package com.prirai.android.nira.media

import java.net.URI
import java.util.Locale

/** No resolution/probing, persistence or logging of the deliberately selected media URL. */
internal object ExternalMpvSourcePolicy {
    fun allowed(value: String): Boolean {
        if (value.length !in 8..8192 || value.any { it.isISOControl() || it.isWhitespace() }) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || uri.rawUserInfo != null) return false
        val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("[")?.removeSuffix("]")?.trimEnd('.') ?: return false
        if (host.isBlank() || host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return false
        if (':' in host) return publicIpv6(host)
        if (host.all { it.isDigit() || it == '.' }) {
            val parts = host.split('.')
            // Native URL stacks may interpret leading-zero octets as octal.
            if (parts.any { it.length > 1 && it.startsWith('0') }) return false
            val octets = parts.map { it.toIntOrNull() ?: return false }
            if (octets.size != 4 || octets.any { it !in 0..255 }) return false
            val a = octets[0]; val b = octets[1]
            return a !in setOf(0, 10, 127) && a < 224 && !(a == 169 && b == 254) &&
                !(a == 172 && b in 16..31) && !(a == 192 && b == 168) && !(a == 100 && b in 64..127)
        }
        // Avoid single-label intranet names and legacy numeric/hex loopback spellings.
        return '.' in host && !host.startsWith("0x")
    }

    /** Numeric parsing only: never resolve a media hostname or expose a zone/interface name. */
    private fun publicIpv6(host: String): Boolean {
        if ('%' in host || '.' in host) return false // Scoped or embedded IPv4 literals.
        fun words(part: String): List<Int>? {
            if (part.isEmpty()) return emptyList()
            return part.split(':').map { word ->
                if (word.length !in 1..4 || word.any { it !in "0123456789abcdef" }) return null
                word.toInt(16)
            }
        }
        val halves = host.split("::", limit = 3)
        if (halves.size > 2) return false
        val left = words(halves[0]) ?: return false
        val address = if (halves.size == 1) {
            if (left.size != 8) return false
            left
        } else {
            val right = words(halves[1]) ?: return false
            val omitted = 8 - left.size - right.size
            if (omitted !in 1..8) return false
            left + List(omitted) { 0 } + right
        }
        // Reject all IPv4-compatible/mapped forms, including expanded hex spellings,
        // plus unspecified and loopback (which fall within the compatible prefix).
        if (address.take(5).all { it == 0 } && address[5] in setOf(0, 0xffff)) return false
        val prefix = address[0]
        return prefix and 0xfe00 != 0xfc00 && // Unique-local fc00::/7.
            prefix and 0xffc0 != 0xfe80 &&   // Link-local fe80::/10.
            prefix and 0xffc0 != 0xfec0 &&   // Deprecated site-local fec0::/10.
            prefix and 0xff00 != 0xff00      // Multicast ff00::/8.
    }
}
