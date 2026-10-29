package com.prirai.android.nira.videosearch

/** A normal background worker must never take over a user-owned tab. */
internal data class VideoSearchNormalWorkerTab(
    val id: String,
    val contextId: String?,
    val isPrivate: Boolean,
)

/** No URLs or Gecko objects are persisted. Main-thread BrowserStore lifetime. */
internal class VideoSearchNormalWorkerLease(
    val tabId: String,
    val owner: VideoSearchScope.Normal,
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

    fun bindSession(session: Any?, selectedTabId: String?): Boolean {
        if (!canUse(selectedTabId) || session == null) return false
        if (boundSession == null) boundSession = session
        return boundSession === session
    }

    fun acceptsSession(session: Any?): Boolean =
        boundSession != null && boundSession === session

    fun mayReclaim(
        tab: VideoSearchNormalWorkerTab?,
        selectedTabId: String?,
        currentOwner: VideoSearchScope.Normal?,
        session: Any?,
        safeOrigin: Boolean,
    ): Boolean =
        canUse(selectedTabId) &&
        tab?.id == tabId &&
        owner.ownsTab(tab.contextId, tab.isPrivate) &&
        currentOwner == owner &&
        safeOrigin &&
        // Before any Gecko session was bound, only a still-null session is safe.
        (if (boundSession == null) session == null else boundSession === session)
}

/** Each concurrent normal worker has an independent short-lived lease. */
internal class VideoSearchNormalWorkerRegistry {
    private val leases = LinkedHashMap<String, VideoSearchNormalWorkerLease>()

    fun register(lease: VideoSearchNormalWorkerLease, selectedTabId: String?) {
        check(!leases.containsKey(lease.tabId)) { "Worker tab ID already leased" }
        lease.observeSelection(selectedTabId)
        leases[lease.tabId] = lease
    }

    fun forTab(id: String): VideoSearchNormalWorkerLease? = leases[id]

    fun observeSelection(selectedTabId: String?) {
        if (leases.isEmpty()) return
        leases.values.forEach { it.observeSelection(selectedTabId) }
    }

    fun release(lease: VideoSearchNormalWorkerLease) {
        if (leases[lease.tabId] === lease) leases.remove(lease.tabId)
    }
}

internal object VideoSearchNormalWorkerSelectionHost {
    val registry = VideoSearchNormalWorkerRegistry()
}
