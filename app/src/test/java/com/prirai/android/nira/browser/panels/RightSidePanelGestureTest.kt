package com.prirai.android.nira.browser.panels

import android.app.Activity
import android.app.Application
import android.graphics.PointF
import android.view.View
import com.prirai.android.nira.browser.SwipeGestureLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Right-hand panel gestures are the LEFT mirror, not a second motion engine. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class RightSidePanelGestureTest {
    private fun fixture(): Pair<Activity, SwipeGestureLayout> {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = SwipeGestureLayout(activity)
        activity.setContentView(root)
        root.measure(View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(840, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 420, 840)
        return activity to root
    }

    @Test fun rightEdgeMountsOnlyWhenQualifiedAndUsesFullFirstDisplacement() {
        val (activity, root) = fixture()
        val panels = SidePanelInfrastructure.attach(root, 36) { true }
        var mounts = 0
        panels.prepareRightOnGesture = {
            mounts++
            panels.host.rightContainer.addView(View(activity))
            panels.activate(SidePanelSide.RIGHT, 280)
        }
        assertFalse(panels.onSwipeStarted(PointF(220f, 180f), PointF(120f, 180f)))
        assertFalse(panels.onSwipeStarted(PointF(418f, 180f), PointF(465f, 180f)))
        assertEquals(0, mounts)
        assertTrue(panels.onSwipeStarted(PointF(418f, 180f), PointF(348f, 180f)))
        assertEquals(1, mounts)
        assertEquals(SidePanelSide.RIGHT, panels.panelSnapshot.side)
        assertEquals(70f / 280f, panels.panelSnapshot.progress, 0.001f)
        assertEquals(210f, panels.host.rightContainer.translationX, 0.001f)
        panels.onSwipeUpdate(70f, 0f) // first GestureDetector onScroll update is already counted
        assertEquals(70f / 280f, panels.panelSnapshot.progress, 0.001f)
        panels.onSwipeUpdate(42f, 0f)
        assertEquals(112f / 280f, panels.panelSnapshot.progress, 0.001f)
        panels.dispose()
        activity.finish()
    }

    @Test fun rightSwipeCanReverseAnOngoingOpenAndBackStartsClosingFromCurrentPosition() {
        val (activity, root) = fixture()
        val panels = SidePanelInfrastructure.attach(root, 36) { true }
        panels.host.rightContainer.addView(View(activity))
        assertTrue(panels.activate(SidePanelSide.RIGHT, 300))
        assertTrue(panels.onSwipeStarted(PointF(418f, 220f), PointF(166f, 220f)))
        panels.onSwipeFinished(0f, 0f)
        assertEquals(SidePanelPhase.SETTLING, panels.panelSnapshot.phase)
        // Inside right panel [120, 420], a swipe RIGHT begins closing.
        assertTrue(panels.onSwipeStarted(PointF(205f, 220f), PointF(242f, 220f)))
        assertEquals(SidePanelPhase.DRAGGING, panels.panelSnapshot.phase)
        assertEquals(SidePanelSide.RIGHT, panels.panelSnapshot.side)
        assertTrue(panels.closeOnBack())
        assertEquals(false, panels.panelSnapshot.targetOpen)
        panels.dispose()
        activity.finish()
    }

    @Test fun leftAndRightNeverShareAHostAndLegacyMustStandDown() {
        val (activity, root) = fixture()
        val panels = SidePanelInfrastructure.attach(root, 36) { true }
        panels.host.leftContainer.addView(View(activity))
        assertTrue(panels.activate(SidePanelSide.LEFT, 300))
        var rightMountCalls = 0
        panels.prepareRightOnGesture = { rightMountCalls++; true }
        assertFalse(panels.onSwipeStarted(PointF(418f, 250f), PointF(310f, 250f)))
        assertEquals(0, rightMountCalls)
        assertTrue(panels.anyPanelEngaged())
        panels.host.rightContainer.addView(View(activity))
        assertFalse(panels.activate(SidePanelSide.RIGHT, 280))
        panels.dispose()
        activity.finish()
    }

    @Test fun rightCloseAndOutsideBackAreConsumedAndFullscreenCleansState() {
        val (activity, root) = fixture()
        val panels = SidePanelInfrastructure.attach(root, 36) { true }
        panels.host.rightContainer.addView(View(activity))
        assertTrue(panels.activate(SidePanelSide.RIGHT, 260))
        assertTrue(panels.onSwipeStarted(PointF(418f, 180f), PointF(260f, 180f)))
        assertTrue(panels.closeOnBack())
        assertEquals(false, panels.panelSnapshot.targetOpen)
        assertTrue(panels.closeOnBack())
        panels.resetForFullscreen()
        assertEquals(SidePanelPhase.CLOSED, panels.panelSnapshot.phase)
        assertEquals(View.GONE, panels.host.visibility)
        panels.dispose()
        activity.finish()
    }

    @Test fun cancelledRightGestureReturnsToOriginalTargetWithoutOpening() {
        val (activity, root) = fixture()
        val panels = SidePanelInfrastructure.attach(root, 36) { true }
        panels.host.rightContainer.addView(View(activity))
        assertTrue(panels.activate(SidePanelSide.RIGHT, 240))
        assertTrue(panels.onSwipeStarted(PointF(418f, 180f), PointF(265f, 180f)))
        panels.onSwipeCancelled()
        assertEquals(SidePanelPhase.SETTLING, panels.panelSnapshot.phase)
        assertEquals(false, panels.panelSnapshot.targetOpen)
        panels.dispose()
        activity.finish()
    }
}
