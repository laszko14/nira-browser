package com.prirai.android.nira.browser.tabgroups

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

/**
 * Middleware that monitors tab creation and applies cross-domain grouping logic.
 *
 * The [UnifiedTabGroupManager] is injected as a [Lazy] so that merely adding
 * this middleware to the [mozilla.components.browser.state.store.BrowserStore]
 * middleware chain does not force construction of the manager. The manager
 * opens a Room database (`TabGroupDatabase.getInstance`) on construction;
 * resolving it eagerly during `BrowserStore` build - which runs on whichever
 * thread first touches `components.store` - would pull Room's class graph
 * onto the main thread during cold start.
 *
 * Instead, we resolve the lazy on the first action-dispatch that actually
 * needs grouping (via [groupManager]), which happens well after startup.
 */
class TabGroupMiddleware(
    private val lazyTabGroupManager: Lazy<UnifiedTabGroupManager>
) : Middleware<BrowserState, BrowserAction> {

    companion object {
        private const val TAG = "TabGroupMiddleware"
    }

    private val tabGroupManager: UnifiedTabGroupManager
        get() = lazyTabGroupManager.value

    // Track tabs pending grouping (tab ID -> parent ID)
    private val pendingGrouping = mutableMapOf<String, String?>()
    
    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction
    ) {
        // Process the action first
        next(action)
        
        // Handle post-action state changes
        when (action) {
            is TabListAction.AddTabAction -> {
                handleNewTab(store.state, action)
            }
            is ContentAction.UpdateUrlAction -> {
                handleUrlUpdate(store.state, action)
            }
            is TabListAction.SelectTabAction -> {
                handleTabSelection(store.state, action)
            }
            else -> {
                // No action needed for other types
            }
        }
    }
    
    /**
     * Handles new tab creation to determine if it should be auto-grouped.
     *
     * This method analyzes newly created tabs to detect if they were opened from
     * a link in another tab. If so, it automatically groups them together to
     * maintain browsing context.
     *
     * Detection logic:
     * - Checks if tab has a parentId (set by context menu "open in new tab")
     * - Falls back to using currently selected tab as source
     * - Skips tabs with blank URLs (waiting for actual URL to load)
     * - Skips system URLs (about:, chrome:)
     *
     * Grouping triggers:
     * - Any tab opened from another tab with a valid URL
     * - Both same-domain and cross-domain links
     * - Delegates actual grouping to UnifiedTabGroupManager.handleNewTabFromLink()
     *
     * Logging:
     * - All decisions are logged with tag "TabGroupMiddleware"
     * - Useful for debugging why tabs are/aren't grouped
     * - Use: adb logcat | grep TabGroupMiddleware
     *
     * @param state Current browser state containing all tabs
     * @param action The AddTabAction containing the new tab
     */
    private fun handleNewTab(state: BrowserState, action: TabListAction.AddTabAction) {
        val newTab = action.tab
        // Never infer an implicit parent from the currently selected tab.
        val parentId = newTab.parentId ?: return
        val url = newTab.content.url
        if (url.isBlank() || url == "about:blank") {
            pendingGrouping[newTab.id] = parentId
            return
        }
        if (url.startsWith("about:") || url.startsWith("chrome:")) return
        val source = state.tabs.firstOrNull { it.id == parentId } ?: return
        if (source.contextId != newTab.contextId ||
            source.content.private != newTab.content.private) return
        val sourceUrl = source.content.url
        if (sourceUrl.isBlank() || sourceUrl == "about:blank") return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                tabGroupManager.handleNewTabFromLink(
                    newTabId = newTab.id, newTabUrl = url,
                    sourceTabId = source.id, sourceTabUrl = sourceUrl,
                    contextId = source.contextId
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to group explicitly parented tab", e)
            }
        }
    }

    /**
     * Handles URL updates for tabs that were created with blank URLs.
     * This catches tabs opened from context menu that initially have no URL.
     */
    private fun handleUrlUpdate(state: BrowserState, action: ContentAction.UpdateUrlAction) {
        val tabId = action.sessionId
        val newUrl = action.url
        
        // Check if this tab is pending grouping
        val parentId = pendingGrouping.remove(tabId) ?: return
        // Skip system URLs
        if ((newUrl.startsWith("about:") && newUrl != "about:homepage") || newUrl.startsWith("chrome:")) {
            return
        }
        
        // Find the parent tab
        val parentTab = state.tabs.find { it.id == parentId }
        if (parentTab == null) return
        val newTab = state.tabs.firstOrNull { it.id == tabId } ?: return
        if (parentTab.contextId != newTab.contextId ||
            parentTab.content.private != newTab.content.private) return

        val parentUrl = parentTab.content.url
        if (parentUrl.isBlank() || parentUrl == "about:blank") {
            return
        }
        // Group the tabs
        CoroutineScope(Dispatchers.IO).launch {
            try {
                tabGroupManager.handleNewTabFromLink(
                    newTabId = tabId,
                    newTabUrl = newUrl,
                    sourceTabId = parentId,
                    sourceTabUrl = parentUrl,
                    contextId = parentTab.contextId
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to group tabs after URL update", e)
            }
        }
    }
    
    private fun handleTabSelection(state: BrowserState, action: TabListAction.SelectTabAction) {
        // UnifiedTabGroupManager handles state updates automatically via StateFlow
        // No manual refresh needed
    }

}