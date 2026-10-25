// MPV Alpha v54 / REX-Player, Apache-2.0. Adapted from c6107ce14a9f1768c73f975c5d60b1d8bd71aaed.
package com.prirai.android.nira.media.alpha.ui

import com.prirai.android.nira.R
import com.prirai.android.nira.media.alpha.*

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

/** Extended ready-to-use text colors. HEX input still supports every custom ARGB color. */
private val LUA_TEXT_PALETTE = listOf(
    0xFFFFFFFF.toInt(), 0xFF92BBFF.toInt(), 0xFFFFFFAE.toInt(),
    0xFFB0F0BA.toInt(), 0xFFFFACAC.toInt(), 0xFF000000.toInt(),
    0xFFECEFF1.toInt(), 0xFFB0BEC5.toInt(), 0xFF78909C.toInt(),
    0xFF616161.toInt(), 0xFF424242.toInt(), 0xFFFFF59D.toInt(),
    0xFFFFE082.toInt(), 0xFFFFD54F.toInt(), 0xFFFFC107.toInt(),
    0xFFFF9800.toInt(), 0xFFFF7043.toInt(), 0xFFFF5252.toInt(),
    0xFFE53935.toInt(), 0xFFB71C1C.toInt(), 0xFFFF80AB.toInt(),
    0xFFFF4081.toInt(), 0xFFEC407A.toInt(), 0xFFAD1457.toInt(),
    0xFFCE93D8.toInt(), 0xFFBA68C8.toInt(), 0xFFAB47BC.toInt(),
    0xFF7E57C2.toInt(), 0xFF673AB7.toInt(), 0xFFB39DDB.toInt(),
    0xFF9FA8DA.toInt(), 0xFF7986CB.toInt(), 0xFF536DFE.toInt(),
    0xFF448AFF.toInt(), 0xFF2196F3.toInt(), 0xFF40C4FF.toInt(),
    0xFF80DEEA.toInt(), 0xFF26C6DA.toInt(), 0xFF00BCD4.toInt(),
    0xFF4DB6AC.toInt(), 0xFF009688.toInt(), 0xFF00E676.toInt(),
    0xFF66BB6A.toInt(), 0xFF388E3C.toInt(), 0xFFCDDC39.toInt(),
    0xFF8BC34A.toInt(), 0xFF795548.toInt(), 0xFF071426.toInt(),
    0xFF143D29.toInt(), 0xFF29244B.toInt(),
)
private val LUA_ACTIVE_TEXT_PALETTE =
    listOf(0xFF071426.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
        0xFFFFFFAE.toInt(), 0xFF143D29.toInt(), 0xFF29244B.toInt()) +
        LUA_TEXT_PALETTE.filterNot { it in listOf(0xFF071426.toInt(), 0xFFFFFFFF.toInt(),
            0xFF000000.toInt(), 0xFFFFFFAE.toInt(), 0xFF143D29.toInt(), 0xFF29244B.toInt()) }

/** One reversible style switch; no stored per-button appearance values are rewritten. */
@Composable
private fun LuaMinimalSoftToggle(prefs: AlphaLuaPreferences) {
    val enabled by prefs.luaButtonMinimalSoft.collectAsState()
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.alpha_panel_lua_minimal_soft_title),
                style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.alpha_panel_lua_minimal_soft_description),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = prefs.luaButtonMinimalSoft::set)
    }
}

/** Shared settings UI: accessible both in Preferences and on top of a playing film. */
@Composable
internal fun LuaButtonAppearanceSettings(prefs: AlphaLuaPreferences, compact: Boolean = false, showQuick: Boolean = true) {
    val cols by prefs.luaPanelColumns.collectAsState()
    val width by prefs.luaButtonWidth.collectAsState()
    val height by prefs.luaButtonHeight.collectAsState()
    val font by prefs.luaButtonFontSize.collectAsState()
    val scale by prefs.luaButtonScalePercent.collectAsState()
    val transparent by prefs.luaButtonBackgroundTransparency.collectAsState()
    val rowGap by prefs.luaButtonRowGap.collectAsState()
    val columnGap by prefs.luaButtonColumnGap.collectAsState()
    val posX by prefs.luaButtonPositionX.collectAsState()
    val posY by prefs.luaButtonPositionY.collectAsState()
    val radius by prefs.luaButtonCornerRadius.collectAsState()
    val border by prefs.luaButtonBorderOpacity.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 7.dp)) {
        if (showQuick) {
            LuaButtonQuickSettings(prefs, compact = compact)
        } else {
            LuaMinimalSoftToggle(prefs)
            LuaFontReadabilitySettings(prefs, compact)
        }
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_columns), cols, 1, 8, "", compact = compact) { prefs.luaPanelColumns.set(it) }
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_button_width), width, 30, 140, " dp", compact = compact) { prefs.luaButtonWidth.set(it) }
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_button_height), height, 22, 80, " dp", compact = compact) { prefs.luaButtonHeight.set(it) }
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_font_size), font, 8, 22, " sp", compact = compact) { prefs.luaButtonFontSize.set(it) }
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_grid_scale), scale, 70, 150, "%", compact = compact) { prefs.luaButtonScalePercent.set(it) }
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_column_spacing), columnGap, 0, 24, " dp", compact = compact) { prefs.luaButtonColumnGap.set(it) }
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_row_spacing), rowGap, 0, 24, " dp", compact = compact) { prefs.luaButtonRowGap.set(it) }
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_right_offset), posX, -16, 240, " dp", compact = compact) { prefs.luaButtonPositionX.set(it) }
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_downward_offset), posY, LUA_POSITION_Y_MIN_DP, LUA_POSITION_Y_MAX_DP, " dp", compact = compact) { prefs.luaButtonPositionY.set(it) }
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_background_transparency), transparent, 0, 100, "%", compact = compact) { prefs.luaButtonBackgroundTransparency.set(it) }
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_corner_radius), radius, 0, 24, " dp", compact = compact) { prefs.luaButtonCornerRadius.set(it) }
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_border), border, 0, 100, "%", compact = compact) { prefs.luaButtonBorderOpacity.set(it) }
        OutlinedButton(
            onClick = {
                prefs.luaPanelColumns.set(5)
                prefs.luaButtonWidth.set(52)
                prefs.luaButtonHeight.set(27)
                prefs.luaButtonFontSize.set(11)
                prefs.luaButtonMinimalSoft.set(true)
                prefs.luaButtonHighContrastText.set(false)
                prefs.luaButtonBoldText.set(false)
                prefs.luaButtonFontWeightPercent.set(0)
                prefs.luaButtonOutlinePercent.set(0)
                prefs.luaButtonScalePercent.set(100)
                prefs.luaButtonColumnGap.set(2)
                prefs.luaButtonRowGap.set(2)
                prefs.luaButtonPositionX.set(0)
                prefs.luaButtonPositionY.set(100)
                prefs.luaButtonBackgroundTransparency.set(20)
                prefs.luaButtonCornerRadius.set(7)
                prefs.luaButtonBorderOpacity.set(20)
                prefs.luaButtonBaseColor.set(0xFF252631.toInt())
                prefs.luaButtonActiveColor.set(0xFF92BBFF.toInt())
                prefs.luaButtonActiveTransparency.set(0)
                prefs.luaButtonTextColor.set(0xFFFFFFFF.toInt())
                prefs.luaButtonActiveTextColor.set(0xFF071426.toInt())
                prefs.luaButtonAlwaysVisible.set(false)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.alpha_panel_audit_restore_defaults)) }
    }
}

/**
 * Important appearance options visible immediately in the ORIGINAL Lua editor
 * as well as in the compact right-hand panel during movie playback.
 */
@Composable
internal fun LuaButtonQuickSettings(prefs: AlphaLuaPreferences, compact: Boolean = false) {
    val persistent by prefs.luaButtonAlwaysVisible.collectAsState()
    val baseColor by prefs.luaButtonBaseColor.collectAsState()
    val activeColor by prefs.luaButtonActiveColor.collectAsState()
    val activeTransparency by prefs.luaButtonActiveTransparency.collectAsState()
    val fontColor by prefs.luaButtonTextColor.collectAsState()
    val activeTextColor by prefs.luaButtonActiveTextColor.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
        LuaMinimalSoftToggle(prefs)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.alpha_panel_audit_always_visible_by_default),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.alpha_panel_audit_disable_to_keep_only_selected_buttons_visible_when_controls_are_hidden),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = persistent,
                onCheckedChange = prefs.luaButtonAlwaysVisible::set,
            )
        }

        LuaFontReadabilitySettings(prefs, compact)
        LuaPalette(
            stringResource(R.string.alpha_panel_audit_text_color),
            fontColor,
            prefs.luaButtonTextColor::set,
            LUA_TEXT_PALETTE,
            compact,
        )
        LuaPalette(
            stringResource(R.string.alpha_panel_audit_active_speed_text_color),
            activeTextColor,
            prefs.luaButtonActiveTextColor::set,
            LUA_ACTIVE_TEXT_PALETTE,
            compact,
        )
        LuaPalette(
            stringResource(R.string.alpha_panel_audit_button_background_color),
            baseColor,
            prefs.luaButtonBaseColor::set,
            listOf(0xFF252631.toInt(), 0xFF121212.toInt(), 0xFF344E67.toInt(),
                0xFF2F5137.toInt(), 0xFF6A3547.toInt(), 0xFF555555.toInt()),
            compact,
        )
        LuaPalette(
            stringResource(R.string.alpha_panel_audit_active_button_color),
            activeColor,
            prefs.luaButtonActiveColor::set,
            listOf(0xFF92BBFF.toInt(), 0xFF80E6B0.toInt(), 0xFFFCCB6B.toInt(),
                0xFFFF8888.toInt(), 0xFFCFA5FF.toInt(), 0xFFFFFFFF.toInt()),
            compact,
        )
        LuaOptionSlider(
            stringResource(R.string.alpha_panel_audit_active_button_transparency),
            activeTransparency, 0, 100, "%", compact = compact,
        ) { prefs.luaButtonActiveTransparency.set(it) }
    }
}

/**
 * Per-button style & persistence overrides are kept in the button data itself.
 * They move with a button when the list is reordered. A null color inherits
 * the current global value; pressing Reset returns all attributes to defaults.
 */
/** Independent 0–100% sliders shown in both the original and landscape Lua editors. */
@Composable
private fun LuaFontReadabilitySettings(prefs: AlphaLuaPreferences, compact: Boolean) {
    val weight by prefs.luaButtonFontWeightPercent.collectAsState()
    val outline by prefs.luaButtonOutlinePercent.collectAsState()
    LuaOptionSlider(stringResource(R.string.alpha_panel_audit_font_weight), weight, 0, 100, "%", compact = compact) {
        prefs.luaButtonFontWeightPercent.set(it)
    }
    LuaOptionSlider(stringResource(R.string.alpha_panel_audit_contrast_outline_strength), outline, 0, 100, "%", compact = compact) {
        prefs.luaButtonOutlinePercent.set(it)
    }
    if (!compact) {
        Text(
            stringResource(R.string.alpha_panel_audit_lua_readability_help),
            style = MaterialTheme.typography.labelSmall,
        )
        // Wrap the three presets at narrow widths / enlarged system fonts;
        // a fixed Row squeezes the last label into a vertical clipped column.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(onClick = {
                prefs.luaButtonFontWeightPercent.set(0)
                prefs.luaButtonOutlinePercent.set(0)
            }) { Text(stringResource(R.string.alpha_panel_audit_natural)) }
            OutlinedButton(onClick = {
                prefs.luaButtonFontWeightPercent.set(60)
                prefs.luaButtonOutlinePercent.set(50)
            }) { Text(stringResource(R.string.alpha_panel_audit_readable)) }
            OutlinedButton(onClick = {
                prefs.luaButtonFontWeightPercent.set(90)
                prefs.luaButtonOutlinePercent.set(85)
            }) { Text(stringResource(R.string.alpha_panel_audit_strong)) }
        }
    }
}

@Composable
internal fun LuaButtonIndividualAppearance(
    button: CustomButton,
    prefs: AlphaLuaPreferences,
    onChange: (CustomButton) -> Unit,
    compact: Boolean = false,
) {
    val globalAlways by prefs.luaButtonAlwaysVisible.collectAsState()
    val globalBg by prefs.luaButtonBaseColor.collectAsState()
    val globalFg by prefs.luaButtonTextColor.collectAsState()
    val globalActiveBg by prefs.luaButtonActiveColor.collectAsState()
    val globalActiveFg by prefs.luaButtonActiveTextColor.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 8.dp)) {
        Text(
            stringResource(R.string.alpha_panel_audit_lua_individual_settings, button.title),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.alpha_panel_audit_always_visible), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.alpha_panel_audit_stays_visible_over_the_video_when_other_buttons_disappear),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = button.alwaysVisible ?: globalAlways,
                onCheckedChange = { onChange(button.copy(alwaysVisible = it)) },
            )
        }
        Text(
            if (button.alwaysVisible == null) stringResource(R.string.alpha_panel_audit_visibility_global_default)
            else stringResource(R.string.alpha_panel_audit_visibility_set_for_this_button_only),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LuaPalette(
            stringResource(R.string.alpha_panel_audit_button_background),
            button.backgroundColor ?: globalBg,
            { onChange(button.copy(backgroundColor = it)) },
            listOf(0xFF252631.toInt(), 0xFF121212.toInt(), 0xFF344E67.toInt(),
                0xFF2F5137.toInt(), 0xFF6A3547.toInt(), 0xFF555555.toInt()),
            compact,
        )
        LuaPalette(
            stringResource(R.string.alpha_panel_audit_text_color),
            button.textColor ?: globalFg,
            { onChange(button.copy(textColor = it)) },
            LUA_TEXT_PALETTE,
            compact,
        )
        LuaPalette(
            stringResource(R.string.alpha_panel_audit_active_speed_background),
            button.activeBackgroundColor ?: globalActiveBg,
            { onChange(button.copy(activeBackgroundColor = it)) },
            listOf(0xFF92BBFF.toInt(), 0xFF80E6B0.toInt(), 0xFFFCCB6B.toInt(),
                0xFFFF8888.toInt(), 0xFFCFA5FF.toInt(), 0xFFFFFFFF.toInt()),
            compact,
        )
        LuaPalette(
            stringResource(R.string.alpha_panel_audit_active_speed_text_color),
            button.activeTextColor ?: globalActiveFg,
            { onChange(button.copy(activeTextColor = it)) },
            LUA_ACTIVE_TEXT_PALETTE,
            compact,
        )
        OutlinedButton(
            onClick = {
                onChange(
                    button.copy(
                        alwaysVisible = null,
                        backgroundColor = null,
                        textColor = null,
                        activeBackgroundColor = null,
                        activeTextColor = null,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.alpha_panel_audit_restore_this_button_to_defaults)) }
    }
}

@Composable
private fun LuaOptionSlider(
    title: String,
    value: Int,
    low: Int,
    high: Int,
    unit: String,
    compact: Boolean = false,
    onChange: (Int) -> Unit,
) {
    if (compact) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(0.42f),
                maxLines = 2,
            )
            Slider(
                modifier = Modifier.weight(0.49f),
                value = value.coerceIn(low, high).toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = low.toFloat()..high.toFloat(),
            )
            Text(
                text = "$value$unit",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(0.20f),
                maxLines = 1,
            )
        }
    } else {
        Column {
            Text(title + ": " + value + unit, style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = value.coerceIn(low, high).toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = low.toFloat()..high.toFloat(),
            )
        }
    }
}

private fun readLuaArgbColor(text: String): Int? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    val number = hex.toLongOrNull(16) ?: return null
    return if (hex.length == 6) (0xFF000000L or number).toInt() else number.toInt()
}

/** Preset swatches PLUS a custom #RRGGBB / #AARRGGBB value. */
@Composable
private fun LuaPalette(
    title: String,
    selected: Int,
    onSelect: (Int) -> Unit,
    palette: List<Int>,
    compact: Boolean = false,
) {
    val shownHex = "#" + java.lang.Long.toHexString(selected.toLong() and 0xFFFFFFFFL)
        .padStart(8, '0').uppercase()
    var hexDraft by remember(title, selected) { mutableStateOf(shownHex) }
    var expanded by remember(title) { mutableStateOf(false) }
    val candidate = readLuaArgbColor(hexDraft)

    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 5.dp)) {
        Text(title, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium)
        val visiblePalette = if (expanded || palette.size <= 14) palette else palette.take(14)
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)) {
            visiblePalette.chunked(7).forEach { swatchRow ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    swatchRow.forEach { color ->
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(if (compact) 26.dp else 32.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .clickable {
                                    onSelect(color)
                                    hexDraft = "#" + java.lang.Long.toHexString(color.toLong() and 0xFFFFFFFFL)
                                        .padStart(8, '0').uppercase()
                                }
                                .padding(3.dp),
                        ) {
                            if (color == selected) {
                                Text(
                                    "✓",
                                    color = if (Color(color).luminance() > 0.5f) Color.Black else Color.White,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (palette.size > 14) {
            OutlinedButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) stringResource(R.string.alpha_panel_audit_show_fewer_colors) else stringResource(R.string.alpha_panel_audit_lua_more_colors, palette.size))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = hexDraft,
                onValueChange = { if (it.length <= 9) hexDraft = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.labelMedium,
                label = { Text(stringResource(R.string.alpha_panel_audit_hex_color), style = MaterialTheme.typography.labelSmall) },
                placeholder = { Text(shownHex) },
                isError = hexDraft.isNotBlank() && candidate == null,
            )
            OutlinedButton(
                onClick = { candidate?.let(onSelect) },
                enabled = candidate != null && candidate != selected,
            ) { Text(stringResource(R.string.alpha_panel_audit_apply)) }
        }
    }
}


/** Compact live controls used by the new three-tab in-player editor. */
@Composable
internal fun LuaButtonCompactQuickSettings(prefs: AlphaLuaPreferences) {
    val cols by prefs.luaPanelColumns.collectAsState()
    val width by prefs.luaButtonWidth.collectAsState()
    val height by prefs.luaButtonHeight.collectAsState()
    val scale by prefs.luaButtonScalePercent.collectAsState()
    val columnGap by prefs.luaButtonColumnGap.collectAsState()
    val rowGap by prefs.luaButtonRowGap.collectAsState()
    val alpha by prefs.luaButtonBackgroundTransparency.collectAsState()
    val posX by prefs.luaButtonPositionX.collectAsState()
    val posY by prefs.luaButtonPositionY.collectAsState()
    val textColor by prefs.luaButtonTextColor.collectAsState()
    val activeColor by prefs.luaButtonActiveColor.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.alpha_panel_audit_frequently_used), style = MaterialTheme.typography.titleSmall)
        LuaMinimalSoftToggle(prefs)
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_columns), cols, 1, 8, "", compact = true, onChange = prefs.luaPanelColumns::set)
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_width), width, 30, 140, " dp", compact = true, onChange = prefs.luaButtonWidth::set)
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_height), height, 22, 80, " dp", compact = true, onChange = prefs.luaButtonHeight::set)
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_grid_scaling), scale, 70, 150, "%", compact = true, onChange = prefs.luaButtonScalePercent::set)
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_horizontal_spacing), columnGap, 0, 24, " dp", compact = true, onChange = prefs.luaButtonColumnGap::set)
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_vertical_spacing), rowGap, 0, 24, " dp", compact = true, onChange = prefs.luaButtonRowGap::set)
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_x_position), posX, 0, 240, " dp", compact = true, onChange = prefs.luaButtonPositionX::set)
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_y_position), posY, LUA_POSITION_Y_MIN_DP, LUA_POSITION_Y_MAX_DP, " dp", compact = true, onChange = prefs.luaButtonPositionY::set)
        LuaOptionSlider(stringResource(R.string.alpha_panel_audit_background_transparency), alpha, 0, 100, "%", compact = true,
            onChange = prefs.luaButtonBackgroundTransparency::set)
        LuaFontReadabilitySettings(prefs, compact = true)
        LuaQuickColorRow(stringResource(R.string.alpha_panel_audit_text), textColor, prefs.luaButtonTextColor::set,
            LUA_TEXT_PALETTE.take(8))
        LuaQuickColorRow(stringResource(R.string.alpha_panel_audit_active_button), activeColor, prefs.luaButtonActiveColor::set,
            listOf(0xFF92BBFF.toInt(), 0xFF80E6B0.toInt(), 0xFFFCCB6B.toInt(),
                0xFFFF8888.toInt(), 0xFFCFA5FF.toInt(), 0xFFFFFFFF.toInt()))
        Text(stringResource(R.string.alpha_panel_audit_more_colors_and_settings_are_available_in_the_appearance_tab),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LuaQuickColorRow(title: String, selected: Int, onSelect: (Int) -> Unit, colors: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            colors.forEach { color ->
                Box(
                    modifier = Modifier.size(28.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .clickable { onSelect(color) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected == color) Text("✓",
                        color = if (Color(color).luminance() > 0.5f) Color.Black else Color.White)
                }
            }
        }
    }
}

/** Grouped version of the appearance options, with independent section resets. */
@Composable
internal fun LuaButtonOrganizedAppearanceSettings(prefs: AlphaLuaPreferences) {
    val cols by prefs.luaPanelColumns.collectAsState()
    val width by prefs.luaButtonWidth.collectAsState()
    val height by prefs.luaButtonHeight.collectAsState()
    val font by prefs.luaButtonFontSize.collectAsState()
    val scale by prefs.luaButtonScalePercent.collectAsState()
    val rowGap by prefs.luaButtonRowGap.collectAsState()
    val columnGap by prefs.luaButtonColumnGap.collectAsState()
    val posX by prefs.luaButtonPositionX.collectAsState()
    val posY by prefs.luaButtonPositionY.collectAsState()
    val alwaysVisible by prefs.luaButtonAlwaysVisible.collectAsState()
    val corner by prefs.luaButtonCornerRadius.collectAsState()
    val border by prefs.luaButtonBorderOpacity.collectAsState()
    val baseColor by prefs.luaButtonBaseColor.collectAsState()
    val activeColor by prefs.luaButtonActiveColor.collectAsState()
    val fontColor by prefs.luaButtonTextColor.collectAsState()
    val activeFont by prefs.luaButtonActiveTextColor.collectAsState()
    val transparency by prefs.luaButtonBackgroundTransparency.collectAsState()
    val activeTransparency by prefs.luaButtonActiveTransparency.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        LuaMinimalSoftToggle(prefs)
        LuaAppearanceSection(stringResource(R.string.alpha_panel_audit_size_and_layout), initiallyOpen = true) {
            LuaOptionSlider(stringResource(R.string.alpha_panel_audit_columns), cols, 1, 8, "", true, prefs.luaPanelColumns::set)
            LuaOptionSlider(stringResource(R.string.alpha_panel_audit_width), width, 30, 140, " dp", true, prefs.luaButtonWidth::set)
            LuaOptionSlider(stringResource(R.string.alpha_panel_audit_height), height, 22, 80, " dp", true, prefs.luaButtonHeight::set)
            LuaOptionSlider(stringResource(R.string.alpha_panel_audit_font_size), font, 8, 22, " sp", true, prefs.luaButtonFontSize::set)
            LuaOptionSlider(stringResource(R.string.alpha_panel_audit_scale), scale, 70, 150, "%", true, prefs.luaButtonScalePercent::set)
            LuaOptionSlider(stringResource(R.string.alpha_panel_audit_spacing_between_columns), columnGap, 0, 24, " dp", true, prefs.luaButtonColumnGap::set)
            LuaOptionSlider(stringResource(R.string.alpha_panel_audit_spacing_between_rows), rowGap, 0, 24, " dp", true, prefs.luaButtonRowGap::set)
            OutlinedButton(onClick = {
                prefs.luaPanelColumns.set(5)
                prefs.luaButtonWidth.set(52)
                prefs.luaButtonHeight.set(27)
                prefs.luaButtonFontSize.set(11)
                prefs.luaButtonScalePercent.set(100)
                prefs.luaButtonColumnGap.set(2)
                prefs.luaButtonRowGap.set(2)
            }) { Text(stringResource(R.string.alpha_panel_audit_reset_sizes_and_spacing)) }
        }
        LuaAppearanceSection(stringResource(R.string.alpha_panel_audit_position_and_visibility)) {
            LuaOptionSlider(stringResource(R.string.alpha_panel_audit_x_position), posX, 0, 240, " dp", true, prefs.luaButtonPositionX::set)
            LuaOptionSlider(stringResource(R.string.alpha_panel_audit_y_position), posY, LUA_POSITION_Y_MIN_DP, LUA_POSITION_Y_MAX_DP, " dp", true, prefs.luaButtonPositionY::set)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.alpha_panel_audit_always_visible_by_default), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium)
                Switch(checked = alwaysVisible, onCheckedChange = prefs.luaButtonAlwaysVisible::set)
            }
            OutlinedButton(onClick = {
                prefs.luaButtonPositionX.set(0)
                prefs.luaButtonPositionY.set(100)
                prefs.luaButtonAlwaysVisible.set(false)
            }) { Text(stringResource(R.string.alpha_panel_audit_reset_position)) }
        }
        LuaAppearanceSection(stringResource(R.string.alpha_panel_audit_text_colors_and_readability)) {
            LuaFontReadabilitySettings(prefs, compact = true)
            LuaPalette(stringResource(R.string.alpha_panel_audit_normal_text), fontColor, prefs.luaButtonTextColor::set,
                LUA_TEXT_PALETTE, compact = true)
            LuaPalette(stringResource(R.string.alpha_panel_audit_active_speed_text), activeFont,
                prefs.luaButtonActiveTextColor::set, LUA_ACTIVE_TEXT_PALETTE, compact = true)
            OutlinedButton(onClick = {
                prefs.luaButtonTextColor.set(0xFFFFFFFF.toInt())
                prefs.luaButtonActiveTextColor.set(0xFF071426.toInt())
                prefs.luaButtonHighContrastText.set(false)
                prefs.luaButtonBoldText.set(false)
                prefs.luaButtonFontWeightPercent.set(0)
                prefs.luaButtonOutlinePercent.set(0)
            }) { Text(stringResource(R.string.alpha_panel_audit_reset_text_appearance)) }
        }
        LuaAppearanceSection(stringResource(R.string.alpha_panel_audit_background_colors)) {
            LuaPalette(stringResource(R.string.alpha_panel_audit_normal_button_background), baseColor, prefs.luaButtonBaseColor::set,
                listOf(0xFF252631.toInt(), 0xFF121212.toInt(), 0xFF344E67.toInt(),
                    0xFF2F5137.toInt(), 0xFF6A3547.toInt(), 0xFF555555.toInt()), true)
            LuaPalette(stringResource(R.string.alpha_panel_audit_active_button_background), activeColor, prefs.luaButtonActiveColor::set,
                listOf(0xFF92BBFF.toInt(), 0xFF80E6B0.toInt(), 0xFFFCCB6B.toInt(),
                    0xFFFF8888.toInt(), 0xFFCFA5FF.toInt(), 0xFFFFFFFF.toInt()), true)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                LuaAppearancePreview(stringResource(R.string.alpha_panel_audit_normal), baseColor, fontColor, transparency)
                LuaAppearancePreview(stringResource(R.string.alpha_panel_audit_active), activeColor, activeFont, activeTransparency)
            }
            OutlinedButton(onClick = {
                prefs.luaButtonBaseColor.set(0xFF252631.toInt())
                prefs.luaButtonActiveColor.set(0xFF92BBFF.toInt())
            }) { Text(stringResource(R.string.alpha_panel_audit_reset_background_colors)) }
        }
        LuaAppearanceSection(stringResource(R.string.alpha_panel_audit_transparency_and_border)) {
            LuaOptionSlider(stringResource(R.string.alpha_panel_audit_normal_background), transparency, 0, 100, "%", true,
                prefs.luaButtonBackgroundTransparency::set)
            LuaOptionSlider(stringResource(R.string.alpha_panel_audit_active_background), activeTransparency, 0, 100, "%", true,
                prefs.luaButtonActiveTransparency::set)
            LuaOptionSlider(stringResource(R.string.alpha_panel_audit_border), border, 0, 100, "%", true,
                prefs.luaButtonBorderOpacity::set)
            LuaOptionSlider(stringResource(R.string.alpha_panel_audit_rounding), corner, 0, 24, " dp", true,
                prefs.luaButtonCornerRadius::set)
            OutlinedButton(onClick = {
                prefs.luaButtonBackgroundTransparency.set(20)
                prefs.luaButtonActiveTransparency.set(0)
                prefs.luaButtonBorderOpacity.set(20)
                prefs.luaButtonCornerRadius.set(7)
            }) { Text(stringResource(R.string.alpha_panel_audit_reset_transparency_and_border)) }
        }
    }
}

@Composable
private fun LuaAppearancePreview(title: String, bg: Int, fg: Int, transparent: Int) {
    Box(
        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
            .background(Color(bg).copy(alpha = 1f - transparent.coerceIn(0, 100) / 100f))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = Color(fg), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun LuaAppearanceSection(
    title: String,
    initiallyOpen: Boolean = false,
    content: @Composable () -> Unit,
) {
    var open by remember(title) { mutableStateOf(initiallyOpen) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        OutlinedButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text(if (open) "▲" else "▼")
            }
        }
        if (open) Column(verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 4.dp)) {
            content()
        }
    }
}
