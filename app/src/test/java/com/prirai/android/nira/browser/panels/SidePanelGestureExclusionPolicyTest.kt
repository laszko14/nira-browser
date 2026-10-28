package com.prirai.android.nira.browser.panels

import android.app.Activity
import android.app.Application
import android.graphics.Rect
import android.view.View
import com.prirai.android.nira.browser.SwipeGestureLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android's Back gesture has priority outside the very small exclusion zones. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class SidePanelGestureExclusionPolicyTest {
    private fun rects(
        width: Int = 1080, height: Int = 2400, density: Float = 3f,
        left: Int = 60, right: Int = 60,
        top: Int = 72, enabled: Boolean = true, ime: Boolean = false
    ) = SidePanelGestureExclusionPolicy.regions(
        width, height, density, 36, left, right, top, enabled, ime
    )

    @Test fun twoNarrowTopEdgeRegionsStayBelowSystem200dpBudget() {
        val zones = rects()
        assertEquals(2, zones.size)
        assertEquals(0, zones[0].left)
        assertEquals(1080, zones[1].right)
        zones.forEach { r ->
            assertTrue(r.height() in 1..480) // <=160dp at 3px/dp
            assertTrue(r.width() in 1..72) // <=24dp at 3px/dp
            assertTrue(r.top > 72)
            assertTrue(r.bottom <= 800) // within upper third
            assertFalse(r.contains(0, 1600)) // lower back area stays available
        }
        assertTrue(zones[0].right < zones[1].left)
    }

    @Test fun threeButtonNavigationAndImeDoNotGetEdgeExclusions() {
        assertTrue(rects(left = 0, right = 0).isEmpty())
        assertTrue(rects(ime = true).isEmpty())
        assertTrue(rects(enabled = false).isEmpty())
    }

    @Test fun onlyTheEdgeWithSystemBackInsetIsExcluded() {
        val zones = rects(left = 0, right = 72)
        assertEquals(1, zones.size)
        assertEquals(1080, zones[0].right)
        assertTrue(rects(left = 72, right = 0).single().left == 0)
    }

    @Test fun dimensionsAndInsetsCannotCreateOffscreenRects() {
        assertTrue(rects(width = 0).isEmpty())
        assertTrue(rects(height = 0).isEmpty())
        assertTrue(rects(density = Float.NaN).isEmpty())
        assertTrue(rects(density = 0f).isEmpty())
        assertTrue(rects(height = 150, top = 130).isEmpty())
        val zones = rects(width = 100, height = 900, density = 1f, top = 10)
        assertTrue(zones.all { it.width() <= 24 && it.bottom <= 300 })
    }

    @Test fun fullscreenResetsTheDrawerWithoutInterceptingBackHistory() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = SwipeGestureLayout(activity)
        activity.setContentView(root)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(840, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 420, 840)
        val panels = SidePanelInfrastructure.attach(root, 36) { true }
        panels.host.leftContainer.addView(View(activity))
        assertTrue(panels.activate(SidePanelSide.LEFT, 300))
        assertTrue(panels.onSwipeStarted(
            android.graphics.PointF(2f, 180f), android.graphics.PointF(140f, 180f)
        ))
        assertEquals(SidePanelPhase.DRAGGING, panels.panelSnapshot.phase)
        root.isSwipeEnabled = false
        panels.onFullscreenChanged(true)
        assertEquals(SidePanelPhase.CLOSED, panels.panelSnapshot.phase)
        assertEquals(View.GONE, panels.host.visibility)
        assertFalse(panels.closeOnBack()) // system Back can now exit fullscreen
        assertFalse(panels.onSwipeStarted(
            android.graphics.PointF(2f, 180f), android.graphics.PointF(120f, 180f)
        ))
        panels.dispose()
        activity.finish()
    }

    @Test fun completedGesturesCanStartAgainAfterLeavingFullscreen() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = SwipeGestureLayout(activity)
        activity.setContentView(root)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(840, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 420, 840)
        val panels = SidePanelInfrastructure.attach(root, 36) { true }
        panels.host.rightContainer.addView(View(activity))
        assertTrue(panels.activate(SidePanelSide.RIGHT, 280))
        panels.onFullscreenChanged(true)
        root.isSwipeEnabled = true
        panels.onFullscreenChanged(false)
        assertFalse(panels.closeOnBack())
        assertFalse(panels.onSwipeStarted(
            android.graphics.PointF(2f, 180f), android.graphics.PointF(100f, 180f)
        )) // left content is absent
        assertTrue(panels.onSwipeStarted(
            android.graphics.PointF(418f, 180f), android.graphics.PointF(330f, 180f)
        ))
        panels.dispose()
        activity.finish()
    }
}
