package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.prirai.android.nira.theme.TabGroupTones
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import mozilla.components.browser.state.state.TabSessionState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Reusable Swipeable Tab Pill Component for Tab Bar
 *
 * This component provides a tab pill with vertical swipe gestures:
 * - Swipe DOWN: Show menu
 * - Swipe UP: Delete/close tab
 *
 * Features:
 * - Visual feedback during swipe (background color + icon)
 * - Configurable swipe threshold
 * - Smooth animations
 * - Group color theming support
 * - Selection state support
 *
 * Usage:
 * ```
 * SwipeableTabPill(
 *     tab = tabSession,
 *     isSelected = currentTab == tabSession.id,
 *     groupColor = 0xFFE57373.toInt(),
 *     onTabClick = { handleClick(tab.id) },
 *     onTabClose = { handleClose(tab.id) },
 *     onShowMenu = { showMenu(tab) }
 * )
 * ```
 */

/**
 * Swipeable tab pill with vertical gesture support
 *
 * @param tab The tab session state to display
 * @param isSelected Whether this tab is currently selected
 * @param groupColor Optional group color for theming (as Int)
 * @param onTabClick Callback when tab is clicked
 * @param onTabClose Callback when tab should be closed (swipe up)
 * @param onShowMenu Callback when menu should be shown (swipe down)
 * @param modifier Optional modifier
 * @param swipeThreshold Vertical distance in pixels required to trigger action (default: 40f)
 */
@Composable
fun SwipeableTabPill(
    tab: TabSessionState,
    isSelected: Boolean,
    groupColor: Int? = null,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onShowMenu: () -> Unit,
    modifier: Modifier = Modifier,
    swipeThreshold: Float = 40f,
    iconSizeDp: Int = 34,
    isDragInProgress: () -> Boolean = { false }
) {
    // Reset offset when the tab id changes so the state does not leak between
    // adjacent slots when Compose reuses this call site for a different tab
    // (e.g. after a neighbouring pill is closed).
    var offsetY by remember(tab.id) { mutableStateOf(0f) }
    val maxOffset = 80f
    // Callbacks change on recomposition; do not restart a running pointer stream.
    val currentOnTabClose by rememberUpdatedState(onTabClose)
    val currentOnShowMenu by rememberUpdatedState(onShowMenu)
    val currentDragInProgress by rememberUpdatedState(isDragInProgress)

    Box(
        modifier = modifier.size(iconSizeDp.coerceIn(10, 52).dp)
    ) {
        // Background indicator based on swipe direction
        if (abs(offsetY) > 5f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        when {
                            offsetY < -5f -> MaterialTheme.colorScheme.errorContainer // Swipe up - delete
                            offsetY > 5f -> MaterialTheme.colorScheme.primaryContainer // Swipe down - menu
                            else -> Color.Transparent
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (offsetY < 0) Icons.Default.Delete else Icons.Default.MoreVert,
                    contentDescription = if (offsetY < 0) "Delete" else "Menu",
                    tint = if (offsetY < 0)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Tab content with swipe gesture
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offsetY.roundToInt()) }
                .pointerInput(tab.id) {
                    awaitEachGesture {
                        // A single pointer owns this swipe, and only until Compose/DnD
                        // consumes the stream. Never promote another finger to owner.
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val owner = SwipePillGestureOwner(
                            touchSlop = viewConfiguration.touchSlop,
                            threshold = swipeThreshold,
                            maxOffset = maxOffset
                        )
                        owner.begin(down.id.value)
                        var outcome = SwipePillOutcome.NONE
                        try {
                            while (owner.isActive) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                val otherFinger = event.changes.any {
                                    it.id != down.id && it.pressed
                                }
                                val consumed = event.changes.any { it.isConsumed }
                                if (change == null || otherFinger || consumed ||
                                    currentDragInProgress()
                                ) {
                                    owner.cancel()
                                    break
                                }
                                if (!change.pressed) {
                                    // Let descendants finish this event before committing.
                                    awaitPointerEvent(PointerEventPass.Final)
                                    outcome = owner.release(
                                        down.id.value,
                                        consumed = event.changes.any { it.isConsumed },
                                        dragInProgress = currentDragInProgress()
                                    )
                                    break
                                }
                                val delta = change.positionChange()
                                val claimed = owner.move(
                                    pointerId = down.id.value,
                                    deltaX = delta.x,
                                    deltaY = delta.y,
                                    dragInProgress = currentDragInProgress()
                                )
                                offsetY = owner.offsetY
                                if (!owner.isActive) break
                                if (claimed) change.consume()

                                // An unclaimed swipe must yield to a child that consumes
                                // the event in a later pass (click, scrolling, or DnD).
                                awaitPointerEvent(PointerEventPass.Final)
                                if (!claimed && event.changes.any { it.isConsumed }) {
                                    owner.cancel()
                                    break
                                }
                            }
                        } finally {
                            // Includes parent cancellation, pointer-input disposal and
                            // exceptions. Never leave a moved pill behind.
                            owner.cancel()
                            offsetY = 0f
                        }
                        when (outcome) {
                            SwipePillOutcome.CLOSE -> currentOnTabClose()
                            SwipePillOutcome.MENU -> currentOnShowMenu()
                            SwipePillOutcome.NONE -> Unit
                        }
                    }
                },
            shape = CircleShape,
            color = when {
                groupColor != null -> {
                    val tone = TabGroupTones.forGroup(groupColor)
                    Color(if (isSelected) tone.activeStart else tone.tabStart)
                }
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else -> Color.Transparent
            },
            border = if (isSelected) {
                BorderStroke(
                    1.dp,
                    groupColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                )
            } else {
                null
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxSize().clickable(onClick = onTabClick)
                        .semantics { contentDescription = getTabDisplayTitle(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    FaviconImage(tab = tab, size = (iconSizeDp * 0.58f).roundToInt().coerceAtLeast(6).dp)
                }

            }
        }
    }
}

/**
 * Example usage in GroupPill:
 *
 * ```kotlin
 * group.tabs.forEachIndexed { index, tab ->
 *     if (index > 0) {
 *         // Divider
 *     }
 *
 *     SwipeableTabPill(
 *         tab = tab,
 *         isSelected = selectedTabId == tab.id,
 *         groupColor = group.color,
 *         onTabClick = { onTabClick(tab.id) },
 *         onTabClose = { onTabClose(tab.id) },
 *         onShowMenu = {
 *             menuTab = tab
 *             showTabMenu = true
 *         }
 *     )
 * }
 * ```
 */

/**
 * Get display title for a tab, avoiding "New Tab" for homepage
 */
fun getTabDisplayTitle(tab: TabSessionState): String {
    return when {
        // For homepage with no title, show "New Tab"
        tab.content.url == "about:homepage" && tab.content.title.isEmpty() -> "New Tab"
        // For other pages, show title if available
        tab.content.title.isNotEmpty() -> tab.content.title
        // If no title but has URL (not homepage), show URL
        tab.content.url.isNotEmpty() && tab.content.url != "about:homepage" -> tab.content.url
        // Last resort fallback
        else -> "New Tab"
    }
}
