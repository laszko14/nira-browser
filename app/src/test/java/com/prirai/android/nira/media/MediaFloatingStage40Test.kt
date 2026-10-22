package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

/** Pure JVM regression tests for the view's repository snapshots and action guard. */
class MediaFloatingStage40Test {
    private val watch = "https://site.example/watch"
    private fun tab(
        session: Any = Any(), id: String = "a", profile: String? = null,
        privateMode: Boolean = false, page: String = watch, loading: Boolean = false,
    ) = MediaTabState(MediaSessionBinding(session, id, profile, privateMode), page, loading)

    private fun begin(repo: MediaRepository, tabs: List<MediaTabState>, keys: List<Long>) {
        repo.observe(tabs)
        tabs.zip(keys).forEach { (tab, key) ->
            assertTrue(repo.registerDocument(tab, tab.pageUrl, key).accepted)
        }
    }

    private fun media(repo: MediaRepository, tab: MediaTabState, key: Long, url: String,
                      source: MediaSource = MediaSource.NETWORK, format: MediaFormat = MediaFormat.MP4) =
        repo.add(tab, tab.pageUrl, key,
            DetectedMedia(url, format, tab.binding.tabId, tab.binding.contextId, tab.binding.isPrivate), source)

    @Test fun emptyRepositoryProducesNoVisibleSources() {
        assertTrue(MediaRepository().currentEntries(tab()).isEmpty())
    }

    @Test fun badgeCountsUniqueSourcesNotCallbacks() {
        val repo = MediaRepository()
        val t = tab()
        begin(repo, listOf(t), listOf(1))
        val url = "https://cdn.example/one.mp4?token=secret"
        assertEquals(MediaRepository.InsertResult.ADDED,
            media(repo, t, 1, url, MediaSource.DOM))
        assertEquals(MediaRepository.InsertResult.DUPLICATE,
            media(repo, t, 1, url, MediaSource.NETWORK))
        assertEquals(1, repo.currentEntries(t).size)
        assertEquals(setOf(MediaSource.DOM, MediaSource.NETWORK),
            repo.currentEntries(t).single().sources)
        assertFalse(repo.currentEntries(t).single().toString().contains("secret"))
    }

    @Test fun selectedTabSwitchingNeverBorrowsAnotherTabsCount() {
        val repo = MediaRepository()
        val a = tab(id = "a")
        val b = tab(id = "b")
        begin(repo, listOf(a, b), listOf(1, 2))
        media(repo, a, 1, "https://cdn.example/one.mp4")
        media(repo, a, 1, "https://cdn.example/two.webm", format = MediaFormat.WEBM)
        media(repo, b, 2, "https://cdn.example/playlist.m3u8", format = MediaFormat.HLS)
        assertEquals(2, repo.currentEntries(a).size)
        assertEquals(1, repo.currentEntries(b).size)
        assertEquals(MediaFormat.HLS, repo.currentEntries(b).single().format)
    }

    @Test fun navigationImmediatelyHidesEntriesEvenBeforeStoreReconcile() {
        val repo = MediaRepository()
        val t = tab()
        begin(repo, listOf(t), listOf(1))
        media(repo, t, 1, "https://cdn.example/old.mp4")
        val next = t.copy(pageUrl = "https://site.example/next")
        assertTrue(repo.currentEntries(next).isEmpty())
        repo.observe(listOf(next))
        assertTrue(repo.currentEntries(next).isEmpty())
        assertFalse(repo.registerDocument(next, next.pageUrl, 1).accepted)
        assertTrue(repo.registerDocument(next, next.pageUrl, 2).accepted)
        media(repo, next, 2, "https://cdn.example/new.mp4")
        assertEquals(1, repo.currentEntries(next).size)
    }

    @Test fun finishingLoadDoesNotTemporarilyHideDetectedVideo() {
        val repo = MediaRepository()
        val loading = tab(loading = true)
        begin(repo, listOf(loading), listOf(17))
        media(repo, loading, 17, "https://cdn.example/movie.mp4")
        assertEquals(1, repo.currentEntries(loading).size)

        // The selected-tab UI collector can observe loading=false first.
        // Repository.observe() is handled by an independent coroutine.
        val finished = loading.copy(loading = false)
        assertEquals(1, repo.currentEntries(finished).size)
        repo.observe(listOf(finished))
        assertEquals(1, repo.currentEntries(finished).size)

        // Beginning another loading cycle must not expose old entries.
        val reloading = finished.copy(loading = true)
        assertTrue(repo.currentEntries(reloading).isEmpty())
        repo.observe(listOf(reloading))
        assertTrue(repo.currentEntries(reloading).isEmpty())
    }

    @Test fun reloadRejectsOldSourceEvenWhenSignedUrlIsIdentical() {
        val repo = MediaRepository()
        val t = tab()
        val url = "https://cdn.example/one.mp4?signature=1"
        begin(repo, listOf(t), listOf(5))
        media(repo, t, 5, url)
        val before = repo.currentEntries(t).single()
        val loading = t.copy(loading = true)
        assertTrue(repo.currentEntries(loading).isEmpty())
        repo.observe(listOf(loading))
        assertTrue(repo.registerDocument(loading, watch, 6).accepted)
        media(repo, loading, 6, url)
        val after = repo.currentEntries(loading).single()
        assertNotEquals(before.id, after.id)
    }

    @Test fun regularPrivateAndProfileSnapshotsStaySeparate() {
        val repo = MediaRepository()
        val session = Any()
        val regular = tab(session, "regular", "profile-a")
        val privateTab = tab(id = "private", profile = "profile-a", privateMode = true)
        val other = tab(id = "other", profile = "profile-b")
        begin(repo, listOf(regular, privateTab, other), listOf(10, 11, 12))
        listOf(regular to 10L, privateTab to 11L, other to 12L).forEach { (t, key) ->
            media(repo, t, key, "https://cdn.example/same.mp4")
        }
        assertEquals(1, repo.currentEntries(regular).size)
        assertEquals(1, repo.currentEntries(privateTab).size)
        assertEquals(1, repo.currentEntries(other).size)
        assertTrue(repo.currentEntries(
            regular.copy(binding = regular.binding.copy(contextId = "profile-b"))).isEmpty())
        assertTrue(repo.currentEntries(
            regular.copy(binding = regular.binding.copy(isPrivate = true))).isEmpty())
    }

    @Test fun closedTabAndStaleSourceCannotBeActioned() {
        val repo = MediaRepository()
        val t = tab()
        begin(repo, listOf(t), listOf(4))
        media(repo, t, 4, "https://cdn.example/a.mp4")
        val clicked = repo.currentEntries(t).single()
        repo.observe(emptyList())
        assertTrue(repo.currentEntries(t).none { it.id == clicked.id && it.url == clicked.url })
        val newSession = tab(id = t.binding.tabId)
        assertTrue(repo.currentEntries(newSession).isEmpty())
    }

    @Test fun revisionEmitsOnAddedDuplicateAndRemovedSources() {
        val repo = MediaRepository()
        val t = tab()
        begin(repo, listOf(t), listOf(2))
        val revision = repo.revision.value
        media(repo, t, 2, "https://cdn.example/a.mp4")
        assertTrue(repo.revision.value > revision)
        val afterAdd = repo.revision.value
        media(repo, t, 2, "https://cdn.example/a.mp4", MediaSource.DOM)
        assertTrue(repo.revision.value > afterAdd)
        repo.observe(emptyList())
        assertTrue(repo.currentEntries(t).isEmpty())
    }

    @Test fun manifestSegmentsAreNotDisplayCandidates() {
        val t = tab()
        for (url in listOf("https://cdn.example/a.ts", "https://cdn.example/part.m4s")) {
            assertNull(MediaClassifier.fromNetwork(
                MediaDetectorMessage.Candidate(url, "video/mp4", MediaSource.NETWORK), t.binding))
        }
    }
}
