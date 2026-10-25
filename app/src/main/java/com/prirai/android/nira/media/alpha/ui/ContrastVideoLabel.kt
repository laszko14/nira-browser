// Adapted from MPV Alpha v54 / REX-Player, Apache-2.0.
// Canonical commit c6107ce14a9f1768c73f975c5d60b1d8bd71aaed.
package com.prirai.android.nira.media.alpha.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Improve readability over bright and dark videos while preserving every configured
 * foreground color. Does not sample or process any video frames.
 */
@Composable
internal fun ContrastVideoLabel(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    outlined: Boolean,
    fontFamily: FontFamily = FontFamily.Default,
    outlineWidthPx: Float? = null,
    // null retains the appearance of old screenshot tests/callers.
    outlineWidthDp: Float? = null,
    outlineColorOverride: Color? = null,
    shadowStyle: Shadow? = null,
    glowColor: Color? = null,
    glowSizePx: Float = 0f,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val outlineColor = outlineColorOverride ?: if (color.luminance() >= 0.5f) Color.Black else Color.White
    // v23 used up to 2.4dp (stroke diameter 4.8dp): it overwhelmed 11sp
    // labels and looked like displaced glyphs. Cap the clean round outline.
    val widthDp = (outlineWidthDp ?: if (outlined) 0.65f else 0f).coerceIn(0f, 1.05f)
    // A true glyph stroke, measured in physical pixels, not a blurred duplicate.
    val outlineWidth = outlineWidthPx?.coerceIn(0f, 6f)
        ?: with(LocalDensity.current) { widthDp.dp.toPx() }
    val haloPx = glowSizePx.coerceIn(0f, 12f)
    Box(contentAlignment = Alignment.Center) {
        // Render OUTSIDE the existing black text contour, not around the rectangular tile.
        // The broad translucent stroke + blurred zero-offset shadow create a halo without
        // sampling video frames, changing touch targets or overwriting the chosen font.
        if (glowColor != null && haloPx > 0f && glowColor.alpha > 0f) {
            Text(
                text = text,
                color = glowColor.copy(alpha = glowColor.alpha * .75f),
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                textAlign = textAlign,
                maxLines = maxLines,
                overflow = overflow,
                style = TextStyle(
                    drawStyle = Stroke(
                        width = outlineWidth * 2f + haloPx * 1.65f,
                        join = StrokeJoin.Round,
                    ),
                    shadow = Shadow(
                        color = glowColor.copy(alpha = glowColor.alpha * .85f),
                        offset = Offset.Zero,
                        blurRadius = haloPx * 2.2f,
                    ),
                ),
            )
        }
        if (outlineWidth > 0f) {
            Text(
                text = text,
                color = outlineColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                textAlign = textAlign,
                maxLines = maxLines,
                overflow = overflow,
                style = TextStyle(drawStyle = Stroke(width = outlineWidth * 2f, join = StrokeJoin.Round)),
            )
        }
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            style = TextStyle(shadow = shadowStyle),
        )
    }
}

/** Only selected speed buttons receive a halo; saved sizes are bounded for readability. */
internal fun luaActiveSpeedHaloPx(active: Boolean, enabled: Boolean, savedSizePx: Int): Float =
    if (active && enabled) savedSizePx.coerceIn(0, 12).toFloat() else 0f

/** Deliberately discrete real font faces rather than near-identical 5-point steps. */
internal fun fontWeightForReadability(percent: Int): FontWeight = when (percent.coerceIn(0, 100)) {
    in 0..19 -> FontWeight.Normal
    in 20..39 -> FontWeight.Medium
    in 40..59 -> FontWeight.SemiBold
    in 60..74 -> FontWeight.Bold
    in 75..89 -> FontWeight.ExtraBold
    else -> FontWeight.Black
}

/** Smooth 0..1.05dp radius: no excessive glow at the maximum. */
internal fun outlineWidthDpForReadability(percent: Int): Float =
    percent.coerceIn(0, 100) * 0.0105f
