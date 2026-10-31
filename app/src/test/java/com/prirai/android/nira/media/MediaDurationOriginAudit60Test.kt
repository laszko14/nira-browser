package com.prirai.android.nira.media

import com.prirai.android.nira.diagnostics.testlab.VideoMetadataLedger
import org.junit.Assert.*
import org.junit.Test

class MediaDurationOriginAudit60Test {
    private val page = "https://site.example/watch"
    private fun fixture(): Pair<MediaRepository, MediaTabState> {
        val repo = MediaRepository()
        val tab = MediaTabState(MediaSessionBinding(Any(), "tab", "profile", false), page, false)
        repo.observe(listOf(tab))
        assertTrue(repo.registerDocument(tab, page, 1).accepted)
        return repo to tab
    }

    private fun offer(
        repo: MediaRepository, tab: MediaTabState, url: String,
        source: MediaSource = MediaSource.NETWORK, verification: String? = null,
        duration: Int? = null, live: Boolean? = null
    ): MediaEntry {
        val candidate = MediaDetectorMessage.Candidate(
            url = url, source = source, pageUrl = page, documentKey = 1,
            privateHint = false, verification = verification,
            durationSeconds = duration, isLive = live
        )
        val media = when (source) {
            MediaSource.NETWORK -> MediaClassifier.fromNetwork(candidate, tab.binding)
            MediaSource.DOM -> MediaClassifier.fromDom(candidate, tab.binding)
            MediaSource.PAGE -> MediaClassifier.fromPage(candidate, tab.binding)
        }!!
        val result = repo.add(tab, page, 1, media, source, metadata = candidate)
        assertTrue(result == MediaRepository.InsertResult.ADDED ||
            result == MediaRepository.InsertResult.DUPLICATE)
        return repo.forTab(tab.binding).single { it.url == url }
    }

    @Test fun hlsManifestThenStreamVerificationRetainsExtinfOriginInLedger() {
        val (repo, tab) = fixture()
        val url = "https://cdn.example/vod.m3u8?sig=private"
        val ledger = VideoMetadataLedger()
        val manifest = offer(repo, tab, url, verification = "manifest_verified", duration = 11, live = false)
        assertEquals("HLS_EXTINF", manifest.durationOrigin)
        assertEquals(11, manifest.durationSeconds)
        ledger.observe(tab.binding, manifest)
        val stream = offer(repo, tab, url, verification = "stream_verified", duration = 11, live = false)
        assertEquals(MediaPlaybackStatus.CONFIRMED, stream.playbackStatus)
        assertEquals(11, stream.durationSeconds)
        assertEquals("HLS_EXTINF", stream.durationOrigin)
        ledger.observe(tab.binding, stream)
        assertEquals("HLS_EXTINF", ledger.snapshot().examples.single().durationSource)
        assertEquals(0, ledger.snapshot().updates)
        assertFalse(ledger.snapshot().toString().contains("private"))
    }

    @Test fun realDomDurationCanReplaceManifestButLateProbeCannotOverwriteItsEvidence() {
        val (repo, tab) = fixture()
        val url = "https://cdn.example/vod.m3u8"
        offer(repo, tab, url, verification = "manifest_verified", duration = 11, live = false)
        val fromPlayer = offer(repo, tab, url, source = MediaSource.DOM, duration = 14)
        assertEquals(14, fromPlayer.durationSeconds)
        assertEquals("HTML5", fromPlayer.durationOrigin)
        val lateStream = offer(repo, tab, url, verification = "stream_verified", duration = 11, live = false)
        assertEquals(14, lateStream.durationSeconds)
        assertEquals("HTML5", lateStream.durationOrigin)
        val ledger = VideoMetadataLedger()
        ledger.observe(tab.binding, lateStream)
        assertEquals("HTML5", ledger.snapshot().examples.single().durationSource)
    }

    @Test fun liveToVodAndProtectedManifestNeverInventHtml5Evidence() {
        val (repo, tab) = fixture()
        val url = "https://cdn.example/live-to-vod.m3u8"
        val live = offer(repo, tab, url, verification = "manifest_verified", live = true)
        assertTrue(live.isLive)
        assertNull(live.durationSeconds)
        val vod = offer(repo, tab, url, verification = "manifest_verified", duration = 21, live = false)
        assertFalse(vod.isLive)
        assertEquals(21, vod.durationSeconds)
        assertEquals("HLS_EXTINF", vod.durationOrigin)
        val blocked = offer(repo, tab, url, verification = "unplayable", duration = 21, live = false)
        assertEquals(MediaPlaybackStatus.UNPLAYABLE, blocked.playbackStatus)
        assertEquals("HLS_EXTINF", blocked.durationOrigin)
        val ledger = VideoMetadataLedger()
        ledger.observe(tab.binding, blocked)
        assertEquals("HLS_EXTINF", ledger.snapshot().examples.single().durationSource)
    }

    @Test fun unverifiedNetworkDurationIsNotMisreportedAsHtml5OrDash() {
        val (repo, tab) = fixture()
        val url = "https://cdn.example/unverified.m3u8"
        val unverified = offer(repo, tab, url, verification = "manifest_observed", duration = 7)
        assertNull(unverified.durationSeconds)
        assertNull(unverified.durationOrigin)
        val streamOnly = offer(repo, tab, url, verification = "stream_verified", duration = 7)
        assertNull(streamOnly.durationSeconds)
        assertNull(streamOnly.durationOrigin)
        val ledger = VideoMetadataLedger()
        ledger.observe(tab.binding, streamOnly)
        assertEquals("NOT_EXPOSED", ledger.snapshot().examples.single().durationSource)

        val dashUrl = "https://cdn.example/movie.mpd"
        val dash = offer(repo, tab, dashUrl, verification = "manifest_verified", duration = 31, live = false)
        assertEquals("DASH_MPD", dash.durationOrigin)
        val dashStream = offer(repo, tab, dashUrl, verification = "stream_verified", duration = 31)
        assertEquals("DASH_MPD", dashStream.durationOrigin)
    }
}
