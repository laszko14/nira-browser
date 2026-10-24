package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import com.prirai.android.nira.browser.profile.BrowserProfile
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.browser.tabs.compose.TabBarCompose
import com.prirai.android.nira.browser.tabs.compose.HybridGroupChips
import com.prirai.android.nira.browser.tabs.compose.HybridGroupTabs
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.browser.tabs.compose.TabViewModel
import com.prirai.android.nira.browser.tabs.compose.TabOrderManager
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ui.theme.NiraTheme
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.lib.state.ext.flowScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


/**
 * Compose-based tab bar with profile switcher pill.
 * Replaces the RecyclerView-based TabGroupWithProfileSwitcher.
 */
class ComposeTabBarWithProfileSwitcher @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(
            androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
    }

    private var onTabSelected: ((String) -> Unit)? = null
    private var onTabClosed: ((String) -> Unit)? = null
    private var onProfileSelected: ((BrowserProfile) -> Unit)? = null
    private var onPrivateModeSelected: (() -> Unit)? = null
    private var lastSelectedTabId: String? = null
    // Only explicit profile switches need a local refresh. BrowserStore changes
    // are observed within the composition and must not call setContent again.
    private var profileRefreshToken by mutableIntStateOf(0)
    private var lastObservedProfileSignature: Pair<String, Boolean>? = null

    private var tabOrderManager: TabOrderManager? = null
    private var groupManager: UnifiedTabGroupManager? = null
    private var tabViewModel: TabViewModel? = null
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        clipToPadding = false
        clipChildren = false
        val elevationDp = 12f * resources.displayMetrics.density
        elevation = elevationDp
        translationZ = elevationDp

        // Add compose view
        addView(
            composeView, LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        // Initialize managers
        groupManager = UnifiedTabGroupManager.getInstance(context)
        tabOrderManager = TabOrderManager.getInstance(context, groupManager!!)
        // TabViewModel.closeTab() now performs store removal directly via
        // TabsUseCases + UndoMiddleware, so no host-side onTabRemove wiring is
        // required. See TabViewModel.closeTab kdoc for the undo contract.
        tabViewModel = TabViewModel(context, groupManager!!)
    }

    fun setup(
        onTabSelected: (String) -> Unit,
        onTabClosed: (String) -> Unit,
        onProfileSelected: (BrowserProfile) -> Unit,
        onPrivateModeSelected: (() -> Unit)? = null
    ) {
        this.onTabSelected = onTabSelected
        this.onTabClosed = onTabClosed
        this.onProfileSelected = onProfileSelected
        this.onPrivateModeSelected = onPrivateModeSelected

        setupComposeContent()
    }

    private fun setupComposeContent() {
        composeView.setContent {
            // Read AMOLED preference
            val prefs = com.prirai.android.nira.preferences.UserPreferences(context)
            val isAmoledActive = prefs.amoledMode
            
            NiraTheme(amoledMode = isAmoledActive) {
                TabBarWithProfileSwitcherContent()
            }
        }
    }

    @Composable
    private fun TabBarWithProfileSwitcherContent() {
        val profileManager = remember { ProfileManager.getInstance(context) }

        // State for tabs and selected tab - observe store state changes
        val store = context.components.store
        val lifecycleOwner = context as LifecycleOwner

        // Use produceState to observe store state changes reactively
        val browserState = produceState(initialValue = store.state, store) {
            store.flowScoped(lifecycleOwner, Dispatchers.Main) { flow ->
                flow.collect { state ->
                    value = state
                }
            }
        }

        // Get current profile and mode - recalculate when browserState changes
        val currentProfile = remember(browserState.value, profileRefreshToken) { profileManager.getActiveProfile() }
        val isPrivateMode = remember(browserState.value, profileRefreshToken) { profileManager.isPrivateMode() }

        // Filter tabs based on current profile - this will update when browserState changes
        val tabs = remember(browserState.value.tabs, currentProfile, isPrivateMode) {
            browserState.value.tabs.filter { tab ->
                val tabIsPrivate = tab.content.private
                if (tabIsPrivate != isPrivateMode) {
                    false
                } else if (isPrivateMode) {
                    tab.contextId == "private"
                } else {
                    val expectedContextId = "profile_${currentProfile.id}"
                    if (currentProfile.id == "default") {
                        (tab.contextId == expectedContextId) || (tab.contextId == null)
                    } else {
                        tab.contextId == expectedContextId
                    }
                }
            }
        }

        val selectedTabId = browserState.value.selectedTabId

        val orderManager = tabOrderManager ?: return
        val viewModel = tabViewModel ?: return

        // Update orderManager and viewModel with current tabs
        LaunchedEffect(tabs, selectedTabId, currentProfile, isPrivateMode) {
            val profileId = if (isPrivateMode) "private" else currentProfile.id

            // Rebuild order to include any new tabs
            orderManager.rebuildOrderForProfile(profileId, tabs)

            viewModel.loadTabsForProfile(profileId, tabs, selectedTabId)
        }

        LaunchedEffect(selectedTabId) {
            val currentTabId = selectedTabId
            if (currentTabId != null && currentTabId != lastSelectedTabId) {
                com.prirai.android.nira.browser.tabs.compose.TabSheetStateManager.notifyTabSheetDismissed()
                lastSelectedTabId = currentTabId
            }
        }

        // Set up global snackbar manager scope
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        androidx.compose.runtime.LaunchedEffect(Unit) {
            com.prirai.android.nira.browser.tabs.compose.GlobalSnackbarManager.getInstance().coroutineScope = scope
        }

        // Observe duplicate tab events
        LaunchedEffect(viewModel, currentProfile.id, isPrivateMode) {
            viewModel.duplicateTabEvent.collect { request ->
                // Create the new tab WITHOUT selecting it. Selecting during creation
                // triggers the tab bar's auto-scroll while the order is still stale
                // (the new tab is briefly an ungrouped singleton before the group
                // membership is applied). We select once, after the order is stable.
                val components = context.components
                val newTabId = components.tabsUseCases.addTab(
                    url = request.url,
                    private = isPrivateMode,
                    contextId = com.prirai.android.nira.browser.profile.BrowserProfile
                        .contextIdFor(currentProfile, isPrivateMode),
                    selectTab = false,
                )

                // If the original tab was in a group, put the duplicate in the same
                // group before selecting it. For an ungrouped source tab,
                // TabGroupMiddleware will auto-group the pair using the parent link
                // when the new tab is added from the currently selected tab.
                if (request.groupId != null) {
                    val groupManager =
                        com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager.getInstance(context)
                    groupManager.addTabToGroup(request.groupId, newTabId)
                }

                components.tabsUseCases.selectTab(newTabId)
            }
        }

        // Create a stable key for the tab bar based on profile
        val profileKey = remember(currentProfile, isPrivateMode) {
            if (isPrivateMode) "private" else "profile_${currentProfile.id}"
        }

        // Observe tab sheet dismissal timestamp; passed directly to TabBarCompose
        // so it can scroll to the active tab after each dismissal event.
        val autoScrollTrigger by com.prirai.android.nira.browser.tabs.compose.TabSheetStateManager.dismissedTimestamp.collectAsState()

        // Hybrid grouping deliberately reuses the existing persisted groups and
        // order model. The default All mode keeps the original draggable bar.
        // Only the active group gets a lighter, scoped second row.
        key(profileKey) {
            val groups by viewModel.groups.collectAsState()
            val liveTabIds = remember(tabs) { tabs.map { it.id }.toSet() }
            val visibleGroups = remember(groups, liveTabIds) {
                groups.filter { group -> group.tabIds.any { it in liveTabIds } }
            }
            var activeGroupId by remember { mutableStateOf<String?>(null) }
            val activeGroup = visibleGroups.firstOrNull { it.id == activeGroupId }

            // A deleted/emptied group must not strand the user in an empty bar.
            LaunchedEffect(visibleGroups, activeGroupId) {
                if (activeGroupId != null && activeGroup == null) activeGroupId = null
            }
            // Navigation from outside the strip (e.g. address-bar new tab,
            // external intent) follows the selected tab rather than showing
            // a stale group while another page is on screen.
            LaunchedEffect(selectedTabId) {
                if (activeGroupId != null && selectedTabId != null &&
                    selectedTabId !in (activeGroup?.tabIds ?: emptyList())) {
                    activeGroupId = visibleGroups.firstOrNull {
                        selectedTabId in it.tabIds
                    }?.id
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                if (com.prirai.android.nira.preferences.UserPreferences(context).niraGroupChipsVisible) HybridGroupChips(
                    groups = visibleGroups,
                    tabs = tabs,
                    activeGroupId = activeGroupId,
                    onGroupSelected = { groupId ->
                        activeGroupId = groupId
                        val group = visibleGroups.firstOrNull { it.id == groupId }
                        if (group != null && selectedTabId !in group.tabIds) {
                            val target = group.tabIds.firstOrNull { it in liveTabIds }
                            if (target != null) onTabSelected?.invoke(target)
                        }
                    },
                    viewModel = viewModel
                )

                if (activeGroup != null) {
                    val scopedTabs = remember(activeGroup, tabs) {
                        activeGroup.tabIds.mapNotNull { tabId ->
                            tabs.firstOrNull { it.id == tabId }
                        }
                    }
                    HybridGroupTabs(
                        tabs = scopedTabs,
                        selectedTabId = selectedTabId,
                        groupColor = activeGroup.color,
                        height = com.prirai.android.nira.preferences.UserPreferences(context)
                            .niraTabStripHeightDp.coerceIn(40, 80).dp,
                        onTabSelected = { tabId -> onTabSelected?.invoke(tabId) },
                        onTabClosed = { tabId ->
                            // TabViewModel already dispatches removeTab and supports Undo.
                            // Do not dispatch removal a second time from the host.
                            viewModel.closeTab(tabId, showUndo = true)
                        },
                        onNewTabInGroup = {
                            scope.launch { orderManager.addNewTabToGroup(activeGroup.id) }
                        }
                    )
                } else {
                    TabBarCompose(
                        tabs = tabs,
                        viewModel = viewModel,
                        orderManager = orderManager,
                        selectedTabId = selectedTabId,
                        onTabClick = { tabId: String ->
                            onTabSelected?.invoke(tabId)
                        },
                        onTabClose = { tabId: String ->
                            // TabViewModel already dispatches removeTab and supports Undo.
                            // Do not dispatch removal a second time from the host.
                            viewModel.closeTab(tabId, showUndo = true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        autoScrollTrigger = autoScrollTrigger
                    )
                }
            }
        }
    }


    @Suppress("UNUSED_PARAMETER")
    fun updateTabs(tabs: List<TabSessionState>, selectedId: String?) {
        // BrowserStore is already collected by produceState. Repeated setContent()
        // here destroyed the ongoing composition on every tab/title/loading
        // update, briefly leaving a black, empty bottom strip.
        // A profile/private-mode switch is the only non-BrowserStore change
        // that needs explicit invalidation.
        val manager = ProfileManager.getInstance(context)
        val signature = manager.getActiveProfile().id to manager.isPrivateMode()
        if (lastObservedProfileSignature != signature) {
            lastObservedProfileSignature = signature
            profileRefreshToken++
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun updateProfileIcon(profile: BrowserProfile) {
        // ProfileManager is not a Compose State, unlike BrowserStore.
        // Invalidate its snapshot without destroying the tab strip.
        profileRefreshToken++
    }

}
