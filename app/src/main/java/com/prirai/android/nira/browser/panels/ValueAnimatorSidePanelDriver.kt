package com.prirai.android.nira.browser.panels

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.Interpolator

/** UI-thread-only Android frame driver. Never owns the panel state itself. */
internal class ValueAnimatorSidePanelDriver : SidePanelAnimationDriver {
    private var running: ValueAnimator? = null

    override fun animate(from: Float, to: Float, durationMs: Long, onFrame: (Float) -> Unit, onEnd: () -> Unit) {
        cancel()
        val animator = ValueAnimator.ofFloat(from, to)
        running = animator
        animator.duration = durationMs
        animator.interpolator = Interpolator { SidePanelMotionPolicy.easeOutCubic(it) }
        animator.addUpdateListener {
            if (running === animator) onFrame(it.animatedValue as Float)
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (running === animator) {
                    running = null
                    onFrame(to)
                    onEnd()
                }
            }
        })
        animator.start()
    }

    override fun cancel() {
        val previous = running ?: return
        running = null
        previous.removeAllUpdateListeners()
        previous.removeAllListeners()
        previous.cancel()
    }
}
