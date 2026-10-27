package com.prirai.android.nira.media

/**
 * One deterministic title decision policy for native records and video tiles.
 *
 * Candidate text, legacy origin and scope association are separate concepts.
 * The v1 protocol does not yet carry independently verified player/DOM identity;
 * a titleKind is a hint, not proof that a visible title belongs to a movie.
 * Source/document/frame validation happens in MediaRepository before State.offer.
 * All title strings stay in process RAM, never in diagnostics or toString().
 */
internal object MediaTitleResolver {
    internal enum class Association {
        PLAYER_HINT, STRUCTURED_URL_HINT, LOCAL_MEDIA_HINT, MEDIA_LINK_HINT, PAGE_ONLY
    }
    internal enum class TextQuality { SPECIFIC, TECHNICAL }
    internal enum class Reason {
        NO_CANDIDATE, ACCEPTED, UPGRADED, REPEATED,
        REJECTED_WEAKER, CONFLICT, REJECTED_TECHNICAL, REJECTED_INVALID
    }

    internal class Candidate(
        val text: String,
        val kind: String,
        val association: Association,
        val quality: TextQuality,
    ) {
        override fun toString(): String =
            "MediaTitleCandidate(text=<redacted>, kind=" + kind + ", association=" + association + ")"
    }

    internal class Resolution(
        val text: String?,
        val kind: String?,
        val ambiguous: Boolean,
    ) {
        override fun toString(): String =
            "MediaTitleResolution(text=<redacted>, kind=" + kind + ", ambiguous=" + ambiguous + ")"
    }

    internal fun rank(kind: String?): Int = when (kind) {
        "player" -> 5
        "structured" -> 4
        "video" -> 3
        "link" -> 2
        "page" -> 1
        else -> 0
    }

    private fun association(kind: String): Association? = when (kind) {
        "player" -> Association.PLAYER_HINT
        "structured" -> Association.STRUCTURED_URL_HINT
        "video" -> Association.LOCAL_MEDIA_HINT
        "link" -> Association.MEDIA_LINK_HINT
        "page" -> Association.PAGE_ONLY
        else -> null
    }

    private val technicalText = Regex(
        "^(?:play|watch|watch now|odtwórz|oglądaj|zobacz|video|film|player|" +
            "hd|sd|download|pobierz|master|index|playlist|stream|" +
            "[0-9]{3,4}p|[0-9]+\\s*(?:kbps|mbps))(?:\\.(?:mp4|m3u8|mpd))?$",
        RegexOption.IGNORE_CASE,
    )


    /**
     * Decode only human-readable captions; signed playback URLs are never
     * rewritten. Keep decoding bounded (one percent pass + one entity pass).
     */
    internal fun normalizeText(raw: String): String {
        val decoded = if (Regex("%[0-9a-fA-F]{2}").containsMatchIn(raw)) {
            runCatching {
                java.net.URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8")
            }.getOrDefault(raw)
        } else raw
        val entities = Regex("&(#(?:[xX][0-9a-fA-F]{1,6}|[0-9]{1,7})|amp|quot|apos|lt|gt|nbsp|ndash|mdash|hellip);",
            RegexOption.IGNORE_CASE)
        val expanded = entities.replace(decoded) { match ->
            val token = match.groupValues[1]
            if (!token.startsWith("#")) {
                when (token.lowercase(java.util.Locale.ROOT)) {
                    "amp" -> "&"
                    "quot" -> "\""
                    "apos" -> "'"
                    "lt" -> "<"
                    "gt" -> ">"
                    "nbsp" -> " "
                    "ndash" -> "\u2013"
                    "mdash" -> "\u2014"
                    "hellip" -> "\u2026"
                    else -> match.value
                }
            } else {
                val hex = token.startsWith("#x", ignoreCase = true)
                val n = token.drop(if (hex) 2 else 1)
                    .toIntOrNull(if (hex) 16 else 10)
                if (n != null && n in 1..0x10FFFF && n !in 0xD800..0xDFFF)
                    String(Character.toChars(n)) else match.value
            }
        }
        return java.text.Normalizer.normalize(expanded, java.text.Normalizer.Form.NFC)
            .replace(Regex("[\u0000-\u001f\u007f]"), " ")
            .replace(Regex("\\s+"), " ").trim()
    }

    /** Quality checks do not assert the relationship between text and media. */
    internal fun candidate(raw: String?, kind: String?): Candidate? {
        if (raw == null || kind == null) return null
        val owner = association(kind) ?: return null
        val text = normalizeText(raw)
        if (text.isEmpty() || text.length > 300 ||
            text.toByteArray(Charsets.UTF_8).size > 900 ||
            text.any { it.isISOControl() }) return null
        val quality = if (technicalText.matches(text)) TextQuality.TECHNICAL
            else TextQuality.SPECIFIC
        return Candidate(text, kind, owner, quality)
    }

    /**
     * Per-stream, O(1) state. No title is used as a deduplication key.
     * Same-rank distinct titles are ambiguous, even if the first was displayed.
     * A stronger title can resolve ambiguity; weaker text cannot downgrade it.
     */
    internal class State {
        private var selected: Candidate? = null
        private var conflicting = false

        fun offer(text: String?, kind: String?): Reason {
            if (text == null && kind == null) return Reason.NO_CANDIDATE
            val incoming = candidate(text, kind) ?: return Reason.REJECTED_INVALID
            if (incoming.quality == TextQuality.TECHNICAL) return Reason.REJECTED_TECHNICAL
            val previous = selected
            if (previous == null) {
                selected = incoming
                return Reason.ACCEPTED
            }
            val rankDifference = rank(incoming.kind) - rank(previous.kind)
            if (rankDifference > 0) {
                selected = incoming
                conflicting = false
                return Reason.UPGRADED
            }
            if (rankDifference < 0) return Reason.REJECTED_WEAKER
            if (incoming.text == previous.text) return Reason.REPEATED
            conflicting = true
            return Reason.CONFLICT
        }

        fun snapshot(): Resolution =
            Resolution(selected?.text, selected?.kind, conflicting)
    }

    /**
     * Shared variant-group policy. A conflicting high-ranked source vetoes
     * weaker titles, and a page-wide title is never assigned to several tiles.
     */
    internal fun selectGroup(entries: List<MediaEntry>, allowPageOnly: Boolean): Resolution {
        val eligible = entries.filter {
            !it.title.isNullOrBlank() && (it.titleKind != "page" || allowPageOnly)
        }
        val bestRank = eligible.maxOfOrNull { rank(it.titleKind) } ?: 0
        val ambiguousRank = entries.filter { it.titleAmbiguous }
            .maxOfOrNull { rank(it.titleKind) } ?: 0
        if (ambiguousRank > 0 && ambiguousRank >= bestRank)
            return Resolution(null, null, true)
        val best = eligible.filter { rank(it.titleKind) == bestRank && bestRank > 0 }
        val names = best.mapNotNull { it.title }.distinct()
        if (names.size > 1) return Resolution(null, null, true)
        return Resolution(names.singleOrNull(), best.firstOrNull()?.titleKind, false)
    }
}
