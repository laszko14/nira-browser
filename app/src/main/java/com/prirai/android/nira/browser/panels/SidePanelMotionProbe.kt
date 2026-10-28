package com.prirai.android.nira.browser.panels

import android.view.View
import android.view.ViewTreeObserver
import com.prirai.android.nira.browser.SwipeGestureLayout
import com.prirai.android.nira.diagnostics.testlab.CompletionProof
import com.prirai.android.nira.diagnostics.testlab.LabOperation
import com.prirai.android.nira.diagnostics.testlab.OperationOutcome
import com.prirai.android.nira.diagnostics.testlab.OperationToken
import com.prirai.android.nira.diagnostics.testlab.PanelSide
import com.prirai.android.nira.diagnostics.testlab.TestLab

/**
 * TestLab probe for EMBEDDED drawers, distinct from the Window-based legacy
 * BottomSheet probe. Does not own animation or draw any pixels.
 *
 * Each gesture opening has two independent measurements:
 *   OPEN: ACTION_DOWN -> first drawable pre-draw (not animator completion).
 *   SETTLE: start of settle -> terminal OPEN/CLOSED (ANIMATOR_END).
 * CLOSE: start of close settle -> terminal CLOSED (ANIMATOR_END).
 *
 * No URL, folder names, tab IDs, content snapshots, or per-frame records.
 */
internal class SidePanelMotionProbe(
    private val host: SidePanelHost,
    private val gestures: SwipeGestureLayout
) {
    private var previous = SidePanelSnapshot(null, SidePanelPhase.CLOSED, 0f)
    private var opening: OperationToken? = null
    private var settling: OperationToken? = null
    private var closing: OperationToken? = null
    private var listener: ViewTreeObserver.OnPreDrawListener? = null
    private var watching: ViewTreeObserver? = null
    private var activeSide: SidePanelSide? = null

    fun onSnapshot(current: SidePanelSnapshot) {
        val before = previous
        if (before.phase == current.phase && before.side == current.side &&
            before.targetOpen == current.targetOpen) return // no allocations or TestLab call per frame
        previous = current

        // Taking over an in-flight settle does not falsely report completion.
        if (before.phase == SidePanelPhase.SETTLING && current.phase == SidePanelPhase.DRAGGING) {
            interruptSettle()
        }

        // OPEN starts at the gesture's original ACTION_DOWN when TestLab captured
        // one; direct commands fall back to an operation started at activation.
        if (before.phase == SidePanelPhase.CLOSED && current.side != null &&
            (current.phase == SidePanelPhase.DRAGGING ||
                (current.phase == SidePanelPhase.SETTLING && current.targetOpen == true))) {
            beginOpen(current.side)
        }

        if (current.phase == SidePanelPhase.SETTLING &&
            (before.phase != SidePanelPhase.SETTLING || before.side != current.side ||
                before.targetOpen != current.targetOpen)) {
            beginSettle(current.side ?: return, current.targetOpen == true)
        }

        if (current.phase == SidePanelPhase.OPEN || current.phase == SidePanelPhase.CLOSED) {
            if (before.phase == SidePanelPhase.SETTLING) {
                TestLab.end(settling, proof = CompletionProof.ANIMATOR_END)
                settling = null
                if (current.phase == SidePanelPhase.CLOSED) {
                    TestLab.end(closing, proof = CompletionProof.ANIMATOR_END)
                    closing = null
                }
            }
            // If no first visible pre-draw occurred before a close, do not claim
            // an opening latency/proof merely because the animation finished.
            if (current.phase == SidePanelPhase.CLOSED) endOpen(OperationOutcome.CANCELLED)
        }
    }

    private fun beginOpen(side: SidePanelSide) {
        endOpen(OperationOutcome.INTERRUPTED)
        activeSide = side
        val labSide = side.labSide()
        opening = TestLab.panelGesture(labSide, gestures.testLabGestureOrigin)
            ?: TestLab.operation(labSide.openOperation)
        TestLab.accept(opening)
        if (opening == null) return // no TestLab session; no listener or observer work
        val predraw = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val state = previous
                if (state.side == side && state.phase != SidePanelPhase.CLOSED &&
                    state.progress > 0f && host.isShown &&
                    host.width > 0 && host.height > 0) {
                    val panel = if (side == SidePanelSide.LEFT) host.leftContainer else host.rightContainer
                    if (panel.isShown && panel.width > 0 && panel.height > 0) {
                        TestLab.end(opening, proof = CompletionProof.FIRST_PREDRAW)
                        opening = null
                        detachPreDraw()
                    }
                }
                return true
            }
        }
        watching = host.viewTreeObserver
        listener = predraw
        watching?.addOnPreDrawListener(predraw)
    }

    private fun beginSettle(side: SidePanelSide, targetOpen: Boolean) {
        interruptSettle()
        settling = TestLab.operation(
            if (side == SidePanelSide.LEFT) LabOperation.LEFT_PANEL_SETTLE
            else LabOperation.RIGHT_PANEL_SETTLE
        )
        if (!targetOpen) closing = TestLab.panelClosing(side.labSide(), true)
    }

    private fun interruptSettle() {
        TestLab.end(settling, OperationOutcome.INTERRUPTED)
        TestLab.end(closing, OperationOutcome.INTERRUPTED)
        settling = null
        closing = null
    }

    private fun endOpen(outcome: OperationOutcome) {
        TestLab.end(opening, outcome)
        opening = null
        activeSide = null
        detachPreDraw()
    }

    private fun detachPreDraw() {
        val watcher = watching
        val old = listener
        if (watcher != null && old != null && watcher.isAlive) {
            watcher.removeOnPreDrawListener(old)
        }
        watching = null
        listener = null
    }

    /** Reset, configuration changes, fullscreen and view destruction are not completions. */
    fun abort() {
        interruptSettle()
        endOpen(OperationOutcome.INTERRUPTED)
        previous = SidePanelSnapshot(null, SidePanelPhase.CLOSED, 0f)
    }

    private fun SidePanelSide.labSide(): PanelSide =
        if (this == SidePanelSide.LEFT) PanelSide.LEFT else PanelSide.RIGHT
}
