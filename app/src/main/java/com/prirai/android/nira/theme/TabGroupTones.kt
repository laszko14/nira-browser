package com.prirai.android.nira.theme

/**
 * A single, dark, legible color mapping for the side drawer and both under-address tab strips.
 *
 * [groupColor] is the persisted ARGB int from TabGroupData.color.
 * null means an ungrouped tab; it consistently uses blue rather than the
 * default pink selected-tab background.
 *
 * All backgrounds are opaque for predictable contrast regardless of theme.
 * This object has no Compose or Android dependencies, so the same result
 * can be used by native Views and Jetpack Compose.
 */
object TabGroupTones {
    val UNGROUPED_BLUE: Int = 0xFF2196F3.toInt()
    private val HEADER_BASE: Int = 0xFF191921.toInt()
    private val TAB_BASE: Int = 0xFF201D27.toInt()

    data class Palette(
        val accent: Int,
        val headerStart: Int,
        val headerEnd: Int,
        val tabStart: Int,
        val tabEnd: Int,
        val activeStart: Int,
        val activeEnd: Int
    )

    fun forGroup(groupColor: Int?): Palette {
        val accent = (groupColor ?: UNGROUPED_BLUE) or 0xFF000000.toInt()
        return Palette(
            accent = accent,
            headerStart = blend(HEADER_BASE, accent, 26),
            headerEnd = blend(HEADER_BASE, accent, 18),
            tabStart = blend(TAB_BASE, accent, 23),
            tabEnd = blend(TAB_BASE, accent, 17),
            activeStart = blend(TAB_BASE, accent, 37),
            activeEnd = blend(TAB_BASE, accent, 29)
        )
    }

    /** Opaque RGB blend in integer percentages; avoids theme-specific alpha compositing. */
    private fun blend(base: Int, accent: Int, percent: Int): Int {
        val amount = percent.coerceIn(0, 100)
        fun component(shift: Int): Int {
            val a = (base ushr shift) and 0xFF
            val b = (accent ushr shift) and 0xFF
            return (a * (100 - amount) + b * amount + 50) / 100
        }
        return (0xFF000000.toInt()
            or (component(16) shl 16)
            or (component(8) shl 8)
            or component(0))
    }
}
