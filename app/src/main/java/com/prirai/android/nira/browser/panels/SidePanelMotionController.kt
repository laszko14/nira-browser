package com.prirai.android.nira.browser.panels

import kotlin.math.abs
import kotlin.math.sqrt

/** Frame callbacks may arrive after cancel; controller rejects them by generation. */
internal interface SidePanelAnimationDriver {
    fun animate(from: Float, to: Float, durationMs: Long, onFrame: (Float) -> Unit, onEnd: () -> Unit)
    fun cancel()
}

/**
 * Stage 5 motion defaults for both sides. These are design targets, NOT 1DM's
 * measured or reverse-engineered timings. Verify on 60/90/120 Hz in stage 7.
 * ValueAnimator uses Android's display VSYNC; never force a 60 Hz frame interval.
 */
internal object SidePanelMotionPolicy {
    const val FLING_THRESHOLD_DP_PER_SECOND = 400f
    private const val OPEN_THRESHOLD = 0.5f
    private const val BASE_DURATION_MS = 224f
    private const val MIN_DURATION_MS = 90L
    private const val MAX_DURATION_MS = 280L
    private const val FLING_TRAVEL_FACTOR = 0.85f

    fun shouldOpen(side: SidePanelSide, progress: Float, velocityXPxPerSecond: Float, density: Float): Boolean {
        require(density.isFinite() && density > 0f)
        val velocityDp = if (velocityXPxPerSecond.isFinite()) velocityXPxPerSecond / density else 0f
        if (abs(velocityDp) >= FLING_THRESHOLD_DP_PER_SECOND) {
            return if (side == SidePanelSide.LEFT) velocityDp > 0f else velocityDp < 0f
        }
        return progress >= OPEN_THRESHOLD
    }

    /** Duration scales with remaining travel; a faster fling never gets a longer tail. */
    fun durationMs(distanceFraction: Float, widthPx: Int, velocityXPxPerSecond: Float, density: Float): Long {
        require(widthPx > 0 && density.isFinite() && density > 0f && distanceFraction.isFinite())
        val distance = distanceFraction.coerceIn(0f, 1f)
        // Sub-pixel tails should settle immediately instead of animating 1 px for 90 ms.
        if (distance * widthPx <= 1f) return 0L
        var duration = BASE_DURATION_MS * sqrt(distance)
        if (velocityXPxPerSecond.isFinite() &&
            abs(velocityXPxPerSecond / density) >= FLING_THRESHOLD_DP_PER_SECOND) {
            val travelTimeMs = 1_000f * distance * widthPx / abs(velocityXPxPerSecond)
            duration = minOf(duration, FLING_TRAVEL_FACTOR * travelTimeMs)
        }
        return duration.toLong().coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
    }

    /** Gentle ease-out cubic: less abrupt initial acceleration than quintic. */
    fun easeOutCubic(fraction: Float): Float {
        if (!fraction.isFinite()) return 0f
        val t = fraction.coerceIn(0f, 1f)
        val remaining = 1f - t
        return 1f - remaining * remaining * remaining
    }
}

/**
 * Shared left/right translation controller. deltaX is TOTAL horizontal travel since beginDrag,
 * not incremental touch distance. No browser listeners or Views are installed in stage 2B.
 * All calls (including driver callbacks) must happen on Android's main thread when integrated.
 */
internal class SidePanelMotionController(
    private val driver: SidePanelAnimationDriver,
    private val density: Float,
    private val onSnapshot: (SidePanelSnapshot) -> Unit
) {
    init { require(density.isFinite() && density > 0f) }

    private val model = SidePanelStateMachine()
    val snapshot: SidePanelSnapshot get() = model.snapshot
    private var generation = 0L
    private var widthPx = 0
    private var dragOrigin = 0f
    private var cancelTargetOpen = false

    fun beginDrag(side: SidePanelSide, measuredWidthPx: Int): Boolean {
        if (measuredWidthPx <= 0) return false
        val before = model.snapshot
        if (!model.startDrag(side)) return false
        invalidateAnimation()
        widthPx = measuredWidthPx
        dragOrigin = before.progress
        cancelTargetOpen = before.phase == SidePanelPhase.OPEN ||
            (before.phase == SidePanelPhase.SETTLING && before.targetOpen == true)
        publish()
        return true
    }

    fun dragBy(totalDeltaX: Float): Boolean {
        val before = model.snapshot
        if (before.phase != SidePanelPhase.DRAGGING || !totalDeltaX.isFinite() || widthPx <= 0) return false
        val direction = if (before.side == SidePanelSide.LEFT) 1f else -1f
        if (!model.dragTo(dragOrigin + totalDeltaX * direction / widthPx)) return false
        publish()
        return true
    }

    fun release(velocityXPxPerSecond: Float): Boolean {
        val before = model.snapshot
        if (before.phase != SidePanelPhase.DRAGGING) return false
        val side = before.side ?: return false
        val velocity = velocityXPxPerSecond.takeIf { it.isFinite() } ?: 0f
        val open = SidePanelMotionPolicy.shouldOpen(side, before.progress, velocity, density)
        return startSettle(side, open, velocity)
    }

    /** ACTION_CANCEL returns to the target from before beginDrag, with no fling decision. */
    fun cancelDrag(): Boolean {
        val side = model.snapshot.takeIf { it.phase == SidePanelPhase.DRAGGING }?.side ?: return false
        return startSettle(side, cancelTargetOpen, 0f)
    }

    /** Programmatic open/close; reject a competing finger or opposite-side owner. */
    fun command(side: SidePanelSide, measuredWidthPx: Int, open: Boolean): Boolean {
        if (measuredWidthPx <= 0) return false
        val before = model.snapshot
        if (before.side != null && before.side != side) return false
        if (before.phase == SidePanelPhase.SETTLING) {
            // startDrag makes the old SETTLING interruptible, preserving its last position.
            if (!model.startDrag(side)) return false
        }
        if (!model.startSettle(side, open)) return false
        widthPx = measuredWidthPx
        animate(side, open, 0f)
        return true
    }

    private fun startSettle(side: SidePanelSide, open: Boolean, velocity: Float): Boolean {
        if (!model.startSettle(side, open)) return false
        animate(side, open, velocity)
        return true
    }

    private fun animate(side: SidePanelSide, open: Boolean, velocity: Float) {
        invalidateAnimation()
        val token = generation
        val from = model.snapshot.progress
        val to = if (open) 1f else 0f
        publish()
        if (abs(to - from) < 0.00001f) {
            model.finishSettle(side)
            publish()
            return
        }
        val duration = SidePanelMotionPolicy.durationMs(abs(to - from), widthPx, velocity, density)
        if (duration == 0L) {
            // At a sub-pixel distance it is cheaper and cleaner to finish now.
            model.finishSettle(side)
            publish()
            return
        }
        driver.animate(from, to, duration,
            onFrame = { value ->
                if (token == generation && model.snapshot.phase == SidePanelPhase.SETTLING &&
                    model.snapshot.side == side && model.settleTo(value)) publish()
            },
            onEnd = {
                if (token == generation && model.finishSettle(side)) publish()
            }
        )
    }

    private fun invalidateAnimation() {
        generation++
        driver.cancel()
    }

    fun reset() {
        invalidateAnimation()
        widthPx = 0
        dragOrigin = 0f
        cancelTargetOpen = false
        model.reset()
        publish()
    }

    private fun publish() { onSnapshot(model.snapshot) }
}
