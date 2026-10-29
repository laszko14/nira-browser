package com.prirai.android.nira.videosearch

import java.net.URI

/** Pure persistence-boundary checks. Do not log rejected URLs or query text. */
internal object VideoSearchIntegrity {
    /** Preserve exact query parameters and case; validation is not normalization. */
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
