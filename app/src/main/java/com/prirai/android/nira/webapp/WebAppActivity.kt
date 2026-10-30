package com.prirai.android.nira.webapp

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.feature.pwa.ext.getWebAppManifest
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ext.enableEdgeToEdgeMode
import com.prirai.android.nira.ext.applyPersistentInsets

/**
 * Activity for Progressive Web Apps (PWAs) - Fullscreen, no browser chrome
 * Provides a native app experience for installed web apps
 */
class WebAppActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WEB_APP_URL = "extra_web_app_url"
        const val EXTRA_WEB_APP_MANIFEST = "extra_web_app_manifest"
    }

    private var notificationPermissionCallback: ((Boolean) -> Unit)? = null

    private val requestNotificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            notificationPermissionCallback?.invoke(isGranted)
            notificationPermissionCallback = null
        }

    private val launchGate = WebAppLaunchGate()
    private var openJob: Job? = null
    private var iconJob: Job? = null
    private var pendingOpen: PendingOpen? = null

    private data class PendingOpen(
        val ticket: WebAppLaunchGate.Ticket,
        val profileId: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webapp)
        setupSystemBars()

        val url = extractUrlFromIntent(intent)
        if (url.isNullOrEmpty()) {
            finish()
            return
        }
        openWebApp(url)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fragment = supportFragmentManager.findFragmentById(R.id.webapp_container) as? WebAppFragment
                if (fragment?.handleBackPressed() != true) finishAndRemoveTask()
            }
        })
    }

    private suspend fun getProfileIdForUrl(url: String): String = withContext(Dispatchers.IO) {
        val found = components.webAppManager.getWebAppByUrl(url)?.profileId ?: "default"
        val valid = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(this@WebAppActivity)
            .getAllProfiles().any { it.id == found }
        if (valid && !com.prirai.android.nira.browser.profile.ProfilePwaLiveSessions.isDeleting(found)) found
        else "default"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractUrlFromIntent(intent)?.let(::openWebApp)
    }

    private fun openWebApp(url: String) {
        val ticket = launchGate.accept(url)
        openJob?.cancel()
        iconJob?.cancel()
        pendingOpen = null
        openJob = lifecycleScope.launch {
            val profileId = getProfileIdForUrl(url)
            if (!launchGate.isCurrent(ticket) || isFinishing || isDestroyed) return@launch

            val requested = PendingOpen(ticket, profileId)
            installOrDefer(requested)
            iconJob = lifecycleScope.launch { updatePwaTaskDescription(ticket) }
        }
    }

    private fun installOrDefer(request: PendingOpen) {
        if (!launchGate.isCurrent(request.ticket) || isFinishing || isDestroyed) return
        if (supportFragmentManager.isStateSaved) {
            pendingOpen = request
            return
        }

        pendingOpen = null
        val fragment = supportFragmentManager.findFragmentById(R.id.webapp_container) as? WebAppFragment
        if (fragment?.matchesLaunch(request.ticket.url, request.profileId) == true) {
            // FragmentManager has already restored/opened this exact PWA.
            return
        }
        // A new intent arriving before onCreate finishes must be able to
        // create the FIRST fragment, not merely replace an existing one.
        supportFragmentManager.beginTransaction()
            .replace(R.id.webapp_container, WebAppFragment.newInstance(
                request.ticket.url, request.profileId
            ))
            .commit()
    }

    override fun onPostResume() {
        super.onPostResume()
        pendingOpen?.let(::installOrDefer)
    }

    private suspend fun updatePwaTaskDescription(ticket: WebAppLaunchGate.Ticket) {
        val url = ticket.url
        val webApp = withContext(Dispatchers.IO) {
            components.webAppManager.getWebAppByUrl(url)
        }
        if (!launchGate.isCurrent(ticket) || isFinishing || isDestroyed) return
        // Replace the old recents icon immediately, even if the new app has
        // no icon. An old asynchronous lookup must never overwrite B with A.
        val title = webApp?.name ?: runCatching { java.net.URI(url).host }.getOrNull() ?: url
        setTaskDescription(android.app.ActivityManager.TaskDescription(title))
        webApp ?: return
        val icon = withContext(Dispatchers.IO) {
            components.webAppManager.loadIconFromFile(webApp.iconUrl)
                ?: com.prirai.android.nira.utils.FaviconLoader.loadFavicon(this@WebAppActivity, url)
                ?: com.prirai.android.nira.utils.FaviconLoader.loadFaviconForPwa(
                    this@WebAppActivity, url, size = 128
                )
        }
        if (icon != null && launchGate.isCurrent(ticket) && !isFinishing && !isDestroyed) {
            setTaskDescription(android.app.ActivityManager.TaskDescription(webApp.name, icon))
        }
    }

    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent == null) return null


        intent.getStringExtra(EXTRA_WEB_APP_URL)?.let { return it.takeIf(::isWebLaunchUrl) }

        intent.getWebAppManifest()?.startUrl?.let { return it.takeIf(::isWebLaunchUrl) }

        intent.data?.toString()?.let { return it.takeIf(::isWebLaunchUrl) }

        if (intent.action == Intent.ACTION_VIEW) {
            intent.dataString?.let { return it.takeIf(::isWebLaunchUrl) }
        }

        return null
    }

    private fun isWebLaunchUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme?.lowercase() in setOf("http", "https") &&
            !(uri.host ?: value.toUri().host).isNullOrBlank()
    }.getOrDefault(false)

    private fun setupSystemBars() {
        // Enable edge-to-edge with standardized approach
        enableEdgeToEdgeMode()
        
        // Extract theme color from PWA manifest if available
        val manifest = intent.getWebAppManifest()
        val themeColor = manifest?.themeColor
        
        // Apply theme color to status bar if provided
        if (themeColor != null) {
            window.statusBarColor = themeColor
        }
        
        // Apply insets to webapp container
        findViewById<View>(R.id.webapp_container)?.applyPersistentInsets()
    }

    /**
     * Request notification permission for PWA notifications (Android 13+)
     *
     * @param callback Called with true if permission granted, false if denied
     */
    fun requestNotificationPermission(callback: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionCallback = callback
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Pre-Android 13, notification permission is granted by default
            callback(true)
        }
    }

}
