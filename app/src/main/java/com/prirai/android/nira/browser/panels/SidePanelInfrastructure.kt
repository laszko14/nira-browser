package com.prirai.android.nira.browser.panels

import android.content.res.Configuration
import android.graphics.PointF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.prirai.android.nira.browser.SwipeGestureLayout
import com.prirai.android.nira.browser.SwipeGestureListener
import com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment
import androidx.fragment.app.FragmentManager
import kotlin.math.abs

/**
 * View-lifecycle owner for the new overlay, gesture listener and motion controller.
 *
 * The gate starts CLOSED for BOTH sides. Stages 3B/4B must explicitly activate each
 * side after its fragment content is mounted; until then legacy dialog gestures win.
 * This class never installs system gesture exclusions or modifies GeckoView.
 */
internal class SidePanelInfrastructure private constructor(
    private val gestures: SwipeGestureLayout,
    private val edgeWidthDp: Int,
    private val mayHandle: () -> Boolean
) : SwipeGestureListener {
    internal val host = SidePanelHost(gestures.context)
    private val controller = SidePanelMotionController(
        ValueAnimatorSidePanelDriver(),
        gestures.resources.displayMetrics.density
    ) { host.render(it) }

    private val enabled = mutableSetOf<SidePanelSide>()
    private var swipeOwner: SidePanelSide? = null
    private var totalX = 0f
    private var disposed = false

    /**
     * On-demand content mount only. Do NOT call from browser initialization:
     * doing so would instantiate a second tab list alongside the legacy sheet.
     * Stage 3B invokes this before activating the left gesture.
     */
    fun mountLeftContent(fragmentManager: FragmentManager): Boolean {
        if (disposed || fragmentManager.isStateSaved) return false
        val found = fragmentManager.findFragmentByTag(LEFT_CONTENT_TAG)
        val tabs = when (found) {
            null -> TabsBottomSheetFragment.newEmbeddedSidePanel()
            is TabsBottomSheetFragment -> found
            else -> return false
        }
        if (found != null && tabs.id != host.leftContainer.id) return false

        // A restored instance may have lost non-parcelable callbacks.
        tabs.bindEmbeddedSidePanel(
            onWidthChanged = { width ->
                if (!disposed) host.setPanelWidth(SidePanelSide.LEFT, width)
            },
            onDismissRequested = { unmountLeftContent(fragmentManager) }
        )
        host.setPanelWidth(SidePanelSide.LEFT, tabs.embeddedSidePanelWidth())
        if (found == null) {
            fragmentManager.beginTransaction()
                .add(host.leftContainer.id, tabs, LEFT_CONTENT_TAG)
                .commitNow()
        }
        // The overlay remains GONE, and no activate(LEFT) is called in stage 3A.
        return tabs.view?.parent === host.leftContainer
    }

    /** Called during parent view teardown, when synchronous fragment transactions are unsafe. */
    fun releaseLeftContentBinding(fragmentManager: FragmentManager) {
        deactivate(SidePanelSide.LEFT)
        (fragmentManager.findFragmentByTag(LEFT_CONTENT_TAG) as? TabsBottomSheetFragment)
            ?.clearEmbeddedSidePanelCallbacks()
    }

    /** Explicit user close, outside FragmentManager's lifecycle dispatch. */
    fun unmountLeftContent(fragmentManager: FragmentManager): Boolean {
        val tabs = fragmentManager.findFragmentByTag(LEFT_CONTENT_TAG)
            as? TabsBottomSheetFragment ?: return false
        if (fragmentManager.isStateSaved) return false
        deactivate(SidePanelSide.LEFT)
        tabs.clearEmbeddedSidePanelCallbacks()
        fragmentManager.beginTransaction().remove(tabs).commitNow()
        return true
    }

    init {
        require(edgeWidthDp > 0)
        host.onOutsideTap = { closeOnBack() }
        gestures.addView(host, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        gestures.addGestureListener(this)
    }

    /**
     * Only usable after a real panel fragment view has been added to the host.
     * Mounting content alone (stage 3A/4A) does not turn on gesture handling.
     */
    fun activate(side: SidePanelSide, widthPx: Int): Boolean {
        if (disposed || widthPx <= 0 || container(side).childCount == 0) return false
        host.setPanelWidth(side, widthPx)
        enabled.add(side)
        return true
    }

    fun deactivate(side: SidePanelSide) {
        if (disposed) return
        enabled.remove(side)
        if (controller.snapshot.side == side) controller.reset()
        if (swipeOwner == side) swipeOwner = null
    }

    override fun onSwipeStarted(start: PointF, next: PointF): Boolean {
        if (disposed || !mayHandle() || !gestures.isSwipeEnabled || enabled.isEmpty() ||
            controller.snapshot.phase != SidePanelPhase.CLOSED) return false

        val density = gestures.resources.displayMetrics.density
        val dx = next.x - start.x
        val dy = next.y - start.y
        if (abs(dx) < 12f * density || abs(dx) < 1.5f * abs(dy)) return false

        val location = IntArray(2)
        gestures.getLocationOnScreen(location)
        val localX = start.x - location[0]
        val viewWidth = gestures.width.toFloat()
        if (viewWidth <= 0f) return false
        val landscape = gestures.resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
        val activeEdge = if (landscape) maxOf(edgeWidthDp * density, 80f * density)
            else edgeWidthDp * density

        val side = when {
            dx > 0f && localX in 0f..activeEdge -> SidePanelSide.LEFT
            dx < 0f && localX in (viewWidth - activeEdge)..viewWidth -> SidePanelSide.RIGHT
            else -> return false
        }
        if (side !in enabled || container(side).childCount == 0) return false
        val width = container(side).layoutParams.width
        if (width <= 0 || !controller.beginDrag(side, width)) return false
        swipeOwner = side
        totalX = 0f
        return true
    }

    override fun onSwipeUpdate(distanceX: Float, distanceY: Float) {
        if (disposed || swipeOwner == null) return
        // GestureDetector reports previousX - currentX, whereas the controller takes
        // total displacement from the beginning of this accepted drag.
        if (!distanceX.isFinite()) return
        totalX -= distanceX
        controller.dragBy(totalX)
    }

    override fun onSwipeFinished(velocityX: Float, velocityY: Float) {
        if (disposed || swipeOwner == null) return
        swipeOwner = null
        controller.release(velocityX)
    }

    override fun onSwipeCancelled() {
        if (disposed || swipeOwner == null) return
        swipeOwner = null
        controller.cancelDrag()
    }

    /** Consume Back before browser history when a migrated panel is active. */
    fun closeOnBack(): Boolean {
        if (disposed) return false
        val state = controller.snapshot
        val side = state.side ?: return false
        if (state.phase == SidePanelPhase.DRAGGING) {
            swipeOwner = null
            controller.reset()
            return true
        }
        if (state.phase == SidePanelPhase.SETTLING && state.targetOpen == false) return true
        val width = container(side).layoutParams.width
        if (width > 0) controller.command(side, width, false) else controller.reset()
        return true
    }

    /** Explicitly hide the overlay before entering video fullscreen. */
    fun resetForFullscreen() {
        if (disposed) return
        swipeOwner = null
        controller.reset()
    }

    /** Must be invoked from BrowserFragment.onDestroyView, not onDestroy. */
    fun dispose() {
        if (disposed) return
        gestures.removeGestureListener(this)
        swipeOwner = null
        enabled.clear()
        controller.reset()
        host.onOutsideTap = null
        (host.parent as? ViewGroup)?.removeView(host)
        disposed = true
    }

    private fun container(side: SidePanelSide): FrameLayout =
        if (side == SidePanelSide.LEFT) host.leftContainer else host.rightContainer

    companion object {
        private const val LEFT_CONTENT_TAG = "NiraEmbeddedSideTabs"

        fun attach(
            gestures: SwipeGestureLayout,
            edgeWidthDp: Int,
            mayHandle: () -> Boolean
        ): SidePanelInfrastructure = SidePanelInfrastructure(gestures, edgeWidthDp, mayHandle)
    }
}
