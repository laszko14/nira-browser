package com.prirai.android.nira.browser.toolbar

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.PointF
import android.graphics.Rect
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.contains
import androidx.core.graphics.toPoint
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.FakeTab
import com.prirai.android.nira.browser.SwipeGestureListener
import com.prirai.android.nira.browser.tabs.TabLRUManager
import com.prirai.android.nira.components.toolbar.ToolbarPosition
import com.prirai.android.nira.ext.getRectWithScreenLocation
import com.prirai.android.nira.ext.getWindowInsets
import com.prirai.android.nira.ext.isKeyboardVisible
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.browser.tabs.sameSwipeScope
import com.prirai.android.nira.browser.tabs.canFinishSwipe
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.support.ktx.android.view.getRectWithViewLocation
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ToolbarGestureHandler(
    private val activity: Activity,
    private val contentLayout: View,
    private val tabPreview: FakeTab,
    private val toolbarLayout: View,
    private val store: BrowserStore,
    private val selectTabUseCase: TabsUseCases.SelectTabUseCase,
    // Live view-lifecycle gate: never claim a toolbar hidden under a side drawer.
    private val canClaimToolbarSwipe: () -> Boolean = { true }
) : SwipeGestureListener {

    private val lruManager by lazy { 
        TabLRUManager.getInstance(activity) 
    }

    private enum class GestureDirection {
        LEFT_TO_RIGHT, RIGHT_TO_LEFT
    }

    private sealed class Destination {
        data class Tab(val tab: TabSessionState) : Destination()
        data object None : Destination()
    }

    private val windowWidth: Int
        get() = activity.resources.displayMetrics.widthPixels

    private val previewOffset =
        activity.resources.getDimensionPixelSize(R.dimen.browser_fragment_gesture_preview_offset)

    private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
    private val minimumFlingVelocity = ViewConfiguration.get(activity).scaledMinimumFlingVelocity

    private var gestureSource: TabSessionState? = null

    private var gestureDirection = GestureDirection.LEFT_TO_RIGHT
    private val animationOwner = ToolbarSwipeAnimationOwner(contentLayout, tabPreview)
    private var gestureEpoch = 0L
    private var gestureActive = false

    /**
     * A drawer may become engaged after a toolbar stream was accepted (or
     * while its settle is still animating). Stop that owner synchronously.
     */
    private fun toolbarIsUncovered(): Boolean {
        if (canClaimToolbarSwipe()) return true
        if (gestureActive) {
            gestureActive = false
            gestureSource = null
            gestureEpoch = animationOwner.beginGesture()
        }
        return false
    }

    override fun onSwipeStarted(start: PointF, next: PointF): Boolean {
        // Listener ordering is toolbar first; let the panel listener see this
        // MOVE instead of using a hitbox for the view underneath its overlay.
        if (!toolbarIsUncovered()) return false
        val dx = next.x - start.x
        val dy = next.y - start.y
        val direction = if (dx < 0) {
            GestureDirection.RIGHT_TO_LEFT
        } else {
            GestureDirection.LEFT_TO_RIGHT
        }

        return if (
            !activity.window.decorView.isKeyboardVisible() &&
            start.isInToolbar() &&
            abs(dx) > touchSlop &&
            abs(dy) < abs(dx)
        ) {
            gestureEpoch = animationOwner.beginGesture()
            gestureActive = true
            gestureDirection = direction
            gestureSource = store.state.selectedTab
            preparePreview(getDestination())
            true
        } else {
            false
        }
    }

    override fun onSwipeUpdate(distanceX: Float, distanceY: Float) {
        if (!toolbarIsUncovered() || !gestureActive || !animationOwner.isCurrent(gestureEpoch)) return
        when (getDestination()) {
            is Destination.Tab -> {
                tabPreview.translationX = when (gestureDirection) {
                    GestureDirection.RIGHT_TO_LEFT -> min(
                        windowWidth.toFloat() + previewOffset,
                        tabPreview.translationX - distanceX
                    ).coerceAtLeast(0f)
                    GestureDirection.LEFT_TO_RIGHT -> max(
                        -windowWidth.toFloat() - previewOffset,
                        tabPreview.translationX - distanceX
                    ).coerceAtMost(0f)
                }
                contentLayout.translationX = when (gestureDirection) {
                    GestureDirection.RIGHT_TO_LEFT -> min(
                        0f,
                        contentLayout.translationX - distanceX
                    ).coerceAtLeast(-windowWidth.toFloat() - previewOffset)
                    GestureDirection.LEFT_TO_RIGHT -> max(
                        0f,
                        contentLayout.translationX - distanceX
                    ).coerceAtMost(windowWidth.toFloat() + previewOffset)
                }
            }
            is Destination.None -> {
                // If there is no "next" tab to swipe to in the gesture direction, only do a
                // partial animation to show that we are at the end of the tab list
                val maxContentHidden = contentLayout.width * OVERSCROLL_HIDE_PERCENT
                contentLayout.translationX = when (gestureDirection) {
                    GestureDirection.RIGHT_TO_LEFT -> max(
                        -maxContentHidden.toFloat(),
                        contentLayout.translationX - distanceX
                    ).coerceAtMost(0f)
                    GestureDirection.LEFT_TO_RIGHT -> min(
                        maxContentHidden.toFloat(),
                        contentLayout.translationX - distanceX
                    ).coerceAtLeast(0f)
                }
            }
        }
    }

    override fun onSwipeFinished(velocityX: Float, velocityY: Float) {
        if (!toolbarIsUncovered() || !gestureActive || !animationOwner.isCurrent(gestureEpoch)) return
        gestureActive = false
        val destination = getDestination()
        if (destination is Destination.Tab && isGestureComplete(velocityX)) {
            animateToNextTab(destination.tab)
        } else {
            animateCanceledGesture(velocityX)
        }
        gestureSource = null
    }

    override fun onSwipeCancelled() {
        if (!toolbarIsUncovered() || !gestureActive || !animationOwner.isCurrent(gestureEpoch)) return
        gestureActive = false
        gestureSource = null
        animateCanceledGesture(0f)
    }

    private fun getDestination(): Destination {
        val isLtr = activity.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR
        val currentTab = store.state.selectedTab ?: return Destination.None
        if (gestureSource?.let { it.id != currentTab.id || !currentTab.sameSwipeScope(it) } == true) return Destination.None
        val tabs = store.state.tabs.filter { it.sameSwipeScope(currentTab) }
        val eligible = tabs.map { it.id }.toSet()
        
        // Left swipe = go to newer tabs (smaller LRU offset: -1)
        // Right swipe = go to older tabs (larger LRU offset: +1)
        val nextTabId = when (gestureDirection) {
            GestureDirection.RIGHT_TO_LEFT -> if (isLtr) {
                // Swiping left in LTR = go to newer tab
                lruManager.getTabAtLRUOffset(currentTab.id, -1, eligible)
            } else {
                lruManager.getTabAtLRUOffset(currentTab.id, 1, eligible)
            }
            GestureDirection.LEFT_TO_RIGHT -> if (isLtr) {
                // Swiping right in LTR = go to older tab
                lruManager.getTabAtLRUOffset(currentTab.id, 1, eligible)
            } else {
                lruManager.getTabAtLRUOffset(currentTab.id, -1, eligible)
            }
        }
        
        if (nextTabId == null) return Destination.None
        
        val nextTab = tabs.find { it.id == nextTabId }
        
        return if (nextTab != null) {
            Destination.Tab(nextTab)
        } else {
            Destination.None
        }
    }

    private fun preparePreview(destination: Destination) {
        val thumbnailId = when (destination) {
            is Destination.Tab -> destination.tab.id
            is Destination.None -> return
        }

        tabPreview.loadPreviewThumbnail(thumbnailId)
        tabPreview.alpha = 1f
        tabPreview.translationX = when (gestureDirection) {
            GestureDirection.RIGHT_TO_LEFT -> windowWidth.toFloat() + previewOffset
            GestureDirection.LEFT_TO_RIGHT -> -windowWidth.toFloat() - previewOffset
        }
        tabPreview.isVisible = true
    }

    private fun isGestureComplete(velocityX: Float): Boolean {
        val previewWidth = tabPreview.getRectWithViewLocation().visibleWidth.toDouble()
        return ToolbarSwipeFinishPolicy.completes(
            swipingLeft = gestureDirection == GestureDirection.RIGHT_TO_LEFT,
            velocityX = velocityX,
            minimumFlingVelocity = minimumFlingVelocity,
            visibleFraction = previewWidth / windowWidth
        )
    }

    private fun getAnimator(
        finalContextX: Float,
        duration: Long,
        epoch: Long,
        direction: GestureDirection
    ): ValueAnimator {
        val width = windowWidth.toFloat()
        return ValueAnimator.ofFloat(contentLayout.translationX, finalContextX).apply {
            this.duration = duration
            this.interpolator = LinearOutSlowInInterpolator()
            addUpdateListener { animator ->
                if (!canClaimToolbarSwipe()) {
                    gestureEpoch = animationOwner.beginGesture()
                    return@addUpdateListener
                }
                if (!animationOwner.isCurrent(epoch)) return@addUpdateListener
                val value = animator.animatedValue as Float
                contentLayout.translationX = value
                tabPreview.translationX = when (direction) {
                    GestureDirection.RIGHT_TO_LEFT -> value + width + previewOffset
                    GestureDirection.LEFT_TO_RIGHT -> value - width - previewOffset
                }
            }
        }
    }

    private fun animateToNextTab(tab: TabSessionState) {
        val epoch = gestureEpoch
        val direction = gestureDirection
        val source = gestureSource ?: store.state.selectedTab
        val browserFinalXCoordinate: Float = when (direction) {
            GestureDirection.RIGHT_TO_LEFT -> -windowWidth.toFloat() - previewOffset
            GestureDirection.LEFT_TO_RIGHT -> windowWidth.toFloat() + previewOffset
        }

        animationOwner.play(
            epoch,
            getAnimator(browserFinalXCoordinate, FINISHED_GESTURE_ANIMATION_DURATION, epoch, direction)
        ) {
            if (!canClaimToolbarSwipe()) {
                gestureEpoch = animationOwner.beginGesture()
                return@play
            }
            if (!animationOwner.isCurrent(epoch)) return@play
            contentLayout.translationX = 0f
            if (activity.isFinishing || activity.isDestroyed || !store.state.canFinishSwipe(source, tab.id)) {
                tabPreview.isVisible = false
                return@play
            }
                lruManager.markAsSwipeNavigation(tab.id)
                selectTabUseCase(tab.id)
            if (animationOwner.isCurrent(epoch)) {
                val shortDuration =
                    activity.resources.getInteger(android.R.integer.config_shortAnimTime)
                animationOwner.fadePreview(epoch, shortDuration.toLong())
            }
        }
    }

    private fun animateCanceledGesture(velocityX: Float) {
        val epoch = gestureEpoch
        val direction = gestureDirection
        val duration = if (abs(velocityX) >= minimumFlingVelocity) {
            CANCELED_FLING_ANIMATION_DURATION
        } else {
            CANCELED_GESTURE_ANIMATION_DURATION
        }
        animationOwner.play(epoch, getAnimator(0f, duration, epoch, direction)) {
            if (!animationOwner.isCurrent(epoch)) return@play
            contentLayout.translationX = 0f
            tabPreview.isVisible = false
        }
    }

    private fun PointF.isInToolbar(): Boolean {
        val toolbarLocation = toolbarLayout.getRectWithScreenLocation()
        // In Android 10, the system gesture touch area overlaps the bottom of the toolbar, so
        // lets make our swipe area taller by that amount
        activity.window.decorView.getWindowInsets()?.let { insets ->
            if (UserPreferences(activity).toolbarPosition == ToolbarPosition.BOTTOM.ordinal) {
                toolbarLocation.top -= (insets.mandatorySystemGestureInsets.bottom - insets.stableInsetBottom)
            }
        }
        return toolbarLocation.contains(toPoint())
    }

    private val Rect.visibleWidth: Int
        get() = if (left < 0) {
            right
        } else {
            windowWidth - left
        }

    companion object {
        /**
         * The percentage of the tab preview that needs to be visible to consider the
         * tab switching gesture complete.
         */
        private const val GESTURE_FINISH_PERCENT = 0.25

        /**
         * The percentage of the content view that can be hidden by the tab switching gesture if
         * there is not tab available to switch to
         */
        private const val OVERSCROLL_HIDE_PERCENT = 0.20

        /**
         * Animation duration when switching to another tab
         */
        private const val FINISHED_GESTURE_ANIMATION_DURATION = 250L

        /**
         * Animation duration gesture is canceled due to the swipe not being far enough
         */
        private const val CANCELED_GESTURE_ANIMATION_DURATION = 200L

        /**
         * Animation duration gesture is canceled due to a swipe in the opposite direction
         */
        private const val CANCELED_FLING_ANIMATION_DURATION = 150L
    }
}
