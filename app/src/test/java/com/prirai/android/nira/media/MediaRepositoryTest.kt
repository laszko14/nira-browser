package com.prirai.android.nira.media

import com.prirai.android.nira.diagnostics.testlab.VideoMetadataLedger
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

    @Test fun dashDrmManifestStatusCannotLeakToAnotherExactSource() {
        val repo = MediaRepository()
        val t = tab(profile = "work")
        begin(repo, t)
        fun offer(url: String, verification: String): MediaRepository.InsertResult {
            val message = MediaDetectorMessage.Candidate(
                url = url, contentType = "application/dash+xml",
                source = MediaSource.NETWORK, privateHint = false,
                pageUrl = t.pageUrl, documentKey = 1,
                verification = verification, httpStatus = 200
            )
            return repo.add(t, t.pageUrl, 1,
                MediaClassifier.fromNetwork(message, t.binding)!!,
                MediaSource.NETWORK, null, message)
        }
        val protected = "https://cdn.example/protected.mpd?sig=opaque"
        val plain = "https://cdn.example/plain.mpd?sig=opaque"
        assertEquals(MediaRepository.InsertResult.ADDED, offer(protected, "manifest_observed"))
        assertEquals(MediaRepository.InsertResult.DUPLICATE, offer(protected, "unplayable"))
        val blocked = repo.forTab(t.binding).single()
        assertEquals(MediaPlaybackStatus.UNPLAYABLE, blocked.playbackStatus)
        assertFalse(mediaVariantPlayable(blocked))
        assertEquals(MediaRepository.InsertResult.ADDED, offer(plain, "manifest_observed"))
        assertEquals(MediaRepository.InsertResult.DUPLICATE, offer(plain, "manifest_verified"))
        val byUrl = repo.forTab(t.binding).associateBy { it.url }
        assertEquals(MediaPlaybackStatus.UNPLAYABLE, byUrl[protected]?.playbackStatus)
        assertEquals(MediaPlaybackStatus.NEEDS_VERIFICATION, byUrl[plain]?.playbackStatus)
        assertEquals(2, byUrl.size)
    }

    @Test fun nativePosterProvenanceIsIndependentFromTitleAndFailsClosedOnConflict() {
        val repo=MediaRepository(); val t=tab(); begin(repo,t)
        val url="https://cdn.example/movie.mp4?token=private"
        fun offer(kind:String,poster:String,titleKind:String?=null):MediaRepository.InsertResult {
            val candidate=MediaDetectorMessage.Candidate(url,source=MediaSource.PAGE,
                pageUrl=t.pageUrl,documentKey=1,title=if(titleKind!=null)"Film" else null,
                titleKind=titleKind,posterUrl=poster,posterKind=kind)
            return repo.add(t,t.pageUrl,1,MediaClassifier.fromPage(candidate,t.binding)!!,
                MediaSource.PAGE,null,candidate)
        }
        assertEquals(MediaRepository.InsertResult.ADDED,
            offer("structured","https://img.example/a.jpg","player"))
        assertEquals(MediaRepository.InsertResult.DUPLICATE,
            offer("card","https://img.example/b.jpg"))
        var observed=repo.forTab(t.binding).single()
        assertEquals("https://img.example/b.jpg",observed.posterUrl)
        assertEquals("LOCAL_CARD",observed.posterOrigin)
        assertEquals(MediaRepository.InsertResult.DUPLICATE,
            offer("card","https://img.example/c.jpg"))
        observed=repo.forTab(t.binding).single()
        assertNull(observed.posterUrl)
        assertTrue(observed.posterAmbiguous)
        assertEquals(MediaRepository.InsertResult.DUPLICATE,
            offer("element","https://img.example/final.jpg"))
        observed=repo.forTab(t.binding).single()
        assertEquals("https://img.example/final.jpg",observed.posterUrl)
        assertEquals("VIDEO_ELEMENT",observed.posterOrigin)
        assertFalse(observed.posterAmbiguous)
        assertFalse(observed.toString().contains("private"))
    }

    @Test fun conflictingDimensionsOfSameSourceRemainUnknownRatherThanMixVariants() {
        val repo = MediaRepository()
        val t = tab()
        begin(repo, t)
        val url = "https://cdn.example/video.mp4"
        fun offer(w: Int, h: Int, quality: String) {
            val candidate = MediaDetectorMessage.Candidate(url,
                source = MediaSource.PAGE, pageUrl = t.pageUrl, documentKey = 1,
                width = w, height = h, quality = quality)
            repo.add(t, t.pageUrl, 1, MediaClassifier.fromPage(candidate, t.binding)!!,
                MediaSource.PAGE, null, candidate)
        }
        offer(1280, 720, "720p")
        assertEquals(720, repo.forTab(t.binding).single().height)
        offer(1920, 1080, "1080p")
        assertNull(repo.forTab(t.binding).single().height)
        assertNull(repo.forTab(t.binding).single().quality)
        offer(1280, 720, "720p")
        assertNull(repo.forTab(t.binding).single().quality)
    }

    @Test fun hundredIndividualMoviesSurviveMetadataUpdatesWithoutDuplicates() {
        val repo = MediaRepository()
        val t = tab()
        begin(repo, t)
        for (i in 1..100) {
            val url = "https://cdn.example/movie-$i.mp4"
            add(repo, t, 1, url)
            val candidate = MediaDetectorMessage.Candidate(url, source = MediaSource.PAGE,
                pageUrl = t.pageUrl, documentKey = 1, groupId = "card-$i",
                title = "Odcinek $i", titleKind = "video")
            assertEquals(MediaRepository.InsertResult.DUPLICATE,
                repo.add(t, t.pageUrl, 1, media(t, url), MediaSource.PAGE, candidate.groupId, candidate))
        }
        val entries = repo.currentEntries(t)
        assertEquals(100, entries.size)
        assertEquals((1..100).map { "Odcinek $it" }.toSet(), entries.map { it.title }.toSet())
        assertEquals(100, mediaTileItems(entries).size)
    }

    @Test fun networkRecoveryAfterErrorReopensTheExactMediaEntryAndIgnoresLateOldResponse() {
        val repo = MediaRepository()
        val t = tab(profile = "work")
        val url = "https://cdn.example/stream.mp4?sig=exact"
        begin(repo, t)
        fun offer(verification: String, httpStatus: Int, sequence: Long): MediaRepository.InsertResult {
            val candidate = MediaDetectorMessage.Candidate(
                url = url, contentType = "video/mp4", source = MediaSource.NETWORK,
                privateHint = false, pageUrl = t.pageUrl, documentKey = 1,
                verification = verification, httpStatus = httpStatus,
                requestSequence = sequence
            )
            return repo.add(t, t.pageUrl, 1,
                MediaClassifier.fromNetwork(candidate, t.binding)!!,
                MediaSource.NETWORK, null, candidate)
        }
        assertEquals(MediaRepository.InsertResult.ADDED, offer("manifest_observed", 200, 1L))
        assertEquals(MediaPlaybackStatus.NEEDS_VERIFICATION, repo.forTab(t.binding).single().playbackStatus)
        assertEquals(MediaRepository.InsertResult.DUPLICATE, offer("auth_error", 403, 2L))
        assertEquals(MediaPlaybackStatus.AUTH_ERROR, repo.forTab(t.binding).single().playbackStatus)
        assertEquals(MediaRepository.InsertResult.DUPLICATE, offer("manifest_observed", 200, 3L))
        val recovered = repo.forTab(t.binding).single()
        assertEquals(MediaPlaybackStatus.NEEDS_VERIFICATION, recovered.playbackStatus)
        assertEquals(200, recovered.httpStatus)
        assertEquals(url, recovered.url)
        assertEquals(1, repo.forTab(t.binding).size)
        assertEquals(MediaRepository.InsertResult.DUPLICATE, offer("auth_error", 403, 2L))
        assertEquals(MediaPlaybackStatus.NEEDS_VERIFICATION, repo.forTab(t.binding).single().playbackStatus)
        assertEquals(200, repo.forTab(t.binding).single().httpStatus)
        assertEquals(MediaRepository.InsertResult.DUPLICATE, offer("expired", 410, 4L))
        assertEquals(MediaPlaybackStatus.EXPIRED, repo.forTab(t.binding).single().playbackStatus)
        assertEquals(MediaRepository.InsertResult.DUPLICATE, offer("manifest_observed", 200, 5L))
        assertEquals(MediaPlaybackStatus.NEEDS_VERIFICATION, repo.forTab(t.binding).single().playbackStatus)
    }

    @Test fun twoDifferentVideosInOneTabStayIndependent() {
        val repo = MediaRepository()
        val t = tab()
        begin(repo, t)
        assertEquals(MediaRepository.InsertResult.ADDED, add(repo, t, 1, "https://cdn.example/a.mp4"))
        assertEquals(MediaRepository.InsertResult.ADDED, add(repo, t, 1, "https://cdn.example/b.mp4"))
        assertEquals(2, repo.forTab(t.binding).size)
    }

    @Test fun manifestAndItsNetworkVariantsStayOneLogicalPresentation() {
        val repo = MediaRepository()
        val t = tab()
        begin(repo, t)
        val group = "manifest-1-1"
        fun candidate(url: String, quality: String? = null) = MediaDetectorMessage.Candidate(
            url = url, contentType = "application/vnd.apple.mpegurl",
            source = MediaSource.NETWORK, privateHint = false, pageUrl = t.pageUrl,
            documentKey = 1, groupId = group, quality = quality)
        val master = candidate("https://cdn.example/master.m3u8")
        val variant = candidate("https://cdn.example/1080/index.m3u8", "1080p")
        for (item in listOf(master, variant)) {
            val detected = MediaClassifier.fromNetwork(item, t.binding)!!
            assertEquals(MediaRepository.InsertResult.ADDED,
                repo.add(t, t.pageUrl, 1, detected, MediaSource.NETWORK, item.groupId, item))
        }
        val entries = repo.currentEntries(t)
        assertEquals(2, entries.size)
        assertEquals(setOf(group), entries.map { it.groupId }.toSet())
        assertEquals("1080p", entries.single { it.url.contains("1080") }.quality)
    }

    @Test fun networkManifestReconcilesEarlierPageGroupBeforeVariantsArrive() {
        val repo = MediaRepository()
        val t = tab()
        begin(repo, t)
        val url = "https://cdn.example/master.m3u8?token=signed"
        val pageCandidate = MediaDetectorMessage.Candidate(url, source = MediaSource.PAGE,
            pageUrl = t.pageUrl, documentKey = 1, groupId = "video-element-1")
        val networkCandidate = MediaDetectorMessage.Candidate(url,
            "application/vnd.apple.mpegurl", MediaSource.NETWORK, false,
            t.pageUrl, 1, groupId = "manifest-1-1")
        repo.add(t, t.pageUrl, 1, MediaClassifier.fromPage(pageCandidate, t.binding)!!,
            MediaSource.PAGE, pageCandidate.groupId, pageCandidate)
        repo.add(t, t.pageUrl, 1, MediaClassifier.fromNetwork(networkCandidate, t.binding)!!,
            MediaSource.NETWORK, networkCandidate.groupId, networkCandidate)
        assertEquals("manifest-1-1", repo.currentEntries(t).single().groupId)
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

    @Test fun hashRouterMaterialSwitchDropsStaleMediaAndRejectsOldEpoch() {
        val repo = MediaRepository()
        val sessionA = Any()
        val original = tab(session = sessionA, id = "hash-A",
            page = "https://site.example/app#/film/a")
        val otherTab = tab(id = "other")
        repo.observe(listOf(original, otherTab))
        assertTrue(repo.registerDocument(original, original.pageUrl, 1).accepted)
        assertTrue(repo.registerDocument(otherTab, otherTab.pageUrl, 2).accepted)
        add(repo, original, 1, "https://cdn.example/movie-a.mp4")
        add(repo, otherTab, 2, "https://cdn.example/other.mp4")
        val next = original.copy(pageUrl = "https://site.example/app#/film/b")
        val changes = repo.observe(listOf(next, otherTab))
        assertEquals(1, changes.size)
        assertEquals(1, changes.single().removed)
        assertTrue(changes.single().navigation)
        assertTrue(repo.forTab(next.binding).isEmpty())
        assertEquals(1, repo.forTab(otherTab.binding).size)
        assertEquals(MediaRepository.InsertResult.STALE,
            add(repo, next, 1, "https://cdn.example/late-a.mp4"))
        assertFalse(repo.registerDocument(next, next.pageUrl, 1).accepted)
        assertTrue(repo.registerDocument(next, next.pageUrl, 3).accepted)
        assertEquals(MediaRepository.InsertResult.ADDED,
            add(repo, next, 3, "https://cdn.example/movie-b.mp4"))
        val back = next.copy(pageUrl = original.pageUrl)
        assertEquals(1, repo.observe(listOf(back, otherTab)).single().removed)
        assertTrue(repo.forTab(back.binding).isEmpty())
        assertTrue(repo.registerDocument(back, back.pageUrl, 4).accepted)
        assertEquals(MediaRepository.InsertResult.ADDED,
            add(repo, back, 4, "https://cdn.example/movie-a.mp4"))
    }

    @Test fun ordinaryAnchorDoesNotDiscardSameMovieButHashRouterDoes() {
        val repo = MediaRepository()
        val t = tab(page = "https://site.example/watch/1#intro")
        begin(repo, t)
        add(repo, t, 1, "https://cdn.example/same.mp4")
        val anchored = t.copy(pageUrl = "https://site.example/watch/1#comments")
        assertTrue(repo.observe(listOf(anchored)).isEmpty())
        assertTrue(repo.registerDocument(anchored, anchored.pageUrl, 1).accepted)
        assertEquals(1, repo.forTab(anchored.binding).size)
        val route = anchored.copy(pageUrl = "https://site.example/watch/1#/film/a")
        val removed = repo.observe(listOf(route))
        assertEquals(1, removed.single().removed)
        assertTrue(removed.single().navigation)
        assertFalse(repo.registerDocument(route, route.pageUrl, 1).accepted)
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

    @Test fun reusedEngineSessionChangingPrivacyCannotReuseOldDocumentKey() {
        val repo = MediaRepository()
        val engineSession = Any()
        val ordinary = tab(session = engineSession, profile = "first", privateMode = false)
        begin(repo, ordinary, key = 7)
        add(repo, ordinary, 7, "https://cdn.example/public.mp4")
        val switched = ordinary.copy(binding = ordinary.binding.copy(
            contextId = "second", isPrivate = true))
        repo.observe(listOf(switched))
        assertEquals(0, repo.currentSize())
        assertFalse(repo.registerDocument(switched, pageA, 7).accepted)
        assertEquals(MediaRepository.InsertResult.STALE,
            add(repo, switched, 7, "https://cdn.example/leaked.mp4"))
        assertTrue(repo.registerDocument(switched, pageA, 8).accepted)
        assertEquals(MediaRepository.InsertResult.ADDED,
            add(repo, switched, 8, "https://cdn.example/private.mp4"))
        assertTrue(repo.forTab(ordinary.binding).isEmpty())
        assertEquals(1, repo.counts("second", true).single().count)
        assertTrue(repo.counts("first", false).isEmpty())
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
    @Test fun backgroundRestartWithSurvivingNativeSessionWouldRejectReusedLowKey() {
        // Conditional protocol observation, not a device-level Gecko restart.
        val repo = MediaRepository()
        val sameEngineSession = Any()
        val normal = tab(session = sameEngineSession, id = "normal", profile = "work")
        val privateTab = tab(session = Any(), id = "private", privateMode = true)
        repo.observe(listOf(normal, privateTab))
        assertTrue(repo.registerDocument(normal, normal.pageUrl, 20L, frameTracking = true).accepted)
        assertEquals(MediaRepository.InsertResult.ADDED,
            add(repo, normal, 20L, "https://cdn.example/retained.mp4"))
        // An independent background reset can mint 1 again; native must not
        // trust it as a new generation or erase the currently retained entry.
        assertFalse(repo.registerDocument(normal, normal.pageUrl, 1L, frameTracking = true).accepted)
        assertTrue(repo.isCurrentDocument(normal, 20L))
        assertFalse(repo.isCurrentDocument(normal, 1L))
        assertEquals(1, repo.forTab(normal.binding).size)
        // A genuinely fresh EngineSession is independent, including privacy scope.
        assertTrue(repo.registerDocument(privateTab, privateTab.pageUrl, 1L, frameTracking = true).accepted)
        assertTrue(repo.forTab(privateTab.binding).isEmpty())
        assertEquals(1, repo.forTab(normal.binding).size)
        // A higher new key still retires stale native media as intended.
        assertTrue(repo.registerDocument(normal, normal.pageUrl, 21L, frameTracking = true).accepted)
        assertTrue(repo.forTab(normal.binding).isEmpty())
        assertFalse(repo.isCurrentDocument(normal, 20L))
    }


    @Test fun audit60DashFractionalFpsSurvivesNativeProtocolRepositoryAndUiFormatting() {
        for ((numerator, expectedText) in listOf(
            24000 to "23.98", 30000 to "29.97", 60000 to "59.94"
        )) {
            val rate = numerator.toDouble() / 1001
            val repo = MediaRepository()
            val t = tab(id = "fps-$numerator", profile = "test")
            begin(repo, t)
            val json = """{"schemaVersion":1,"event":"media_candidate",
                "source":"network","url":"https://cdn.example/fps-$numerator.mpd?sig=private",
                "contentType":"application/dash+xml","isPrivate":false,
                "pageUrl":"$pageA","documentKey":1,
                "verification":"manifest_verified","frameRate":$rate}"""
            val message = MediaMessageProtocol.decode(json) as MediaDetectorMessage.Candidate
            assertEquals(rate, message.frameRate!!, 1e-10)
            val detected = MediaClassifier.fromNetwork(message, t.binding)!!
            assertEquals(MediaRepository.InsertResult.ADDED,
                repo.add(t, t.pageUrl, 1L, detected, MediaSource.NETWORK, null, message))
            val entry = repo.forTab(t.binding).single()
            assertEquals(rate, entry.frameRate!!, 1e-10)
            assertEquals(expectedText,
                String.format(java.util.Locale.ROOT, "%.2f", entry.frameRate))
            val diagnostics = VideoMetadataLedger()
            diagnostics.observe(t.binding, entry)
            assertEquals(1, diagnostics.snapshot().fpsKnown)
            assertFalse(entry.toString().contains("private"))
        }
    }


}
