package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.prirai.android.nira.components.toolbar.unified.UnifiedToolbar

/**
 * CoordinatorLayout behavior that:
 *   1. wires the active EngineView into [UnifiedToolbar] /
 *      [ModernToolbarSystem] on layout,
 *   2. enters the toolbar's minimal state when the user scrolls the
 *      page **downwards** past a threshold.
 *
 * # Scroll contract (post-v0.5.14)
 * Only the enter path is scroll-driven. Exiting minimal state is a
 * deliberate affordance: the user taps the chevron button on the
 * address bar (see
 * `MinimalStateToggleIntegration`). The reasoning:
 *   - Scroll-down -> minimise is cheap and expected: the user wants
 *     more vertical real estate for reading content and the aux bars
 *     (tab bar + contextual bar) are the natural thing to collapse.
 *   - Scroll-up -> auto-exit was the source of jitter on sites with
 *     internal scroll containers (horizontal carousels, sticky
 *     grids), and on short pages the aux bars oscillated in and out
 *     on tiny gestures. The chevron tap is a precise, user-initiated
 *     restore that avoids this entirely.
 *
 * The 56 dp threshold matches the previous iteration and the heuristic
 * Chrome/Firefox use. A direction change resets the accumulator so a
 * small up-flick in the middle of a long down-scroll does not stall
 * entry; only a sustained scroll in one direction trips the state.
 */
class ModernScrollBehavior(
    context: Context,
    attrs: AttributeSet? = null,
) : CoordinatorLayout.Behavior<View>(context, attrs) {

    private var isScrollingEnabled = true

    private var scrollYAccumulator = 0
    private var topBarsHidden = false
    private val revealThresholdPx: Int =
        (24 * context.resources.displayMetrics.density).toInt()

    // 56 dp - same threshold previously used, matches Chrome/Firefox.
    private val scrollThresholdPx: Int =
        (56 * context.resources.displayMetrics.density).toInt()

    override fun onLayoutChild(
        parent: CoordinatorLayout,
        child: View,
        layoutDirection: Int,
    ): Boolean {
        // Find and connect to the EngineView for UnifiedToolbar or ModernToolbarSystem.
        findEngineView(parent)?.let { engine ->
            when (child) {
                is UnifiedToolbar -> child.setEngineView(engine)
                is ModernToolbarSystem -> child.setEngineView(engine)
            }
        }
        return super.onLayoutChild(parent, child, layoutDirection)
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int,
    ): Boolean {
        // GeckoView does not consistently dispatch nested scroll in portrait,
        // so TOP chrome is driven by GeckoSession.ScrollDelegate instead.
        val isTop = com.prirai.android.nira.preferences.UserPreferences(child.context).toolbarPosition ==
            com.prirai.android.nira.components.toolbar.ToolbarPosition.TOP.ordinal
        return !isTop && isScrollingEnabled && axes and ViewCompat.SCROLL_AXIS_VERTICAL != 0
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int,
    ) {
        if (!isScrollingEnabled || type != ViewCompat.TYPE_TOUCH) return
        accumulateAndMaybeEnterMinimal(child, dy)
    }

    override fun onNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray,
    ) {
        // Top chrome reacts only to actual finger scroll, not overscroll bounce.
        // Unconsumed deltas would cause a reveal/hide loop on short pages.
        if (!isScrollingEnabled || type != ViewCompat.TYPE_TOUCH ||
            child.context.let { com.prirai.android.nira.preferences.UserPreferences(it).toolbarPosition } ==
            com.prirai.android.nira.components.toolbar.ToolbarPosition.TOP.ordinal) return
        accumulateAndMaybeEnterMinimal(child, dyUnconsumed)
    }

    override fun onStopNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        type: Int,
    ) {
        scrollYAccumulator = 0
    }

    /**
     * Accumulate scroll deltas and enter minimal state when the user
     * has scrolled down more than [scrollThresholdPx] in a single
     * sustained gesture. Scroll-up never exits minimal here (chevron
     * button handles exit; see class kdoc).
     */
    private fun accumulateAndMaybeEnterMinimal(child: View, dy: Int) {
        val toolbar = child as? UnifiedToolbar ?: return
        if (com.prirai.android.nira.preferences.UserPreferences(child.context).toolbarPosition ==
            com.prirai.android.nira.components.toolbar.ToolbarPosition.TOP.ordinal) {
            // Hide/show the *whole* top chrome, including the address and tab
            // bars. UnifiedToolbar.collapse/expand animates both bars together
            // and reports offset to BrowserFragment for viewport adjustment.
            when {
                dy > 0 -> {
                    if (scrollYAccumulator < 0) scrollYAccumulator = 0
                    scrollYAccumulator = (scrollYAccumulator + dy).coerceAtMost(scrollThresholdPx)
                    if (!topBarsHidden && scrollYAccumulator >= scrollThresholdPx) {
                        toolbar.collapse()
                        topBarsHidden = true
                        scrollYAccumulator = 0
                    }
                }
                dy < 0 -> {
                    if (scrollYAccumulator > 0) scrollYAccumulator = 0
                    scrollYAccumulator = (scrollYAccumulator + dy).coerceAtLeast(-revealThresholdPx)
                    if (topBarsHidden && scrollYAccumulator <= -revealThresholdPx) {
                        toolbar.expand()
                        topBarsHidden = false
                        scrollYAccumulator = 0
                    }
                }
            }
            return
        }
        when {
            dy > 0 -> {
                // Finger moving upward -> reading further down the page.
                if (scrollYAccumulator < 0) scrollYAccumulator = 0
                scrollYAccumulator += dy
                if (!toolbar.isMinimal() && scrollYAccumulator >= scrollThresholdPx) {
                    toolbar.enterMinimalState()
                    scrollYAccumulator = 0
                }
            }
            dy < 0 -> {
                // Scroll-up: do not auto-exit. Reset the downward
                // accumulator so a down-scroll preceded by an up-flick
                // still needs threshold-worth of sustained downward
                // scroll to minimise.
                scrollYAccumulator = 0
            }
        }
    }

    private fun findEngineView(coordinatorLayout: CoordinatorLayout): mozilla.components.concept.engine.EngineView? {
        for (i in 0 until coordinatorLayout.childCount) {
            val child = coordinatorLayout.getChildAt(i)

            if (child is mozilla.components.concept.engine.EngineView) {
                return child
            }

            if (child is androidx.fragment.app.FragmentContainerView) {
                return searchForEngineView(child)
            }
        }
        return null
    }

    private fun searchForEngineView(view: View): mozilla.components.concept.engine.EngineView? {
        if (view is mozilla.components.concept.engine.EngineView) return view

        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                searchForEngineView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    /** Reinstates scroll-driven enter-minimal. Pair with [disableScrolling]. */
    fun enableScrolling() {
        isScrollingEnabled = true
    }

    /** Suspends scroll-driven enter-minimal (e.g. during PiP / fullscreen / pull-to-refresh). */
    fun disableScrolling() {
        isScrollingEnabled = false
    }
}
