package com.prirai.android.nira.browser.tabs

import com.prirai.android.nira.browser.tabs.compose.BarItem
import com.prirai.android.nira.browser.tabs.compose.UnifiedTabOrder
import com.prirai.android.nira.browser.tabs.compose.buildBarItems
import com.prirai.android.nira.browser.tabgroups.TabGroupData
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.TabSessionState
import org.junit.Assert.*
import org.junit.Test

class TabBarOrderProjectionTest {
    private fun tab(id: String) =
        TabSessionState(id = id, content = ContentState(url = "https://example.org/$id"))

    @Test fun mixedOrderProjectsOnlyLiveMembersAndAppendsNewTabsOnce() {
        val tabs = listOf(tab("fresh"), tab("b"), tab("a"), tab("c"))
        val group = TabGroupData(id = "g", name = "G", color = 11, tabIds = listOf("b", "missing", "c"), createdAt = 1)
        val order = UnifiedTabOrder("default", listOf(
            UnifiedTabOrder.OrderItem.SingleTab("a"),
            UnifiedTabOrder.OrderItem.SingleTab("gone"),
            UnifiedTabOrder.OrderItem.TabGroup("g", "old name", 5, false, group.tabIds)
        ))
        val items = buildBarItems(order, tabs, listOf(group))
        assertEquals(3, items.size)
        assertEquals("a", (items[0] as BarItem.SingleTab).tab.id)
        val renderedGroup = items[1] as BarItem.Group
        assertEquals("g", renderedGroup.groupId)
        assertEquals(11, renderedGroup.color)
        assertEquals(listOf("b", "c"), renderedGroup.tabs.map { it.id })
        assertFalse(renderedGroup.isExpanded)
        assertEquals("fresh", (items[2] as BarItem.SingleTab).tab.id)
    }

    @Test fun manySinglesKeepExactOrderWithoutRepeatedLinearLookups() {
        val tabs = (0 until 400).map { tab("tab$it") }
        val order = UnifiedTabOrder("default", tabs.asReversed().map {
            UnifiedTabOrder.OrderItem.SingleTab(it.id)
        })
        assertEquals(tabs.asReversed().map { it.id },
            buildBarItems(order, tabs, emptyList()).map { (it as BarItem.SingleTab).tab.id })
        assertEquals(tabs.map { it.id },
            buildBarItems(null, tabs, emptyList()).map { (it as BarItem.SingleTab).tab.id })
    }
}
