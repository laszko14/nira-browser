package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

class MediaClassifierTest {
    private val regular = MediaSessionBinding(Any(), "tab-A", "profile-1", false)

    @Test fun classifiesDirectMp4AndWebmIncludingQueryAndFragments() {
        val mp4 = MediaClassifier.fromDom(
            MediaDetectorMessage.Candidate("https://cdn.example.org/v/movie.MP4?token=secret#part"), regular)
        val webm = MediaClassifier.fromDom(
            MediaDetectorMessage.Candidate("http://cdn.example.org/v/movie.webm?expires=123"), regular)
        assertEquals(MediaFormat.MP4, mp4?.format)
        assertEquals(MediaFormat.WEBM, webm?.format)
        assertEquals("https://cdn.example.org/v/movie.MP4?token=secret#part", mp4?.url)
        assertFalse(mp4.toString().contains("secret"))
    }

    @Test fun mimeHintHandlesExtensionlessAndTakesPriorityOverExtension() {
        val mime = MediaClassifier.fromDom(
            MediaDetectorMessage.Candidate("https://cdn.example.org/stream?sig=x", "video/webm; codecs=vp9"), regular)
        assertEquals(MediaFormat.WEBM, mime?.format)
        assertEquals(MediaFormat.WEBM, MediaClassifier.fromDom(
            MediaDetectorMessage.Candidate("https://cdn.example.org/file.mp4", "video/webm"), regular)?.format)
        assertNull(MediaClassifier.fromDom(
            MediaDetectorMessage.Candidate("https://cdn.example.org/file.mp4", "video/ogg"), regular))
    }

    @Test fun skipsSegmentsAndNonMedia() {
        for (url in listOf("https://cdn.example.org/seg-001.mp4",
            "https://cdn.example.org/init.mp4", "https://cdn.example.org/23.m4s",
            "https://cdn.example.org/01.ts", "https://cdn.example.org/image.jpg")) {
            assertNull(url, MediaClassifier.fromDom(MediaDetectorMessage.Candidate(url), regular))
        }
        assertNull(MediaClassifier.fromDom(
            MediaDetectorMessage.Candidate("https://cdn.example.org/a.m4s", "video/mp4"), regular))
        assertNull(MediaClassifier.fromDom(
            MediaDetectorMessage.Candidate("https://cdn.example.org/master.m3u8", "video/mp4"), regular))
        assertNull(MediaClassifier.fromDom(
            MediaDetectorMessage.Candidate("https://cdn.example.org/manifest.mpd", "video/webm"), regular))
    }

    @Test fun htmlVideoCanDeclareManifestBeforeAnyNetworkResponse() {
        val hls = MediaClassifier.fromDom(
            MediaDetectorMessage.Candidate("https://cdn.example.org/master.m3u8?sig=abc"), regular)
        val dash = MediaClassifier.fromDom(
            MediaDetectorMessage.Candidate("https://cdn.example.org/manifest.mpd"), regular)
        assertEquals(MediaFormat.HLS, hls?.format)
        assertEquals(MediaFormat.DASH, dash?.format)
        assertEquals(MediaFormat.HLS, MediaClassifier.fromDom(
            MediaDetectorMessage.Candidate("https://cdn.example.org/playlist?id=5",
                "application/vnd.apple.mpegurl"), regular)?.format)
    }

    @Test fun nativeBindingIsPreservedWithoutTrustingPageProvidedIdentifiers() {
        val binding = MediaSessionBinding(Any(), "tab-B", "partition-B", true)
        val media = MediaClassifier.fromDom(
            MediaDetectorMessage.Candidate("https://cdn.example.org/video.mp4?token=hidden"), binding)!!
        assertEquals("tab-B", media.tabId)
        assertEquals("partition-B", media.contextId)
        assertTrue(media.isPrivate)
        assertFalse(media.toString().contains("hidden"))
        assertFalse(media.toString().contains("cdn.example"))
    }

    @Test fun unsupportedSchemesCannotBecomeDirectMedia() {
        for (url in listOf("blob:https://example.org/a.mp4", "file:///a.mp4",
            "intent://example.org/a.mp4", "javascript:alert(1)")) {
            assertNull(MediaClassifier.fromDom(MediaDetectorMessage.Candidate(url, "video/mp4"), regular))
        }
    }
}
