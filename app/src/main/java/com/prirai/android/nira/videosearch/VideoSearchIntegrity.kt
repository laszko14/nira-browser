package com.prirai.android.nira.videosearch

import java.net.URI

/** Pure persistence-boundary checks. Do not log rejected URLs or query text. */
internal object VideoSearchIntegrity {
    /** Preserve exact query parameters and case; validation is not normalization. */
    /**
     * Accept a full URL or a bare domain. Validation never contacts the site.
     * Keep path and query untouched; strip only surrounding input whitespace.
     */
    fun siteInputUrl(input: String): String {
        val typed = input.trim()
        require(typed.isNotEmpty()) { "Podaj adres witryny" }
        val normalized = if (typed.contains("://")) typed else "https://$typed"
        requirePageUrl(normalized)
        return normalized
    }

    fun requirePageUrl(url: String) {
        require(url.length <= 8192 && url.isNotBlank() &&
            url.none { it.isWhitespace() || it.isISOControl() }) { "Invalid page URL" }
        val parsed = try { URI(url) } catch (_: Exception) { null }
        require(parsed != null &&
            (parsed.scheme.equals("https", ignoreCase = true) ||
                parsed.scheme.equals("http", ignoreCase = true)) &&
            !parsed.host.isNullOrBlank() &&
            parsed.rawUserInfo == null &&
            parsed.port in -1..65535) { "Only valid HTTP(S) URLs are allowed" }
    }
}
