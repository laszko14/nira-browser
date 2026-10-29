package com.prirai.android.nira.videosearch

import com.prirai.android.nira.browser.profile.BrowserProfile

/**
 * Owner of a film-search run and all its records. Never infer this from the
 * currently selected tab/profile after a run has started.
 *
 * Gecko's private contextId is always "private" and does NOT distinguish
 * separate private browsing lifetimes. A new, host-generated sessionId must
 * therefore be supplied for each private lifetime; its end clears all RAM data.
 */
internal sealed interface VideoSearchScope {
    val profileId: String
    val contextId: String?
    val isPrivate: Boolean

    /** Null is an explicit "never persist" marker, not the default profile. */
    val persistentProfileId: String?

    fun ownsTab(tabContextId: String?, tabIsPrivate: Boolean): Boolean =
        isPrivate == tabIsPrivate && contextId == tabContextId

    data class Normal(override val profileId: String) : VideoSearchScope {
        init {
            validateProfileId(profileId)
        }

        override val contextId: String? get() = BrowserProfile.contextIdFor(profileId)
        override val isPrivate: Boolean = false
        override val persistentProfileId: String get() = profileId
    }

    data class Private(
        override val profileId: String,
        /** Unique ID of the *current private lifetime*, not Gecko's contextId. */
        val sessionId: String,
    ) : VideoSearchScope {
        init {
            validateProfileId(profileId)
            require(sessionId.isNotBlank()) { "Private session ID is required" }
        }

        override val contextId: String = "private"
        override val isPrivate: Boolean = true
        override val persistentProfileId: String? = null

        override fun toString(): String = "VideoSearchScope.Private(redacted)"
    }
}

private fun validateProfileId(profileId: String) {
    require(profileId.isNotBlank() && profileId == profileId.trim() && profileId != "private") {
        "A canonical regular profile ID is required"
    }
}
