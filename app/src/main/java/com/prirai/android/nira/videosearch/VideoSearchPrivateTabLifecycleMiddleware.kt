package com.prirai.android.nira.videosearch

import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

/**
 * Process-owned BrowserStore private-lifetime observer.
 *
 * Store.dispatch runs synchronously: each individual tab-list action is
 * reconciled after the reducer. A close-last followed immediately by a new
 * private tab therefore observes zero and retires the old RAM epoch, even
 * when a Flow collector could have conflated the intermediate inventory.
 *
 * No Activity, delay, persistent ID, poller, or file storage is involved.
 * Explicit profile migration uses RemoveTabAction + AddTabAction; moving out
 * of private retires the old epoch and remaining private tabs retain theirs.
 */
internal class VideoSearchPrivateTabLifecycleMiddleware(
    private val activeProfileId: () -> String,
    private val registry: VideoSearchPrivateSessionRegistry = VideoSearchPrivateSessionHost.registry,
) : Middleware<BrowserState, BrowserAction> {
    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        next(action)
        // Do not allocate an inventory for every content or media event.
        if (action !is TabListAction) return
        registry.reconcile(
            store.state.tabs.map { tab ->
                VideoSearchPrivateTab(
                    id = tab.id, contextId = tab.contextId,
                    isPrivate = tab.content.private,
                )
            },
            activeProfileId(),
        )
    }
}
