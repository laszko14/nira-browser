// MPV Alpha v54 / REX-Player, Apache-2.0. Adapted from c6107ce14a9f1768c73f975c5d60b1d8bd71aaed.
package com.prirai.android.nira.media.alpha.ui

import com.prirai.android.nira.R
import com.prirai.android.nira.media.alpha.*

import androidx.compose.ui.res.stringResource
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Compact right-side configuration, leaving the original Lua buttons exposed
 * to the LEFT for visual adjustment during a landscape movie.
 *
 * Appearance and script editing use separate tabs so neither wastes space.
 */
@Composable
internal fun LuaButtonsInPlayerDialog(
    prefs: AlphaLuaPreferences,
    engineStatus: String,
    onDismiss: () -> Unit,
    hiddenDuringDrag: Boolean = false,
    manualMinimized: Boolean = false,
    onManualMinimize: (Boolean) -> Unit = {},
) {
    val landscape = LocalConfiguration.current.orientation == ORIENTATION_LANDSCAPE
    val savedTab by prefs.luaEditorLastTab.collectAsState()
    val tab = if (savedTab == 2) 1 else 0
    val autoHideOnDrag by prefs.luaAutoHideOnDrag.collectAsState()
    val isDragHidden = hiddenDuringDrag && autoHideOnDrag
    BackHandler(onBack = onDismiss)
    // This is deliberately in the player window, NOT a Dialog: touches to the
    // exposed Lua grid and drop targets continue to work while settings are open.
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = if (landscape) Alignment.CenterEnd else Alignment.BottomCenter,
    ) {
        if (!isDragHidden && manualMinimized) {
            TextButton(
                modifier = Modifier.align(Alignment.BottomCenter),
                onClick = { onManualMinimize(false) },
            ) { Text(stringResource(R.string.alpha_panel_audit_show_lua_settings), color = LuaDarkText) }
        }
        if (!isDragHidden && !manualMinimized) {
            // The host app may be in light mode. Never inherit black Material3 labels
            // inside this intentionally dark player overlay or its nested editor.
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = LuaDarkAccent,
                    onPrimary = androidx.compose.ui.graphics.Color.White,
                    surface = androidx.compose.ui.graphics.Color(0xFF171D26),
                    onSurface = LuaDarkText,
                    onSurfaceVariant = LuaDarkMuted,
                    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF252D39),
                    outline = androidx.compose.ui.graphics.Color(0xFF728099),
                ),
            ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(if (landscape) .53f else .96f)
                    .fillMaxHeight(if (landscape) .96f else .84f)
                    .padding(end = if (landscape) 6.dp else 0.dp)
                    .imePadding(),
                shape = RoundedCornerShape(20.dp),
                color = androidx.compose.ui.graphics.Color(0xF5161B23),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = .14f)),
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("⚙", fontSize = 20.sp,
                            color = LuaDarkMuted, modifier = Modifier.padding(end = 10.dp))
                        Text(stringResource(R.string.alpha_panel_lua_v2_title), color = LuaDarkText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onManualMinimize(true) }) {
                            Text("—", color = LuaDarkMuted)
                        }
                        TextButton(onClick = onDismiss) { Text("✕", color = LuaDarkMuted) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(androidx.compose.ui.graphics.Color(0xFF242C38),
                                RoundedCornerShape(12.dp)).padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(stringResource(R.string.alpha_panel_lua_v2_appearance),
                            stringResource(R.string.alpha_panel_lua_v2_buttons)).forEachIndexed { i, text ->
                            Box(
                                modifier = Modifier.weight(1f)
                                    .background(if (tab == i) LuaDarkAccent
                                        else androidx.compose.ui.graphics.Color.Transparent,
                                        RoundedCornerShape(9.dp))
                                    .clickable { prefs.luaEditorLastTab.set(if (i == 0) 1 else 2) }
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text, color = LuaDarkText,
                                    fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    if (tab == 0) {
                        Column(modifier = Modifier.weight(1f)
                            .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LuaSoftDarkAppearanceSettings(prefs)
                        }
                    } else {
                        LuaInPlayerCodeBrowser(prefs, Modifier.weight(1f))
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun LuaInPlayerCodeBrowser(prefs: AlphaLuaPreferences, modifier: Modifier = Modifier) {
    val serialized by prefs.customButtons.collectAsState()
    val slots = remember(serialized) { decodeLuaButtonSlots(serialized) }
    var selectedIndex by remember { mutableIntStateOf(-1) }

    // 30 left + 8 right, five archived legacy slots remain editable (no lost codes).
    val listState = rememberLazyListState()

    if (selectedIndex !in 0 until LUA_SLOT_COUNT) {
        LuaSoftDarkButtonList(prefs, onEdit = { selectedIndex = it }, modifier = modifier)
    } else {
        val index = selectedIndex
        val old = slots[index]
        var name by remember(index, old?.id) { mutableStateOf(old?.title ?: "") }
        var clickCode by remember(index, old?.id) { mutableStateOf(old?.content ?: "") }
        var longCode by remember(index, old?.id) { mutableStateOf(old?.longPressContent ?: "") }
        var startCode by remember(index, old?.id) { mutableStateOf(old?.onStartup ?: "") }
        var enabled by remember(index, old?.id) { mutableStateOf(old?.enabled ?: true) }
        var icon by remember(index, old?.id) { mutableStateOf(old?.icon.orEmpty()) }

        fun write(button: CustomButton?, closeEditor: Boolean = true) {
            // Update the current serialized slot, preserving unrelated buttons.
            val latest = decodeLuaButtonSlots(prefs.customButtons.get()).toMutableList()
            latest[index] = button
            prefs.customButtons.set(AlphaSlots.encode(latest))
            if (closeEditor) selectedIndex = -1
        }

        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            OutlinedButton(onClick = { selectedIndex = -1 }) { Text(stringResource(R.string.alpha_panel_audit_lua_button_list, LUA_SLOT_COUNT)) }
            Text(
                stringResource(R.string.alpha_panel_audit_lua_button_index, index + 1, LUA_SLOT_COUNT) +
                    when {
                        index >= LUA_EXTRA_START -> stringResource(R.string.alpha_panel_audit_right_side_4_2)
                        index >= LUA_MAIN_SLOT_COUNT -> stringResource(R.string.alpha_panel_audit_v23_archive_off_screen)
                        else -> stringResource(R.string.alpha_panel_audit_left_side)
                    },
                style = MaterialTheme.typography.titleMedium,
            )
            if (old != null && index < LUA_EXTRA_START) {
                val freeExtra = (LUA_EXTRA_START until LUA_SLOT_COUNT)
                    .firstOrNull { slots[it] == null }
                if (freeExtra != null) {
                    OutlinedButton(
                        onClick = {
                            val latest = decodeLuaButtonSlots(prefs.customButtons.get())
                            if (latest[index]?.id == old.id && latest[freeExtra] == null) {
                                val moved = swapLuaSlotPositions(latest, index, freeExtra)
                                prefs.customButtons.set(AlphaSlots.encode(moved))
                                selectedIndex = -1
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.alpha_panel_audit_move_to_the_8_right_slots)) }
                }
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.alpha_panel_audit_button_name)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = icon, onValueChange = { if (it.length <= 3) icon = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.alpha_panel_lua_v2_icon)) },
                singleLine = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.alpha_panel_audit_enabled), modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            if (old != null) {
                var individualOpen by remember(index) { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { individualOpen = !individualOpen },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (individualOpen) stringResource(R.string.alpha_panel_audit_collapse_visibility_and_colors) else stringResource(R.string.alpha_panel_audit_this_button_s_visibility_and_colors))
                }
                if (individualOpen) {
                    LuaButtonIndividualAppearance(
                        button = old,
                        prefs = prefs,
                        compact = true,
                        onChange = { write(it, closeEditor = false) },
                    )
                }
            } else {
                Text(
                    stringResource(R.string.alpha_panel_audit_save_the_button_to_set_its_visibility_and_colors),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            OutlinedTextField(
                value = clickCode, onValueChange = { clickCode = it },
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.alpha_panel_audit_lua_code_tap)) },
                minLines = 2, maxLines = 5,
            )
            OutlinedTextField(
                value = longCode, onValueChange = { longCode = it },
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.alpha_panel_audit_lua_code_long_press)) },
                minLines = 2, maxLines = 5,
            )
            OutlinedTextField(
                value = startCode, onValueChange = { startCode = it },
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.alpha_panel_audit_lua_code_on_startup)) },
                minLines = 2, maxLines = 5,
            )
            Button(
                onClick = {
                    write(CustomButton(
                        id = old?.id ?: UUID.randomUUID().toString(),
                        title = name.trim(),
                        content = clickCode,
                        longPressContent = longCode,
                        onStartup = startCode,
                        enabled = enabled,
                        alwaysVisible = old?.alwaysVisible,
                        backgroundColor = old?.backgroundColor,
                        textColor = old?.textColor,
                        activeBackgroundColor = old?.activeBackgroundColor,
                        activeTextColor = old?.activeTextColor,
                        icon = icon.trim().ifEmpty { null },
                    ))
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.alpha_panel_audit_save_lua)) }
            if (old != null) {
                var confirmRemoval by remember(index) { mutableStateOf(false) }
                FilledTonalButton(onClick = { confirmRemoval = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.alpha_panel_audit_delete_button))
                }
                if (confirmRemoval) {
                    AlertDialog(
                        onDismissRequest = { confirmRemoval = false },
                        title = { Text(stringResource(R.string.alpha_panel_audit_delete_this_button)) },
                        text = { Text(stringResource(R.string.alpha_panel_audit_lua_remove_help, index + 1)) },
                        confirmButton = {
                            TextButton(onClick = { confirmRemoval = false; write(null) }) {
                                Text(stringResource(R.string.alpha_panel_audit_delete))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmRemoval = false }) { Text(stringResource(R.string.alpha_panel_audit_cancel)) }
                        },
                    )
                }
            }
        }
    }
}
