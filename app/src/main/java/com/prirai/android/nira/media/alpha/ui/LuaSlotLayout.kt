// MPV Alpha v54 / REX-Player, Apache-2.0. Adapted from c6107ce14a9f1768c73f975c5d60b1d8bd71aaed.
package com.prirai.android.nira.media.alpha.ui

import com.prirai.android.nira.R
import com.prirai.android.nira.media.alpha.*

import kotlinx.serialization.json.Json
import kotlin.math.floor

/** Allow the Lua overlay to move 120 dp above its former top limit. */
internal const val LUA_POSITION_Y_MIN_DP = -120
internal const val LUA_POSITION_Y_MAX_DP = 240

/** The slider, player, and grid must agree on which stored values are valid. */
internal fun luaButtonVerticalPositionDp(positionY: Int): Int =
    positionY.coerceIn(LUA_POSITION_Y_MIN_DP, LUA_POSITION_Y_MAX_DP)

// Keep the original 35 indexes (0..34) unchanged when old preferences are loaded.
internal const val LUA_MAIN_SLOT_COUNT = 30
internal const val LUA_EXTRA_SLOT_COUNT = 8
internal const val LUA_EXTRA_COLUMNS = 4
// Five historical slots 30..34 survive as data-only overflow if all 38 slots are full.
internal const val LUA_LEGACY_SLOT_START = 30
internal const val LUA_LEGACY_SLOT_END = 35
internal const val LUA_EXTRA_START = LUA_LEGACY_SLOT_END
internal const val LUA_SLOT_COUNT = LUA_EXTRA_START + LUA_EXTRA_SLOT_COUNT

/**
 * Decode 30/35/43-slot installations without losing a script, ID or appearance.
 * Slots 30..34 are now off-screen. Migrate them into the first FREE right slot,
 * then left; keep rare overflow editable if the visible 38 are all occupied.
 */
internal fun <T> normalizeLuaVisibleSlots(source: List<T?>): List<T?> {
    val result = MutableList<T?>(LUA_SLOT_COUNT) { source.getOrNull(it) }
    for (old in LUA_LEGACY_SLOT_START until LUA_LEGACY_SLOT_END) {
        val button = result[old] ?: continue
        val free = (LUA_EXTRA_START until LUA_SLOT_COUNT).firstOrNull { result[it] == null }
            ?: (0 until LUA_MAIN_SLOT_COUNT).firstOrNull { result[it] == null }
        if (free != null) {
            result[free] = button
            result[old] = null
        }
    }
    return result
}

internal fun decodeLuaButtonSlots(serialized:String):List<CustomButton?> = AlphaSlots.decode(serialized)

internal fun isLuaScreenSlot(index: Int): Boolean =
    index in 0 until LUA_MAIN_SLOT_COUNT || index in LUA_EXTRA_START until LUA_SLOT_COUNT

/** Measured in root coordinates, so the right side remains droppable at any width. */
internal fun luaDropTargetAt(
    bounds: Map<Int, androidx.compose.ui.geometry.Rect>,
    center: androidx.compose.ui.geometry.Offset,
): Int? = bounds.entries.firstOrNull { (index, rect) ->
    isLuaScreenSlot(index) && rect.contains(center)
}?.key

/** Drop onto another button swaps them; drop onto a hole leaves a hole behind. */
internal fun <T> swapLuaSlotPositions(
    slots: List<T?>,
    from: Int,
    to: Int,
): List<T?> {
    val result = MutableList<T?>(LUA_SLOT_COUNT) { slots.getOrNull(it) }
    if (!isLuaScreenSlot(from) || !isLuaScreenSlot(to) || from == to ||
        result[from] == null) return result
    val previous = result[to]
    result[to] = result[from]
    result[from] = previous
    return result
}
