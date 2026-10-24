package com.prirai.android.nira.browser.tabs

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import android.view.animation.PathInterpolator
import android.transition.TransitionManager
import android.transition.ChangeBounds
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.BrowsingMode
import com.prirai.android.nira.browser.BrowsingModeManager
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import com.prirai.android.nira.browser.tabs.compose.TabViewModel
import com.prirai.android.nira.browser.tabs.compose.TabSheetListView
import com.prirai.android.nira.browser.tabs.compose.TabSheetGridView
import com.prirai.android.nira.databinding.FragmentTabsBottomSheetBinding
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.lib.state.ext.flowScoped
import androidx.core.content.edit

/**
 * Modern Compose-based tab switcher displayed as a fullscreen bottom sheet.
 *
 * Features:
 * - List and grid view modes
 * - Tab groups with drag-and-drop
 * - Multi-profile support
 * - Private browsing mode
 * - Tab search
 * - Context menus for tab operations
 *
 * Architecture:
 * - UI: Jetpack Compose (TabSheetListView / TabSheetGridView)
 * - State: TabViewModel with StateFlow
 * - Groups: UnifiedTabGroupManager
 * - Tabs: Mozilla Components Store
 *
 */
class TabsBottomSheetFragment : DialogFragment() {

    private var _binding: FragmentTabsBottomSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var browsingModeManager: BrowsingModeManager
    private lateinit var unifiedGroupManager: UnifiedTabGroupManager

    private var composeOrderManager: com.prirai.android.nira.browser.tabs.compose.TabOrderManager? = null
    private var tabViewModel: TabViewModel? = null

    // Compose state management
    private val profileStateTrigger = mutableStateOf(0) // Triggers recomposition on profile/mode changes
    private val showMenuState = mutableStateOf(false) // Controls context menu visibility
    private val menuTabState = mutableStateOf<TabSessionState?>(null) // Currently selected tab for menu
    private val menuIsInGroupState = mutableStateOf(false) // Whether selected tab is in a group

    private val isSidePanel: Boolean get() = arguments?.getBoolean("nira_side_panel") == true
    private var isInitializing = true
    private val isGridViewState = mutableStateOf(false)
    private var isGridView: Boolean
        get() = isGridViewState.value
        set(value) { isGridViewState.value = value }
    private val viewPrefs by lazy {
        requireContext().getSharedPreferences("tabs_view_prefs", android.content.Context.MODE_PRIVATE)
    }

    /** ActivityResultLauncher registered at fragment creation time for profile ZIP import. */
    private val importProfileLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val exportManager = com.prirai.android.nira.browser.profile.ProfileExportManager
                    .getInstance(requireContext())
                val imported = exportManager.importProfile(uri)
                setupMergedProfileButtons()
                android.widget.Toast.makeText(
                    requireContext(),
                    "Profile \"${imported.name}\" imported",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Import failed: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        const val TAG = "TabsBottomSheet"
        fun newInstance() = TabsBottomSheetFragment()
        fun newSidePanel() = TabsBottomSheetFragment().apply {
            arguments = Bundle().apply { putBoolean("nira_side_panel", true) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTabsBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = (activity as BrowserActivity)
        browsingModeManager = activity.browsingModeManager
        unifiedGroupManager = UnifiedTabGroupManager.getInstance(requireContext())

        // Switch to the correct profile based on the selected tab's context
        val store = requireContext().components.store
        val selectedTab = store.state.tabs.find { it.id == store.state.selectedTabId }
        if (selectedTab != null) {
            val isPrivate = selectedTab.content.private
            val tabContextId = selectedTab.contextId

            // Switch to correct browsing mode
            if (isPrivate && browsingModeManager.mode != BrowsingMode.Private) {
                browsingModeManager.mode = BrowsingMode.Private
            } else if (!isPrivate && browsingModeManager.mode != BrowsingMode.Normal) {
                browsingModeManager.mode = BrowsingMode.Normal
            }

            // Switch to correct profile for normal mode
            if (!isPrivate) {
                val profileManager =
                    com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                when {
                    tabContextId == null || tabContextId == "profile_default" -> {
                        val defaultProfile = profileManager.getAllProfiles().find { it.id == "default" }
                        if (defaultProfile != null && browsingModeManager.currentProfile.id != "default") {
                            profileManager.setActiveProfile(defaultProfile)
                            browsingModeManager.currentProfile = defaultProfile
                        }
                    }

                    tabContextId.startsWith("profile_") -> {
                        val profileId = tabContextId.removePrefix("profile_")
                        val targetProfile = profileManager.getAllProfiles().find { it.id == profileId }
                        if (targetProfile != null && browsingModeManager.currentProfile.id != profileId) {
                            profileManager.setActiveProfile(targetProfile)
                            browsingModeManager.currentProfile = targetProfile
                        }
                    }
                }
            }
        }

        setupUI()

            composeOrderManager = com.prirai.android.nira.browser.tabs.compose.TabOrderManager.getInstance(
                requireContext(),
                unifiedGroupManager
            )
            setupComposeTabViews()
        setupMergedProfileButtons()
        if (isSidePanel) setupSidePanelResizeGrip()

        isInitializing = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Use fullscreen dialog style
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogTheme)
    }

    override fun onStart() {
        super.onStart()

        // Make dialog fullscreen
        dialog?.window?.let { window ->
            val side = isSidePanel
            if (side) {
                dialog?.setCanceledOnTouchOutside(true)
                isCancelable = true
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setDimAmount(0.55f)
                window.setGravity(android.view.Gravity.START or android.view.Gravity.TOP)
            }
            window.setLayout(
                if (side) sidePanelWidth()
                else ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Preserve status bar appearance (don't change icons to black)
            val isDark = com.prirai.android.nira.theme.ThemeManager.isDarkMode(requireContext())
            androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isDark
            }

            // Apply window insets for edge-to-edge
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
                val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())

                // Apply padding to profile button container to avoid navigation bar overlap
                binding.profileButtonContainer.setPadding(
                    binding.profileButtonContainer.paddingLeft,
                    binding.profileButtonContainer.paddingTop,
                    binding.profileButtonContainer.paddingRight,
                    systemBars.bottom + 16 // Add extra padding for navigation bar
                )

                insets
            }
        }
    }

    private fun setupUI() {
        // Apply Material 3 theming
        com.prirai.android.nira.theme.ThemeManager.isDarkMode(requireContext())
        val isAmoled = com.prirai.android.nira.theme.ThemeManager.isAmoledActive(requireContext())

        if (isAmoled) {
            dialog?.window?.decorView?.setBackgroundColor(Color.BLACK)
        }

        if (isSidePanel) {
            binding.profileButtonContainer.visibility = View.GONE
            // Keep these capabilities in the full tab manager and toolbar, not in the side drawer.
            (binding.newTabFab.parent?.parent as? View)?.visibility = View.GONE
        }

        binding.newTabFab.setOnClickListener {
            addNewTab()
            // Post dismiss to avoid touch event recycling crash
            view?.post { dismiss() }
        }

        binding.searchTabFab.setOnClickListener {
            showTabSearch()
        }

        // Hide XML view mode switcher — replaced by Compose SingleChoiceSegmentedButtonRow in header
        binding.viewModeSwitcher.visibility = View.GONE

        // Setup view mode switcher
        setupViewModeSwitcher()
    }

    private fun setupViewModeSwitcher() {
        // Restore saved view preference; Compose header handles the toggle UI
        isGridViewState.value = viewPrefs.getBoolean("is_grid_view", false)
    }

    private fun handleSwitchToListView() {
            setupComposeTabViews()
    }

    private fun handleSwitchToGridView() {
            setupComposeTabViews()
    }

    private fun showTabSearch() {
        val searchFragment = TabSearchFragment.newInstance()
        searchFragment.show(parentFragmentManager, TabSearchFragment.TAG)
    }

    private fun setupMergedProfileButtons() {
        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
        val profiles = profileManager.getAllProfiles()
        val isPrivateMode = browsingModeManager.mode.isPrivate
        val currentProfile = browsingModeManager.currentProfile

        binding.profileButtonGroup.removeAllViews()

        val buttons = mutableListOf<MergedProfileButton>()

        // Add profile buttons
        profiles.forEachIndexed { index, profile ->
            val button = MergedProfileButton(requireContext()).apply {
                id = View.generateViewId()
                tag = profile.id
                setText(profile.emoji, profile.name)
                setActive(!isPrivateMode && profile.id == currentProfile.id, animated = false)

                val position = when {
                    profiles.size == 1 -> MergedProfileButton.Position.ONLY
                    index == 0 -> MergedProfileButton.Position.FIRST
                    index == profiles.size - 1 -> MergedProfileButton.Position.MIDDLE
                    else -> MergedProfileButton.Position.MIDDLE
                }
                setPosition(position)

                val heightPx = (48 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    heightPx
                ).apply {
                    marginEnd = 1 // 1dp for merged appearance
                }

                setOnClickListener {
                    switchToProfile(profile.id, false)
                }

                setOnLongClickListener {
                    showProfileEditDialog(profile)
                    true
                }
            }
            buttons.add(button)
            binding.profileButtonGroup.addView(button)
        }

        // Add private button
        val privateButton = MergedProfileButton(requireContext()).apply {
            id = View.generateViewId()
            tag = "private"
            setText("🕵️", "Private")
            setActive(isPrivateMode, animated = false)
            setPosition(MergedProfileButton.Position.MIDDLE)

            val heightPx = (48 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                heightPx
            ).apply {
                marginEnd = 1
            }

            setOnClickListener {
                switchToProfile("private", true)
            }
        }
        binding.profileButtonGroup.addView(privateButton)

        // Add plus button
        val plusButton = MergedProfileButton(requireContext()).apply {
            id = View.generateViewId()
            setText("+", "")
            setActive(false, animated = false)
            setPosition(MergedProfileButton.Position.LAST)

            val heightPx = (48 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                heightPx
            )

            setOnClickListener {
                showProfileCreateDialog()
            }
        }
        binding.profileButtonGroup.addView(plusButton)
    }

    private fun switchToProfile(profileId: String, isPrivate: Boolean) {
        if (isInitializing) return

        // Animate all buttons
        val transition = ChangeBounds().apply {
            duration = 300
            interpolator = PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f)
        }
        TransitionManager.beginDelayedTransition(binding.profileButtonGroup, transition)

        for (i in 0 until binding.profileButtonGroup.childCount) {
            val child = binding.profileButtonGroup.getChildAt(i) as? MergedProfileButton
            child?.let {
                val buttonTag = it.tag as? String
                val shouldBeActive = if (isPrivate) {
                    buttonTag == "private"
                } else {
                    buttonTag == profileId
                }
                it.setActive(shouldBeActive, animated = true)
            }
        }

        // Update browsing mode and tabs
        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
        if (isPrivate) {
            if (!browsingModeManager.mode.isPrivate) {
                browsingModeManager.mode = BrowsingMode.Private
                profileManager.setPrivateMode(true)
                // Trigger Compose recomposition (it will automatically refresh via LaunchedEffect)
                profileStateTrigger.value++
            }
        } else {
            val profiles = profileManager.getAllProfiles()
            val profile = profiles.find { it.id == profileId }
            if (profile != null && (browsingModeManager.mode.isPrivate || profile.id != browsingModeManager.currentProfile.id)) {
                browsingModeManager.currentProfile = profile
                browsingModeManager.mode = BrowsingMode.Normal
                profileManager.setActiveProfile(profile)
                profileManager.setPrivateMode(false)
                // Trigger Compose recomposition (it will automatically refresh via LaunchedEffect)
                profileStateTrigger.value++
            }
        }
    }

    // updateGridDisplay / updateTabsDisplay used to feed a RecyclerView with
    // a TabGridItem list produced by the now-deleted TabsGridAdapter. The
    // grid RecyclerView is still in the layout XML but is permanently GONE
    // (see setupComposeTabViews) - the Compose tab sheet renders everything.
    // The helper methods are gone with the adapter.

    private fun showMoveToProfileDialog(tabIds: List<String>, isGroup: Boolean = false) {
        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
        val profiles = profileManager.getAllProfiles()

        val items = profiles.map { "${it.emoji} ${it.name}" }.toMutableList()
        items.add("🕵️ Private")

        val tabWord = if (isGroup) "group" else if (tabIds.size > 1) "tabs" else "tab"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Move $tabWord to Profile")
            .setItems(items.toTypedArray()) { _, which ->
                val targetProfileId = if (which == items.size - 1) {
                    "private"
                } else {
                    profiles[which].id
                }

                // Migrate tabs
                val migratedCount = profileManager.migrateTabsToProfile(tabIds, targetProfileId)

                // Show confirmation
                android.widget.Toast.makeText(
                    requireContext(),
                    "Moved $migratedCount $tabWord to ${items[which]}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addNewTab() {
        lifecycleScope.launch {
            val isPrivate = browsingModeManager.mode.isPrivate
            val currentProfile = browsingModeManager.currentProfile
            val contextId = com.prirai.android.nira.browser.profile.BrowserProfile
                .contextIdFor(currentProfile, isPrivate)

            requireContext().components.tabsUseCases.addTab(
                url = "about:homepage",
                private = isPrivate,
                contextId = contextId,
                selectTab = true
            )

            try {
                NavHostFragment.findNavController(this@TabsBottomSheetFragment)
                    .navigate(R.id.homeFragment)
                // Post dismiss to avoid touch event recycling crash
                view?.post { dismiss() }
            } catch (e: Exception) {
                view?.post { dismiss() }
            }
        }
    }

    private fun showProfileCreateDialog() {
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext())
        val userPreferences = com.prirai.android.nira.preferences.UserPreferences(requireContext())
        val themeChoice =
            com.prirai.android.nira.settings.ThemeChoice.entries.toTypedArray()[userPreferences.appThemeChoice]
        val isDark = when (themeChoice) {
            com.prirai.android.nira.settings.ThemeChoice.DARK -> true
            com.prirai.android.nira.settings.ThemeChoice.LIGHT -> false
            com.prirai.android.nira.settings.ThemeChoice.SYSTEM ->
                requireContext().resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

        composeView.setContent {
            com.prirai.android.nira.ui.theme.NiraTheme(
                darkTheme = isDark,
                isPrivateMode = browsingModeManager.mode == BrowsingMode.Private,
                amoledMode = userPreferences.amoledMode,
                dynamicColor = userPreferences.dynamicColors
            ) {
                com.prirai.android.nira.browser.profile.ProfileCreateDialog(
                    onDismiss = {
                        (composeView.parent as? ViewGroup)?.removeView(composeView)
                    },
                    onConfirm = { name, color, emoji ->
                        val profileManager =
                            com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                        val newProfile = profileManager.createProfile(name, color, emoji)

                        setupMergedProfileButtons()

                        browsingModeManager.currentProfile = newProfile
                        browsingModeManager.mode = BrowsingMode.Normal
                        (composeView.parent as? ViewGroup)?.removeView(composeView)
                    },
                    onImport = {
                        (composeView.parent as? ViewGroup)?.removeView(composeView)
                        importProfileLauncher.launch("*/*")
                    }
                )
            }
        }

        (binding.root as ViewGroup).addView(composeView)
    }

    private fun showProfileEditDialog(profile: com.prirai.android.nira.browser.profile.BrowserProfile) {
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext())
        val userPreferences = com.prirai.android.nira.preferences.UserPreferences(requireContext())
        val themeChoice =
            com.prirai.android.nira.settings.ThemeChoice.entries.toTypedArray()[userPreferences.appThemeChoice]
        val isDark = when (themeChoice) {
            com.prirai.android.nira.settings.ThemeChoice.DARK -> true
            com.prirai.android.nira.settings.ThemeChoice.LIGHT -> false
            com.prirai.android.nira.settings.ThemeChoice.SYSTEM ->
                requireContext().resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

        composeView.setContent {
            com.prirai.android.nira.ui.theme.NiraTheme(
                darkTheme = isDark,
                isPrivateMode = browsingModeManager.mode == BrowsingMode.Private,
                amoledMode = userPreferences.amoledMode,
                dynamicColor = userPreferences.dynamicColors
            ) {
                com.prirai.android.nira.browser.profile.ProfileEditDialog(
                    profile = profile,
                    onDismiss = {
                        (composeView.parent as? ViewGroup)?.removeView(composeView)
                    },
                    onConfirm = { name, color, emoji ->
                        val profileManager =
                            com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                        val updatedProfile = profile.copy(name = name, color = color, emoji = emoji)
                        profileManager.updateProfile(updatedProfile)

                        if (profile.id == browsingModeManager.currentProfile.id) {
                            browsingModeManager.currentProfile = updatedProfile
                        }

                        setupMergedProfileButtons()

                        (composeView.parent as? ViewGroup)?.removeView(composeView)
                    },
                    onDelete = {
                        val profileManager =
                            com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())

                        if (profile.id == browsingModeManager.currentProfile.id) {
                            browsingModeManager.currentProfile =
                                com.prirai.android.nira.browser.profile.BrowserProfile.getDefaultProfile()
                        }

                        profileManager.deleteProfile(profile.id)

                        setupMergedProfileButtons()
                        (composeView.parent as? ViewGroup)?.removeView(composeView)
                    },
                    onExport = {
                        (composeView.parent as? ViewGroup)?.removeView(composeView)
                        lifecycleScope.launch {
                            try {
                                val exportManager = com.prirai.android.nira.browser.profile.ProfileExportManager
                                    .getInstance(requireContext())
                                val zipUri = exportManager.exportProfile(profile)
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(android.content.Intent.EXTRA_STREAM, zipUri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(
                                    android.content.Intent.createChooser(shareIntent, "Share Profile")
                                )
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(
                                    requireContext(),
                                    "Export failed: ${e.message}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )
            }
        }

        (binding.root as ViewGroup).addView(composeView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        // Notify that the tab sheet was dismissed to trigger auto-scroll in tab bar
        com.prirai.android.nira.browser.tabs.compose.TabSheetStateManager.notifyTabSheetDismissed()
        
        _binding = null
    }

    // ============ NEW FLAT ADAPTER SYSTEM ============

    private fun setupComposeTabViews() {
        // Hide RecyclerView
        binding.tabsRecyclerView.visibility = View.GONE
        binding.tabsGridRecyclerView.visibility = View.GONE

        // Find parent container
        val container = binding.tabsRecyclerView.parent as? ViewGroup ?: return

        // Remove existing compose view if any
        container.findViewWithTag<androidx.compose.ui.platform.ComposeView>("tabs_compose")?.let {
            container.removeView(it)
        }

        // Add ComposeView
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext()).apply {
            tag = "tabs_compose"
            setContent {
                // Read AMOLED preference
                val prefs = com.prirai.android.nira.preferences.UserPreferences(requireContext())
                val isAmoledActive = prefs.amoledMode
                
                com.prirai.android.nira.ui.theme.NiraTheme(amoledMode = isAmoledActive) {
                    ComposeTabContent()
                }
            }
        }

        // Add to container with same layout params as RecyclerView
        val layoutParams = binding.tabsRecyclerView.layoutParams
        composeView.layoutParams = layoutParams
        container.addView(composeView)
    }

    @Composable
    private fun ComposeTabContent() {
        // Menu state
        val showMenu by showMenuState
        val menuTab by menuTabState
        val menuIsInGroup by menuIsInGroupState

        // Update profile button visibility based on menu state - use SideEffect for immediate execution
        androidx.compose.runtime.SideEffect {
            binding.profileButtonContainer.visibility = if (isSidePanel || showMenu) View.GONE else View.VISIBLE
        }

        // Create ViewModel once and store it. TabViewModel.closeTab() now
        // performs removal + undo via TabsUseCases + UndoMiddleware directly,
        // so onTabRemove / onTabRestore glue is no longer needed here.
        val viewModel = remember {
            TabViewModel(requireContext(), unifiedGroupManager).also {
                tabViewModel = it
            }
        }

        // Set up global snackbar manager scope
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            com.prirai.android.nira.browser.tabs.compose.GlobalSnackbarManager.getInstance().coroutineScope = scope
        }

        // Get snackbar host state from global manager
        val snackbarHostState =
            com.prirai.android.nira.browser.tabs.compose.GlobalSnackbarManager.getInstance().snackbarHostState

        // Load tabs for current profile - trigger recomposition on profile/mode changes
        val trigger by profileStateTrigger
        LaunchedEffect(trigger) {
            val store = requireContext().components.store
            val browsingMode = browsingModeManager.mode
            val currentProfile = browsingModeManager.currentProfile
            val isPrivateMode = browsingMode.isPrivate
            val profileId = if (isPrivateMode) "private" else currentProfile.id

            // Helper to filter tabs
            fun filterTabs(tabs: List<TabSessionState>) = tabs.filter { tab ->
                val tabIsPrivate = tab.content.private
                if (tabIsPrivate != isPrivateMode) {
                    false
                } else if (isPrivateMode) {
                    tab.contextId == "private"
                } else {
                    val expectedContextId = "profile_${currentProfile.id}"
                    // Default profile shows null contextId tabs, other profiles don't
                    if (currentProfile.id == "default") {
                        (tab.contextId == expectedContextId) || (tab.contextId == null)
                    } else {
                        tab.contextId == expectedContextId
                    }
                }
            }

            // Collect store updates
            launch {
                store.flowScoped(viewLifecycleOwner, Dispatchers.Main) { flow ->
                    flow.collect { state ->
                        val filteredTabs = filterTabs(state.tabs)
                        viewModel.loadTabsForProfile(profileId, filteredTabs, state.selectedTabId)
                    }
                }
            }

            // Listen to group events and trigger refresh
            launch {
                unifiedGroupManager.groupEvents.collect { event ->
                    // When groups change, force a refresh with current store state
                    val state = store.state
                    val filteredTabs = filterTabs(state.tabs)
                    viewModel.loadTabsForProfile(profileId, filteredTabs, state.selectedTabId)
                }
            }
        }

        // Use Scaffold for proper snackbar positioning
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = 80.dp) // Above bottom buttons
                )
            }
        ) { paddingValues ->
            val isGridView by isGridViewState
            val tabs by viewModel.tabs.collectAsState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Header: tab count + view mode toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val tabWord = if (tabs.size == 1) "Tab" else "Tabs"
                    Text(
                        text = "${tabs.size} $tabWord",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (!isSidePanel) SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = !isGridView,
                            onClick = {
                                isGridViewState.value = false
                                viewPrefs.edit { putBoolean("is_grid_view", false) }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Icon(Icons.Rounded.ViewList, contentDescription = "List view")
                        }
                        SegmentedButton(
                            selected = isGridView,
                            onClick = {
                                isGridViewState.value = true
                                viewPrefs.edit { putBoolean("is_grid_view", true) }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Icon(Icons.Rounded.GridView, contentDescription = "Grid view")
                        }
                    }
                }

                // Wrapper Box to hold tabs and menu overlay
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    // Choose view based on grid mode
                    if (isSidePanel) {
                        CompactSideTabs(viewModel, tabs)
                    } else if (isGridView) {
                        TabSheetGridView(
                            viewModel = viewModel,
                            orderManager = composeOrderManager!!,
                            onTabClick = ::handleTabClickCompose,
                            onTabClose = ::handleTabCloseCompose,
                            onTabLongPress = ::handleTabLongPressCompose,
                            onGroupClick = { groupId ->
                                viewModel.toggleGroupExpanded(groupId)
                            },
                            onGroupOptionsClick = { groupId ->
                                // Show group options menu - could be enhanced
                            }
                        )
                    } else {
                        TabSheetListView(
                            viewModel = viewModel,
                            orderManager = composeOrderManager!!,
                            onTabClick = ::handleTabClickCompose,
                            onTabClose = ::handleTabCloseCompose,
                            onTabLongPress = ::handleTabLongPressCompose,
                            onGroupClick = { groupId ->
                                viewModel.toggleGroupExpanded(groupId)
                            },
                            onGroupOptionsClick = { groupId ->
                                // Show group options menu - could be enhanced
                            }
                        )
                    }

                    // Menu overlay - shows at bottom when tab is long-pressed
                    if (showMenu && menuTab != null) {
                        // Clickable background to dismiss menu when clicking outside
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                ) {
                                    showMenuState.value = false
                                    menuTabState.value = null
                                }
                        )

                        TabContextMenu(
                            tab = menuTab!!,
                            isInGroup = menuIsInGroup,
                            onDismiss = {
                                showMenuState.value = false
                                menuTabState.value = null
                            },
                            onMoveToProfile = {
                                showMoveToProfileDialog(listOf(menuTab!!.id))
                                showMenuState.value = false
                                menuTabState.value = null
                            },
                            onRemoveFromGroup = {
                                lifecycleScope.launch {
                                    tabViewModel?.removeTabFromGroup(menuTab!!.id)
                                }
                                showMenuState.value = false
                                menuTabState.value = null
                            },
                            modifier = Modifier
                                .align(androidx.compose.ui.Alignment.BottomCenter)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }


    // Persist separate side-panel widths for portrait and landscape.
    private fun sidePanelWidth(): Int {
        val metrics = resources.displayMetrics
        val key = if (resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE) "width_landscape" else "width_portrait"
        val preferences = requireContext().getSharedPreferences("nira_side_panel_width", android.content.Context.MODE_PRIVATE)
        val minWidth = (220 * metrics.density).toInt().coerceAtMost(metrics.widthPixels)
        val maxWidth = (metrics.widthPixels - 48 * metrics.density).toInt().coerceAtLeast(minWidth)
        return preferences.getInt(key, (metrics.widthPixels * 0.78f).toInt()).coerceIn(minWidth, maxWidth)
    }

    private fun setupSidePanelResizeGrip() {
        val root = binding.root as ViewGroup
        val grip = View(requireContext()).apply {
            contentDescription = "Przeciągnij krawędź, aby zmienić szerokość panelu kart"
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        val params = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
            (20 * resources.displayMetrics.density).toInt(),
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply { gravity = android.view.Gravity.END }
        root.addView(grip, params)

        var downX = 0f
        var initialWidth = 0
        grip.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    initialWidth = dialog?.window?.attributes?.width?.takeIf { it > 0 } ?: sidePanelWidth()
                    true
                }
                android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP -> {
                    val screenWidth = resources.displayMetrics.widthPixels
                    val density = resources.displayMetrics.density
                    val min = (220 * density).toInt().coerceAtMost(screenWidth)
                    val max = (screenWidth - 48 * density).toInt().coerceAtLeast(min)
                    val desired = (initialWidth + event.rawX - downX).toInt().coerceIn(min, max)
                    dialog?.window?.setLayout(desired, ViewGroup.LayoutParams.MATCH_PARENT)
                    if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                        val key = if (resources.configuration.orientation ==
                            android.content.res.Configuration.ORIENTATION_LANDSCAPE) "width_landscape" else "width_portrait"
                        requireContext().getSharedPreferences("nira_side_panel_width", android.content.Context.MODE_PRIVATE)
                            .edit().putInt(key, desired).apply()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Compact side panel uses its own rows, NOT TabSheetListView. Its old
     * combinedClickable opened the menu on long press, which prevented drag.
     * Now a long press + movement reorders/moves; the visible overflow button
     * retains all context-menu actions without a competing gesture.
     */
    @Composable
    private fun CompactSideTabs(viewModel: TabViewModel, tabs: List<TabSessionState>) {
        val groups by viewModel.groups.collectAsState()
        val expanded by viewModel.expandedGroups.collectAsState()
        val selectedTabId by viewModel.selectedTabId.collectAsState()
        val savedOrder by viewModel.currentOrder.collectAsState()
        val scope = rememberCoroutineScope()
        val listState = rememberLazyListState()
        var groupMenuId by remember { mutableStateOf<String?>(null) }
        var dragTabId by remember { mutableStateOf<String?>(null) }
        var dragY by remember { mutableStateOf(0f) }
        var listTopY by remember { mutableStateOf(0f) }
        var hoveredKey by remember { mutableStateOf<String?>(null) }
        val draggedTab = tabs.firstOrNull { it.id == dragTabId }
        // Hovering a collapsed header while holding a tab opens the target group
        // without requiring the user to release the pointer first.
        LaunchedEffect(dragTabId, hoveredKey) {
            val targetId = hoveredKey?.takeIf { it.startsWith("group_") }
                ?.removePrefix("group_")
            if (dragTabId != null && targetId != null && targetId !in expanded) {
                kotlinx.coroutines.delay(450)
                if (dragTabId != null && hoveredKey == "group_" + targetId) {
                    viewModel.toggleGroupExpanded(targetId)
                }
            }
        }
        val groupedIds = groups.flatMap { it.tabIds }.toSet()
        val tabsById = tabs.associateBy { it.id }
        val groupsById = groups.associateBy { it.id }
        // Side drawer, landscape and native portrait bar share ONE persisted
        // hierarchy. Group members follow Room's committed position order.
        val entries = buildList<Pair<String, String>> {
            val seenGroups = mutableSetOf<String>()
            val seenTabs = mutableSetOf<String>()
            savedOrder?.primaryOrder?.forEach { item ->
                when (item) {
                    is com.prirai.android.nira.browser.tabs.compose.UnifiedTabOrder.OrderItem.TabGroup -> {
                        if (item.groupId in groupsById && seenGroups.add(item.groupId)) {
                            add("group" to item.groupId)
                            seenTabs.addAll(groupsById[item.groupId]?.tabIds.orEmpty())
                        }
                    }
                    is com.prirai.android.nira.browser.tabs.compose.UnifiedTabOrder.OrderItem.SingleTab -> {
                        if (item.tabId in tabsById && item.tabId !in groupedIds && seenTabs.add(item.tabId))
                            add("single" to item.tabId)
                    }
                }
            }
            groups.forEach { group ->
                if (seenGroups.add(group.id)) {
                    add("group" to group.id)
                    seenTabs.addAll(group.tabIds)
                }
            }
            tabs.forEach { tab ->
                if (tab.id !in groupedIds && seenTabs.add(tab.id)) add("single" to tab.id)
            }
        }
        val density = resources.displayMetrics.density

        // Use actual finger coordinates rather than accumulating scroll-sensitive deltas.
        fun moveDrag(pointerRootY: Float) {
            dragY = pointerRootY - listTopY
            val visible = listState.layoutInfo.visibleItemsInfo
            hoveredKey = visible.firstOrNull {
                dragY >= it.offset && dragY < it.offset + it.size
            }?.key?.toString()
            val viewport = listState.layoutInfo.viewportEndOffset
            if (dragY < 48f * density || dragY > viewport - 48f * density) {
                val speed = if (dragY < 48f * density) -15f else 15f
                scope.launch { listState.scrollBy(speed * density) }
            }
        }
        fun finishDrag() {
            val fromId = dragTabId ?: return
            val visible = listState.layoutInfo.visibleItemsInfo
            val selected = visible.firstOrNull {
                dragY >= it.offset && dragY < it.offset + it.size
            } ?: visible.minByOrNull {
                kotlin.math.abs(dragY - (it.offset + it.size / 2f))
            }
            val target = selected?.key?.toString() ?: hoveredKey
            val after = selected?.let { dragY >= it.offset + it.size / 2f } ?: true
            dragTabId = null
            hoveredKey = null
            if (target.isNullOrEmpty()) return
            val originalKey = if (fromId in groupedIds)
                "group_tab_$fromId" else "single_tab_$fromId"
            if (target == originalKey) return
            // Wait until the group manager and shared order are committed.
            scope.launch {
                try {
                    if (!viewModel.commitSidePanelDrop(fromId, target, after)) {
                        android.widget.Toast.makeText(
                            requireContext(), "Nie udało się przenieść karty",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (error: Exception) {
                    android.util.Log.e("ZavroTabDrop", "Drop failed", error)
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            userScrollEnabled = dragTabId == null,
            modifier = Modifier.fillMaxSize()
                .onGloballyPositioned { listTopY = it.positionInRoot().y },
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp)
        ) {
            entries.forEach { (kind, id) ->
                if (kind == "group") {
                    val group = groupsById[id] ?: return@forEach
                    val members = group.tabIds.mapNotNull { tabsById[it] }
                    if (members.isEmpty()) return@forEach
                    item(key = "group_" + group.id) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                                .background(
                                    if (hoveredKey == "group_" + group.id)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable { viewModel.toggleGroupExpanded(group.id) }
                                .padding(start = 8.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (group.id in expanded) Icons.Default.KeyboardArrowDown
                                else Icons.Default.KeyboardArrowRight,
                                contentDescription = "Rozwiń lub zwiń grupę",
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "(" + members.size + ") " + group.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f).padding(start = 4.dp)
                            )
                            IconButton(onClick = { groupMenuId = group.id }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Opcje grupy",
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    if (group.id in expanded) {
                        items(members, key = { "group_tab_" + it.id }) { tab ->
                            CompactSideTabRow(
                                tab = tab, ordinal = tabs.indexOf(tab) + 1,
                                selected = tab.id == selectedTabId,
                                hovered = hoveredKey == "group_tab_" + tab.id,
                                indent = true,
                                onClick = { if (dragTabId == null) handleTabClickCompose(tab.id) },
                                onClose = { handleTabCloseCompose(tab.id) },
                                onMenu = { handleTabLongPressCompose(tab, true) },
                                onDragStart = { rootY ->
                                    dragTabId = tab.id
                                    dragY = rootY - listTopY
                                },
                                onDragMove = ::moveDrag,
                                onDragFinish = ::finishDrag,
                                onDragCancel = { dragTabId = null; hoveredKey = null }
                            )
                        }
                    }
                } else {
                    val tab = tabsById[id] ?: return@forEach
                item(key = "single_tab_" + tab.id) {
                CompactSideTabRow(
                    tab = tab, ordinal = tabs.indexOf(tab) + 1,
                    selected = tab.id == selectedTabId,
                    hovered = hoveredKey == "single_tab_" + tab.id,
                    indent = false,
                    onClick = { if (dragTabId == null) handleTabClickCompose(tab.id) },
                    onClose = { handleTabCloseCompose(tab.id) },
                    onMenu = { handleTabLongPressCompose(tab, false) },
                    onDragStart = { rootY ->
                        dragTabId = tab.id
                        dragY = rootY - listTopY
                    },
                    onDragMove = ::moveDrag,
                    onDragFinish = ::finishDrag,
                    onDragCancel = { dragTabId = null; hoveredKey = null }
                )
                }
            }
        }
        }
        // The original row stays in place while dragged. An overlay follows
        // the finger so the user can see that long-press actually took hold.
        // Pointer events remain owned by the originating row, not this preview.
        if (draggedTab != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset {
                        IntOffset(0, (dragY - 23f * density).roundToInt())
                    }
                    .padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                shadowElevation = 10.dp
            ) {
                Row(
                    Modifier.height(46.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DragHandle, contentDescription = null)
                    Text(
                        draggedTab.content.title.ifBlank { "Nowa karta" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
        }
        }
        groupMenuId?.let { id ->
            com.prirai.android.nira.browser.tabs.compose.GroupContextMenu(
                groupId = id,
                groupName = groups.find { it.id == id }?.name.orEmpty(),
                onDismiss = { groupMenuId = null },
                viewModel = viewModel,
                scope = scope
            )
        }
    }

    @Composable
    private fun CompactSideTabRow(
        tab: TabSessionState,
        ordinal: Int,
        selected: Boolean,
        hovered: Boolean,
        indent: Boolean,
        onClick: () -> Unit,
        onClose: () -> Unit,
        onMenu: () -> Unit,
        onDragStart: (Float) -> Unit,
        onDragMove: (Float) -> Unit,
        onDragFinish: () -> Unit,
        onDragCancel: () -> Unit
    ) {
        var originY by remember { mutableStateOf(0f) }
        val haptic = LocalHapticFeedback.current
        val currentStart by rememberUpdatedState(onDragStart)
        val currentMove by rememberUpdatedState(onDragMove)
        val currentFinish by rememberUpdatedState(onDragFinish)
        val currentCancel by rememberUpdatedState(onDragCancel)
        Surface(
            color = when {
                hovered || selected -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(46.dp)
                    .onGloballyPositioned { originY = it.positionInRoot().y }
                    .clickable(onClick = onClick)
                    // Attach the long-press recognizer on the inside of click:
                    // normal taps still select; holding the row starts a drag.
                    .pointerInput(tab.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentStart(originY + offset.y)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                currentMove(originY + change.position.y)
                            },
                            onDragEnd = { currentFinish() },
                            onDragCancel = { currentCancel() }
                        )
                    }
                    .padding(start = if (indent) 12.dp else 3.dp, end = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ordinal.toString() + ".",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(23.dp)
                )
                // A dedicated handle also works when children (including
                // menu/close buttons) consume their own touch sequences.
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Przytrzymaj i przeciągnij kartę",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp)
                        .pointerInput(tab.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentStart(originY + offset.y)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                currentMove(originY + change.position.y)
                            },
                            onDragEnd = { currentFinish() },
                            onDragCancel = { currentCancel() }
                        )
                        }
                )
                com.prirai.android.nira.browser.tabs.compose.FaviconImage(tab = tab, size = 21.dp)
                Text(
                    text = tab.content.title.ifBlank { "Nowa karta" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opcje karty",
                        modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Zamknij kartę",
                        modifier = Modifier.size(21.dp))
                }
            }
        }
    }

    private fun handleTabClickCompose(tabId: String) {
        lifecycleScope.launch {
            // Get the tab to check its context
            val store = requireContext().components.store
            val tab = store.state.tabs.find { it.id == tabId }

            if (tab != null) {
                // Switch profile/mode if needed based on tab's context
                val isPrivate = tab.content.private
                val tabContextId = tab.contextId

                if (isPrivate) {
                    // Switch to private mode
                    if (browsingModeManager.mode != BrowsingMode.Private) {
                        browsingModeManager.mode = BrowsingMode.Private
                    }
                } else {
                    // Switch to normal mode and determine profile
                    if (browsingModeManager.mode != BrowsingMode.Normal) {
                        browsingModeManager.mode = BrowsingMode.Normal
                    }

                    // Switch profile if needed
                    val profileManager =
                        com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                    when {
                        tabContextId == null || tabContextId == "profile_default" -> {
                            // Default profile
                            val defaultProfile = profileManager.getAllProfiles().find { it.id == "default" }
                            if (defaultProfile != null && browsingModeManager.currentProfile.id != "default") {
                                profileManager.setActiveProfile(defaultProfile)
                                browsingModeManager.currentProfile = defaultProfile
                            }
                        }

                        tabContextId.startsWith("profile_") -> {
                            // Other profile
                            val profileId = tabContextId.removePrefix("profile_")
                            val targetProfile = profileManager.getAllProfiles().find { it.id == profileId }
                            if (targetProfile != null && browsingModeManager.currentProfile.id != profileId) {
                                profileManager.setActiveProfile(targetProfile)
                                browsingModeManager.currentProfile = targetProfile
                            }
                        }
                    }
                }
            }

            requireContext().components.tabsUseCases.selectTab(tabId)
            dismiss()
        }
    }

    private fun handleTabCloseCompose(tabId: String) {
        // Use ViewModel's closeTab with undo support
        // The actual deletion will be handled by the onTabDeleteConfirmed callback
        tabViewModel?.closeTab(tabId, showUndo = true)
    }

    private fun handleTabLongPressCompose(tab: TabSessionState, isInGroup: Boolean) {
        // Show menu in place of profile switcher
        menuTabState.value = tab
        menuIsInGroupState.value = isInGroup
        showMenuState.value = true
    }

    @Composable
    private fun TabContextMenu(
        tab: TabSessionState,
        isInGroup: Boolean,
        onDismiss: () -> Unit,
        onMoveToProfile: () -> Unit,
        onRemoveFromGroup: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = true,
            enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
            modifier = modifier
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    // Tab title
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = tab.content.title.ifEmpty { "Tab" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                contentDescription = "Close menu"
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // Menu options
                    if (isInGroup) {
                        MenuOption(
                            icon = androidx.compose.material.icons.Icons.Default.Clear,
                            text = "Remove from Group",
                            onClick = onRemoveFromGroup
                        )
                    }

                    MenuOption(
                        icon = androidx.compose.material.icons.Icons.Default.AccountCircle,
                        text = "Move to Profile",
                        onClick = onMoveToProfile
                    )

                    MenuOption(
                        icon = androidx.compose.material.icons.Icons.Default.Close,
                        text = "Close Tab",
                        onClick = {
                            handleTabCloseCompose(tab.id)
                            onDismiss()
                        },
                        contentColor = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    @Composable
    private fun MenuOption(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        text: String,
        onClick: () -> Unit,
        contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = text,
                    color = contentColor
                )
            },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = contentColor
                )
            },
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}
