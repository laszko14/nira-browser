package com.prirai.android.nira.videosearch

import java.net.URI
import java.util.UUID

/**
 * Stage 10B: minimal private-site management backed EXCLUSIVELY by RAM.
 * Every read and mutation is authorized against a live Gecko private tab
 * and the currently active normal-profile identity. No DAO or Context.
 *
 * The 10B UI deliberately does not submit searches: private Gecko bridge,
 * worker tab ownership, and transient results require stage 10C.
 */
internal data class VideoSearchPrivateSitesSnapshot(
    val sites: List<VideoSearchSite>,
    val selectedIds: Set<String>,
)

internal class VideoSearchPrivateSitesAccess(
    private val registry: VideoSearchPrivateSessionRegistry =
        VideoSearchPrivateSessionHost.registry,
) {
    companion object {
        const val MAX_TEMPORARY_SITES = 200
    }

    fun snapshot(tab: VideoSearchPrivateTab, activeProfileId: String):
        VideoSearchPrivateSitesSnapshot? =
        registry.withAuthorizedTab(tab, activeProfileId) { store, scope ->
            VideoSearchPrivateSitesSnapshot(
                sites = store.sites(scope),
                selectedIds = store.settings(scope).selectedSiteIds,
            )
        }

    /** Returns false on duplicate or at the memory budget, null on revoked owner. */
    fun addSite(tab: VideoSearchPrivateTab, activeProfileId: String, typedUrl: String):
        Boolean? = registry.withAuthorizedTab(tab, activeProfileId) { store, scope ->
        val exactUrl = VideoSearchIntegrity.siteInputUrl(typedUrl)
        val existing = store.sites(scope)
        if (existing.size >= MAX_TEMPORARY_SITES ||
            existing.any { it.siteUrl == exactUrl }) return@withAuthorizedTab false
        val site = VideoSearchSite(
            id = UUID.randomUUID().toString(), scope = scope,
            siteUrl = exactUrl, displayName = URI(exactUrl).host,
            position = existing.size, createdAtMs = System.currentTimeMillis(),
        )
        store.putSite(site)
        store.selectSite(scope, site.id, true)
        true
    }

    /** Edit only the RAM-scoped site; never touch normal Room preferences. */
    fun setResultLimit(
        tab: VideoSearchPrivateTab, activeProfileId: String,
        siteId: String, resultLimit: Int,
    ): Boolean? = registry.withAuthorizedTab(tab, activeProfileId) { store, scope ->
        if (resultLimit !in 1..VideoSearchResultBudgets.MAX_SITE_RESULTS)
            return@withAuthorizedTab false
        val site = store.sites(scope).singleOrNull { it.id == siteId }
            ?: return@withAuthorizedTab false
        store.putSite(site.copy(resultLimit = resultLimit,
            updatedAtMs = maxOf(System.currentTimeMillis(), site.updatedAtMs + 1)))
        true
    }

    fun setSelected(
        tab: VideoSearchPrivateTab, activeProfileId: String,
        siteId: String, selected: Boolean,
    ): Boolean? = registry.withAuthorizedTab(tab, activeProfileId) { store, scope ->
        store.selectSite(scope, siteId, selected)
    }

    fun selectAll(
        tab: VideoSearchPrivateTab, activeProfileId: String, selected: Boolean,
    ): Boolean? = registry.withAuthorizedTab(tab, activeProfileId) { store, scope ->
        store.selectAll(scope, selected)
        true
    }

    fun removeSite(
        tab: VideoSearchPrivateTab, activeProfileId: String, siteId: String,
    ): Boolean? = registry.withAuthorizedTab(tab, activeProfileId) { store, scope ->
        store.removeSite(scope, siteId)
    }
}
