package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

class MediaEarlyDiscoveryTest {
    private val binding = MediaSessionBinding(Any(), "tab-a", "profile-a", false)

    @Test fun declaredMp4WebmHlsAndDashWorkWithoutPlayback() {
        for ((url, type, expected) in listOf(
            Triple("https://cdn.example/a.mp4?token=secret", null, MediaFormat.MP4),
            Triple("https://cdn.example/b.webm", null, MediaFormat.WEBM),
            Triple("https://cdn.example/manifest.m3u8", null, MediaFormat.HLS),
            Triple("https://cdn.example/live.mpd", null, MediaFormat.DASH),
            Triple("https://cdn.example/media?id=7", "application/vnd.apple.mpegurl", MediaFormat.HLS),
            Triple("https://cdn.example/video?id=7", "video/mp4", MediaFormat.MP4),
        )) {
            val source = MediaClassifier.fromPage(MediaDetectorMessage.Candidate(
                url, type, MediaSource.PAGE), binding)
            assertEquals(expected, source?.format)
        }
    }

    @Test fun rejectsEmbedBlobHtmlSegmentsAndConflictingMime() {
        for (url in listOf(
            "https://embed.example/player", "blob:https://cdn.example/a.mp4",
            "https://cdn.example/segment-101.ts", "https://cdn.example/init.mp4",
            "https://site.example/poster.jpg",
        )) assertNull(url, MediaClassifier.fromPage(MediaDetectorMessage.Candidate(
            url, source = MediaSource.PAGE), binding))
        assertNull(MediaClassifier.fromPage(MediaDetectorMessage.Candidate(
            "https://cdn.example/a.mp4", "text/html", MediaSource.PAGE), binding))
    }

    @Test fun validatesFrameKeysAndRejectsSpoofedFields() {
        val raw = """{"schemaVersion":1,"event":"media_candidate","source":"page",
            "url":"https://cdn.example/a.m3u8","pageUrl":"https://site.example/watch",
            "documentKey":7,"frameUrl":"https://embed.example/player","frameKey":11,
            "groupId":"f11-g1"}""".replace("\n", "").replace(" ", "")
        val parsed = MediaMessageProtocol.decode(raw) as MediaDetectorMessage.Candidate
        assertEquals(MediaSource.PAGE, parsed.source)
        assertEquals(11L, parsed.frameKey)
        assertEquals("f11-g1", parsed.groupId)
        assertEquals("https://site.example/watch", parsed.pageUrl)
        assertNull(MediaMessageProtocol.decode(raw.replace("\"frameKey\":11,", "")))
        assertNull(MediaMessageProtocol.decode(raw.replace("\"frameKey\":11", "\"frameKey\":0")))
        assertNull(MediaMessageProtocol.decode(raw.replace("\"groupId\":\"f11-g1\"",
            "\"groupId\":\"<script>\"")))
    }

    @Test fun duplicateAcrossPageAndNetworkIsOneConfirmedGroup() {
        val repo = MediaRepository()
        val t = MediaTabState(binding, "https://site.example/watch", false)
        repo.observe(listOf(t))
        assertTrue(repo.registerDocument(t, t.pageUrl, 9).accepted)
        val url = "https://cdn.example/a.mp4?signature=secret"
        val media = MediaClassifier.fromPage(MediaDetectorMessage.Candidate(
            url, source = MediaSource.PAGE), binding)!!
        assertEquals(MediaRepository.InsertResult.ADDED,
            repo.add(t, t.pageUrl, 9, media, MediaSource.PAGE, "t9-g1"))
        assertEquals(MediaRepository.InsertResult.DUPLICATE,
            repo.add(t, t.pageUrl, 9, media, MediaSource.NETWORK))
        val result = repo.currentEntries(t).single()
        assertEquals(setOf(MediaSource.PAGE, MediaSource.NETWORK), result.sources)
        assertEquals("t9-g1", result.groupId)
        assertFalse(result.toString().contains("secret"))
    }

    @Test fun discoveredVariantsGroupAndPrivatePartitionStaysIsolated() {
        val repo = MediaRepository()
        val regular = MediaTabState(binding, "https://site.example/watch", false)
        val privateBinding = MediaSessionBinding(Any(), "private", "profile-a", true)
        val secret = MediaTabState(privateBinding, regular.pageUrl, false)
        repo.observe(listOf(regular, secret))
        repo.registerDocument(regular, regular.pageUrl, 12)
        repo.registerDocument(secret, secret.pageUrl, 13)
        for (url in listOf("https://cdn.example/a.mp4", "https://cdn.example/b.webm")) {
            val media = MediaClassifier.fromPage(MediaDetectorMessage.Candidate(
                url, source = MediaSource.PAGE), binding)!!
            repo.add(regular, regular.pageUrl, 12, media, MediaSource.PAGE, "t12-g1")
        }
        assertEquals(1, repo.currentEntries(regular).map { it.groupId }.distinct().size)
        assertEquals(2, repo.currentEntries(regular).size)
        assertTrue(repo.currentEntries(secret).isEmpty())
        assertTrue(repo.counts("profile-a", true).single().count == 0)
    }
}
