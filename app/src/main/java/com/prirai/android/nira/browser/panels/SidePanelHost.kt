package com.prirai.android.nira.browser.panels

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

/**
 * Inert until attached by the browser in a later stage. Existing dialog panels are untouched.
 * Hosts ONE active panel over the browser and a single dismiss-on-outside surface.
 */
internal class SidePanelHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val scrim = View(context).apply {
        setBackgroundColor(Color.BLACK)
        alpha = 0f
        isClickable = true
        visibility = View.GONE
        setOnClickListener { onOutsideTap?.invoke() }
    }

    val leftContainer: FrameLayout = FrameLayout(context).apply {
        id = View.generateViewId()
        visibility = View.GONE
    }
    val rightContainer: FrameLayout = FrameLayout(context).apply {
        id = View.generateViewId()
        visibility = View.GONE
    }

    var onOutsideTap: (() -> Unit)? = null

    init {
        visibility = View.GONE
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(leftContainer, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.START))
        addView(rightContainer, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.END))
    }

    fun setPanelWidth(side: SidePanelSide, widthPx: Int) {
        require(widthPx > 0) { "Panel width must be positive" }
        val container = containerFor(side)
        val params = container.layoutParams as LayoutParams
        params.width = widthPx
        container.layoutParams = params
    }

    fun render(state: SidePanelSnapshot) {
        if (state.phase == SidePanelPhase.CLOSED) {
            visibility = View.GONE
            scrim.visibility = View.GONE
            leftContainer.visibility = View.GONE
            rightContainer.visibility = View.GONE
            return
        }
        val side = checkNotNull(state.side) { "Active panel requires a side" }
        check(state.progress.isFinite() && state.progress in 0f..1f) { "Invalid panel progress" }
        val active = containerFor(side)
        val width = active.layoutParams.width
        check(width > 0) { "Set panel width before rendering" }
        visibility = View.VISIBLE
        scrim.visibility = View.VISIBLE
        scrim.alpha = state.progress * MAX_SCRIM_ALPHA
        leftContainer.visibility = if (side == SidePanelSide.LEFT) View.VISIBLE else View.GONE
        rightContainer.visibility = if (side == SidePanelSide.RIGHT) View.VISIBLE else View.GONE
        active.translationX = (1f - state.progress) * width *
            if (side == SidePanelSide.LEFT) -1f else 1f
    }

    private fun containerFor(side: SidePanelSide): FrameLayout =
        if (side == SidePanelSide.LEFT) leftContainer else rightContainer

    companion object {
        private const val MAX_SCRIM_ALPHA = 0.55f
    }
}
