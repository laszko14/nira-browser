package com.prirai.android.nira.videosearch

import android.os.Bundle

/**
 * Minimal, normal-profile-only UI draft. Does not contain private search
 * phrases, browsing data, run IDs, or a command to execute after recreation.
 */
internal enum class VideoSearchNormalDialogKind { QUEUE, SEARCH, EDIT }

internal data class VideoSearchNormalDialogDraft(
    val ownerId: String,
    val kind: VideoSearchNormalDialogKind,
    val siteId: String? = null,
    val primary: String = "",
    val secondary: String = "",
    val tertiary: String = "",
) {
    fun toBundle() = Bundle().apply {
        putString("owner", ownerId)
        putString("kind", kind.name)
        putString("site", siteId)
        putString("primary", primary)
        putString("secondary", secondary)
        putString("tertiary", tertiary)
    }

    companion object {
        fun belongsTo(value: VideoSearchNormalDialogDraft, ownerId: String): Boolean =
            value.ownerId == ownerId && ownerId.isNotBlank() &&
                value.primary.length <= 2048 &&
                value.secondary.length <= 2048 && value.tertiary.length <= 2048 &&
                (value.kind == VideoSearchNormalDialogKind.QUEUE ||
                    !value.siteId.isNullOrBlank())

        fun fromBundle(bundle: Bundle?, ownerId: String): VideoSearchNormalDialogDraft? {
            if (bundle == null || bundle.getString("owner") != ownerId) return null
            val kind = VideoSearchNormalDialogKind.values().firstOrNull {
                it.name == bundle.getString("kind")
            } ?: return null
            return VideoSearchNormalDialogDraft(
                ownerId, kind, bundle.getString("site"),
                bundle.getString("primary").orEmpty(),
                bundle.getString("secondary").orEmpty(),
                bundle.getString("tertiary").orEmpty(),
            ).takeIf { belongsTo(it, ownerId) }
        }
    }
}
