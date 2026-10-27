package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

/** Signed manifests keep exact URLs. Ambiguous query fields must not merge films. */
class MediaStreamRevisionTest {
    private val page = "https://site.example/watch"
    private fun tab(session: Any = Any(), privateMode: Boolean = false) =
        MediaTabState(MediaSessionBinding(session, "tab", "profile", privateMode), page, false)

    private fun repoFor(tab: MediaTabState): MediaRepository {
        val repo = MediaRepository()
        repo.observe(listOf(tab))
        assertTrue(repo.registerDocument(tab, page, 1L).accepted)
        return repo
    }

    private fun add(
        repo: MediaRepository, tab: MediaTabState, url: String,
        provenance: String = "observed", frameKey: Long? = null,
        frameUrl: String? = null, verification: String? = null, httpStatus: Int? = null,
        requestSequence: Long? = null, groupId: String? = null,
    ): MediaRepository.InsertResult {
        val candidate = MediaDetectorMessage.Candidate(
            url = url, source = MediaSource.NETWORK, privateHint = tab.binding.isPrivate,
            pageUrl = tab.pageUrl, documentKey = 1L, frameUrl = frameUrl,
            frameKey = frameKey, provenance = provenance,
            verification = verification, httpStatus = httpStatus,
            requestSequence = requestSequence, groupId = groupId)
        val media = MediaClassifier.fromNetwork(candidate, tab.binding)!!
        return repo.add(tab, tab.pageUrl, 1L, media, MediaSource.NETWORK,
            groupId = groupId, metadata = candidate)
    }

    private fun aws(sig: String) =
        "https://cdn.test/index.m3u8?X-Amz-Algorithm=AWS4-HMAC-SHA256" +
        "&X-Amz-Credential=account%2Fregion&X-Amz-SignedHeaders=host" +
        "&X-Amz-Date=20261027T100000Z&X-Amz-Expires=600&X-Amz-Signature=$sig"

    @Test fun iframeOriginAndVerificationRemainSeparateWithoutSignedLineage() {
        val t = tab()
        val repo = repoFor(t)
        val iframe = "https://player.example/embed/77"
        add(repo, t, "https://cdn.test/index.m3u8?t=old&s=1", frameKey = 22,
            frameUrl = iframe, verification = "manifest_observed", httpStatus = 200)
        add(repo, t, "https://cdn.test/index.m3u8?t=fresh&s=2", frameKey = 22,
            frameUrl = iframe, verification = "stream_verified", httpStatus = 206)
        val entries = repo.currentEntries(t)
        assertEquals(2, entries.size)
        val fresh = entries.single { it.url.contains("t=fresh") }
        assertEquals(iframe, fresh.frameUrl)
        assertEquals(MediaPlaybackStatus.CONFIRMED, fresh.playbackStatus)
        assertEquals(206, fresh.httpStatus)
    }

    @Test fun authorizationFailureCannotRemainNetworkConfirmed() {
        val t = tab()
        val repo = repoFor(t)
        add(repo, t, "https://cdn.test/index.m3u8?t=expired&s=1",
            verification = "auth_error", httpStatus = 403)
        val entry = repo.currentEntries(t).single()
        assertEquals(MediaPlaybackStatus.AUTH_ERROR, entry.playbackStatus)
        assertEquals(403, entry.httpStatus)
    }

    @Test fun lateOldResponseCannotReplaceNewerVerifiedAwsSigningRequest() {
        val t = tab()
        val repo = repoFor(t)
        add(repo, t, aws("fresh"), verification = "manifest_observed",
            httpStatus = 200, requestSequence = 12, groupId = "manifest-1-1")
        add(repo, t, aws("old"), verification = "auth_error",
            httpStatus = 403, requestSequence = 11, groupId = "manifest-1-1")
        val entry = repo.currentEntries(t).single()
        assertEquals(aws("fresh"), entry.url)
        assertEquals(MediaPlaybackStatus.NEEDS_VERIFICATION, entry.playbackStatus)
        assertEquals(200, entry.httpStatus)
    }

    @Test fun ambiguousShortQueryParametersNeverBecomeSignedRevisionAliases() {
        val t = tab()
        val repo = repoFor(t)
        val old = "https://cdn.test/hls/index.m3u8?s=111&t=old&e=10800"
        val fresh = "https://cdn.test/hls/index.m3u8?s=222&t=new&e=10800"
        assertEquals(MediaRepository.InsertResult.ADDED, add(repo, t, old))
        assertEquals(MediaRepository.InsertResult.ADDED, add(repo, t, fresh))
        val entries = repo.currentEntries(t)
        assertEquals(2, entries.size)
        assertEquals(setOf(old, fresh), entries.map { it.url }.toSet())
        assertTrue(entries.all { it.revisionCount == 1 })
        assertTrue(entries.none { it.toString().contains("new") })
    }

    @Test fun resolverCannotReplaceObservedRequestWhenProbeHasDifferentToken() {
        val t = tab()
        val repo = repoFor(t)
        val observed = "https://cdn.test/hls/master.m3u8?token=working"
        add(repo, t, observed)
        add(repo, t, "https://cdn.test/hls/master.m3u8?token=probe", "derived")
        val entries = repo.currentEntries(t)
        assertEquals(2, entries.size)
        assertTrue(entries.any { it.url == observed && MediaSource.NETWORK in it.sources })
        assertTrue(entries.any { it.url.contains("token=probe") && MediaSource.PAGE in it.sources })
    }

    @Test fun stableQueryAndFramesDoNotGetAggressivelyMerged() {
        val t = tab()
        val repo = repoFor(t)
        add(repo, t, "https://cdn.test/index.m3u8?quality=720&t=A")
        add(repo, t, "https://cdn.test/index.m3u8?quality=1080&t=B")
        add(repo, t, "https://cdn.test/index.m3u8?quality=720&t=C", frameKey = 22)
        assertEquals(3, repo.currentEntries(t).size)
    }

    @Test fun opaqueTokenValuesAreDistinctAndPrivateScopesAreSeparate() {
        val session = Any()
        val normal = tab(session)
        val repo = repoFor(normal)
        for (i in 0 until 25) add(repo, normal, "https://cdn.test/index.m3u8?token=$i")
        assertEquals(25, repo.currentEntries(normal).size)
        assertTrue(repo.currentEntries(normal).all { it.revisionCount == 1 })
        val privateTab = tab(session, true)
        repo.observe(listOf(privateTab))
        assertTrue(repo.currentEntries(normal).isEmpty())
        assertFalse(repo.registerDocument(privateTab, page, 1L).accepted)
        assertTrue(repo.registerDocument(privateTab, page, 2L).accepted)
    }
}
