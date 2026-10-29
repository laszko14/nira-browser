package com.prirai.android.nira.videosearch

import java.util.UUID

/**
 * BrowserStore inventory projection only. Not a page URL, search term,
 * cookie, Gecko port, or profile preference.
 */
internal data class VideoSearchPrivateTab(
    val id: String,
    val contextId: String?,
    val isPrivate: Boolean,
)

/**
 * One RAM-only search lifetime for the process's set of private browser tabs.
 * Switching the visible mode, Activity rotation/backgrounding or selecting a
 * normal tab does NOT end it while any private tab is still present.
 *
 * Gecko's "private" context is shared and has no lifetime ID. Never infer
 * authorization from that string alone: only registered IDs whose Gecko
 * context is exactly "private" and whose original normal-profile owner is
 * currently active can receive an ephemeral scope. A profile switch denies
 * access rather than silently adopting private data into another profile.
 *
 * A finished session is retired by VideoSearchPrivateStore. New private tabs
 * after the last one closes always get a fresh, nonpersistent UUID. The
 * registry deliberately does not implement searching or persistent caches.
 */
internal class VideoSearchPrivateSessionRegistry(
    private val privateStore: VideoSearchPrivateStore = VideoSearchPrivateStore(),
    private val newSessionId: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Any()
    private data class Lifetime(
        val scope: VideoSearchScope.Private,
        var tabIds: Set<String>,
    )
    private var lifetime: Lifetime? = null

    fun reconcile(tabs: List<VideoSearchPrivateTab>, activeProfileId: String) =
        synchronized(lock) {
            // Only real Gecko-private tabs sustain this lifetime.
            val privateTabs = tabs.filter { it.isPrivate && it.contextId == "private" }
            val ids = privateTabs.mapTo(LinkedHashSet()) { it.id }
            if (ids.isEmpty()) {
                lifetime?.let { privateStore.finish(it.scope) }
                lifetime = null
                return@synchronized
            }
            // Never accept a malformed / duplicated BrowserStore inventory
            // as an independent source of ownership.
            if (ids.size != privateTabs.size ||
                ids.any { it.isBlank() }) return@synchronized
            if (lifetime == null) {
                // If profile identity is unknown, fail closed. A subsequent
                // inventory emission can retry without fabricating an owner.
                val scope = try {
                    // Reject invalid regular profile identity *before* minting
                    // a private-epoch ID. A bad inventory cannot consume IDs.
                    VideoSearchScope.Normal(activeProfileId)
                    VideoSearchScope.Private(activeProfileId, newSessionId())
                } catch (_: IllegalArgumentException) {
                    return@synchronized
                }
                privateStore.start(scope)
                lifetime = Lifetime(scope, ids)
            } else {
                lifetime?.tabIds = ids
            }
        }

    /** Returns null for a foreign/normal tab, stale ID or switched profile. */
    fun ownerForTab(
        tab: VideoSearchPrivateTab, activeProfileId: String,
    ): VideoSearchScope.Private? = synchronized(lock) {
        val session = lifetime ?: return@synchronized null
        if (!tab.isPrivate || tab.contextId != "private" ||
            tab.id !in session.tabIds ||
            session.scope.profileId != activeProfileId) null
        else session.scope
    }

    /**
     * Complete one RAM operation under the same lock as tab retirement.
     * Never hand a long-lived private-store handle to an Activity or worker.
     * Null means a tab/profile is no longer authorized.
     */
    fun <T> withAuthorizedTab(
        tab: VideoSearchPrivateTab,
        activeProfileId: String,
        block: (VideoSearchPrivateStore, VideoSearchScope.Private) -> T,
    ): T? = synchronized(lock) {
        val owner = ownerForTab(tab, activeProfileId) ?: return@synchronized null
        block(privateStore, owner)
    }

    fun activeScope(): VideoSearchScope.Private? = synchronized(lock) {
        lifetime?.scope
    }
}

/** Process-owned only; neither persisted nor retained through process death. */
internal object VideoSearchPrivateSessionHost {
    val registry = VideoSearchPrivateSessionRegistry()
}
