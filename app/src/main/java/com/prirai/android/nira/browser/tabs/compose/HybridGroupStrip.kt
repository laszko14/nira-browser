package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.browser.tabgroups.TabGroupData
import com.prirai.android.nira.theme.TabGroupTones
import mozilla.components.browser.state.state.TabSessionState

/**
 * Nira Hybrid: Firefox-style named/colored groups above a WebLibre-like quick
 * tab switcher. All group membership and edit operations stay in the existing
 * Room-backed UnifiedTabGroupManager / TabViewModel; no second group database.
 * The separate full tab sheet keeps its drag-and-drop and grid/list views.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HybridGroupChips(
    groups: List<TabGroupData>,
    tabs: List<TabSessionState>,
    activeGroupId: String?,
    onGroupSelected: (String?) -> Unit,
    viewModel: TabViewModel
) {
    if (groups.isEmpty()) return

    val liveTabIds = remember(tabs) { tabs.map { it.id }.toSet() }
    val scope = rememberCoroutineScope()
    var menuGroupId by remember { mutableStateOf<String?>(null) }
    val menuGroup = groups.firstOrNull { it.id == menuGroupId }

    Column {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = "all-groups") {
                    GroupChip(
                        label = "(" + tabs.size + ") Wszystkie",
                        selected = activeGroupId == null,
                        groupColor = null,
                        onClick = { onGroupSelected(null) },
                        onLongClick = {}
                    )
                }
                items(groups, key = { it.id }) { group ->
                    val liveCount = group.tabIds.count { it in liveTabIds }
                    GroupChip(
                        label = "(" + liveCount + ") " + group.name.ifBlank { "Grupa" },
                        selected = activeGroupId == group.id,
                        groupColor = Color(group.color),
                        onClick = { onGroupSelected(group.id) },
                        onLongClick = { menuGroupId = group.id }
                    )
                }
                if (activeGroupId != null && menuGroup != null) {
                    // The group menu below is also reachable by a long press.
                }
            }
        }

        if (menuGroup != null) {
            GroupContextMenu(
                groupId = menuGroup.id,
                groupName = menuGroup.name,
                onDismiss = { menuGroupId = null },
                viewModel = viewModel,
                scope = scope
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupChip(
    label: String,
    selected: Boolean,
    groupColor: Color?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val palette = groupColor?.let { TabGroupTones.forGroup(it.toArgb()) }
    val accent = palette?.let { Color(it.accent) } ?: MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier
            .height(36.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(13.dp),
        color = palette?.let { Color(if (selected) it.activeStart else it.headerStart) }
            ?: MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(1.dp, accent) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (groupColor != null) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    color = groupColor,
                    shape = CircleShape
                ) {}
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (palette != null) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * The second row shows only the active group's live tabs; a dedicated plus
 * uses TabOrderManager.addNewTabToGroup rather than creating an orphan tab.
 */
@Composable
fun HybridGroupTabs(
    tabs: List<TabSessionState>,
    selectedTabId: String?,
    groupColor: Int,
    height: Dp,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onNewTabInGroup: () -> Unit
) {
    val tones = TabGroupTones.forGroup(groupColor)
    Surface(
        modifier = Modifier.fillMaxWidth().height(height),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(tabs, key = { it.id }) { tab ->
                val selected = tab.id == selectedTabId
                Surface(
                    modifier = Modifier.height(36.dp).width(150.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(if (selected) tones.activeStart else tones.tabStart),
                    border = if (selected) BorderStroke(1.dp, Color(tones.accent)) else null
                ) {
                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .clickable { onTabSelected(tab.id) }
                                .padding(start = 8.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FaviconImage(tab = tab, size = 19.dp)
                            Text(
                                text = tab.content.title.ifBlank { tab.content.url },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        IconButton(
                            onClick = { onTabClosed(tab.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Zamknij kartę",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            item(key = "new-group-tab") {
                IconButton(onClick = onNewTabInGroup) {
                    Icon(Icons.Default.Add, contentDescription = "Nowa karta w grupie")
                }
            }
        }
    }
}
