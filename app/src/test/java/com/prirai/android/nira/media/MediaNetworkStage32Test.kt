package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

class MediaNetworkStage32Test {
    private val tabA = MediaSessionBinding(Any(), "tab-A", "normal", false)
    private fun network(url: String, mime: String? = null) =
        MediaDetectorMessage.Candidate(url, mime, MediaSource.NETWORK)
    private fun format(url: String, mime: String? = null) =
        MediaClassifier.fromNetwork(network(url, mime), tabA)?.format

    @Test fun detectsDirectMp4WebmExtensionlessAndQuery() {
        assertEquals(MediaFormat.MP4, format("https://cdn.example/video.mp4?token=a"))
        assertEquals(MediaFormat.MP4, format("https://cdn.example/play?id=1", "video/mp4"))
        assertEquals(MediaFormat.WEBM, format("https://cdn.example/movie.webm", "video/webm"))
        assertEquals(MediaFormat.WEBM, format("https://cdn.example/movie.mp4", "video/webm"))
        assertEquals(MediaFormat.OGG, format("https://cdn.example/audio-video.ogv", "video/ogg"))
        assertEquals(MediaFormat.MOV, format("https://cdn.example/movie.mov", "video/quicktime"))
    }

    @Test fun findsManifestsButNotSegmentsEvenWithVideoMime() {
        assertEquals(MediaFormat.HLS, format("https://cdn.example/master.m3u8?sig=x"))
        assertEquals(MediaFormat.HLS, format("https://cdn.example/stream?id=1", "application/vnd.apple.mpegurl"))
        assertEquals(MediaFormat.HLS, format("https://cdn.example/stream?id=2", "application/x-mpegurl"))
        assertEquals(MediaFormat.DASH, format("https://cdn.example/manifest.mpd"))
        assertEquals(MediaFormat.DASH, format("https://cdn.example/v?id=3", "application/dash+xml"))
        for (url in listOf("segment-001.ts", "file.m4s", "init.mp4",
            "initialization-12.mp4", "chunk_22.webm", "frag_17.mp4")) {
            assertTrue(url, MediaClassifier.isSegment("https://cdn.example/$url?signature=xyz"))
            assertNull(url, format("https://cdn.example/$url?signature=xyz", "video/mp4"))
        }
    }

    @Test fun conflictingAndUnsafeMimeDoNotTurnPagesIntoMedia() {
        assertNull(format("https://cdn.example/404.mp4", "text/html"))
        assertNull(format("https://cdn.example/unrecognizable", "application/json"))
        assertNull(format("https://cdn.example/notvideo.jpg", "image/jpeg"))
        assertNull(format("blob:https://cdn.example/abc.mp4", "video/mp4"))
        assertNull(format("data:video/mp4;base64,AAA", "video/mp4"))
        assertNull(format("https://user:pw@cdn.example/file.mp4", "video/mp4"))
    }

    @Test fun networkAndDomShareBoundedSessionScopedDedup() {
        val session = Any()
        val first = MediaSessionBinding(session, "tab-A", "profile-A", false)
        val sameUrl = "https://cdn.example/movie.mp4?signature=xyz"
        val media = MediaClassifier.fromDom(MediaDetectorMessage.Candidate(sameUrl), first)!!
        val dedup = MediaRecentDeduplicator(ttlMillis = 20000, maxPerSession = 2)
        assertTrue(dedup.accept(first, media, 1000))
        assertFalse(dedup.accept(first, MediaClassifier.fromNetwork(network(sameUrl, "video/mp4"), first)!!, 1500))
        assertTrue(dedup.accept(MediaSessionBinding(Any(), "tab-B", "profile-A", false), media, 1500))
        assertTrue(dedup.accept(MediaSessionBinding(Any(), "tab-A", "profile-B", true), media, 1500))
        assertTrue(dedup.accept(first, media, 22000))
        val other = MediaClassifier.fromNetwork(network("https://cdn.example/new.webm", "video/webm"), first)!!
        assertTrue(dedup.accept(first, other, 22001))
        assertFalse(dedup.accept(first, other, 22002))
        // Native session reused in another partition must not inherit previously observed URLs.
        assertTrue(dedup.accept(MediaSessionBinding(session, "tab-A", "profile-B", true), other, 22003))
        dedup.retainSessions(emptyList())
    }

    @Test fun validatesNetworkBridgeAndAnonymousDiagnostics() {
        val valid = """{"schemaVersion":1,"event":"media_candidate","source":"network","isPrivate":false,"url":"https://cdn.example/a.mpd?secret=1","contentType":"application/dash+xml"}"""
        val message = MediaMessageProtocol.decode(valid) as MediaDetectorMessage.Candidate
        assertEquals(MediaSource.NETWORK, message.source)
        assertEquals(false, message.privateHint)
        assertNull(MediaMessageProtocol.decode(valid.replace("\\\"isPrivate\\\":false", "\\\"isPrivate\\\":true-string")))
        assertEquals(MediaFormat.DASH, MediaClassifier.fromNetwork(message, tabA)?.format)
        assertFalse(message.toString().contains("secret"))
        assertNull(MediaMessageProtocol.decode(valid.dropLast(1) + ""","tabId":"spoofed"}"""))
        val anonymous = MediaMessageProtocol.decode(
            """{"schemaVersion":1,"event":"network_unassigned","isPrivate":true}""")
        assertTrue(anonymous is MediaDetectorMessage.NetworkUnassigned)
        assertTrue((anonymous as MediaDetectorMessage.NetworkUnassigned).isPrivate)
        assertNull(MediaMessageProtocol.decode(
            """{"schemaVersion":1,"event":"network_unassigned","isPrivate":"true"}"""))
        assertNull(MediaMessageProtocol.decode(
            """{"schemaVersion":1,"event":"network_segments","isPrivate":false,"count":-5}"""))
        assertEquals(24, (MediaMessageProtocol.decode(
            """{"schemaVersion":1,"event":"network_segments","isPrivate":false,"count":24}""")
            as MediaDetectorMessage.NetworkSegments).count)
    }

    @Test fun diagnosticsMaintainIsolationAndNeverPrintTokens() {
        MediaDetectorDiagnostics.clearForTest()
        try {
            val privateTab = MediaSessionBinding(Any(), "tab-secret", "profile-secret", true)
            MediaDetectorDiagnostics.detected(tabA, MediaFormat.HLS, MediaSource.NETWORK)
            MediaDetectorDiagnostics.detected(tabA, MediaFormat.MP4, MediaSource.DOM)
            MediaDetectorDiagnostics.duplicate(tabA)
            MediaDetectorDiagnostics.detected(privateTab, MediaFormat.DASH, MediaSource.NETWORK)
            MediaDetectorDiagnostics.networkUnassigned(true)
            MediaDetectorDiagnostics.networkSegments(true, 42)
            MediaDetectorDiagnostics.validationError(privateTab)
            val normal = MediaDetectorDiagnostics.snapshot("tab-A", false, "normal")
            assertEquals(2, normal.allTabMediaCount)
            assertEquals(1, normal.domMediaCount)
            assertEquals(1, normal.networkMediaCount)
            assertEquals(1, normal.hlsCount)
            assertEquals(1, normal.duplicateCount)
            assertEquals(0, normal.dashCount)
            assertEquals(0, normal.segmentCount)
            val incognito = MediaDetectorDiagnostics.snapshot("tab-secret", true, "profile-secret")
            assertEquals(1, incognito.dashCount)
            assertEquals(1, incognito.unassignedCount)
            assertEquals(42, incognito.segmentCount)
            assertEquals(1, incognito.validationErrorCount)
            assertFalse(incognito.asPolishReport().contains("secret"))
        } finally {
            MediaDetectorDiagnostics.clearForTest()
        }
    }
}
