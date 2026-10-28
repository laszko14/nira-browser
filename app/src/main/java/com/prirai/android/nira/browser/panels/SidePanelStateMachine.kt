package com.prirai.android.nira.browser.panels

/** One owner for both edge panels; exactly one side may be active. */
internal enum class SidePanelSide { LEFT, RIGHT }

internal enum class SidePanelPhase { CLOSED, DRAGGING, SETTLING, OPEN }

internal data class SidePanelSnapshot(
    val side: SidePanelSide?,
    val phase: SidePanelPhase,
    val progress: Float,
    val targetOpen: Boolean? = null
)

/**
 * Pure state model: gesture arbitration and timing belong to the motion controller (stage 2B).
 * Progress is the visible fraction [0, 1]; CLOSED has no owner, OPEN is fully visible.
 * All methods are expected to run on the UI thread when connected to views.
 */
internal class SidePanelStateMachine {
    var snapshot = SidePanelSnapshot(null, SidePanelPhase.CLOSED, 0f)
        private set

    fun startDrag(side: SidePanelSide): Boolean {
        val previous = snapshot
        if (previous.phase == SidePanelPhase.DRAGGING ||
            (previous.side != null && previous.side != side)) return false
        snapshot = SidePanelSnapshot(side, SidePanelPhase.DRAGGING, previous.progress)
        return true
    }

    fun dragTo(progress: Float): Boolean {
        if (snapshot.phase != SidePanelPhase.DRAGGING || !progress.isFinite()) return false
        snapshot = snapshot.copy(progress = progress.coerceIn(0f, 1f))
        return true
    }

    /** Can also open a closed panel or close an open one without a gesture. */
    fun startSettle(side: SidePanelSide, open: Boolean): Boolean {
        val previous = snapshot
        if (previous.phase == SidePanelPhase.SETTLING ||
            (previous.side != null && previous.side != side) ||
            (previous.phase == SidePanelPhase.CLOSED && !open) ||
            (previous.phase == SidePanelPhase.OPEN && open)) return false
        snapshot = SidePanelSnapshot(side, SidePanelPhase.SETTLING, previous.progress, open)
        return true
    }

    fun settleTo(progress: Float): Boolean {
        if (snapshot.phase != SidePanelPhase.SETTLING || !progress.isFinite()) return false
        snapshot = snapshot.copy(progress = progress.coerceIn(0f, 1f))
        return true
    }

    /** Returns false for stale/duplicate completion callbacks. */
    fun finishSettle(side: SidePanelSide): Boolean {
        val previous = snapshot
        if (previous.phase != SidePanelPhase.SETTLING || previous.side != side) return false
        snapshot = if (previous.targetOpen == true) {
            SidePanelSnapshot(side, SidePanelPhase.OPEN, 1f)
        } else {
            SidePanelSnapshot(null, SidePanelPhase.CLOSED, 0f)
        }
        return true
    }

    /** Lifecycle teardown; neither side may retain gesture ownership. */
    fun reset() {
        snapshot = SidePanelSnapshot(null, SidePanelPhase.CLOSED, 0f)
    }
}
