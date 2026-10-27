package com.prirai.android.nira.components.toolbar.modern

import com.prirai.android.nira.diagnostics.testlab.*

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import mozilla.components.concept.toolbar.ScrollableToolbar
import mozilla.components.concept.engine.EngineView

/**
 * Revolutionary unified toolbar system that combines all toolbar components
 * into one seamless, beautifully animated container with perfect scroll behavior.
 */
class ModernToolbarSystem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr), ScrollableToolbar {

    private var currentOffset = 0
    // Preserve hide state across rotation and group-chip height changes.
    private var requestedCollapsed = false
    private var scrollingEnabled = true
    private val hideAnimator = ValueAnimator()
    private var offsetAnimating = false
    private var animatedTargetOffset: Int? = null
    private var engineView: EngineView? = null

    // Position tracking for proper scroll direction
    private var toolbarPosition: ToolbarPosition = ToolbarPosition.BOTTOM

    // Components
    private var tabGroupComponent: View? = null
    private var addressBarComponent: View? = null
    private var contextualComponent: View? = null
    
    // Toolbar offset callback for smooth margin adjustment
    private var onOffsetChangedListener: ((Int, Int) -> Unit)? = null

    enum class ToolbarPosition {
        TOP, BOTTOM
    }

    init {
        orientation = VERTICAL
        clipToPadding = false
        clipChildren = false
        
        // Use unified ThemeManager helper (supports AMOLED + Material 3)
        setBackgroundColor(
            com.prirai.android.nira.theme.ThemeManager.getToolbarBackgroundColor(
                context, 
                useElevation = true, 
                elevationDp = 3f
            )
        )
    }

    fun addComponent(component: View, type: ComponentType) {

        val height = if (type == ComponentType.ADDRESS_BAR) {
            (com.prirai.android.nira.preferences.UserPreferences(context).niraAddressBarHeightDp *
                resources.displayMetrics.density).toInt()
        } else LayoutParams.WRAP_CONTENT
        val layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, height)

        when (type) {
            ComponentType.TAB_GROUP -> {
                tabGroupComponent = component
                addView(component, if (addressBarComponent != null) 1 else 0, layoutParams)
            }

            ComponentType.ADDRESS_BAR -> {
                addressBarComponent = component
                addView(component, 0, layoutParams) // Address above tabs
            }

            ComponentType.CONTEXTUAL -> {
                contextualComponent = component
                addView(component, layoutParams) // Bottom position
            }
        }

        // Update engine view clipping after adding component
        updateEngineViewClipping()
    }

    fun removeComponent(type: ComponentType) {
        val component = when (type) {
            ComponentType.TAB_GROUP -> tabGroupComponent
            ComponentType.ADDRESS_BAR -> addressBarComponent
            ComponentType.CONTEXTUAL -> contextualComponent
        }

        component?.let {
            removeView(it)
            when (type) {
                ComponentType.TAB_GROUP -> tabGroupComponent = null
                ComponentType.ADDRESS_BAR -> addressBarComponent = null
                ComponentType.CONTEXTUAL -> contextualComponent = null
            }
        }

        // Update engine view clipping after removing component
        updateEngineViewClipping()
    }

    fun setEngineView(engine: EngineView) {
        engineView = engine

        // Previously called `setDynamicToolbarMaxHeight(currentHeight)`
        // on first attach; that reserved a soft gutter inside the
        // engine viewport which caused `position: fixed; bottom: 0`
        // page elements to float above our pinned toolbar instead of
        // sitting flush against it. The pinned toolbar's layout
        // reservation now happens entirely via swipeRefresh.bottomMargin
        // on the BrowserFragment side (see
        // UnifiedToolbar.reconcileEngineViewHeight), so we tell Gecko
        // there is no dynamic chrome at all.
        post {
            engine.setDynamicToolbarMaxHeight(0)
        }
    }

    fun setToolbarPosition(position: ToolbarPosition) {
        toolbarPosition = position
        
        // Setup window insets handling for edge-to-edge
        // This allows toolbars to add padding for system bars without affecting fullscreen
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            // Account for landscape cutouts and status/navigation bars on all
            // sides. Keep padding even during the alpha hide animation: changing
            // height mid-animation makes the toolbar overlap system UI.
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            val top = if (toolbarPosition == ToolbarPosition.TOP) bars.top else 0
            val bottom = if (toolbarPosition == ToolbarPosition.BOTTOM) bars.bottom else 0
            if (view.paddingLeft != bars.left || view.paddingTop != top ||
                view.paddingRight != bars.right || view.paddingBottom != bottom) {
                view.setPadding(bars.left, top, bars.right, bottom)
            }

            insets
        }
    }

    fun getTotalHeight(): Int {
        var totalHeight = paddingTop + paddingBottom

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.isVisible) {
                totalHeight += child.height
            }
        }

        return totalHeight
    }

    override fun enableScrolling() {
        scrollingEnabled = true
    }

    override fun disableScrolling() {
        scrollingEnabled = false
        expand()
    }

    override fun expand() {
        requestedCollapsed = false
        animateToOffset(0)
    }

    override fun collapse() {
        requestedCollapsed = true
        val totalHeight = getTotalHeight()
        if (totalHeight > 0) {
            animateToOffset(totalHeight)
        }
    }

    private fun animateToOffset(targetOffset: Int) {
        val target = targetOffset.coerceIn(0, getTotalHeight())
        // Repeated scroll requests must not cancel and restart identical animations.
        if (hideAnimator.isRunning && animatedTargetOffset == target) return
        if (!hideAnimator.isRunning && currentOffset == target) return
        hideAnimator.cancel()
        hideAnimator.removeAllUpdateListeners()
        hideAnimator.removeAllListeners()
        offsetAnimating = true
        animatedTargetOffset = target
        val capture=TestLab.capture()
        val measurement=capture?.operation(if(targetOffset==0)LabOperation.TOOLBAR_SHOW else LabOperation.TOOLBAR_HIDE)

        hideAnimator.apply {
            setIntValues(currentOffset, target)
            duration = 200
            addUpdateListener { animation ->
                val frameOffset = animation.animatedValue as Int
                if (frameOffset != currentOffset) setToolbarOffset(frameOffset)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    cancelled = true
                    animatedTargetOffset = null
                    capture?.end(measurement,OperationOutcome.CANCELLED)
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!cancelled) {
                        offsetAnimating = false
                        animatedTargetOffset = null
                        if (currentOffset != target) setToolbarOffset(target)
                        capture?.end(measurement,proof=CompletionProof.ANIMATOR_END)
                    }
                }
            })
            start()
        }
    }

    /** Keep GeckoView's measured viewport stable during toolbar motion. */
    fun isOffsetAnimating(): Boolean = offsetAnimating

    /** The state requested by page scroll, independent of animation progress. */
    fun isCollapsed(): Boolean = requestedCollapsed

    fun setToolbarOffset(offset: Int) {
        val totalHeight = getTotalHeight()
        currentOffset = offset.coerceIn(0, totalHeight)

        // Position-aware translation:
        // TOP toolbar: Negative Y to hide upward, 0 Y to show
        // BOTTOM toolbar: Positive Y to hide downward, 0 Y to show
        translationY = when (toolbarPosition) {
            ToolbarPosition.TOP -> -currentOffset.toFloat()  // Negative moves UP (hiding)
            ToolbarPosition.BOTTOM -> currentOffset.toFloat()  // Positive moves DOWN (hiding)
        }

        // Notify listener for engine view padding adjustment
        onOffsetChangedListener?.invoke(currentOffset, totalHeight)

        // Update engine view padding to maintain clipping
        updateEngineViewClipping()
        
        // Make toolbar transparent when fully hidden to prevent visibility in gesture navigation bar
        // Calculate hide percentage (0 = fully visible, 1 = fully hidden)
        val hidePercentage = if (totalHeight > 0) {
            (currentOffset.toFloat() / totalHeight.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        
        // Fade out toolbar as it hides (becomes fully transparent when completely hidden)
        alpha = 1f - hidePercentage
        
        // Keep visibility as VISIBLE to maintain layout space
        // (alpha handles transparency, not visibility)
        visibility = VISIBLE
    }
    
    /**
     * Formerly updated GeckoView's dynamic-toolbar reservation as the
     * toolbar translated during scroll. The toolbar is now pinned; only
     * the aux bars (tab bar + contextual bar) toggle via UnifiedToolbar's
     * minimal state. Scroll-DOWN enters minimal (ModernScrollBehavior);
     * the chevron button on the address bar toggles both directions and
     * is the sole exit path. Chrome reservation is a static
     * swipeRefresh.bottomMargin on the fragment side, so GeckoView's
     * dynamic-toolbar reservation stays at 0.
     *
     * Kept as a no-op so the existing `updateEngineViewClipping()`
     * call sites inside this file (addComponent, removeComponent,
     * setToolbarOffset for fullscreen) continue to compile without
     * touching every callsite.
     */
    private fun updateEngineViewClipping() {
        // Intentional no-op; see KDoc.
    }

    /**
     * The measured height can change while the top toolbar is hidden (rotation,
     * group chips or a new tab). A stale offset must not reserve a blank band.
     */
    fun getEffectiveOffset(): Int {
        val total = getTotalHeight()
        if (requestedCollapsed && !hideAnimator.isRunning) {
            if (currentOffset != total) setToolbarOffset(total)
            return total
        }
        return currentOffset.coerceIn(0, total)
    }

    fun getCurrentOffset(): Int = currentOffset
    
    /**
     * Set listener for offset changes (for smooth margin adjustment)
     */
    fun setOnOffsetChangedListener(listener: (Int, Int) -> Unit) {
        onOffsetChangedListener = listener
    }

    enum class ComponentType {
        TAB_GROUP,
        ADDRESS_BAR,
        CONTEXTUAL,
    }
}
