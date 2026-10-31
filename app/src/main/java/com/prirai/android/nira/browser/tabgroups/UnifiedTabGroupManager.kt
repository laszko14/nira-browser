package com.prirai.android.nira.browser.tabgroups

import com.prirai.android.nira.diagnostics.testlab.*
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import androidx.room.withTransaction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import androidx.core.net.toUri
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.prirai.android.nira.ext.components

/**
 * Unified Tab Group Manager - Single source of truth for all tab grouping operations.
 * Synchronizes between tab bar and tab sheet automatically.
 * 
 * Best Practices:
 * - Single Responsibility: Manages groups and their persistence
 * - Observer Pattern: UI components observe changes via StateFlow
 * - Immutable Data: Groups are immutable, changes create new instances
 * - Thread Safety: All DB operations on IO dispatcher
 * - Lifecycle Aware: Cleanup methods for memory management
 */
class UnifiedTabGroupManager internal constructor(
    private val context: Context,
    private val database: TabGroupDatabase = TabGroupDatabase.getInstance(context),
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val lab: () -> LabCapture? = {TestLab.capture()},
    private val liveTabs: () -> List<mozilla.components.browser.state.state.TabSessionState> = {
        context.components.store.state.tabs
    }
) {
    private val dao = database.tabGroupDao()
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + dispatcher)
    private val initialized = CompletableDeferred<Boolean>()

    /** True only after Room groups have been loaded successfully. */
    suspend fun awaitInitialized(): Boolean = initialized.await()

    private val mutationMutex = Mutex()
    private class Mutation(val owner: UnifiedTabGroupManager) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Mutation>
        val events = mutableListOf<GroupEvent>()
    }

    /** One Room commit and one publication for a complete logical operation. */
    private suspend fun <T> mutate(labOperation:LabOperation?=null,block: suspend () -> T): T {
        val capture=if(coroutineContext[Mutation]?.owner===this)null else lab()
        val measurement=labOperation?.let{capture?.operation(it)}
        return try {withContext(dispatcher) {
        if (coroutineContext[Mutation]?.owner === this@UnifiedTabGroupManager) {
            return@withContext block()
        }
        check(awaitInitialized()) { "Tab groups have not loaded successfully" }
        mutationMutex.withLock {
            // Once admitted, finish the DB/cache commit even if the originating view disappears.
            withContext(NonCancellable + Mutation(this@UnifiedTabGroupManager)) {
                val beforeGroups = groupsCache.toMap()
                val beforeMembership = tabToGroupMap.toMap()
                try {
                    val result = database.withTransaction { block() }
                    _groupsState.value = groupsCache.values.sortedBy { it.createdAt }
                    coroutineContext[Mutation]!!.events.forEach { _groupEvents.tryEmit(it) }
                    if(capture!=null&&measurement!=null) {
                        try {
                            val changed=(beforeGroups.keys+groupsCache.keys).filter{beforeGroups[it]!=groupsCache[it]}
                            val live=liveTabs().associateBy{it.id}
                            for(id in changed) {
                                val current=groupsCache[id]
                                val storedMembers:List<String> = dao.getTabIdsInGroup(id)
                                val expectedMembers:List<String> = current?.tabIds?:emptyList()
                                capture.check(measurement,storedMembers==expectedMembers,LabInvariant.TAB_MEMBERSHIP_MISMATCH)
                                if(current!=null)for(tabId in current.tabIds) {
                                    val tab=live[tabId]?:continue // Session restore may still be loading; never remove records.
                                    fun canonical(value:String?)=value?.takeUnless{it=="profile_default"}
                                    capture.check(measurement,tab.content.private==(current.contextId=="private")&&canonical(tab.contextId)==canonical(current.contextId),LabInvariant.PROFILE_MISMATCH)
                                }
                            }
                        } catch (_:Exception) {capture.error()} // Diagnostics must not roll back a committed user mutation.
                        capture.end(measurement,if(result==false)OperationOutcome.CANCELLED else OperationOutcome.SUCCESS,CompletionProof.ROOM_COMMITTED)
                    }
                    result
                } catch (error: Throwable) {
                    groupsCache.clear(); groupsCache.putAll(beforeGroups)
                    tabToGroupMap.clear(); tabToGroupMap.putAll(beforeMembership)
                    throw error
                }
            }
        }
        }} catch (error:Throwable) {
            capture?.end(measurement,if(error is kotlinx.coroutines.CancellationException)OperationOutcome.INTERRUPTED else OperationOutcome.FAILED)
            throw error
        }
    }

    private suspend fun recordEvent(event: GroupEvent) {
        coroutineContext[Mutation]!!.events.add(event)
    }

    private fun tabMatchesContext(tabId: String, contextId: String?): Boolean {
        val tab = liveTabs().firstOrNull { it.id == tabId } ?: return false
        fun canonical(id: String?) = if (id == "profile_default") null else id
        return tab.content.private == (contextId == "private") &&
            canonical(tab.contextId) == canonical(contextId)
    }

    // In-memory cache of groups for fast access.
    //
    // Writes come from suspend fns dispatched to Dispatchers.IO (createGroup,
    // addTabToGroup, loadGroupsFromDatabase, etc.). Reads come from the Main
    // thread via Compose recomposition in ComposeTabBarWithProfileSwitcher,
    // awesomebar providers, and callers inside TabsBottomSheetFragment /
    // TabSearchFragment.
    //
    // A plain HashMap here is a cross-thread race: structural mutation on IO
    // concurrent with iteration on Main can throw ConcurrentModificationException
    // or return corrupt reads. ConcurrentHashMap gives weakly-consistent
    // iterators and lock-free reads, which is exactly what the read paths need
    // (they only ever snapshot values and sort/filter).
    private val groupsCache = ConcurrentHashMap<String, TabGroupData>()

    // Map tab ID to group ID for quick lookup. Same cross-thread access
    // pattern as groupsCache, so same reasoning applies.
    private val tabToGroupMap = ConcurrentHashMap<String, String>()

    // State flows for reactive UI updates
    private val _groupsState = MutableStateFlow<List<TabGroupData>>(emptyList())
    val groupsState: StateFlow<List<TabGroupData>> = _groupsState.asStateFlow()

    // Event flow for fine-grained updates (e.g., single group changed)
    private val _groupEvents = MutableSharedFlow<GroupEvent>(replay = 0, extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val groupEvents: SharedFlow<GroupEvent> = _groupEvents.asSharedFlow()

    companion object {
        private val AVAILABLE_COLORS = listOf(
            0xFFE57373.toInt(), // Red
            0xFF81C784.toInt(), // Green
            0xFF64B5F6.toInt(), // Blue
            0xFFFFB74D.toInt(), // Orange
            0xFFBA68C8.toInt(), // Purple
            0xFFF06292.toInt(), // Pink
            0xFF4DD0E1.toInt(), // Cyan
            0xFFFFF176.toInt()  // Yellow
        )

        @Volatile
        private var instance: UnifiedTabGroupManager? = null

        fun getInstance(context: Context): UnifiedTabGroupManager {
            return instance ?: synchronized(this) {
                instance ?: UnifiedTabGroupManager(context.applicationContext).also { 
                    instance = it
                    it.initialize()
                }
            }
        }
    }

    internal fun initialize() {
        scope.launch {
            val success = loadGroupsFromDatabase()
            initialized.complete(success)
            // Groups with null contextId are now valid - they represent the
            // default profile which shares Gecko's default partition with
            // custom tabs. The inverse migration
            // (DefaultProfileTabMigration) handles flipping legacy
            // "profile_default" rows back to null on first launch of this
            // release.
        }
    }

    private suspend fun loadGroupsFromDatabase() = withContext(dispatcher) {
        try {
            val groups = dao.getAllActiveGroups().first()
            val groupDataList = mutableListOf<TabGroupData>()
            
            for (dbGroup in groups) {
                val tabIds = dao.getTabIdsInGroup(dbGroup.id)
                val groupData = TabGroupData(
                    id = dbGroup.id,
                    name = dbGroup.name,
                    color = parseColor(dbGroup.color),
                    tabIds = tabIds,
                    createdAt = dbGroup.createdAt,
                    isCollapsed = dbGroup.isCollapsed,
                    contextId = dbGroup.contextId
                )
                groupDataList.add(groupData)
            }

            groupsCache.clear()
            tabToGroupMap.clear()

            groupDataList.forEach { groupData ->
                groupsCache[groupData.id] = groupData
                groupData.tabIds.forEach { tabId ->
                    tabToGroupMap[tabId] = groupData.id
                }
            }

            _groupsState.value = groupDataList
            true
        } catch (e: Exception) {
            // Loading groups from Room failed - schema mismatch or DB
            // corruption. Log so the empty tab-group state has an
            // explanation instead of appearing to work fine but with
            // no groups.
            android.util.Log.e("UnifiedTabGroupManager", "Failed to load groups from database", e)
            false
        }
    }

    /**
     * Creates a new group with the specified tabs
     */
    suspend fun createGroup(
        tabIds: List<String>,
        name: String? = null,
        color: Int? = null,
        profileId: String? = null,
        contextId: String? = null,
        groupId: String = UUID.randomUUID().toString()
    ): TabGroupData = mutate(LabOperation.GROUP_CREATE) {
        require(tabIds.isNotEmpty() && tabIds.distinct().size == tabIds.size) {
            "Cannot create an empty or duplicate group"
        }
        require(tabIds.all { tabMatchesContext(it, contextId) }) { "Tab scope changed" }

        // Remove tabs from existing groups first
        tabIds.forEach { tabId ->
            removeTabFromGroup(tabId, notifyChange = false)
        }

        require(!groupsCache.containsKey(groupId)) { "Group ID already exists" }
        val groupName = name ?: ""
        val groupColor = color ?: AVAILABLE_COLORS.random()
        val now = System.currentTimeMillis()

        // Create database entity
        val group = TabGroup(
            id = groupId,
            name = groupName,
            color = colorToString(groupColor),
            createdAt = now,
            isActive = true,
            contextId = contextId
        )

        dao.insertGroup(group)

        // Add tab associations
        tabIds.forEachIndexed { index, tabId ->
            val member = TabGroupMember(
                tabId = tabId,
                groupId = groupId,
                position = index
            )
            dao.insertTabGroupMember(member)
        }

        val groupData = TabGroupData(
            id = groupId,
            name = groupName,
            color = groupColor,
            tabIds = tabIds,
            createdAt = now,
            contextId = contextId
        )

        // Update cache
        groupsCache[groupId] = groupData
        tabIds.forEach { tabId ->
            tabToGroupMap[tabId] = groupId
        }

        emitStateUpdate()
        recordEvent(GroupEvent.GroupCreated(groupData))

        groupData
    }


    /**
     * Recovery from "Bez grupy" must never steal a tab moved by another
     * operation while the panel was waiting for the Room mutation lock.
     * The membership check and creation share one serialized transaction.
     * Ordinary createGroup intentionally keeps its move-from-group behavior.
     */
    suspend fun createGroupIfUngrouped(
        tabIds: List<String>,
        name: String,
        profileId: String?,
        contextId: String?
    ): TabGroupData? = mutate(LabOperation.GROUP_CREATE) {
        if (tabIds.isEmpty() || tabIds.distinct().size != tabIds.size ||
            tabIds.any { tabToGroupMap.containsKey(it) } ||
            !tabIds.all { tabMatchesContext(it, contextId) }) {
            return@mutate null
        }
        createGroup(tabIds = tabIds, name = name, profileId = profileId, contextId = contextId)
    }

    /**
     * Adds a tab to an existing group
     */
    suspend fun addTabToGroup(
        tabId: String,
        groupId: String,
        position: Int? = null,
        notifyChange: Boolean = true
    ): Boolean = mutate(LabOperation.TAB_MOVE) {
        val group = groupsCache[groupId] ?: return@mutate false
        if (!tabMatchesContext(tabId, group.contextId)) return@mutate false

        // Check if tab is already in this group
        if (group.tabIds.contains(tabId)) {
            return@mutate true
        }

        // Remove from other groups first
        removeTabFromGroup(tabId, notifyChange = false)

        val insertPosition = (position ?: group.tabIds.size).coerceIn(0, group.tabIds.size)
        val newTabIds = group.tabIds.toMutableList().apply { add(insertPosition, tabId) }

        // Reindex the full group transactionally; duplicate position values reorder tabs on restart.
        persistPositions(groupId, newTabIds)

        // Update cache
        val updatedGroup = group.copy(tabIds = newTabIds)
        groupsCache[groupId] = updatedGroup
        tabToGroupMap[tabId] = groupId

        if (notifyChange) {
            emitStateUpdate()
            recordEvent(GroupEvent.TabAddedToGroup(tabId, groupId))
        }

        true
    }

    /** Resolve the anchor inside the serialized mutation, never against a stale UI list. */
    suspend fun moveTabRelative(tabId: String, groupId: String, targetTabId: String?, after: Boolean): Boolean = mutate(LabOperation.TAB_MOVE) {
        val group = groupsCache[groupId] ?: return@mutate false
        if (!tabMatchesContext(tabId, group.contextId)) return@mutate false
        if (targetTabId != null && targetTabId !in group.tabIds) return@mutate false
        if (tabId !in group.tabIds) {
            val position = targetTabId?.let { group.tabIds.indexOf(it) + if (after) 1 else 0 }
            return@mutate addTabToGroup(tabId, groupId, position)
        }
        if (targetTabId == null || targetTabId == tabId) return@mutate true
        val ids = group.tabIds.toMutableList().apply { remove(tabId) }
        val target = ids.indexOf(targetTabId)
        ids.add(target + if (after) 1 else 0, tabId)
        persistPositions(groupId, ids)
        groupsCache[groupId] = group.copy(tabIds = ids)
        recordEvent(GroupEvent.GroupUpdated(groupId))
        true
    }

    /**
     * Removes a tab from its group
     */
    suspend fun removeTabFromGroup(
        tabId: String,
        notifyChange: Boolean = true
    ): Boolean = mutate(LabOperation.TAB_MOVE) {
        val groupId = tabToGroupMap[tabId] ?: return@mutate false
        val group = groupsCache[groupId] ?: return@mutate false

        // Remove from database
        dao.removeTabFromGroup(tabId, groupId)

        // Update cache
        val newTabIds = group.tabIds - tabId
        tabToGroupMap.remove(tabId)

        if (newTabIds.isEmpty()) {
            // Delete empty group
            deleteGroup(groupId, notifyChange = false)
        } else {
            persistPositions(groupId, newTabIds)
            val updatedGroup = group.copy(tabIds = newTabIds)
            groupsCache[groupId] = updatedGroup
        }

        if (notifyChange) {
            emitStateUpdate()
            recordEvent(GroupEvent.TabRemovedFromGroup(tabId, groupId))
        }

        true
    }

    /** Keep Room positions dense and stable, including after insert at an index. */
    private suspend fun persistPositions(groupId: String, tabIds: List<String>) {
        database.withTransaction {
            dao.removeAllTabsFromGroup(groupId)
            tabIds.forEachIndexed { index, id ->
                dao.insertTabGroupMember(TabGroupMember(tabId = id, groupId = groupId, position = index))
            }
        }
    }

    /**
     * Renames a group
     */
    suspend fun renameGroup(groupId: String, newName: String): Boolean = mutate {
        val group = groupsCache[groupId] ?: return@mutate false
        val dbGroup = dao.getGroupById(groupId) ?: return@mutate false

        val updatedDbGroup = dbGroup.copy(name = newName)
        dao.updateGroup(updatedDbGroup)

        val updatedGroup = group.copy(name = newName)
        groupsCache[groupId] = updatedGroup

        emitStateUpdate()
        recordEvent(GroupEvent.GroupRenamed(groupId, newName))

        true
    }

    /**
     * Changes group color
     */
    suspend fun changeGroupColor(groupId: String, newColor: Int): Boolean = mutate {
        val group = groupsCache[groupId] ?: return@mutate false
        val dbGroup = dao.getGroupById(groupId) ?: return@mutate false

        val updatedDbGroup = dbGroup.copy(color = colorToString(newColor))
        dao.updateGroup(updatedDbGroup)

        val updatedGroup = group.copy(color = newColor)
        groupsCache[groupId] = updatedGroup

        emitStateUpdate()
        recordEvent(GroupEvent.GroupColorChanged(groupId, newColor))

        true
    }

    /**
     * Toggles group collapsed/expanded state
     */
    suspend fun toggleGroupCollapsed(groupId: String): Boolean = mutate(if(groupsCache[groupId]?.isCollapsed==true)LabOperation.GROUP_EXPAND else LabOperation.GROUP_COLLAPSE) {
        val group = groupsCache[groupId] ?: return@mutate false
        setGroupCollapsed(groupId, !group.isCollapsed)
    }

    suspend fun setGroupCollapsed(groupId: String, collapsed: Boolean): Boolean = mutate(if(collapsed)LabOperation.GROUP_COLLAPSE else LabOperation.GROUP_EXPAND) {
        val group = groupsCache[groupId] ?: return@mutate false
        val dbGroup = dao.getGroupById(groupId) ?: return@mutate false
        dao.updateGroup(dbGroup.copy(isCollapsed = collapsed))
        groupsCache[groupId] = group.copy(isCollapsed = collapsed)
        recordEvent(GroupEvent.GroupCollapsedChanged(groupId, collapsed))
        true
    }

    /** An explicit destination may be null for the default Gecko partition. */
    suspend fun reassignGroupContext(groupId: String, contextId: String?): Boolean = mutate {
        val group = groupsCache[groupId] ?: return@mutate false
        if (group.tabIds.any { !tabMatchesContext(it, contextId) }) return@mutate false
        val stored = dao.getGroupById(groupId) ?: return@mutate false
        dao.updateGroup(stored.copy(contextId = contextId))
        groupsCache[groupId] = group.copy(contextId = contextId)
        recordEvent(GroupEvent.GroupUpdated(groupId))
        true
    }

    internal fun liveTabsSnapshot() = liveTabs()

    /**
     * Updates a group with new properties
     */
    suspend fun updateGroup(
        groupId: String,
        name: String? = null,
        color: Int? = null,
        tabIds: List<String>? = null,
        contextId: String? = null
    ): Boolean = mutate {
        val group = groupsCache[groupId] ?: return@mutate false
        val dbGroup = dao.getGroupById(groupId) ?: return@mutate false

        val updatedName = name ?: group.name
        val updatedColor = color ?: group.color
        val updatedTabIds = tabIds?.distinct() ?: group.tabIds
        val updatedContextId = contextId ?: group.contextId
        // Retain already committed closed members for Undo; validate all new assignments.
        if (updatedTabIds.any { (it !in group.tabIds || updatedContextId != group.contextId) &&
                !tabMatchesContext(it, updatedContextId) }) return@mutate false

        // Update database
        val updatedDbGroup = dbGroup.copy(
            name = updatedName,
            color = colorToString(updatedColor),
            contextId = updatedContextId
        )
        dao.updateGroup(updatedDbGroup)

        // A tab may belong to one group only. Drop other memberships.
        if (tabIds != null) {
            updatedTabIds.forEach { tabId ->
                val previousGroupId = tabToGroupMap[tabId]
                if (previousGroupId != null && previousGroupId != groupId) {
                    removeTabFromGroup(tabId, notifyChange = false)
                }
            }
            dao.removeAllTabsFromGroup(groupId)
            updatedTabIds.forEachIndexed { index, tabId ->
                val member = TabGroupMember(
                    tabId = tabId,
                    groupId = groupId,
                    position = index
                )
                dao.insertTabGroupMember(member)
            }
        }

        // Update cache
        val updatedGroup = group.copy(
            name = updatedName,
            color = updatedColor,
            tabIds = updatedTabIds,
            contextId = updatedContextId
        )
        groupsCache[groupId] = updatedGroup

        // Update tab to group map
        if (tabIds != null) {
            group.tabIds.forEach { tabToGroupMap.remove(it, groupId) }
            updatedTabIds.forEach { tabToGroupMap[it] = groupId }
        }

        emitStateUpdate()
        recordEvent(GroupEvent.GroupUpdated(groupId))

        true
    }

    /**
     * Deletes a group (ungroups all tabs)
     */
    suspend fun deleteGroup(groupId: String, notifyChange: Boolean = true): Boolean = mutate(LabOperation.GROUP_DELETE) {
        val group = groupsCache[groupId] ?: return@mutate false

        // Remove all tab associations
        dao.removeAllTabsFromGroup(groupId)
        dao.markGroupAsInactive(groupId)

        // Update cache
        group.tabIds.forEach { tabId ->
            tabToGroupMap.remove(tabId)
        }
        groupsCache.remove(groupId)

        if (notifyChange) {
            emitStateUpdate()
            recordEvent(GroupEvent.GroupDeleted(groupId))
        }

        true
    }

    /**
     * Commit stale membership removals together in one Room transaction.
     * Live members are rechecked after acquiring the manager mutation lock.
     */
    suspend fun pruneFinalizedClosedMembers(retainedIds: Set<String>): Int = mutate {
        val currentlyLive = liveTabs().mapTo(mutableSetOf()) { it.id }
        val stale = tabToGroupMap.keys.filter { it !in retainedIds && it !in currentlyLive }
        val affected = stale.mapNotNull { tabToGroupMap[it] }.toSet()
        stale.forEach { removeTabFromGroup(it, notifyChange = false) }
        if (stale.isNotEmpty()) {
            emitStateUpdate()
            affected.forEach { id ->
                if (groupsCache.containsKey(id)) recordEvent(GroupEvent.GroupUpdated(id))
                else recordEvent(GroupEvent.GroupDeleted(id))
            }
        }
        stale.size
    }

    /**
     * Gets the group containing the specified tab
     */
    fun getGroupForTab(tabId: String): TabGroupData? {
        return groupsState.value.firstOrNull { tabId in it.tabIds }
    }

    /**
     * Gets a group by ID
     */
    fun getGroup(groupId: String): TabGroupData? {
        return groupsState.value.firstOrNull { it.id == groupId }
    }

    /**
     * Gets all groups
     */
    fun getAllGroups(): List<TabGroupData> {
        return groupsState.value
    }

    /**
     * Checks if a tab is in any group
     */
    fun isTabGrouped(tabId: String): Boolean {
        return getGroupForTab(tabId) != null
    }


    /**
     * Merges two groups together
     */
    suspend fun mergeGroups(sourceGroupId: String, targetGroupId: String): Boolean = mutate(LabOperation.GROUP_MOVE) {
        if (sourceGroupId == targetGroupId) return@mutate false

        val sourceGroup = groupsCache[sourceGroupId] ?: return@mutate false
        val targetGroup = groupsCache[targetGroupId] ?: return@mutate false
        if (sourceGroup.contextId != targetGroup.contextId) return@mutate false

        run {
            // Add all tabs from source to target without individual notifications
            sourceGroup.tabIds.forEachIndexed { index, tabId ->
                val member = TabGroupMember(
                    tabId = tabId,
                    groupId = targetGroupId,
                    position = targetGroup.tabIds.size + index
                )
                dao.insertTabGroupMember(member)
                tabToGroupMap[tabId] = targetGroupId
            }

            // Delete source group from database
            dao.removeAllTabsFromGroup(sourceGroupId)
            dao.markGroupAsInactive(sourceGroupId)
            groupsCache.remove(sourceGroupId)

            // Update target group cache
            val newTabIds = targetGroup.tabIds + sourceGroup.tabIds
            groupsCache[targetGroupId] = targetGroup.copy(tabIds = newTabIds)

            emitStateUpdate()
            recordEvent(GroupEvent.GroupsMerged(sourceGroupId, targetGroupId))

        }
        true
    }

    /**
     * Handles tab closure - removes from group
     */
    suspend fun onTabClosed(tabId: String) {
        removeTabFromGroup(tabId)
    }

    /**
     * Clears all groups
     */
    suspend fun clearAllGroups() = mutate {
        val groupIds = groupsCache.keys.toList()
        groupIds.forEach { groupId ->
            deleteGroup(groupId, notifyChange = false)
        }
        emitStateUpdate()
        recordEvent(GroupEvent.AllGroupsCleared)
    }

    /** Explicit menu intent only; navigation ancestry and URL updates never call this. */
    suspend fun groupExplicitlyOpenedLink(
        newTabId: String,
        newTabUrl: String,
        sourceTabId: String?,
        sourceTabUrl: String?,
        contextId: String? = null
    ) = mutate {
        if (!tabMatchesContext(newTabId, contextId) || sourceTabId == null ||
            !tabMatchesContext(sourceTabId, contextId) || newTabId == sourceTabId) return@mutate
        // A later manual move wins over this delayed explicit request.
        if (tabToGroupMap.containsKey(newTabId)) return@mutate
        // Only proceed if we have source information
        if (sourceTabUrl == null) {
            return@mutate
        }

        // Skip system URLs
        if (newTabUrl.startsWith("about:") || newTabUrl.startsWith("chrome:")) {
            return@mutate
        }

        // Get the source tab's group
        val sourceGroupId = tabToGroupMap[sourceTabId]

        if (sourceGroupId != null) {
            // addTabToGroup validates privacy/context, including legacy default aliases.
            addTabToGroup(newTabId, sourceGroupId)
        } else {
            createGroup(tabIds = listOf(sourceTabId, newTabId), contextId = contextId)
        }
    }

    /**
     * Extract domain from URL for comparison
     */
    private fun extractDomain(url: String): String {
        return try {
            url.toUri().host?.replace("www.", "") ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private suspend fun emitStateUpdate() {
        // Publication belongs to mutate(), after the outer Room transaction commits.
    }

    private fun parseColor(colorString: String): Int {
        return com.prirai.android.nira.theme.ColorConstants.TabGroups.parseColor(colorString)
    }

    private fun colorToString(color: Int): String {
        return com.prirai.android.nira.theme.ColorConstants.TabGroups.getColorName(color)
    }
}

/**
 * Immutable data class representing a tab group
 */
data class TabGroupData(
    val id: String,
    val name: String,
    val color: Int,
    val tabIds: List<String>,
    val createdAt: Long,
    val isCollapsed: Boolean = false,
    val contextId: String? = null
) {
    val tabCount: Int get() = tabIds.size
    val isEmpty: Boolean get() = tabIds.isEmpty()
    val isNotEmpty: Boolean get() = tabIds.isNotEmpty()
}

/**
 * Events emitted when groups change
 */
sealed class GroupEvent {
    data class GroupCreated(val group: TabGroupData) : GroupEvent()
    data class GroupDeleted(val groupId: String) : GroupEvent()
    data class GroupRenamed(val groupId: String, val newName: String) : GroupEvent()
    data class GroupColorChanged(val groupId: String, val newColor: Int) : GroupEvent()
    data class GroupCollapsedChanged(val groupId: String, val isCollapsed: Boolean) : GroupEvent()
    data class GroupUpdated(val groupId: String) : GroupEvent()
    data class TabAddedToGroup(val tabId: String, val groupId: String) : GroupEvent()
    data class TabRemovedFromGroup(val tabId: String, val groupId: String) : GroupEvent()
    data class GroupsMerged(val sourceGroupId: String, val targetGroupId: String) : GroupEvent()
    object AllGroupsCleared : GroupEvent()
}
