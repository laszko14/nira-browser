package com.prirai.android.nira.browser.panels

import android.app.Activity
import android.app.Application
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class SidePanelHostTest {
    @Test fun detachedFromActiveUiByDefaultAndClosedHostDoesNotInterceptPageTouches() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val parent = FrameLayout(activity)
        var touches = 0
        val web = View(activity).apply {
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) touches++
                true
            }
        }
        parent.addView(web, FrameLayout.LayoutParams(-1, -1))
        val host = SidePanelHost(activity)
        parent.addView(host, FrameLayout.LayoutParams(-1, -1))
        activity.setContentView(parent)
        parent.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, 400, 800)
        assertEquals(View.GONE, host.visibility)
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(0, 5, action, 200f, 400f, 0)
            parent.dispatchTouchEvent(event)
            event.recycle()
        }
        assertEquals(1, touches)
        activity.finish()
    }

    @Test fun rendersOnlyOneSideWithStableWidthAndFractionalTranslation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = SidePanelHost(activity)
        host.setPanelWidth(SidePanelSide.LEFT, 320)
        host.setPanelWidth(SidePanelSide.RIGHT, 260)
        val state = SidePanelStateMachine()
        assertTrue(state.startDrag(SidePanelSide.LEFT))
        assertTrue(state.dragTo(0.25f))
        host.render(state.snapshot)
        assertEquals(View.VISIBLE, host.visibility)
        assertEquals(View.VISIBLE, host.leftContainer.visibility)
        assertEquals(View.GONE, host.rightContainer.visibility)
        assertEquals(320, host.leftContainer.layoutParams.width)
        assertEquals(-240f, host.leftContainer.translationX, 0.001f)
        assertEquals(0.55f * 0.25f, host.getChildAt(0).alpha, 0.001f)
        assertTrue(state.startSettle(SidePanelSide.LEFT, false))
        assertTrue(state.finishSettle(SidePanelSide.LEFT))
        host.render(state.snapshot)
        assertEquals(View.GONE, host.visibility)
        assertTrue(state.startDrag(SidePanelSide.RIGHT))
        assertTrue(state.dragTo(0.5f))
        host.render(state.snapshot)
        assertEquals(View.GONE, host.leftContainer.visibility)
        assertEquals(View.VISIBLE, host.rightContainer.visibility)
        assertEquals(130f, host.rightContainer.translationX, 0.001f)
        activity.finish()
    }

    @Test fun widthMustBeExplicitAndOutsideTapDelegatesWithoutMakingItsOwnPolicy() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = SidePanelHost(activity)
        val state = SidePanelStateMachine()
        assertTrue(state.startSettle(SidePanelSide.LEFT, true))
        try {
            host.render(state.snapshot)
            fail("Rendering before width measurement must fail")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("width"))
        }
        try {
            host.setPanelWidth(SidePanelSide.LEFT, 0)
            fail("Invalid panel width must fail")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
        host.setPanelWidth(SidePanelSide.LEFT, 300)
        host.render(state.snapshot)
        var outsideTaps = 0
        host.onOutsideTap = { outsideTaps++ }
        host.getChildAt(0).performClick()
        assertEquals(1, outsideTaps)
        assertEquals(SidePanelPhase.SETTLING, state.snapshot.phase) // controller owns the decision
        activity.finish()
    }
}
