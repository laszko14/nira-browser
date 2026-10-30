package com.prirai.android.nira.browser.profile

import mozilla.components.browser.state.state.TabSessionState

/** Canonical normal profile is Gecko's null partition, not a named profile. */
internal object ProfileTabScope {
    fun selectedNormalProfile(contextId: String?, isPrivate: Boolean, profiles: List<BrowserProfile>): BrowserProfile? {
        if (isPrivate) return null // Keep the last normal profile when browsing privately.
        return when {
            contextId == null || contextId == "profile_default" ->
                profiles.firstOrNull { it.id == "default" } ?: BrowserProfile.getDefaultProfile()
            contextId.startsWith("profile_") ->
                profiles.firstOrNull { it.id == contextId.removePrefix("profile_") }
            else -> null // Never silently reassign an unknown/foreign Gecko partition.
        }
    }

    fun belongsToNormalProfile(tab: TabSessionState, profileId: String): Boolean {
        if (tab.content.private) return false
        return if (profileId == "default") tab.contextId == null || tab.contextId == "profile_default"
        else tab.contextId == "profile_$profileId"
    }

    /** Every NewTab surface uses current privacy; normal default is explicit until middleware canonicalizes it. */
    fun newTabDestination(profile: BrowserProfile, isPrivate: Boolean): NewTabDestination =
        NewTabDestination(isPrivate,
            if (isPrivate) "private" else BrowserProfile.contextIdFor(profile, false) ?: "profile_default")
}

internal data class NewTabDestination(val isPrivate: Boolean, val contextId: String)
