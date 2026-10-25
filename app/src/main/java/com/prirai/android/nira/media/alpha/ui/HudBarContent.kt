// Adapted from MPV Alpha v54 / REX-Player, Apache-2.0.
// Canonical commit c6107ce14a9f1768c73f975c5d60b1d8bd71aaed.
package com.prirai.android.nira.media.alpha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Real player and screenshots share this model; old fixtures keep the legacy arrangement. */
internal data class HudBarModel(
  val showBattery: Boolean,
  val showClock: Boolean,
  val showAdjustedRemaining: Boolean,
  val showRemaining: Boolean,
  val showSpeed: Boolean,
  val batteryPercent: Int,
  val isCharging: Boolean,
  val clockText: String,
  val adjustedTimeLabel: String,
  val originalTimeLabel: String,
  val playbackSpeed: Float,
  val lowBatteryColor: Color,
  val clockColor: Color,
  val adjustedRemainingColor: Color,
  val remainingColor: Color,
  val speedColor: Color,
  val fontSize: TextUnit,
  val iconSize: Dp,
  val innerGap: Dp,
  val textWeight: FontWeight,
  val highContrastText: Boolean,
  val gap: Dp,
  val factor: Float,
  val outlineWidthDp: Float = if (highContrastText) 1.2f else 0f,
  val fontFamily: FontFamily = FontFamily.Default,
  val layout: HudLayout? = null,
  val downloadSpeedMbps: Float? = null,
  val downloadColor: Color = Color(0xFFFF9100),
  val rowGap: Dp = (4f * factor).dp,
  val showIcons: Boolean = true,
)

/** Flexible, separately ordered rows; items can be added, removed and moved in settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HudBarContent(
  model: HudBarModel,
  modifier: Modifier = Modifier,
  maxWidth: Dp,
) {
  with(model) {
    val visibleLayout = layout ?: legacyHudLayout(
      showBattery, showClock, showAdjustedRemaining, showRemaining, showSpeed,
    )
    if (visibleLayout.items.isEmpty()) return

    Column(
      modifier = modifier.widthIn(max = (maxWidth - 8.dp).coerceAtLeast(1.dp)),
      verticalArrangement = Arrangement.spacedBy(rowGap),
    ) {
      listOf(visibleLayout.first, visibleLayout.second).forEach { row ->
        if (row.isEmpty()) return@forEach
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(gap),
          verticalArrangement = Arrangement.spacedBy((4f * factor).dp),
        ) {
          row.forEach { item ->
            when (item) {
              HudElement.BATTERY -> {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(innerGap),
                ) {
                  if (showIcons) Icon(Icons.Default.BatteryFull, contentDescription = null,
                    modifier = Modifier.size(iconSize), tint = lowBatteryColor)
                  ContrastVideoLabel(
                    (if (batteryPercent >= 0) "$batteryPercent%" else "--%") +
                      (if (isCharging) " ⚡" else ""),
                    color = lowBatteryColor, fontSize = fontSize, fontWeight = textWeight,
                    outlined = highContrastText, outlineWidthDp = outlineWidthDp,
                    fontFamily = fontFamily, maxLines = 1,
                  )
                }
              }

              HudElement.CLOCK -> {
                Row(verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(innerGap)) {
                  if (showIcons) Icon(Icons.Default.AccessTime, contentDescription = null,
                    modifier = Modifier.size(iconSize), tint = clockColor)
                  ContrastVideoLabel(clockText, color = clockColor, fontSize = fontSize,
                    fontWeight = textWeight, outlined = highContrastText,
                    outlineWidthDp = outlineWidthDp, fontFamily = fontFamily, maxLines = 1)
                }
              }

              HudElement.ADJUSTED -> {
                ContrastVideoLabel(adjustedTimeLabel, color = adjustedRemainingColor,
                  fontSize = fontSize, fontWeight = textWeight, outlined = highContrastText,
                  outlineWidthDp = outlineWidthDp, fontFamily = fontFamily, maxLines = 1)
              }

              HudElement.REMAINING -> {
                Row(verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(innerGap)) {
                  if (showIcons) Icon(Icons.Default.HourglassBottom, contentDescription = null,
                    modifier = Modifier.size(iconSize), tint = remainingColor)
                  ContrastVideoLabel(originalTimeLabel, color = remainingColor,
                    fontSize = fontSize, fontWeight = textWeight, outlined = highContrastText,
                    outlineWidthDp = outlineWidthDp, fontFamily = fontFamily, maxLines = 1)
                }
              }

              HudElement.SPEED -> {
                Row(verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(innerGap)) {
                  if (showIcons) Icon(Icons.Default.PlayArrow, contentDescription = null,
                    modifier = Modifier.size(iconSize), tint = speedColor)
                  ContrastVideoLabel(String.format(Locale.US, "%.2f×", playbackSpeed.coerceAtLeast(0f)),
                    color = speedColor, fontSize = fontSize, fontWeight = textWeight,
                    outlined = highContrastText, outlineWidthDp = outlineWidthDp,
                    fontFamily = fontFamily, maxLines = 1)
                }
              }

              HudElement.DOWNLOAD -> {
                Row(verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(innerGap)) {
                  if (showIcons) Icon(Icons.Default.CloudDownload, contentDescription = null,
                    modifier = Modifier.size(iconSize), tint = downloadColor)
                  ContrastVideoLabel(hudDownloadSpeedText(downloadSpeedMbps),
                    color = downloadColor, fontSize = fontSize, fontWeight = textWeight,
                    outlined = highContrastText, outlineWidthDp = outlineWidthDp,
                    fontFamily = fontFamily, maxLines = 1)
                }
              }
            }
          }
        }
      }
    }
  }
}
