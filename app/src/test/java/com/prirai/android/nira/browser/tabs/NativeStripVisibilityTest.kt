package com.prirai.android.nira.browser.tabs

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.MotionEvent
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
    @Test fun sixTabGroupRemainsStillWithOtherGroupsOutsideViewport() {
        val strip = strip()
        val tabs = tabs(40)
        val first = TabGroupData("first", "A", 0xff336699.toInt(), (0..5).map { "$it" }, 0)
        val second = TabGroupData("second", "B", first.color, (6..11).map { "$it" }, 1)
        strip.updateGroups(listOf(first, second))
        strip.updateTabs(tabs, "0"); layout(strip, 600)
        val header = descendants(strip).single {
            it.contentDescription?.toString()?.startsWith("Zwiń grupę B") == true
        }
        val segment = header.parent as View
        assertTrue(segment.width <= scroll(strip).width - 20)
        scroll(strip).scrollTo(segment.left - 10, 0)
        val before = scroll(strip).scrollX
        for (id in listOf("6", "8", "11")) {
            strip.updateTabs(tabs, id); layout(strip, 600)
            assertEquals(before, scroll(strip).scrollX)
        }
    }
    @Test fun latestSelectionWinsBeforePendingChecksRunAndAfterResize() {
        val strip = strip()
        val tabs = tabs(30)
        strip.updateTabs(tabs, "0"); layout(strip)
        strip.updateTabs(tabs, "29")
        strip.updateTabs(tabs, "0")
        layout(strip)
        assertEquals(0, scroll(strip).scrollX)
        strip.updateTabs(tabs, "29"); layout(strip)
        layout(strip, 600)
        val target = descendants(strip).single { it.contentDescription == "Karta: 29" }
        val row = scroll(strip).getChildAt(0)
        var left = target.left
        var parent = target.parent as View
        while (parent !== row) {
            left += parent.left
            parent = parent.parent as View
        }
        assertTrue(left >= scroll(strip).scrollX)
        assertTrue(left + target.width <= scroll(strip).scrollX + scroll(strip).width)
    }

    @Test fun touchBeginningOnClickablePillCancelsPendingAutomaticScroll() {
        val strip = strip()
        val tabs = tabs(30)
        strip.updateTabs(tabs, "0"); layout(strip)
        strip.updateTabs(tabs, "29")
        // Dispatch before draining the posted visibility check. Advancing the
        // Robolectric looper here can finish the animation before the touch.
        val scroller = scroll(strip)
        val pill = descendants(strip).single { it.contentDescription == "Karta: 0" }
        var x = pill.width / 2f + pill.left
        var parent = pill.parent as View
        while (parent !== scroller) {
            x += parent.left
            parent = parent.parent as View
        }
        x -= scroller.scrollX
        val y = scroller.height / 2f
        assertTrue(x >= 0 && x < scroller.width)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        scroller.dispatchTouchEvent(down)
        down.recycle()
        val before = scroller.scrollX
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertEquals(before, scroller.scrollX)
        val up = MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, x, y, 0)
        scroller.dispatchTouchEvent(up)
        up.recycle()
    }

}
