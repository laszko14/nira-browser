package com.prirai.android.nira.browser.panels

import android.app.Activity
import android.app.Application
import android.graphics.PointF
import android.view.View
import com.prirai.android.nira.browser.SwipeGestureLayout
import com.prirai.android.nira.diagnostics.testlab.LabDomain
import com.prirai.android.nira.diagnostics.testlab.LabOperation
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression contracts when TestLab is inactive. Live-session operation timing
 * and real window pre-draw must be verified in instrumented stage 7B/7C.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class SidePanelMotionProbeTest {
    @Test fun theTwoSettleOperationsArePanelDomainAndDoNotRequireAcceptance() {
        for (operation in listOf(LabOperation.LEFT_PANEL_SETTLE, LabOperation.RIGHT_PANEL_SETTLE)) {
            assertEquals(LabDomain.PANELS, operation.domain)
            assertFalse(operation.needsAccept)
        }
    }

    @Test fun probeIsDormantWithoutTestLabAndMayBeResetRepeatedly() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val gestures = SwipeGestureLayout(activity)
        activity.setContentView(gestures)
        val host = SidePanelHost(activity)
        gestures.addView(host)
        val probe = SidePanelMotionProbe(host, gestures)
        for (side in SidePanelSide.entries) {
            val moving = SidePanelSnapshot(side, SidePanelPhase.DRAGGING, 0.2f)
            val settling = SidePanelSnapshot(side, SidePanelPhase.SETTLING, 0.3f, true)
            probe.onSnapshot(moving)
            probe.onSnapshot(moving.copy(progress = 0.6f))
            probe.onSnapshot(settling)
            probe.abort()
            probe.abort()
            probe.onSnapshot(SidePanelSnapshot(null, SidePanelPhase.CLOSED, 0f))
        }
        activity.finish()
    }

    @Test fun cancelledAndReversedTransitionsNeverThrowWithoutCapture() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val gestures = SwipeGestureLayout(activity)
        val host = SidePanelHost(activity)
        gestures.addView(host)
        val probe = SidePanelMotionProbe(host, gestures)
        probe.onSnapshot(SidePanelSnapshot(SidePanelSide.LEFT, SidePanelPhase.DRAGGING, 0f))
        probe.onSnapshot(SidePanelSnapshot(SidePanelSide.LEFT, SidePanelPhase.SETTLING, 0.45f, true))
        probe.onSnapshot(SidePanelSnapshot(SidePanelSide.LEFT, SidePanelPhase.DRAGGING, 0.45f))
        probe.onSnapshot(SidePanelSnapshot(SidePanelSide.LEFT, SidePanelPhase.SETTLING, 0.45f, false))
        probe.onSnapshot(SidePanelSnapshot(null, SidePanelPhase.CLOSED, 0f))
        probe.abort()
        activity.finish()
    }
}
