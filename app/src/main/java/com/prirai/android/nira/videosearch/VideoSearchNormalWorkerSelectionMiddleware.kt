package com.prirai.android.nira.videosearch

import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

/** Never miss select→deselect while Flow subscribers are suspended. */
internal class VideoSearchNormalWorkerSelectionMiddleware(
    private val workers: VideoSearchNormalWorkerRegistry =
        VideoSearchNormalWorkerSelectionHost.registry,
) : Middleware<BrowserState, BrowserAction> {
    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        next(action)
        workers.observeSelection(store.state.selectedTabId)
    }
}
