package com.prirai.android.nira.media

/**
 * Poster evidence is independent of title evidence. The chosen URL remains in
 * native process memory only; diagnostics use origin and ambiguity flags.
 * Equal-strength conflicting posters yield UNKNOWN, never an arbitrary image.
 *
 * This is evidence ranking, not proof that a remote image is the correct frame.
 * Caller must validate the URL, document, frame and stream identity first.
 */
internal object MediaPosterResolver {
    internal enum class Origin(val wire: String, val rank: Int) {
        VIDEO_ELEMENT("element", 5),
        LOCAL_CARD("card", 4),
        PLAYER_CONFIG("player", 3),
        STRUCTURED_VIDEO("structured", 2),
        LEGACY("legacy", 1),
    }

    internal fun origin(wire: String?): Origin? =
        Origin.entries.firstOrNull { it.wire == wire } ?:
            if (wire == null) Origin.LEGACY else null

    internal class Decision(
        val url: String?, val origin: Origin?, val ambiguous: Boolean,
    ) {
        override fun toString(): String =
            "MediaPosterDecision(url=<redacted>, origin=$origin, ambiguous=$ambiguous)"
    }

    internal class State {
        private var selected: String? = null
        private var evidence: Origin? = null
        private var conflict = false

        fun offer(url: String?, wireOrigin: String?): Boolean {
            if (url == null) return false
            val incoming = origin(wireOrigin) ?: return false
            if (selected == null && evidence == null) {
                selected = url; evidence = incoming
                return true
            }
            val prior = evidence ?: return false
            when {
                incoming.rank > prior.rank -> {
                    selected = url; evidence = incoming; conflict = false
                    return true
                }
                incoming.rank < prior.rank -> return false
                selected == url -> return false
                else -> {
                    conflict = true
                    return true
                }
            }
        }

        fun snapshot() = Decision(if (conflict) null else selected, evidence, conflict)
    }
}
