package com.prirai.android.nira.videosearch

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ImageRequest
import java.security.MessageDigest

/**
 * Stage 9B: one Coil LRU disk cache for ALL ordinary video-search profiles.
 * The combined disk budget is 500 million bytes, not 500 MB per profile.
 * It is stored under cacheDir, not the persistent database/files directory.
 *
 * Keep the ImageLoader private to this feature: other app imagery must not
 * consume the film-poster budget or accidentally share a profile cache key.
 */
internal object VideoSearchThumbnailCache {
    const val MAX_DISK_BYTES = 500_000_000L
    private const val DIRECTORY_NAME = "zavro-video-search-thumbnails-v1"
    private const val KEY_PREFIX = "zavro-video-search-poster-v1-"

    @Volatile private var imageLoader: ImageLoader? = null

    /**
     * URL query bytes matter: different video posters can share a path.
     * Profile + complete URL use SHA-256, with no URL/title/token in the key.
     * Never use this persistent key for VideoSearchScope.Private.
     */
    fun key(owner: VideoSearchScope.Normal, exactImageUrl: String): String {
        VideoSearchIntegrity.requirePageUrl(exactImageUrl)
        val bytes = (owner.profileId + "\u0000" + exactImageUrl)
            .toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = "0123456789abcdef"
        return buildString(KEY_PREFIX.length + digest.size * 2) {
            append(KEY_PREFIX)
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }

    fun request(
        context: Context, owner: VideoSearchScope.Normal, exactImageUrl: String,
    ): ImageRequest {
        val cacheKey = key(owner, exactImageUrl)
        return ImageRequest.Builder(context.applicationContext)
            .data(exactImageUrl)
            .diskCacheKey(cacheKey)
            .memoryCacheKey(cacheKey)
            .build()
    }

    fun loader(context: Context): ImageLoader {
        imageLoader?.let { return it }
        return synchronized(this) {
            imageLoader ?: ImageLoader.Builder(context.applicationContext)
                .memoryCache {
                    MemoryCache.Builder(context.applicationContext)
                        .maxSizePercent(0.10)
                        .build()
                }
                .diskCache {
                    // Coil's built-in journal trims least-recently-used images
                    // after writes. A missing entry will simply be fetched
                    // again; no history/Room row is deleted.
                    DiskCache.Builder()
                        .directory(context.applicationContext.cacheDir.resolve(DIRECTORY_NAME))
                        .maxSizeBytes(MAX_DISK_BYTES)
                        .build()
                }
                .build().also { imageLoader = it }
        }
    }
}

/**
 * UI displays only lazy visible posters; no prefetch, manual file enumeration,
 * cache polling or per-frame hash. Only the currently active NORMAL owner may
 * start a disk-cached thumbnail request. Future private results need a
 * separate RAM-only loader and never use this composable.
 */
@Composable
internal fun VideoSearchThumbnail(
    exactImageUrl: String?,
    owner: VideoSearchScope.Normal?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val safeOwner = owner?.takeIf { isActiveNormalProfile(context, it.profileId) }
    val request = remember(context.applicationContext, safeOwner?.profileId, exactImageUrl) {
        if (safeOwner == null || exactImageUrl == null) null else
            runCatching {
                VideoSearchThumbnailCache.request(
                    context.applicationContext, safeOwner, exactImageUrl
                )
            }.getOrNull()
    }
    if (request != null) {
        val loader = remember(context.applicationContext) {
            VideoSearchThumbnailCache.loader(context.applicationContext)
        }
        AsyncImage(
            model = request,
            imageLoader = loader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}
