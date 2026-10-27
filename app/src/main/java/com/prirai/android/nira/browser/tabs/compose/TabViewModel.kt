package com.prirai.android.nira.browser.tabs.compose

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import com.prirai.android.nira.browser.tabgroups.TabGroupData
import com.prirai.android.nira.ext.components
import mozilla.components.browser.state.state.TabSessionState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/**
 * ViewModel for managing tabs, groups, and their order in Compose UI.
 * Serves as single source of truth, bridging UnifiedTabGroupManager and Compose UI.
 *
 * Tab-close undo is handled by mozilla-components' `UndoMiddleware` (installed in
 * `Components.kt`) plus `TabsUseCases.undo`. When the user closes a tab we simply
 * dispatch `RemoveTabAction`; the middleware snapshots the tab (including its
 * `engineSessionState` and `id`) into `BrowserState.undoHistory` for ~5s. Calling
 * `undo()` restores tabs with their original id, so `TabGroupMember` Room rows
 * still resolve correctly - no restore-side reattachment code is needed.
 */
class TabViewModel(
    private val context: Context,
    private val groupManager: UnifiedTabGroupManager,
    private val orderManager: TabOrderManager = TabOrderManager.getInstance(context, groupManager)
) : ViewModel() {

    private val _tabs = MutableStateFlow<List<TabSessionState>>(emptyList())
    val tabs: StateFlow<List<TabSessionState>> = _tabs.asStateFlow()

    private val _groups = MutableStateFlow<List<TabGroupData>>(emptyList())
    val groups: StateFlow<List<TabGroupData>> = _groups.asStateFlow()

    private val _expandedGroups = MutableStateFlow<Set<String>>(emptySet())
    val expandedGroups: StateFlow<Set<String>> = _expandedGroups.asStateFlow()

    private val _selectedTabId = MutableStateFlow<String?>(null)
    val selectedTabId: StateFlow<String?> = _selectedTabId.asStateFlow()

    private val _currentProfileId = MutableStateFlow<String?>(null)
    val currentProfileId: StateFlow<String?> = _currentProfileId.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    // Expose order for UI to use
    val currentOrder: StateFlow<UnifiedTabOrder?> = orderManager.currentOrder
    val groupSnapshot: StateFlow<List<TabGroupData>> = groupManager.groupsState

    /**
     * Optional callback invoked *after* a tab has been removed from the store.
     * Kept as a hook so hosts (e.g. `TabsBottomSheetFragment`,
     * `ComposeTabBarWithProfileSwitcher`) can run their own UI-only follow-up work
     * (dismissing sheets, refreshing selection, etc.). The actual store removal is
     * performed by this ViewModel via `TabsUseCases.removeTab`, and undo is handled
     * upstream by `UndoMiddleware` - see [closeTab].
     */
    var onTabRemove: ((String) -> Unit)? = null

    /**
     * Canonical mapping from the internal `profileId` string to the
     * `contextId` persisted on `TabGroup` / `TabSessionState`. Matches
     * `BrowserProfile.contextIdFor`.
     *
     * Default profile => null (shared Gecko partition with custom tabs).
     * Private mode   => "private".
     * Named profile  => "profile_<id>".
     */
    private fun contextIdForProfile(profileId: String): String? =
        com.prirai.android.nira.browser.profile.BrowserProfile.contextIdFor(
            profileId = profileId,
            isPrivate = profileId == "private",
        )

    /**
     * Does [group] belong to the given [profileId]?
     *
     * The default profile accepts groups with `contextId == null` (new
     * canonical) OR `"profile_default"` (legacy sentinel for installs
     * mid-migration). All other profiles match on exact contextId.
     */
    private fun groupMatchesProfile(group: TabGroupData, profileId: String): Boolean =
        when (profileId) {
            "default" -> group.contextId == null || group.contextId == "profile_default"
            else -> group.contextId == contextIdForProfile(profileId)
        }

    init {
        viewModelScope.launch {
            groupManager.groupsState.collect {
                val profileId = _currentProfileId.value ?: return@collect
                if (!groupManager.awaitInitialized()) return@collect
                orderManager.rebuildOrderForProfile(profileId, _tabs.value)
                if (_currentProfileId.value == profileId) refreshGroupsForProfile(profileId)
            }
        }
    }

    private fun tabMatchesProfile(tab: TabSessionState, profileId: String): Boolean =
        tab.content.private == (profileId == "private") &&
            (tab.contextId == contextIdForProfile(profileId) ||
                (profileId == "default" && tab.contextId == "profile_default"))

    private fun launchMutation(block: suspend () -> Unit) {
        val profileId = _currentProfileId.value ?: return
        viewModelScope.launch {
            orderManager.mutateProfile(profileId) {
                if (_currentProfileId.value != profileId) return@mutateProfile false
                block()
                true
            }
        }
    }

    /**
     * Refresh groups for the current profile
     */
    private suspend fun refreshGroupsForProfile(profileId: String) {
        val allGroups = groupManager.getAllGroups()

        // Filter groups to only show those matching the current profile.
        val groupsList = allGroups.filter { group ->
            groupMatchesProfile(group, profileId)
        }.filter { group ->
            // Additionally, only show groups that have tabs in the current tab list
            group.tabIds.any { tabId -> _tabs.value.any { it.id == tabId } }
        }

        _groups.value = groupsList

        // Read expanded state from saved order, preserving current state as fallback
        val savedOrder = orderManager.currentOrder.value?.takeIf { it.profileId == profileId }
        val savedExpandedStates = savedOrder?.primaryOrder
            ?.filterIsInstance<UnifiedTabOrder.OrderItem.TabGroup>()
            ?.associate { it.groupId to it.isExpanded }
            ?: emptyMap()
        
        val currentExpanded = _expandedGroups.value
        // For each group, use saved state if available, otherwise preserve current state, default to expanded for new groups
        val updatedExpanded = groupsList.mapNotNull { group ->
            when {
                savedExpandedStates.containsKey(group.id) -> if (savedExpandedStates[group.id] == true) group.id else null
                currentExpanded.contains(group.id) -> group.id
                else -> group.id // New groups default to expanded
            }
        }.toSet()
        _expandedGroups.value = updatedExpanded
    }

    /**
     * Load tabs and groups for a profile
     */
    fun loadTabsForProfile(profileId: String, tabs: List<TabSessionState>, selectedTabId: String?) {
        loadJob?.cancel()
        if (_currentProfileId.value != profileId) {
            // Do not expose the previous profile while the new file/Room snapshot is loading.
            _tabs.value = emptyList()
            _groups.value = emptyList()
            _expandedGroups.value = emptySet()
            _selectedTabId.value = null
        }
        _currentProfileId.value = profileId
        val scopedTabs = tabs.filter { tabMatchesProfile(it, profileId) }
        loadJob = viewModelScope.launch {
            if (!groupManager.awaitInitialized()) return@launch
            orderManager.rebuildOrderForProfile(profileId, scopedTabs)
            val order = orderManager.loadOrder(profileId)
            if (_currentProfileId.value != profileId) return@launch
            val groups = groupManager.getAllGroups().filter { groupMatchesProfile(it, profileId) }
            _tabs.value = orderedTabs(order, scopedTabs, groups)
            _groups.value = groups.filter { g -> g.tabIds.any { id -> scopedTabs.any { it.id == id } } }
            _expandedGroups.value = order.primaryOrder.filterIsInstance<UnifiedTabOrder.OrderItem.TabGroup>()
                .filter { it.isExpanded }.map { it.groupId }.toSet()
            _selectedTabId.value = selectedTabId?.takeIf { id -> scopedTabs.any { it.id == id } }
        }
    }

    fun forceRefresh(tabs: List<TabSessionState>, selectedTabId: String?) {
        _currentProfileId.value?.let { loadTabsForProfile(it, tabs, selectedTabId) }
    }

    private fun orderedTabs(order: UnifiedTabOrder, tabs: List<TabSessionState>, groups: List<TabGroupData>): List<TabSessionState> =
        UnifiedItemBuilder.buildItems(order, tabs, groups, emptySet()).flatMap { item ->
            when (item) {
                is UnifiedItem.SingleTab -> listOf(item.tab)
                is UnifiedItem.GroupContainer -> item.children
                else -> emptyList()
            }
        }

    private suspend fun getOrderedTabs(tabs: List<TabSessionState>, profileId: String, groups: List<TabGroupData>): List<TabSessionState> =
        orderedTabs(orderManager.loadOrder(profileId), tabs, groups)

    /**
     * Update tab list (for refreshing)
     */
    fun updateTabs(tabs: List<TabSessionState>, selectedTabId: String?) {
        _currentProfileId.value?.let { profileId ->
            // Cancel any ongoing operation first
            loadJob?.cancel()
            loadTabsForProfile(profileId, tabs, selectedTabId)
        }
    }

    /**
     * Toggle group expanded/collapsed state
     */
    fun toggleGroupExpanded(groupId: String) {
        launchMutation {
            val profileId = _currentProfileId.value ?: return@launchMutation
            if (groupManager.getGroup(groupId)?.let { groupMatchesProfile(it, profileId) } != true) return@launchMutation
            orderManager.toggleGroupExpansion(groupId)
            val order = orderManager.loadOrder(profileId)
            val expanded = order.primaryOrder.filterIsInstance<UnifiedTabOrder.OrderItem.TabGroup>()
                .firstOrNull { it.groupId == groupId }?.isExpanded ?: return@launchMutation
            groupManager.setGroupCollapsed(groupId, !expanded)
            if (_currentProfileId.value == profileId) {
                _expandedGroups.value = if (expanded) _expandedGroups.value + groupId else _expandedGroups.value - groupId
            }
        }
    }

    /**
     * Get a group by ID from the group manager
     */
    fun getGroup(groupId: String): TabGroupData? {
        return groupManager.getGroup(groupId)
    }

    /**
     * Group two tabs together
     */
    fun groupTabsTogether(tab1Id: String, tab2Id: String) {
        launchMutation {
            _currentProfileId.value?.let { profileId ->
                // Get tabs to determine contextId
                val tab1 = _tabs.value.find { it.id == tab1Id }
                val tab2 = _tabs.value.find { it.id == tab2Id }

                // Validate contextId - tabs must have same contextId or both null
                if (tab1 != null && tab2 != null) {
                    val contextId1 = tab1.contextId
                    val contextId2 = tab2.contextId

                    // Can only group if contextIds match (both null or both same value)
                    if (contextId1 == contextId2) {
                        // Create new group with both tabs. The group's contextId is
                        // the tab's contextId verbatim - default profile tabs are
                        // null, named profile tabs are "profile_<id>".
                        groupManager.createGroup(
                            tabIds = listOf(tab1Id, tab2Id),
                            name = "New Group",
                            color = generateRandomGroupColor(),
                            contextId = contextId1
                        )

                        // Save new order after group creation completes
                        saveCurrentOrder(profileId)

                        // Note: Group events observer will trigger the UI refresh
                    }
                }
            }
        }
    }

    /**
     * Move tab to an existing group
     */
    fun moveTabToGroup(
        tabId: String,
        groupId: String,
        targetTabId: String? = null,
        insertAfter: Boolean = true
    ) {
        launchMutation {
            _currentProfileId.value?.let { profileId ->
                // Get the tab and the target group
                val tab = _tabs.value.find { it.id == tabId }
                val targetGroup = groupManager.getGroup(groupId)

                if (tab != null && targetGroup != null) {
                    // Normalize contextIds (treat empty string as null) and allow
                    // either `null` or legacy `"profile_default"` to match for the
                    // default profile. All other profiles require an exact match.
                    val tabContextId = tab.contextId?.takeIf { it.isNotEmpty() }
                    val groupContextId = targetGroup.contextId?.takeIf { it.isNotEmpty() }

                    val defaultProfileMatch = profileId == "default" &&
                        (tabContextId == null || tabContextId == "profile_default") &&
                        (groupContextId == null || groupContextId == "profile_default")

                    if (tabContextId == groupContextId || defaultProfileMatch) {
                        // A drop on another group's tab inserts at that
                        // exact place; a header drop appends. Preserve the
                        // existing profile/context security guard above.
                        val targetIndex = targetTabId?.let { target ->
                            val index = targetGroup.tabIds.indexOf(target)
                            if (index < 0) null else index + (if (insertAfter) 1 else 0)
                        }
                        groupManager.addTabToGroup(
                            tabId = tabId,
                            groupId = groupId,
                            position = targetIndex,
                            notifyChange = true
                        )

                        // Save order
                        saveCurrentOrder(profileId)

                        // Note: Group events observer will trigger the UI refresh
                    }
                }
            }
        }
    }

    /**
     * Rename a group
     */
    fun renameGroup(groupId: String, newName: String) {
        launchMutation {
            groupManager.renameGroup(groupId, newName)

            // Note: Group events observer will trigger the UI refresh
        }
    }

    /**
     * Change group color
     */
    fun changeGroupColor(groupId: String, color: Int) {
        launchMutation {
            groupManager.changeGroupColor(groupId, color)

            // Note: Group events observer will trigger the UI refresh
        }
    }

    /**
     * Ungroup all tabs in a group
     */
    fun ungroupAll(groupId: String) {
        launchMutation {
            _currentProfileId.value?.let { profileId ->
                val group = groupManager.getGroup(groupId)
                group?.tabIds?.forEach { tabId ->
                    groupManager.removeTabFromGroup(tabId, notifyChange = true)
                }

                // Save order
                saveCurrentOrder(profileId)

                // Note: Group events observer will trigger the UI refresh
            }
        }
    }

    /**
     * Reorder tabs/groups
     */
    fun reorderTabs(fromIndex: Int, toIndex: Int) {
        launchMutation {
            val currentTabs = _tabs.value.toMutableList()
            if (fromIndex in currentTabs.indices && toIndex in currentTabs.indices) {
                val item = currentTabs.removeAt(fromIndex)
                currentTabs.add(toIndex, item)
                _tabs.value = currentTabs

                // Save new order
                _currentProfileId.value?.let { saveCurrentOrder(it) }
            }
        }
    }

    /**
     * Save current tab order to persistent storage
     */
    private suspend fun saveCurrentOrder(profileId: String) {
        // Finish persistence for the originating profile even when its UI was replaced.
        val liveTabs = groupManager.liveTabsSnapshot().filter { tabMatchesProfile(it, profileId) }
        orderManager.rebuildOrderForProfile(profileId, liveTabs)
        if (_currentProfileId.value == profileId) refreshGroupsForProfile(profileId)
    }

    private fun generateRandomGroupColor(): Int {
        val colors = listOf(
            android.graphics.Color.parseColor("#FF6B6B"),
            android.graphics.Color.parseColor("#4ECDC4"),
            android.graphics.Color.parseColor("#45B7D1"),
            android.graphics.Color.parseColor("#FFA07A"),
            android.graphics.Color.parseColor("#98D8C8"),
            android.graphics.Color.parseColor("#F7DC6F"),
            android.graphics.Color.parseColor("#BB8FCE"),
            android.graphics.Color.parseColor("#85C1E2"),
            android.graphics.Color.parseColor("#F8B739"),
            android.graphics.Color.parseColor("#52B788")
        )
        return colors.random()
    }

    /**
     * Create a new group with tabs
     */
    fun createGroup(tabIds: List<String>, name: String? = null, contextId: String? = null) {
        launchMutation {
            val profileId = _currentProfileId.value ?: return@launchMutation
            val tabsToGroup = _tabs.value.filter { it.id in tabIds }
            if (tabsToGroup.size != tabIds.distinct().size || tabsToGroup.isEmpty()) return@launchMutation
            // Creating a group with null contextId under a named/private profile
            // made it instantly disappear from the side panel after refresh.
            val resolvedContext = contextId ?: contextIdForProfile(profileId)
            val group = groupManager.createGroup(
                tabIds = tabIds.distinct(),
                name = name?.take(70)?.ifBlank { "Nowa grupa" } ?: "Nowa grupa",
                profileId = profileId,
                contextId = resolvedContext
            )
            saveCurrentOrder(profileId)
            refreshGroupsForProfile(profileId)
            _expandedGroups.value = _expandedGroups.value + group.id
        }
    }

    /**
     * Convert the visible "Bez grupy" recovery section into a real, named
     * group. Recheck membership and profile at commit time; do not silently
     * take cards that have since been moved elsewhere.
     */
    suspend fun createNamedGroupFromUngrouped(tabIds: List<String>, requestedName: String): Boolean {
        val profileId = _currentProfileId.value ?: return false
        if (!groupManager.awaitInitialized()) return false
        val name = requestedName.trim().take(70)
        if (name.isEmpty()) return false
        val distinctIds = tabIds.distinct()
        if (distinctIds.isEmpty()) return false
        val tabs = _tabs.value.filter { it.id in distinctIds }
        if (tabs.size != distinctIds.size) return false
        val contextId = contextIdForProfile(profileId)
        val belongsToProfile = tabs.all { tab ->
            if (profileId == "default") !tab.content.private &&
                (tab.contextId == null || tab.contextId == "profile_default")
            else tab.contextId == contextId && tab.content.private == (profileId == "private")
        }
        if (!belongsToProfile) return false
        if (groupManager.getAllGroups().any { group ->
                group.tabIds.any { it in distinctIds }
            }) return false

        val created = groupManager.createGroup(
            tabIds = distinctIds,
            name = name,
            profileId = profileId,
            contextId = contextId
        )
        saveCurrentOrder(profileId)
        refreshGroupsForProfile(profileId)
        _expandedGroups.value = _expandedGroups.value + created.id
        return true
    }

    /**
     * Create a group and move exactly one tab into it in the same coroutine,
     * avoiding racing asynchronous remove/add operations from the UI.
     */
    fun moveTabToNewGroup(tabId: String, name: String) {
        launchMutation {
            val profileId = _currentProfileId.value ?: return@launchMutation
            val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return@launchMutation
            // createGroup detaches the source and inserts its new group in ONE Room transaction.
            groupManager.createGroup(
                tabIds = listOf(tabId),
                name = name.take(70).ifBlank { "Nowa grupa" },
                profileId = profileId,
                contextId = tab.contextId
            )
            saveCurrentOrder(profileId)
        }
    }

    /**
     * Add tab to existing group
     */
    fun addTabToGroup(tabId: String, groupId: String) {
        launchMutation {
            _currentProfileId.value?.let { profileId ->
                groupManager.addTabToGroup(tabId, groupId)

                saveCurrentOrder(profileId)

                // Note: Group events observer will trigger the UI refresh
            }
        }
    }

    /**
     * Remove tab from its group.
     *
     * When the removed tab was the last one in the group, the group is deleted
     * by [UnifiedTabGroupManager] and the tab becomes a standalone pill. That
     * shifts the LazyRow layout and can leave the viewport pointing at empty
     * space. Nudging [TabSheetStateManager.notifyTabSheetDismissed] tells the
     * tab bar to animate back to whichever tab is currently selected so the
     * user is never left staring at a blank strip.
     */
    fun removeTabFromGroup(tabId: String) {
        launchMutation {
            _currentProfileId.value?.let { profileId ->
                groupManager.removeTabFromGroup(tabId)

                saveCurrentOrder(profileId)

                TabSheetStateManager.notifyTabSheetDismissed()

                // Note: Group events observer will trigger the UI refresh
            }
        }
    }

    /**
     * Merge two groups
     */
    fun mergeGroups(sourceGroupId: String, targetGroupId: String) {
        launchMutation {
            val profileId = _currentProfileId.value ?: return@launchMutation
            if (groupManager.mergeGroups(sourceGroupId, targetGroupId)) saveCurrentOrder(profileId)
        }
    }

    /**
     * Commit a side-panel drop in a single suspendable operation. The previous
     * UI dispatched fire-and-forget ViewModel jobs after onDragEnd, allowing
     * a new browser-state refresh to race the persisted group position.
     * Return only when Room membership AND the shared order are reconciled.
     */
    suspend fun commitSidePanelDrop(
        draggedId: String,
        targetKey: String,
        insertAfter: Boolean,
        expectedProfileId: String? = _currentProfileId.value
    ): Boolean {
        val profileId = expectedProfileId ?: return false
        return orderManager.mutateProfile(profileId) drop@ {
        if (_currentProfileId.value != profileId) return@drop false
        val draggedTab = _tabs.value.firstOrNull { it.id == draggedId } ?: return@drop false
        if (!tabMatchesProfile(draggedTab, profileId)) return@drop false
        val allGroups = groupManager.getAllGroups()
        fun validGroup(group: TabGroupData): Boolean {
            if (!groupMatchesProfile(group, profileId)) return false
            val a = draggedTab.contextId
            val b = group.contextId
            return a == b || (profileId == "default" &&
                a in setOf(null, "profile_default") &&
                b in setOf(null, "profile_default"))
        }

        val success = when {
            targetKey.startsWith("group_tab_") -> {
                val targetId = targetKey.removePrefix("group_tab_")
                if (targetId == draggedId) return@drop false
                val group = allGroups.firstOrNull { targetId in it.tabIds } ?: return@drop false
                if (!validGroup(group)) return@drop false
                groupManager.moveTabRelative(draggedId, group.id, targetId, insertAfter)
            }
            targetKey.startsWith("group_") -> {
                val group = allGroups.firstOrNull {
                    it.id == targetKey.removePrefix("group_")
                } ?: return@drop false
                if (!validGroup(group)) return@drop false
                groupManager.moveTabRelative(draggedId, group.id, null, true)
            }
            targetKey.startsWith("single_tab_") -> {
                val targetId = targetKey.removePrefix("single_tab_")
                if (targetId == draggedId) return@drop false
                val targetTab = _tabs.value.firstOrNull { it.id == targetId } ?: return@drop false
                if (targetTab.contextId != draggedTab.contextId &&
                    !(profileId == "default" &&
                        targetTab.contextId in setOf(null, "profile_default") &&
                        draggedTab.contextId in setOf(null, "profile_default"))) return@drop false
                if (!tabMatchesProfile(targetTab, profileId) || allGroups.any { targetId in it.tabIds }) return@drop false
                commitTabAtRoot(profileId, draggedId, targetId, insertAfter)
            }
            targetKey == "ungrouped_header" -> commitTabAtRoot(profileId, draggedId, null, true)
            else -> false
        }
        if (!success) return@drop false

        // Read back from Room rather than rendering a stale Store snapshot.
        saveCurrentOrder(profileId)
        val orderedGroups = groupManager.getAllGroups().filter {
            groupMatchesProfile(it, profileId) &&
                it.tabIds.any { tabId -> _tabs.value.any { tab -> tab.id == tabId } }
        }
        if (_currentProfileId.value == profileId) {
            _groups.value = orderedGroups
            _tabs.value = getOrderedTabs(_tabs.value, profileId, orderedGroups)
        }
        android.util.Log.d("ZavroTabDrop", "Drop committed to group/order state")
        true
        }
    }

    private suspend fun commitTabAtRoot(profileId: String, tabId: String, targetId: String?, after: Boolean): Boolean {
        orderManager.rememberRootMove(profileId, tabId, targetId, after)
        if (groupManager.getGroupForTab(tabId) != null && !groupManager.removeTabFromGroup(tabId)) return false
        orderManager.rebuildOrderForProfile(profileId, _tabs.value)
        return true
    }

    suspend fun commitSidePanelGroupDrop(groupId: String, targetKey: String, after: Boolean, expectedProfileId: String?): Boolean {
        val profileId = expectedProfileId ?: return false
        return orderManager.mutateProfile(profileId) {
            if (_currentProfileId.value != profileId) return@mutateProfile false
            val group = groupManager.getGroup(groupId) ?: return@mutateProfile false
            if (!groupMatchesProfile(group, profileId)) return@mutateProfile false
            val targetId = rootTargetId(targetKey) ?: return@mutateProfile false
            orderManager.rebuildOrderForProfile(profileId, _tabs.value)
            val success = orderManager.moveRootRelative(groupId, targetId, after)
            saveCurrentOrder(profileId)
            success
        }
    }

    private fun rootTargetId(key: String): String? = when {
        key.startsWith("group_tab_") -> groupManager.getGroupForTab(key.removePrefix("group_tab_"))?.id
        key.startsWith("group_") -> key.removePrefix("group_")
        key.startsWith("single_tab_") -> key.removePrefix("single_tab_")
        else -> null
    }

    /** Await one complete drag operation in every Compose surface; no timed remove/add jobs. */
    suspend fun commitUnifiedDrop(item: DraggableItemType, target: DropTarget, expectedProfileId: String?): Boolean {
        val profileId = expectedProfileId ?: return false
        return orderManager.mutateProfile(profileId) drop@ {
            if (_currentProfileId.value != profileId) return@drop false
            orderManager.rebuildOrderForProfile(profileId, _tabs.value)
            val before = orderManager.loadOrder(profileId)
            val targetTabId = target.metadata["tabId"] as? String ?: target.metadata["targetTabId"] as? String
            val targetGroupId = target.metadata["groupId"] as? String
            val after = target.dropZone != DropZone.BEFORE
            val center = target.dropZone == DropZone.CENTER
            val success = when (item) {
                is DraggableItemType.Tab -> {
                    val tab = _tabs.value.firstOrNull { it.id == item.tabId } ?: return@drop false
                    if (!tabMatchesProfile(tab, profileId)) return@drop false
                    when (target.type) {
                        DropTargetType.TAB, DropTargetType.GROUP_BODY -> {
                            val targetId = targetTabId ?: return@drop false
                            val targetTab = _tabs.value.firstOrNull { it.id == targetId } ?: return@drop false
                            if (!tabMatchesProfile(targetTab, profileId) || targetId == tab.id) return@drop false
                            val group = groupManager.getGroupForTab(targetId)
                            if (group != null) {
                                groupManager.moveTabRelative(tab.id, group.id, targetId, after)
                            } else if (center) {
                                val newGroupId = java.util.UUID.randomUUID().toString()
                                val sourceGroup = groupManager.getGroupForTab(tab.id)
                                val removedRootId = sourceGroup?.takeIf { it.tabIds.size == 1 }?.id
                                    ?: tab.id.takeIf { sourceGroup == null }
                                val targetPosition = before.getItemPosition(targetId) ?: return@drop false
                                val anchor = before.primaryOrder.drop(targetPosition + 1).map { root ->
                                    when (root) {
                                        is UnifiedTabOrder.OrderItem.SingleTab -> root.tabId
                                        is UnifiedTabOrder.OrderItem.TabGroup -> root.groupId
                                    }
                                }.firstOrNull { it != removedRootId }
                                orderManager.rememberRootMove(profileId, newGroupId, anchor, false)
                                groupManager.createGroup(listOf(tab.id, targetId), contextId = tab.contextId, groupId = newGroupId)
                                saveCurrentOrder(profileId)
                                true
                            } else commitTabAtRoot(profileId, tab.id, targetId, after)
                        }
                        DropTargetType.GROUP_HEADER -> {
                            val groupId = targetGroupId ?: return@drop false
                            val group = groupManager.getGroup(groupId) ?: return@drop false
                            if (!groupMatchesProfile(group, profileId)) return@drop false
                            if (center) groupManager.moveTabRelative(tab.id, groupId, null, true)
                            else commitTabAtRoot(profileId, tab.id, groupId, after)
                        }
                        DropTargetType.ROOT_POSITION -> {
                            val boundary = target.metadata["position"] as? Int ?: return@drop false
                            val anchor = before.primaryOrder.getOrNull(boundary)?.let {
                                when (it) {
                                    is UnifiedTabOrder.OrderItem.SingleTab -> it.tabId
                                    is UnifiedTabOrder.OrderItem.TabGroup -> it.groupId
                                }
                            }
                            if (anchor == tab.id) true else commitTabAtRoot(profileId, tab.id, anchor, false)
                        }
                        DropTargetType.EMPTY_SPACE -> commitTabAtRoot(profileId, tab.id, null, true)
                    }
                }
                is DraggableItemType.Group -> {
                    val group = groupManager.getGroup(item.groupId) ?: return@drop false
                    if (!groupMatchesProfile(group, profileId)) return@drop false
                    when (target.type) {
                        DropTargetType.TAB -> {
                            val tabId = targetTabId ?: return@drop false
                            if (center) groupManager.moveTabRelative(tabId, group.id, null, true)
                            else orderManager.moveRootRelative(group.id, groupManager.getGroupForTab(tabId)?.id ?: tabId, after)
                        }
                        DropTargetType.GROUP_HEADER -> {
                            val id = targetGroupId ?: return@drop false
                            if (center) groupManager.mergeGroups(group.id, id)
                            else orderManager.moveRootRelative(group.id, id, after)
                        }
                        DropTargetType.ROOT_POSITION -> {
                            val boundary = target.metadata["position"] as? Int ?: return@drop false
                            val from = before.getItemPosition(group.id) ?: return@drop false
                            orderManager.reorderItem(from, DragPlacement.destination(from, boundary, before.primaryOrder.size))
                            true
                        }
                        else -> false
                    }
                }
            }
            if (success) saveCurrentOrder(profileId)
            success
        }
    }

    /**
     * Reorder tab within a group
     * @param insertAfter If true, insert after target; if false, insert before target
     */
    fun reorderTabInGroup(draggedTabId: String, targetTabId: String, groupId: String, insertAfter: Boolean = true) {
        launchMutation {
            _currentProfileId.value?.let { profileId ->
                val group = groupManager.getGroup(groupId) ?: return@launchMutation
                if (groupManager.moveTabRelative(draggedTabId, groupId, targetTabId, insertAfter)) {
                    saveCurrentOrder(profileId)
                }
            }
        }
    }

    /**
     * Reorder an entire group to a new position
     */
    fun reorderGroup(groupId: String, targetIndex: Int) {
        launchMutation {
            _currentProfileId.value?.let { profileId ->
                val current = orderManager.loadOrder(profileId)

                // Find the group in the order
                val groupIndex = current.primaryOrder.indexOfFirst {
                    it is UnifiedTabOrder.OrderItem.TabGroup && it.groupId == groupId
                }

                if (groupIndex != -1 && groupIndex != targetIndex) {
                    val size = current.primaryOrder.size
                    val validTargetIndex = targetIndex.coerceIn(0, size - 1)
                    if (groupIndex != validTargetIndex) {
                        orderManager.reorderItem(groupIndex, validTargetIndex)
                        saveCurrentOrder(profileId)
                    }
                }
            }
        }
    }

    /**
     * Move a tab to a specific position in the overall order
     */
    fun moveTabToPosition(tabId: String, targetIndex: Int) {
        launchMutation {
            _currentProfileId.value?.let { profileId ->
                val current = orderManager.loadOrder(profileId)

                // Find the tab - could be a SingleTab or inside a TabGroup
                var tabIndex = -1
                var isInGroup = false

                current.primaryOrder.forEachIndexed { index, item ->
                    when (item) {
                        is UnifiedTabOrder.OrderItem.SingleTab -> {
                            if (item.tabId == tabId) {
                                tabIndex = index
                            }
                        }

                        is UnifiedTabOrder.OrderItem.TabGroup -> {
                            if (tabId in item.tabIds) {
                                tabIndex = index
                                isInGroup = true
                            }
                        }
                    }
                }

                if (tabIndex != -1) {
                    val size = current.primaryOrder.size
                    val validTargetIndex = targetIndex.coerceIn(0, size)

                    // If tab is in a group at the found position, we need to extract it first
                    if (isInGroup && tabIndex != validTargetIndex) {
                        // Remove from group and add as single tab at target position
                        orderManager.removeTabFromGroup(tabId, validTargetIndex)
                        saveCurrentOrder(profileId)
                    } else if (!isInGroup && tabIndex != validTargetIndex) {
                        // Tab is already single, just reorder
                        orderManager.reorderItem(tabIndex, validTargetIndex)
                        saveCurrentOrder(profileId)
                    }
                }
            }
        }
    }

    // ========== Unified Menu Actions ==========

    /**
     * Create a new tab in the current profile
     */
    fun createNewTab() {
        launchMutation {
            val profileId = _currentProfileId.value ?: return@launchMutation
            // This will be handled by the caller (e.g., BrowserActivity)
            // through the components.tabsUseCases.addTab()
        }
    }

    /**
     * Duplicate a tab
     */
    fun duplicateTab(tabId: String) {
        launchMutation {
            val tab = _tabs.value.find { it.id == tabId }
            if (tab == null) {
                android.util.Log.e("TabViewModel", "Cannot duplicate - tab not found: $tabId")
                return@launchMutation
            }

            // Get group if tab is in a group
            val groupId = _groups.value.find { group ->
                group.tabIds.contains(tabId)
            }?.id
            // Note: Actual tab creation must be handled by the caller through:
            // components.tabsUseCases.addTab(url = tab.content.url, private = tab.content.private)
            // Then if groupId != null: addTabToGroup(newTabId, groupId)

            // For now, we'll emit an event that can be observed
            _duplicateTabEvent.emit(DuplicateTabRequest(tabId, tab.content.url, groupId))
        }
    }

    // Event flow for duplicate tab requests
    private val _duplicateTabEvent = MutableSharedFlow<DuplicateTabRequest>()
    val duplicateTabEvent: SharedFlow<DuplicateTabRequest> = _duplicateTabEvent.asSharedFlow()

    data class DuplicateTabRequest(
        val originalTabId: String,
        val url: String,
        val groupId: String?
    )

    /**
     * Toggle pin status of a tab
     */
    fun togglePinTab(tabId: String) {
        launchMutation {
            // This will be handled by the caller
            // through components.tabsUseCases.pinTab() or unpinTab()
        }
    }

    /**
     * Show dialog to add tab to a group
     */
    fun showAddToGroupDialog(tabId: String) {
        // This will be handled by the caller to show the group selection dialog
    }

    /**
     * Enter multi-select mode for batch operations
     */
    fun enterMultiSelectMode() {
        // This will be handled by the caller to enter multi-select mode
    }

    /**
     * Close a specific tab, optionally with an undo snackbar.
     *
     * Delegates removal to `TabsUseCases.removeTab`, which triggers
     * `UndoMiddleware` (installed in `Components.kt`) to snapshot the tab (id,
     * `contextId`, `engineSessionState`, `index`, `lastAccess`, ...) into
     * `BrowserState.undoHistory` for ~5s. If the user taps *Undo* on the
     * snackbar we dispatch `TabsUseCases.undo()`, which restores every tab in
     * `undoHistory` *with its original id*, so:
     *  - the `EngineSession` bound to that id remains valid (no reload),
     *  - `TabGroupMember` Room rows still reference a live tab,
     *  - scroll position and form data are preserved.
     *
     * The [onTabRemove] callback (if set) fires *after* the store dispatch so
     * hosts can dismiss sheets or refresh their own UI. It intentionally does
     * NOT perform the store removal any more - keeping the store update in one
     * place makes the undo semantics predictable across every entry point.
     */
    fun closeTab(tabId: String, showUndo: Boolean = true) {
        launchMutation {
            val tab = _tabs.value.find { it.id == tabId } ?: return@launchMutation
            val tabsUseCases = context.components.tabsUseCases

            // Dispatch the removal. UndoMiddleware picks this up and stashes the
            // tab in state.undoHistory for the default 5000ms window.
            tabsUseCases.removeTab(tabId)
            onTabRemove?.invoke(tabId)

            if (showUndo) {
                GlobalSnackbarManager.getInstance().showUndoSnackbar(
                    message = "Tab closed",
                    onUndo = {
                        // Restores everything currently in undoHistory - which is
                        // just this tab, because the 5s window resets on each new
                        // removal. Same id is reused, so groups stay intact.
                        tabsUseCases.undo()
                    },
                    onConfirm = {
                        // Timeout: nothing to do. UndoMiddleware will clear
                        // undoHistory on its own after clearAfterMillis.
                    }
                )
            }
        }
    }

    /**
     * Close all other tabs except the specified one
     */
    fun closeOtherTabs(keepTabId: String) {
        launchMutation {
            _currentProfileId.value?.let { profileId ->
                // Get all tab IDs except the one to keep
                val tabsToClose = _tabs.value.filter { it.id != keepTabId }.map { it.id }
                // This will be handled by the caller to close these tabs
                // The caller should iterate through tabsToClose and call removeTab() for each
            }
        }
    }

    /**
     * Pin a tab (move to first position in its group or root)
     */
    fun pinTab(tabId: String) {
        launchMutation {
            _currentProfileId.value?.let { profileId ->
                val tab = _tabs.value.find { it.id == tabId } ?: return@launchMutation
                val group = _groups.value.find { it.tabIds.contains(tabId) }

                if (group != null) {
                    // Tab is in a group - move to first position in that group
                    val updatedTabIds = listOf(tabId) + group.tabIds.filter { it != tabId }
                    groupManager.updateGroup(
                        groupId = group.id,
                        name = group.name,
                        color = group.color,
                        tabIds = updatedTabIds
                    )
                } else {
                    // Tab is not in a group - move to first position in root
                    moveTabToPosition(tabId, 0)
                }

                saveCurrentOrder(profileId)
            }
        }
    }

    /**
     * Pin a group (move to first position in order)
     */
    fun pinGroup(groupId: String) {
        launchMutation {
            _currentProfileId.value?.let { profileId ->
                reorderGroup(groupId, 0)
            }
        }
    }

    /**
     * Close all tabs in a group
     */
    fun closeAllTabsInGroup(groupId: String) {
        launchMutation {
            val group = groupManager.getGroup(groupId) ?: return@launchMutation
            val useCases = context.components.tabsUseCases
            // Work from a stable snapshot: store/group updates occur for each removal.
            group.tabIds.toList().forEach { id ->
                if (context.components.store.state.tabs.any { it.id == id }) {
                    useCases.removeTab(id)
                }
            }
        }
    }

    /**
     * Create a new tab in a specific group. `contextId` is nullable so
     * default-profile groups (contextId == null post-migration) can create
     * tabs without being force-stamped with a profile sentinel.
     */
    fun createNewTabInGroup(groupId: String, contextId: String?) {
        launchMutation { orderManager.addNewTabToGroup(groupId) }
    }

    /**
     * Move a group to a different profile
     */
    fun moveGroupToProfile(groupId: String, profileId: String) {
        launchMutation {
            val sourceProfile = _currentProfileId.value ?: return@launchMutation
            val group = groupManager.getGroup(groupId) ?: return@launchMutation
            if (!groupMatchesProfile(group, sourceProfile) || sourceProfile == profileId) return@launchMutation
            val ids = group.tabIds.filter { id -> groupManager.liveTabsSnapshot().any { it.id == id } }
            val profiles = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(context)
            if (ids.size != group.tabIds.size || profiles.migrateTabsToProfile(ids, profileId) != ids.size) return@launchMutation
            if (groupManager.reassignGroupContext(groupId, contextIdForProfile(profileId))) {
                orderManager.forgetTabs(sourceProfile, ids.toSet())
                saveCurrentOrder(sourceProfile)
                orderManager.rebuildOrderForProfile(profileId, groupManager.liveTabsSnapshot())
            }
        }
    }

    /**
     * Ungroup all tabs in a group (alias for ungroupAll)
     */
    fun ungroupAllTabs(groupId: String) {
        ungroupAll(groupId)
    }

    /**
     * Create group with tabs (alias for createGroup)
     */
    fun createGroupWithTabs(tabIds: List<String>, name: String? = null) {
        createGroup(tabIds, name)
    }

    // Methods for drag-and-drop integration

    /**
     * Create a new group from two tabs
     */
    fun createGroupFromTabs(tabId1: String, tabId2: String) {
        groupTabsTogether(tabId1, tabId2)
    }

    /**
     * Add tab to group, removing from previous group if needed
     */
    fun addTabToGroup(tabId: String, groupId: String, fromGroupId: String?) {
        launchMutation {
            _currentProfileId.value?.let { profileId ->
                // If tab was in another group, remove it first
                if (fromGroupId != null) {
                    groupManager.removeTabFromGroup(tabId, notifyChange = false)
                }

                // Add to new group
                groupManager.addTabToGroup(tabId, groupId, position = null, notifyChange = true)

                saveCurrentOrder(profileId)
            }
        }
    }

    /**
     * Ungroup a tab (remove from group)
     */
    fun ungroupTab(tabId: String, groupId: String?) {
        launchMutation {
            _currentProfileId.value?.let { profileId ->
                groupManager.removeTabFromGroup(tabId, notifyChange = true)

                saveCurrentOrder(profileId)
            }
        }
    }

    /**
     * Reorder a group to a new position
     */
    fun reorderGroup(groupId: String, targetGroupId: String) {
        launchMutation {
            _currentProfileId.value?.let { profileId ->
                val order = orderManager.currentOrder.value ?: return@launchMutation

                val fromIndex = order.primaryOrder.indexOfFirst {
                    it is UnifiedTabOrder.OrderItem.TabGroup && it.groupId == groupId
                }
                val toIndex = order.primaryOrder.indexOfFirst {
                    it is UnifiedTabOrder.OrderItem.TabGroup && it.groupId == targetGroupId
                }

                if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                    orderManager.reorderItem(fromIndex, toIndex)
                    saveCurrentOrder(profileId)
                }
            }
        }
    }

    /**
     * Toggle group collapse state
     */
    fun toggleGroupCollapse(groupId: String) {
        toggleGroupExpanded(groupId)
    }

    /**
     * Select a tab
     */
    fun selectTab(tabId: String) {
        _selectedTabId.value = tabId
    }
}
