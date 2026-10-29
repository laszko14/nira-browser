package com.prirai.android.nira.videosearch

/**
 * Pure SPA rebind policy. Same Gecko port is reusable only for the same
 * profile/origin, with an exact BrowserStore URL and monotonic revision.
 * A genuine new port starts at revision -1; it cannot be reused on a
 * different owner or a cross-origin navigation.
 */
internal object VideoSearchSpaBinding {
    fun maySync(
        previousUrl: String?, previousOwner: VideoSearchScope?,
        currentUrl: String?, currentOwner: VideoSearchScope?,
    ): Boolean =
        previousUrl != null && previousOwner != null &&
            currentUrl != null && currentOwner == previousOwner &&
            VideoSearchBridgeProtocol.sameOrigin(previousUrl, currentUrl)

    fun acceptHello(
        previousUrl: String?, previousOwner: VideoSearchScope?,
        previousRevision: Int, storeUrl: String, storeOwner: VideoSearchScope,
        message: VideoSearchBridgeMessage.Hello,
    ): Boolean {
        if (message.pageUrl != storeUrl) return false
        if (previousUrl == null && previousOwner == null && previousRevision == -1)
            return message.navigationRevision >= 0
        if (!maySync(previousUrl, previousOwner, storeUrl, storeOwner))
            return false
        return message.navigationRevision > previousRevision
    }
}
