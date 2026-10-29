package com.prirai.android.nira.videosearch

/**
 * Immutable launch snapshot for ONE private Gecko lifetime. The query/URLs
 * are transient and the redacted toString must never reveal them in logs.
 * The UI may select up to 200 sites; execution later rechecks each site,
 * tab, epoch, and the current memory budget before performing any work.
 */
internal class VideoSearchPrivateMultiPlan private constructor(
    val owner: VideoSearchScope.Private,
    val query: String,
    val sites: List<VideoSearchSite>,
) {
    override fun toString(): String = "VideoSearchPrivateMultiPlan(redacted)"

    companion object {
        fun create(
            owner: VideoSearchScope.Private,
            query: String,
            settings: VideoSearchSettings,
            availableSites: List<VideoSearchSite>,
        ): VideoSearchPrivateMultiPlan {
            require(VideoSearchBridgeProtocol.validQuery(query))
            require(settings.scope == owner)
            require(availableSites.size <= VideoSearchPrivateSitesAccess.MAX_TEMPORARY_SITES)
            require(availableSites.all { it.scope == owner })
            require(availableSites.map { it.id }.distinct().size == availableSites.size)
            val selected = availableSites.filter {
                it.enabled && it.id in settings.selectedSiteIds
            }.sortedWith(compareBy<VideoSearchSite>({ it.position }, { it.createdAtMs }, { it.id }))
            require(selected.isNotEmpty()) { "No selected private sites" }
            return VideoSearchPrivateMultiPlan(owner, query, selected.toList())
        }
    }
}
