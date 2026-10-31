package com.prirai.android.nira.browser.toolbar

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.PointF
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.contains
import androidx.core.graphics.toPoint
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.navigation.findNavController
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class HomeToolbarGestureHandler(
    private val activity: Activity,
    private val contentLayout: View,
    private val tabPreview: FakeTab,
    private val toolbarLayout: View,
    private val store: BrowserStore,
    private val selectTabUseCase: TabsUseCases.SelectTabUseCase
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

    override fun onSwipeStarted(start: PointF, next: PointF): Boolean {
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
        if (!gestureActive || !animationOwner.isCurrent(gestureEpoch)) return
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
        if (!gestureActive || !animationOwner.isCurrent(gestureEpoch)) return
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
        if (!gestureActive || !animationOwner.isCurrent(gestureEpoch)) return
        gestureActive = false
        gestureSource = null
        animateCanceledGesture(0f)
    }

    private fun getDestination(): Destination {
        activity.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR
        val currentTab = store.state.selectedTab ?: return Destination.None
        if (gestureSource?.let { it.id != currentTab.id || !currentTab.sameSwipeScope(it) } == true) return Destination.None
        val tabs = store.state.tabs.filter { it.id != currentTab.id && it.sameSwipeScope(currentTab) }
        val nextTabId = lruManager.getMostRecentTab(tabs.map { it.id }.toSet()) ?: return Destination.None
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
        val velocityMatchesDirection = when (gestureDirection) {
            GestureDirection.RIGHT_TO_LEFT -> velocityX <= 0
            GestureDirection.LEFT_TO_RIGHT -> velocityX >= 0
        }
        val reverseFling = abs(velocityX) >= minimumFlingVelocity && !velocityMatchesDirection

        val visibleWidth = if (tabPreview.translationX < 0) {
            windowWidth + tabPreview.translationX
        } else {
            windowWidth - tabPreview.translationX
        }

        return !reverseFling && (visibleWidth / windowWidth >= GESTURE_FINISH_PERCENT ||
            abs(velocityX) >= minimumFlingVelocity)
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
            if (!animationOwner.isCurrent(epoch)) return@play
            contentLayout.translationX = 0f
            if (activity.isFinishing || activity.isDestroyed || !store.state.canFinishSwipe(source, tab.id)) {
                tabPreview.isVisible = false
                return@play
            }
                if (tab.content.url != "about:homepage") {
                    activity.findNavController(R.id.container).navigate(R.id.browserFragment)
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
        activity.window.decorView.getWindowInsets()?.let { insets ->
            if (UserPreferences(activity).toolbarPosition == ToolbarPosition.BOTTOM.ordinal) {
                toolbarLocation.top -= (insets.mandatorySystemGestureInsets.bottom - insets.stableInsetBottom)
            }
        }
        return toolbarLocation.contains(toPoint())
    }

    companion object {
        private const val GESTURE_FINISH_PERCENT = 0.25
        private const val OVERSCROLL_HIDE_PERCENT = 0.20
        private const val FINISHED_GESTURE_ANIMATION_DURATION = 250L
        private const val CANCELED_GESTURE_ANIMATION_DURATION = 200L
        private const val CANCELED_FLING_ANIMATION_DURATION = 150L
    }
}
