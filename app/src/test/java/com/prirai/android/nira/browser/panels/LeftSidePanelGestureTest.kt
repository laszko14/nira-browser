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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class LeftSidePanelGestureTest {
    private fun fixture(): Pair<Activity, SwipeGestureLayout> {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val layout = SwipeGestureLayout(activity)
        activity.setContentView(layout)
        layout.measure(View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(840, View.MeasureSpec.EXACTLY))
        layout.layout(0, 0, 420, 840)
        return activity to layout
    }

    @Test fun leftSwipeMountsLazilyAndTracksInitialDistanceExactlyOnce() {
        val (activity, root) = fixture()
        val panels = SidePanelInfrastructure.attach(root, 36) { true }
        var mounts = 0
        panels.prepareLeftOnGesture = {
            mounts++
            panels.host.leftContainer.addView(View(activity))
            panels.activate(SidePanelSide.LEFT, 320)
        }
        assertEquals(0, mounts)
        assertEquals(View.GONE, panels.host.visibility)
        assertFalse(panels.onSwipeStarted(PointF(190f, 200f), PointF(250f, 200f)))
        assertFalse(panels.onSwipeStarted(PointF(2f, 200f), PointF(-60f, 200f)))
        assertEquals(0, mounts)
        assertTrue(panels.onSwipeStarted(PointF(2f, 200f), PointF(66f, 200f)))
        assertEquals(1, mounts)
        assertEquals(SidePanelPhase.DRAGGING, panels.panelSnapshot.phase)
        assertEquals(64f / 320f, panels.panelSnapshot.progress, 0.0001f)
        panels.onSwipeUpdate(-64f, 0f) // initial onScroll update is skipped
        assertEquals(64f / 320f, panels.panelSnapshot.progress, 0.0001f)
        panels.onSwipeUpdate(-32f, 0f)
        assertEquals(96f / 320f, panels.panelSnapshot.progress, 0.0001f)
        assertEquals(View.VISIBLE, panels.host.visibility)
        assertTrue(panels.leftIsEngaged())
        panels.dispose()
        activity.finish()
    }

    @Test fun openPanelMayBeDraggedClosedAndOtherSideMayNotClaim() {
        val (activity, root) = fixture()
        val panels = SidePanelInfrastructure.attach(root, 36) { true }
        panels.host.leftContainer.addView(View(activity))
        assertTrue(panels.activate(SidePanelSide.LEFT, 310))
        assertTrue(panels.onSwipeStarted(PointF(3f, 230f), PointF(250f, 230f)))
        panels.onSwipeUpdate(-247f, 0f)
        assertEquals(247f / 310f, panels.panelSnapshot.progress, 0.001f)
        panels.onSwipeFinished(0f, 0f)
        // A settle may be interrupted from the current position.
        assertTrue(panels.onSwipeStarted(PointF(150f, 230f), PointF(110f, 230f)))
        assertEquals(SidePanelPhase.DRAGGING, panels.panelSnapshot.phase)
        assertFalse(panels.onSwipeStarted(PointF(419f, 230f), PointF(330f, 230f)))
        assertTrue(panels.closeOnBack())
        assertEquals(false, panels.panelSnapshot.targetOpen)
        panels.dispose()
        activity.finish()
    }

    @Test fun failedMountKeepsLegacyFallbackAvailableAndNothingIsShown() {
        val (activity, root) = fixture()
        val panels = SidePanelInfrastructure.attach(root, 36) { true }
        var called = 0
        panels.prepareLeftOnGesture = { called++; false }
        assertFalse(panels.onSwipeStarted(PointF(2f, 200f), PointF(80f, 200f)))
        assertEquals(1, called)
        assertFalse(panels.leftIsEngaged())
        assertEquals(View.GONE, panels.host.visibility)
        panels.dispose()
        activity.finish()
    }

    @Test fun cancelledGestureSettlesClosedAndBackIsConsumedWhileActive() {
        val (activity, root) = fixture()
        val panels = SidePanelInfrastructure.attach(root, 36) { true }
        panels.host.leftContainer.addView(View(activity))
        assertTrue(panels.activate(SidePanelSide.LEFT, 300))
        assertTrue(panels.onSwipeStarted(PointF(2f, 130f), PointF(110f, 130f)))
        panels.onSwipeCancelled()
        assertEquals(false, panels.panelSnapshot.targetOpen)
        assertTrue(panels.closeOnBack())
        panels.resetForFullscreen()
        assertEquals(SidePanelPhase.CLOSED, panels.panelSnapshot.phase)
        assertEquals(View.GONE, panels.host.visibility)
        panels.dispose()
        activity.finish()
    }

    @Test fun longPressDragAndResizeHavePriorityOverSwipeAndReleaseTheGate() {
        val (activity, root) = fixture()
        val panels = SidePanelInfrastructure.attach(root, 36) { true }
        panels.host.leftContainer.addView(View(activity))
        assertTrue(panels.activate(SidePanelSide.LEFT, 300))
        panels.setChildInteraction(true)
        assertFalse(panels.onSwipeStarted(PointF(2f, 200f), PointF(75f, 200f)))
        assertFalse(panels.onSwipeStarted(PointF(419f, 200f), PointF(330f, 200f)))
        assertEquals(SidePanelPhase.CLOSED, panels.panelSnapshot.phase)
        panels.setChildInteraction(false)
        assertTrue(panels.onSwipeStarted(PointF(2f, 200f), PointF(75f, 200f)))
        assertEquals(SidePanelPhase.DRAGGING, panels.panelSnapshot.phase)
        panels.resetForFullscreen()
        panels.dispose()
        activity.finish()
    }

    @Test fun drawerOpeningDoesNotTakeOverTheOtherEdge() {
        val (activity, root) = fixture()
        val panels = SidePanelInfrastructure.attach(root, 36) { true }
        panels.host.leftContainer.addView(View(activity))
        assertTrue(panels.activate(SidePanelSide.LEFT, 280))
        assertFalse(panels.onSwipeStarted(PointF(418f, 180f), PointF(340f, 180f)))
        assertEquals(SidePanelPhase.CLOSED, panels.panelSnapshot.phase)
        assertFalse(panels.leftIsEngaged().not())
        panels.dispose()
        activity.finish()
    }

}
