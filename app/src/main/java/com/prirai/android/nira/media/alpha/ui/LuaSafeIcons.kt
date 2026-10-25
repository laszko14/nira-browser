// MPV Alpha v54 / REX-Player, Apache-2.0. Adapted from c6107ce14a9f1768c73f975c5d60b1d8bd71aaed.
package com.prirai.android.nira.media.alpha.ui

import com.prirai.android.nira.R
import com.prirai.android.nira.media.alpha.*

/**
 * Icon styles are presentation-only. Never change button title or Lua code.
 * Numeric speeds keep their exact value, and unknown custom names stay verbatim.
 */
internal fun luaSafeIconLabel(label: String, icon: String?, style: Int): String {
    val explicit = icon?.trim()?.takeIf { it.isNotEmpty() }
    if (explicit != null) return explicit.take(3)
    if (style == 0) return label

    val name = label.trim().lowercase()
    val speed = Regex("""[0-9]{1,2}(?:[.,][0-9]{1,2})?[x×]""", RegexOption.IGNORE_CASE)
    if (speed.matches(name)) {
        val number = label.trim().dropLast(1)
        // Modern and minimal use recognizably different typography, but the
        // numeric speed remains explicit even for user-defined scripts.
        return if (style == 2) "×$number" else "$number×"
    }

    val modern = when (name) {
        "odtwarzaj / pauza", "play / pause", "odtwarzaj/pauza", "play/pause" -> "▶"
        "odtwarzaj", "play" -> "▶"
        "pauza", "pause" -> "Ⅱ"
        "przewiń -10s", "przewiń −10s", "rewind -10s" -> "◀◀"
        "przewiń +10s", "rewind +10s", "forward +10s" -> "▶▶"
        "głośność", "volume" -> "♪"
        "ambient+" -> "▣"
        "szum (denoise)" -> "≋"
        "głos (voice)" -> "♩"
        else -> null
    } ?: return label

    if (style != 2) return modern
    return when (modern) {
        "▶" -> "▷"
        "Ⅱ" -> "❚❚"
        "◀◀" -> "«"
        "▶▶" -> "»"
        "♪" -> "♫"
        "▣" -> "□"
        "≋" -> "≈"
        else -> "♬"
    }
}
