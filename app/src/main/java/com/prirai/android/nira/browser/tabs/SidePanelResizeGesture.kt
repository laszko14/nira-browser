package com.prirai.android.nira.browser.tabs

import android.view.Gravity
import android.view.MotionEvent

/** The drawer is on the physical LEFT; its resize edge is always the physical RIGHT. */
internal object SidePanelResizeGeometry {
    const val EDGE_DP = 20
    const val GRIP_GRAVITY = Gravity.RIGHT
    // Compose start/end are relative to text direction, unlike the physical drawer edge.
    fun contentStartPaddingDp(rtl: Boolean): Int = if (rtl) EDGE_DP else 0
    fun contentEndPaddingDp(rtl: Boolean): Int = if (rtl) 0 else EDGE_DP
}

/**
 * One pointer owns a resize from DOWN through UP. A second finger, lost pointer,
 * or Android CANCEL rolls back the draft width and never persists it. Keep this
 * independent of the fragment so the real MotionEvent sequence is testable.
 */
internal class SidePanelResizeGesture(
    private val initialWidth: () -> Int,
    private val allowedWidths: () -> IntRange,
    private val onStart: () -> Unit,
    private val onResize: (Int) -> Unit,
    private val onFinish: (committed: Boolean, initial: Int, final: Int) -> Unit,
) {
    private var ownerId = MotionEvent.INVALID_POINTER_ID
    private var downRawX = 0f
    private var startWidth = 0
    private var draftWidth = 0

    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancel()
                if (event.pointerCount == 1) {
                    ownerId = event.getPointerId(0)
                    downRawX = rawX(event, 0)
                    startWidth = initialWidth()
                    draftWidth = startWidth
                    onStart()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (ownerId != MotionEvent.INVALID_POINTER_ID) {
                    val index = event.findPointerIndex(ownerId)
                    if (event.pointerCount != 1 || index < 0) cancel()
                    else updateWidth(rawX(event, index))
                }
            }
            MotionEvent.ACTION_UP -> {
                if (ownerId != MotionEvent.INVALID_POINTER_ID) {
                    val index = event.findPointerIndex(ownerId)
                    if (event.pointerCount != 1 || index < 0 ||
                        event.getPointerId(event.actionIndex) != ownerId
                    ) {
                        cancel()
                    } else {
                        updateWidth(rawX(event, index))
                        val original = startWidth
                        val final = draftWidth
                        reset()
                        onFinish(final != original, original, final)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> cancel()
        }
        // Consume the rest of an aborted multi-touch stream; do not hand it to rows or a drawer.
        return true
    }

    private fun updateWidth(x: Float) {
        val range = allowedWidths()
        val desired = (startWidth + x - downRawX).toInt().coerceIn(range.first, range.last)
        if (desired != draftWidth) {
            draftWidth = desired
            onResize(desired)
        }
    }

    private fun cancel() {
        if (ownerId == MotionEvent.INVALID_POINTER_ID) return
        val original = startWidth
        if (draftWidth != original) onResize(original)
        reset()
        onFinish(false, original, original)
    }

    private fun reset() {
        ownerId = MotionEvent.INVALID_POINTER_ID
    }

    private fun rawX(event: MotionEvent, index: Int): Float =
        // MotionEvent.rawX refers to pointer index 0 on older Android versions.
        // The local-to-screen offset is common to all pointers in the same View.
        event.getX(index) + event.rawX - event.x
}
