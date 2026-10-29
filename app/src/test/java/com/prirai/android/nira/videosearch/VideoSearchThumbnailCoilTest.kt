package com.prirai.android.nira.videosearch

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Stage 9B: verify Coil's real request keys and presence of disk LRU. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchThumbnailCoilTest {
    private val owner = VideoSearchScope.Normal("default")
    private val url = "https://example.org/poster?film=1&signed=yes"

    @Test fun requestPreservesExactUrlAndExplicitProfileKeys() {
        val context = RuntimeEnvironment.getApplication<Application>()
        val request = VideoSearchThumbnailCache.request(context, owner, url)
        assertEquals(url, request.data)
        assertEquals(VideoSearchThumbnailCache.key(owner, url), request.diskCacheKey)
        assertEquals(VideoSearchThumbnailCache.key(owner, url),
            request.memoryCacheKey?.key)
    }

    @Test fun oneSharedImageLoaderUsesOneDiskCache() {
        val context = RuntimeEnvironment.getApplication<Application>()
        val first = VideoSearchThumbnailCache.loader(context)
        val second = VideoSearchThumbnailCache.loader(context)
        assertSame(first, second)
        assertNotNull(first.diskCache)
        assertNotNull(first.memoryCache)
    }
}
