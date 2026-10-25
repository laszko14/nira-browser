package com.prirai.android.nira

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.browser.home.compose.jumpBackInItems
import com.prirai.android.nira.browser.home.compose.recentlyClosedItems
import com.prirai.android.nira.browser.toolbar.ToolbarGestureHandler
import com.prirai.android.nira.components.toolbar.ToolbarMenu
import com.prirai.android.nira.downloads.DownloadsBottomSheetFragment
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ext.nav
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.media.MediaDetectorController
import com.prirai.android.nira.media.MediaFloatingUi
import com.prirai.android.nira.media.MediaUiSnapshot
import com.prirai.android.nira.media.MediaTabState
import com.prirai.android.nira.media.MediaSessionBinding
import com.prirai.android.nira.media.MediaElementPlaybackBridge
import com.prirai.android.nira.media.MpvDebugTrace
import com.prirai.android.nira.media.VideoPlayerPolicy
import com.prirai.android.nira.media.MpvPlaybackHeaders
import com.prirai.android.nira.media.ZavroMpvActivity
import com.prirai.android.nira.media.MediaSource
import com.prirai.android.nira.media.MediaPlaybackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.SessionState
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.browser.thumbnails.BrowserThumbnails
import mozilla.components.feature.tabs.WindowFeature
import mozilla.components.lib.state.ext.flow
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.lib.state.ext.observeAsComposableState
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import mozilla.components.support.ktx.kotlinx.coroutines.flow.ifAnyChanged
import org.mozilla.geckoview.GeckoSession

/**
 * Fragment used for browsing the web within the main app.
 */
@ExperimentalCoroutinesApi
class BrowserFragment : BaseBrowserFragment(), UserInteractionHandler {

    private val windowFeature = ViewBoundFeatureWrapper<WindowFeature>()
    private var mediaFloatingUi: MediaFloatingUi? = null
    /** Only resume the exact page/session we ourselves paused for native playback. */
    private data class SuspendedWebPlayback(
        val tabId: String,
        val contextId: String?,
        val privateTab: Boolean,
        val session: Any,
        val pageUrl: String,
        val elementLeaseId: String?,
        val fallbackMediaPaused: Boolean,
    )
    private var suspendedWebPlayback: SuspendedWebPlayback? = null
    private var inlineMpv: com.prirai.android.nira.media.InlineMpvUi? = null
    private var inlinePage: String? = null
    private var inlineSession: Any? = null
    private fun closeInlineMpv() {
        inlineMpv?.detach()
        inlineMpv=null
        inlinePage=null
        inlineSession=null
        restoreWebPlaybackAfterMpv()
    }
    private val internalMpvLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { restoreWebPlaybackAfterMpv() }

    private fun restoreWebPlaybackAfterMpv() {
        val held = suspendedWebPlayback ?: return
        suspendedWebPlayback = null
        if (!isAdded) {
            held.elementLeaseId?.let(MediaElementPlaybackBridge::discard)
            return
        }
        val state = requireContext().components.store.state
        val tab = state.tabs.firstOrNull { it.id == held.tabId } ?: run {
            held.elementLeaseId?.let(MediaElementPlaybackBridge::discard)
            return
        }
        if (tab.engineState.engineSession !== held.session ||
            tab.contextId != held.contextId || tab.content.private != held.privateTab ||
            tab.content.url != held.pageUrl) {
            held.elementLeaseId?.let(MediaElementPlaybackBridge::discard)
            return
        }
        // Per-element Gecko handoff wins: restore ONLY the same connected
        // HTMLVideoElement and exact source that was paused on user request.
        val session = tab.engineState.engineSession ?: return
        val current = MediaTabState(
            MediaSessionBinding(session, tab.id, tab.contextId, tab.content.private),
            tab.content.url, tab.content.loading
        )
        if (held.elementLeaseId != null) {
            MediaElementPlaybackBridge.restore(held.elementLeaseId, current)
        } else if (held.fallbackMediaPaused) {
            // Older player APIs may not expose the HTML5 element; only resume
            // Gecko if this integration actually paused its media session.
            runCatching { tab.mediaSessionState?.controller?.play() }
        }
    }

    // Track last tab IDs for auto-grouping detection
    private var lastTabIds = setOf<String>()
    
    // Homepage ViewModel for shortcuts and bookmarks
    private lateinit var homeViewModel: com.prirai.android.nira.browser.home.compose.HomeViewModel

    // Cached toolbar position for this fragment lifetime. Changing the
    // toolbar position pref triggers Activity.recreate() (see
    // BrowserActivity.onResume), so the value is stable within one fragment
    // instance. Reading it once avoids a SharedPreferences monitor acquire
    // on every pixel of toolbar scroll offset change (see
    // adjustWebContentMarginsForToolbarOffset which fires ~60-120Hz during
    // a flick).
    private val cachedToolbarPosition: Int by lazy {
        UserPreferences(requireContext()).toolbarPosition
    }

    // Toolbar icon for fullscreen toggle

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // This Activity handles orientation/screenSize without recreation.
        // Re-measure the detached footer after its new constraints apply.
        val bottomStrip = unifiedToolbar?.getBottomComponentsContainer()
        bottomStrip?.post {
            if (!isAdded || view == null) return@post
            bottomStrip.requestLayout()
            unifiedToolbar?.reconcileEngineViewHeight()
            requestWebContentPositionUpdate()
        }
    }

    override fun initializeUI(view: View, tab: SessionState) {
        UserPreferences(requireContext()).apply {
            toolbarPosition = com.prirai.android.nira.components.toolbar.ToolbarPosition.TOP.ordinal
            showContextualToolbar = false
            showTabGroupBar = true
        }
        super.initializeUI(view, tab)

        val context = requireContext()
        val components = context.components
        
        // Initialize homepage ViewModel
        initializeHomeViewModel()
        
        // Setup homepage ComposeView
        setupHomePageComposeView()
        
        // Update visibility based on current tab URL
        updateContentVisibility(tab.content.url)

        // Only add gesture handler if unifiedToolbar has a browser toolbar
        unifiedToolbar?.getBrowserToolbar()?.let { toolbar ->
            binding.gestureLayout.addGestureListener(
                ToolbarGestureHandler(
                    activity = requireActivity(),
                    contentLayout = binding.browserLayout,
                    tabPreview = binding.tabPreview,
                    toolbarLayout = toolbar,
                    store = components.store,
                    selectTabUseCase = components.tabsUseCases.selectTab
                )
            )
        }

        thumbnailsFeature.set(
            feature = BrowserThumbnails(context, binding.engineView, components.store),
            owner = this,
            view = view
        )

        windowFeature.set(
            feature = WindowFeature(
                store = components.store, tabsUseCases = components.tabsUseCases
            ), owner = this, view = view
        )

        // Tab groups and contextual toolbar handled by UnifiedToolbar

        observeTabChangesForToolbar()
        observeDownloadsForSheet()
        observeNewTabSelection()
        setupFloatingMediaUi()
        setupEdgePanelGestures()
    }

    /** Only intercept swipes beginning in a narrow edge zone of the webpage. */
    // Observe real finger scrolling at the gesture container without stealing
    // touch events from GeckoView. Unlike CoordinatorLayout nested scrolling,
    // this works with GeckoView in both portrait and landscape.
    private var accumulatedDownScroll = 0f
    private var accumulatedUpScroll = 0f

    private fun onVerticalPageGesture(scrollDeltaY: Float) {
        if (cachedToolbarPosition !=
            com.prirai.android.nira.components.toolbar.ToolbarPosition.TOP.ordinal) return
        val toolbar = unifiedToolbar ?: return
        if (toolbar.isScrollAnimationRunning()) return

        val density = resources.displayMetrics.density
        when {
            scrollDeltaY > 0f -> {
                accumulatedUpScroll = 0f
                accumulatedDownScroll += scrollDeltaY
                if (!toolbar.isCollapsed() && accumulatedDownScroll >= 48f * density) {
                    toolbar.collapse()
                    accumulatedDownScroll = 0f
                }
            }
            scrollDeltaY < 0f -> {
                accumulatedDownScroll = 0f
                accumulatedUpScroll -= scrollDeltaY
                if (toolbar.isCollapsed() && accumulatedUpScroll >= 28f * density) {
                    toolbar.expand()
                    accumulatedUpScroll = 0f
                }
            }
        }
    }

    private fun setupEdgePanelGestures() {
        binding.gestureLayout.onVerticalSwipe = { delta -> onVerticalPageGesture(delta) }
        val prefs = UserPreferences(requireContext())
        if (!prefs.niraEdgePanelsEnabled) return
        val density = resources.displayMetrics.density
        val edgeWidth = prefs.niraEdgeWidthDp * density
        val threshold = 64 * density
        binding.gestureLayout.addGestureListener(object : com.prirai.android.nira.browser.SwipeGestureListener {
            private var direction = 0
            private var travelled = 0f
            override fun onSwipeStarted(start: android.graphics.PointF, next: android.graphics.PointF): Boolean {
                val dx = next.x - start.x
                val dy = next.y - start.y
                if (kotlin.math.abs(dx) < 12 * density ||
                    kotlin.math.abs(dx) < 1.5f * kotlin.math.abs(dy)) return false
                // Gesture coordinates are screen-relative; the content origin can
                // move inward in landscape when Android navigation is on the left.
                val location = IntArray(2)
                binding.gestureLayout.getLocationOnScreen(location)
                val localX = start.x - location[0]
                val width = binding.gestureLayout.width.toFloat()
                val landscape = resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val activeEdge = if (landscape) maxOf(edgeWidth, 80f * density) else edgeWidth
                direction = when {
                    localX in 0f..activeEdge && dx > 0 -> 1
                    localX >= width - activeEdge && localX <= width && dx < 0 -> -1
                    else -> 0
                }
                travelled = 0f
                return direction != 0
            }
            override fun onSwipeUpdate(distanceX: Float, distanceY: Float) {
                travelled -= distanceX
            }
            override fun onSwipeFinished(velocityX: Float, velocityY: Float) {
                val finishedDirection = direction
                direction = 0
                if (kotlin.math.abs(travelled) < threshold || !isAdded ||
                    parentFragmentManager.isStateSaved) return
                if (finishedDirection == 1 && travelled > 0 &&
                    parentFragmentManager.findFragmentByTag("NiraSideTabs") == null) {
                    com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment
                        .newSidePanel().show(parentFragmentManager, "NiraSideTabs")
                }
                if (finishedDirection == -1 && travelled < 0 &&
                    parentFragmentManager.findFragmentByTag("NiraSideBookmarks") == null) {
                    com.prirai.android.nira.browser.bookmark.ui.BookmarksBottomSheetFragment
                        .newSidePanel().show(parentFragmentManager, "NiraSideBookmarks")
                }
            }
        })
    }

    private fun currentMediaSnapshot(): MediaUiSnapshot? {
        val store = requireContext().components.store.state
        val selected = store.tabs.firstOrNull { it.id == store.selectedTabId } ?: return null
        val engineSession = selected.engineState.engineSession ?: return null
        val repository = MediaDetectorController.uiRepository.value ?: return null
        val tab = MediaTabState(
            MediaSessionBinding(engineSession, selected.id, selected.contextId, selected.content.private),
            selected.content.url, selected.content.loading
        )
        return MediaUiSnapshot(tab, repository.currentEntries(tab))
    }

    private fun setupFloatingMediaUi() {
        val store = requireContext().components.store
        // Content-script Play offers are recommendations only. The trusted
        // Store session, active tab and explicit AUTO preference decide.
        MediaElementPlaybackBridge.onAutoOffer = auto@{ offer ->
            fun log(code: String) = MpvDebugTrace.note(
                MpvDebugTrace.Section.HANDOFF, code, offer.port.engineSession
            )
            if (!isAdded || view == null || suspendedWebPlayback != null ||
                MediaElementPlaybackBridge.busy()) {
                log("auto_busy")
                return@auto
            }
            val state = store.state
            val selected = state.tabs.firstOrNull { it.id == state.selectedTabId }
                ?: return@auto
            val session = selected.engineState.engineSession ?: return@auto
            if (session !== offer.port.engineSession) {
                log("auto_wrong_session")
                return@auto
            }
            if (selected.content.loading) {
                log("auto_navigation")
                return@auto
            }
            if (VideoPlayerPolicy.resolve(requireContext(), selected.content.url,
                    selected.content.private) != VideoPlayerPolicy.Mode.AUTO) {
                log("auto_mode_not_enabled")
                return@auto
            }
            val uri = runCatching { android.net.Uri.parse(offer.mediaUrl) }.getOrNull()
                ?: return@auto
            if (!ZavroMpvActivity.allowedVideoUrl(uri)) return@auto
            val frame = runCatching { android.net.Uri.parse(offer.pageUrl) }.getOrNull()
                ?.takeIf { ZavroMpvActivity.allowedVideoUrl(it) } ?: return@auto
            val safeReferrer = frame.buildUpon().clearQuery().fragment(null).build().toString()
            if (!offer.topFrame || offer.w < 320.0 || offer.h < 180.0) {
                log("inline_frame_unsupported")
                return@auto
            }
            log("inline_eligible")
            launchInlineVideo(uri,safeReferrer,offer)
        }
        MediaElementPlaybackBridge.onInlineLayout = { nonce,rect ->
            val ui=inlineMpv
            if (ui != null && ui.token == nonce) ui.place(rect)
        }
        val overlay = MediaFloatingUi(
            parent = binding.browserLayout,
            current = { if (isAdded) currentMediaSnapshot() else null },
            safeToolbarSpace = {
                val height = unifiedToolbar?.getToolbarView()?.height ?: 0
                if (cachedToolbarPosition ==
                    com.prirai.android.nira.components.toolbar.ToolbarPosition.BOTTOM.ordinal) {
                    0 to height
                } else height to 0
            },
            openVideo = { streamId ->
                // Resolve on click, not when the bottom sheet rendered: the signed URL
                // may have rotated while the user was choosing a quality.
                val fresh = currentMediaSnapshot()
                val entry = fresh?.entries?.firstOrNull { it.id == streamId }
                val uri = entry?.url?.toUri()
                if (uri != null && uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()) {
                    if (entry.playbackStatus in setOf(
                            com.prirai.android.nira.media.MediaPlaybackStatus.EXPIRED,
                            com.prirai.android.nira.media.MediaPlaybackStatus.AUTH_ERROR,
                            com.prirai.android.nira.media.MediaPlaybackStatus.UNPLAYABLE)) {
                        Toast.makeText(requireContext(), "Link nie jest aktualnie odtwarzalny — odśwież stronę lub uruchom odtwarzacz strony.", Toast.LENGTH_LONG).show()
                    } else {
                        // An embedded player commonly signs the URL for its own origin.
                        // Use the trusted frame URL captured by webRequest correlation;
                        // fall back to the top document only for top-frame media.
                        launchVideoInMpv(uri, entry.frameUrl ?: fresh?.tab?.pageUrl)
                    }
                } else {
                    Toast.makeText(requireContext(), R.string.video_link_invalid, Toast.LENGTH_SHORT).show()
                }
            }
        )
        mediaFloatingUi = overlay
        viewLifecycleOwner.lifecycleScope.launch {
            val repositoryChanges = MediaDetectorController.uiRepository.flatMapLatest { repo ->
                if (repo == null) flowOf(null)
                else repo.revision.map { repo }
            }
            // Only changes of the selected document or MediaRepository revision
            // repaint the badge; progress/scroll Store actions are ignored.
            val selectedDocument = store.flow().map { state ->
                val selected = state.tabs.firstOrNull { it.id == state.selectedTabId }
                val session = selected?.engineState?.engineSession
                if (selected == null || session == null) null
                else MediaTabState(
                    MediaSessionBinding(session, selected.id, selected.contextId, selected.content.private),
                    selected.content.url, selected.content.loading
                )
            }.distinctUntilChanged()
            combine(selectedDocument, repositoryChanges) { tab, repo ->
                if (tab == null || repo == null) null
                else MediaUiSnapshot(tab, repo.currentEntries(tab))
            }.collect {
                overlay.render(it)
                if (inlineMpv != null) {
                    val s=store.state
                    val tab=s.tabs.firstOrNull { t -> t.id == s.selectedTabId }
                    if (tab == null || tab.engineState.engineSession !== inlineSession ||
                        tab.content.url != inlinePage) closeInlineMpv()
                }
            }
        }
    }

    override fun onDestroyView() {
        MediaElementPlaybackBridge.onAutoOffer = null
        MediaElementPlaybackBridge.onInlineLayout = null
        closeInlineMpv()
        binding.gestureLayout.onVerticalSwipe = null
        mediaFloatingUi?.detach()
        mediaFloatingUi = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        // Contextual toolbar updates handled automatically by UnifiedToolbar

        // Ensure tab preview is hidden (fixes doubled content issue after returning from settings)
        binding.tabPreview.visibility = View.GONE

        // Dynamic toolbar clipping is managed by ModernToolbarSystem
        // No manual configuration needed here
        
        // Apply AMOLED mode background if enabled
        applyThemeColors()
        
        // Update toolbar styling for private mode
        updateToolbarForPrivateMode()
    }
    
    private fun updateToolbarForPrivateMode() {
        val selectedTab = requireContext().components.store.state.tabs.find { 
            it.id == requireContext().components.store.state.selectedTabId 
        }
        val isPrivate = selectedTab?.content?.private == true
        
        // Use the UnifiedToolbar instance from this fragment
        _unifiedToolbar?.applyPrivateMode(isPrivate)
        
        // Also update status bar color
        updateStatusBarForPrivateMode(isPrivate)
    }
    
    private fun updateStatusBarForPrivateMode(isPrivate: Boolean) {
        val window = requireActivity().window
        if (isPrivate) {
            val purpleColor = android.graphics.Color.parseColor("#6200EE")
            window.statusBarColor = purpleColor
        } else {
            // Reset to theme color
            val typedValue = android.util.TypedValue()
            requireContext().theme.resolveAttribute(
                com.google.android.material.R.attr.colorSurface, 
                typedValue, 
                true
            )
            window.statusBarColor = typedValue.data
        }
    }
    
    private fun applyThemeColors() {
        val bgColor = com.prirai.android.nira.theme.ThemeManager.getBackgroundColor(requireContext())
        
        // Apply to all background views
        binding.gestureLayout.setBackgroundColor(bgColor)
        binding.browserWindow.setBackgroundColor(bgColor)
        binding.browserLayout.setBackgroundColor(bgColor)
        binding.swipeRefresh.setBackgroundColor(bgColor)
    }

    private fun observeDownloadsForSheet() {
        val shownDownloadIds = mutableSetOf<String>()
        viewLifecycleOwner.lifecycleScope.launch {
            requireContext().components.store.flow()
                .map { state ->
                    state.downloads.values.filter {
                        it.status == DownloadState.Status.INITIATED ||
                        it.status == DownloadState.Status.DOWNLOADING
                    }
                }
                .distinctUntilChanged()
                .collect { activeDownloads ->
                    val newDownloads = activeDownloads.filter { it.id !in shownDownloadIds }
                    if (newDownloads.isNotEmpty()) {
                        newDownloads.forEach { shownDownloadIds.add(it.id) }
                        if (!isDetached && isAdded && activity != null) {
                            val existing = parentFragmentManager.findFragmentByTag(DownloadsBottomSheetFragment.TAG)
                            if (existing == null) {
                                DownloadsBottomSheetFragment.newInstance()
                                    .show(parentFragmentManager, DownloadsBottomSheetFragment.TAG)
                            }
                        }
                    }
                }
        }
    }

    private fun observeNewTabSelection() {
        if (!isAdded) return
        var knownTabIds = requireContext().components.store.state.tabs.map { it.id }.toSet()
        viewLifecycleOwner.lifecycleScope.launch {
            requireContext().components.store.flow()
                .map { state -> state.selectedTabId to state.tabs.map { it.id }.toSet() }
                .distinctUntilChanged()
                .collect { (selectedId, currentIds) ->
                    val newIds = currentIds - knownTabIds
                    knownTabIds = currentIds
                    if (selectedId != null && selectedId in newIds) {
                        com.prirai.android.nira.browser.tabs.compose.TabSheetStateManager.notifyTabSheetDismissed()
                    }
                }
        }
    }

    private fun observeTabChangesForToolbar() {
        // Safety check: ensure fragment is attached before accessing context
        if (!isAdded) return
        
        val lruManager = com.prirai.android.nira.browser.tabs.TabLRUManager.getInstance(requireContext())
        
        // Observe browser state changes to update toolbar in real-time
        viewLifecycleOwner.lifecycleScope.launch {
            requireContext().components.store.flowScoped(viewLifecycleOwner, Dispatchers.Main) { flow ->
                flow.mapNotNull { state ->
                    // Safety check: ensure fragment is still attached
                    if (!isAdded) return@mapNotNull null
                    val currentTab = state.tabs.find { it.id == state.selectedTabId }
                    currentTab
                }.ifAnyChanged { tab ->
                    arrayOf(
                        tab.content.loading,
                        tab.content.canGoBack,
                        tab.content.canGoForward,
                        tab.content.url,
                        tab.id
                    )
                }.collect { tab ->
                    // Toolbar updates handled automatically by UnifiedToolbar
                }
            }
        }

        // Track tab removals for LRU
        viewLifecycleOwner.lifecycleScope.launch {
            // Safety check before accessing context
            if (!isAdded) return@launch
            
            var lastTabIds = requireContext().components.store.state.tabs.map { it.id }.toSet()
            requireContext().components.store.flowScoped(viewLifecycleOwner, Dispatchers.Main) { flow ->
                flow.mapNotNull { state -> 
                    if (!isAdded) return@mapNotNull null
                    state.tabs.map { it.id }.toSet() 
                }.distinctUntilChanged()
                .collect { currentTabIds: Set<String> ->
                    val removedTabIds = lastTabIds - currentTabIds
                    removedTabIds.forEach { tabId: String ->
                        lruManager.onTabClosed(tabId)
                    }
                    lastTabIds = currentTabIds
                }
            }
        }

        // Also observe tab selection changes
        viewLifecycleOwner.lifecycleScope.launch {
            requireContext().components.store.flowScoped(viewLifecycleOwner, Dispatchers.Main) { flow ->
                flow.mapNotNull { state ->
                    // Safety check: ensure fragment is still attached
                    if (!isAdded) return@mapNotNull null
                    state
                }.distinctUntilChangedBy { it.selectedTabId }.collect { state ->
                    // Safety check before accessing context
                    if (!isAdded || context == null) return@collect
                    
                    val fragmentContext = requireContext()
                    
                    // Track tab selection in LRU manager
                    state.selectedTabId?.let { tabId ->
                        lruManager.onTabSelected(tabId)
                    }
                    
                    // Update toolbar profile context based on selected tab
                    val selectedTab = state.tabs.find { it.id == state.selectedTabId }
                    selectedTab?.let { tab ->
                        // Update toolbar for private mode if needed
                        updateToolbarForPrivateMode()
                        
                        // Get the profile for this tab
                        val tabContextId = tab.contextId
                        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(fragmentContext)
                        
                        // Extract profile ID from contextId (format: "profile_X")
                        val profileId = tabContextId?.removePrefix("profile_")
                        
                        if (profileId != null) {
                            val allProfiles = profileManager.getAllProfiles()
                            val profile = allProfiles.find { it.id == profileId }
                            val currentProfile = profileManager.getActiveProfile()
                            
                            if (profile != null && currentProfile.id != profileId) {
                                // Switch to this profile context in the toolbar
                                profileManager.setActiveProfile(profile)
                                unifiedToolbar?.getTabGroupBar()?.updateProfileIcon(profile)
                            }
                        }
                    }
                    
                    // NO NAVIGATION - HomePage content is now rendered inline in BrowserFragment
                    // when URL is "about:homepage". This keeps the toolbar alive and prevents
                    // tab pills from reloading. See fragment_browser.xml for homepage ComposeView.
                }
            }
        }

        // Observe tab changes to update content visibility (homepage vs web content)
        components.store.flowScoped(viewLifecycleOwner, Dispatchers.Main) { flow ->
            flow.mapNotNull { state -> state.selectedTabId }
                .distinctUntilChanged()
                .collect { tabId: String ->
                    if (!isAdded) return@collect
                    val tab = components.store.state.tabs.find { it.id == tabId }
                    tab?.let {
                        updateContentVisibility(it.content.url)
                    }
                }
        }

        // Also observe URL changes within the same tab
        components.store.flowScoped(viewLifecycleOwner, Dispatchers.Main) { flow ->
            flow.mapNotNull { state ->
                state.tabs.find { it.id == state.selectedTabId }
            }
            .distinctUntilChangedBy { it.content.url }
            .collect { tab ->
                if (!isAdded) return@collect
                updateContentVisibility(tab.content.url)
            }
        }
    }

    override fun initializeUnifiedToolbar(view: View, tab: SessionState) {
        val prefs = UserPreferences(requireContext())

        try {
            // Create UnifiedToolbar using the interactor from base class
            _unifiedToolbar = com.prirai.android.nira.components.toolbar.unified.UnifiedToolbar.create(
                context = requireContext(),
                parent = binding.browserLayout,
                lifecycleOwner = viewLifecycleOwner,
                interactor = browserInteractor,
                customTabSession = null,
                store = requireContext().components.store
            )

            // Set engine view for scroll behavior
            unifiedToolbar?.setEngineView(binding.engineView)

            // Add layout listener to update web content positioning once toolbar is measured
            // Only for the main toolbar view, not bottom components to avoid jank
            unifiedToolbar?.getToolbarView()?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                requestWebContentPositionUpdate()
            }

            unifiedToolbar?.getBottomComponentsContainer()?.addOnLayoutChangeListener {
                    _, _, _, _, _, _, _, _, _ ->
                // The detached bottom strip can grow/shrink without changing
                // the address toolbar's height. Keep the Gecko viewport above it.
                unifiedToolbar?.reconcileEngineViewHeight()
                requestWebContentPositionUpdate()
            }

            // Set tab selection listener
            unifiedToolbar?.setOnTabSelectedListener { tabId ->
                requireContext().components.tabsUseCases.selectTab(tabId)
            }

            // Minimal state has two inputs:
            //  - scrolling DOWN past the threshold (56 dp sustained)
            //    enters minimal state - see ModernScrollBehavior.
            //  - the chevron toggle button to the right of the URL pill
            //    toggles both directions and is the only way to EXIT
            //    minimal (scroll-up intentionally does not auto-exit,
            //    which avoids the oscillation bug on sites with
            //    internal scroll containers).
            // The address bar itself never scrolls off; only the aux
            // bars (tab bar + contextual bar) animate in and out.
            // Compact top-only mode keeps the tab bar visible. No chevron
            // is added to the already crowded address/action row.

            // Set expansion state listener to update web content positioning
            unifiedToolbar?.setOnExpansionStateChangedListener { expanded ->
                requestWebContentPositionUpdate()
            }

            // Minimal-state listener - compose the two concerns here
            // because UnifiedToolbar only supports a single listener:
            //  1. Recompute web content positioning (aux bars appeared /
            //     disappeared).
            //  2. Tell the toggle integration to swap its chevron glyph
            //     (so the icon stays in sync when the state is changed
            //     by page scroll, not just by tapping the button).
            unifiedToolbar?.setOnMinimalStateChangedListener {
                requestWebContentPositionUpdate()
            }
            
            // Set toolbar offset listener for smooth margin adjustment
            unifiedToolbar?.setOnToolbarOffsetChangedListener { currentOffset, totalHeight ->
                adjustWebContentMarginsForToolbarOffset(currentOffset, totalHeight)
            }

            // Set contextual toolbar listener
            val contextualListener = object : com.prirai.android.nira.toolbar.ContextualBottomToolbar.ContextualToolbarListener {
                override fun onBackClicked() {
                    requireContext().components.sessionUseCases.goBack()
                }

                override fun onForwardClicked() {
                    requireContext().components.sessionUseCases.goForward()
                }

                override fun onBookmarksClicked() {
                    val bookmarksBottomSheet = com.prirai.android.nira.browser.bookmark.ui.BookmarksBottomSheetFragment.newInstance()
                    bookmarksBottomSheet.show(parentFragmentManager, "BookmarksBottomSheet")
                }

                override fun onShareClicked() {
                    val state = requireContext().components.store.state
                    val selectedTab = state.tabs.find { it.id == state.selectedTabId }
                    val currentUrl = selectedTab?.content?.url
                    if (!currentUrl.isNullOrBlank()) {
                        val shareIntent = android.content.Intent()
                        shareIntent.action = android.content.Intent.ACTION_SEND
                        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, currentUrl)
                        shareIntent.type = "text/plain"
                        startActivity(android.content.Intent.createChooser(shareIntent, "Share"))
                    }
                }

                override fun onTabCountClicked() {
                    try {
                        val tabsBottomSheet =
                            com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.newInstance()
                        tabsBottomSheet.show(
                            parentFragmentManager,
                            com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.TAG
                        )
                    } catch (e: Exception) {
                        // Silently handle tab sheet opening failure
                    }
                }

                override fun onMenuClicked() {
                    // Show custom Material 3 menu
                    // Use try-catch to handle cases where view might not be ready
                    try {
                        if (view != null && isAdded) {
                            showBrowserMenu()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BrowserFragment", "Error showing menu", e)
                    }
                }

                override fun onSearchClicked() {
                    val directions = NavGraphDirections.actionGlobalSearchDialog(sessionId = null)
                    nav(R.id.browserFragment, directions)
                }

                override fun onNewTabClicked() {
                    requireContext().components.tabsUseCases.addTab.invoke("about:homepage")
                    // Trigger autofocus on the newly created tab
                    com.prirai.android.nira.browser.tabs.compose.TabSheetStateManager.notifyTabSheetDismissed()
                }
            }

            unifiedToolbar?.setContextualToolbarListener(contextualListener)

            // Start observing tab changes for real-time updates
            observeTabChangesForModernToolbar()

            // Initialize with current tab state
            initializeModernToolbarWithCurrentState()

        } catch (e: Exception) {
            // Toolbar initialization failed; the engine view keeps rendering
            // full-screen without chrome, which is better than crashing the
            // whole fragment. UnifiedToolbar handles its own dynamic height
            // coordination, so there is no fallback positioning to apply.
            android.util.Log.e("BrowserFragment", "UnifiedToolbar init failed", e)
        }
    }

    private fun observeTabChangesForModernToolbar() {
        // Track last known tab IDs for detecting new tabs
        var lastTabIds = emptySet<String>()

        // Use proper flow-based observation instead of polling
        viewLifecycleOwner.lifecycleScope.launch {
            val store = requireContext().components.store

            store.flowScoped(viewLifecycleOwner, Dispatchers.Main) { flow ->
                flow.mapNotNull { state ->
                    // Check if fragment is still attached before proceeding
                    if (!isAdded) return@mapNotNull null

                    val activity = activity as? BrowserActivity ?: return@mapNotNull null
                    val isPrivateMode = activity.browsingModeManager.mode.isPrivate
                    val profileManager =
                        com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                    val currentProfile = profileManager.getActiveProfile()
                    val currentProfileContextId = if (isPrivateMode) {
                        "private"
                    } else {
                        "profile_${currentProfile.id}"
                    }

                    val filteredTabs = state.tabs.filter { tab ->
                        // Match private mode first
                        if (tab.content.private != isPrivateMode) {
                            false
                        } else {
                            // Include tabs with matching contextId OR guest tabs (null contextId)
                            (tab.contextId == currentProfileContextId) ||
                                (!isPrivateMode && currentProfile.id == "default" && tab.contextId == null)
                        }
                    }

                    // Return state data for change detection
                    state to filteredTabs
                }.distinctUntilChangedBy { (state, filteredTabs) ->
                    // Detect changes in:
                    // 1. Tab list (additions/removals)
                    // 2. Selected tab
                    // 3. Tab content (title, url, icon) for any visible tab
                    Triple(
                        filteredTabs.map { it.id },
                        state.selectedTabId,
                        filteredTabs.joinToString("|") { tab ->
                            // Include title, url, icon hash, and loading state
                            "${tab.id}:${tab.content.title}:${tab.content.url}:${tab.content.icon?.hashCode()}:${tab.content.loading}"
                        }
                    )
                }.collect { (state, filteredTabs) ->
                    if (!isAdded) return@collect

                    // Detect new tabs for auto-grouping
                    val currentTabIds = state.tabs.map { it.id }.toSet()
                    currentTabIds - lastTabIds
                    
                    // Update toolbar with current context
                    val currentSelectedTab = state.tabs.find { it.id == state.selectedTabId }
                    val currentUrl = currentSelectedTab?.content?.url ?: ""
                    // Properly detect homepage - both empty URL and about:homepage
                    val currentIsHomepage = currentUrl.isEmpty() || currentUrl == "about:homepage"

                    unifiedToolbar?.updateContextualToolbar(
                        tab = currentSelectedTab,
                        canGoBack = currentSelectedTab?.content?.canGoBack ?: false,
                        canGoForward = currentSelectedTab?.content?.canGoForward ?: false,
                        tabCount = filteredTabs.size,  // Use filtered tabs count
                        isHomepage = currentIsHomepage
                    )
                }
            }
        }
    }

    private fun initializeModernToolbarWithCurrentState() {
        // Initialize the modern toolbar with the current tab state immediately
        val state = requireContext().components.store.state

        // Apply same filtering logic as in observeTabChangesForModernToolbar
        val activity = activity as? BrowserActivity
        val isPrivateMode = activity?.browsingModeManager?.mode?.isPrivate ?: false
        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
        val currentProfile = profileManager.getActiveProfile()
        val currentProfileContextId = if (isPrivateMode) {
            "private"
        } else {
            "profile_${currentProfile.id}"
        }

        val filteredTabs = state.tabs.filter { tab ->
            // Match private mode first
            if (tab.content.private != isPrivateMode) {
                false
            } else {
                // Include tabs with matching contextId OR guest tabs (null contextId)
                (tab.contextId == currentProfileContextId) ||
                                (!isPrivateMode && currentProfile.id == "default" && tab.contextId == null)
            }
        }

        val selectedTab = state.tabs.find { it.id == state.selectedTabId }
        val currentUrl = selectedTab?.content?.url ?: ""
        // Properly detect homepage - both empty URL and about:homepage
        val isHomepage = currentUrl.isEmpty() || currentUrl == "about:homepage"

        unifiedToolbar?.updateContextualToolbar(
            tab = selectedTab,
            canGoBack = selectedTab?.content?.canGoBack ?: false,
            canGoForward = selectedTab?.content?.canGoForward ?: false,
            tabCount = filteredTabs.size,  // Use filtered tabs count
            isHomepage = isHomepage
        )

    }

    private var lastTopMargin = -1
    
    /**
     * Adjust web content padding dynamically as toolbar scrolls to maintain clipping.
     * Updates padding to prevent content from appearing under the toolbar.
     */
    private fun adjustWebContentMarginsForToolbarOffset(currentOffset: Int, totalHeight: Int) {
        // CRITICAL: Check if view is still attached to prevent crash when switching tabs
        if (!isAdded || view == null) {
            return
        }

        // Calculate visible toolbar height
        val visibleHeight = (totalHeight - currentOffset).coerceAtLeast(0)

        // Since the toolbar is pinned (never scrolls off), the pinned chrome
        // area must be subtracted from swipeRefresh's layout bounds so the
        // engine view does not draw underneath it. The toolbar sits in the
        // CoordinatorLayout with Gravity.TOP or BOTTOM; we match that with
        // a margin on the opposite edge of swipeRefresh.
        //
        // visibleHeight comes from UnifiedToolbar.reconcileEngineViewHeight()
        // which fires every time the toolbar's layout changes (minimal
        // state toggle, rotation, font-scale change).
        val lp = binding.swipeRefresh.layoutParams
            as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            ?: return

        // A GeckoView relayout every 16 ms during hide/show makes the webpage
        // jump, especially in landscape. Move its existing viewport together
        // with the chrome; update the actual layout margin only after the
        // 200 ms animation has completed.
        if (cachedToolbarPosition ==
                com.prirai.android.nira.components.toolbar.ToolbarPosition.TOP.ordinal &&
            unifiedToolbar?.isScrollAnimationRunning() == true) {
            binding.swipeRefresh.translationY = (visibleHeight - lp.topMargin).toFloat()
            return
        }
        binding.swipeRefresh.translationY = 0f

        when (cachedToolbarPosition) {
            com.prirai.android.nira.components.toolbar.ToolbarPosition.BOTTOM.ordinal -> {
                if (lp.topMargin != 0) {
                    lp.topMargin = 0
                    binding.swipeRefresh.layoutParams = lp
                }
                if (lp.bottomMargin != visibleHeight) {
                    lp.bottomMargin = visibleHeight
                    binding.swipeRefresh.layoutParams = lp
                }
                // Clear any stale top padding from a previous TOP-mode layout
                if (binding.swipeRefresh.paddingTop != 0 ||
                    binding.swipeRefresh.paddingBottom != 0) {
                    binding.swipeRefresh.setPadding(0, 0, 0, 0)
                }
            }
            else -> {
                // Keep SwipeRefreshLayout's gesture surface aligned with
                // the visible page: padding places GeckoView inside the
                // refresh container and can leave a dead pull zone at top.
                // A top margin moves both the touch surface and indicator.
                var changed = false
                if (lp.topMargin != visibleHeight) {
                    lp.topMargin = visibleHeight
                    changed = true
                }
                val bottomStripHeight = unifiedToolbar?.getBottomComponentsContainer()
                    ?.takeIf { it.parent != null && it.visibility == View.VISIBLE }?.height ?: 0
                if (lp.bottomMargin != bottomStripHeight) {
                    lp.bottomMargin = bottomStripHeight
                    changed = true
                }
                if (changed) binding.swipeRefresh.layoutParams = lp
                if (binding.swipeRefresh.paddingTop != 0 ||
                    binding.swipeRefresh.paddingBottom != 0) {
                    binding.swipeRefresh.setPadding(0, 0, 0, 0)
                }
            }
        }
    }

    /**
     * Setup floating fullscreen toggle button above toolbars
     */


    /**
     * Override fullscreen handling to properly hide/show modern toolbar system.
     * Based on Mozilla's implementation in Fenix BaseBrowserFragment.
     *
     * Note: Swipe-to-fullscreen gestures are handled by the web content (e.g., YouTube player).
     * If swipe gestures don't work consistently, it's typically due to the video player's
     * internal state management, not the browser. The browser correctly responds to fullscreen
     * API calls from the web content.
     */
    override fun onFullScreenModeChanged(inFullScreen: Boolean) {
        mediaFloatingUi?.setFullscreen(inFullScreen)
        if (inFullScreen) {
            // Entering fullscreen - completely hide and collapse all toolbars
            expandBrowserViewForFullscreen()
            // Do not guess the video from the tab's first network URL.
            // Automatic handoff stays disabled until element/frame binding
            // and pause/restore ACKs are implemented and regression-tested.
        } else {
            // Exiting fullscreen - restore toolbars
            collapseBrowserViewFromFullscreen()
        }
    }

    /**
     * Expands the browser view to full screen by hiding all toolbar elements.
     * Follows Mozilla's pattern from Fenix.
     */
    private fun expandBrowserViewForFullscreen() {
        // The bottom tab strip is a sibling of UnifiedToolbar, not its child.
        // Hiding only UnifiedToolbar left tabs visible over fullscreen video.
        unifiedToolbar?.getBottomComponentsContainer()?.visibility = View.GONE
        unifiedToolbar?.apply {
            collapse()
            isVisible = false
        }
        requestWebContentPositionUpdate()
    }

    /**
     * Collapses the browser view from fullscreen by restoring toolbar elements.
     * Follows Mozilla's pattern from Fenix.
     */
    private fun collapseBrowserViewFromFullscreen() {
        if (!webAppToolbarShouldBeVisible) return

        unifiedToolbar?.apply {
            isVisible = true
            getBottomComponentsContainer()?.apply {
                visibility = View.VISIBLE
                alpha = 1f
                translationY = 0f
                requestLayout()
            }
            post {
                requestApplyInsets()
                expand()
                requestLayout()
                reconcileEngineViewHeight()
                requestWebContentPositionUpdate()
            }
        }
    }
    private fun showBrowserMenu() {
        val store = requireContext().components.store
        val selectedTab = store.state.tabs.find { it.id == store.state.selectedTabId }
        
        val menuItems = mutableListOf<com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem>()

        // One short diagnostic instead of three manual reports.
        // The same 40-second capture can also be started inside native MPV.
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "mpv_supertest",
                title = "SUPERTEST • 40 s • jeden raport",
                iconRes = mozilla.components.ui.icons.R.drawable.mozac_ic_settings_24,
                onClick = { startMpvSupertest() }
            )
        )
        menuItems.add(com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Divider)
        
        // QuadRow: History, Bookmarks, Print, Save as PDF
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.QuadRow(
                title1 = getString(R.string.action_history),
                icon1 = mozilla.components.ui.icons.R.drawable.mozac_ic_history_24,
                onClick1 = {
                    browserInteractor.onBrowserToolbarMenuItemTapped(ToolbarMenu.Item.History)
                },
                title2 = getString(R.string.action_bookmarks),
                icon2 = mozilla.components.ui.icons.R.drawable.mozac_ic_bookmark_24,
                onClick2 = {
                    browserInteractor.onBrowserToolbarMenuItemTapped(ToolbarMenu.Item.Bookmarks)
                },
                title3 = getString(R.string.action_print),
                icon3 = mozilla.components.ui.icons.R.drawable.mozac_ic_print_24,
                onClick3 = {
                    browserInteractor.onBrowserToolbarMenuItemTapped(ToolbarMenu.Item.Print)
                },
                title4 = getString(R.string.save_as_pdf),
                icon4 = mozilla.components.ui.icons.R.drawable.mozac_ic_save_file_24,
                onClick4 = {
                    browserInteractor.onBrowserToolbarMenuItemTapped(ToolbarMenu.Item.PDF)
                }
            )
        )

        menuItems.add(com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Divider)

        // IconRow: Find in Page, Add to Bookmarks, Add to Favorite
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.IconRow(
                items = listOf(
                    com.prirai.android.nira.components.menu.Material3BrowserMenu.IconRowItem(
                        title = getString(R.string.mozac_feature_findindpage_input),
                        iconRes = R.drawable.mozac_ic_search_24,
                        onClick = {
                            browserInteractor.onBrowserToolbarMenuItemTapped(ToolbarMenu.Item.FindInPage)
                        }
                    ),
                    com.prirai.android.nira.components.menu.Material3BrowserMenu.IconRowItem(
                        title = "Add Bookmark",
                        iconRes = mozilla.components.ui.icons.R.drawable.mozac_ic_bookmark_fill_24,
                        onClick = {
                            selectedTab?.let { tab ->
                                val title = tab.content.title.ifEmpty { tab.content.url }
                                val url = tab.content.url
                                val dialog = com.prirai.android.nira.browser.bookmark.ui.AddBookmarkSiteDialog(
                                    requireActivity(),
                                    title,
                                    url
                                )
                                dialog.setOnClickListener { _, _ ->
                                    val saved = com.prirai.android.nira.browser.bookmark.repository.BookmarkManager
                                        .getInstance(requireContext())
                                        .save()
                                    // Surface save failures instead of pretending
                                    // the write succeeded. BookmarkManager.save()
                                    // logs the underlying IOException.
                                    android.widget.Toast.makeText(
                                        requireContext(),
                                        if (saved) "Bookmark added" else "Failed to save bookmark",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                dialog.show()
                            }
                        }
                    ),
                    com.prirai.android.nira.components.menu.Material3BrowserMenu.IconRowItem(
                        title = getString(R.string.menu_add_to_favorite),
                        iconRes = mozilla.components.ui.icons.R.drawable.mozac_ic_pin_fill_24,
                        onClick = {
                            selectedTab?.let { tab ->
                                val title = tab.content.title.ifEmpty { tab.content.url }
                                val url = tab.content.url
                                val dialog = com.prirai.android.nira.browser.shortcuts.AddShortcutDialogFragment.newInstance(url, title)
                                dialog.show(parentFragmentManager, com.prirai.android.nira.browser.shortcuts.AddShortcutDialogFragment.TAG)
                            }
                        }
                    )
                )
            )
        )

        menuItems.add(com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Divider)

        // Desktop Mode Toggle
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Toggle(
                id = "desktop_mode",
                title = getString(R.string.desktop_mode),
                iconRes = mozilla.components.ui.icons.R.drawable.mozac_ic_device_desktop_24,
                isChecked = selectedTab?.content?.desktopMode ?: false,
                onToggle = { checked ->
                    browserInteractor.onBrowserToolbarMenuItemTapped(
                        ToolbarMenu.Item.RequestDesktop(checked)
                    )
                }
            )
        )

        if (UserPreferences(requireContext()).translationsEnabled &&
            selectedTab?.content?.url?.startsWith("http") == true
        ) {
            val translated = selectedTab.translationsState.isTranslated
            menuItems.add(
                com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                    id = "translate",
                    title = getString(
                        if (translated) R.string.show_original_page else R.string.translate_page
                    ),
                    iconRes = mozilla.components.ui.icons.R.drawable.mozac_ic_translate_24,
                    onClick = {
                        browserInteractor.onBrowserToolbarMenuItemTapped(ToolbarMenu.Item.Translate)
                    }
                )
            )
        }

        menuItems.add(com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Divider)

        // Add to Homescreen/Install (conditional)
        if (requireContext().components.webAppUseCases.isPinningSupported()) {
            if (requireContext().components.webAppUseCases.isInstallable()) {
                menuItems.add(
                    com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                        id = "install_webapp",
                        title = getString(R.string.install_web_app),
                        iconRes = mozilla.components.ui.icons.R.drawable.mozac_ic_device_mobile_24,
                        onClick = {
                            browserInteractor.onBrowserToolbarMenuItemTapped(ToolbarMenu.Item.InstallWebApp)
                        }
                    )
                )
            } else {
                menuItems.add(
                    com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                        id = "add_to_homescreen",
                        title = getString(R.string.action_add_to_homescreen),
                        iconRes = mozilla.components.ui.icons.R.drawable.mozac_ic_device_mobile_24,
                        onClick = {
                            browserInteractor.onBrowserToolbarMenuItemTapped(ToolbarMenu.Item.AddToHomeScreen)
                        }
                    )
                )
            }
        }

        // Open in App (conditional)
        selectedTab?.let { tab ->
            if (requireContext().components.appLinksUseCases.appLinkRedirect(tab.content.url).hasExternalApp()) {
                menuItems.add(
                    com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                        id = "open_in_app",
                        title = getString(R.string.mozac_feature_contextmenu_open_link_in_external_app),
                        iconRes = mozilla.components.ui.icons.R.drawable.mozac_ic_external_link_24,
                        onClick = {
                            browserInteractor.onBrowserToolbarMenuItemTapped(ToolbarMenu.Item.OpenInApp)
                        }
                    )
                )
            }
        }

        // Extensions
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "extensions",
                title = getString(R.string.extensions),
                iconRes = R.drawable.mozac_ic_extension_24,
                onClick = {
                    val extensionsBottomSheet = com.prirai.android.nira.addons.ExtensionsBottomSheetFragment.newInstance()
                    extensionsBottomSheet.show(parentFragmentManager, com.prirai.android.nira.addons.ExtensionsBottomSheetFragment.TAG)
                }
            )
        )

        // Downloads
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "downloads",
                title = "Downloads",
                iconRes = mozilla.components.ui.icons.R.drawable.mozac_ic_download_24,
                onClick = {
                    val downloadsBottomSheet = com.prirai.android.nira.downloads.DownloadsBottomSheetFragment.newInstance()
                    downloadsBottomSheet.show(parentFragmentManager, com.prirai.android.nira.downloads.DownloadsBottomSheetFragment.TAG)
                }
            )
        )

        // Experimental playback routing (global + per-site overrides).
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "zavro_video_routing",
                title = "Odtwarzacz wideo: AUTO / MPV / WEB / EXTERNAL",
                iconRes = mozilla.components.ui.icons.R.drawable.mozac_ic_settings_24,
                onClick = { showVideoPlayerModeDialog() }
            )
        )

        // Settings
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "settings",
                title = getString(R.string.settings),
                iconRes = mozilla.components.ui.icons.R.drawable.mozac_ic_settings_24,
                onClick = {
                    browserInteractor.onBrowserToolbarMenuItemTapped(ToolbarMenu.Item.Settings)
                }
            )
        )

        menuItems.add(com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Divider)

        // New Tab & Private Tab
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "new_tab",
                title = getString(R.string.mozac_browser_menu_new_tab),
                iconRes = R.drawable.mozac_ic_tab_new_24,
                onClick = {
                    browserInteractor.onBrowserToolbarMenuItemTapped(ToolbarMenu.Item.NewTab)
                }
            )
        )

        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "new_private_tab",
                title = getString(R.string.mozac_browser_menu_new_private_tab),
                iconRes = mozilla.components.ui.icons.R.drawable.mozac_ic_private_mode_24,
                onClick = {
                    browserInteractor.onBrowserToolbarMenuItemTapped(ToolbarMenu.Item.NewPrivateTab)
                }
            )
        )

        // Close current tab. Distinct from the back button (which walks the
        // page history) - this dispatches TabsUseCases.removeTab which flows
        // through UndoMiddleware, so the user gets the standard undo snackbar
        // if they change their mind. If no tab is selected, the item is not
        // shown.
        selectedTab?.let { tab ->
            menuItems.add(
                com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                    id = "close_tab",
                    title = getString(R.string.menu_close_tab),
                    iconRes = mozilla.components.ui.icons.R.drawable.mozac_ic_cross_24,
                    onClick = {
                        requireContext().components.tabsUseCases.removeTab(tab.id)
                    }
                )
            )
        }

        menuItems.add(com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Divider)
        
        // Toolbar Row at bottom
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.ToolbarRow(
                onBackClick = {
                    requireContext().components.sessionUseCases.goBack()
                },
                onForwardClick = {
                    requireContext().components.sessionUseCases.goForward()
                },
                onReloadClick = {
                    requireContext().components.sessionUseCases.reload()
                },
                onShareClick = {
                    browserInteractor.onBrowserToolbarMenuItemTapped(
                        ToolbarMenu.Item.Share
                    )
                },
                backEnabled = selectedTab?.content?.canGoBack ?: false,
                forwardEnabled = selectedTab?.content?.canGoForward ?: false
            )
        )
        
        // Find anchor for menu
        // When contextual toolbar is enabled, prioritize menu button from it, not from address bar.
        // Previously `showContextualToolbar` was read twice through the SharedPreferences
        // monitor on the same local; `toolbarPosition` is cached on the fragment.
        val showContextualToolbar = UserPreferences(requireContext()).showContextualToolbar
        val menuButton = view?.findViewById<android.widget.ImageButton>(R.id.menu_button)
        val toolbarView = unifiedToolbar?.getToolbarView()

        // Use menu button if contextual toolbar is showing, otherwise use toolbar view
        val anchor = if (showContextualToolbar && menuButton != null) {
            menuButton
        } else {
            toolbarView ?: menuButton ?: view
        }

        // Determine menu position: prefer bottom UNLESS toolbar is at top AND contextual toolbar is disabled
        val preferBottom = cachedToolbarPosition == com.prirai.android.nira.components.toolbar.ToolbarPosition.BOTTOM.ordinal ||
                          showContextualToolbar
        
        anchor?.let {
            com.prirai.android.nira.components.menu.Material3BrowserMenu(
                requireContext(),
                menuItems
            ).show(it, preferBottom)
        }
    }

    /** One button: record the current Gecko -> MPV lifecycle for 40 seconds.
     * The clipboard is populated automatically, including when MPV is opened.
     */
    private fun startMpvSupertest() {
        val context = requireContext()
        val state = context.components.store.state
        val tab = state.tabs.firstOrNull { it.id == state.selectedTabId }
        val session = tab?.engineState?.engineSession
        val mode = if (tab == null) "brak aktywnej karty" else VideoPlayerPolicy
            .resolve(context, tab.content.url, tab.content.private).name
        if (com.prirai.android.nira.media.MpvSupertest.start(context, session, mode)) {
            Toast.makeText(context,
                "SUPERTEST 40 s: naciśnij Play lub uruchom MPV. Jeden raport skopiuje się automatycznie.",
                Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context,
                "Supertest już trwa: ${com.prirai.android.nira.media.MpvSupertest.remainingSeconds()} s",
                Toast.LENGTH_SHORT).show()
        }
    }

    /** Read-only snapshot, taken from the native bridge after entering the menu. */
    private fun showMediaDetectorDiagnostics() {
        val store = requireContext().components.store
        fun report(): String {
            val tab = store.state.tabs.find { it.id == store.state.selectedTabId }
            return com.prirai.android.nira.media.MediaDetectorDiagnostics
                .snapshot(tab?.id, tab?.content?.private == true, tab?.contextId)
                .asPolishReport()
        }
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Diagnostyka wykrywania filmów")
            .setMessage(report())
            .setPositiveButton("Zamknij", null)
            .setNeutralButton("Odśwież", null)
            .setNegativeButton("Kopiuj raport", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.setMessage(report())
            }
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Nira media diagnostics", report()))
                android.widget.Toast.makeText(requireContext(), "Raport bez adresów skopiowany", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun showVideoPlayerModeDialog() {
        val context = requireContext()
        val labels = arrayOf("AUTO — przejmuj obsługiwane Play",
            "MPV — uruchamiaj ręcznie", "WEB — oryginalny odtwarzacz",
            "EXTERNAL — odtwarzacz zewnętrzny")
        val modes = VideoPlayerPolicy.Mode.entries.toTypedArray()
        val current = VideoPlayerPolicy.global(context)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Domyślny odtwarzacz")
            .setSingleChoiceItems(labels, modes.indexOf(current)) { dialog, choice ->
                VideoPlayerPolicy.setGlobal(context, modes[choice])
                dialog.dismiss()
                showVideoPlayerSiteDialog()
            }
            .setNeutralButton("Wyjątek dla strony") { _, _ -> showVideoPlayerSiteDialog() }
            .setNegativeButton("Anuluj", null).show()
    }

    private fun showVideoPlayerSiteDialog() {
        val context = requireContext()
        val state = context.components.store.state
        val tab = state.tabs.firstOrNull { it.id == state.selectedTabId } ?: return
        if (tab.content.private) {
            Toast.makeText(context, "Wyjątki nie są zapisywane w kartach prywatnych",
                Toast.LENGTH_LONG).show()
            return
        }
        val labels = arrayOf("Dziedzicz ustawienie globalne", "AUTO", "MPV", "WEB", "EXTERNAL")
        val options = VideoPlayerPolicy.Mode.entries.toTypedArray()
        val override = VideoPlayerPolicy.siteOverride(context, tab.content.url, false)
        val selected = if (override == null) 0 else options.indexOf(override) + 1
        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Odtwarzacz dla tej witryny")
            .setSingleChoiceItems(labels, selected) { dialog, choice ->
                VideoPlayerPolicy.setSite(context, tab.content.url, false,
                    if (choice == 0) null else options[choice - 1])
                dialog.dismiss()
            }
            .setNegativeButton("Anuluj", null).show()
    }

    private fun showOpenVideoLinkDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.video_link_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.open_video_link)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.open, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val uri = input.text.toString().trim().toUri()
                if (uri.scheme != "https" || uri.host.isNullOrBlank()) {
                    input.error = getString(R.string.video_link_invalid)
                    return@setOnClickListener
                }

                launchVideoInMpv(uri)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun launchVideoInMpv(uri: android.net.Uri, referrer: String? = null) {
        // The canonical app now embeds libmpv. No applicationId suffix toggle.
        if (!ZavroMpvActivity.allowedVideoUrl(uri)) {
            Toast.makeText(requireContext(), R.string.video_link_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        val page = referrer?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
        val safeReferrer = page?.takeIf { ZavroMpvActivity.allowedVideoUrl(it) }
            ?.buildUpon()?.clearQuery()?.fragment(null)?.build()?.toString()
        val prefs = requireContext().getSharedPreferences(
            "nira_video_player", android.content.Context.MODE_PRIVATE
        )
        val selected = requireContext().components.store.state.selectedTabId?.let { id ->
            requireContext().components.store.state.tabs.firstOrNull { it.id == id }
        }
        when (VideoPlayerPolicy.resolve(requireContext(), selected?.content?.url.orEmpty(),
                selected?.content?.private == true)) {
            VideoPlayerPolicy.Mode.WEB -> {
                Toast.makeText(requireContext(),
                    "Tryb WEB: odtwarzaj na stronie. Zmień tryb w menu przeglądarki.",
                    Toast.LENGTH_LONG).show()
                return
            }
            VideoPlayerPolicy.Mode.EXTERNAL -> {
                launchVideoExternally(uri, safeReferrer)
                return
            }
            else -> Unit // MPV manual and AUTO both allow explicit launch
        }
        if (prefs.getBoolean("ask_each_time", false)) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.choose_video_player)
                .setItems(arrayOf("MPV w Zavro", "Zewnętrzny MPV Alpha / wybór aplikacji")) { _, option ->
                    if (option == 0) launchInternalVideo(uri, safeReferrer, referrer)
                    else launchVideoExternally(uri, safeReferrer)
                }
                .show()
            return
        }
        // Manual playback from the existing detected-media panel is the only
        // native handoff in E1/E2. Automatic Play requires a per-element ACK.
        launchInternalVideo(uri, safeReferrer, referrer)
    }

    private fun launchInlineVideo(
        uri: android.net.Uri, safeReferrer: String?,
        offer: MediaElementPlaybackBridge.AutoOffer
    ) {
        if (inlineMpv != null || suspendedWebPlayback != null || !isAdded) return
        val s=requireContext().components.store.state
        val tab=s.tabs.firstOrNull { it.id == s.selectedTabId } ?: return
        val session=tab.engineState.engineSession ?: return
        val requested=MediaTabState(
            MediaSessionBinding(session,tab.id,tab.contextId,tab.content.private),
            tab.content.url,tab.content.loading)
        MediaElementPlaybackBridge.prepare(requested,uri.toString(),offer.pageUrl,offer) { lease ->
            if (lease == null) return@prepare
            if (!isAdded || view == null) {
                MediaElementPlaybackBridge.restore(lease,requested)
                return@prepare
            }
            val now=requireContext().components.store.state
            val live=now.tabs.firstOrNull { it.id == now.selectedTabId }
            if (live == null || live.engineState.engineSession !== session ||
                live.content.url != tab.content.url ||
                live.contextId != tab.contextId ||
                live.content.private != tab.content.private) {
                MediaElementPlaybackBridge.restore(lease,requested)
                return@prepare
            }
            suspendedWebPlayback=SuspendedWebPlayback(
                live.id,live.contextId,live.content.private,session,live.content.url,lease,false)
            val ui=com.prirai.android.nira.media.InlineMpvUi(
                binding.browserLayout,binding.engineView.asView(),
                requireActivity(),requireContext(),lease,session) { closeInlineMpv() }
            inlineMpv=ui
            inlinePage=live.content.url
            inlineSession=session
            val rect=MediaElementPlaybackBridge.InlineRect(
                offer.x,offer.y,offer.w,offer.h,offer.dpr)
            if (!ui.open(uri.toString(),GeckoSession.getDefaultUserAgent(),safeReferrer,rect) ||
                !MediaElementPlaybackBridge.activateInline(lease,requested)) {
                closeInlineMpv()
            } else {
                MpvDebugTrace.note(MpvDebugTrace.Section.HANDOFF,"inline_open",session)
            }
        }
    }

    private fun launchInternalVideo(
        uri: android.net.Uri,
        safeReferrer: String?,
        frameUrl: String?,
        target: MediaElementPlaybackBridge.AutoOffer? = null,
    ) {
        // Bind the handoff to the selected browser tab, GeckoSession and
        // document. Activity checks the same snapshot immediately before loadfile.
        val store = requireContext().components.store.state
        val selected = store.tabs.firstOrNull { it.id == store.selectedTabId }
        val session = selected?.engineState?.engineSession
        if (selected == null || session == null) {
            Toast.makeText(requireContext(), "Brak aktywnej karty wideo", Toast.LENGTH_SHORT).show()
            return
        }
        val requested = MediaTabState(
            MediaSessionBinding(session, selected.id, selected.contextId, selected.content.private),
            selected.content.url, selected.content.loading
        )
        // Manual opt-in only. Ask the exact frame to pause the unique matching
        // element. If its native port is absent/ambiguous, safely fall back to
        // Gecko's media-session controller for the selected tab.
        MediaElementPlaybackBridge.prepare(requested, uri.toString(), frameUrl, target) { leaseId ->
            // AUTO never pauses a random media session or launches MPV without
            // a confirmed ACK from the precise frame and element.
            if (target != null && leaseId == null) return@prepare
            if (!isAdded || view == null) {
                if (leaseId != null) MediaElementPlaybackBridge.restore(leaseId, requested)
                return@prepare
            }
            val currentState = requireContext().components.store.state
            val live = currentState.tabs.firstOrNull { it.id == currentState.selectedTabId }
            if (live == null || live.engineState.engineSession !== session ||
                live.contextId != selected.contextId ||
                live.content.private != selected.content.private ||
                live.content.url != selected.content.url) {
                if (leaseId != null) MediaElementPlaybackBridge.restore(leaseId, requested)
                Toast.makeText(requireContext(), "Zmieniła się karta filmu", Toast.LENGTH_SHORT).show()
                return@prepare
            }

            var fallbackPaused = false
            if (leaseId == null &&
                live.mediaSessionState?.playbackState == MediaSession.PlaybackState.PLAYING) {
                fallbackPaused = runCatching {
                    live.mediaSessionState?.controller?.pause()
                    true
                }.getOrDefault(false)
            }
            suspendedWebPlayback = if (leaseId != null || fallbackPaused)
                SuspendedWebPlayback(
                    live.id, live.contextId, live.content.private,
                    session, live.content.url, leaseId, fallbackPaused
                ) else null

            val ticket = com.prirai.android.nira.media.PlaybackSourceRegistry.issue(
                com.prirai.android.nira.media.PlaybackSourceRegistry.Source(
                    url = uri.toString(),
                    userAgent = GeckoSession.getDefaultUserAgent(),
                    referrer = safeReferrer,
                    owner = MediaSessionBinding(
                        session, live.id, live.contextId, live.content.private
                    ),
                    pageUrl = live.content.url,
                    autoHandoff = target != null,
                )
            )
            val intent = Intent(requireContext(), ZavroMpvActivity::class.java).apply {
                // Signed URLs and cookies never cross an Android Intent.
                putExtra(ZavroMpvActivity.EXTRA_TICKET_ID, ticket)
            }
            try {
                internalMpvLauncher.launch(intent)
            } catch (error: RuntimeException) {
                restoreWebPlaybackAfterMpv()
                com.prirai.android.nira.media.PlaybackSourceRegistry.clear()
                Toast.makeText(requireContext(), "Nie można uruchomić MPV", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun launchVideoExternally(uri: android.net.Uri, referrer: String?) {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            // MPV Alpha already accepts a validated legacy `headers` array.
            // Protocol 1 metadata is optional for older installed builds.
            val page = referrer?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
            if (page != null && page.scheme in listOf("http", "https") &&
                !page.host.isNullOrBlank() && page.userInfo == null) {
                val safeReferrer = page.buildUpon().clearQuery().fragment(null).build().toString()
                putExtra("nira.playback_protocol", 1)
                putExtra("nira.referrer", safeReferrer)
                putExtra("nira.private", currentMediaSnapshot()?.tab?.binding?.isPrivate == true)
                MpvPlaybackHeaders.build(GeckoSession.getDefaultUserAgent(), safeReferrer)
                    ?.let { putExtra("headers", it) }
            } else {
                MpvPlaybackHeaders.build(GeckoSession.getDefaultUserAgent(), null)
                    ?.let { putExtra("headers", it) }
            }
        }
        // Signed MPV Alpha debug builds use the .debug applicationId suffix.
        // Prefer Alpha to the older release (REX Player), without an Android chooser.
        for (packageName in listOf("xyz.mpv.rex.debug", "xyz.mpv.rex")) {
            try {
                startActivity(Intent(viewIntent).setClassName(
                    packageName, "xyz.mpv.rex.ui.player.PlayerActivity"
                ))
                return
            } catch (_: ActivityNotFoundException) {
                // Try the other signed variant before falling back to Android.
            } catch (_: SecurityException) {
                // Explicit player not accessible on this device.
            } catch (_: IllegalArgumentException) {
                // An OEM resolver might reject the explicit activity.
            }
        }
        openVideoChooser(viewIntent)
    }

    private fun openVideoChooser(viewIntent: Intent) {
        try {
            startActivity(Intent.createChooser(viewIntent, getString(R.string.choose_video_player)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.no_video_player, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(requireContext(), R.string.no_video_player, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed(): Boolean {
        // First, try the default back handling (reader mode, find in page, fullscreen, etc.)
        val handled = super.onBackPressed()
        
        if (!handled) {
            // If nothing handled the back press, check if we can go back in browser history
            val store = requireContext().components.store
            val selectedTab = store.state.tabs.find { it.id == store.state.selectedTabId }
            
            // If we can't go back in history, navigate to home fragment
            if (selectedTab != null && !selectedTab.content.canGoBack) {
                try {
                    val navController = androidx.navigation.fragment.NavHostFragment.findNavController(this)
                    // Only navigate if we're not already on home fragment
                    if (navController.currentDestination?.id != R.id.homeFragment) {
                        // Signal to home fragment to prevent auto-navigation back
                        com.prirai.android.nira.browser.home.ComposeHomeFragment.navigateToHome()
                        navController.navigate(R.id.homeFragment)
                        return true
                    }
                } catch (e: Exception) {
                    // Navigation failed, let system handle it
                }
            }
        }
        
        return handled
    }
    
    /**
     * Initialize homepage ViewModel for shortcuts and bookmarks. Uses the
     * process-wide singleton from [com.prirai.android.nira.browser.shortcuts.ShortcutDatabase.getInstance]
     * so schema migrations only live in one place.
     */
    private fun initializeHomeViewModel() {
        val database = com.prirai.android.nira.browser.shortcuts.ShortcutDatabase
            .getInstance(requireContext())

        val factory = com.prirai.android.nira.browser.home.compose.HomeViewModelFactory(
            bookmarkManager = com.prirai.android.nira.browser.bookmark.repository.BookmarkManager.getInstance(requireContext()),
            shortcutDao = database.shortcutDao(),
            historyStorage = requireContext().components.historyStorage,
        )

        homeViewModel = androidx.lifecycle.ViewModelProvider(this, factory)[com.prirai.android.nira.browser.home.compose.HomeViewModel::class.java]
    }
    
    /**
     * Setup homepage Compose view with all the home screen content
     */
    private fun setupHomePageComposeView() {
        val homePageView = binding.root.findViewById<androidx.compose.ui.platform.ComposeView>(
            R.id.homePageComposeView
        ) ?: return
        
        homePageView.setViewCompositionStrategy(
            androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        
        // Render the actual homepage content (same as ComposeHomeFragment)
        homePageView.setContent {
            val store = requireContext().components.store
            val browsingModeManager = (requireActivity() as BrowserActivity).browsingModeManager
            
            // Observe selected tab
            val selectedTabId by store.observeAsComposableState { state -> state.selectedTabId }
            
            val selectedTab = selectedTabId?.let { id ->
                store.state.tabs.find { it.id == id }
            }

            val isPrivateMode = selectedTab?.content?.private ?: browsingModeManager.mode.isPrivate

            val shortcuts by homeViewModel.shortcuts.collectAsState()
            val showAddDialog by homeViewModel.showAddShortcutDialog.collectAsState()
            val jumpBackInHistory by homeViewModel.jumpBackInHistory.collectAsState()
            val recentsKey by store.observeAsComposableState { state ->
                Triple(
                    state.selectedTabId,
                    state.tabs.map { it.id to it.lastAccess },
                    state.closedTabs.map { it.id },
                )
            }
            val jumpBackInItems = androidx.compose.runtime.remember(recentsKey, jumpBackInHistory) {
                store.state.jumpBackInItems(jumpBackInHistory)
            }
            val recentlyClosedItems by store.observeAsComposableState { state ->
                state.recentlyClosedItems()
            }

            val profileManager =
                com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
            val currentProfile = run {
                val tabContextId = selectedTab?.contextId
                when {
                    tabContextId == "private" || isPrivateMode -> {
                        com.prirai.android.nira.browser.home.compose.ProfileInfo("private", "Private", "🕵️", true, 0)
                    }

                    tabContextId != null && tabContextId.startsWith("profile_") -> {
                        val profileId = tabContextId.removePrefix("profile_")
                        val profile = profileManager.getAllProfiles().find { it.id == profileId }
                            ?: profileManager.getActiveProfile()
                        com.prirai.android.nira.browser.home.compose.ProfileInfo(profile.id, profile.name, profile.emoji, false, profile.color)
                    }

                    else -> {
                        val profile = profileManager.getActiveProfile()
                        com.prirai.android.nira.browser.home.compose.ProfileInfo(profile.id, profile.name, profile.emoji, false, profile.color)
                    }
                }
            }

            val prefs = UserPreferences(requireContext())
            val backgroundImageUrl = when (prefs.homepageBackgroundChoice) {
                com.prirai.android.nira.settings.HomepageBackgroundChoice.NONE.ordinal -> null
                com.prirai.android.nira.settings.HomepageBackgroundChoice.URL.ordinal,
                com.prirai.android.nira.settings.HomepageBackgroundChoice.GALLERY.ordinal -> prefs.homepageBackgroundUrl
                else -> null
            }
            
            val isToolbarAtTop = prefs.toolbarPosition == com.prirai.android.nira.components.toolbar.ToolbarPosition.TOP.ordinal
            val homepageType = prefs.homepageType
            val wallpaperDimmed = prefs.keepWallpaperDimmed

            com.prirai.android.nira.ui.theme.NiraTheme(
                isPrivateMode = isPrivateMode,
                amoledMode = prefs.amoledMode,
                dynamicColor = prefs.dynamicColors
            ) {
                com.prirai.android.nira.browser.home.compose.HomeScreen(
                    isPrivateMode = isPrivateMode,
                    shortcuts = shortcuts,
                    currentProfile = currentProfile,
                    onProfileClick = {}, // Profile icon is display-only
                    backgroundImageUrl = backgroundImageUrl,
                    isToolbarAtTop = isToolbarAtTop,
                    homepageType = homepageType,
                    wallpaperDimmed = wallpaperDimmed,
                    onShortcutClick = { shortcut ->
                        components.sessionUseCases.loadUrl(shortcut.url)
                        // URL will change, triggering visibility update automatically
                    },
                    onShortcutDelete = { shortcut ->
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Delete Shortcut")
                            .setMessage("Are you sure you want to delete '${shortcut.title}'?")
                            .setPositiveButton("Delete") { _, _ ->
                                homeViewModel.deleteShortcut(shortcut)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    },
                    onShortcutAdd = { homeViewModel.showAddShortcutDialog() },
                    onHistoryClick = {
                        startActivity(
                            android.content.Intent(
                                requireContext(),
                                com.prirai.android.nira.history.HistoryActivity::class.java
                            )
                        )
                    },
                    onBookmarksClick = {
                        com.prirai.android.nira.browser.bookmark.ui.BookmarksBottomSheetFragment
                            .newInstance()
                            .show(parentFragmentManager, "BookmarksBottomSheet")
                    },
                    jumpBackInItems = jumpBackInItems,
                    recentlyClosedItems = recentlyClosedItems.orEmpty(),
                    onJumpBackInClick = { item ->
                        val tabId = item.tabId
                        if (tabId != null) {
                            components.tabsUseCases.selectTab(tabId)
                        } else {
                            components.sessionUseCases.loadUrl(item.url)
                        }
                    },
                    onRecentlyClosedClick = { item ->
                        val tab = store.state.closedTabs.find { it.id == item.id } ?: return@HomeScreen
                        viewLifecycleOwner.lifecycleScope.launch {
                            components.tabsUseCases.restore(
                                tab,
                                components.recentlyClosedTabsStorage.engineStateStorage(),
                            )
                            store.dispatch(
                                mozilla.components.browser.state.action.RecentlyClosedAction.RemoveClosedTabAction(tab)
                            )
                        }
                    },
                    onSearchClick = {
                        // Open search dialog
                        val sessionId = components.store.state.selectedTabId
                        val directions = NavGraphDirections.actionGlobalSearchDialog(sessionId = sessionId)
                        try {
                            androidx.navigation.fragment.NavHostFragment.findNavController(this@BrowserFragment).navigate(directions)
                        } catch (e: Exception) {
                            // Navigation failed
                        }
                    }
                )

                if (showAddDialog) {
                    com.prirai.android.nira.browser.home.compose.AddShortcutDialog(
                        onDismiss = { homeViewModel.hideAddShortcutDialog() },
                        onSave = { url, title ->
                            homeViewModel.addShortcut(url, title)
                        }
                    )
                }
            }
        }
    }
    
    /**
     * Update content visibility based on current URL
     * Shows homepage ComposeView for "about:homepage", otherwise shows engineView
     */
    private fun updateContentVisibility(url: String) {
        val binding = bindingOrNull ?: return
        val isHomepage = url == "about:homepage"

        val homePageView = binding.root.findViewById<androidx.compose.ui.platform.ComposeView>(
            R.id.homePageComposeView
        )

        // Keep EngineView attached. GONE detaches Gecko's surface and later selectTab
        // can leave the last frame stuck (AC#6664 / bug 1630775). Homepage sits on top.
        binding.swipeRefresh.visibility = View.VISIBLE
        binding.engineView.asView().visibility = View.VISIBLE
        homePageView?.apply {
            visibility = if (isHomepage) View.VISIBLE else View.GONE
            isClickable = isHomepage
            isFocusable = isHomepage
        }
    }
}
