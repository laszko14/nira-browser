// Extracted from Alpha v54 components/Seekbar.kt (Apache-2.0), c6107ce14a9f1768c73f975c5d60b1d8bd71aaed.
// Standard track/thumb preserved; Koin, chapters, glass and alternative styles excluded.
package com.prirai.android.nira.media.alpha.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardSeekbar(
    position: () -> Float,
    duration: Float,
    readAheadValue: () -> Float,
    isPaused: Boolean = false,
    isScrubbing: Boolean = false,
    useWavySeekbar: Boolean = false,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onSeekCancelled: () -> Unit,
    loopStart: Float? = null,
    loopEnd: Float? = null,
    isCancelActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Original Standard style, with explicit defaults instead of Koin.
    val showSeekbarReadAhead=false
    val primaryColor=MaterialTheme.colorScheme.primary
    val interactionSource=remember { MutableInteractionSource() }
    val isThick=false
    val trackHeightDp=8.dp
    val thumbWidth=6.dp
    val thumbHeight=24.dp
    val thumbShape=CircleShape
    val preventSeekbarTap=false
    val useRelativeSeeking=false

    val currentPosition by rememberUpdatedState(position)
    val currentDuration by rememberUpdatedState(duration)

    val thumbScale by animateFloatAsState(
        targetValue = if (isCancelActive) 0.7f else 1f,
        label = "thumbScale"
    )

    var isDragging by remember { mutableStateOf(false) }
    var dragStartValue by remember { mutableFloatStateOf(0f) }
    var accumulatedDragPx by remember { mutableFloatStateOf(0f) }
    var userPosition by remember { mutableFloatStateOf(position()) }
    var isUserInteracting by remember { mutableStateOf(false) }

    // Update userPosition when not interacting
    if (!isUserInteracting) {
        userPosition = position()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        if (preventSeekbarTap || !currentDuration.isFinite() || currentDuration<=0) return@detectTapGestures
                        val newPosition = (offset.x / size.width) * currentDuration
                        onSeek(newPosition.coerceIn(0f, currentDuration))
                        onSeekFinished()
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        isUserInteracting = true
                        dragStartValue = currentPosition()
                        accumulatedDragPx = 0f
                    },
                    onDragEnd = {
                        isDragging = false
                        isUserInteracting = false
                        onSeekFinished()
                    },
                    onDragCancel = {
                        isDragging = false
                        isUserInteracting = false
                        onSeekCancelled()
                    },
                ) { change, dragAmount ->
                    if (!currentDuration.isFinite() || currentDuration<=0) return@detectDragGestures
                    change.consume()
                    val newPosition = if (useRelativeSeeking) {
                        // Relative seeking: calculate position relative to drag start
                        accumulatedDragPx += dragAmount.x
                        if (currentDuration > 0f) {
                            dragStartValue + (accumulatedDragPx / size.width) * currentDuration
                        } else {
                            currentPosition()
                        }
                    } else {
                        // Absolute seeking: use current finger position
                        (change.position.x / size.width) * currentDuration
                    }
                    userPosition = newPosition.coerceIn(0f, currentDuration)
                    onSeek(userPosition)
                }
            }
    ) {
        val safeDuration = if (duration.isFinite() && duration>0) duration else 1f
        Slider(
            value = finiteAlphaSeekPosition(if (isUserInteracting) userPosition else position(),safeDuration),
            onValueChange = { /* Handled by custom gestures */ },
            onValueChangeFinished = { /* Handled by custom gestures */ },
            valueRange = 0f..safeDuration,
            modifier = Modifier.fillMaxWidth(),
            interactionSource = interactionSource,
            enabled = false, // Disable built-in interaction
            track = { sliderState ->
            val disabledAlpha = 0.3f
            val bufferAlpha = 0.5f

            val isGlassActive=false
            val outerRadiusDp = trackHeightDp / 2

            val trackModifier = Modifier
                .fillMaxWidth()
                .height(trackHeightDp)
                .background(
                    color = if (isGlassActive) Color.White.copy(alpha = 0.15f) 
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = disabledAlpha),
                    shape = RoundedCornerShape(outerRadiusDp)
                )

            Box(
                modifier = trackModifier
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val min = sliderState.valueRange.start
                    val max = sliderState.valueRange.endInclusive
                    val range = (max - min).takeIf { it > 0f } ?: 1f

                    val trackValue = if (isDragging) dragStartValue else sliderState.value
                    val playedFraction = ((trackValue - min) / range).coerceIn(0f, 1f)
                    val readAheadFraction = ((readAheadValue() - min) / range).coerceIn(0f, 1f)

                    val playedPx = size.width * playedFraction
                    val readAheadPx = size.width * readAheadFraction
                    val trackHeight = size.height
                    
                    // Radius for the outer ends of the seekbar
                    val outerRadius = trackHeight / 2f
                    
                    // MODIFIED: For Thick style, inner corners now match the outer rounding
                    val innerRadius = if (isThick) outerRadius else 2.dp.toPx()
                    
                    val thumbTrackGapSize = 14.dp.toPx()
                    val gapHalf = thumbTrackGapSize / 2f
                    val chapterGapHalf = 1.dp.toPx()
                    
                    val thumbFraction = ((sliderState.value - min) / range).coerceIn(0f, 1f)
                    val thumbPx = size.width * thumbFraction
                    val thumbGapStart = (thumbPx - gapHalf).coerceIn(0f, size.width)
                    val thumbGapEnd = (thumbPx + gapHalf).coerceIn(0f, size.width)
                    
                    val chapterGaps = emptyList<Pair<Float,Float>>()

                    val allGaps = (chapterGaps + (thumbGapStart to thumbGapEnd))
                        .filter { it.first < it.second }
                    
                    fun drawSegment(startX: Float, endX: Float, color: Color) {
                        if (endX - startX < 0.5f) return
                        
                        val path = Path()
                        val isOuterLeft = startX <= 0.5f
                        val isInnerLeft = kotlin.math.abs(startX - thumbGapEnd) < 0.5f
                        
                        val cornerRadiusLeft = when {
                            isOuterLeft -> androidx.compose.ui.geometry.CornerRadius(outerRadius)
                            isInnerLeft -> androidx.compose.ui.geometry.CornerRadius(innerRadius)
                            else -> androidx.compose.ui.geometry.CornerRadius.Zero
                        }

                        val isOuterRight = endX >= size.width - 0.5f
                        val isInnerRight = kotlin.math.abs(endX - thumbGapStart) < 0.5f
                        val isBufferRight = kotlin.math.abs(endX - readAheadPx) < 0.5f && readAheadPx > playedPx && showSeekbarReadAhead

                        val cornerRadiusRight = when {
                            isOuterRight || isBufferRight -> androidx.compose.ui.geometry.CornerRadius(outerRadius)
                            isInnerRight -> androidx.compose.ui.geometry.CornerRadius(innerRadius)
                            else -> androidx.compose.ui.geometry.CornerRadius.Zero
                        }
                        
                        path.addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                left = startX,
                                top = 0f,
                                right = endX,
                                bottom = trackHeight,
                                topLeftCornerRadius = cornerRadiusLeft,
                                bottomLeftCornerRadius = cornerRadiusLeft,
                                topRightCornerRadius = cornerRadiusRight,
                                bottomRightCornerRadius = cornerRadiusRight
                            )
                         )
                         drawPath(path, color)
                     }
                     
                     fun drawRangeWithGaps(
                         rangeStart: Float, 
                         rangeEnd: Float, 
                         gaps: List<Pair<Float, Float>>, 
                         color: Color
                     ) {
                         if (rangeEnd <= rangeStart) return
                         val relevantGaps = gaps
                             .filter { (gStart, gEnd) -> gEnd > rangeStart && gStart < rangeEnd }
                             .sortedBy { it.first }
                         
                         var currentPos = rangeStart
                         for ((gStart, gEnd) in relevantGaps) {
                             val segmentEnd = gStart.coerceAtMost(rangeEnd)
                             if (segmentEnd > currentPos) {
                                 drawSegment(currentPos, segmentEnd, color)
                             }
                             currentPos = gEnd.coerceAtLeast(currentPos)
                         }
                         if (currentPos < rangeEnd) {
                             drawSegment(currentPos, rangeEnd, color)
                         }
                     }
                     
                     // 1. Unplayed Background
                     if (!isGlassActive) {
                         drawRangeWithGaps(playedPx, size.width, allGaps, primaryColor.copy(alpha = disabledAlpha))
                     }
                     
                     // 2. Buffer
                     if (showSeekbarReadAhead && readAheadPx > playedPx) {
                         drawRangeWithGaps(playedPx, readAheadPx, allGaps, primaryColor.copy(alpha = bufferAlpha))
                     }
                     
                     // 3. Played
                     if (playedPx > 0f) {
                         drawRangeWithGaps(0f, playedPx, allGaps, primaryColor)
                     }
 
                     // 3. A-B Loop Indicators
                     if (loopStart != null || loopEnd != null) {
                         val loopColor = Color(0xFFFFB300) // Amber/Gold color for loop
                         val markerWidth = 2.dp.toPx()
                         
                         // Draw loop start marker
                         if (loopStart != null) {
                             val startPx = (loopStart / duration).coerceIn(0f, 1f) * size.width
                             drawLine(
                                 color = loopColor,
                                 start = Offset(startPx, 0f),
                                 end = Offset(startPx, size.height),
                                 strokeWidth = markerWidth
                             )
                         }
 
                         // Draw loop end marker
                         if (loopEnd != null) {
                             val endPx = (loopEnd / duration).coerceIn(0f, 1f) * size.width
                             drawLine(
                                 color = loopColor,
                                 start = Offset(endPx, 0f),
                                 end = Offset(endPx, size.height),
                                 strokeWidth = markerWidth
                             )
                         }
 
                         // Draw connected segment if both are set
                         if (loopStart != null && loopEnd != null) {
                             val minPx = (minOf(loopStart, loopEnd) / duration).coerceIn(0f, 1f) * size.width
                             val maxPx = (maxOf(loopStart, loopEnd) / duration).coerceIn(0f, 1f) * size.width
                             
                             // Draw a semi-transparent overlay between A and B
                             drawRect(
                                 color = loopColor.copy(alpha = 0.3f),
                                 topLeft = Offset(minPx, 0f),
                                 size = Size(maxPx - minPx, size.height)
                             )
                         }
                     }
                 }
             }
         },
        thumb = {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = thumbScale
                        scaleY = thumbScale
                    }
                    .width(thumbWidth)
                    .height(thumbHeight)
                    .background(primaryColor, thumbShape)
            )
        }
        )
    }
}


/** Native properties may not have arrived when the fullscreen composition first mounts. */
internal fun finiteAlphaSeekPosition(position:Float,duration:Float):Float {
    val max=duration.takeIf { it.isFinite() && it>0 } ?: 1f
    return (position.takeIf { it.isFinite() } ?: 0f).coerceIn(0f,max)
}
