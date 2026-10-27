package com.prirai.android.nira.browser.tabs

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.prirai.android.nira.browser.tabgroups.TabGroupData
import com.prirai.android.nira.browser.tabs.compose.*
import com.prirai.android.nira.components.toolbar.modern.PortraitNativeTabStrip
import mozilla.components.browser.state.state.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class NativeStripTest {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun tab(id: String) = TabSessionState(id = id, content = ContentState(url = "about:homepage", title = id))
    @Test fun nativePortraitUsesTheSameInterleavedOrderAndLiveCountsAsUnifiedItems() {
        val strip = PortraitNativeTabStrip(RuntimeEnvironment.getApplication())
        val tabs = listOf("a","b","solo","c").map(::tab)
        val a = TabGroupData("A", "Filmy", 0xff336699.toInt(), listOf("b","a"), 0)
        val b = a.copy(id = "B", name = "Druga", tabIds = listOf("c"))
        val order = UnifiedTabOrder("default", listOf(
            UnifiedTabOrder.OrderItem.TabGroup(a.id,a.name,a.color,true,a.tabIds),
            UnifiedTabOrder.OrderItem.SingleTab("solo"),
            UnifiedTabOrder.OrderItem.TabGroup(b.id,b.name,b.color,true,b.tabIds)))
        strip.updateGroups(listOf(a,b)); strip.updateOrder(order); strip.updateTabs(tabs,"a")
        val captions = descendants(strip).mapNotNull { it.contentDescription?.toString() }.filter { it.startsWith("Karta: ") }
        assertEquals(listOf("Karta: b","Karta: a","Karta: solo","Karta: c"), captions)
        strip.updateTabs(tabs.filterNot { it.id == "b" }, "a")
        assertTrue(descendants(strip).filterIsInstance<TextView>().any { it.text.toString().contains("(1) Filmy") })
        strip.updateTabs(tabs, "a")
        assertTrue(descendants(strip).filterIsInstance<TextView>().any { it.text.toString().contains("(2) Filmy") })
    }
    @Test fun collapseUsesSavedOrderAndInvokesExactlyOneCommitCallback() {
        val strip = PortraitNativeTabStrip(RuntimeEnvironment.getApplication())
        val group = TabGroupData("g", "Filmy", 0xff336699.toInt(), listOf("a","b"), 0)
        val entry = UnifiedTabOrder.OrderItem.TabGroup("g", "Filmy", group.color, false, group.tabIds)
        strip.updateGroups(listOf(group)); strip.updateOrder(UnifiedTabOrder("default", listOf(entry)))
        strip.updateTabs(listOf(tab("a"),tab("b")), "a")
        assertTrue(descendants(strip).none { it.contentDescription?.toString()?.startsWith("Karta: ") == true })
        var commits = 0
        strip.onGroupToggle = { assertEquals("g", it); commits++ }
        assertTrue(descendants(strip).filterIsInstance<TextView>().none { it.text.toString().contains("Filmy") })
        descendants(strip).filterIsInstance<TextView>().single { it.text.toString().contains("(2)") }.performClick()
        assertEquals(1, commits)
        strip.updateOrder(UnifiedTabOrder("default", listOf(entry.copy(isExpanded=true))))
        assertEquals(2, descendants(strip).count { it.contentDescription?.toString()?.startsWith("Karta: ") == true })
    }
    @Test fun titleFaviconAndSelectionChangesReusePillsWithoutRebuildingTheRow() {
        val strip = PortraitNativeTabStrip(RuntimeEnvironment.getApplication())
        val original = listOf(tab("a"), tab("b"))
        strip.updateTabs(original, "a")
        fun pills() = descendants(strip).filter {
            it.contentDescription?.toString()?.startsWith("Karta: ") == true
        }
        val initial = pills()
        val firstBackground = initial[0].background
        val changed = original[0].copy(
            content = ContentState(url = "about:blank", title = "Nowy tytuł", loading = true)
        )
        strip.updateTabs(listOf(changed, original[1]), "b")
        val updated = pills()
        assertSame(initial[0], updated[0])
        assertSame(initial[1], updated[1])
        assertEquals("Karta: Nowy tytuł", updated[0].contentDescription)
        assertNotSame(firstBackground, updated[0].background)
        strip.updateTabs(listOf(original[1], changed), "b")
        assertNotSame(initial[0], pills()[1]) // structural reorder is still rebuilt
    }
}
