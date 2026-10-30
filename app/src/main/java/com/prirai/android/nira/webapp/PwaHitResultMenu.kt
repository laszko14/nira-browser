package com.prirai.android.nira.webapp

import mozilla.components.concept.engine.HitResult
import java.net.URI

/**
 * Only URLs reported by this WebAppFragment's own EngineSession.Observer.
 * Never read BrowserStore.selectedTab hit results across profile boundaries.
 */
internal object PwaHitResultMenu {
    fun safeTarget(hit: HitResult): String? {
        val candidate = when (hit) {
            is HitResult.IMAGE_SRC -> hit.uri.takeIf(::isWebUrl) ?: hit.src
            else -> hit.src
        }
        return candidate.takeIf(::isWebUrl)
    }

    private fun isWebUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}
