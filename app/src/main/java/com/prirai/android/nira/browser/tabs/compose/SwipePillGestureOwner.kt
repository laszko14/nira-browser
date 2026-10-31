package com.prirai.android.nira.browser.tabs.compose

import kotlin.math.abs

/** Exclusive gesture ownership for a tab pill; independent of Compose frame timing. */
internal enum class SwipePillOutcome { NONE, CLOSE, MENU }

internal class SwipePillGestureOwner(
    private val touchSlop: Float,
    private val threshold: Float,
    private val maxOffset: Float
) {
    private var pointerId: Long? = null
    private var horizontalTravel = 0f
    private var verticalTravel = 0f
    private var claimed = false

    var offsetY: Float = 0f
        private set

    val isActive: Boolean get() = pointerId != null
    val isClaimed: Boolean get() = claimed

    fun begin(id: Long) {
        cancel()
        pointerId = id
    }

    /** True only after vertical touch-slop wins and the pointer remains owned. */
    fun move(
        pointerId: Long,
        deltaX: Float,
        deltaY: Float,
        consumed: Boolean = false,
        otherPointerPressed: Boolean = false,
        dragInProgress: Boolean = false
    ): Boolean {
        if (this.pointerId != pointerId || consumed || otherPointerPressed || dragInProgress) {
            cancel()
            return false
        }
        if (!claimed) {
            horizontalTravel += abs(deltaX)
            verticalTravel += abs(deltaY)
            if (horizontalTravel > touchSlop && horizontalTravel >= verticalTravel) {
                cancel()
                return false
            }
            if (verticalTravel > touchSlop && verticalTravel > horizontalTravel) {
                claimed = true
            }
        }
        if (claimed) offsetY = (offsetY + deltaY).coerceIn(-maxOffset, maxOffset)
        return claimed
    }

    fun release(
        pointerId: Long,
        consumed: Boolean = false,
        otherPointerPressed: Boolean = false,
        dragInProgress: Boolean = false
    ): SwipePillOutcome {
        val result = if (this.pointerId == pointerId && claimed &&
            !consumed && !otherPointerPressed && !dragInProgress
        ) {
            when {
                offsetY < -threshold -> SwipePillOutcome.CLOSE
                offsetY > threshold -> SwipePillOutcome.MENU
                else -> SwipePillOutcome.NONE
            }
        } else {
            SwipePillOutcome.NONE
        }
        cancel()
        return result
    }

    /** Invalidating ownership is irreversible until a fresh DOWN. */
    fun cancel() {
        pointerId = null
        claimed = false
        horizontalTravel = 0f
        verticalTravel = 0f
        offsetY = 0f
    }
}
