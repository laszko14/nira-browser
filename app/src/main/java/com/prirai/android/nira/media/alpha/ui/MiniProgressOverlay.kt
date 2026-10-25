// Adapted from MPV Alpha v54 / REX-Player, Apache-2.0.
// Canonical commit c6107ce14a9f1768c73f975c5d60b1d8bd71aaed.
package com.prirai.android.nira.media.alpha.ui

import com.prirai.android.nira.media.alpha.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
internal fun miniProgressFraction(position: Float, duration: Float): Float? {
  if (!duration.isFinite() || duration <= 0f || !position.isFinite()) return null
  return (position / duration).coerceIn(0f, 1f)
}

@Composable
internal fun MiniProgressBar(
  positionSeconds: Float,
  durationSeconds: Float,
  visible: Boolean,
  navigationBarBottom: Dp,
  bufferedUntilSeconds: Float? = null,
  settings:AlphaPreferences=AlphaPreferences(),
) {
  val fraction = miniProgressFraction(positionSeconds,durationSeconds) ?: return
  val bufferedFraction:Float? = null
  val p=AlphaRenderedValues(settings)
  if (!visible || !p.bool("mini_progress_enabled",true)) return
  val thickness=p.int("mini_progress_thickness_px",3)
  val opacity=p.int("mini_progress_opacity_percent",65)
  val margin=p.int("mini_progress_bottom_margin_dp",8)
  val color=runCatching { Color(android.graphics.Color.parseColor(
    p.text("mini_progress_color_hex","#2B9BFA"))) }.getOrDefault(Color(0xFF2B9BFA))
  val bufferColor = Color(0xFFFF9100)
  val alpha = opacity.coerceIn(0, 100) / 100f
  val strokeWidthPx = thickness.coerceIn(1, 8).toFloat()
  val lineHeight = with(LocalDensity.current) { strokeWidthPx.toDp() }
  Box(Modifier.fillMaxSize()) {
    Canvas(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(start = 18.dp, end = 18.dp, bottom = margin.coerceIn(0, 30).dp + navigationBarBottom)
        .height(lineHeight),
    ) {
      val midY = size.height / 2f
      drawLine(
        color = Color(0xFFD4DCE1).copy(alpha = alpha * 0.42f),
        start = Offset(strokeWidthPx / 2f, midY),
        end = Offset((size.width - strokeWidthPx / 2f).coerceAtLeast(strokeWidthPx / 2f), midY),
        strokeWidth = strokeWidthPx,
        cap = StrokeCap.Round,
      )
      // Draw only the measured cache range, behind the played section.
      if (bufferedFraction != null) {
        drawLine(
          color = bufferColor.copy(alpha = alpha),
          start = Offset(strokeWidthPx / 2f + (size.width - strokeWidthPx) * fraction, midY),
          end = Offset(
            (strokeWidthPx / 2f + (size.width - strokeWidthPx) * bufferedFraction).coerceAtMost(size.width),
            midY,
          ),
          strokeWidth = strokeWidthPx,
          cap = StrokeCap.Round,
        )
      }
      if (fraction > 0f) {
        drawLine(
          color = color.copy(alpha = alpha),
          start = Offset(strokeWidthPx / 2f, midY),
          end = Offset(
            (strokeWidthPx / 2f + (size.width - strokeWidthPx) * fraction).coerceAtMost(size.width),
            midY,
          ),
          strokeWidth = strokeWidthPx,
          cap = StrokeCap.Round,
        )
      }
    }
  }
}
