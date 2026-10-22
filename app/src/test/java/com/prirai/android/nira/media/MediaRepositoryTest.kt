package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

class MediaRepositoryTest {
    private val pageA = "https://site.example/watch/1"
    private val pageB = "https://site.example/watch/2"
    private fun tab(
        session: Any = Any(), id: String = "tab", profile: String? = null,
        privateMode: Boolean = false, page: String = pageA, loading: Boolean = false
    ) = MediaTabState(MediaSessionBinding(session, id, profile, privateMode), page, loading)
    private fun media(tab: MediaTabState, url: String, format: MediaFormat = MediaFormat.MP4) =
        DetectedMedia(url, format, tab.binding.tabId, tab.binding.contextId, tab.binding.isPrivate)
    private fun begin(repo: MediaRepository, tab: MediaTabState, key: Long = 1L) {
        repo.observe(listOf(tab))
        assertTrue(repo.registerDocument(tab, tab.pageUrl, key).accepted)
    }
    private fun add(repo: MediaRepository, tab: MediaTabState, key: Long, url: String,
                    source: MediaSource = MediaSource.DOM, format: MediaFormat = MediaFormat.MP4) =
        repo.add(tab, tab.pageUrl, key, media(tab, url, format), source)

    @Test fun twoDifferentVideosInOneTabStayIndependent() {
        val repo = MediaRepository()
        val t = tab()
        begin(repo, t)
        assertEquals(MediaRepository.InsertResult.ADDED, add(repo, t, 1, "https://cdn.example/a.mp4"))
        assertEquals(MediaRepository.InsertResult.ADDED, add(repo, t, 1, "https://cdn.example/b.mp4"))
        assertEquals(2, repo.forTab(t.binding).size)
    }

    @Test fun domAndNetworkShareOneEntryButKeepBothSourceFlags() {
        val repo = MediaRepository()
        val t = tab()
        begin(repo, t)
        val url = "https://cdn.example/movie.mp4?signature=private-token&expires=500"
        add(repo, t, 1, url)
        assertEquals(MediaRepository.InsertResult.DUPLICATE,
            add(repo, t, 1, url, MediaSource.NETWORK))
        val entry = repo.forTab(t.binding).single()
        assertEquals(url, entry.url)
        assertEquals(setOf(MediaSource.DOM, MediaSource.NETWORK), entry.sources)
        assertFalse(entry.toString().contains("private-token"))
        assertEquals(1, repo.currentSize())
        assertEquals(1, repo.counts(null, false).single().dom)
        assertEquals(1, repo.counts(null, false).single().network)
    }

    @Test fun signedUrlsAreNeverNormalizedIntoIncorrectMatches() {
        val repo = MediaRepository()
        val t = tab()
        begin(repo, t)
        add(repo, t, 1, "https://cdn.example/a.mp4?token=X")
        add(repo, t, 1, "https://cdn.example/a.mp4?token=x")
        add(repo, t, 1, "https://cdn.example/a.mp4?token=X&quality=2")
        assertEquals(3, repo.currentSize())
    }

    @Test fun identicalUrlInTwoTabsIsNotDeduplicated() {
        val repo = MediaRepository()
        val a = tab(id = "A")
        val b = tab(id = "B")
        repo.observe(listOf(a, b))
        assertTrue(repo.registerDocument(a, pageA, 1).accepted)
        assertTrue(repo.registerDocument(b, pageA, 2).accepted)
        val url = "https://cdn.example/same.mp4"
        add(repo, a, 1, url)
        add(repo, b, 2, url)
        assertEquals(2, repo.currentSize())
        assertEquals(1, repo.forTab(a.binding).size)
        assertEquals(1, repo.forTab(b.binding).size)
    }

    @Test fun separateProfilesAndIncognitoStayIsolated() {
        val repo = MediaRepository()
        val regular = tab(id = "regular", profile = "profile-1")
        val second = tab(id = "second", profile = "profile-2")
        val privateTab = tab(id = "secret", profile = "profile-1", privateMode = true)
        repo.observe(listOf(regular, second, privateTab))
        listOf(regular to 1L, second to 2L, privateTab to 3L).forEach { (t, id) ->
            assertTrue(repo.registerDocument(t, t.pageUrl, id).accepted)
            add(repo, t, id, "https://cdn.example/same.mp4")
        }
        assertEquals(1, repo.counts("profile-1", false).single().count)
        assertEquals(1, repo.counts("profile-2", false).single().count)
        assertEquals(1, repo.counts("profile-1", true).single().count)
        assertTrue(repo.counts(null, false).isEmpty())
        assertTrue(repo.forTab(tab(id = "regular", profile = "profile-1").binding).isEmpty())
    }

    @Test fun closingTabDiscardsPrivateRecordsAndReleasesItsSession() {
        val repo = MediaRepository()
        val session = Any()
        val t = tab(session = session, privateMode = true)
        begin(repo, t)
        add(repo, t, 1, "https://cdn.example/token.mp4?secret=1")
        assertEquals(1, repo.activeScopes())
        repo.observe(emptyList())
        assertEquals(0, repo.currentSize())
        assertEquals(0, repo.activeScopes())
        assertEquals(MediaRepository.InsertResult.STALE,
            add(repo, t, 1, "https://cdn.example/late.mp4"))
    }

    @Test fun spaPushStateAndReplaceStateNavigationClearsOnlyItsTab() {
        val repo = MediaRepository()
        val sessionA = Any()
        val a = tab(session = sessionA, id = "A")
        val b = tab(id = "B")
        repo.observe(listOf(a, b))
        repo.registerDocument(a, pageA, 1)
        repo.registerDocument(b, pageA, 2)
        add(repo, a, 1, "https://cdn.example/old.mp4")
        add(repo, b, 2, "https://cdn.example/other.mp4")
        val moved = a.copy(pageUrl = pageB)
        val changed = repo.observe(listOf(moved, b))
        assertEquals(1, changed.count { it.navigation })
        assertEquals(1, changed.single().removed)
        assertTrue(repo.forTab(a.binding).isEmpty())
        assertEquals(1, repo.forTab(b.binding).size)
        assertEquals(MediaRepository.InsertResult.STALE,
            add(repo, moved, 1, "https://cdn.example/stale.mp4"))
        assertFalse(repo.registerDocument(moved, pageA, 3).accepted)
        assertTrue(repo.registerDocument(moved, pageB, 3).accepted)
        assertEquals(MediaRepository.InsertResult.ADDED,
            add(repo, moved, 3, "https://cdn.example/new.mp4"))
    }

    @Test fun sameUrlReloadAndBackForwardProduceFreshGenerations() {
        val repo = MediaRepository()
        val t = tab()
        begin(repo, t)
        add(repo, t, 1, "https://cdn.example/old.mp4")
        val reloading = t.copy(loading = true)
        assertEquals(1, repo.observe(listOf(reloading)).single().removed)
        assertEquals(0, repo.currentSize())
        assertFalse(repo.registerDocument(reloading, pageA, 1).accepted)
        assertTrue(repo.registerDocument(reloading, pageA, 2).accepted)
        add(repo, reloading, 2, "https://cdn.example/fresh.mp4")
        val completed = t.copy(loading = false)
        assertTrue(repo.observe(listOf(completed)).isEmpty())
        assertEquals(1, repo.currentSize())
        val back = completed.copy(pageUrl = pageB)
        assertEquals(1, repo.observe(listOf(back)).single().removed)
        assertEquals(0, repo.currentSize())
    }

    @Test fun delayedResponseCannotResurrectPreviousDocumentEvenAtSameUrl() {
        val repo = MediaRepository()
        val t = tab()
        begin(repo, t, key = 10)
        add(repo, t, 10, "https://cdn.example/old.mp4")
        assertTrue(repo.registerDocument(t, pageA, 11).accepted)
        assertEquals(MediaRepository.InsertResult.STALE,
            add(repo, t, 10, "https://cdn.example/old.mp4", MediaSource.NETWORK))
        assertFalse(repo.registerDocument(t, pageA, 10).accepted)
        assertEquals(0, repo.currentSize())
    }

    @Test fun fakeSessionBindingAndCrossProfileAccessAreRejected() {
        val repo = MediaRepository()
        val t = tab(id = "id", profile = "p1")
        begin(repo, t)
        add(repo, t, 1, "https://cdn.example/file.mp4")
        val wrongSession = tab(id = "id", profile = "p1")
        val wrongProfile = t.copy(binding = t.binding.copy(contextId = "p2"))
        assertEquals(MediaRepository.InsertResult.STALE,
            add(repo, wrongSession, 1, "https://cdn.example/evil.mp4"))
        assertEquals(MediaRepository.InsertResult.STALE,
            add(repo, wrongProfile, 1, "https://cdn.example/evil.mp4"))
        assertTrue(repo.forTab(wrongProfile.binding).isEmpty())
    }

    @Test fun boundedMediaEntriesAndFloodOfSegmentsCannotGrowMemory() {
        val repo = MediaRepository(perTabLimit = 8, globalLimit = 12)
        val a = tab(id = "A")
        val b = tab(id = "B")
        repo.observe(listOf(a, b))
        repo.registerDocument(a, pageA, 1)
        repo.registerDocument(b, pageA, 2)
        for (i in 0 until 120) {
            val segment = "https://cdn.example/segment-$i.m4s"
            assertNull(MediaClassifier.fromNetwork(
                MediaDetectorMessage.Candidate(segment, "video/mp4", MediaSource.NETWORK), a.binding))
            add(repo, a, 1, "https://cdn.example/movie-$i.mp4")
            add(repo, b, 2, "https://cdn.example/other-$i.mp4")
        }
        assertTrue(repo.forTab(a.binding).size <= 8)
        assertTrue(repo.forTab(b.binding).size <= 8)
        assertTrue(repo.currentSize() <= 12)
    }

    @Test fun numberOfTrackedSessionsIsBounded() {
        val repo = MediaRepository(scopeLimit = 3)
        val open = mutableListOf<MediaTabState>()
        for (i in 0 until 20) {
            val t = tab(id = "tab-$i")
            open.add(t)
            repo.observe(open)
            repo.registerDocument(t, pageA, i.toLong() + 1)
        }
        assertTrue(repo.activeScopes() <= 3)
        repo.observe(emptyList())
        assertEquals(0, repo.activeScopes())
    }

    @Test fun protocolAcceptsOnlyValidDocumentCorrelationAndKeepsUrlSecret() {
        val document = MediaMessageProtocol.decode(
            """{"schemaVersion":1,"event":"media_document","pageUrl":"https://site.example/watch/1","documentKey":5}""")
        assertTrue(document is MediaDetectorMessage.Document)
        val message = MediaMessageProtocol.decode(
            """{"schemaVersion":1,"event":"media_candidate","source":"network","url":"https://cdn.example/a.m3u8?token=shh","isPrivate":true,"pageUrl":"https://site.example/watch/1","documentKey":5}""")
        assertTrue(message is MediaDetectorMessage.Candidate)
        assertEquals(5L, (message as MediaDetectorMessage.Candidate).documentKey)
        assertFalse(message.toString().contains("shh"))
        for (bad in listOf(
            """{"schemaVersion":1,"event":"media_document","pageUrl":"https://site.example/watch/1","documentKey":-1}""",
            """{"schemaVersion":1,"event":"media_document","pageUrl":"https://site.example/watch/1","documentKey":"5"}""",
            """{"schemaVersion":1,"event":"media_document","pageUrl":"file:///bad","documentKey":5}""",
            """{"schemaVersion":1,"event":"media_document","pageUrl":"https://site.example/watch/1","documentKey":5,"tabId":"spoof"}""",
            """{"schemaVersion":1,"event":"media_candidate","source":"dom","url":"https://cdn.example/a.mp4","documentKey":5}"""
        )) assertNull(bad, MediaMessageProtocol.decode(bad))
    }
}
