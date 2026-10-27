package com.prirai.android.nira.browser.tabs

import com.prirai.android.nira.diagnostics.testlab.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import com.prirai.android.nira.browser.tabs.compose.DragPlacement
import com.prirai.android.nira.browser.tabs.compose.DropRow
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
import androidx.compose.ui.geometry.Offset
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
import mozilla.components.lib.state.ext.flow
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
private object SideDrawerPalette {
    val backgroundTop = ComposeColor(0xFF15151B)
    val backgroundBottom = ComposeColor(0xFF1B1B28)
    val cardTop = ComposeColor(0xFF45202A)
    val cardBottom = ComposeColor(0xFF341D28)
    val activeTop = ComposeColor(0xFF6B243A)
    val activeBottom = ComposeColor(0xFF4B202D)
    val activeAccent = ComposeColor(0xFFFF2B65)
    val groupTop = ComposeColor(0xFF401E2A)
    val groupBottom = ComposeColor(0xFF291C26)
    val ungroupedTop = ComposeColor(0xFF183351)
    val ungroupedBottom = ComposeColor(0xFF14263E)
    val blue = ComposeColor(0xFF218EF5)
    val primaryText = ComposeColor(0xFFF5F0F3)
    val secondaryText = ComposeColor(0xFFAFA5AD)
}

/**
 * One source of geometry for the whole side-drawer list.
 * H is the measured row height; gaps outside LazyColumn items do not count.
 * Text keeps usable sp sizes and the subtitle disappears if two lines do not fit.
 */
internal data class SideDrawerRowGeometry(
    val height: Dp,
    val radius: Dp,
    val accent: Dp,
    val imageFrame: Dp,
    val favicon: Dp,
    val folderFrame: Dp,
    val folder: Dp,
    val chevron: Dp,
    val action: Dp,
    val actionIcon: Dp,
    val leadingGap: Dp,
    val textGap: Dp,
    val showSubtitle: Boolean
)

internal object SideDrawerHeight {
    const val MIN = 24
    const val MAX = 112
    const val STEP = 2
    const val KEY = "row_height_dp"
    const val PREFERENCES = "nira_side_drawer_height"

    fun base(landscape: Boolean): Int = if (landscape) 36 else 48

    fun validated(value: Int?): Int? =
        value?.takeIf { it in MIN..MAX && (it - MIN) % STEP == 0 }

    fun snapped(value: Float): Int =
        ((kotlin.math.round((value - MIN) / STEP).toInt() * STEP) + MIN)
            .coerceIn(MIN, MAX)

    fun geometry(heightDp: Int, landscape: Boolean, fontScale: Float): SideDrawerRowGeometry {
        val height = heightDp.coerceIn(MIN, MAX).toFloat()
        val ratio = height / base(landscape)
        fun scaled(value: Float, min: Float, max: Float): Dp =
            (value * ratio).coerceIn(min, max).coerceAtMost(height - 2f).dp
        val titleLine = if (landscape) 13f else 15f
        val subtitleLine = if (landscape) 11f else 13f
        // Approximate measured line-height in dp via system font scale.
        // Two lines have a little breathing room; 24 dp is always one line.
        val secondLineFits = height >=
            (titleLine + subtitleLine) * fontScale + 4f
        return SideDrawerRowGeometry(
            height = height.dp,
            radius = scaled(10f, 4f, 18f),
            accent = scaled(5f, 2f, 7f),
            imageFrame = scaled(if (landscape) 26f else 33f, 13f, 62f),
            favicon = scaled(if (landscape) 22f else 28f, 11f, 52f),
            folderFrame = scaled(if (landscape) 25f else 31f, 13f, 56f),
            folder = scaled(if (landscape) 19f else 23f, 10f, 42f),
            chevron = scaled(if (landscape) 16f else 19f, 10f, 28f),
            action = scaled(if (landscape) 30f else 36f, 16f, 52f),
            actionIcon = scaled(if (landscape) 17f else 19f, 11f, 27f),
            leadingGap = scaled(5f, 2f, 9f),
            textGap = scaled(if (landscape) 7f else 8f, 3f, 13f),
            showSubtitle = secondLineFits
        )
    }
}

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

    // Independent of the existing width_portrait / width_landscape preferences.
    private val sideHeightPrefs by lazy {
        requireContext().getSharedPreferences(
            SideDrawerHeight.PREFERENCES, android.content.Context.MODE_PRIVATE
        )
    }

    private fun loadSideHeight(): Int? =
        SideDrawerHeight.validated(
            runCatching { sideHeightPrefs.getInt(SideDrawerHeight.KEY, -1) }.getOrNull()
        )

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

    private var testLabSideDrag:Pair<LabCapture,OperationToken>?=null
    private var testLabResize:Pair<LabCapture,OperationToken>?=null
    private fun startTestLabDrag(group:Boolean) {
        finishTestLabDrag(OperationOutcome.INTERRUPTED)
        val capture=TestLab.capture()?:return
        val op=capture.operation(if(group)LabOperation.GROUP_DRAG else LabOperation.TAB_DRAG)?:return
        testLabSideDrag=capture to op
    }
    private fun finishTestLabDrag(outcome:OperationOutcome) {
        testLabSideDrag?.let{it.first.end(it.second,outcome)};testLabSideDrag=null
    }
    private val testLabPanel = com.prirai.android.nira.diagnostics.testlab.TestLabPanelProbe(
        com.prirai.android.nira.diagnostics.testlab.PanelSide.LEFT)
    override fun dismiss() {testLabPanel.closing();super.dismiss()}
    override fun onCancel(dialog:android.content.DialogInterface) {testLabPanel.cancelled();super.onCancel(dialog)}
    override fun onDismiss(dialog:android.content.DialogInterface) {testLabPanel.dismissed();super.onDismiss(dialog)}
    override fun onStop() {testLabPanel.stop();super.onStop()}

    override fun onStart() {
        super.onStart()
        if (isSidePanel) dialog?.window?.let(testLabPanel::start)

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
            // Only the landscape reference has the pre-existing decorative handle.
            // This is NOT the horizontal resize grip or a new gesture.
            binding.dragHandle.visibility = if (resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE) View.VISIBLE else View.GONE
            (binding.root as ViewGroup).getChildAt(0)?.setBackgroundColor(
                android.graphics.Color.rgb(21, 21, 28)
            )
            // Keep profiles, private mode and search reachable through the
            // unchanged full manager and toolbar, rather than deleting them.
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

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isSidePanel) {
            dialog?.window?.setLayout(sidePanelWidth(), ViewGroup.LayoutParams.MATCH_PARENT)
            binding.dragHandle.visibility = if (newConfig.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        finishTestLabDrag(OperationOutcome.INTERRUPTED)
        testLabResize?.let{it.first.end(it.second,OperationOutcome.INTERRUPTED)};testLabResize=null
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
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
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
        val modelStore = remember { androidx.lifecycle.ViewModelStore() }
        val viewModel = remember {
            androidx.lifecycle.ViewModelProvider(modelStore,
                object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        TabViewModel(requireContext(), unifiedGroupManager) as T
                })[TabViewModel::class.java].also { tabViewModel = it }
        }
        DisposableEffect(modelStore) {
            onDispose { modelStore.clear() }
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
                store.flow(viewLifecycleOwner).collect { state ->
                    val filteredTabs = filterTabs(state.tabs)
                    viewModel.loadTabsForProfile(profileId, filteredTabs, state.selectedTabId)
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
            containerColor = if (isSidePanel) ComposeColor.Transparent else MaterialTheme.colorScheme.surface,
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
                    .then(if (isSidePanel) Modifier
                        .background(Brush.verticalGradient(listOf(
                            SideDrawerPalette.backgroundTop,
                            SideDrawerPalette.backgroundBottom
                        )))
                        // Keep the 20 dp edge-resize strip separate from row actions.
                        .padding(end = 20.dp)
                        else Modifier)
            ) {
                // Manual override is shared between orientations; when absent,
                // portrait and landscape retain the independently matched C/D bases.
                val landscape = LocalConfiguration.current.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
                var manualRowHeight by remember {
                    mutableStateOf(if (isSidePanel) loadSideHeight() else null)
                }
                val effectiveRowHeight = manualRowHeight ?: SideDrawerHeight.base(landscape)
                var showHeightMenu by remember { mutableStateOf(false) }
                if (isSidePanel) {
                    Row(
                        Modifier.fillMaxWidth().padding(
                            start = 12.dp, end = 5.dp,
                            top = if (landscape) 2.dp else 9.dp,
                            bottom = if (landscape) 3.dp else 7.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Tabs & Groups",
                                fontSize = if (landscape) 14.sp else 17.sp,
                                lineHeight = if (landscape) 18.sp else 21.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SideDrawerPalette.primaryText
                            )
                            Text(
                                "${tabs.size} ${if (tabs.size == 1) "Tab" else "Tabs"}",
                                fontSize = if (landscape) 10.sp else 12.sp,
                                lineHeight = if (landscape) 12.sp else 15.sp,
                                color = SideDrawerPalette.secondaryText
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showHeightMenu = true },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = "Ustawienia wysokości kart i grup",
                                    tint = SideDrawerPalette.primaryText,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showHeightMenu,
                                onDismissRequest = { showHeightMenu = false },
                                modifier = Modifier.background(SideDrawerPalette.backgroundBottom)
                            ) {
                                Column(Modifier.width(198.dp).padding(horizontal = 10.dp, vertical = 7.dp)) {
                                    Text(
                                        "Wysokość kart i grup",
                                        fontSize = 13.sp,
                                        color = SideDrawerPalette.primaryText
                                    )
                                    Text(
                                        "$effectiveRowHeight dp",
                                        fontSize = 12.sp,
                                        color = SideDrawerPalette.secondaryText
                                    )
                                    Slider(
                                        value = effectiveRowHeight.toFloat(),
                                        onValueChange = { manualRowHeight = SideDrawerHeight.snapped(it) },
                                        onValueChangeFinished = {
                                            manualRowHeight?.let { selected ->
                                                sideHeightPrefs.edit()
                                                    .putInt(SideDrawerHeight.KEY, selected).apply()
                                            }
                                        },
                                        valueRange = 24f..112f,
                                        steps = 43,
                                        modifier = Modifier.fillMaxWidth().semantics {
                                            contentDescription = "Wysokość kart i grup w dp"
                                        }
                                    )
                                    TextButton(
                                        onClick = {
                                            manualRowHeight = null
                                            sideHeightPrefs.edit().remove(SideDrawerHeight.KEY).apply()
                                        },
                                        enabled = manualRowHeight != null
                                    ) { Text("Przywróć domyślną") }
                                }
                            }
                        }
                    }
                } else {
                // The normal (non-side) manager keeps its existing header.
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

                }
                // Wrapper Box to hold tabs and menu overlay
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    // Choose view based on grid mode
                    if (isSidePanel) {
                        CompactSideTabs(viewModel, tabs, effectiveRowHeight)
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
                if (isSidePanel) {
                    val landscape = LocalConfiguration.current.orientation ==
                        android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    val footerBrush = if (landscape) {
                        Brush.horizontalGradient(listOf(
                            ComposeColor(0xFF8871D9), ComposeColor(0xFF366CF4)
                        ))
                    } else {
                        Brush.horizontalGradient(listOf(
                            ComposeColor(0xFFC52351), ComposeColor(0xFF7F284A)
                        ))
                    }
                    Box(
                        // Scaffold's content padding already includes the dialog's
                        // navigation-bar inset; don't reserve it twice.
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 11.dp, end = 8.dp, top = 9.dp, bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (landscape) 42.dp else 48.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                .background(footerBrush)
                                .clickable {
                                    addNewTab()
                                    view?.post { dismiss() }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null,
                                tint = ComposeColor.White, modifier = Modifier.size(23.dp))
                            Text("Nowa karta", color = ComposeColor.White,
                                fontSize = if (landscape) 12.sp else 14.sp,
                                lineHeight = if (landscape) 16.sp else 18.sp,
                                modifier = Modifier.padding(start = 8.dp))
                        }
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
                    testLabResize?.let{it.first.end(it.second,OperationOutcome.INTERRUPTED)}
                    val capture=TestLab.capture();val op=capture?.operation(LabOperation.PANEL_RESIZE)
                    testLabResize=if(capture!=null&&op!=null)capture to op else null
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
                        val measurement=testLabResize;testLabResize=null
                        if(desired==initialWidth)measurement?.let{it.first.end(it.second,OperationOutcome.CANCELLED)}
                        else if(measurement!=null)dialog?.window?.decorView?.let{decor->
                            androidx.core.view.OneShotPreDrawListener.add(decor){measurement?.let{it.first.end(it.second,proof=CompletionProof.FIRST_PREDRAW)}}
                        }
                        val key = if (resources.configuration.orientation ==
                            android.content.res.Configuration.ORIENTATION_LANDSCAPE) "width_landscape" else "width_portrait"
                        requireContext().getSharedPreferences("nira_side_panel_width", android.content.Context.MODE_PRIVATE)
                            .edit().putInt(key, desired).apply()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    testLabResize?.let{it.first.end(it.second,OperationOutcome.CANCELLED)};testLabResize=null
                    false
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
    private fun CompactSideTabs(viewModel: TabViewModel, tabs: List<TabSessionState>, rowHeightDp: Int) {
        val publishedGroups by unifiedGroupManager.groupsState.collectAsState()
        val activeProfileId by viewModel.currentProfileId.collectAsState()
        // The ViewModel can briefly lag while a BrowserStore refresh cancels an
        // older loadJob. Use the group's published DB state for the side panel
        // so a second real group never disappears into "ungrouped" rows.
        val visibleTabIds = tabs.map { it.id }.toSet()
        val liveGroups = publishedGroups.filter { group ->
            val correctProfile = when (activeProfileId) {
                "default" -> group.contextId == null || group.contextId == "profile_default"
                "private" -> group.contextId == "private"
                null -> false
                else -> group.contextId == "profile_${activeProfileId}"
            }
            correctProfile && group.tabIds.any { it in visibleTabIds }
        }
        val groups = liveGroups
        val expanded by viewModel.expandedGroups.collectAsState()
        val selectedTabId by viewModel.selectedTabId.collectAsState()
        val savedOrder by viewModel.currentOrder.collectAsState()
        val scope = rememberCoroutineScope()
        val listState = rememberLazyListState()
        val labScroll=remember {TestLabNestedScrollProbe()}
        DisposableEffect(labScroll){onDispose{labScroll.close()}}
        var groupMenuId by remember { mutableStateOf<String?>(null) }
        var showNameUngroupedDialog by remember { mutableStateOf(false) }
        var ungroupedName by remember { mutableStateOf("Nowa grupa") }
        var ungroupedExpanded by remember { mutableStateOf(true) }
        val orientation = LocalConfiguration.current.orientation
        val landscape = orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val rowGeometry = SideDrawerHeight.geometry(rowHeightDp, landscape, LocalDensity.current.fontScale)
        var dragTabId by remember(activeProfileId, orientation) { mutableStateOf<String?>(null) }
        var dragGroupId by remember(activeProfileId, orientation) { mutableStateOf<String?>(null) }
        var dragProfileId by remember(activeProfileId, orientation) { mutableStateOf<String?>(null) }
        var dragY by remember { mutableStateOf(0f) }
        var listTopY by remember { mutableStateOf(0f) }
        var listLeftX by remember { mutableStateOf(0f) }
        var listWidth by remember { mutableStateOf(0f) }
        var dragX by remember { mutableStateOf(0f) }
        var hoveredKey by remember { mutableStateOf<String?>(null) }
        val draggedTab = tabs.firstOrNull { it.id == dragTabId }
        val draggedGroup = groups.firstOrNull { it.id == dragGroupId }
        // Hovering a collapsed header while holding a tab opens the target group
        // without requiring the user to release the pointer first.
        LaunchedEffect(dragTabId, hoveredKey) {
            val targetId = hoveredKey?.takeIf { it.startsWith("group_") && !it.startsWith("group_tab_") }
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
        val ungroupedTabs = buildList {
            val added = mutableSetOf<String>()
            savedOrder?.primaryOrder?.forEach { item ->
                if (item is com.prirai.android.nira.browser.tabs.compose.UnifiedTabOrder.OrderItem.SingleTab &&
                    item.tabId !in groupedIds && added.add(item.tabId)) {
                    tabsById[item.tabId]?.let { add(it) }
                }
            }
            tabs.forEach { if (it.id !in groupedIds && added.add(it.id)) add(it) }
        }
        // Side drawer, landscape and native portrait bar share ONE persisted
        // hierarchy. Group members follow Room's committed position order.
        val entries = buildList<Pair<String, String>> {
            val seenGroups = mutableSetOf<String>()
            val seenTabs = mutableSetOf<String>()
            var recoveryHeaderAdded = false
            fun addUngroupedHeader() {
                if (!recoveryHeaderAdded && (ungroupedTabs.isNotEmpty() || dragTabId != null)) {
                    add("ungrouped_header" to "ungrouped")
                    recoveryHeaderAdded = true
                }
            }
            savedOrder?.primaryOrder?.forEach { item ->
                when (item) {
                    is com.prirai.android.nira.browser.tabs.compose.UnifiedTabOrder.OrderItem.TabGroup -> {
                        if (item.groupId in groupsById && seenGroups.add(item.groupId)) {
                            add("group" to item.groupId)
                            seenTabs.addAll(groupsById[item.groupId]?.tabIds.orEmpty())
                        }
                    }
                    is com.prirai.android.nira.browser.tabs.compose.UnifiedTabOrder.OrderItem.SingleTab -> {
                        if (item.tabId in tabsById && item.tabId !in groupedIds && seenTabs.add(item.tabId)) {
                            addUngroupedHeader()
                            add("single" to item.tabId)
                        }
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
                if (tab.id !in groupedIds && seenTabs.add(tab.id)) {
                    addUngroupedHeader()
                    add("single" to tab.id)
                }
            }
            addUngroupedHeader()
        }
        val density = resources.displayMetrics.density

        // Use actual finger coordinates rather than accumulating scroll-sensitive deltas.
        fun moveDrag(pointerRoot: Offset) {
            dragY = pointerRoot.y - listTopY
            dragX = pointerRoot.x - listLeftX
            val visible = listState.layoutInfo.visibleItemsInfo
            hoveredKey = visible.firstOrNull {
                dragY >= it.offset && dragY < it.offset + it.size
            }?.key?.toString()
        }
        // One bounded scroll job follows the frame clock while the pointer stays
        // inside the edge zone. No competing scroll jobs per pointer event.
        LaunchedEffect(dragTabId, dragGroupId, dragY, dragX) {
            if (dragTabId == null && dragGroupId == null) return@LaunchedEffect
            if (dragX < 0f || dragX >= listWidth) return@LaunchedEffect
            while (true) {
                val viewport = listState.layoutInfo.viewportEndOffset.toFloat()
                if (dragY < 0f || dragY >= viewport) break
                val edge = 48f * density
                val velocity = when {
                    dragY < edge -> -8f * density * (1f - dragY / edge)
                    dragY > viewport - edge -> 8f * density * (1f - (viewport - dragY) / edge)
                    else -> break
                }
                androidx.compose.runtime.withFrameNanos { }
                if (listState.scrollBy(velocity) == 0f) break
            }
        }
        fun finishDrag() {
            val lab=testLabSideDrag;testLabSideDrag=null
            fun cancelled(){lab?.let{it.first.end(it.second,OperationOutcome.CANCELLED)}}
            val fromId = dragTabId
            val fromGroupId = dragGroupId
            val expectedProfile = dragProfileId
            val info = listState.layoutInfo
            val resolved = DragPlacement.sideTarget(
                dragY, 0, info.viewportEndOffset,
                info.visibleItemsInfo.map { DropRow(it.key.toString(), it.offset, it.size) },
                x = dragX, viewportWidth = listWidth
            )
            dragTabId = null
            dragGroupId = null
            hoveredKey = null
            val target = resolved?.key ?: run{cancelled();return}
            val after = resolved.after
            if (fromId == null && fromGroupId == null) {cancelled();return}
            val originalKey = if (fromId != null) {
                if (fromId in groupedIds) "group_tab_$fromId" else "single_tab_$fromId"
            } else "group_$fromGroupId"
            if (target == originalKey) {cancelled();return}
            scope.launch {
                try {
                    val committed = if (fromId != null) {
                        viewModel.commitSidePanelDrop(fromId, target, after, expectedProfile)
                    } else {
                        viewModel.commitSidePanelGroupDrop(fromGroupId!!, target, after, expectedProfile)
                    }
                    lab?.let{it.first.end(it.second,if(committed)OperationOutcome.SUCCESS else OperationOutcome.CANCELLED)}
                    if (!committed && isAdded) android.widget.Toast.makeText(
                        requireContext(), "Nie udało się przenieść elementu", android.widget.Toast.LENGTH_SHORT
                    ).show()
                } catch (error: kotlinx.coroutines.CancellationException) {
                    lab?.let{it.first.end(it.second,OperationOutcome.INTERRUPTED)}
                    throw error
                } catch (error: Exception) {
                    lab?.let{it.first.end(it.second,OperationOutcome.FAILED)}
                    android.util.Log.e("ZavroTabDrop", "Drop failed", error)
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            userScrollEnabled = dragTabId == null && dragGroupId == null,
            modifier = Modifier.fillMaxSize().nestedScroll(labScroll)
                .onGloballyPositioned {
                    listTopY = it.positionInRoot().y
                    listLeftX = it.positionInRoot().x
                    listWidth = it.size.width.toFloat()
                },
            contentPadding = PaddingValues(start = 11.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            entries.forEach { (kind, id) ->
                if (kind == "group") {
                    val group = groupsById[id] ?: return@forEach
                    val members = group.tabIds.mapNotNull { tabsById[it] }
                    if (members.isEmpty()) return@forEach
                    item(key = "group_" + group.id) {
                        var headerRoot by remember(group.id) { mutableStateOf(Offset.Zero) }
                        DrawerGroupHeader(
                            title = group.name,
                            count = members.size,
                            expanded = group.id in expanded,
                            ungrouped = false,
                            hovered = hoveredKey == "group_" + group.id,
                            geometry = rowGeometry,
                            onClick = { viewModel.toggleGroupExpanded(group.id) },
                            onMenu = { groupMenuId = group.id },
                            dragModifier = Modifier
                                .onGloballyPositioned { headerRoot = it.positionInRoot() }
                                .pointerInput(group.id, activeProfileId, orientation, rowHeightDp) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            startTestLabDrag(true)
                                            dragGroupId = group.id
                                            dragTabId = null
                                            dragProfileId = activeProfileId
                                            dragY = headerRoot.y + offset.y - listTopY
                                            dragX = headerRoot.x + offset.x - listLeftX
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            moveDrag(headerRoot + change.position)
                                        },
                                        onDragEnd = ::finishDrag,
                                        onDragCancel = {
                                            finishTestLabDrag(OperationOutcome.CANCELLED)
                                            dragGroupId = null
                                            hoveredKey = null
                                        }
                                    )
                                }
                        )
                    }
                    if (group.id in expanded) {
                        items(members, key = { "group_tab_" + it.id }) { tab ->
                            CompactSideTabRow(
                                tab = tab, ordinal = tabs.indexOf(tab) + 1,
                                selected = tab.id == selectedTabId,
                                hovered = hoveredKey == "group_tab_" + tab.id,
                                indent = true,
                                geometry = rowGeometry,
                                onClick = { if (dragTabId == null) handleTabClickCompose(tab.id) },
                                onClose = { handleTabCloseCompose(tab.id) },
                                onMenu = { handleTabLongPressCompose(tab, true) },
                                onDragStart = { root ->
                                    startTestLabDrag(false)
                                    dragTabId = tab.id
                                    dragGroupId = null
                                    dragProfileId = activeProfileId
                                    dragY = root.y - listTopY
                                    dragX = root.x - listLeftX
                                },
                                onDragMove = ::moveDrag,
                                onDragFinish = ::finishDrag,
                                onDragCancel = { finishTestLabDrag(OperationOutcome.CANCELLED);dragTabId = null; hoveredKey = null }
                            )
                        }
                    }
                } else if (kind == "ungrouped_header") {
                    item(key = "ungrouped_header") {
                        DrawerGroupHeader(
                            title = "Bez grupy",
                            count = ungroupedTabs.size,
                            expanded = ungroupedExpanded,
                            ungrouped = true,
                            hovered = hoveredKey == "ungrouped_header",
                            geometry = rowGeometry,
                            onClick = { ungroupedExpanded = !ungroupedExpanded },
                            onMenu = {
                                ungroupedName = "Nowa grupa"
                                showNameUngroupedDialog = true
                            }
                        )
                    }
                } else if (ungroupedExpanded) {
                    val tab = tabsById[id] ?: return@forEach
                item(key = "single_tab_" + tab.id) {
                CompactSideTabRow(
                    tab = tab, ordinal = tabs.indexOf(tab) + 1,
                    selected = tab.id == selectedTabId,
                    hovered = hoveredKey == "single_tab_" + tab.id,
                    indent = false,
                    geometry = rowGeometry,
                    onClick = { if (dragTabId == null) handleTabClickCompose(tab.id) },
                    onClose = { handleTabCloseCompose(tab.id) },
                    onMenu = { handleTabLongPressCompose(tab, false) },
                    onDragStart = { root ->
                        startTestLabDrag(false)
                        dragTabId = tab.id
                        dragGroupId = null
                        dragProfileId = activeProfileId
                        dragY = root.y - listTopY
                                    dragX = root.x - listLeftX
                    },
                    onDragMove = ::moveDrag,
                    onDragFinish = ::finishDrag,
                    onDragCancel = { finishTestLabDrag(OperationOutcome.CANCELLED);dragTabId = null; hoveredKey = null }
                )
                }
            }
        }
        }
        // The original row stays in place while dragged. An overlay follows
        // the finger so the user can see that long-press actually took hold.
        // Pointer events remain owned by the originating row, not this preview.
        if (draggedGroup != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().offset {
                    IntOffset(0, (dragY - rowHeightDp * density / 2f).roundToInt())
                },
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text("(${draggedGroup.tabIds.count { it in visibleTabIds }}) ${draggedGroup.name}",
                    modifier = Modifier.padding(12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (draggedTab != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset {
                        IntOffset(0, (dragY - rowHeightDp * density / 2f).roundToInt())
                    }
                    .padding(horizontal = 8.dp),
                color = SideDrawerPalette.cardTop,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                shadowElevation = 10.dp
            ) {
                Row(
                    Modifier.height(rowGeometry.height)
                        .padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.prirai.android.nira.browser.tabs.compose.FaviconImage(
                        tab = draggedTab, size = rowGeometry.favicon
                    )
                    Text(
                        draggedTab.content.title.ifBlank { "Nowa karta" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 10.dp),
                        color = SideDrawerPalette.primaryText
                    )
                }
            }
        }
        }
        if (showNameUngroupedDialog) {
            AlertDialog(
                onDismissRequest = { showNameUngroupedDialog = false },
                title = { Text("Utwórz grupę z ${ungroupedTabs.size} kart") },
                text = {
                    Column {
                        Text("Te karty nie mają aktywnej grupy. Nadaj jej nazwę, aby zapisać je jako jedną grupę.")
                        OutlinedTextField(
                            value = ungroupedName,
                            onValueChange = { ungroupedName = it.take(70) },
                            label = { Text("Nazwa grupy") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = ungroupedName.isNotBlank() && ungroupedTabs.isNotEmpty(),
                        onClick = {
                            val ids = ungroupedTabs.map { it.id }
                            val chosenName = ungroupedName.trim()
                            showNameUngroupedDialog = false
                            scope.launch {
                                val ok = viewModel.createNamedGroupFromUngrouped(ids, chosenName)
                                if (!ok) {
                                    android.widget.Toast.makeText(
                                        requireContext(),
                                        "Karty zmieniły grupę. Otwórz panel ponownie.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    ) { Text("Utwórz grupę") }
                },
                dismissButton = {
                    TextButton(onClick = { showNameUngroupedDialog = false }) {
                        Text("Anuluj")
                    }
                }
            )
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
    private fun DrawerGroupHeader(
        title: String,
        count: Int,
        expanded: Boolean,
        ungrouped: Boolean,
        hovered: Boolean,
        geometry: SideDrawerRowGeometry,
        onClick: () -> Unit,
        onMenu: () -> Unit,
        dragModifier: Modifier = Modifier
    ) {
        val landscape = LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val accent = if (ungrouped) SideDrawerPalette.blue else SideDrawerPalette.activeAccent
        val background = if (ungrouped) {
            Brush.horizontalGradient(listOf(
                SideDrawerPalette.ungroupedTop, SideDrawerPalette.ungroupedBottom
            ))
        } else {
            Brush.horizontalGradient(listOf(
                SideDrawerPalette.groupTop, SideDrawerPalette.groupBottom
            ))
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(geometry.height)
                .then(dragModifier)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(geometry.radius))
                .background(background)
                .then(if (hovered) Modifier.border(
                    1.dp, accent, androidx.compose.foundation.shape.RoundedCornerShape(geometry.radius)
                ) else Modifier)
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = "$title, $count kart, ${if (expanded) "rozwinięta" else "zwinięta"}"
                }
                .padding(start = geometry.leadingGap, end = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = if (expanded) "Zwiń grupę" else "Rozwiń grupę",
                tint = accent,
                modifier = Modifier.size(geometry.chevron)
            )
            Box(
                Modifier.padding(start = geometry.leadingGap).size(geometry.folderFrame)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(geometry.radius * 0.7f))
                    .background(accent.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = accent,
                    modifier = Modifier.size(geometry.folder))
            }
            Column(
                Modifier.weight(1f).padding(start = geometry.textGap),
                verticalArrangement = Arrangement.Center
            ) {
                Text(title, color = SideDrawerPalette.primaryText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (landscape) 11.sp else 13.sp,
                    lineHeight = if (landscape) 13.sp else 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (geometry.showSubtitle) {
                    Text("$count ${if (count == 1) "karta" else if (count in 2..4) "karty" else "kart"}",
                        color = SideDrawerPalette.secondaryText,
                        fontSize = if (landscape) 9.sp else 10.sp,
                        lineHeight = if (landscape) 11.sp else 13.sp,
                        maxLines = 1)
                }
            }
            Box(
                Modifier.size(geometry.action).clickable(onClick = onMenu),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Opcje grupy $title",
                    tint = SideDrawerPalette.primaryText,
                    modifier = Modifier.size(geometry.actionIcon))
            }
        }
    }

    @Composable
    private fun CompactSideTabRow(
        tab: TabSessionState,
        ordinal: Int,
        selected: Boolean,
        hovered: Boolean,
        indent: Boolean,
        geometry: SideDrawerRowGeometry,
        onClick: () -> Unit,
        onClose: () -> Unit,
        onMenu: () -> Unit,
        onDragStart: (Offset) -> Unit,
        onDragMove: (Offset) -> Unit,
        onDragFinish: () -> Unit,
        onDragCancel: () -> Unit
    ) {
        // Retain the existing row API, but never draw numbering absent from C/D.
        @Suppress("UNUSED_VARIABLE")
        val unusedOrdinal = ordinal
        var rowOrigin by remember { mutableStateOf(Offset.Zero) }
        val orientation = LocalConfiguration.current.orientation
        val landscape = orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val haptic = LocalHapticFeedback.current
        val currentStart by rememberUpdatedState(onDragStart)
        val currentMove by rememberUpdatedState(onDragMove)
        val currentFinish by rememberUpdatedState(onDragFinish)
        val currentCancel by rememberUpdatedState(onDragCancel)
        val rowShape = androidx.compose.foundation.shape.RoundedCornerShape(geometry.radius)
        val rowBrush = Brush.horizontalGradient(
            if (selected) listOf(SideDrawerPalette.activeTop, SideDrawerPalette.activeBottom)
            else listOf(SideDrawerPalette.cardTop, SideDrawerPalette.cardBottom)
        )
        val host = remember(tab.content.url) {
            runCatching { android.net.Uri.parse(tab.content.url).host }.getOrNull()
                ?.removePrefix("www.")
                ?: tab.content.url.takeIf { it.startsWith("about:") }.orEmpty()
        }
        val imageSize = geometry.imageFrame
        Row(
            modifier = Modifier.fillMaxWidth()
                .height(geometry.height)
                .clip(rowShape)
                .background(rowBrush)
                .then(if (selected || hovered) Modifier.border(
                    1.dp,
                    if (selected) SideDrawerPalette.activeAccent.copy(alpha = 0.55f)
                    else SideDrawerPalette.activeAccent.copy(alpha = 0.32f),
                    rowShape
                ) else Modifier)
                .onGloballyPositioned { rowOrigin = it.positionInRoot() }
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = "Karta ${tab.content.title.ifBlank { "Nowa karta" }}, $host"
                }
                .pointerInput(tab.id, orientation, geometry.height) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentStart(rowOrigin + offset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentMove(rowOrigin + change.position)
                        },
                        onDragEnd = { currentFinish() },
                        onDragCancel = { currentCancel() }
                    )
                }
                .padding(end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Box(Modifier.width(geometry.accent).fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(
                        SideDrawerPalette.activeAccent,
                        ComposeColor(0xFFDE174A)
                    ))))
            } else {
                Spacer(Modifier.width(geometry.accent))
            }
            Spacer(Modifier.width(
                if (indent) geometry.leadingGap + 2.dp else geometry.leadingGap
            ))
            // Favicon/thumbnail is also the unobtrusive context-menu entry point.
            // Long-press on the REST of the row still starts the original reorder.
            Box(
                modifier = Modifier.size(imageSize)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(geometry.radius * 0.6f))
                    .background(ComposeColor.White.copy(alpha = 0.08f))
                    .semantics { contentDescription = "Opcje karty: ${tab.content.title}" }
                    .clickable(onClick = onMenu),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    com.prirai.android.nira.browser.tabs.compose.ThumbnailImageView(
                        tab = tab, modifier = Modifier.fillMaxSize()
                    )
                } else {
                    com.prirai.android.nira.browser.tabs.compose.FaviconImage(
                        tab = tab, size = geometry.favicon
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = geometry.textGap),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = tab.content.title.ifBlank { "Nowa karta" },
                    color = SideDrawerPalette.primaryText,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = if (landscape) 11.sp else 12.sp,
                    lineHeight = if (landscape) 13.sp else 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (host.isNotBlank() && geometry.showSubtitle) {
                    Text(host, color = SideDrawerPalette.secondaryText,
                        fontSize = if (landscape) 9.sp else 10.sp,
                        lineHeight = if (landscape) 11.sp else 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(
                Modifier.size(geometry.action).clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Zamknij kartę",
                    tint = SideDrawerPalette.primaryText.copy(alpha = 0.8f),
                    modifier = Modifier.size(geometry.actionIcon))
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
        var creatingGroup by remember(tab.id) { mutableStateOf(false) }
        var groupName by remember(tab.id) { mutableStateOf("") }
        if (creatingGroup) {
            AlertDialog(
                onDismissRequest = { creatingGroup = false },
                title = { Text("Nowa grupa kart") },
                text = {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it.take(70) },
                        label = { Text("Nazwa grupy") }, singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        tabViewModel?.moveTabToNewGroup(tab.id, groupName.trim().ifBlank { "Nowa grupa" })
                        creatingGroup = false
                        onDismiss()
                    }) { Text("Utwórz i przenieś") }
                },
                dismissButton = { TextButton(onClick = { creatingGroup = false }) { Text("Anuluj") } }
            )
        }
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

                    // This is the active side-panel menu (not TabMenuCompose).
                    MenuOption(
                        icon = androidx.compose.material.icons.Icons.Default.Add,
                        text = "Utwórz nową grupę",
                        onClick = { creatingGroup = true }
                    )
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
