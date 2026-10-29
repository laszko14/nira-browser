package com.prirai.android.nira.videosearch

/**
 * Main-thread, per-worker private tab ownership token. A private tab is NOT
 * reclaimable merely because it currently is not selected. Selection is an
 * irreversible adoption signal for this lease, even after focus moves away.
 * No URL, query, cookies, Context or Gecko objects are persisted.
 *
 * EngineSession is retained by reference only while the worker is running.
 * A session replacement cannot silently continue a crawl or be reclaimed.
 */
internal class VideoSearchPrivateWorkerLease(
    val tabId: String,
    val owner: VideoSearchScope.Private,
) {
    private var adopted = false
    private var boundSession: Any? = null

    val hasBoundSession: Boolean get() = boundSession != null

    fun observeSelection(selectedTabId: String?) {
        if (selectedTabId == tabId) adopted = true
    }

    fun canUse(selectedTabId: String?): Boolean {
        observeSelection(selectedTabId)
        return !adopted
    }

    fun bindSession(session: Any?): Boolean {
        if (session == null || adopted) return false
        if (boundSession == null) boundSession = session
        return boundSession === session
    }

    fun acceptsSession(session: Any?): Boolean =
        boundSession == null || boundSession === session

    fun mayReclaim(
        tab: VideoSearchPrivateTab?,
        selectedTabId: String?,
        currentOwner: VideoSearchScope.Private?,
        sameEngine: Boolean,
        safeDocument: Boolean,
    ): Boolean =
        canUse(selectedTabId) && tab?.id == tabId &&
            tab.isPrivate && tab.contextId == "private" &&
            currentOwner == owner && sameEngine && safeDocument
}
