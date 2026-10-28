package com.prirai.android.nira.browser.panels

import android.app.Activity
import android.app.Application
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real Android View hierarchy assertions: visibility, touch surfaces, sizing,
 * physical left/right edges, and back/outside-click contracts. The actual
 * BrowserActivity fragments need 7C/instrumentation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class SidePanelHostFunctionalTest {
    private data class Fixture(val activity: Activity, val root: FrameLayout, val host: SidePanelHost)
    private fun fixture(): Fixture {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val host = SidePanelHost(activity)
        root.addView(host, FrameLayout.LayoutParams(-1, -1))
        activity.setContentView(root)
        root.measure(View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(840, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 420, 840)
        return Fixture(activity, root, host)
    }

    @Test fun overlayDoesNotShowBeforeFirstQualifiedGesture() {
        val f = fixture()
        assertEquals(View.GONE, f.host.visibility)
        assertEquals(View.GONE, f.host.leftContainer.visibility)
        assertEquals(View.GONE, f.host.rightContainer.visibility)
        assertEquals(View.GONE, f.host.getChildAt(0).visibility)
        f.activity.finish()
    }

    @Test fun openingLeftMovesItsContentAndNeverShowsRightContent() {
        val f = fixture()
        f.host.setPanelWidth(SidePanelSide.LEFT, 300)
        f.host.setPanelWidth(SidePanelSide.RIGHT, 260)
        f.host.leftContainer.addView(View(f.activity))
        f.host.rightContainer.addView(View(f.activity))
        f.host.render(SidePanelSnapshot(SidePanelSide.LEFT, SidePanelPhase.DRAGGING, 0.25f))
        assertEquals(View.VISIBLE, f.host.visibility)
        assertEquals(View.VISIBLE, f.host.leftContainer.visibility)
        assertEquals(View.GONE, f.host.rightContainer.visibility)
        assertEquals(-225f, f.host.leftContainer.translationX, 0.001f)
        assertEquals(0.55f * 0.25f, f.host.getChildAt(0).alpha, 0.0001f)
        f.host.render(SidePanelSnapshot(SidePanelSide.LEFT, SidePanelPhase.OPEN, 1f))
        assertEquals(0f, f.host.leftContainer.translationX, 0f)
        f.activity.finish()
    }

    @Test fun openingRightMovesItsContentAndNeverShowsLeftContent() {
        val f = fixture()
        f.host.setPanelWidth(SidePanelSide.LEFT, 300)
        f.host.setPanelWidth(SidePanelSide.RIGHT, 240)
        f.host.render(SidePanelSnapshot(SidePanelSide.RIGHT, SidePanelPhase.DRAGGING, 0.4f))
        assertEquals(144f, f.host.rightContainer.translationX, 0.001f)
        assertEquals(View.GONE, f.host.leftContainer.visibility)
        assertEquals(View.VISIBLE, f.host.rightContainer.visibility)
        f.host.render(SidePanelSnapshot(SidePanelSide.RIGHT, SidePanelPhase.OPEN, 1f))
        assertEquals(0f, f.host.rightContainer.translationX, 0f)
        f.activity.finish()
    }

    @Test fun outsideTapIsDistinctFromPanelContentAndOnlyCallsActiveCallback() {
        val f = fixture()
        var outside = 0
        f.host.onOutsideTap = { outside++ }
        val scrim = f.host.getChildAt(0)
        f.host.setPanelWidth(SidePanelSide.LEFT, 300)
        f.host.render(SidePanelSnapshot(SidePanelSide.LEFT, SidePanelPhase.OPEN, 1f))
        assertTrue(scrim.performClick())
        assertEquals(1, outside)
        val child = View(f.activity)
        var clicked = 0
        child.setOnClickListener { clicked++ }
        f.host.leftContainer.addView(child)
        assertTrue(child.performClick())
        assertEquals(1, clicked)
        assertEquals(1, outside)
        f.activity.finish()
    }

    @Test fun closingEitherPanelLeavesNoOverlayBlockingTheWebPage() {
        for (side in SidePanelSide.entries) {
            val f = fixture()
            f.host.setPanelWidth(side, 280)
            f.host.render(SidePanelSnapshot(side, SidePanelPhase.OPEN, 1f))
            f.host.render(SidePanelSnapshot(null, SidePanelPhase.CLOSED, 0f))
            assertEquals(View.GONE, f.host.visibility)
            assertEquals(View.GONE, f.host.getChildAt(0).visibility)
            assertEquals(View.GONE, f.host.leftContainer.visibility)
            assertEquals(View.GONE, f.host.rightContainer.visibility)
            f.activity.finish()
        }
    }

    @Test fun manualWidthChangeUsesNewPixelsWithoutLosingCurrentProgress() {
        val f = fixture()
        f.host.setPanelWidth(SidePanelSide.LEFT, 300)
        f.host.render(SidePanelSnapshot(SidePanelSide.LEFT, SidePanelPhase.DRAGGING, 0.5f))
        assertEquals(-150f, f.host.leftContainer.translationX, 0f)
        f.host.setPanelWidth(SidePanelSide.LEFT, 360)
        f.host.render(SidePanelSnapshot(SidePanelSide.LEFT, SidePanelPhase.DRAGGING, 0.5f))
        assertEquals(360, f.host.leftContainer.layoutParams.width)
        assertEquals(-180f, f.host.leftContainer.translationX, 0f)
        f.activity.finish()
    }

    @Test fun physicalLeftAndRightDoNotSwapInRtlLayoutDirection() {
        val f = fixture()
        f.host.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val left = f.host.leftContainer.layoutParams as FrameLayout.LayoutParams
        val right = f.host.rightContainer.layoutParams as FrameLayout.LayoutParams
        assertEquals(Gravity.LEFT, left.gravity)
        assertEquals(Gravity.RIGHT, right.gravity)
        f.activity.finish()
    }
}
