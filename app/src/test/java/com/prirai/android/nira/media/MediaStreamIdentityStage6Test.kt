package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

/** Synthetic fixtures; exact signed URLs never enter diagnostic logs. */
class MediaStreamIdentityStage6Test {
    private val page = "https://site.example/watch"
    private val tab = MediaTabState(
        MediaSessionBinding(Any(), "tab", "profile", false), page, false)
    private fun repo() = MediaRepository().also {
        it.observe(listOf(tab))
        assertTrue(it.registerDocument(tab, page, 1L).accepted)
    }
    private fun aws(signature: String, date: String = "20261027T100000Z",
                    asset: String = "first", credential: String = "account%2Fregion") =
        "https://cdn.example/hls/index.m3u8?asset=$asset" +
        "&X-Amz-Algorithm=AWS4-HMAC-SHA256" +
        "&X-Amz-Credential=$credential&X-Amz-SignedHeaders=host" +
        "&X-Amz-Date=$date&X-Amz-Expires=600&X-Amz-Signature=$signature"

    private fun add(repository: MediaRepository, url: String,
        sequence: Long? = null, group: String? = null,
        provenance: String = "observed", verification: String? = null,
    ): MediaRepository.InsertResult {
        val candidate = MediaDetectorMessage.Candidate(
            url = url, contentType = "application/vnd.apple.mpegurl",
            source = MediaSource.NETWORK, privateHint = false, pageUrl = page,
            documentKey = 1L, groupId = group, requestSequence = sequence,
            provenance = provenance, verification = verification)
        val media = MediaClassifier.fromNetwork(candidate, tab.binding)!!
        return repository.add(tab, page, 1L, media, MediaSource.NETWORK, group, candidate)
    }

    @Test fun arbitraryManifestQueryIdentifiersRemainDifferentStreams() {
        val repository = repo()
        val urls = listOf("s=1", "s=2", "e=1", "e=2", "t=a", "t=b",
            "token=a", "token=b", "sig=x", "signature=z", "expires=10",
            "hdnts=one", "hdnts=two").map { "https://cdn.example/index.m3u8?$it" }
        for (url in urls) assertEquals(MediaRepository.InsertResult.ADDED,
            add(repository, url))
        assertEquals(urls.size, repository.currentEntries(tab).size)
        assertEquals(urls.size, repository.currentEntries(tab).map { it.id }.distinct().size)
    }

    @Test fun fullAwsSigV4NetworkLineageRefreshesSameRecordWithoutRewritingUrl() {
        val repository = repo()
        val old = aws("AAA")
        val fresh = aws("BBB", date = "20261027T110000Z")
        assertEquals(MediaRepository.InsertResult.ADDED,
            add(repository, old, 10, "manifest-1-1"))
        val id = repository.currentEntries(tab).single().id
        assertEquals(MediaRepository.InsertResult.DUPLICATE,
            add(repository, fresh, 11, "manifest-1-1", verification = "stream_verified"))
        val record = repository.currentEntries(tab).single()
        assertEquals(id, record.id)
        assertEquals(fresh, record.url)
        assertEquals(2, record.revisionCount)
        assertEquals(MediaPlaybackStatus.CONFIRMED, record.playbackStatus)
        assertFalse(record.toString().contains("BBB"))
    }

    @Test fun staleOldSignedResponseCannotReplaceFreshNetworkObservation() {
        val repository = repo()
        val old = aws("OLD")
        val fresh = aws("NEW", date = "20261027T120000Z")
        add(repository, old, 10, "manifest-1-1")
        add(repository, fresh, 20, "manifest-1-1", verification = "stream_verified")
        assertEquals(MediaRepository.InsertResult.DUPLICATE,
            add(repository, old, 11, "manifest-1-1", verification = "auth_error"))
        val record = repository.currentEntries(tab).single()
        assertEquals(fresh, record.url)
        assertEquals(MediaPlaybackStatus.CONFIRMED, record.playbackStatus)
        assertEquals(2, record.revisionCount)
    }

    @Test fun queryIdentityCredentialAndGroupDifferencesNeverAlias() {
        val repository = repo()
        add(repository, aws("AAA", asset = "film-a"), 1, "manifest-1-1")
        add(repository, aws("BBB", asset = "film-b"), 2, "manifest-1-1")
        add(repository, aws("CCC", asset = "film-a", credential = "other%2Fregion"),
            3, "manifest-1-1")
        add(repository, aws("DDD", asset = "film-a"), 4, "manifest-1-2")
        assertEquals(4, repository.currentEntries(tab).size)
    }

    @Test fun noRequestLineageAndShortOrAmbiguousTokensNeverAlias() {
        val repository = repo()
        add(repository, aws("AAA"), group = "manifest-1-1")
        add(repository, aws("BBB"), group = "manifest-1-1")
        val old = "https://cdn.example/index.m3u8?s=1&token=film-a"
        val newer = "https://cdn.example/index.m3u8?s=2&token=film-b"
        add(repository, old, 10, "manifest-1-1")
        add(repository, newer, 11, "manifest-1-1")
        assertEquals(4, repository.currentEntries(tab).size)
    }

    @Test fun derivedProbeRemainsSeparateAndCannotReplaceObservedSource() {
        val repository = repo()
        val observed = aws("WORKING")
        val probe = aws("PROBE")
        add(repository, observed, 1, "manifest-1-1")
        add(repository, probe, 2, "manifest-1-1", provenance = "derived")
        assertTrue(repository.currentEntries(tab).any { it.url == observed &&
            MediaSource.NETWORK in it.sources })
        assertEquals(2, repository.currentEntries(tab).size)
    }

    @Test fun refreshKeyIsFrameScopedAndIncompleteSigningFailsClosed() {
        val url = aws("ONE")
        assertNotEquals(MediaStreamIdentity.key(url, MediaFormat.HLS, 1),
            MediaStreamIdentity.key(url, MediaFormat.HLS, 2))
        assertNotEquals(MediaStreamIdentity.signedRefreshKey(url, MediaFormat.HLS, 1),
            MediaStreamIdentity.signedRefreshKey(url, MediaFormat.HLS, 2))
        assertNull(MediaStreamIdentity.signedRefreshKey(
            "https://cdn.example/index.m3u8?X-Amz-Signature=partial", MediaFormat.HLS))
        assertNull(MediaStreamIdentity.signedRefreshKey(
            url + "&x-amz-signature=duplicate", MediaFormat.HLS))
        assertFalse(MediaStreamIdentity.sameSignedRefresh(url, url, MediaFormat.HLS))
        assertFalse(MediaStreamIdentity.sameSignedRefresh(
            aws("OLD"), aws("NEW"), MediaFormat.MP4))
    }

    @Test fun validRenewalHistoryStaysBoundedAfterTwentyFiveObservations() {
        val repository = repo()
        for (i in 1..25) add(repository, aws("SIG$i",
            date = "20261027T" + i.toString().padStart(2, '0') + "0000Z"),
            sequence = i.toLong(), group = "manifest-1-1")
        val record = repository.currentEntries(tab).single()
        assertEquals(8, record.revisionCount)
        assertTrue(record.url.contains("SIG25"))
        assertFalse(record.toString().contains("SIG25"))
    }
}
