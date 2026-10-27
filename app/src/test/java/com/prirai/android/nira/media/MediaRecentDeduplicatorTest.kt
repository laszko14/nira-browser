package com.prirai.android.nira.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRecentDeduplicatorTest {
    private fun binding(session: Any = Any(), context: String? = "profile-A") =
        MediaSessionBinding(session, "tab-A", context, false)

    private fun media() = DetectedMedia(
        "https://fixture.invalid/movie.mp4?sig=private", MediaFormat.MP4, "tab-A", "profile-A", false
    )

    @Test
    fun duplicatesRemainFilteredUntilExactTtlBoundary() {
        val dedup = MediaRecentDeduplicator(ttlMillis = 1_000L)
        val session = binding()
        val item = media()
        assertTrue(dedup.accept(session, item, 2_000L))
        assertFalse(dedup.accept(session, item, 2_999L))
        assertTrue(dedup.accept(session, item, 3_000L))
    }

    @Test
    fun elapsedClockRegressionDoesNotTrapPreviousEntry() {
        val dedup = MediaRecentDeduplicator(ttlMillis = 1_000L)
        val session = binding()
        val item = media()
        assertTrue(dedup.accept(session, item, 5_000L))
        assertTrue(dedup.accept(session, item, 1_000L))
        assertFalse(dedup.accept(session, item, 1_001L))
    }

    @Test
    fun separateNativeSessionsRemainIndependent() {
        val dedup = MediaRecentDeduplicator()
        val first = binding()
        val second = binding()
        val item = media()
        assertTrue(dedup.accept(first, item, 50L))
        assertTrue(dedup.accept(second, item, 50L))
        assertFalse(dedup.accept(first, item, 51L))
    }

    @Test
    fun contextChangeResetsSessionScopeWithoutPersistingUrls() {
        val dedup = MediaRecentDeduplicator()
        val nativeSession = Any()
        val item = media()
        assertTrue(dedup.accept(binding(nativeSession, "profile-A"), item, 50L))
        assertFalse(dedup.accept(binding(nativeSession, "profile-A"), item, 51L))
        assertTrue(dedup.accept(binding(nativeSession, "profile-B"), item, 52L))
    }
}
