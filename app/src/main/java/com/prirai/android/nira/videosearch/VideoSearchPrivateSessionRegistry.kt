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

    // IDs are accepted only for the normal profile that was active at their
    // first observation. Gecko's shared private context carries no profile ID.
    // Without this map, a tab opened under B while A still has a live private
    // epoch could silently join A when the user switches back to A.
    private val tabProfileOwners = LinkedHashMap<String, String>()

    private fun startLifetime(profileId: String, ids: Set<String>) {
        if (ids.isEmpty()) return
        val scope = VideoSearchScope.Private(profileId, newSessionId())
        privateStore.start(scope)
        lifetime = Lifetime(scope, ids)
    }

    fun reconcile(tabs: List<VideoSearchPrivateTab>, activeProfileId: String) =
        synchronized(lock) {
            // Require both the private flag and the real Gecko private context.
            val privateTabs = tabs.filter { it.isPrivate && it.contextId == "private" }
            val ids = privateTabs.mapTo(LinkedHashSet()) { it.id }
            if (ids.isEmpty()) {
                lifetime?.let { privateStore.finish(it.scope) }
                lifetime = null
                tabProfileOwners.clear()
                return@synchronized
            }
            // Malformed inventories must not keep an old owner alive.
            if (ids.size != privateTabs.size || ids.any { it.isBlank() }) {
                lifetime?.let { privateStore.finish(it.scope) }
                lifetime = null
                tabProfileOwners.clear()
                return@synchronized
            }
            val validProfile = try {
                VideoSearchScope.Normal(activeProfileId)
                true
            } catch (_: IllegalArgumentException) {
                false
            }
            // A closed ID never retains its old owner if the ID is reused later.
            tabProfileOwners.keys.retainAll(ids)
            if (validProfile) {
                ids.forEach { id -> tabProfileOwners.putIfAbsent(id, activeProfileId) }
            }

            lifetime?.let { current ->
                val surviving = ids.filterTo(LinkedHashSet()) { id ->
                    tabProfileOwners[id] == current.scope.profileId
                }
                if (surviving.isEmpty()) {
                    privateStore.finish(current.scope)
                    lifetime = null
                } else {
                    current.tabIds = surviving
                }
            }
            if (lifetime == null && validProfile) {
                val newlyOwned = ids.filterTo(LinkedHashSet()) { id ->
                    tabProfileOwners[id] == activeProfileId
                }
                startLifetime(activeProfileId, newlyOwned)
            }
        }

    /** Returns null for a foreign/normal tab, stale ID or switched profile. */
    fun ownerForTab(
        tab: VideoSearchPrivateTab, activeProfileId: String,
    ): VideoSearchScope.Private? = synchronized(lock) {
        if (!tab.isPrivate || tab.contextId != "private" ||
            tabProfileOwners[tab.id] != activeProfileId) return@synchronized null

        // A private B tab may remain alive after A's last private tab closes,
        // while A is still the foreground profile. When B returns, allow a
        // fresh, empty B lifetime without requiring an unrelated tab action.
        if (lifetime == null) {
            val ids = tabProfileOwners.filterValues { it == activeProfileId }.keys.toSet()
            startLifetime(activeProfileId, ids)
        }
        val current = lifetime ?: return@synchronized null
        if (current.scope.profileId != activeProfileId ||
            tab.id !in current.tabIds) null else current.scope
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
