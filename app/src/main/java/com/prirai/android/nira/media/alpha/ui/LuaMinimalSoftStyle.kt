// MPV Alpha v54 / REX-Player, Apache-2.0. Adapted from c6107ce14a9f1768c73f975c5d60b1d8bd71aaed.
package com.prirai.android.nira.media.alpha.ui

import com.prirai.android.nira.R
import com.prirai.android.nira.media.alpha.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** v29 constants stay intact when the reversible v2 appearance is off. */
internal val LuaSoftText = Color(0xFFC9D1D9)
internal val LuaSoftActiveText = Color(0xFF9EB6C8)
internal val LuaSoftActiveBackground = Color(0xFF26333D)

internal const val LUA_SOFT_IDLE_ALPHA = .55f
internal const val LUA_SOFT_TOUCH_ALPHA = .85f
internal const val LUA_SOFT_ACTIVE_ALPHA = .90f
internal const val LUA_SOFT_ACTIVE_BACKGROUND_ALPHA = .30f
internal const val LUA_SOFT_V2_IDLE_ALPHA = .68f
internal const val LUA_SOFT_V2_TOUCH_ALPHA = .85f
internal const val LUA_SOFT_V2_ACTIVE_ALPHA = .95f
internal const val LUA_SOFT_V2_ACTIVE_BACKGROUND_ALPHA = .50f
internal const val LUA_SOFT_TOUCH_DURATION_MS = 3000L

/** Only used by v29. Retain its old transparency logic and saved settings. */
internal fun luaSoftEffectiveAlpha(softAlpha: Float, savedTransparency: Int): Float =
    (softAlpha * (100 - savedTransparency.coerceIn(0, 100)) / 80f).coerceIn(0f, 1f)

/** v2: background opacity does not also fade the text, icon and touch target. */
internal fun luaSoftV2BackgroundOpacity(savedTransparency: Int): Float =
    .55f * (100 - savedTransparency.coerceIn(0, 100)) / 100f

/** Increasing contrast changes both outline coverage and shadow, not only a label. */
internal fun luaSoftContrastOutlinePx(profile: Int): Float = when (profile.coerceIn(0, 3)) {
    0 -> 0f
    1 -> .65f
    2 -> .82f
    else -> 1f
}
internal fun luaSoftContrastShadowAlpha(profile: Int): Float = when (profile.coerceIn(0, 3)) {
    0 -> .20f
    1 -> .22f
    2 -> .25f
    else -> .28f
}

/** v2 affects rendering only; caller's original dimensions and gesture modifier remain on
 * the OUTER Box. Circle draws a real circle inside that unchanged touch rectangle. */
@Composable
internal fun LuaMinimalSoftTile(
    text: String,
    active: Boolean,
    fontSize: TextUnit,
    activeBackgroundOpacity: Float,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(7.dp),
    v2: Boolean = false,
    shapeMode: Int = 2,
    circleDiameterDp: Float = 27f,
    normalBackgroundColor: Color = Color(0xFF1F2A33),
    activeBackgroundColor: Color = Color(0xFF1F2A33),
    foreground: Color = LuaSoftText,
    activeForeground: Color = LuaSoftActiveText,
    normalBackgroundOpacity: Float = 0f,
    borderOpacity: Int = 20,
    fontWeight: FontWeight = FontWeight.Medium,
    contrastProfile: Int = 1,
    textShadow: Boolean = true,
    fontFamily: FontFamily = FontFamily.Default,
    userOutlinePx: Float? = null,
    userOutlineColor: Color = Color.Black,
    activeBackgroundEnabled: Boolean = true,
    activeGlowColor: Color? = null,
    activeGlowSizePx: Float = 0f,
) {
    val density = LocalDensity.current
    val lineRadiusDp = with(density) {
        (if (v2) luaSoftContrastOutlinePx(contrastProfile) else 1f) / (2f * this.density)
    }
    val shadow = if (v2 && textShadow) {
        Shadow(
            color = Color(0xFF0A1018).copy(alpha = luaSoftContrastShadowAlpha(contrastProfile)),
            offset = Offset(0f, with(density) { .5f.dp.toPx() }),
            blurRadius = with(density) { 1.dp.toPx() },
        )
    } else null

    val fill = when {
        active && !activeBackgroundEnabled -> Color.Transparent
        !v2 && active -> LuaSoftActiveBackground.copy(alpha = activeBackgroundOpacity.coerceIn(0f, 1f))
        !v2 -> Color.Transparent
        active -> activeBackgroundColor.copy(
            alpha = activeBackgroundColor.alpha * activeBackgroundOpacity.coerceIn(0f, 1f))
        else -> normalBackgroundColor.copy(
            alpha = normalBackgroundColor.alpha * normalBackgroundOpacity.coerceIn(0f, 1f))
    }
    val borderAlpha = if (active && !activeBackgroundEnabled) 0f
        else if (v2) borderOpacity.coerceIn(0, 100) / 200f else 0f
    val borderWidth = with(density) { (1f / this.density).dp }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val tileModifier = if (v2 && shapeMode == 0) {
            Modifier.size(circleDiameterDp.coerceAtLeast(1f).dp)
        } else {
            Modifier.matchParentSize()
        }
        Box(
            modifier = tileModifier
                .background(fill, shape)
                .then(if (borderAlpha > 0f) Modifier.border(borderWidth,
                    Color.White.copy(alpha = borderAlpha), shape) else Modifier),
        )
        ContrastVideoLabel(
            text = text,
            color = if (!v2) { if (active) LuaSoftActiveText else LuaSoftText }
                    else if (active) activeForeground else foreground,
            textAlign = TextAlign.Center,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            outlined = userOutlinePx != null || !v2 || contrastProfile > 0,
            outlineWidthPx = userOutlinePx,
            outlineWidthDp = lineRadiusDp,
            outlineColorOverride = if (userOutlinePx != null) userOutlineColor else if (v2) Color(0xFF1E2630) else null,
            shadowStyle = shadow,
            glowColor = if (active) activeGlowColor else null,
            glowSizePx = if (active) activeGlowSizePx else 0f,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
