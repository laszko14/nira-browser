package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

/** v1109: signed manifests keep exact URLs, bounded histories and session isolation. */
class MediaStreamRevisionTest {
    private val page = "https://site.example/watch"
    private fun tab(session: Any = Any(), privateMode: Boolean = false) =
        MediaTabState(MediaSessionBinding(session, "tab", "profile", privateMode), page, false)

    private fun add(
        repo: MediaRepository, tab: MediaTabState, url: String,
        provenance: String = "observed", frameKey: Long? = null,
    ): MediaRepository.InsertResult {
        val candidate = MediaDetectorMessage.Candidate(
            url = url, source = MediaSource.NETWORK, privateHint = tab.binding.isPrivate,
            pageUrl = tab.pageUrl, documentKey = 1, frameKey = frameKey,
            provenance = provenance
        )
        val media = MediaClassifier.fromNetwork(candidate, tab.binding)!!
        return repo.add(tab, tab.pageUrl, 1, media, MediaSource.NETWORK,
            metadata = candidate)
    }
    private fun repoFor(tab: MediaTabState): MediaRepository {
        val repo = MediaRepository()
        repo.observe(listOf(tab))
        assertTrue(repo.registerDocument(tab, page, 1).accepted)
        return repo
    }

    @Test fun newSignedUrlReplacesPreviousWithoutMutatingExactUrl() {
        val t = tab()
        val repo = repoFor(t)
        val old = "https://cdn.test/hls/index.m3u8?s=111&t=old&e=10800"
        val fresh = "https://cdn.test/hls/index.m3u8?s=222&t=new&e=10800"
        assertEquals(MediaRepository.InsertResult.ADDED, add(repo, t, old))
        val oldEntry = repo.currentEntries(t).single()
        assertEquals(MediaRepository.InsertResult.DUPLICATE, add(repo, t, fresh))
        val entry = repo.currentEntries(t).single()
        assertEquals(oldEntry.id, entry.id)
        assertEquals(fresh, entry.url)
        assertEquals(2, entry.revisionCount)
        assertEquals(1, repo.currentSize())
        assertFalse(entry.toString().contains("new"))
    }

    @Test fun resolverCannotReplaceObservedRequestEvenWhenProbeArrivesLater() {
        val t = tab()
        val repo = repoFor(t)
        val observed = "https://cdn.test/hls/master.m3u8?token=working"
        add(repo, t, observed)
        add(repo, t, "https://cdn.test/hls/master.m3u8?token=probe", "derived")
        val entry = repo.currentEntries(t).single()
        assertEquals(observed, entry.url)
        assertEquals(2, entry.revisionCount)
        assertEquals(setOf(MediaSource.NETWORK, MediaSource.PAGE), entry.sources)
    }

    @Test fun stableQueryAndFramesDoNotGetAggressivelyMerged() {
        val t = tab()
        val repo = repoFor(t)
        add(repo, t, "https://cdn.test/index.m3u8?quality=720&t=A")
        add(repo, t, "https://cdn.test/index.m3u8?quality=1080&t=B")
        add(repo, t, "https://cdn.test/index.m3u8?quality=720&t=C", frameKey = 22)
        assertEquals(3, repo.currentEntries(t).size)
    }

    @Test fun historyIsBoundedAndPrivateScopesAreSeparate() {
        val session = Any()
        val normal = tab(session)
        val repo = repoFor(normal)
        for (i in 0 until 25) add(repo, normal, "https://cdn.test/index.m3u8?token=$i")
        assertEquals(8, repo.currentEntries(normal).single().revisionCount)
        val privateTab = tab(session, true)
        repo.observe(listOf(privateTab))
        assertTrue(repo.currentEntries(normal).isEmpty())
        assertFalse(repo.registerDocument(privateTab, page, 1).accepted)
        assertTrue(repo.registerDocument(privateTab, page, 2).accepted)
    }
}
