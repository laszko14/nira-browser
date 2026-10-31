package com.prirai.android.nira.browser.tabs.compose

import com.prirai.android.nira.diagnostics.testlab.*
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import mozilla.components.lib.state.ext.flow
import kotlinx.serialization.json.Json
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

private val Context.orderDataStore: DataStore<Preferences> by preferencesDataStore(name = "tab_order")

/**
 * Manages tab ordering and grouping across all views.
 * Single source of truth for tab positions.
 * Singleton to ensure all components share the same order state.
 */
class TabOrderManager internal constructor(
    private val context: Context,
    private val groupManager: UnifiedTabGroupManager,
    private val orderStore: DataStore<Preferences> = context.orderDataStore,
    private val lab: () -> LabCapture? = {TestLab.capture()},
    private val activeProfile: () -> String = {
        val manager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(context)
        if (manager.isPrivateMode()) "private" else manager.getActiveProfile().id
    }
) {
    
    companion object {
        @Volatile
        private var instance: TabOrderManager? = null
        
        fun getInstance(context: Context, groupManager: UnifiedTabGroupManager): TabOrderManager {
            return instance ?: synchronized(this) {
                instance ?: TabOrderManager(context.applicationContext, groupManager).also { instance = it }
            }
        }
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private val _currentOrder = MutableStateFlow<UnifiedTabOrder?>(null)
    val currentOrder: StateFlow<UnifiedTabOrder?> = _currentOrder.asStateFlow()
    
    private val operationMutex = Mutex()
    private class Operation(val owner: TabOrderManager, val profileId: String) :
        AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Operation>
    }

    private suspend fun <T> operation(block: suspend () -> T): T {
        if (coroutineContext[Operation]?.owner === this) return block()
        return operationMutex.withLock {
            withContext(NonCancellable + Operation(this, activeProfile())) { block() }
        }
    }

    /** Bind an entire UI mutation to the profile that originated it. */
    suspend fun mutateProfile(profileId: String, block: suspend () -> Boolean): Boolean = operation {
        if (activeProfile() != profileId) false else block()
    }

    private suspend fun currentForOperation(): UnifiedTabOrder? = _currentOrder.value?.takeIf {
        it.profileId == coroutineContext[Operation]?.profileId
    }

    private fun publish(order: UnifiedTabOrder) {
        // A suspended load/save for A must never replace the visible order of B.
        if (order.profileId == activeProfile()) _currentOrder.value = order
    }

    @kotlinx.serialization.Serializable
    private data class RootMoveIntent(val sourceId: String, val targetId: String?, val after: Boolean)

    /** Durable root placement intent, written before changing Room membership. */
    suspend fun rememberRootMove(profileId: String, sourceId: String, targetId: String?, after: Boolean) = operation {
        orderStore.edit { prefs ->
            prefs[stringPreferencesKey("pending_root_$profileId")] =
                json.encodeToString(RootMoveIntent(sourceId, targetId, after))
        }
    }

    /** Forget only IDs explicitly transferred to another profile, never a partial restore. */
    suspend fun forgetTabs(profileId: String, tabIds: Set<String>) = operation {
        val order = loadOrder(profileId)
        val items = order.primaryOrder.mapNotNull { item ->
            when (item) {
                is UnifiedTabOrder.OrderItem.SingleTab -> item.takeUnless { it.tabId in tabIds }
                is UnifiedTabOrder.OrderItem.TabGroup -> item.copy(tabIds = item.tabIds.filterNot { it in tabIds })
                    .takeUnless { it.tabIds.isEmpty() }
            }
        }
        saveOrder(order.copy(primaryOrder = items))
    }

    /**
     * Load order for a specific profile
     */
    suspend fun loadOrder(profileId: String): UnifiedTabOrder = operation op@ {
        val key = stringPreferencesKey("order_$profileId")
        val order = orderStore.data
            .map { prefs ->
                prefs[key]?.let { json.decodeFromString<UnifiedTabOrder>(it) }
            }
            .first()
            ?: UnifiedTabOrder(profileId, emptyList())
        
        // Clean up orphaned group references
        val cleanedOrder = cleanupOrphanedGroups(order)
        publish(cleanedOrder)
        return@op cleanedOrder
    }
    
    /**
     * Clean up orphaned group IDs from unified order
     */
    private suspend fun cleanupOrphanedGroups(order: UnifiedTabOrder): UnifiedTabOrder {
        // Never drop persisted groups before the asynchronous Room load has finished.
        if (!groupManager.awaitInitialized()) return order
        val expected = com.prirai.android.nira.browser.profile.BrowserProfile
            .contextIdFor(order.profileId, isPrivate = order.profileId == "private")
        val groups = groupManager.getAllGroups().filter { group ->
            group.contextId == expected || (order.profileId == "default" && group.contextId == "profile_default")
        }
        // Deleting a group must not discard the former members' root positions.
        // This is an in-memory projection; only rebuild commits reconciliation.
        return order.reconciled(emptyList(), groups)
    }

    /**
     * Save current order
     */
    suspend fun saveOrder(order: UnifiedTabOrder) = operation op@ {
        val capture=lab();val measurement=capture?.operation(LabOperation.ORDER_SAVE)
        try {
            val key = stringPreferencesKey("order_${order.profileId}")
            var encoded:String?=null
            val saved=orderStore.edit { prefs ->
                // Keep serialization on DataStore's dispatcher, as before instrumentation.
                val serialized=json.encodeToString(order)
                encoded=serialized;prefs[key]=serialized
            }
            publish(order)
            capture?.check(measurement,encoded!=null&&saved[key]==encoded,LabInvariant.ORDER_NOT_PERSISTED)
            capture?.end(measurement,proof=CompletionProof.DATASTORE_COMMITTED)
        } catch(error:Throwable) {
            capture?.end(measurement,if(error is kotlinx.coroutines.CancellationException)OperationOutcome.INTERRUPTED else OperationOutcome.FAILED)
            throw error
        }
    }
    
    // === REORDERING OPERATIONS ===
    
    /**
     * Reorder an item in the primary order
     */
    suspend fun reorderItem(fromIndex: Int, toIndex: Int) = operation op@ {
        val current = currentForOperation() ?: return@op
        if (fromIndex == toIndex) return@op
        
        val newOrder = current.primaryOrder.toMutableList()
        val size = newOrder.size
        
        // Bounds checking - critical to prevent IndexOutOfBoundsException
        if (fromIndex < 0 || fromIndex >= size) {
            android.util.Log.e("TabOrderManager", "Invalid fromIndex: $fromIndex, size: $size")
            return@op
        }
        if (toIndex < 0 || toIndex >= size) {
            android.util.Log.e("TabOrderManager", "Invalid toIndex: $toIndex, size: $size")
            return@op
        }
        
        val item = newOrder.removeAt(fromIndex)
        newOrder.add(toIndex, item)
        saveOrder(current.copy(primaryOrder = newOrder))
    }

    suspend fun moveRootRelative(sourceId: String, targetId: String?, after: Boolean): Boolean = operation {
        val current = currentForOperation() ?: return@operation false
        val from = current.getItemPosition(sourceId) ?: return@operation false
        val target = targetId?.let { current.getItemPosition(it) }
        if (targetId != null && target == null) return@operation false
        val boundary = target?.let { it + if (after) 1 else 0 } ?: current.primaryOrder.size
        reorderItem(from, DragPlacement.destination(from, boundary, current.primaryOrder.size))
        true
    }

    /**
     * Create a new group from multiple tabs
     */
    suspend fun createGroup(tabIds: List<String>, groupName: String, color: Int) = operation op@ {
        val current = currentForOperation() ?: return@op
        if (tabIds.isEmpty()) return@op
        
        // Persist the group first so Room and DataStore share the same UUID.
        val created = groupManager.createGroup(
            tabIds = tabIds,
            name = groupName,
            color = color,
            contextId = com.prirai.android.nira.browser.profile.BrowserProfile
                .contextIdFor(current.profileId, isPrivate = current.profileId == "private")
        )
        val groupId = created.id
        
        // Find first tab position
        val firstTabIndex = current.primaryOrder.indexOfFirst { item ->
            item is UnifiedTabOrder.OrderItem.SingleTab && item.tabId == tabIds.first()
        }
        
        // Remove tabs from current positions
        val remainingOrder = current.primaryOrder.filterNot { item ->
            when (item) {
                is UnifiedTabOrder.OrderItem.SingleTab -> item.tabId in tabIds
                is UnifiedTabOrder.OrderItem.TabGroup -> {
                    // Remove tabs from other groups
                    val remaining = item.tabIds.filterNot { it in tabIds }
                    if (remaining.isEmpty()) return@filterNot true // Remove empty group
                    false
                }
            }
        }.map { item ->
            // Update existing groups to remove moved tabs
            if (item is UnifiedTabOrder.OrderItem.TabGroup) {
                val remaining = item.tabIds.filterNot { it in tabIds }
                if (remaining.size == 1) {
                    // Ungroup if only one tab left
                    UnifiedTabOrder.OrderItem.SingleTab(remaining.first())
                } else {
                    item.copy(tabIds = remaining)
                }
            } else {
                item
            }
        }.toMutableList()
        
        // Create new group
        val newGroup = UnifiedTabOrder.OrderItem.TabGroup(
            groupId = groupId,
            groupName = groupName,
            color = color,
            isExpanded = true,
            tabIds = tabIds
        )
        
        // Insert group at first tab position
        val insertPos = firstTabIndex.coerceAtLeast(0).coerceAtMost(remainingOrder.size)
        remainingOrder.add(insertPos, newGroup)
        
        saveOrder(current.copy(primaryOrder = remainingOrder))
    }
    
    /**
     * Add a tab to an existing group
     */
    suspend fun addTabToGroup(tabId: String, groupId: String, positionInGroup: Int? = null) = operation {
        val current = currentForOperation() ?: return@operation
        if (groupManager.addTabToGroup(tabId, groupId, positionInGroup)) {
            rebuildOrderForProfile(current.profileId, context.components.store.state.tabs)
        }
    }

    /**
     * Remove a tab from its group
     */
    suspend fun removeTabFromGroup(tabId: String, newPosition: Int? = null) = operation {
        val current = currentForOperation() ?: return@operation
        if (!groupManager.removeTabFromGroup(tabId)) return@operation
        rebuildOrderForProfile(current.profileId, context.components.store.state.tabs)
        val updated = currentForOperation() ?: return@operation
        val from = updated.getItemPosition(tabId) ?: return@operation
        if (newPosition != null) reorderItem(from, newPosition.coerceIn(0, updated.primaryOrder.lastIndex))
    }

    /**
     * Add a new tab to a specific group
     */
    suspend fun addNewTabToGroup(groupId: String) = operation op@ {
        // Get the group to determine its contextId
        val group = groupManager.getGroup(groupId)
        if (group == null) {
            android.util.Log.e("TabOrderManager", "Group $groupId not found")
            return@op
        }
        
        val originProfile = coroutineContext[Operation]?.profileId ?: return@op
        val expectedContext = com.prirai.android.nira.browser.profile.BrowserProfile
            .contextIdFor(originProfile, isPrivate = originProfile == "private")
        if (activeProfile() != originProfile || (group.contextId != expectedContext &&
                !(originProfile == "default" && group.contextId == "profile_default"))) return@op
        val contextId = group.contextId
        // Use context to get components
        val store = context.components.store
        val tabsUseCases = context.components.tabsUseCases
        
        // Direct return@op value is the newly created tab ID; selectedTabId
        // can change between async operations and must not be used here.
        val newTabId = tabsUseCases.addTab(
            url = "about:homepage",
            private = contextId == "private",
            contextId = contextId,
            selectTab = true
        )
                // Verify the tab exists in store
        val newTab = store.flow().first { state -> state.tabs.any { it.id == newTabId } }
            .tabs.firstOrNull { it.id == newTabId }
        if (newTab == null) {
            android.util.Log.e("TabOrderManager", "Tab $newTabId not found in store")
            return@op
        }
        // Add the new tab to the group in the order
        addTabToGroup(newTabId, groupId)
        
        // Also add it directly to the group manager if not already there
        groupManager.addTabToGroup(newTabId, groupId)
        
        // Force refresh by updating the order timestamp
        val current = currentForOperation() ?: return@op
        publish(current.copy(lastModified = System.currentTimeMillis()))
    }
    
    /**
     * Remove a tab completely from the order
     */
    suspend fun removeTab(tabId: String) = operation op@ {
        val current = currentForOperation() ?: return@op

        val newOrder = current.primaryOrder.mapNotNull { item ->
            when (item) {
                is UnifiedTabOrder.OrderItem.SingleTab -> {
                    if (item.tabId == tabId) null else item
                }
                is UnifiedTabOrder.OrderItem.TabGroup -> {
                    val remainingTabs = item.tabIds.filter { it != tabId }
                    when (remainingTabs.size) {
                        0 -> null // Remove empty group
                        else -> item.copy(tabIds = remainingTabs) // Keep group with remaining tabs
                    }
                }
            }
        }

        // Persist the updated order first
        saveOrder(current.copy(primaryOrder = newOrder))

        // Ensure group membership is kept in sync after removing a tab. This prevents stale
        // group state that can hide pills or cause UI inconsistencies when the store later
        // emits updates. Run sync in a safe try/catch to avoid crashing callers.
        try {
            syncToGroupManager()
        } catch (e: Exception) {
            android.util.Log.e("TabOrderManager", "Failed to sync group manager after removeTab: ${e.message}", e)
        }
    }
    
    /**
     * Toggle group expansion state
     */
    suspend fun toggleGroupExpansion(groupId: String) = operation op@ {
        // Just update local order for UI state
        val current = currentForOperation() ?: return@op
        val newOrder = current.primaryOrder.map { item ->
            if (item is UnifiedTabOrder.OrderItem.TabGroup && item.groupId == groupId) {
                item.copy(isExpanded = !item.isExpanded)
            } else {
                item
            }
        }
        val toggled=current.primaryOrder.filterIsInstance<UnifiedTabOrder.OrderItem.TabGroup>().firstOrNull{it.groupId==groupId}
        val capture=lab();val measurement=toggled?.let{capture?.operation(if(it.isExpanded)LabOperation.GROUP_COLLAPSE else LabOperation.GROUP_EXPAND)}
        try {saveOrder(current.copy(primaryOrder = newOrder));capture?.end(measurement,proof=CompletionProof.DATASTORE_COMMITTED)}
        catch(error:Throwable){capture?.end(measurement,OperationOutcome.INTERRUPTED);throw error}
    }
    
    /**
     * Auto-expand group containing the selected tab
     */
    suspend fun expandGroupContainingTab(tabId: String) = operation op@ {
        val current = currentForOperation() ?: return@op
        var hasChanges = false
        val newOrder = current.primaryOrder.map { item ->
            if (item is UnifiedTabOrder.OrderItem.TabGroup && tabId in item.tabIds) {
                if (!item.isExpanded) {
                    hasChanges = true
                    item.copy(isExpanded = true)
                } else {
                    item
                }
            } else {
                item
            }
        }
        if (hasChanges) {
            saveOrder(current.copy(primaryOrder = newOrder))
        }
    }

    /**
     * Initialize from current state with groups, preserving tab positions
     */
    suspend fun initializeFromCurrentState(profileId: String, tabIds: List<String>, groups: List<com.prirai.android.nira.browser.tabgroups.TabGroupData>) = operation op@ {
        val items = mutableListOf<UnifiedTabOrder.OrderItem>()
        val processedTabIds = mutableSetOf<String>()
        
        // Map each group to the position of its first tab
        val groupPositions = groups.mapNotNull { group ->
            val validTabIds = group.tabIds.filter { it in tabIds }
            if (validTabIds.isEmpty()) return@mapNotNull null
            
            val firstTabPosition = tabIds.indexOf(validTabIds.first())
            if (firstTabPosition == -1) return@mapNotNull null
            
            firstTabPosition to UnifiedTabOrder.OrderItem.TabGroup(
                groupId = group.id,
                groupName = group.name,
                color = group.color,
                isExpanded = true,
                tabIds = validTabIds
            )
        }.toMap()
        
        val groupedTabIds = groups.flatMap { it.tabIds }.toSet()
        
        // Process tabs in their original order
        tabIds.forEachIndexed { index, tabId ->
            if (tabId in processedTabIds) return@forEachIndexed
            
            // If a group starts at this position, add the group
            if (index in groupPositions) {
                val groupItem = groupPositions[index]!!
                items.add(groupItem)
                // Mark all tabs in this group as processed
                groupItem.tabIds.forEach { processedTabIds.add(it) }
            } else if (tabId !in groupedTabIds) {
                // Add ungrouped tab
                items.add(UnifiedTabOrder.OrderItem.SingleTab(tabId))
                processedTabIds.add(tabId)
            }
        }
        
        val order = UnifiedTabOrder(
            profileId = profileId,
            primaryOrder = items,
            lastModified = System.currentTimeMillis()
        )
        saveOrder(order)
    }
    
    /**
     * Sync changes back to UnifiedTabGroupManager
     * Updates group membership and properties
     */
    suspend fun syncToGroupManager() = operation {
        val current = currentForOperation() ?: return@operation
        rebuildOrderForProfile(current.profileId, context.components.store.state.tabs)
    }

    /**
     * Complete terminal close only after restore and Undo. Unlike ordinary
     * rebuild, this intentionally drops historical IDs from DataStore and Room.
     * Pending root-move intents must first be replayed by rebuild.
     */
    suspend fun finalizeClosedTabs(
        tabs: List<mozilla.components.browser.state.state.TabSessionState>,
        undoTabIds: Set<String>,
        restoreComplete: Boolean
    ) = operation {
        if (!restoreComplete || !groupManager.awaitInitialized()) return@operation
        val prefs = orderStore.data.first()
        val orderKeys = prefs.asMap().keys
            .mapNotNull { key ->
                key.name.takeIf { it.startsWith("order_") }
                    ?.removePrefix("order_")?.takeIf { it.isNotEmpty() }
            }
        // A pending root move may reference a temporarily absent tab. Protect
        // both ends before pruning Room, not just when saving its order file.
        val pendingMoves = mutableMapOf<String, RootMoveIntent>()
        for (profileId in orderKeys) {
            val pending = prefs[stringPreferencesKey("pending_root_$profileId")] ?: continue
            val move = runCatching { json.decodeFromString<RootMoveIntent>(pending) }
                .getOrElse { return@operation } // Corrupt intent: never delete speculative data.
            pendingMoves[profileId] = move
        }
        val retainedIds = tabs.mapTo(mutableSetOf()) { it.id }.apply {
            addAll(undoTabIds)
            pendingMoves.values.forEach { move ->
                add(move.sourceId)
                move.targetId?.let(::add)
            }
        }
        groupManager.pruneFinalizedClosedMembers(retainedIds)
        for (profileId in orderKeys) {
            // First replay the durable move through normal rebuild/restore.
            if (profileId in pendingMoves) continue
            val expected = com.prirai.android.nira.browser.profile.BrowserProfile
                .contextIdFor(profileId, isPrivate = profileId == "private")
            val groups = groupManager.getAllGroups().filter { group ->
                group.contextId == expected ||
                    (profileId == "default" && group.contextId == "profile_default")
            }
            val ids = tabs.filter { tab ->
                tab.content.private == (profileId == "private") &&
                    (tab.contextId == expected ||
                        (profileId == "default" && tab.contextId == "profile_default"))
            }.map { it.id }
            val cleaned = loadOrder(profileId).reconciled(ids, groups).prunedTo(retainedIds)
            val key = stringPreferencesKey("order_$profileId")
            if (orderStore.data.first()[key] != json.encodeToString(cleaned)) saveOrder(cleaned)
        }
    }

    /**
     * Rebuild order for a profile from current tabs and groups.
     * Used when groups are created/deleted to ensure order stays in sync.
     */
    suspend fun rebuildOrderForProfile(profileId: String, tabs: List<mozilla.components.browser.state.state.SessionState>) = operation {
        if (!groupManager.awaitInitialized()) return@operation
        val profileGroups = groupManager.getAllGroups().filter { group ->
            if (profileId == "default") group.contextId in setOf(null, "profile_default")
            else group.contextId == com.prirai.android.nira.browser.profile.BrowserProfile
                .contextIdFor(profileId, isPrivate = profileId == "private")
        }
        val contextId = com.prirai.android.nira.browser.profile.BrowserProfile
            .contextIdFor(profileId, isPrivate = profileId == "private")
        val ids = tabs.filter { tab ->
            tab.content.private == (profileId == "private") &&
                (tab.contextId == contextId || (profileId == "default" && tab.contextId == "profile_default"))
        }.map { it.id }
        // Room owns membership and positions; never sync this snapshot backwards.
        val existing = loadOrder(profileId)
        var updated = existing.reconciled(ids, profileGroups)
        val pendingKey = stringPreferencesKey("pending_root_$profileId")
        val pending = orderStore.data.first()[pendingKey]?.let { json.decodeFromString<RootMoveIntent>(it) }
        if (pending != null) {
            // Room decides whether detach/create committed. If the source is still
            // grouped (or was never created), the interrupted operation aborted.
            val from = updated.getItemPosition(pending.sourceId)
            val target = pending.targetId?.let { updated.getItemPosition(it) }
            if (from != null && (pending.targetId == null || target != null)) {
                val boundary = target?.let { it + if (pending.after) 1 else 0 } ?: updated.primaryOrder.size
                val destination = DragPlacement.destination(from, boundary, updated.primaryOrder.size)
                val items = updated.primaryOrder.toMutableList()
                items.add(destination, items.removeAt(from))
                updated = updated.copy(primaryOrder = items)
            }
        }
        orderStore.edit { prefs ->
            prefs[stringPreferencesKey("order_$profileId")] = json.encodeToString(updated)
            prefs.remove(pendingKey)
        }
        publish(updated)
    }
}
