package com.prirai.android.nira.components.toolbar.unified

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.components.toolbar.BrowserToolbarView
import com.prirai.android.nira.components.toolbar.BrowserToolbarViewInteractor
import com.prirai.android.nira.components.toolbar.ToolbarIntegration
import com.prirai.android.nira.components.toolbar.ToolbarPosition
import com.prirai.android.nira.components.toolbar.modern.ModernToolbarSystem
import com.prirai.android.nira.components.toolbar.modern.ComposeTabBarWithProfileSwitcher
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.toolbar.ContextualBottomToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.CustomTabSessionState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.concept.engine.EngineView
import mozilla.components.concept.toolbar.ScrollableToolbar
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.ktx.android.content.res.resolveAttribute
import com.prirai.android.nira.components.toolbar.modern.ModernScrollBehavior
import androidx.core.view.isNotEmpty

/**
 * UnifiedToolbar - Centralized toolbar component that unifies tab bar, address bar, and contextual toolbar.
 *
 * Uses the proper components:
 * - ComposeTabBarWithProfileSwitcher: Compose-based tab bar with profile pill
 * - BrowserToolbar: Address bar for URL/search
 * - ContextualBottomToolbar: Context-aware action buttons
 * 
 * Wraps ModernToolbarSystem which provides:
 * - Material 3 theming with elevation overlay
 * - Proper scroll behavior
 * - Edge-to-edge window insets
 * - Top/bottom positioning support
 */
class UnifiedToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : android.widget.FrameLayout(context, attrs, defStyleAttr), ScrollableToolbar {

    // Preferences
    private val prefs = UserPreferences(context)

    // The actual toolbar system
    private val toolbarSystem: ModernToolbarSystem = ModernToolbarSystem(context, attrs, defStyleAttr)

    // Toolbar components
    private var tabGroupBar: ComposeTabBarWithProfileSwitcher? = null
    private var addressBarContainer: ViewGroup? = null
    private var browserToolbar: BrowserToolbar? = null
    private var browserToolbarView: BrowserToolbarView? = null
    private var contextualToolbar: ContextualBottomToolbar? = null
    
    // EngineView reference for toolbar behavior
    private var engineView: EngineView? = null
    
    // Store reference for tab updates
    private var browserStore: BrowserStore? = null
    
    // Tab selection callback
    private var onTabSelectedCallback: ((String) -> Unit)? = null
    
    // Expansion state callback
    private var onExpansionStateChanged: ((Boolean) -> Unit)? = null
    
    // Toolbar offset callback for smooth margin adjustment
    private var onToolbarOffsetChanged: ((Int, Int) -> Unit)? = null

    // Minimal state (address bar only). Set by BrowserFragment when a page has
    // finished loading. First tap on the address bar exits this state (see the
    // interceptor wiring in BrowserFragment); a second tap opens the search
    // dialog. Swipe gestures on the tab bar remain unaffected because the tab
    // bar is fully detached from touch input while GONE.
    private var minimalState: Boolean = false
    private var onMinimalStateChanged: ((Boolean) -> Unit)? = null

    // Single value-animator that drives the minimal-state transition. We
    // use the same `ValueAnimator`-emitting-offset-per-frame pattern
    // `ModernToolbarSystem.animateToOffset` uses (and that AC's
    // `EngineViewClippingBehavior` effectively uses by updating
    // `engineViewParent.translationY` / `setVerticalClipping` on every
    // frame of a nested scroll). Each frame we:
    //   - translate the aux bars by the current offset so they slide
    //     out/in,
    //   - fire `onToolbarOffsetChanged(offset, totalHeight)` so
    //     `BrowserFragment.adjustWebContentMarginsForToolbarOffset`
    //     shrinks/grows `swipeRefresh.bottomMargin` in step.
    // Without the per-frame listener call, the aux bars fade out but the
    // engine view does not grow to fill the vacated space until the
    // animation's end layout pass - leaving a visible black band during
    // the 200 ms window where chrome WAS.
    private var minimalAnimator: ValueAnimator? = null

    // Set to `true` while `minimalAnimator` is running so the
    // `OnLayoutChangeListener` on `toolbarSystem` does not fire a
    // mid-animation `reconcileEngineViewHeight()` that would conflict
    // with the per-frame updates.
    private var suppressReconcileDuringAnimation = false

    // Reload/Stop button integration
    private var reloadStopIntegration: com.prirai.android.nira.integration.ReloadStopButtonIntegration? = null

    
    // Contextual toolbar listener reference (used when contextual toolbar is disabled)
    private var contextualToolbarListener: ContextualBottomToolbar.ContextualToolbarListener? = null

    // Configuration
    private var showTabGroupBar: Boolean = prefs.showTabGroupBar
    private var showContextualToolbar: Boolean = prefs.showContextualToolbar
    
    // Bottom components container reference (for TOP toolbar mode)
    private var bottomComponentsContainer: ViewGroup? = null
    private var shouldHideBottomComponents = false

    init {
        // Allow children to draw outside bounds (for elevated tab pills during drag)
        clipChildren = false
        clipToPadding = false

        // Add toolbarSystem as child
        addView(toolbarSystem, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ))

        // Set toolbar position from preferences
        val toolbarPos = if (prefs.toolbarPosition == ToolbarPosition.BOTTOM.ordinal) {
            ModernToolbarSystem.ToolbarPosition.BOTTOM
        } else {
            ModernToolbarSystem.ToolbarPosition.TOP
        }
        toolbarSystem.setToolbarPosition(toolbarPos)
        shouldHideBottomComponents = (toolbarPos == ModernToolbarSystem.ToolbarPosition.TOP)

        // Reconcile the engine view's reserved toolbar height whenever the
        // toolbar system's layout changes. The aux bars (tab bar +
        // contextual bar) go VISIBLE <-> GONE as minimal state toggles,
        // which shrinks or grows toolbarSystem.height; without this listener
        // GeckoView would keep clipping the originally-reserved full
        // toolbar height, leaving a blank band of wasted space where the
        // aux bars used to be. This also covers rotation and font-scale
        // changes that affect toolbar height.
        //
        // During the minimal-state animation the aux bars are only being
        // translated (not laid out), so this listener does NOT fire from
        // the animation itself. But the terminal `visibility = GONE` /
        // `VISIBLE` toggles at animation end would re-trigger it and
        // double-fire the offset listener on the same frame the animator
        // emitted its final value; the `suppressReconcileDuringAnimation`
        // flag below skips that duplicate.
        toolbarSystem.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop && !suppressReconcileDuringAnimation) {
                reconcileEngineViewHeight()
            }
        }
    }
    
    // Forward ScrollableToolbar methods to toolbarSystem and behavior
    override fun enableScrolling() {
        toolbarSystem.enableScrolling()
        (layoutParams as? CoordinatorLayout.LayoutParams)?.let {
            (it.behavior as? ModernScrollBehavior)?.enableScrolling()
        }
    }
    
    override fun disableScrolling() {
        toolbarSystem.disableScrolling()
        (layoutParams as? CoordinatorLayout.LayoutParams)?.let {
            (it.behavior as? ModernScrollBehavior)?.disableScrolling()
        }
    }
    
    override fun expand() {
        toolbarSystem.expand()
        // Also show bottom components if in TOP mode
        if (shouldHideBottomComponents) {
            bottomComponentsContainer?.let { container ->
                container.visibility = VISIBLE
                container.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            }
        }
        // Update status bar color to show toolbar through it (top toolbar only)
        updateStatusBarForToolbarState(expanded = true)
        // Notify expansion state changed
        onExpansionStateChanged?.invoke(true)
    }
    
    override fun collapse() {
        toolbarSystem.collapse()
        // Also hide bottom components if in TOP mode
        if (shouldHideBottomComponents) {
            bottomComponentsContainer?.let { container ->
                // Set GONE immediately to prevent space reservation
                container.visibility = GONE
                // Still animate for smooth visual effect (though invisible)
                val height = container.height.toFloat()
                container.animate()
                    .translationY(height)
                    .alpha(0f)
                    .setDuration(200)
                    .start()
            }
        }
        // Update status bar color to hide toolbar (top toolbar only)
        updateStatusBarForToolbarState(expanded = false)
        // Notify expansion state changed
        onExpansionStateChanged?.invoke(false)
    }
    
    /**
     * Show menu from the toolbar's menu button
     */
    fun showMenu(menuItems: List<com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem>) {
        // Use the browser toolbar view as anchor for the menu
        val toolbarView = browserToolbarView?.view
        if (toolbarView != null) {
            com.prirai.android.nira.components.menu.Material3BrowserMenu(
                context,
                menuItems
            ).show(toolbarView)
        }
    }

    /**
     * Setup the unified toolbar with all components.
     *
     * @param parent The parent container (typically a CoordinatorLayout)
     * @param lifecycleOwner The lifecycle owner for observing state changes
     * @param interactor Handles toolbar interactions
     * @param customTabSession Optional custom tab session
     * @param store The browser store for tab observation
     * @return The configured UnifiedToolbar instance
     */
    fun setup(
        parent: ViewGroup,
        lifecycleOwner: LifecycleOwner,
        interactor: BrowserToolbarViewInteractor,
        customTabSession: CustomTabSessionState? = null,
        store: BrowserStore
    ): UnifiedToolbar {

        // Store reference
        this.browserStore = store

        // Add components in order based on toolbar position
        val toolbarPos = if (prefs.toolbarPosition == ToolbarPosition.BOTTOM.ordinal) {
            ModernToolbarSystem.ToolbarPosition.BOTTOM
        } else {
            ModernToolbarSystem.ToolbarPosition.TOP
        }

        when (toolbarPos) {
            ModernToolbarSystem.ToolbarPosition.TOP -> {
                // One address row, tab strip immediately below; no footer.
                addAddressBar(parent, lifecycleOwner, interactor, customTabSession, store)
                addTabGroupBar(store, lifecycleOwner)
                addBrowserActionsToAddressBar()
            }
            ModernToolbarSystem.ToolbarPosition.BOTTOM -> {
                // BOTTOM: Contextual Toolbar -> Address Bar -> Tab Bar (all together)
                addContextualToolbar()
                addAddressBar(parent, lifecycleOwner, interactor, customTabSession, store)
                addTabGroupBar(store, lifecycleOwner)
                // Add browser actions to address bar if contextual toolbar is disabled
                if (!showContextualToolbar) {
                    addBrowserActionsToAddressBar()
                }
            }
        }

        // Always setup scroll behavior (always enabled now)
        setupScrollBehavior(parent)

        return this
    }

    /**
     * Creates the tab group bar component (ComposeTabBarWithProfileSwitcher).
     * This is separated from addTabGroupBar to allow creating the component
     * without immediately adding it to the toolbar system (needed for TOP toolbar mode).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun createTabGroupBar(
        store: BrowserStore,
        lifecycleOwner: LifecycleOwner
    ) {
        if (!showTabGroupBar) {
            return
        }

        tabGroupBar = ComposeTabBarWithProfileSwitcher(context).apply {
            // Material 3 theming is already applied by ComposeTabBarWithProfileSwitcher
            
            // Setup with all required callbacks for tab interaction and profile management
            setup(
                onTabSelected = { tabId ->
                    // Delegate to external callback set via setOnTabSelectedListener()
                    onTabSelectedCallback?.invoke(tabId)
                },
                onTabClosed = { tabId ->
                    // Close the tab using browser's tab management system
                    context.components.tabsUseCases.removeTab(tabId)
                },
                onProfileSelected = { profile ->
                    // Switch to the selected profile and reload to apply changes
                    val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(context)
                    profileManager.setActiveProfile(profile)
                    profileManager.setPrivateMode(false) // Switching to a profile exits private mode

                    // Manually trigger tab bar update with filtered tabs
                    // Include guest tabs (contextId == null) in addition to profile tabs
                    val expectedContextId = "profile_${profile.id}"
                    val state = store.state
                    val filteredTabs = state.tabs.filter { tab ->
                        if (tab.contextId == null) {
                            // Guest tabs from custom tabs - always show in current profile
                            tab.content.private == false
                        } else {
                            tab.content.private == false && tab.contextId == expectedContextId
                        }
                    }

                    tabGroupBar?.updateTabs(filteredTabs, state.selectedTabId)
                    tabGroupBar?.updateProfileIcon(profile)

                    context.components.sessionUseCases.reload()
                },
                onPrivateModeSelected = {
                    // Private mode was already set by the menu handler, just update the UI
                    val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(context)
                    val isPrivateMode = profileManager.isPrivateMode()
                    val currentProfile = profileManager.getActiveProfile()

                    // Manually trigger tab bar update with filtered tabs
                    // Include guest tabs in normal mode, but not in private mode
                    val expectedContextId = if (isPrivateMode) {
                        "private"
                    } else {
                        "profile_${currentProfile.id}"
                    }
                    val state = store.state
                    val filteredTabs = state.tabs.filter { tab ->
                        if (tab.contextId == null && !isPrivateMode) {
                            // Guest tabs from custom tabs - show in normal mode
                            tab.content.private == false
                        } else {
                            tab.content.private == isPrivateMode && tab.contextId == expectedContextId
                        }
                    }

                    tabGroupBar?.updateTabs(filteredTabs, state.selectedTabId)

                    context.components.sessionUseCases.reload()
                }
            )
        }

        // Observe browser store and filter tabs by current profile and private mode.
        // This ensures that:
        // 1. Each profile only sees its own tabs (contextId matches "profile_{id}")
        // 2. Private mode only shows private tabs (contextId = "private")
        // 3. Guest tabs from custom tabs (contextId == null) are shown in normal mode
        // 4. Tab bar updates automatically when profile switches or tabs change
        lifecycleOwner.lifecycleScope.launch {
            store.flowScoped(dispatcher = Dispatchers.Main) { flow ->
                flow.collect { state ->
                    val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(context)
                    val isPrivateMode = profileManager.isPrivateMode()
                    val currentProfile = profileManager.getActiveProfile()
                    
                    // Determine expected contextId based on current browsing mode
                    // Private mode uses "private", normal mode uses "profile_{id}"
                    val expectedContextId = if (isPrivateMode) {
                        "private"
                    } else {
                        "profile_${currentProfile.id}"
                    }
                    
                    // Filter tabs to only show those belonging to current profile/mode
                    // Also include guest tabs (contextId == null) which come from custom tabs
                    val filteredTabs = state.tabs.filter { tab ->
                        if (tab.contextId == null) {
                            // Guest tabs from custom tabs - always show in current profile if not private
                            !isPrivateMode && !tab.content.private
                        } else {
                            // Regular profile tabs
                            tab.content.private == isPrivateMode && tab.contextId == expectedContextId
                        }
                    }
                    
                    tabGroupBar?.updateTabs(filteredTabs, state.selectedTabId)
                }
            }
        }

    }
    
    /**
     * Force update the tab bar with the provided filtered tabs.
     * Used when fragments need to explicitly refresh the tab bar (e.g., ComposeHomeFragment on load).
     */
    fun forceUpdateTabBar(filteredTabs: List<TabSessionState>, selectedTabId: String?) {
        tabGroupBar?.updateTabs(filteredTabs, selectedTabId)
    }
    
    /**
     * Adds the tab group bar component (ComposeTabBarWithProfileSwitcher) to the unified toolbar.
     *
     * This method:
     * 1. Creates ComposeTabBarWithProfileSwitcher with all necessary callbacks
     * 2. Sets up profile switching and private mode toggling
     * 3. Observes browser store to filter tabs by current profile and private mode
     * 4. Adds the component to the ModernToolbarSystem
     *
     * Tab filtering logic:
     * - Only shows tabs matching current profile (contextId = "profile_{id}")
     * - In private mode, only shows tabs with contextId = "private"
     * - Guest tabs from custom tabs (contextId == null) are shown in normal mode
     * - Ensures each profile sees only its own tabs plus guest tabs
     *
     * @param store The browser store to observe for tab changes
     * @param lifecycleOwner Lifecycle owner to scope the store observation
     */
    private fun addTabGroupBar(
        store: BrowserStore,
        lifecycleOwner: LifecycleOwner
    ) {
        createTabGroupBar(store, lifecycleOwner)
        if (tabGroupBar != null) {
            toolbarSystem.addComponent(tabGroupBar!!, ModernToolbarSystem.ComponentType.TAB_GROUP)
        }
    }

    /**
     * Add address bar component - using BrowserToolbarView for proper integration
     */
    private fun addAddressBar(
        parent: ViewGroup,
        lifecycleOwner: LifecycleOwner,
        interactor: BrowserToolbarViewInteractor,
        customTabSession: CustomTabSessionState?,
        store: BrowserStore
    ) {
        // Create a temporary container for BrowserToolbarView
        val tempContainer = android.widget.FrameLayout(context)
        
        val toolbarPos = if (prefs.toolbarPosition == ToolbarPosition.BOTTOM.ordinal) {
            ToolbarPosition.BOTTOM
        } else {
            ToolbarPosition.TOP
        }
        
        // Use BrowserToolbarView to get properly configured toolbar
        browserToolbarView = BrowserToolbarView(
            container = tempContainer,
            toolbarPosition = toolbarPos,
            interactor = interactor,
            customTabSession = customTabSession,
            lifecycleOwner = lifecycleOwner,
            engineView = engineView
        )
        
        // Extract the toolbar from the temporary container
        browserToolbar = browserToolbarView!!.view
        
        // Remove from temp container's parent if it has one
        (browserToolbar?.parent as? ViewGroup)?.removeView(browserToolbar)
        
        // Add directly to toolbar system
        toolbarSystem.addComponent(browserToolbar!!, ModernToolbarSystem.ComponentType.ADDRESS_BAR)
        
        // Start the toolbar integration to connect it to browser state
        browserToolbarView!!.toolbarIntegration.start()
        
        // Stop integration when lifecycle is destroyed
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                browserToolbarView?.toolbarIntegration?.stop()
            }
        })
        
        // Add reload/stop button integration
        setupReloadStopButton(store, lifecycleOwner)
    }

    
    /**
     * Setup reload/stop button integration
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun setupReloadStopButton(store: BrowserStore, lifecycleOwner: LifecycleOwner) {
        val toolbar = browserToolbar ?: return
        
        reloadStopIntegration = com.prirai.android.nira.integration.ReloadStopButtonIntegration(
            context = context,
            store = store,
            toolbar = toolbar,
            onReload = {
                context.components.sessionUseCases.reload()
            },
            onStop = {
                context.components.sessionUseCases.stopLoading()
            }
        )
        
        // Start the integration to observe loading state
        reloadStopIntegration?.start()
        
        // Stop when lifecycle is destroyed
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                reloadStopIntegration?.stop()
                reloadStopIntegration = null
            }
        })
    }

    /**
     * Add bottom components (tab bar and contextual) when toolbar is at TOP
     * This creates a separate bottom container for these components
     */
    private fun addBottomComponentsForTopToolbar(store: BrowserStore, lifecycleOwner: LifecycleOwner) {
        // For top toolbar mode, create components but don't add them to a container yet
        // The container will be created lazily in getBottomComponentsContainer()
        
        // Create tab group bar if enabled
        if (showTabGroupBar) {
            createTabGroupBar(store, lifecycleOwner)
        }
        
        // Always create contextual toolbar (it might be hidden)
        contextualToolbar = ContextualBottomToolbar(context).apply {
            visibility = if (showContextualToolbar) VISIBLE else GONE
        }
    }

    /**
     * Add contextual toolbar component (ContextualBottomToolbar)
     */
    private fun addContextualToolbar() {
        if (!showContextualToolbar) {
            // When contextual toolbar is disabled, don't create it
            // Buttons will be added to address bar in setup()
            return
        }

        contextualToolbar = ContextualBottomToolbar(context).apply {
            // No need to set background - it already applies Material 3 theming
        }

        toolbarSystem.addComponent(contextualToolbar!!, ModernToolbarSystem.ComponentType.CONTEXTUAL)
    }
    
    /** Toolbar anchor used by popups and browser chrome positioning. */
    fun getToolbarView(): View? = browserToolbar?.asView()

    // Count remains on the leading side of the address field.
    private var tabCountDisplayCount = 0
    private var tabCountBadgeDrawable: android.graphics.drawable.Drawable? = null

    private fun addBrowserActionsToAddressBar() {
        val toolbar = browserToolbar ?: return
        val density = resources.displayMetrics.density
        val foreground = com.google.android.material.color.MaterialColors.getColor(
            context, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.WHITE
        )
        val counter = object : android.graphics.drawable.Drawable() {
            private val pen = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun draw(canvas: android.graphics.Canvas) {
                val b = bounds
                val x = b.exactCenterX()
                val y = b.exactCenterY()
                val half = 12f * density
                pen.style = android.graphics.Paint.Style.STROKE
                pen.color = foreground
                pen.strokeWidth = 1.8f * density
                canvas.drawRoundRect(x-half, y-half, x+half, y+half, 2*density, 2*density, pen)
                pen.style = android.graphics.Paint.Style.FILL
                pen.textSize = 11f * resources.displayMetrics.scaledDensity
                pen.typeface = android.graphics.Typeface.DEFAULT_BOLD
                pen.textAlign = android.graphics.Paint.Align.CENTER
                val number = if (tabCountDisplayCount > 99) "99+" else tabCountDisplayCount.toString()
                canvas.drawText(number, x, y-(pen.ascent()+pen.descent())/2, pen)
            }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(filter: android.graphics.ColorFilter?) {}
            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        }
        tabCountBadgeDrawable = counter
        toolbar.addNavigationAction(BrowserToolbar.Button(
            imageDrawable = counter,
            contentDescription = "Otwarte karty",
            listener = { contextualToolbarListener?.onTabCountClicked() }
        ))
        fun action(icon: Int, label: String, click: () -> Unit) {
            val img = ContextCompat.getDrawable(context, icon)?.mutate() ?: return
            img.setTint(foreground)
            toolbar.addBrowserAction(BrowserToolbar.Button(
                imageDrawable = img, contentDescription = label, listener = click
            ))
        }
        // The pre-existing reload/stop integration already adds refresh first.
        action(com.prirai.android.nira.R.drawable.ic_round_add, "Nowa karta") {
            contextualToolbarListener?.onNewTabClicked()
        }
        action(android.R.drawable.ic_media_next, "Następna karta") { cycleTab(1) }
        action(android.R.drawable.ic_media_previous, "Poprzednia karta") { cycleTab(-1) }
        action(com.prirai.android.nira.R.drawable.ic_round_close, "Zamknij kartę") {
            browserStore?.state?.selectedTabId?.let { context.components.tabsUseCases.removeTab(it) }
        }
        action(com.prirai.android.nira.R.drawable.ic_ios_back, "Wstecz") {
            contextualToolbarListener?.onBackClicked()
        }
        action(com.prirai.android.nira.R.drawable.ic_ios_forward, "Do przodu") {
            contextualToolbarListener?.onForwardClicked()
        }
        action(mozilla.components.ui.icons.R.drawable.mozac_ic_ellipsis_vertical_24, "Ustawienia") {
            contextualToolbarListener?.onMenuClicked()
        }
        // Action views are real Android controls. Keep Settings reachable after
        // rotation, use a >=40dp touch target and allow deliberate long-press
        // drag reordering without rebuilding BrowserToolbar's click listeners.
        toolbar.asView().post {
            val actions = toolbar.asView().findViewById<ViewGroup>(
                mozilla.components.browser.toolbar.R.id.mozac_browser_toolbar_browser_actions
            ) ?: return@post
            val ids = mutableMapOf<View, String>()
            val customActions = listOf("new", "next", "previous", "close", "back", "forward", "settings")
            val prefixCount = (actions.childCount - customActions.size).coerceAtLeast(0)
            for (i in 0 until actions.childCount) {
                ids[actions.getChildAt(i)] =
                    if (i < prefixCount) "refresh_$i" else customActions.getOrNull(i - prefixCount) ?: "action_$i"
            }
            val preferredOrder = prefs.niraToolbarActionOrder.split(",").filter { it.isNotBlank() }
            if (preferredOrder.isNotEmpty()) {
                val desired = (preferredOrder.mapNotNull { name ->
                    ids.entries.firstOrNull { it.value == name }?.key
                } + (0 until actions.childCount).map { actions.getChildAt(it) })
                    .distinct()
                desired.forEachIndexed { index, button ->
                    if (actions.indexOfChild(button) != index) {
                        actions.removeView(button)
                        actions.addView(button, index)
                    }
                }
            }

            fun updateActionLayout() {
                val densityNow = resources.displayMetrics.density
                val slot = (prefs.niraToolbarActionSlotDp.coerceIn(40, 52) * densityNow).toInt()
                val padding = (2f * densityNow).toInt()
                for (i in 0 until actions.childCount) {
                    val child = actions.getChildAt(i)
                    if (child.layoutParams.width != slot) {
                        child.layoutParams = child.layoutParams.apply { width = slot }
                    }
                    child.minimumWidth = slot
                    child.minimumHeight = (40f * densityNow).toInt()
                    if (child.paddingLeft != padding || child.paddingRight != padding) {
                        child.setPadding(padding, child.paddingTop, padding, child.paddingBottom)
                    }
                    if (child.visibility != View.VISIBLE) child.visibility = View.VISIBLE
                }
                val width = toolbar.asView().width
                if (width == 0) return
                val reservedAddress = (prefs.niraAddressMinWidthDp.coerceIn(64, 220) * densityNow).toInt()
                val reservedCounter = (38f * densityNow).toInt()
                val availableSlots = ((width - reservedAddress - reservedCounter) / slot)
                    .coerceAtLeast(1)
                var visibleCount = actions.childCount
                // Hide secondary actions first, never Settings. Width is
                // recalculated whenever the toolbar bounds change.
                for (name in listOf("previous", "next", "forward", "close", "back")) {
                    if (visibleCount <= availableSlots) break
                    val child = ids.entries.firstOrNull { it.value == name }?.key ?: continue
                    if (child.visibility == View.VISIBLE) {
                        child.visibility = View.GONE
                        visibleCount--
                    }
                }
                // New/refresh/settings are prioritized; extremely narrow
                // layouts can still use the regular menu for other commands.
            }
            actions.setOnDragListener { _, event ->
                when (event.action) {
                    android.view.DragEvent.ACTION_DRAG_STARTED -> event.localState is View
                    android.view.DragEvent.ACTION_DROP -> {
                        val dragged = event.localState as? View ?: return@setOnDragListener false
                        if (actions.indexOfChild(dragged) < 0) return@setOnDragListener false
                        val target = (0 until actions.childCount).firstOrNull { index ->
                            val button = actions.getChildAt(index)
                            button.visibility == View.VISIBLE &&
                                event.x < button.x + button.width / 2f
                        } ?: (actions.childCount - 1)
                        val from = actions.indexOfChild(dragged)
                        if (from != target) {
                            actions.removeView(dragged)
                            actions.addView(dragged, target.coerceIn(0, actions.childCount))
                            prefs.niraToolbarActionOrder = (0 until actions.childCount)
                                .mapNotNull { ids[actions.getChildAt(it)] }.joinToString(",")
                        }
                        updateActionLayout()
                        true
                    }
                    android.view.DragEvent.ACTION_DRAG_ENDED -> true
                    else -> true
                }
            }
            for (i in 0 until actions.childCount) {
                actions.getChildAt(i).setOnLongClickListener { button ->
                    button.startDragAndDrop(
                        android.content.ClipData.newPlainText("nira-toolbar-action", ids[button] ?: ""),
                        View.DragShadowBuilder(button), button, 0
                    )
                }
            }
            var lastWidth = -1
            toolbar.asView().addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
                if (right - left != oldRight - oldLeft || lastWidth < 0) {
                    val newWidth = right - left
                    if (newWidth != lastWidth) {
                        lastWidth = newWidth
                        updateActionLayout()
                    }
                }
            }
            updateActionLayout()
        }
        observeTabCountForBadge()
    }

    private fun tabsForActiveProfile(): List<TabSessionState> {
        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(context)
        val private = profileManager.isPrivateMode()
        val active = profileManager.getActiveProfile()
        val expected = if (private) "private" else "profile_${active.id}"
        return (browserStore?.state?.tabs ?: emptyList()).filter {
            it.content.private == private &&
                (it.contextId == expected || (!private && active.id == "default" && it.contextId == null))
        }
    }

    private fun cycleTab(direction: Int) {
        val tabs = tabsForActiveProfile()
        if (tabs.size < 2) return
        val selected = browserStore?.state?.selectedTabId
        val index = tabs.indexOfFirst { it.id == selected }
        if (index < 0) return
        context.components.tabsUseCases.selectTab(tabs[(index + direction + tabs.size) % tabs.size].id)
    }

    private fun observeTabCountForBadge() {
        val store = browserStore ?: return
        val owner = context as? LifecycleOwner ?: return
        owner.lifecycleScope.launch {
            store.flowScoped(dispatcher = Dispatchers.Main) { flow ->
                flow.collect {
                    tabCountDisplayCount = tabsForActiveProfile().size
                    tabCountBadgeDrawable?.invalidateSelf()
                }
            }
        }
        tabCountDisplayCount = tabsForActiveProfile().size
        tabCountBadgeDrawable?.invalidateSelf()
    }

    /**
     * Setup scroll behavior for hiding toolbar on scroll
     */
    private fun setupScrollBehavior(parent: ViewGroup) {
        if (parent is CoordinatorLayout) {
            val toolbarPos = if (prefs.toolbarPosition == ToolbarPosition.BOTTOM.ordinal) {
                ToolbarPosition.BOTTOM
            } else {
                ToolbarPosition.TOP
            }
            
            val layoutParams = CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = when (toolbarPos) {
                    ToolbarPosition.BOTTOM -> android.view.Gravity.BOTTOM
                    ToolbarPosition.TOP -> android.view.Gravity.TOP
                }

                behavior = ModernScrollBehavior(context, null)
            }

            this.layoutParams = layoutParams
        }
    }

    /**
     * Update the contextual toolbar based on browsing context
     */
    fun updateContextualToolbar(
        tab: TabSessionState?,
        canGoBack: Boolean,
        canGoForward: Boolean,
        tabCount: Int,
        isHomepage: Boolean = false
    ) {
        contextualToolbar?.updateForContext(
            tab = tab,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            tabCount = tabCount,
            isHomepage = isHomepage
        )
    }

    /**
     * Set listener for contextual toolbar interactions
     */
    fun setContextualToolbarListener(listener: ContextualBottomToolbar.ContextualToolbarListener) {
        // Store listener for use when contextual toolbar is disabled
        contextualToolbarListener = listener
        contextualToolbar?.listener = listener
    }
    
    /**
     * Set listener for tab selection in tab bar
     */
    fun setOnTabSelectedListener(listener: (String) -> Unit) {
        onTabSelectedCallback = listener
    }

    /**
     * Set listener for toolbar expansion state changes (expanded = true, collapsed = false)
     */
    fun setOnExpansionStateChangedListener(listener: (Boolean) -> Unit) {
        onExpansionStateChanged = listener
    }
    
    /**
     * Set listener for toolbar offset changes (for smooth margin adjustment)
     * @param listener (currentOffset, totalHeight) -> Unit
     */
    fun setOnToolbarOffsetChangedListener(listener: (Int, Int) -> Unit) {
        onToolbarOffsetChanged = listener
        toolbarSystem.setOnOffsetChangedListener(listener)
    }

    /**
     * Get the browser toolbar component
     */
    fun getBrowserToolbar(): BrowserToolbar? = browserToolbar

    /**
     * Get the browser toolbar view (wraps the AC BrowserToolbar and lets
     * callers install a URL click interceptor).
     */
    fun getBrowserToolbarView(): BrowserToolbarView? = browserToolbarView

    /**
     * Get the contextual toolbar component
     */
    fun getContextualToolbar(): ContextualBottomToolbar? = contextualToolbar

    /**
     * Get the tab group bar component
     */
    fun getTabGroupBar(): ComposeTabBarWithProfileSwitcher? = tabGroupBar
    
    /**
     * Get a container with bottom components (tab bar and contextual) for TOP toolbar mode.
     * This returns a LinearLayout containing the components that should be positioned at the bottom.
     * Returns null if toolbar is not in TOP mode.
     */
    fun getBottomComponentsContainer(): ViewGroup? {
        val toolbarPos = if (prefs.toolbarPosition == ToolbarPosition.BOTTOM.ordinal) {
            ToolbarPosition.BOTTOM
        } else {
            ToolbarPosition.TOP
        }
        
        if (toolbarPos != ToolbarPosition.TOP) {
            return null
        }
        
        // Create container lazily
        if (bottomComponentsContainer == null) {
            val container = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.BOTTOM
                }
            }
            
            // Add tab bar if it exists
            tabGroupBar?.let { container.addView(it) }

            // Add contextual toolbar if it exists
            contextualToolbar?.let { container.addView(it) }
            
            bottomComponentsContainer = container
        }
        
        return bottomComponentsContainer
    }

    /**
     * Update component visibility based on settings
     */
    fun updateComponentVisibility(
        showTabBar: Boolean = showTabGroupBar,
        showContextual: Boolean = showContextualToolbar
    ) {
        showTabGroupBar = showTabBar
        showContextualToolbar = showContextual

        tabGroupBar?.visibility = if (showTabBar) VISIBLE else GONE
        contextualToolbar?.visibility = if (showContextual) VISIBLE else GONE
    }

    /** True while the toolbar is in the minimal (address-bar-only) state. */
    fun isMinimal(): Boolean = minimalState

    /**
     * Enter the minimal state: hide the tab bar and contextual toolbar so only
     * the address bar remains visible. Idempotent.
     */
    fun enterMinimalState() {
        if (minimalState) return
        minimalState = true
        animateAuxiliaryComponents(show = false)
        onMinimalStateChanged?.invoke(true)
    }

    /**
     * Exit the minimal state: re-show whichever auxiliary components the user
     * has enabled in preferences. Idempotent.
     */
    fun exitMinimalState() {
        if (!minimalState) return
        minimalState = false
        animateAuxiliaryComponents(show = true)
        onMinimalStateChanged?.invoke(false)
    }

    /**
     * Smoothly animate the tab bar and contextual toolbar in/out so the
     * engine view's reserved layout space (`swipeRefresh.bottomMargin` in
     * `BrowserFragment`) grows or shrinks **in step** with the aux bars'
     * translation.
     *
     * Previous implementation ran independent `view.animate()` chains per
     * aux view and let `withEndAction { visibility = GONE }` trigger the
     * layout-change listener. That worked but produced a visible black
     * band for the full 200 ms duration: the aux bars faded out via
     * alpha + translation, but the engine view's layout bounds stayed
     * clipped above the (now-visually-gone) chrome area because the
     * reconcile fired only once, at the END of the animation.
     *
     * Pattern copied from upstream:
     * - `ModernToolbarSystem.animateToOffset` (same repo): single
     *   `ValueAnimator` interpolates an `Int` offset, each frame it
     *   both translates its children and fires
     *   `onOffsetChangedListener(currentOffset, totalHeight)` so a
     *   downstream `BrowserFragment` can update its margins in step.
     * - AC's `mozilla.components.ui.widgets.behavior.EngineViewClippingBehavior`
     *   (`ui-widgets-153` sources): on every nested-scroll tick it
     *   updates `engineViewParent.translationY` AND calls
     *   `EngineView.setVerticalClipping(...)` so the engine view slides
     *   in lock-step with the toolbar instead of waiting for a terminal
     *   layout pass.
     *
     * Nira's pinned-toolbar design uses layout margin (not GeckoView
     * clipping) for the reservation, so we emit the per-frame offset
     * to the fragment's `adjustWebContentMarginsForToolbarOffset` which
     * updates `swipeRefresh.bottomMargin` directly. The visual effect
     * matches: everything slides together.
     *
     * Duration (200 ms) and interpolator (`AccelerateDecelerateInterpolator`,
     * the Android default) match `ModernToolbarSystem.animateToOffset`
     * so the collapse-on-fullscreen and the minimal-state transition
     * feel identical.
     */
    private fun animateAuxiliaryComponents(show: Boolean) {
        val auxiliaries = listOfNotNull(
            tabGroupBar?.takeIf { showTabGroupBar },
            contextualToolbar?.takeIf { showContextualToolbar },
        )
        if (auxiliaries.isEmpty()) return

        // Cancel any animator mid-flight; start the new one from the
        // current translation so a rapid toggle (e.g. two quick scroll
        // reversals) stays continuous rather than snapping.
        minimalAnimator?.cancel()

        // Pre-measure aux bars so `view.height` is populated when we
        // read it below. Views that are currently GONE have height 0;
        // when transitioning to visible we need the measured height
        // so we know the full extent of the slide.
        auxiliaries.forEach { it.visibility = VISIBLE }
        // Force a measure pass so freshly-made-VISIBLE views report
        // their real height before the animator reads it.
        measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )

        val auxTotalHeight = auxiliaries.sumOf { it.measuredHeight }
        if (auxTotalHeight <= 0) return

        // Address bar height is the stable "pinned" chrome height; the
        // animator interpolates between this (minimal state) and
        // `addressBarHeight + auxTotalHeight` (full chrome).
        val addressBarHeight = (toolbarSystem.measuredHeight - auxTotalHeight)
            .coerceAtLeast(0)
        val fullHeight = addressBarHeight + auxTotalHeight

        // Current translation of each aux view: 0 when visible,
        // view.height when fully hidden. We read it so cancelled
        // animations resume cleanly.
        val startTranslation = auxiliaries.first().translationY
        val targetTranslation = if (show) 0f else auxTotalHeight.toFloat()

        suppressReconcileDuringAnimation = true

        minimalAnimator = ValueAnimator.ofFloat(startTranslation, targetTranslation).apply {
            duration = ANIMATION_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animator ->
                val currentTranslation = animator.animatedValue as Float
                // How many pixels of the aux stack are currently hidden
                // (0 = fully visible, auxTotalHeight = fully hidden).
                val hiddenPx = currentTranslation.coerceIn(0f, auxTotalHeight.toFloat())
                // Visible chrome height for this frame; passed to the
                // fragment to update swipeRefresh.bottomMargin so the
                // engine view grows/shrinks in step with the aux bars.
                val visibleChrome = (fullHeight - hiddenPx).toInt().coerceAtLeast(addressBarHeight)
                // Translate each aux view individually so stacked bars
                // slide the same amount (they are siblings in a vertical
                // LinearLayout; translating the parent would move the
                // address bar too). Alpha fade is kept for a smoother
                // visual - the parent window background may show
                // through briefly during the slide, and the fade
                // disguises it the same way AC's toolbar does.
                auxiliaries.forEach { view ->
                    view.translationY = hiddenPx
                    view.alpha = 1f - (hiddenPx / auxTotalHeight.toFloat()).coerceIn(0f, 1f)
                }
                // Emit per-frame to BrowserFragment. `currentOffset` is
                // the amount hidden, `totalHeight` is the full chrome
                // height; `adjustWebContentMarginsForToolbarOffset`
                // computes `visibleHeight = totalHeight - currentOffset`.
                onToolbarOffsetChanged?.invoke(hiddenPx.toInt(), fullHeight)
            }

            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Snap to final state in a single layout pass. All
                    // of these are idempotent with what the final
                    // animator frame already applied, so no visible jump.
                    auxiliaries.forEach { view ->
                        view.translationY = 0f
                        if (show) {
                            view.alpha = 1f
                            view.visibility = VISIBLE
                        } else {
                            view.alpha = 1f // reset for next show
                            view.visibility = GONE
                        }
                    }
                    suppressReconcileDuringAnimation = false
                    // One final reconcile so the engine view sees the
                    // post-layout stable height (not the measured-but-
                    // not-laid-out value the animator was using).
                    reconcileEngineViewHeight()
                    minimalAnimator = null
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    // If we were cancelled mid-flight the new animator's
                    // `onAnimationEnd` will clear the suppress flag; do
                    // not clear it here, otherwise rapid toggles could
                    // leak reconciles between the two animations.
                }
            })

            start()
        }
    }

    /** Observe minimal-state transitions. */
    fun setOnMinimalStateChangedListener(listener: (Boolean) -> Unit) {
        onMinimalStateChanged = listener
    }

    /**
     * Set the engine view for scroll behavior integration
     */
    fun setEngineView(engine: EngineView) {
        this.engineView = engine
        toolbarSystem.setEngineView(engine)
        // The toolbar system does its own initial `setDynamicToolbarMaxHeight`
        // via a `post {}` block but does NOT set vertical clipping, so the
        // first paint on a pinned bottom toolbar would still have web
        // content extending under the chrome. Post our own reconcile to
        // the same message queue so it runs after measure settles.
        post { reconcileEngineViewHeight() }
    }

    /**
     * Tell BrowserFragment the toolbar's **current** visible height so
     * `SwipeRefreshLayout` can carve out room for the pinned chrome.
     *
     * The actual reservation happens on the fragment side (see
     * `BrowserFragment.adjustWebContentMarginsForToolbarOffset`): in
     * BOTTOM mode we set `swipeRefreshParams.bottomMargin = visibleHeight`,
     * in TOP mode we pad the top. This shrinks the engine view's layout
     * bounds so the Gecko surface itself does not draw underneath the
     * toolbar - no clipping tricks required.
     *
     * `setDynamicToolbarMaxHeight(0)` tells GeckoView there is **no**
     * dynamic chrome. Our toolbar is pinned so the entire reservation
     * happens at the Android layout level via the margin above; if we
     * were to pass the toolbar height here, GeckoView would additionally
     * push `position: fixed; bottom: 0` elements up by that height
     * **inside** our already-cropped surface, making page footers
     * (Fandom's sticky nav, cookie banners, chat bubbles) float visibly
     * above the toolbar instead of sitting flush against it.
     *
     * `setVerticalClipping` is intentionally **not** called either: it
     * tracks the dynamic toolbar's translation and is only meaningful
     * while a toolbar is in motion. For a pinned toolbar both APIs
     * must stay at 0 so GeckoView treats the (externally shrunk)
     * viewport as the full canvas.
     *
     * This function is invoked automatically from a layout-change
     * listener on `toolbarSystem` (installed in `init`), so entering or
     * exiting minimal state - which flips the aux bars between VISIBLE
     * and GONE - immediately re-coordinates the layout.
     *
     * No-op before `setEngineView` has run.
     */
    fun reconcileEngineViewHeight() {
        val engine = engineView ?: return
        val visible = toolbarSystem.getTotalHeight()
        // Zero out any dynamic-toolbar reservation; the pinned toolbar is
        // reserved purely at the layout level (see BrowserFragment's
        // adjustWebContentMarginsForToolbarOffset / bottomMargin on
        // swipeRefresh). We still emit the visible height to the
        // offset listener so the fragment can update its own
        // margin/padding bookkeeping.
        engine.setDynamicToolbarMaxHeight(0)
        onToolbarOffsetChanged?.invoke(0, visible)
    }
    
    /**
     * Apply private mode theming
     */
    fun applyPrivateMode(isPrivate: Boolean) {
        // Private mode changes the Material 3 surface color
        val bgColor = if (isPrivate) {
            // Purple for private mode
            com.prirai.android.nira.theme.ColorConstants.PrivateMode.PURPLE
        } else {
            // Check for AMOLED mode
            if (com.prirai.android.nira.theme.ThemeManager.isAmoledActive(context)) {
                // Pure black for AMOLED
                android.graphics.Color.BLACK
            } else {
                // Normal Material 3 elevated surface
                val elevationDp = 3f * resources.displayMetrics.density
                com.google.android.material.elevation.ElevationOverlayProvider(context)
                    .compositeOverlayWithThemeSurfaceColorIfNeeded(elevationDp)
            }
        }

        toolbarSystem.setBackgroundColor(bgColor)
        
        // Update status bar immediately for private mode
        val activity = context as? android.app.Activity
        if (activity != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            com.prirai.android.nira.theme.ThemeManager.applySystemBarsTheme(activity, isPrivate)
        }
    }
    
    /**
     * Update status bar color based on toolbar visibility state.
     * This prevents the toolbar from being visible through the status bar on devices
     * with transparent status bars (Android 15+, especially Realme devices).
     * 
     * @param expanded true if toolbar is visible, false if hidden
     */
    private fun updateStatusBarForToolbarState(expanded: Boolean) {
        // Only update for TOP toolbar position
        val toolbarPos = if (prefs.toolbarPosition == ToolbarPosition.BOTTOM.ordinal) {
            ToolbarPosition.BOTTOM
        } else {
            ToolbarPosition.TOP
        }
        
        if (toolbarPos != ToolbarPosition.TOP) {
            return
        }
        
        // Get the activity window
        val activity = context as? android.app.Activity ?: return
        val window = activity.window
        
        val isDark = com.prirai.android.nira.theme.ThemeManager.isDarkMode(context)
        
        if (expanded) {
            // Toolbar is visible - use semi-transparent status bar to show toolbar through it
            window.statusBarColor = if (isDark) {
                android.graphics.Color.argb(230, 28, 27, 31) // 90% opaque Material 3 dark surface
            } else {
                android.graphics.Color.argb(230, 255, 251, 254) // 90% opaque Material 3 light surface
            }
        } else {
            // Toolbar is hidden - use fully opaque status bar to hide toolbar content
            window.statusBarColor = if (isDark) {
                android.graphics.Color.argb(255, 28, 27, 31) // 100% opaque Material 3 dark surface
            } else {
                android.graphics.Color.argb(255, 255, 251, 254) // 100% opaque Material 3 light surface
            }
        }
    }

    companion object {
        /**
         * Minimal-state transition duration. Matches
         * `ModernToolbarSystem.animateToOffset`'s 200 ms so the two
         * code paths (fullscreen collapse vs. minimal toggle) feel
         * identical and AC's typical dynamic-toolbar snap window.
         */
        private const val ANIMATION_DURATION_MS: Long = 200L

        /**
         * Create and attach a unified toolbar to a parent container
         */
        fun create(
            context: Context,
            parent: ViewGroup,
            lifecycleOwner: LifecycleOwner,
            interactor: BrowserToolbarViewInteractor,
            customTabSession: CustomTabSessionState? = null,
            store: BrowserStore
        ): UnifiedToolbar {
            val toolbar = UnifiedToolbar(context)

            // Add to parent
            if (parent is CoordinatorLayout) {
                val prefs = UserPreferences(context)
                val toolbarPos = if (prefs.toolbarPosition == ToolbarPosition.BOTTOM.ordinal) {
                    ToolbarPosition.BOTTOM
                } else {
                    ToolbarPosition.TOP
                }
                
                val layoutParams = CoordinatorLayout.LayoutParams(
                    CoordinatorLayout.LayoutParams.MATCH_PARENT,
                    CoordinatorLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = when (toolbarPos) {
                        ToolbarPosition.BOTTOM -> android.view.Gravity.BOTTOM
                        ToolbarPosition.TOP -> android.view.Gravity.TOP
                    }
                }
                parent.addView(toolbar, layoutParams)
            } else {
                parent.addView(toolbar, ViewGroup.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ))
            }

            // Setup toolbar
            return toolbar.setup(
                parent,
                lifecycleOwner,
                interactor,
                customTabSession,
                store
            )
        }
    }
}
