package com.prirai.android.nira.browser.panels

import android.app.Activity
import android.app.Application
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.prirai.android.nira.browser.SwipeGestureLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class SidePanelInfrastructureTest {

    private fun parent(activity: Activity): SwipeGestureLayout =
        SwipeGestureLayout(activity).also { parent ->
            activity.setContentView(parent)
            parent.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
            )
            parent.layout(0, 0, 400, 800)
        }

    @Test fun mountingIsInertAndLeavesLegacyPageTouchUnaffected() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = parent(activity)
        val infrastructure = SidePanelInfrastructure.attach(root, 36) { true }
        assertEquals(View.GONE, infrastructure.host.visibility)
        assertFalse(infrastructure.onSwipeStarted(PointF(1f, 300f), PointF(100f, 300f)))
        assertFalse(infrastructure.activate(SidePanelSide.LEFT, 300)) // no fragment view yet
        var receivedDown = 0
        val underlay = View(activity).apply {
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) receivedDown++
                true
            }
        }
        // A closed host cannot steal a tap even when it is the topmost child.
        root.addView(underlay, 0, FrameLayout.LayoutParams(-1, -1))
        // The new child must be measured/layouted before Android can hit-test it.
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 400, 800)
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL)) {
            val touch = MotionEvent.obtain(0L, 100L, action, 200f, 300f, 0)
            root.dispatchTouchEvent(touch)
            touch.recycle()
        }
        assertEquals(1, receivedDown)
        assertFalse(infrastructure.closeOnBack())
        infrastructure.dispose()
        assertEquals(1, root.childCount)
        activity.finish()
    }

    @Test fun explicitActivationIsRequiredAndEdgeDirectionIsChecked() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = parent(activity)
        var available = false
        val infrastructure = SidePanelInfrastructure.attach(root, 36) { available }
        infrastructure.host.leftContainer.addView(View(activity))
        assertTrue(infrastructure.activate(SidePanelSide.LEFT, 280))
        assertEquals(View.GONE, infrastructure.host.visibility)
        assertFalse(infrastructure.onSwipeStarted(PointF(2f, 200f), PointF(70f, 200f)))
        available = true
        assertFalse(infrastructure.onSwipeStarted(PointF(200f, 200f), PointF(250f, 200f)))
        assertFalse(infrastructure.onSwipeStarted(PointF(2f, 200f), PointF(-50f, 200f)))
        assertTrue(infrastructure.onSwipeStarted(PointF(2f, 200f), PointF(50f, 200f)))
        infrastructure.onSwipeUpdate(-70f, 0f)
        assertEquals(View.VISIBLE, infrastructure.host.visibility)
        // The initial onScroll is already applied as dx=48px; the synthetic
        // next update is intentionally skipped to prevent double-counting.
        assertEquals(-232f, infrastructure.host.leftContainer.translationX, 0.001f)
        assertFalse(infrastructure.onSwipeStarted(PointF(399f, 200f), PointF(320f, 200f)))
        infrastructure.resetForFullscreen()
        assertEquals(View.GONE, infrastructure.host.visibility)
        assertFalse(infrastructure.closeOnBack())
        infrastructure.dispose()
        activity.finish()
    }

    @Test fun cleanupDisablesCallbackAndAllowsSafeReattachment() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = parent(activity)
        val first = SidePanelInfrastructure.attach(root, 36) { true }
        assertEquals(1, root.childCount)
        first.dispose()
        first.dispose()
        assertEquals(0, root.childCount)
        assertFalse(first.onSwipeStarted(PointF(2f, 100f), PointF(80f, 100f)))
        val second = SidePanelInfrastructure.attach(root, 36) { true }
        assertEquals(1, root.childCount)
        assertEquals(View.GONE, second.host.visibility)
        second.dispose()
        assertEquals(0, root.childCount)
        activity.finish()
    }

    @Test fun disabledSwipeContainerCannotBeClaimedEvenWithContent() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = parent(activity)
        val infrastructure = SidePanelInfrastructure.attach(root, 36) { true }
        infrastructure.host.rightContainer.addView(View(activity))
        assertTrue(infrastructure.activate(SidePanelSide.RIGHT, 260))
        root.isSwipeEnabled = false
        assertFalse(infrastructure.onSwipeStarted(PointF(398f, 200f), PointF(330f, 200f)))
        infrastructure.dispose()
        activity.finish()
    }
}
