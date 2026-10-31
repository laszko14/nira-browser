package com.prirai.android.nira.browser.toolbar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import androidx.core.view.isVisible

/**
 * Single animation owner for a toolbar swipe. Invalidating a generation happens
 * BEFORE cancel(), because Android sends onAnimationEnd even after cancellation.
 * Both the browser and home toolbar use this same lifecycle contract.
 */
internal class ToolbarSwipeAnimationOwner(
    private val content: View,
    private val preview: View
) {
    private var generation = 0L
    private var activeAnimator: ValueAnimator? = null

    private val detachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) {
            invalidate()
        }
    }

    init {
        content.addOnAttachStateChangeListener(detachListener)
        preview.addOnAttachStateChangeListener(detachListener)
    }

    fun beginGesture(): Long {
        invalidate()
        return generation
    }

    fun isCurrent(token: Long): Boolean =
        token == generation && content.isAttachedToWindow && preview.isAttachedToWindow

    fun play(token: Long, animator: ValueAnimator, onComplete: () -> Unit) {
        if (!isCurrent(token)) return
        // A new settlement replaces the previous one even within a generation.
        val previous = activeAnimator
        activeAnimator = null
        previous?.cancel()
        animator.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }
            override fun onAnimationEnd(animation: Animator) {
                if (activeAnimator !== animator) return
                activeAnimator = null
                if (!cancelled && isCurrent(token)) onComplete()
            }
        })
        activeAnimator = animator
        animator.start()
    }

    fun fadePreview(token: Long, duration: Long) {
        if (!isCurrent(token)) return
        preview.animate().cancel()
        preview.animate()
            .alpha(0f)
            .setDuration(duration)
            .withEndAction {
                if (isCurrent(token)) preview.isVisible = false
            }
            .start()
    }

    /** Also called on either view's detach (rotation, fragment replacement). */
    fun invalidate() {
        generation += 1L
        val stale = activeAnimator
        activeAnimator = null
        stale?.cancel()
        preview.animate().cancel()
        content.translationX = 0f
        preview.translationX = 0f
        preview.alpha = 1f
        preview.isVisible = false
    }
}
