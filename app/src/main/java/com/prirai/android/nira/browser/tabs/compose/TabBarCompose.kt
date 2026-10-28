package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.withFrameNanos
import com.prirai.android.nira.browser.tabs.ActiveTabVisibility
import com.prirai.android.nira.browser.tabs.TabStripBounds
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.prirai.android.nira.theme.TabGroupTones
import com.prirai.android.nira.browser.tabgroups.TabGroupData
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import mozilla.components.browser.state.state.TabSessionState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Horizontal tab bar with Chromium-style drag & drop support
 * Uses two-layer rendering: static layer + floating drag layer
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabBarCompose(
    tabs: List<TabSessionState>,
    viewModel: TabViewModel,
    orderManager: TabOrderManager,
    selectedTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoScrollTrigger: Long = 0L
) {
    val scope = rememberCoroutineScope()
    val tabContext = LocalContext.current
    val stripPreferences = remember(tabContext) { com.prirai.android.nira.preferences.UserPreferences(tabContext) }
    val stripHeight = stripPreferences.niraTabStripHeightDp
    var iconSizeDp by remember { mutableIntStateOf(stripPreferences.niraTabCircleSizeDp.coerceIn(10, 52)) }
    var iconGapDp by remember { mutableIntStateOf(stripPreferences.niraTabCircleGapDp.coerceIn(-6, 40)) }
    DisposableEffect(stripPreferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "nira_tab_circle_size_dp") iconSizeDp = stripPreferences.niraTabCircleSizeDp.coerceIn(10, 52)
            if (key == "nira_tab_circle_gap_dp") iconGapDp = stripPreferences.niraTabCircleGapDp.coerceIn(-6, 40)
        }
        stripPreferences.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { stripPreferences.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val savedOrder by orderManager.currentOrder.collectAsState()
    val profileId by viewModel.currentProfileId.collectAsState()
    val roomGroups by viewModel.groupSnapshot.collectAsState()
    val visibleIds = tabs.map { it.id }.toSet()
    val groups = roomGroups.filter { group -> group.tabIds.any { it in visibleIds } }
    val order = savedOrder?.takeIf { it.profileId == profileId }
        ?.reconciled(tabs.map { it.id }, groups)
    val listState = rememberLazyListState()

    // Create drag coordinator
    val coordinator = rememberDragCoordinator(
        scope = scope,
        viewModel = viewModel,
        orderManager = orderManager
    )

    // Build items from order
    val items = remember(order, tabs, groups) {
        buildBarItems(order, tabs, groups)
    }
    
    val uniqueItems = remember(items) {
        items.distinctBy { item ->
            when (item) {
                is BarItem.SingleTab -> "tab-${item.id}"
                is BarItem.Group -> "group-${item.id}"
            }
        }
    }

    // Coordinates are deliberately not snapshot state: scrolling updates them without
    // recomposition or retriggering visibility checks on each frame.
    val tabCoordinates = remember { mutableMapOf<String, LayoutCoordinates>() }
    val groupCoordinates = remember { mutableMapOf<String, LayoutCoordinates>() }
    var viewportWidth by remember { mutableIntStateOf(0) }
    var viewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val edgeMargin = with(LocalDensity.current) { 12.dp.toPx() }
    val manuallyDragging by listState.interactionSource.collectIsDraggedAsState()
    val dragging = coordinator.dragState.value.isDragging
    var positioned by remember { mutableStateOf(false) }
    // Only layout-relevant data. Page titles and favicon updates must not scroll.
    val structure = uniqueItems.map { item ->
        when (item) {
            is BarItem.SingleTab -> listOf(item.id)
            is BarItem.Group -> listOf(item.id, item.groupName, item.isExpanded.toString()) + item.tabIds
        }
    }
    LaunchedEffect(selectedTabId, structure, profileId, autoScrollTrigger,
        viewportWidth, iconSizeDp, iconGapDp, dragging) {
        if (viewportWidth <= 0 || manuallyDragging || dragging) return@LaunchedEffect
        tabCoordinates.keys.retainAll(visibleIds)
        groupCoordinates.keys.retainAll(uniqueItems.filterIsInstance<BarItem.Group>().map { it.id }.toSet())
        // Allow the new structure and nested pills to finish placement.
        withFrameNanos { }
        withFrameNanos { }
        if (manuallyDragging || coordinator.dragState.value.isDragging) return@LaunchedEffect
        val animate = positioned
        if (tabs.size <= 1) {
            listState.scrollToItem(0)
            positioned = true
            return@LaunchedEffect
        }
        val index = uniqueItems.indexOfFirst { item ->
            when (item) {
                is BarItem.SingleTab -> item.id == selectedTabId
                is BarItem.Group -> selectedTabId in item.tabIds
            }
        }
        if (index < 0) return@LaunchedEffect
        val item = uniqueItems[index]
        // Materialize an absent lazy item before measuring; never estimate widths.
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            if (animate) listState.animateScrollToItem(index)
            else listState.scrollToItem(index)
            withFrameNanos { }
            withFrameNanos { }
        }
        val viewport = viewportCoordinates?.takeIf { it.isAttached } ?: return@LaunchedEffect
        fun bounds(coordinates: LayoutCoordinates?): TabStripBounds? {
            if (coordinates == null || !coordinates.isAttached) return null
            val left = coordinates.positionInRoot().x - viewport.positionInRoot().x
            return TabStripBounds(left, left + coordinates.size.width)
        }
        val group = (item as? BarItem.Group)?.let { bounds(groupCoordinates[it.id]) }
        val target = if (item is BarItem.Group && !item.isExpanded) group
            else bounds(tabCoordinates[selectedTabId])
        if (target == null || manuallyDragging || coordinator.dragState.value.isDragging) return@LaunchedEffect
        val delta = ActiveTabVisibility.scrollDelta(tabs.size, 0f, viewportWidth.toFloat(),
            target, group?.takeIf { item is BarItem.Group && item.isExpanded },
            listState.canScrollBackward, listState.canScrollForward, edgeMargin)
        positioned = true
        if (delta != 0f) {
            // LazyListState clamps to the real range and user gestures cancel its mutation.
            if (animate) listState.animateScrollBy(delta, tween(180))
            else listState.scrollBy(delta)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // Static layer - main tab bar
        Surface(
            modifier = Modifier.fillMaxWidth().height(maxOf(stripHeight.coerceIn(40, 80), iconSizeDp + 8).dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp
        ) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize()
                    .onSizeChanged { viewportWidth = it.width }
                    .onGloballyPositioned { viewportCoordinates = it }
            ) {
                itemsIndexed(
                    uniqueItems,
                    key = { _, item ->
                        when (item) {
                            is BarItem.SingleTab -> "tab-${item.id}"
                            is BarItem.Group -> "group-${item.id}"
                        }
                    }
                ) { index, item ->
                    when (item) {
                        is BarItem.SingleTab -> {
                            var offsetY by remember { mutableStateOf(0f) }
                            var showMenu by remember { mutableStateOf(false) }

                            Box(
                                modifier = Modifier
                                    // Compact measured step can be smaller than a circle.
                                    .width((iconSizeDp + if (uniqueItems.getOrNull(index + 1) is BarItem.SingleTab) iconGapDp else 4).coerceAtLeast(1).dp)
                                    .draggableItem(
                                        itemType = DraggableItemType.Tab(item.tab.id),
                                        coordinator = coordinator
                                    )
                                    .dropTarget(
                                        id = item.tab.id,
                                        type = DropTargetType.TAB,
                                        coordinator = coordinator,
                                        metadata = mapOf(
                                            "tabId" to item.tab.id,
                                            "contextId" to (item.tab.contextId ?: "")
                                        )
                                    )
                                    .dragVisualFeedback(item.tab.id, coordinator)
                                    .pointerInput(item.tab.id) {
                                        detectVerticalDragGestures(
                                            onDragEnd = {
                                                if (abs(offsetY) > 50) {
                                                    if (offsetY < 0) {
                                                        // Swipe up - delete
                                                        onTabClose(item.tab.id)
                                                    } else {
                                                        // Swipe down - menu
                                                        showMenu = true
                                                    }
                                                }
                                                offsetY = 0f
                                            },
                                            onDragCancel = { offsetY = 0f },
                                            onVerticalDrag = { _, dragAmount ->
                                                offsetY += dragAmount
                                            }
                                        )
                                    }
                            ) {
                                TabPill(
                                    tab = item.tab,
                                    onPositioned = { tabCoordinates[item.id] = it },
                                    isSelected = item.tab.id == selectedTabId,
                                    coordinator = coordinator,
                                    onTabClick = onTabClick,
                                    iconSizeDp = iconSizeDp,
                                    isDragging = false,
                                    modifier = Modifier
                                        .offset { IntOffset(0, offsetY.roundToInt()) }
                                        .zIndex(1f)
                                )

                                // Swipe feedback
                                if (abs(offsetY) > 10) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                if (offsetY < 0)
                                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                                else
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            ),
                                        contentAlignment = if (offsetY < 0) Alignment.TopCenter else Alignment.BottomCenter
                                    ) {
                                        Icon(
                                            imageVector = if (offsetY < 0) Icons.Default.Delete else Icons.Default.MoreVert,
                                            contentDescription = null,
                                            tint = if (offsetY < 0)
                                                MaterialTheme.colorScheme.onErrorContainer
                                            else
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            if (showMenu) {
                                ShowTabMenu(
                                    tab = item.tab,
                                    isInGroup = false,
                                    onDismiss = { showMenu = false },
                                    viewModel = viewModel,
                                    scope = scope
                                )
                            }
                        }

                        is BarItem.Group -> {
                            var offsetY by remember { mutableStateOf(0f) }
                            var showMenu by remember { mutableStateOf(false) }

                            Box(
                                modifier = Modifier
                                    .draggableItem(
                                        itemType = DraggableItemType.Group(item.groupId),
                                        coordinator = coordinator
                                    )
                                    .dropTarget(
                                        id = item.groupId,
                                        type = DropTargetType.GROUP_HEADER,
                                        coordinator = coordinator,
                                        metadata = mapOf<String, Any>(
                                            "groupId" to item.groupId,
                                            "contextId" to (item.contextId ?: "")
                                        )
                                    )
                                    .dragVisualFeedback(item.groupId, coordinator)
                                    .pointerInput(item.groupId) {
                                        detectVerticalDragGestures(
                                            onDragEnd = {
                                                if (abs(offsetY) > 50) {
                                                    if (offsetY < 0) {
                                                        // Swipe up - ungroup all
                                                        viewModel.ungroupAll(item.groupId)
                                                    } else {
                                                        // Swipe down - menu
                                                        showMenu = true
                                                    }
                                                }
                                                offsetY = 0f
                                            },
                                            onDragCancel = { offsetY = 0f },
                                            onVerticalDrag = { _, dragAmount ->
                                                offsetY += dragAmount
                                            }
                                        )
                                    }
                            ) {
                                GroupPill(
                                    group = item,
                                    onTabPositioned = { id, coordinates -> tabCoordinates[id] = coordinates },
                                    isSelected = selectedTabId in item.tabIds,
                                    selectedTabId = selectedTabId,
                                    coordinator = coordinator,
                                    onTabClick = onTabClick,
                                    onTabClose = onTabClose,
                                    onGroupClick = { groupId ->
                                        viewModel.toggleGroupExpanded(groupId)
                                    },
                                    viewModel = viewModel,
                                    iconSizeDp = iconSizeDp,
                                    iconGapDp = iconGapDp,
                                    isDragging = false,
                                    modifier = Modifier
                                        .onGloballyPositioned { groupCoordinates[item.id] = it }
                                        .offset { IntOffset(0, offsetY.roundToInt()) }
                                        .zIndex(1f)
                                )

                                // Swipe feedback
                                if (abs(offsetY) > 10) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                if (offsetY < 0)
                                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                                else
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            ),
                                        contentAlignment = if (offsetY < 0) Alignment.TopCenter else Alignment.BottomCenter
                                    ) {
                                        Icon(
                                            imageVector = if (offsetY < 0) Icons.Default.Close else Icons.Default.MoreVert,
                                            contentDescription = null,
                                            tint = if (offsetY < 0)
                                                MaterialTheme.colorScheme.onErrorContainer
                                            else
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            if (showMenu) {
                                ShowGroupMenu(
                                    groupId = item.groupId,
                                    groupName = item.groupName,
                                    onDismiss = { showMenu = false },
                                    viewModel = viewModel,
                                    scope = scope
                                )
                            }
                        }
                    }

                    // Add invisible divider after each item for drag-and-drop (except the last one)
                    if (index < uniqueItems.size - 1 &&
                        !(item is BarItem.SingleTab && uniqueItems[index + 1] is BarItem.SingleTab)) {
                        TabDivider(
                            id = "divider_${index + 1}",
                            coordinator = coordinator,
                            position = index + 1
                        )
                    }
                }
            }
        }

        // Drag layer - floating item that follows pointer
        DragLayer(coordinator = coordinator) { draggedItem ->
            when (draggedItem) {
                is DraggableItemType.Tab -> {
                    val tab = tabs.find { it.id == draggedItem.tabId }
                    if (tab != null) {
                        // Glass-morphic dragged tab with pill shape
                        Surface(
                            modifier = Modifier.size(iconSizeDp.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation = 8.dp,
                            shadowElevation = 8.dp
                        ) {
                            TabPill(
                                tab = tab,
                                isSelected = false,
                                coordinator = coordinator,
                                onTabClick = {},
                                iconSizeDp = iconSizeDp,
                                isDragging = true
                            )
                        }
                    }
                }

                is DraggableItemType.Group -> {
                    val item = items.find {
                        it is BarItem.Group && it.groupId == draggedItem.groupId
                    } as? BarItem.Group
                    if (item != null) {
                        GroupPill(
                            group = item,
                            isSelected = false,
                            selectedTabId = null,
                            coordinator = coordinator,
                            onTabClick = {},
                            onTabClose = {},
                            onGroupClick = null,
                            viewModel = viewModel,
                            iconSizeDp = iconSizeDp,
                            iconGapDp = iconGapDp,
                            isDragging = true
                        )
                    }
                }
            }
        }
    }
}

/**
 * Build bar items from order
 */
private fun buildBarItems(
    order: UnifiedTabOrder?,
    tabs: List<TabSessionState>,
    liveGroups: List<TabGroupData>
): List<BarItem> {
    if (order == null) return tabs.map { BarItem.SingleTab(it) }

    // The persisted order can lag behind live BrowserStore tabs (new tab,
    // restored session or rotation). Never render an empty strip just because
    // a tab has not been added to the order snapshot yet.
    val orderedItems = order.primaryOrder.mapNotNull { orderItem ->
        when (orderItem) {
            is UnifiedTabOrder.OrderItem.SingleTab -> {
                tabs.find { it.id == orderItem.tabId }?.let { BarItem.SingleTab(it) }
            }

            is UnifiedTabOrder.OrderItem.TabGroup -> {
                val groupTabs = orderItem.tabIds.mapNotNull { tabId ->
                    tabs.find { it.id == tabId }
                }
                if (groupTabs.isNotEmpty()) {
                    BarItem.Group(
                        groupId = orderItem.groupId,
                        groupName = orderItem.groupName,
                        color = liveGroups.firstOrNull { it.id == orderItem.groupId }?.color
                            ?: orderItem.color,
                        contextId = groupTabs.first().contextId,
                        tabs = groupTabs,
                        tabIds = orderItem.tabIds,
                        isExpanded = orderItem.isExpanded
                    )
                } else null
            }
        }
    }
    val representedTabIds = orderedItems.flatMap { item ->
        when (item) {
            is BarItem.SingleTab -> listOf(item.tab.id)
            is BarItem.Group -> item.tabs.map { it.id }
        }
    }.toSet()
    return orderedItems + tabs.filterNot { it.id in representedTabIds }
        .map { BarItem.SingleTab(it) }
}

/**
 * Sealed class for bar items
 */
sealed class BarItem {
    abstract val id: String

    data class SingleTab(val tab: TabSessionState) : BarItem() {
        override val id = tab.id
    }

    data class Group(
        val groupId: String,
        val groupName: String,
        val color: Int,
        val contextId: String?,
        val tabs: List<TabSessionState>,
        val tabIds: List<String>,
        val isExpanded: Boolean
    ) : BarItem() {
        override val id = groupId
    }
}

private data class SelectedTabLocation(
    val itemIndex: Int,
    val isGrouped: Boolean
)

private fun findSelectedTabLocation(order: UnifiedTabOrder, tabId: String): SelectedTabLocation? {
    val index = order.primaryOrder.indexOfFirst { item ->
        when (item) {
            is UnifiedTabOrder.OrderItem.SingleTab -> item.tabId == tabId
            is UnifiedTabOrder.OrderItem.TabGroup -> tabId in item.tabIds
        }
    }
    if (index < 0) return null
    return SelectedTabLocation(
        itemIndex = index,
        isGrouped = order.primaryOrder.getOrNull(index) is UnifiedTabOrder.OrderItem.TabGroup
    )
}

/**
 * Tab pill composable - matches grouped tab pill appearance
 */
@Composable
private fun TabPill(
    tab: TabSessionState,
    onPositioned: (LayoutCoordinates) -> Unit = {},
    isSelected: Boolean,
    coordinator: DragCoordinator,
    onTabClick: (String) -> Unit,
    iconSizeDp: Int = 34,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tones = TabGroupTones.forGroup(null)
    Surface(
        // requiredSize preserves the requested visual diameter even when the measured
        // row step is smaller (negative gap intentionally overlaps circles).
        modifier = modifier.requiredSize(iconSizeDp.dp).onGloballyPositioned(onPositioned),
        shape = CircleShape,
        color = Color(if (isSelected) tones.activeStart else tones.tabStart),
        border = if (isSelected) BorderStroke(1.dp, Color(tones.accent)) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .clickable { onTabClick(tab.id) }
                    .semantics { contentDescription = getTabDisplayTitle(tab) },
                contentAlignment = Alignment.Center
            ) {
                FaviconImage(tab = tab, size = (iconSizeDp * 0.58f).roundToInt().coerceAtLeast(6).dp)
            }

        }
    }
}

/**
 * Invisible divider that acts as a drop target for reordering
 * Used for drag-and-drop but not visually rendered
 */
@Composable
private fun TabDivider(
    id: String,
    coordinator: DragCoordinator,
    position: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .width(4.dp)
            .dropTarget(
                id = id,
                type = DropTargetType.ROOT_POSITION,
                coordinator = coordinator,
                metadata = mapOf("position" to position)
            )
    ) {
        // No visual divider - just an invisible drop target
    }
}

/**
 * Reusable tab context menu wrapper - used by tab bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowTabMenu(
    tab: TabSessionState,
    isInGroup: Boolean,
    onDismiss: () -> Unit,
    viewModel: TabViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    modifier: Modifier = Modifier
) {
    com.prirai.android.nira.browser.tabs.compose.TabContextMenu(
        tab = tab,
        isInGroup = isInGroup,
        onDismiss = onDismiss,
        viewModel = viewModel,
        scope = scope,
        modifier = modifier
    )
}

/**
 * Reusable group context menu wrapper - used by tab bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowGroupMenu(
    groupId: String,
    groupName: String,
    onDismiss: () -> Unit,
    viewModel: TabViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    modifier: Modifier = Modifier
) {
    com.prirai.android.nira.browser.tabs.compose.GroupContextMenu(
        groupId = groupId,
        groupName = groupName,
        onDismiss = onDismiss,
        viewModel = viewModel,
        scope = scope,
        modifier = modifier
    )
}

/**
 * Group pill composable - solid background with dividers between tabs
 */
@Composable
private fun GroupPill(
    group: BarItem.Group,
    onTabPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> },
    isSelected: Boolean,
    selectedTabId: String?,
    coordinator: DragCoordinator,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onGroupClick: ((String) -> Unit)? = null,
    viewModel: TabViewModel,
    iconSizeDp: Int = 34,
    iconGapDp: Int = 4,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Get expanded state from centralized group manager
    val expanded = group.isExpanded
    val tones = TabGroupTones.forGroup(group.color)
    
    var menuTab by remember { mutableStateOf<TabSessionState?>(null) }
    var showTabMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.padding(horizontal = 2.dp)
            .then(
                if (expanded) Modifier.wrapContentHeight().defaultMinSize(minHeight = 40.dp)
                else Modifier.height(40.dp)
            )
            .wrapContentWidth()
            .then(
                if (isDragging) Modifier else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        color = Color(tones.headerStart)
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .then(if (expanded) Modifier.wrapContentHeight() else Modifier.fillMaxHeight())
                .clickable(
                    enabled = true,
                    onClick = {
                        if (onGroupClick != null) onGroupClick(group.groupId)
                        else viewModel.toggleGroupExpanded(group.groupId)
                    }
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Collapse/Expand icon
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = Color(group.color),
                modifier = Modifier.size(20.dp)
            )

            // Group name - use onSurface color when dragging, no ellipsis
            Text(
                text = if (expanded) "(${group.tabs.size}) ${group.groupName}" else "(${group.tabs.size})",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                color = Color.White
            )

            if (expanded) {
                // Show tab pills in group with dividers
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(iconGapDp.dp)
                ) {
                    group.tabs.forEachIndexed { index, tab ->
                        // Key each pill by tab id so its remembered state (drag offset,
                        // placement) travels with the tab across insertions
                        // and removals instead of being reused by whichever tab now sits
                        // in the same slot. Without this, closing a tab caused the next
                        // pill to inherit the closed pill's transient state and disappear.
                        key(tab.id) {
                            // Wrap SwipeableTabPill with draggable and drop target for individual tab reordering
                            val isTabSelected = selectedTabId == tab.id
                            Box(
                                modifier = Modifier
                                    .draggableItem(
                                        itemType = DraggableItemType.Tab(tab.id),
                                        coordinator = coordinator
                                    )
                                    .onGloballyPositioned { onTabPositioned(tab.id, it) }
                                    .dropTarget(
                                        id = tab.id,
                                        type = DropTargetType.TAB,
                                        coordinator = coordinator,
                                        metadata = mapOf(
                                            "tabId" to tab.id,
                                            "groupId" to group.groupId,
                                            "isInGroup" to true
                                        )
                                    )
                            ) {
                                SwipeableTabPill(
                                    tab = tab,
                                    isSelected = isTabSelected,
                                    groupColor = group.color,
                                    onTabClick = { onTabClick(tab.id) },
                                    onTabClose = { onTabClose(tab.id) },
                                    onShowMenu = {
                                        menuTab = tab
                                        showTabMenu = true
                                    },
                                    modifier = Modifier,
                                    swipeThreshold = 40f,
                                    iconSizeDp = iconSizeDp
                                )
                            }
                        }
                    }

                    // Keep add action separate from the user-configurable circle spacing.
                    Box(modifier = Modifier.width(4.dp))

                    // Add tab button
                    IconButton(
                        onClick = {
                            // Create new tab and add to this group. Default-profile
                            // groups now carry contextId == null (shared with
                            // custom tabs' Gecko partition).
                            viewModel.createNewTabInGroup(group.groupId, group.contextId)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add tab to group",
                            tint = Color(group.color),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // Show tab menu when triggered
    if (showTabMenu && menuTab != null) {
        ShowTabMenu(
            tab = menuTab!!,
            isInGroup = true,
            onDismiss = {
                showTabMenu = false
                menuTab = null
            },
            viewModel = viewModel,
            scope = rememberCoroutineScope()
        )
    }
}
