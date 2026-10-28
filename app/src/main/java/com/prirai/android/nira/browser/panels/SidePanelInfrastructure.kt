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
    ) { renderPanel(it) }

    private val enabled = mutableSetOf<SidePanelSide>()
    private var swipeOwner: SidePanelSide? = null
    private var totalX = 0f
    private var skipFirstUpdate = false
    private var disposed = false
    private var leftFragmentManager: FragmentManager? = null
    private var leftWasVisible = false
    private var childInteractionInProgress = false

    /** Native resize and Compose DnD temporarily have priority over panel swipes. */
    fun setChildInteraction(inProgress: Boolean) {
        if (!disposed) childInteractionInProgress = inProgress
    }

    /**
     * Called only after a qualified LEFT-edge swipe, before claiming that swipe.
     * The browser installs this to mount content and activate LEFT on demand.
     */
    var prepareLeftOnGesture: (() -> Boolean)? = null
    internal val panelSnapshot: SidePanelSnapshot get() = controller.snapshot

    private fun renderPanel(snapshot: SidePanelSnapshot) {
        host.render(snapshot)
        if (snapshot.side == SidePanelSide.LEFT) leftWasVisible = true
        if (snapshot.phase == SidePanelPhase.CLOSED && leftWasVisible) {
            leftWasVisible = false
            // Do not remove a Fragment inside Animator's onEnd or a touch dispatch.
            gestures.post {
                if (!disposed && controller.snapshot.phase == SidePanelPhase.CLOSED) {
                    leftFragmentManager?.let { manager ->
                        if (!manager.isStateSaved && !manager.isDestroyed) unmountLeftContent(manager)
                    }
                }
            }
        }
    }

    /** Prevents the legacy listener from opening a second dialog over the embedded one. */
    fun leftIsEngaged(): Boolean = !disposed &&
        (host.leftContainer.childCount > 0 || controller.snapshot.side == SidePanelSide.LEFT)

    /**
     * On-demand content mount only. Do NOT call from browser initialization:
     * doing so would instantiate a second tab list alongside the legacy sheet.
     * Stage 3B invokes this before activating the left gesture.
     */
    fun mountLeftContent(fragmentManager: FragmentManager): Boolean {
        if (disposed || fragmentManager.isStateSaved || fragmentManager.isDestroyed) return false
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
            onDismissRequested = { requestLeftClose() },
            onChildInteractionChanged = { busy -> setChildInteraction(busy) }
        )
        host.setPanelWidth(SidePanelSide.LEFT, tabs.embeddedSidePanelWidth())
        if (found == null) {
            fragmentManager.beginTransaction()
                .add(host.leftContainer.id, tabs, LEFT_CONTENT_TAG)
                .commitNow()
        }
        leftFragmentManager = fragmentManager
        // Mounting content alone does not activate the swipe handler.
        return tabs.view?.parent === host.leftContainer
    }

    /** Called during parent view teardown, when synchronous fragment transactions are unsafe. */
    fun releaseLeftContentBinding(fragmentManager: FragmentManager) {
        childInteractionInProgress = false
        leftFragmentManager = null
        leftWasVisible = false
        deactivate(SidePanelSide.LEFT)
        (fragmentManager.findFragmentByTag(LEFT_CONTENT_TAG) as? TabsBottomSheetFragment)
            ?.clearEmbeddedSidePanelCallbacks()
    }

    /** Explicit user close, outside FragmentManager's lifecycle dispatch. */
    fun unmountLeftContent(fragmentManager: FragmentManager): Boolean {
        val tabs = fragmentManager.findFragmentByTag(LEFT_CONTENT_TAG)
            as? TabsBottomSheetFragment ?: return false
        if (fragmentManager.isStateSaved || fragmentManager.isDestroyed) return false
        if (leftFragmentManager === fragmentManager) leftFragmentManager = null
        leftWasVisible = false
        deactivate(SidePanelSide.LEFT)
        childInteractionInProgress = false
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
        if (disposed || childInteractionInProgress || !mayHandle() || !gestures.isSwipeEnabled) return false

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

        val state = controller.snapshot
        val side = if (state.phase == SidePanelPhase.CLOSED) {
            when {
                dx > 0f && localX in 0f..activeEdge -> SidePanelSide.LEFT
                dx < 0f && localX in (viewWidth - activeEdge)..viewWidth -> SidePanelSide.RIGHT
                else -> return false
            }
        } else {
            // Swipe LEFT within the already open/settling left drawer to close
            // or reverse its animation. Right remains strictly disabled until 4B.
            if (state.side != SidePanelSide.LEFT || dx >= 0f ||
                localX < 0f || localX > host.leftContainer.layoutParams.width) return false
            SidePanelSide.LEFT
        }
        if (side == SidePanelSide.LEFT && side !in enabled) {
            if (prepareLeftOnGesture?.invoke() != true) return false
        }
        if (side !in enabled || container(side).childCount == 0) return false
        val width = container(side).layoutParams.width
        if (width <= 0 || !controller.beginDrag(side, width)) return false
        swipeOwner = side
        // The first GestureDetector onScroll dispatch also invokes onSwipeUpdate
        // for this same move: account for the initial travel exactly ONCE.
        totalX = dx
        skipFirstUpdate = true
        controller.dragBy(totalX)
        return true
    }

    override fun onSwipeUpdate(distanceX: Float, distanceY: Float) {
        if (disposed || swipeOwner == null) return
        if (skipFirstUpdate) {
            skipFirstUpdate = false
            return
        }
        // GestureDetector reports previousX - currentX, whereas the controller takes
        // total displacement from the beginning of this accepted drag.
        if (!distanceX.isFinite()) return
        totalX -= distanceX
        controller.dragBy(totalX)
    }

    override fun onSwipeFinished(velocityX: Float, velocityY: Float) {
        if (disposed || swipeOwner == null) return
        swipeOwner = null
        skipFirstUpdate = false
        controller.release(velocityX)
    }

    override fun onSwipeCancelled() {
        if (disposed || swipeOwner == null) return
        swipeOwner = null
        skipFirstUpdate = false
        controller.cancelDrag()
    }

    /** Request a visual close; the child Fragment is removed after CLOSED. */
    fun requestLeftClose(): Boolean {
        if (disposed) return false
        val state = controller.snapshot
        if (state.side == SidePanelSide.LEFT) return closeOnBack()
        val manager = leftFragmentManager ?: return false
        return unmountLeftContent(manager)
    }

    /** Consume Back before browser history when a migrated panel is active. */
    fun closeOnBack(): Boolean {
        if (disposed) return false
        val state = controller.snapshot
        val side = state.side ?: return false
        if (state.phase == SidePanelPhase.DRAGGING) {
            swipeOwner = null
            skipFirstUpdate = false
            // Force a smooth close from the current drag position, not a snap.
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
        skipFirstUpdate = false
        controller.reset()
    }

    /** Must be invoked from BrowserFragment.onDestroyView, not onDestroy. */
    fun dispose() {
        if (disposed) return
        gestures.removeGestureListener(this)
        swipeOwner = null
        skipFirstUpdate = false
        enabled.clear()
        leftFragmentManager = null
        childInteractionInProgress = false
        prepareLeftOnGesture = null
        leftWasVisible = false
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
