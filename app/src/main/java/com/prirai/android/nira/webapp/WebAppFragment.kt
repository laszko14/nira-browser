package com.prirai.android.nira.webapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.browser.profile.BrowserProfile
import com.prirai.android.nira.browser.profile.ProfileManager
import mozilla.components.concept.engine.HitResult
import kotlinx.coroutines.launch
import mozilla.components.browser.engine.gecko.GeckoEngineView
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.window.WindowRequest
import androidx.core.net.toUri

/**
 * Fragment for displaying PWA content in fullscreen mode
 * No toolbar, no URL bar, just pure web content
 */
class WebAppFragment : Fragment(), EngineSession.Observer {

    companion object {
        private const val ARG_WEB_APP_URL = "web_app_url"
        private const val ARG_PROFILE_ID = "profile_id"

        fun newInstance(url: String, profileId: String = "default"): WebAppFragment {
            return WebAppFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WEB_APP_URL, url)
                    putString(ARG_PROFILE_ID, profileId)
                }
            }
        }
    }

    /** Used by the Activity to avoid unnecessary Gecko-session replacement on rotation/reopen. */
    fun matchesLaunch(url: String, profile: String): Boolean =
        arguments?.getString(ARG_WEB_APP_URL) == url &&
            arguments?.getString(ARG_PROFILE_ID) == profile

    private lateinit var engineView: GeckoEngineView
    private var engineSession: EngineSession? = null
    private var profileId: String = "default"
    private var startUrl: String? = null
    private var canGoBack: Boolean = false
    private var hitResultDialog: android.app.AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_webapp, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        engineView = view.findViewById(R.id.engineView)
        
        // Get URL and profile from arguments
        val url = arguments?.getString(ARG_WEB_APP_URL)
        profileId = arguments?.getString(ARG_PROFILE_ID) ?: "default"
        startUrl = url
        
        if (url.isNullOrEmpty()) {
            activity?.finish()
            return
        }
        
        // PWA owns a standalone Gecko session; its long-press events must
        // never be routed through the unrelated main BrowserStore.selectedTab.
        // Setup keyboard adjustment
        setupKeyboardAdjustment()
        
        // Create a session with the specified profile context
        lifecycleScope.launch {
            try {
                val engine = requireContext().components.engine
                // Canonical default Gecko partition is null. Do not silently
                // merge or delete legacy profile_default storage on upgrade.
                val known = ProfileManager.getInstance(requireContext()).getAllProfiles()
                    .any { it.id == profileId }
                if (!known) profileId = "default"
                val contextId = BrowserProfile.contextIdFor(profileId, isPrivate = false)
                
                engineSession = engine.createSession(private = false, contextId = contextId)
                
                // Extract domain from URL
                val webappDomain = try {
                    java.net.URL(url).host
                } catch (e: Exception) {
                    url
                }
                
                // Register this session with the request interceptor
                requireContext().components.appRequestInterceptor.registerWebAppSession(
                    engineSession!!,
                    webappDomain,
                    profileId
                )
                
                // Register as observer to track navigation
                engineSession?.register(this@WebAppFragment)
                
                // Link the engine session to the view
                engineView.render(engineSession!!)
                
                // Load the URL
                engineSession?.loadUrl(url)
                
            } catch (e: Exception) {
                activity?.finish()
            }
        }
    }

    override fun onDestroyView() {
        hitResultDialog?.dismiss()
        hitResultDialog = null
        super.onDestroyView()
        engineSession?.let {
            try {
                requireContext().components.appRequestInterceptor.unregisterWebAppSession(it)
            } catch (e: Exception) {
                // Context might be detached
            }
        }
        engineSession?.unregister(this)
        engineView.release()
        engineSession?.close()
        engineSession = null
    }
    
    override fun onWindowRequest(windowRequest: WindowRequest) {
        // Handle new window requests (e.g., target="_blank" links)
        when (windowRequest.type) {
            WindowRequest.Type.OPEN -> {
                // External link - open in custom tab with same profile
                openInCustomTab(windowRequest.url, profileId)
                // Consume the request
                windowRequest.prepare()
                windowRequest.start()
            }
            WindowRequest.Type.CLOSE -> {
                // Close request - ignore for webapps
            }
        }
    }

    override fun onNavigationStateChange(canGoBack: Boolean?, canGoForward: Boolean?) {
        // Track navigation state
        canGoBack?.let { this.canGoBack = it }
    }

    fun handleBackPressed(): Boolean {
        // Check if we can go back in navigation history
        val session = engineSession ?: return false
        
        // Use tracked canGoBack state
        return if (this.canGoBack) {
            session.goBack()
            true
        } else {
            // No history to go back to - let the activity close the webapp
            false
        }
    }
    
    override fun onLongPress(hitResult: HitResult) {
        // EngineSession.Observer callback is from *this* PWA's Gecko session.
        // Using ContextMenuIntegration(tabId=null) would instead read hitResult
        // from BrowserStore.selectedTab, which can belong to another profile.
        val target = PwaHitResultMenu.safeTarget(hitResult) ?: return
        activity?.runOnUiThread {
            if (!isAdded || view == null) return@runOnUiThread
            hitResultDialog?.dismiss()
            val options = arrayOf("Open in browser", "Copy address", "Share link")
            hitResultDialog = android.app.AlertDialog.Builder(requireContext())
                .setTitle(target)
                .setItems(options) { _, selected ->
                    when (selected) {
                        0 -> openInCustomTab(target, profileId)
                        1 -> {
                            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("PWA link", target))
                        }
                        2 -> {
                            val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, target)
                            }
                            startActivity(android.content.Intent.createChooser(share, "Share link"))
                        }
                    }
                }.setNegativeButton(android.R.string.cancel, null).create().also { it.show() }
        }
    }

    /**
     * Adjusts engine view bottom padding when keyboard appears to prevent content overlap.
     * Uses WindowInsets to detect keyboard visibility and automatically adjust layout.
     */
    private fun setupKeyboardAdjustment() {
        // Adjust engine view position when keyboard appears
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(engineView as View) { view, insets ->
            val imeInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            val systemBarsInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            // Adjust bottom padding when keyboard appears
            if (imeInsets.bottom > systemBarsInsets.bottom) {
                // Keyboard is showing, add bottom padding
                val keyboardHeight = imeInsets.bottom - systemBarsInsets.bottom
                view.setPadding(0, 0, 0, keyboardHeight)
            } else {
                // Keyboard is hidden, reset padding
                view.setPadding(0, 0, 0, 0)
            }
            
            insets
        }
        
        // Request initial insets
        androidx.core.view.ViewCompat.requestApplyInsets(engineView as View)
    }
    
    private fun openInCustomTab(url: String, profileId: String) {
        try {
            val customTabIntent = androidx.browser.customtabs.CustomTabsIntent.Builder().build()
            customTabIntent.intent.putExtra("PROFILE_ID", profileId)
            // Target Nira specifically so the custom tab inherits the correct profile context
            customTabIntent.intent.setPackage(requireContext().packageName)
            customTabIntent.launchUrl(requireContext(), url.toUri())
        } catch (e: Exception) {
            // Fallback: open in default system browser
            try {
                val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
                startActivity(fallbackIntent)
            } catch (ex: Exception) {
                // Ignore if no browser available
            }
        }
    }
    
    private fun openInBrowser(url: String, profileId: String) {
        try {
            val intent = android.content.Intent(requireContext(), com.prirai.android.nira.BrowserActivity::class.java)
            intent.action = android.content.Intent.ACTION_VIEW
            intent.data = url.toUri()
            intent.putExtra("PROFILE_ID", profileId)
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback: open in default system browser
            try {
                val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                    url.toUri())
                startActivity(fallbackIntent)
            } catch (ex: Exception) {
                // Ignore if no browser available
            }
        }
    }

}
