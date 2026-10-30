package com.prirai.android.nira.browser.profile

/**
 * Live, standalone PWA sessions are not present in BrowserStore.
 * Refuse named-profile erasure while one is open; never clear cookies under
 * a still-running GeckoSession.
 */
internal object ProfilePwaLiveSessions {
    private val open = mutableMapOf<String, Int>()
    private val deleting = mutableSetOf<String>()

    @Synchronized fun reserve(profileId: String): Boolean {
        if (profileId in deleting) return false
        open[profileId] = (open[profileId] ?: 0) + 1
        return true
    }

    @Synchronized fun release(profileId: String) {
        val count = (open[profileId] ?: 0) - 1
        if (count <= 0) open.remove(profileId) else open[profileId] = count
    }

    @Synchronized fun beginDeletion(profileId: String): Boolean {
        if (profileId in deleting || (open[profileId] ?: 0) > 0) return false
        deleting.add(profileId)
        return true
    }

    @Synchronized fun cancelDeletion(profileId: String) { deleting.remove(profileId) }

    @Synchronized fun isDeleting(profileId: String): Boolean = profileId in deleting
}
