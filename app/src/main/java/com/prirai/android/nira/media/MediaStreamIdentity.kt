package com.prirai.android.nira.media

import java.net.URI
import java.util.Locale

/**
 * A comparison key, NEVER a playback URL. Ordinary manifest query parameters
 * are part of stream identity: e, s, t, token, sig, etc. can identify a movie.
 * Neither URL nor identifying query data is ever written to logs.
 *
 * The sole optional renewal equivalence is a fully formed AWS SigV4 presigned
 * URL with an unchanged credential and all unchanged non-rotating parameters.
 * Even that equivalence requires native request lineage and group evidence
 * from MediaRepository. This helper alone must NOT deduplicate two streams.
 */
internal object MediaStreamIdentity {
    private val awsRotating = setOf(
        "x-amz-date", "x-amz-expires", "x-amz-signature",
    )

    fun key(url: String, format: MediaFormat, frameKey: Long? = null): String =
        // No heuristic parameter stripping. Exact URLs are kept for playback.
        "exact:" + format.name + ":" + (frameKey ?: 0L) + ":" + url

    /**
     * Candidate key for known SigV4 credential renewal only; not a universal
     * stream identifier. Keep X-Amz-Credential, security token, signed headers,
     * all custom params and the exact raw media path significant.
     */
    internal fun signedRefreshKey(
        url: String, format: MediaFormat, frameKey: Long? = null,
    ): String? {
        if (format != MediaFormat.HLS && format != MediaFormat.DASH) return null
        val uri = try { URI(url) } catch (_: Exception) { return null }
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (scheme !in setOf("https", "http") || uri.rawUserInfo != null ||
            uri.rawFragment != null) return null
        val fields = uri.rawQuery?.split('&')?.takeIf { it.isNotEmpty() } ?: return null
        // Fail closed on duplicate identity-bearing fields, including case
        // variants, ambiguous % encodings and malformed empty assignments.
        val names = HashSet<String>()
        val pairs = ArrayList<Pair<String, String>>(fields.size)
        for (part in fields) {
            val name = part.substringBefore('=').lowercase(Locale.ROOT)
            if (!part.contains('=') || name.isBlank()) return null
            // Encoded names have ambiguous normalization across parsers.
            if (name.contains('%')) return null
            if (name.startsWith("x-amz-") && !names.add(name)) return null
            pairs.add(name to part.substringAfter('='))
        }
        val signature = pairs.firstOrNull { it.first == "x-amz-signature" }?.second
        val algorithm = pairs.firstOrNull { it.first == "x-amz-algorithm" }?.second
        val credential = pairs.firstOrNull { it.first == "x-amz-credential" }?.second
        val signedHeaders = pairs.firstOrNull { it.first == "x-amz-signedheaders" }?.second
        if (algorithm != "AWS4-HMAC-SHA256" || signature.isNullOrBlank() ||
            credential.isNullOrBlank() || signedHeaders.isNullOrBlank()) return null
        val stable = fields.filter { raw ->
            raw.substringBefore('=').lowercase(Locale.ROOT) !in awsRotating
        }.sorted().joinToString("&")
        return format.name + "|" + scheme + "|" + host + "|" + uri.port + "|" +
            uri.rawPath.orEmpty() + "|" + stable + "|" + (frameKey ?: 0L)
    }

    internal fun sameSignedRefresh(
        oldUrl: String, newUrl: String, format: MediaFormat, frameKey: Long? = null,
    ): Boolean {
        if (oldUrl == newUrl) return false
        val oldKey = signedRefreshKey(oldUrl, format, frameKey) ?: return false
        return oldKey == signedRefreshKey(newUrl, format, frameKey)
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
