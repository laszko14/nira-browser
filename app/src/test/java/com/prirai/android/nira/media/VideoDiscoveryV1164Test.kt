package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

class VideoDiscoveryV1164Test {
    @Test fun opaqueFrameUrlsAreAllowedOnlyInTheFrameField() {
        for (frame in listOf("about:blank", "about:srcdoc")) {
            val raw = """{"schemaVersion":1,"event":"media_candidate","source":"page","url":"https://cdn.test/movie.mp4","pageUrl":"https://page.test/watch","documentKey":1,"frameUrl":"$frame","frameKey":2}"""
            assertNotNull(MediaMessageProtocol.decode(raw))
            assertNull(MediaMessageProtocol.decode(raw.replace("https://cdn.test/movie.mp4", frame)))
            assertNull(MediaMessageProtocol.decode(raw.replace("https://page.test/watch", frame)))
            for (invalid in listOf("about:config", "about:blank#forged", "file:///private", "javascript:alert(1)"))
                assertNull(MediaMessageProtocol.decode(raw.replace(frame, invalid)))
        }
    }
    @Test fun fragmentChangePreservesCurrentMediaButRouteChangeAndReloadClearIt() {
        val session = Any()
        val first = MediaTabState(MediaSessionBinding(session, "tab", "A", false), "https://page.test/watch#one", false)
        val repo = MediaRepository()
        repo.observe(listOf(first)); assertTrue(repo.registerDocument(first, first.pageUrl, 1).accepted)
        val media = DetectedMedia("https://cdn.test/movie.mp4", MediaFormat.MP4, "tab", "A", false)
        repo.add(first, first.pageUrl, 1, media, MediaSource.PAGE)
        val fragment = first.copy(pageUrl = "https://page.test/watch#two")
        repo.observe(listOf(fragment))
        assertEquals(1, repo.currentEntries(fragment).size)
        assertTrue(repo.registerDocument(fragment, fragment.pageUrl, 1).accepted)
        assertEquals(MediaRepository.InsertResult.STALE, repo.add(fragment, first.pageUrl, 1, media, MediaSource.PAGE))
        val next = fragment.copy(pageUrl = "https://page.test/next")
        repo.observe(listOf(next)); assertTrue(repo.currentEntries(next).isEmpty())
        assertTrue(repo.registerDocument(next, next.pageUrl, 2).accepted)
        repo.add(next, next.pageUrl, 2, media, MediaSource.PAGE)
        val reload = next.copy(loading = true)
        repo.observe(listOf(reload)); assertTrue(repo.currentEntries(reload).isEmpty())
    }
    @Test fun earlyDiagnosticsHaveOnlyBoundedAllowlistedCodes() {
        val raw = """{"schemaVersion":1,"event":"discovery_stats","code":"early_request_bound","count":1,"isPrivate":false}"""
        assertNotNull(MediaMessageProtocol.decode(raw))
        assertNull(MediaMessageProtocol.decode(raw.replace("early_request_bound", "https://private.test/token")))
    }
    @Test fun frameTrackingIsAnExplicitCompatibleProtocolExtension() {
        assertNotNull(MediaMessageProtocol.decode("""{"schemaVersion":1,"event":"media_document","pageUrl":"https://page.test/watch","documentKey":1}"""))
        assertNotNull(MediaMessageProtocol.decode("""{"schemaVersion":1,"event":"media_document","pageUrl":"https://page.test/watch","documentKey":1,"frameTracking":true}"""))
        assertNotNull(MediaMessageProtocol.decode("""{"schemaVersion":1,"event":"media_frame","pageUrl":"https://page.test/watch","documentKey":1,"frameUrl":"about:blank","frameId":7,"frameKey":9,"active":true}"""))
        assertNull(MediaMessageProtocol.decode("""{"schemaVersion":1,"event":"media_frame","pageUrl":"https://page.test/watch","documentKey":1,"frameUrl":"about:config","frameId":7,"frameKey":9,"active":true}"""))
    }
    @org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
    class RootBlobBindingTest {
        @org.robolectric.annotation.Config(sdk=[35],application=android.app.Application::class,manifest=org.robolectric.annotation.Config.NONE)
        @Test fun aRootNetworkHlsBelongsToTheRootFrameWithoutInferringAnIframe() {
            val tab = MediaTabState(MediaSessionBinding(Any(), "tab", null, false), "https://page.test/watch", false)
            val repo = MediaRepository(); repo.observe(listOf(tab)); repo.registerDocument(tab, tab.pageUrl, 1)
            val candidate = MediaDetectorMessage.Candidate("https://cdn.test/root.m3u8", source=MediaSource.NETWORK, privateHint=false)
            val media = MediaClassifier.fromNetwork(candidate,tab.binding)!!
            repo.add(tab,tab.pageUrl,1,media,MediaSource.NETWORK,metadata=candidate)
            assertEquals(candidate.url,BlobHlsSelector.select(repo.currentEntries(tab),tab.pageUrl)?.url)
            assertNull(BlobHlsSelector.select(repo.currentEntries(tab),"https://frame.test/embed"))
        }
    }

    @Test fun frameRetirementRejectsLateDataAndCannotRetireAReplacement() {
        val tab = MediaTabState(MediaSessionBinding(Any(),"tab","A",false),"https://page.test/watch",false)
        val repo = MediaRepository(); repo.observe(listOf(tab)); repo.registerDocument(tab,tab.pageUrl,1,true)
        val frame = MediaDetectorMessage.Frame(tab.pageUrl,1,"https://frame.test/embed",7,11,true)
        assertTrue(repo.registerFrame(tab,frame))
        val candidate = MediaDetectorMessage.Candidate("https://cdn.test/stream.m3u8",source=MediaSource.NETWORK,
            privateHint=false,frameKey=11,frameUrl=frame.frameUrl)
        val media = MediaClassifier.fromNetwork(candidate,tab.binding)!!
        assertEquals(MediaRepository.InsertResult.ADDED,repo.add(tab,tab.pageUrl,1,media,MediaSource.NETWORK,metadata=candidate))
        assertTrue(repo.registerFrame(tab,MediaDetectorMessage.Frame(tab.pageUrl,1,frame.frameUrl,7,11,false)))
        assertTrue(repo.currentEntries(tab).isEmpty())
        assertEquals(MediaRepository.InsertResult.STALE,repo.add(tab,tab.pageUrl,1,media,MediaSource.NETWORK,metadata=candidate))
        assertTrue(repo.registerFrame(tab,MediaDetectorMessage.Frame(tab.pageUrl,1,frame.frameUrl,7,12,true)))
        assertFalse(repo.registerFrame(tab,MediaDetectorMessage.Frame(tab.pageUrl,1,frame.frameUrl,7,11,false)))
        assertFalse(repo.registerFrame(tab,MediaDetectorMessage.Frame(tab.pageUrl,1,"https://forged.test/",7,12,true)))
        val other = tab.copy(binding=MediaSessionBinding(tab.binding.session,"tab","B",false))
        repo.observe(listOf(other)); assertFalse(repo.registerFrame(other,frame))
    }
    @org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
    class FrameAmbiguityTest {
        @org.robolectric.annotation.Config(sdk=[35],application=android.app.Application::class,manifest=org.robolectric.annotation.Config.NONE)
        @Test fun twoSameUrlFramesRemainAmbiguousEvenIfOnlyOneAdvertisesHls() {
            val tab = MediaTabState(MediaSessionBinding(Any(),"tab",null,false),"https://page.test/watch",false)
            val repo = MediaRepository(); repo.observe(listOf(tab)); repo.registerDocument(tab,tab.pageUrl,1,true)
            val url = "https://frame.test/embed"
            assertTrue(repo.registerFrame(tab,MediaDetectorMessage.Frame(tab.pageUrl,1,url,1,11,true)))
            val candidate = MediaDetectorMessage.Candidate("https://cdn.test/movie.m3u8",source=MediaSource.NETWORK,
                privateHint=false,frameUrl=url,frameKey=11)
            val media = MediaClassifier.fromNetwork(candidate,tab.binding)!!
            repo.add(tab,tab.pageUrl,1,media,MediaSource.NETWORK,metadata=candidate)
            assertNotNull(BlobHlsSelector.select(repo.currentEntries(tab),url))
            assertTrue(repo.registerFrame(tab,MediaDetectorMessage.Frame(tab.pageUrl,1,url,2,12,true)))
            assertEquals(1,repo.currentEntries(tab).size) // manual selection is still available
            assertTrue(repo.currentEntries(tab).single().frameAmbiguous)
            assertNull(BlobHlsSelector.select(repo.currentEntries(tab),url))
            assertNotNull(BlobHlsSelector.select(repo.currentEntries(tab),url,11))
            assertNull(BlobHlsSelector.select(repo.currentEntries(tab),url,12))
            assertTrue(repo.registerFrame(tab,MediaDetectorMessage.Frame(tab.pageUrl,1,url,2,12,false)))
            assertNotNull(BlobHlsSelector.select(repo.currentEntries(tab),url))
        }
    }

}
