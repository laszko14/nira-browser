package com.prirai.android.nira.videosearch

import android.content.Context
import com.prirai.android.nira.ext.components

/**
 * Stage 8C: both entry points use one normal-profile library route.
 * Re-select an already open matching library tab instead of duplicating it.
 * Never reuse a private, foreign-profile or lookalike web tab.
 */
internal data class VideoSavedFolderTabCandidate(
    val id: String,
    val url: String?,
    val contextId: String?,
    val isPrivate: Boolean,
)

internal object VideoSavedFoldersTabOpener {
    fun reusableTabId(
        scope: VideoSearchScope.Normal,
        candidates: Iterable<VideoSavedFolderTabCandidate>,
    ): String? = candidates.firstOrNull { candidate ->
        candidate.id.isNotBlank() &&
            VideoSavedFoldersTabRoute.normalOwner(
                candidate.url, candidate.contextId, candidate.isPrivate,
                scope.profileId,
            ) == scope.profileId
    }?.id

    fun open(context: Context, scope: VideoSearchScope.Normal): String {
        require(isActiveNormalProfile(context, scope.profileId)) {
            "Saved film folders require the active normal profile"
        }
        val browser = context.applicationContext.components
        val reusable = reusableTabId(scope, browser.store.state.tabs.map { tab ->
            VideoSavedFolderTabCandidate(
                tab.id, tab.content.url, tab.contextId, tab.content.private,
            )
        })
        if (reusable != null) {
            browser.tabsUseCases.selectTab(reusable)
            return reusable
        }
        return browser.tabsUseCases.addTab.invoke(
            VIDEO_SAVED_FOLDERS_TAB_URL,
            selectTab = true, private = false, contextId = scope.contextId,
        )
    }
}

/**
 * View-bound bridge between BrowserFragment's Back priority and Compose folder
 * state. An old composition cannot unregister its newer replacement.
 * Main-thread only; no global state, URL or saved data is kept here.
 */
internal class VideoSavedFoldersBackDispatcher {
    private class Registration(
        val tabId: String,
        val profileId: String,
        val navigateUp: () -> Boolean,
    )
    private var active: Registration? = null

    fun register(tabId: String, profileId: String, navigateUp: () -> Boolean): () -> Unit {
        require(tabId.isNotBlank() && profileId.isNotBlank())
        val registration = Registration(tabId, profileId, navigateUp)
        active = registration
        return { if (active === registration) active = null }
    }

    fun handle(tabId: String, profileId: String?): Boolean {
        val registration = active ?: return false
        if (profileId == null || registration.tabId != tabId ||
            registration.profileId != profileId) return false
        return registration.navigateUp()
    }

    fun clear() { active = null }
}
