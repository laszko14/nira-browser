package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class, manifest = org.robolectric.annotation.Config.NONE)
class VideoDiscoveryV1163Test {
    private val binding = MediaSessionBinding(Any(), "tab", "profile-A", false)
    private val tab = MediaTabState(binding, "https://page.test/watch", false)

    @Test fun playingOggAndMovKeepTheMetadataPathAvailableBeforePlay() {
        for ((url, mime, format) in listOf(
            Triple("https://cdn.test/movie.ogv", "video/ogg", MediaFormat.OGG),
            Triple("https://cdn.test/movie.mov", "video/quicktime", MediaFormat.MOV))) {
            val candidate = MediaDetectorMessage.Candidate(url, contentType = mime, source = MediaSource.DOM)
            assertEquals(format, MediaClassifier.fromDom(candidate, binding)?.format)
        }
    }

    @Test fun extensionlessObservedHlsCanResolveAnUnambiguousBlobInItsOwnFrame() {
        val repo = MediaRepository()
        repo.observe(listOf(tab))
        assertTrue(repo.registerDocument(tab, tab.pageUrl, 1).accepted)
        val frame = "https://frame.test/embed"
        val candidate = MediaDetectorMessage.Candidate("https://cdn.test/play?id=film&sig=exact",
            contentType = "application/vnd.apple.mpegurl", source = MediaSource.NETWORK,
            privateHint = false, pageUrl = tab.pageUrl, documentKey = 1,
            frameUrl = frame, frameKey = 5, verification = "manifest_observed")
        val media = MediaClassifier.fromNetwork(candidate, binding)!!
        repo.add(tab, tab.pageUrl, 1, media, MediaSource.NETWORK, metadata = candidate)
        assertEquals(candidate.url, BlobHlsSelector.select(repo.currentEntries(tab), frame)?.url)
        assertNull(BlobHlsSelector.select(repo.currentEntries(tab), "https://other.test/embed"))
        assertTrue(repo.currentEntries(tab.copy(binding = MediaSessionBinding(binding.session, "tab", "profile-B", false))).isEmpty())
    }
    @Test fun discoveryDiagnosticsUseOnlyStrictBoundedCategories() {
        val valid = """{"schemaVersion":1,"event":"discovery_stats","code":"scan_yield","count":3,"documentKey":1}"""
        assertNotNull(MediaMessageProtocol.decode(valid))
        assertNull(MediaMessageProtocol.decode(valid.replace("scan_yield", "https://private.test/token")))
        assertNull(MediaMessageProtocol.decode(valid.replace("\"count\":3", "\"count\":-1")))
        assertNull(MediaMessageProtocol.decode(valid.replace("\"count\":3", "\"count\":100001")))
        assertNull(MediaMessageProtocol.decode(valid.replace("\"documentKey\":1", "\"documentKey\":1,\"url\":\"https://private.test/\"")))
        assertNotNull(MediaMessageProtocol.decode("""{"schemaVersion":1,"event":"discovery_stats","code":"manifest_invalid","count":1,"isPrivate":true}"""))
    }

    @Test fun diagnosticSnapshotRejectsStaleDocumentAndIsolatesProfiles() {
        val repo = MediaRepository()
        repo.observe(listOf(tab))
        repo.registerDocument(tab, tab.pageUrl, 1)
        assertTrue(repo.isCurrentDocument(tab, 1))
        assertFalse(repo.isCurrentDocument(tab, 2))
        repo.observe(listOf(tab.copy(pageUrl = "https://page.test/other")))
        assertFalse(repo.isCurrentDocument(tab, 1))
        MediaDetectorDiagnostics.clearForTest()
        try {
            MediaDetectorDiagnostics.discovery(binding, "scan_yield", 2)
            MediaDetectorDiagnostics.discovery(binding, "private-url-must-not-pass", 1)
            MediaDetectorDiagnostics.discoveryAnonymous(true, "manifest_invalid", 3)
            val own = MediaDetectorDiagnostics.snapshot("tab", false, "profile-A")
            assertEquals(2, own.discovery["scan_yield"])
            assertFalse(own.asPolishReport().contains("private-url"))
            assertFalse(own.asPolishReport().contains("profile-A"))
            assertEquals(0, MediaDetectorDiagnostics.snapshot(null, false, "profile-B").discovery["scan_yield"])
            assertEquals(3, MediaDetectorDiagnostics.snapshot(null, true).anonymousDiscovery["manifest_invalid"])
            assertNull(own.anonymousDiscovery["manifest_invalid"])
            assertTrue(own.asPolishReport().contains("VIDEO DISCOVERY"))
            assertTrue(MediaDetectorDiagnostics.discoveryReportForSession(binding.session).contains("scan_yield: profil=2"))
            assertFalse(MediaDetectorDiagnostics.discoveryReportForSession(Any()).contains("profil=2"))
        } finally { MediaDetectorDiagnostics.clearForTest() }
    }

}
