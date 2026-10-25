// Adapted from MPV Alpha v54 / REX-Player, Apache-2.0.
// Canonical commit c6107ce14a9f1768c73f975c5d60b1d8bd71aaed.
package com.prirai.android.nira.media.alpha.ui

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
) {
  val fraction = miniProgressFraction(positionSeconds,durationSeconds) ?: return
  val bufferedFraction:Float? = null
  if (!visible) return
  val thickness=3
  val opacity=65
  val margin=8
  val color = Color(0xFF2B9BFA)
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

