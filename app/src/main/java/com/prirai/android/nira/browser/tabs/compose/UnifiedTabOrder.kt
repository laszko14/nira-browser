package com.prirai.android.nira.browser.tabs.compose

import kotlinx.serialization.Serializable

/**
 * Single source of truth for tab ordering across all views.
 * Represents the hierarchical structure of tabs and groups.
 */
@Serializable
data class UnifiedTabOrder(
    val profileId: String,
    val primaryOrder: List<OrderItem>,
    val lastModified: Long = System.currentTimeMillis()
) {
    @Serializable
    sealed class OrderItem {
        @Serializable
        data class SingleTab(val tabId: String) : OrderItem()
        
        @Serializable
        data class TabGroup(
            val groupId: String,
            val groupName: String,
            val color: Int,
            val isExpanded: Boolean,
            val tabIds: List<String>
        ) : OrderItem()
    }
    
    /** Reconcile Room membership without destroying slots during partial session restore. */
    fun reconciled(tabIds: List<String>, groups: List<com.prirai.android.nira.browser.tabgroups.TabGroupData>): UnifiedTabOrder {
        val groupsById = groups.associateBy { it.id }
        val groupedIds = groups.flatMap { it.tabIds }.toSet()
        val seenTabs = mutableSetOf<String>()
        val seenGroups = mutableSetOf<String>()
        val items = mutableListOf<OrderItem>()
        fun addSingle(id: String) {
            if (id !in groupedIds && seenTabs.add(id)) items.add(OrderItem.SingleTab(id))
        }
        fun addGroup(group: com.prirai.android.nira.browser.tabgroups.TabGroupData, expanded: Boolean) {
            if (seenGroups.add(group.id)) {
                items.add(OrderItem.TabGroup(group.id, group.name, group.color, expanded, group.tabIds.distinct()))
                seenTabs.addAll(group.tabIds)
            }
        }
        primaryOrder.forEach { item ->
            when (item) {
                is OrderItem.SingleTab -> {
                    // A newly created group inherits the earliest member's container slot.
                    val newGroup = groups.firstOrNull { item.tabId in it.tabIds &&
                        primaryOrder.none { old -> old is OrderItem.TabGroup && old.groupId == it.id } }
                    if (newGroup != null) addGroup(newGroup, !newGroup.isCollapsed)
                    else addSingle(item.tabId)
                }
                is OrderItem.TabGroup -> {
                    groupsById[item.groupId]?.let { addGroup(it, item.isExpanded) }
                    // Detaching/deleting a group retains the former members' relative slots.
                    item.tabIds.forEach(::addSingle)
                }
            }
        }
        groups.forEach { addGroup(it, !it.isCollapsed) }
        tabIds.forEach(::addSingle)
        return if (items == primaryOrder) this else copy(primaryOrder = items, lastModified = System.currentTimeMillis())
    }

    /**
     * Get all tab IDs in flat order
     */
    fun getAllTabIds(): List<String> {
        return primaryOrder.flatMap { item ->
            when (item) {
                is OrderItem.SingleTab -> listOf(item.tabId)
                is OrderItem.TabGroup -> item.tabIds
            }
        }
    }
    
    /**
     * Find which group contains a specific tab
     */
    fun findGroupContaining(tabId: String): OrderItem.TabGroup? {
        return primaryOrder.filterIsInstance<OrderItem.TabGroup>()
            .find { tabId in it.tabIds }
    }
    
    /**
     * Get the flat position of a tab (across all groups)
     */
    fun getTabPosition(tabId: String): Int? {
        val allTabs = getAllTabIds()
        return allTabs.indexOf(tabId).takeIf { it >= 0 }
    }
    
    /**
     * Get the position of an item in the primary order
     */
    fun getItemPosition(itemId: String): Int? {
        return primaryOrder.indexOfFirst { item ->
            when (item) {
                is OrderItem.SingleTab -> item.tabId == itemId
                is OrderItem.TabGroup -> item.groupId == itemId
            }
        }.takeIf { it >= 0 }
    }
}
