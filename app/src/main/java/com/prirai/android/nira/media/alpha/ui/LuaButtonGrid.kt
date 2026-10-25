// MPV Alpha v54 / REX-Player, Apache-2.0. Adapted from c6107ce14a9f1768c73f975c5d60b1d8bd71aaed.
package com.prirai.android.nira.media.alpha.ui

import com.prirai.android.nira.R
import com.prirai.android.nira.media.alpha.*

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * v24: EXACTLY 30 on-screen positions on the left and eight on the right.
 * Slots 30..34 are retained only as legacy data overflow and never intercept touches.
 * Each grid is a passive layout during playback: only rendered buttons handle input.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LuaButtonGrid(
    preferences: AlphaLuaPreferences,
    buttons: List<AlphaLuaButtonState>,
    playbackSpeed: Float,
    showAllButtons: Boolean,
    editMode: Boolean,
    onButtonClick: (String) -> Unit,
    onButtonLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDragStateChange: (Boolean) -> Unit = {},
    leftOffsetDp: Int = 0,
    previewDragging: Boolean = false,
    availableHeightDp:Int=240,
) {
    val serialized by preferences.customButtons.collectAsState()
    val savedSlots = remember(serialized) { decodeLuaButtonSlots(serialized) }
    val readyById = remember(buttons) { buttons.associateBy { it.id } }
    val columns by preferences.luaPanelColumns.collectAsState()
    val scale by preferences.luaButtonScalePercent.collectAsState()
    val transparency by preferences.luaButtonBackgroundTransparency.collectAsState()
    val rowGap by preferences.luaButtonRowGap.collectAsState()
    val columnGap by preferences.luaButtonColumnGap.collectAsState()
    val buttonWidth by preferences.luaButtonWidth.collectAsState()
    val buttonHeight by preferences.luaButtonHeight.collectAsState()
    val fontSize by preferences.luaButtonFontSize.collectAsState()
    val fontWeightPercent by preferences.luaButtonFontWeightPercent.collectAsState()
    val outlinePercent by preferences.luaButtonOutlinePercent.collectAsState()
    val fontId by preferences.luaButtonTypeface.collectAsState()
    val userOutlineEnabled by preferences.luaButtonTextOutlineEnabled.collectAsState()
    val userOutlineHalfPx by preferences.luaButtonTextOutlineHalfPx.collectAsState()
    val userOutlineArgb by preferences.luaButtonTextOutlineColor.collectAsState()
    val speedGlowEnabled by preferences.luaActiveSpeedGlowEnabled.collectAsState()
    val speedGlowArgb by preferences.luaActiveSpeedGlowColor.collectAsState()
    val speedGlowSize by preferences.luaActiveSpeedGlowSizePx.collectAsState()
    val activeBackgroundEnabled by preferences.luaActiveSpeedBackgroundEnabled.collectAsState()
    val selectedFontFamily = luaFontFamily(fontId)
    val userOutline = if (userOutlineEnabled) userOutlineHalfPx.coerceIn(0, 12) / 2f else null
    val positionY by preferences.luaButtonPositionY.collectAsState()
    val cornerRadius by preferences.luaButtonCornerRadius.collectAsState()
    val borderOpacity by preferences.luaButtonBorderOpacity.collectAsState()
    val buttonColor by preferences.luaButtonBaseColor.collectAsState()
    val activeColor by preferences.luaButtonActiveColor.collectAsState()
    val activeTransparency by preferences.luaButtonActiveTransparency.collectAsState()
    val textColor by preferences.luaButtonTextColor.collectAsState()
    val activeTextColor by preferences.luaButtonActiveTextColor.collectAsState()
    val globalAlwaysVisible by preferences.luaButtonAlwaysVisible.collectAsState()
    val minimalSoft by preferences.luaButtonMinimalSoft.collectAsState()
    val softV2 by preferences.luaButtonMinimalSoftV2.collectAsState()
    val softShape by preferences.luaSoftShape.collectAsState()
    val softBg by preferences.luaSoftBackgroundColor.collectAsState()
    val softText by preferences.luaSoftTextColor.collectAsState()
    val softActiveText by preferences.luaSoftActiveTextColor.collectAsState()
    val softContrast by preferences.luaSoftContrast.collectAsState()
    val softShadow by preferences.luaSoftTextShadow.collectAsState()
    val softIcons by preferences.luaSoftIconStyle.collectAsState()
    val rightColumns by preferences.luaRightColumns.collectAsState()
    val emptySlots by preferences.luaEmptySlots.collectAsState()
    var softTouchSequence by remember { mutableIntStateOf(0) }
    var softTouched by remember { mutableStateOf(false) }
    LaunchedEffect(minimalSoft, softTouchSequence) {
        softTouched = minimalSoft && softTouchSequence > 0
        if (softTouched) {
            delay(LUA_SOFT_TOUCH_DURATION_MS)
            softTouched = false
        }
    }
    val softNormalOpacity by animateFloatAsState(
        targetValue = if (softTouched) LUA_SOFT_TOUCH_ALPHA else if (softV2) LUA_SOFT_V2_IDLE_ALPHA else LUA_SOFT_IDLE_ALPHA,
        animationSpec = tween(durationMillis = 240),
        label = "luaSoftTouchFade",
    )

    val requestedColumns = columns.coerceIn(1, 8)
    val factor = scale.coerceIn(70, 150) / 100f
    val width = (buttonWidth.coerceIn(30, 140) * factor).dp
    val requestedHeight = (buttonHeight.coerceIn(22, 80) * factor).dp
    val regionWidth=(LocalConfiguration.current.screenWidthDp-32).coerceAtLeast(100)
    val cellWidth=width.value+columnGap.coerceIn(0,24)
    val colCount=minOf(requestedColumns,((regionWidth*.65f)/cellWidth).toInt().coerceAtLeast(1))
    val rightCount=minOf(rightColumns.coerceIn(1,8),((regionWidth*.35f)/cellWidth).toInt().coerceAtLeast(1))
    val rowSpace = rowGap.coerceIn(0, 24).dp
    val colSpace = columnGap.coerceIn(0, 24).dp
    val maxHeight = (LocalConfiguration.current.screenHeightDp -
        92 - luaButtonVerticalPositionDp(positionY)).coerceIn(40,availableHeightDp.coerceAtLeast(40)).dp
    val shape = if (minimalSoft && softV2) when (softShape.coerceIn(0, 3)) {
        0 -> CircleShape
        1 -> RoundedCornerShape(0.dp)
        3 -> RoundedCornerShape(2.dp)
        else -> RoundedCornerShape((cornerRadius.coerceIn(0, 24) * factor).dp)
    } else RoundedCornerShape((cornerRadius.coerceIn(0, 24) * factor).dp)
    val alpha = (1f - transparency.coerceIn(0, 100) / 100f) * .86f
    val activeOpacity = (if (activeBackgroundEnabled) 1f else 0f) *
        (1f - activeTransparency.coerceIn(0, 100) / 100f)

    fun isVisible(index: Int): Boolean {
        val saved = savedSlots.getOrNull(index) ?: return false
        if (!saved.enabled || saved.title.isBlank()) return false
        val live = readyById[saved.id] ?: return false
        return showAllButtons || (live.alwaysVisible ?: globalAlwaysVisible)
    }
    val lastLeft = if ((editMode && emptySlots != 0) || (!editMode && emptySlots == 2)) LUA_MAIN_SLOT_COUNT else
        (0 until LUA_MAIN_SLOT_COUNT).indexOfLast(::isVisible) + 1
    val lastRight = if ((editMode && emptySlots != 0) || (!editMode && emptySlots == 2)) LUA_SLOT_COUNT else
        (LUA_EXTRA_START until LUA_SLOT_COUNT).lastOrNull(::isVisible)?.plus(1)
            ?: LUA_EXTRA_START
    val leftRows = (0 until lastLeft).toList().chunked(colCount)
    val rightRows = (LUA_EXTRA_START until lastRight).toList().chunked(rightCount)

    val rows=maxOf(leftRows.size,rightRows.size,1)
    val height=minOf(requestedHeight.value,
        ((maxHeight.value-(rows-1)*rowSpace.value)/rows).coerceAtLeast(22f)).dp

    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragDistance by remember { mutableStateOf(Offset.Zero) }
    val tileBounds = remember { mutableStateMapOf<Int, Rect>() }
    var gridRoot by remember { mutableStateOf(Offset.Zero) }
    val currentDragRect = tileBounds[draggedIndex]
    val dropTarget = if (editMode && currentDragRect != null) {
        luaDropTargetAt(tileBounds, currentDragRect.center + dragDistance)
    } else null

    fun finishDrag(cancel: Boolean) {
        val index = draggedIndex
        val target = if (cancel) null else tileBounds[index]?.let {
            luaDropTargetAt(tileBounds, it.center + dragDistance)
        }
        if (target != null && index != target) {
            val latest = decodeLuaButtonSlots(preferences.customButtons.get())
            if (index in latest.indices && latest[index] != null) {
                val moved = swapLuaSlotPositions(latest, index, target)
                preferences.customButtons.set(AlphaSlots.encode(moved))
            }
        }
        draggedIndex = -1
        dragDistance = Offset.Zero
        onDragStateChange(false)
    }

    @Composable
    fun Slot(index: Int) {
        val saved = savedSlots[index]
        val live = saved?.let { readyById[it.id] }
        val button = if (editMode && saved != null && saved.title.isNotBlank()) {
            live ?: AlphaLuaButtonState(
                id = saved.id, label = saved.title, isLeft = true,
                alwaysVisible = saved.alwaysVisible,
                backgroundColor = saved.backgroundColor, textColor = saved.textColor,
                activeBackgroundColor = saved.activeBackgroundColor,
                activeTextColor = saved.activeTextColor,
            )
        } else if (isVisible(index)) live else null
        val visible = (editMode && emptySlots != 0) || button != null
        val isDragging = draggedIndex == index
        val boundModifier = Modifier.width(width).height(height).then(
            if (editMode) Modifier.onGloballyPositioned { tileBounds[index] = it.boundsInRoot() }
            else Modifier,
        )
        if (!visible) {
            Spacer(boundModifier)
        } else if (button == null) {
            Box(
                modifier = boundModifier.border(
                    if (dropTarget == index) 2.dp else 1.dp,
                    if (dropTarget == index) Color(0xFF80D8FF)
                    else Color.White.copy(alpha = .30f), shape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text("＋", color = Color.White.copy(alpha = .40f),
                    fontSize = (fontSize.coerceIn(8, 22) * factor).sp)
            }
        } else {
            val label = button.label.trim()
            val raw = if (label.endsWith("x", true) || label.endsWith("×"))
                label.dropLast(1).replace(',', '.').toFloatOrNull() else null
            val active = button.active
            val haloPx = luaActiveSpeedHaloPx(active, speedGlowEnabled, speedGlowSize)
            val bg = if (active) button.activeBackgroundColor ?: activeColor
                     else button.backgroundColor ?: buttonColor
            val fg = if (active) button.activeTextColor ?: activeTextColor
                     else button.textColor ?: textColor
            // The geometry and pointer target are identical in both skins.
            // Only the visual layer fades: even a fully invisible tile is clickable.
            val interactionModifier = if (editMode) Modifier.pointerInput(index) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        draggedIndex = index
                        dragDistance = Offset.Zero
                        onDragStateChange(true)
                    },
                    onDragEnd = { finishDrag(false) },
                    onDragCancel = { finishDrag(true) },
                    onDrag = { change, amount ->
                        change.consume()
                        dragDistance += amount
                    },
                )
            } else Modifier.combinedClickable(
                onClick = {
                    if (minimalSoft) softTouchSequence++
                    onButtonClick(button.id)
                },
                onLongClick = {
                    if (minimalSoft) softTouchSequence++
                    onButtonLongClick(button.id)
                },
            )
            val drawingModifier = boundModifier
                .graphicsLayer {
                    this.alpha = when {
                        isDragging -> .25f
                        !minimalSoft || editMode -> 1f
                        else -> if (softV2) {
                            if (active) LUA_SOFT_V2_ACTIVE_ALPHA else softNormalOpacity
                        } else luaSoftEffectiveAlpha(
                            if (active) LUA_SOFT_ACTIVE_ALPHA else softNormalOpacity,
                            transparency,
                        )
                    }
                }
                .then(if (minimalSoft) {
                    if (isDragging || dropTarget == index) {
                        Modifier.border(2.dp, Color(0xFF80D8FF), shape)
                    } else Modifier
                } else {
                    Modifier.background(
                        Color(bg).copy(alpha = if (active) activeOpacity else alpha), shape,
                    ).border(
                        if (isDragging || dropTarget == index) 2.dp else 1.dp,
                        if (isDragging || dropTarget == index) Color(0xFF80D8FF)
                        else Color.White.copy(alpha = if (active) .65f * activeOpacity
                            else borderOpacity.coerceIn(0, 100) / 100f),
                        shape,
                    )
                })
                .then(interactionModifier)

            if (minimalSoft) {
                LuaMinimalSoftTile(
                    text = if (softV2) luaSafeIconLabel(label, saved?.icon, softIcons) else label,
                    active = active,
                    fontSize = (fontSize.coerceIn(8, 22) * factor).sp,
                    activeBackgroundOpacity = (if (softV2) LUA_SOFT_V2_ACTIVE_BACKGROUND_ALPHA
                        else LUA_SOFT_ACTIVE_BACKGROUND_ALPHA) * activeOpacity,
                    modifier = drawingModifier,
                    shape = shape,
                    v2 = softV2,
                    shapeMode = softShape,
                    circleDiameterDp = minOf(width.value, height.value),
                    normalBackgroundColor = Color(saved?.backgroundColor ?: softBg),
                    activeBackgroundColor = Color(saved?.activeBackgroundColor ?: softBg),
                    foreground = Color(saved?.textColor ?: softText),
                    activeForeground = Color(saved?.activeTextColor ?: softActiveText),
                    normalBackgroundOpacity = if (softV2) luaSoftV2BackgroundOpacity(transparency) else 0f,
                    borderOpacity = borderOpacity,
                    fontWeight = if (softV2) fontWeightForReadability(fontWeightPercent) else androidx.compose.ui.text.font.FontWeight.Medium,
                    contrastProfile = softContrast,
                    textShadow = softShadow,
                    fontFamily = selectedFontFamily,
                    userOutlinePx = userOutline,
                    userOutlineColor = Color(userOutlineArgb),
                    activeBackgroundEnabled = activeBackgroundEnabled,
                    activeGlowColor = if (haloPx > 0f) Color(speedGlowArgb) else null,
                    activeGlowSizePx = haloPx,
                )
            } else {
                Box(contentAlignment = Alignment.Center, modifier = drawingModifier) {
                    ContrastVideoLabel(
                        text = label, color = Color(fg),
                        textAlign = TextAlign.Center,
                        fontSize = (fontSize.coerceIn(8, 22) * factor).sp,
                        fontWeight = fontWeightForReadability(fontWeightPercent),
                        fontFamily = selectedFontFamily,
                        outlineWidthPx = userOutline,
                        outlineColorOverride = if (userOutline != null) Color(userOutlineArgb) else null,
                        outlined = userOutline != null || outlinePercent > 0,
                        glowColor = if (haloPx > 0f) Color(speedGlowArgb) else null,
                        glowSizePx = haloPx,
                        outlineWidthDp = outlineWidthDpForReadability(outlinePercent),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    // Passive container occupies the width but never consumes touch itself.
    Box(modifier = modifier.fillMaxWidth()
        .onGloballyPositioned { gridRoot = it.positionInRoot() }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.offset(x = leftOffsetDp.coerceIn(-16, 240).dp)
                    .heightIn(max = maxHeight).clipToBounds().then(
                    if (editMode) Modifier.verticalScroll(rememberScrollState()) else Modifier
                ),
                verticalArrangement = Arrangement.spacedBy(rowSpace),
            ) {
                leftRows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(colSpace)) {
                        row.forEach { index -> Slot(index) }
                    }
                }
            }
            Column(modifier=Modifier.heightIn(max=maxHeight).clipToBounds().then(if(editMode) Modifier.verticalScroll(rememberScrollState()) else Modifier), verticalArrangement = Arrangement.spacedBy(rowSpace)) {
                rightRows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(colSpace)) {
                        row.forEach { index -> Slot(index) }
                    }
                }
            }
        }
        // A separate drag preview is drawn above BOTH regions; the source tile's
        // pointer input remains mounted when the right-hand settings panel hides.
        if (editMode && currentDragRect != null) {
            val source = savedSlots[draggedIndex]
            Box(
                modifier = Modifier.zIndex(100f)
                    .offset {
                        IntOffset(
                            (currentDragRect.left - gridRoot.x + dragDistance.x).roundToInt(),
                            (currentDragRect.top - gridRoot.y + dragDistance.y).roundToInt(),
                        )
                    }
                    .width(width).height(height)
                    .background(Color(0xDB303B4C), shape)
                    .border(2.dp, Color(0xFF80D8FF), shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(source?.title.orEmpty(), color = Color.White,
                    fontSize = (fontSize.coerceIn(8, 22) * factor).sp,
                    textAlign = TextAlign.Center, maxLines = 1)
            }
        }
        if (previewDragging && editMode) {
            Text(stringResource(R.string.alpha_panel_audit_moving_settings_hidden),
                modifier = Modifier.align(Alignment.BottomCenter),
                fontSize = 10.sp, color = Color.White)
        }
    }
}
