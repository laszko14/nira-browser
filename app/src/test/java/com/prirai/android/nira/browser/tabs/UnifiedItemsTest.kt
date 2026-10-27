package com.prirai.android.nira.browser.tabs

import com.prirai.android.nira.browser.tabs.compose.*
import com.prirai.android.nira.browser.tabgroups.TabGroupData
import mozilla.components.browser.state.state.TabSessionState
import org.junit.Assert.*
import org.junit.Test

class UnifiedItemsTest {
    private val tabs = listOf("a", "b", "c", "solo").map { TabSessionState(id = it, content = mozilla.components.browser.state.state.ContentState(url = "https://example.org")) }
    private val group = TabGroupData("g", "Filmy", 1, listOf("c", "a", "b"), 0)
    private fun saved(vararg items: UnifiedTabOrder.OrderItem) = UnifiedTabOrder("default", items.toList())
    private fun entry() = UnifiedTabOrder.OrderItem.TabGroup("g", "old", 1, true, listOf("a", "b"))
    @Test fun roomMembershipAndPositionOverrideLaggingOrderCopy() {
        val items = UnifiedItemBuilder.buildItems(saved(entry()), tabs, listOf(group), setOf("g"))
        val container = items.filterIsInstance<UnifiedItem.GroupContainer>().single()
        assertEquals(listOf("c", "a", "b"), container.children.map { it.id })
        assertEquals(3, container.tabCount)
        assertEquals(listOf("solo"), items.filterIsInstance<UnifiedItem.SingleTab>().map { it.tab.id })
    }
    @Test fun recentlyGroupedSoloIsNotRenderedTwiceOrStolenFromItsGroup() {
        val items = UnifiedItemBuilder.buildItems(saved(UnifiedTabOrder.OrderItem.SingleTab("c"), entry()), tabs, listOf(group), setOf("g"))
        assertEquals(listOf("c", "a", "b"), items.filterIsInstance<UnifiedItem.GroupContainer>().single().children.map { it.id })
        assertFalse(items.filterIsInstance<UnifiedItem.SingleTab>().any { it.tab.id == "c" })
    }
    @Test fun newlyCommittedGroupAppearsBeforeOrderSnapshotCatchesUp() {
        val items = UnifiedItemBuilder.buildItems(saved(UnifiedTabOrder.OrderItem.SingleTab("solo")), tabs, listOf(group), emptySet())
        assertEquals(1, items.filterIsInstance<UnifiedItem.GroupContainer>().size)
        assertEquals("solo", (items.first() as UnifiedItem.SingleTab).tab.id)
        assertEquals(false, items.filterIsInstance<UnifiedItem.GroupContainer>().single().isExpanded)
    }
    @Test fun interleavedGroupsAndSoloCardsRemainStableWhenMembersCloseAndUndo() {
        val a = group.copy(id = "A", tabIds = listOf("a","b"))
        val b = group.copy(id = "B", tabIds = listOf("c"))
        val original = saved(
            UnifiedTabOrder.OrderItem.TabGroup("A", a.name, a.color, false, a.tabIds),
            UnifiedTabOrder.OrderItem.SingleTab("solo"),
            UnifiedTabOrder.OrderItem.TabGroup("B", b.name, b.color, true, b.tabIds)
        )
        val closed = original.reconciled(tabs.filterNot { it.id == "b" }.map { it.id }, listOf(a,b))
        assertEquals(original.primaryOrder, closed.primaryOrder)
        val afterUndo = closed.reconciled(tabs.map { it.id }, listOf(a,b))
        val items = UnifiedItemBuilder.buildItems(afterUndo, tabs, listOf(a,b), setOf("B"))
        assertEquals(listOf("group_A", "tab_solo", "group_B"), items.map { it.id })
        assertEquals(listOf("a","b"), (items.first() as UnifiedItem.GroupContainer).children.map { it.id })
        assertFalse((items.first() as UnifiedItem.GroupContainer).isExpanded)
    }
    @Test fun deletingGroupUngroupsMembersAtTheOriginalSlot() {
        val original = saved(UnifiedTabOrder.OrderItem.SingleTab("solo"), entry())
        val reconciled = original.reconciled(tabs.map { it.id }, emptyList())
        assertEquals(listOf("solo","a","b","c"), reconciled.getAllTabIds())
    }

}
