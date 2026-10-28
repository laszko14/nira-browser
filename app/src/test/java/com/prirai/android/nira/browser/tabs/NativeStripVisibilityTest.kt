package com.prirai.android.nira.browser.tabs

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.TextView
import com.prirai.android.nira.browser.tabgroups.TabGroupData
import com.prirai.android.nira.components.toolbar.modern.PortraitNativeTabStrip
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.TabSessionState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class NativeStripVisibilityTest {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun tabs(count: Int) = (0 until count).map {
        TabSessionState(id = "$it", content = ContentState(url = "about:homepage", title = "$it"))
    }
    private fun layout(strip: View, width: Int = 320) {
        strip.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY))
        strip.layout(0, 0, width, 100)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
    }
    private fun strip(): PortraitNativeTabStrip {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        return PortraitNativeTabStrip(activity).also { activity.setContentView(it) }
    }
    private fun scroll(strip: View) = descendants(strip).filterIsInstance<HorizontalScrollView>().single()

    @Test fun offscreenSelectionIsMeasuredAndClosingToOneReturnsToStart() {
        val strip = strip()
        val tabs = tabs(30)
        strip.updateTabs(tabs, "0"); layout(strip)
        strip.updateTabs(tabs, "29"); layout(strip)
        assertTrue(scroll(strip).scrollX > 0)
        strip.updateTabs(listOf(tabs.last()), "29"); layout(strip)
        assertEquals(0, scroll(strip).scrollX)
    }
    @Test fun fittingContentAndPageMetadataNeverScroll() {
        val strip = strip()
        val tabs = tabs(2)
        strip.updateTabs(tabs, "0"); layout(strip, 800)
        strip.updateTabs(tabs, "1"); layout(strip, 800)
        assertEquals(0, scroll(strip).scrollX)
        val many = tabs(30)
        strip.updateTabs(many, "0"); layout(strip)
        scroll(strip).scrollTo(150, 0)
        val before = scroll(strip).scrollX
        strip.updateTabs(many.map { it.copy(content = it.content.copy(title = "updated")) }, "0")
        layout(strip)
        assertEquals(before, scroll(strip).scrollX)
    }
    @Test fun collapsedActiveGroupRevealsHeaderWithoutExpanding() {
        val strip = strip()
        val tabs = tabs(30)
        strip.updateGroups(listOf(TabGroupData("g", "Grupa", 0xff336699.toInt(), listOf("29"), 0,
            isCollapsed = true)))
        // Native fallback puts groups first without saved order; use the persisted interleaving.
        strip.updateOrder(com.prirai.android.nira.browser.tabs.compose.UnifiedTabOrder("default",
            tabs.dropLast(1).map { com.prirai.android.nira.browser.tabs.compose.UnifiedTabOrder.OrderItem.SingleTab(it.id) } +
                com.prirai.android.nira.browser.tabs.compose.UnifiedTabOrder.OrderItem.TabGroup("g", "Grupa",
                    0xff336699.toInt(), false, listOf("29"))))
        strip.updateTabs(tabs, "29"); layout(strip)
        assertTrue(scroll(strip).scrollX > 0)
        assertFalse(descendants(strip).any { it.contentDescription == "Karta: 29" })
        assertTrue(descendants(strip).filterIsInstance<TextView>().any {
            it.contentDescription?.toString()?.startsWith("Rozwiń grupę") == true
        })
    }
}
