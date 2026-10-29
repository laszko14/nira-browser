package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** Stage 9B pure key/budget regression: no network or browser session. */
class VideoSearchThumbnailCacheTest {
    private val default = VideoSearchScope.Normal("default")
    private val work = VideoSearchScope.Normal("work")

    @Test fun budgetIs500DecimalMegabytesForOneSharedCache() {
        assertEquals(500_000_000L, VideoSearchThumbnailCache.MAX_DISK_BYTES)
    }

    @Test fun sameProfileAndFullUrlReusesOneCacheEntry() {
        val url = "https://example.org/poster?id=1&size=120"
        assertEquals(VideoSearchThumbnailCache.key(default, url),
            VideoSearchThumbnailCache.key(default, url))
    }

    @Test fun differentProfilesNeverShareKeyEvenForSameUrl() {
        val url = "https://example.org/poster?id=1"
        assertNotEquals(VideoSearchThumbnailCache.key(default, url),
            VideoSearchThumbnailCache.key(work, url))
    }

    @Test fun queryStringVariantsAreNeverMerged() {
        val a = "https://example.org/poster?id=1&token=a"
        val b = "https://example.org/poster?id=1&token=b"
        assertNotEquals(VideoSearchThumbnailCache.key(default, a),
            VideoSearchThumbnailCache.key(default, b))
    }

    @Test fun keyDoesNotRevealUrlProfileOrSignedParameters() {
        val url = "https://example.org/movie?id=1&secret=veryprivate"
        val key = VideoSearchThumbnailCache.key(work, url)
        assertFalse(key.contains("example.org"))
        assertFalse(key.contains("secret"))
        assertFalse(key.contains("work"))
        assertEquals(93, key.length)
    }

    @Test fun noCredentialsInThumbnailUrl() {
        try {
            VideoSearchThumbnailCache.key(default, "https://user:pass@example.org/p.jpg")
            fail("Credentialed image URL accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun unsafeSchemesCannotBeCached() {
        for (url in listOf("file:///private/img.jpg", "javascript:alert(1)",
            "data:image/png;base64,AAAA", "content://private/thumb")) {
            try {
                VideoSearchThumbnailCache.key(default, url)
                fail("Unsafe image URL accepted")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun preservingUrlCaseAndParameterOrderAvoidsWrongImage() {
        val a = "https://example.org/img?ID=1&color=blue"
        val b = "https://example.org/img?color=blue&ID=1"
        assertNotEquals(VideoSearchThumbnailCache.key(default, a),
            VideoSearchThumbnailCache.key(default, b))
    }

    @Test fun urlFragmentDifferencesHaveIndependentKeys() {
        val a = "https://example.org/img#one"
        val b = "https://example.org/img#two"
        assertNotEquals(VideoSearchThumbnailCache.key(default, a),
            VideoSearchThumbnailCache.key(default, b))
    }

    @Test fun repeatCallsNeverUseUnboundedPlaintextIdentifiers() {
        val urls = (1..30).map { "https://example.org/img?frame=$it" }
        val keys = urls.map { VideoSearchThumbnailCache.key(default, it) }
        assertEquals(30, keys.distinct().size)
        assertEquals(1, keys.map { it.length }.distinct().size)
        assertTrue(keys.all { it.length <= 100 })
    }
}
