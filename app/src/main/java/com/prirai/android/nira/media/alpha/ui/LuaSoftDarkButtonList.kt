// MPV Alpha v54 / REX-Player, Apache-2.0. Adapted from c6107ce14a9f1768c73f975c5d60b1d8bd71aaed.
package com.prirai.android.nira.media.alpha.ui

import com.prirai.android.nira.R
import com.prirai.android.nira.media.alpha.*

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.roundToInt

/** Return only the visible slots; historical 30..34 remain untouched. */
internal fun luaFirstFreeVisibleSlot(slots: List<CustomButton?>): Int? =
    ((0 until LUA_MAIN_SLOT_COUNT).toList() +
        (LUA_EXTRA_START until LUA_SLOT_COUNT).toList()).firstOrNull { slots.getOrNull(it) == null }

/** Moves a filled button through occupied positions, preserving free slots and archived data. */
internal fun luaReorderOccupied(slots: List<CustomButton?>, source: Int, target: Int): List<CustomButton?> {
    val occupied = ((0 until LUA_MAIN_SLOT_COUNT).toList() +
        (LUA_EXTRA_START until LUA_SLOT_COUNT).toList()).filter { slots.getOrNull(it) != null }
    val from = occupied.indexOf(source)
    val to = occupied.indexOf(target)
    if (from == -1 || to == -1 || from == to) return slots
    val next = slots.toMutableList()
    val moved = next[source]
    if (from < to) {
        for (position in from until to) next[occupied[position]] = next[occupied[position + 1]]
    } else {
        for (position in from downTo to + 1) next[occupied[position]] = next[occupied[position - 1]]
    }
    next[target] = moved
    return next
}

@Composable
internal fun LuaSoftDarkButtonList(prefs: AlphaLuaPreferences, onEdit: (Int) -> Unit,
                                   modifier: Modifier = Modifier) {
    val serialized by prefs.customButtons.collectAsState()
    val slots = remember(serialized) { decodeLuaButtonSlots(serialized) }
    val columns by prefs.luaPanelColumns.collectAsState()
    val rightColumns by prefs.luaRightColumns.collectAsState()
    val empty by prefs.luaEmptySlots.collectAsState()
    val autoHide by prefs.luaAutoHideOnDrag.collectAsState()
    val density = LocalDensity.current
    val context = LocalContext.current
    // Read locale-aware resources in composition, not via LocalContext.getString().
    val noSpaceMessage = stringResource(R.string.alpha_panel_lua_v2_no_space)
    val occupied = remember(slots) {
        ((0 until LUA_MAIN_SLOT_COUNT).toList() + (LUA_EXTRA_START until LUA_SLOT_COUNT).toList())
            .filter { slots[it] != null }
    }
    val archive = (LUA_MAIN_SLOT_COUNT until LUA_EXTRA_START).filter { slots[it] != null }
    var deleteIndex by remember { mutableIntStateOf(-1) }

    fun persist(next: List<CustomButton?>) {
        prefs.customButtons.set(AlphaSlots.encode(next))
    }
    fun noSpace() {
        Toast.makeText(context, noSpaceMessage, Toast.LENGTH_LONG).show()
    }
    fun duplicate(index: Int) {
        val current = decodeLuaButtonSlots(prefs.customButtons.get())
        val source = current.getOrNull(index) ?: return
        val free = luaFirstFreeVisibleSlot(current)
        if (free == null) { noSpace(); return }
        val next = current.toMutableList()
        next[free] = source.copy(id = UUID.randomUUID().toString())
        persist(next)
    }
    fun moveOtherSide(index: Int) {
        val current = decodeLuaButtonSlots(prefs.customButtons.get())
        val options = if (index < LUA_MAIN_SLOT_COUNT) (LUA_EXTRA_START until LUA_SLOT_COUNT)
            else (0 until LUA_MAIN_SLOT_COUNT)
        val target = options.firstOrNull { current[it] == null }
        if (target == null) { noSpace(); return }
        persist(swapLuaSlotPositions(current, index, target))
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                LuaSoftCard(stringResource(R.string.alpha_panel_lua_v2_layout)) {
                    LuaCountRow(stringResource(R.string.alpha_panel_lua_v2_left_cols), columns, 1, 8, prefs.luaPanelColumns::set)
                    LuaCountRow(stringResource(R.string.alpha_panel_lua_v2_right_cols), rightColumns, 1, 8, prefs.luaRightColumns::set)
                    Text(stringResource(R.string.alpha_panel_lua_v2_empty_slots), color = LuaDarkText, fontSize = 12.sp)
                    LuaSoftSegment(listOf(stringResource(R.string.alpha_panel_lua_v2_never), stringResource(R.string.alpha_panel_lua_v2_edit),
                        stringResource(R.string.alpha_panel_lua_v2_always)), empty, prefs.luaEmptySlots::set)
                    LuaSoftSwitch(stringResource(R.string.alpha_panel_lua_v2_auto_hide), autoHide, prefs.luaAutoHideOnDrag::set)
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.alpha_panel_lua_v2_list), color = LuaDarkText,
                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    TextButton(onClick = {
                        val free = luaFirstFreeVisibleSlot(decodeLuaButtonSlots(prefs.customButtons.get()))
                        if (free == null) noSpace() else onEdit(free)
                    }, modifier = Modifier.background(LuaDarkAccent, RoundedCornerShape(10.dp))) {
                        Text(stringResource(R.string.alpha_panel_lua_v2_add), color = Color.White, fontSize = 13.sp)
                    }
                }
            }
            items(occupied, key = { idx -> slots[idx]?.id ?: "empty-$idx" }) { index ->
                val button = slots[index] ?: return@items
                var dragDy by remember(index) { mutableStateOf(0f) }
                val draggable = Modifier.pointerInput(index, occupied) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { dragDy = 0f },
                        onDragEnd = {
                            val from = occupied.indexOf(index)
                            val step = (dragDy / with(density) { 56.dp.toPx() }).roundToInt()
                            val to = (from + step).coerceIn(0, occupied.lastIndex)
                            if (to != from) {
                                val latest = decodeLuaButtonSlots(prefs.customButtons.get())
                                persist(luaReorderOccupied(latest, index, occupied[to]))
                            }
                            dragDy = 0f
                        },
                        onDragCancel = { dragDy = 0f },
                        onDrag = { change, delta -> change.consume(); dragDy += delta.y },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                        .background(Color(0xFF252D39), RoundedCornerShape(9.dp))
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(button.icon?.takeIf { it.isNotBlank() } ?: when {
                        button.title.contains("pauza", true) -> "▶"
                        button.title.contains("szum", true) -> "≋"
                        button.title.contains("głos", true) -> "♫"
                        else -> "◉"
                    }, modifier = Modifier.width(30.dp), color = LuaDarkText, fontSize = 16.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(button.title, color = LuaDarkText, fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text(if (index < LUA_MAIN_SLOT_COUNT) "L${index + 1}" else "P${index - LUA_EXTRA_START + 1}",
                            color = LuaDarkMuted, fontSize = 9.sp)
                    }
                    LuaSmallAction("✎", stringResource(R.string.alpha_panel_lua_v2_edit_btn)) { onEdit(index) }
                    LuaSmallAction("▣", stringResource(R.string.alpha_panel_lua_v2_copy)) { duplicate(index) }
                    LuaSmallAction("↔", stringResource(R.string.alpha_panel_lua_v2_move)) { moveOtherSide(index) }
                    LuaSmallAction("▤", stringResource(R.string.alpha_panel_lua_v2_remove)) { deleteIndex = index }
                    Box(modifier = Modifier.width(27.dp).heightIn(min = 42.dp).then(draggable),
                        contentAlignment = Alignment.Center) {
                        Text("⠿", color = LuaDarkMuted, fontSize = 19.sp)
                    }
                }
            }
            if (archive.isNotEmpty()) {
                item { Text(stringResource(R.string.alpha_panel_lua_v2_archives), color = LuaDarkMuted, fontSize = 12.sp) }
                items(archive) { index ->
                    TextButton(onClick = { onEdit(index) }, modifier = Modifier.fillMaxWidth()) {
                        Text("ARCH${index - LUA_MAIN_SLOT_COUNT + 1}: ${slots[index]?.title.orEmpty()}",
                            color = LuaDarkText, maxLines = 1)
                    }
                }
            }
            item {
                Text(stringResource(R.string.alpha_panel_lua_v2_drag), color = LuaDarkMuted,
                    fontSize = 11.sp, modifier = Modifier.padding(8.dp))
            }
        }
    }
    if (deleteIndex >= 0) AlertDialog(
        onDismissRequest = { deleteIndex = -1 },
        title = { Text(stringResource(R.string.alpha_panel_audit_delete_this_button)) },
        text = { Text(slots.getOrNull(deleteIndex)?.title.orEmpty()) },
        confirmButton = {
            TextButton(onClick = {
                val idx = deleteIndex
                deleteIndex = -1
                val latest = decodeLuaButtonSlots(prefs.customButtons.get()).toMutableList()
                if (idx in latest.indices) { latest[idx] = null; persist(latest) }
            }) { Text(stringResource(R.string.alpha_panel_lua_v2_remove)) }
        },
        dismissButton = {
            TextButton(onClick = { deleteIndex = -1 }) { Text(stringResource(R.string.alpha_panel_lua_v2_cancel)) }
        },
    )
}

@Composable
private fun LuaSmallAction(glyph: String, help: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.width(30.dp).semantics { contentDescription = help }) {
        Text(glyph, color = if (glyph == "▤") Color(0xFFFF727C) else LuaDarkMuted, fontSize = 17.sp)
    }
}

@Composable
private fun LuaCountRow(title: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = LuaDarkText, modifier = Modifier.weight(1f), fontSize = 12.sp)
        TextButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }, enabled = value > min) {
            Text("−", color = LuaDarkText)
        }
        Text("$value", color = LuaDarkText)
        TextButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max) {
            Text("+", color = LuaDarkText)
        }
    }
}
