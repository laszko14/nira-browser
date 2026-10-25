// Adapted from MPV Alpha v54 / REX-Player, Apache-2.0.
// Canonical commit c6107ce14a9f1768c73f975c5d60b1d8bd71aaed.
package com.prirai.android.nira.media.alpha.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

@Suppress("ModifierClickableOrder")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ControlsButton(
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onDoubleClick: (() -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
  title: String? = null,
  color: Color? = null,
  isActive: Boolean = false,
) {
  val interactionSource = remember { MutableInteractionSource() }
  // v54 no-background variant; user XML/glass configuration is not imported yet.
  val hideBackground = true
  val enableGlass = false
  val matchTheme = false
  val inDock = false
  val controlColor = Color.White

  val buttonShape = if (inDock) RoundedCornerShape(14.dp) else RoundedCornerShape(12.dp)

  val activeSurfaceColor = when {
    hideBackground -> Color.Transparent
    enableGlass -> Color.White.copy(alpha = 0.20f)
    matchTheme -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
    else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.90f)
  }

  val activeContentColor = when {
    enableGlass -> Color.White
    matchTheme -> {
      if (hideBackground) MaterialTheme.colorScheme.secondary
      else MaterialTheme.colorScheme.onSecondaryContainer
    }
    else -> MaterialTheme.colorScheme.primary
  }

  val activeBorderColor = when {
    hideBackground -> null
    enableGlass -> BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
    matchTheme -> BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
    else -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
  }

  val normalSurfaceColor = when {
    inDock -> Color.Transparent
    enableGlass && !hideBackground -> Color.Transparent
    hideBackground -> Color.Transparent
    matchTheme -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
  }

  val normalContentColor = when {
    color != null -> color
    matchTheme -> {
      if (hideBackground) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.onPrimaryContainer
    }
    else -> if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface
  }

  val normalBorderColor = if (inDock || enableGlass || hideBackground) {
    null
  } else {
    BorderStroke(
      1.dp,
      if (matchTheme) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
      else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
  }

  val surfaceColor = if (isActive) activeSurfaceColor else normalSurfaceColor
  val contentColor = if (isActive) activeContentColor else normalContentColor
  val border = if (isActive) activeBorderColor else normalBorderColor

  val glassModifier = Modifier

  Surface(
    modifier =
      modifier
        .then(glassModifier)
        .clip(buttonShape)
        .combinedClickable(
          onClick = {
            onClick()
          },
          onDoubleClick = onDoubleClick?.let {
            {
                it()
            }
          },
          onLongClick = onLongClick,
          interactionSource = interactionSource,
          indication = ripple(),
        ),
    shape = buttonShape,
    color = surfaceColor,
    contentColor = contentColor,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    border = border,
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = if (hideBackground && isActive) normalContentColor else contentColor,
        modifier =
          Modifier
            .padding(8.dp)
            .size(24.dp)
            .align(Alignment.Center),
      )

      if (hideBackground && isActive) {
        androidx.compose.foundation.layout.Box(
          modifier =
            Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 3.dp)
              .size(width = 12.dp, height = 2.dp)
              .clip(CircleShape)
              .background(normalContentColor),
        )
      }
    }
  }
}
