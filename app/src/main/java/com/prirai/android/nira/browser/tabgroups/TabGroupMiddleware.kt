package com.prirai.android.nira.browser.tabgroups

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

/**
 * Store-lifetime handler for explicit "open in group" requests only.
 * parentId is navigation ancestry: AddTab/URL/selection/restore never imply grouping.
 * Normal browsing does not resolve the lazy Room manager or enqueue deferred work.
 */
class TabGroupMiddleware(
    private val lazyTabGroupManager: Lazy<UnifiedTabGroupManager>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : Middleware<BrowserState, BrowserAction> {
    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction
    ) = next(action)

    /** Called solely by the distinct context-menu command, with the returned new tab ID. */
    fun groupExplicitlyOpenedTab(
        newTabId: String,
        newTabUrl: String,
        sourceTabId: String,
        sourceTabUrl: String,
        contextId: String?
    ) = scope.launch {
        try {
            lazyTabGroupManager.value.groupExplicitlyOpenedLink(
                newTabId, newTabUrl, sourceTabId, sourceTabUrl, contextId
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("TabGroupMiddleware", "Explicit group request failed", error)
        }
    }
}
