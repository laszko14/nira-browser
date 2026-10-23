package com.prirai.android.nira.media

import java.net.URI
import java.util.Locale

/**
 * A session-local comparison key, NEVER a URL to open. The exact signed URL is retained
 * separately. Only recognised volatile authentication parameters are removed, and only
 * for manifests (ordinary video query parameters remain distinct).
 *
 * Tab, navigation and profile isolation is enforced by MediaRepository.Scope.
 * frameKey prevents a navigated iframe from reusing another frame's stream.
 */
internal object MediaStreamIdentity {
    private val volatileQueryNames = setOf(
        "s", "e", "t", "token", "sig", "signature", "expires", "expiry", "exp",
        "hdnts", "hdntl", "policy", "key-pair-id", "x-amz-algorithm",
        "x-amz-credential", "x-amz-date", "x-amz-expires",
        "x-amz-signedheaders", "x-amz-signature", "x-amz-security-token"
    )

    fun key(url: String, format: MediaFormat, frameKey: Long? = null): String {
        if (format != MediaFormat.HLS && format != MediaFormat.DASH) {
            return "exact:" + (frameKey ?: 0L) + ":" + url
        }
        val parsed = try { URI(url) } catch (_: Exception) { return "exact:$url" }
        val host = parsed.host?.lowercase(Locale.ROOT) ?: return "exact:$url"
        // rawPath/rawQuery avoid decoding or re-encoding signed components.
        val stableQuery = parsed.rawQuery.orEmpty().split('&')
            .filter { it.isNotEmpty() && it.substringBefore('=').lowercase(Locale.ROOT) !in volatileQueryNames }
            .sorted().joinToString("&")
        return buildString {
            append(format.name).append('|')
            append(parsed.scheme?.lowercase(Locale.ROOT)).append('|')
            append(host).append('|').append(parsed.port).append('|')
            append(parsed.rawPath.orEmpty()).append('|')
            append(stableQuery).append('|').append(frameKey ?: 0L)
        }
    }
}

internal enum class MediaProvenance {
    NETWORK_OBSERVED,
    MANIFEST_DERIVED,
    DOM_HINT
}

internal data class MediaUrlRevision(
    val exactUrl: String,
    val observedSequence: Long,
    val provenance: MediaProvenance,
) {
    override fun toString() = "MediaUrlRevision(url=<redacted>, provenance=$provenance)"
}
