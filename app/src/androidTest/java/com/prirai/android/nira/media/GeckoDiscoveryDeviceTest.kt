package com.prirai.android.nira.media

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prirai.android.nira.BrowserApp
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.concept.engine.EngineView
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real BrowserApp / engine / shipped WebExtension / native protocol / repository.
 * No EngineSession or extension mocks. Requires the explicit x86_64 test build.
 * These tests do NOT exercise native MPV or a physical phone.
 */
@RunWith(AndroidJUnit4::class)
class GeckoDiscoveryDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as BrowserApp
    private lateinit var activity: Activity
    private lateinit var server: FixtureServer
    private val tabs = mutableListOf<String>()
    private var view: EngineView? = null

    @Before fun setup() {
        server = FixtureServer(instrumentation.context.assets.open("discovery/fixture.mp4").readBytes())
        activity = instrumentation.startActivitySync(Intent().setClassName(
            instrumentation.targetContext.packageName,
            "com.prirai.android.nira.media.discovery.GeckoDiscoveryActivity"
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("extension installed") { MediaDetectorDiagnostics.snapshot(null).installState == "Zainstalowane" }
    }
    @After fun cleanup() {
        instrumentation.runOnMainSync {
            view?.release()
            tabs.forEach { app.components.store.dispatch(TabListAction.RemoveTabAction(it)) }
            if (::activity.isInitialized) activity.finish()
        }
        if (::server.isInitialized) server.close()
    }
    private fun await(label: String, predicate: () -> Boolean) = runBlocking {
        try { withTimeout(90_000) {
            while (true) {
                var done = false
                instrumentation.runOnMainSync { done = predicate() }
                if (done) break
                delay(50) // bounded condition wait; no fixed playback/registration sleep
            }
        } } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            var summary = ""
            instrumentation.runOnMainSync {
                val selected = app.components.store.state.selectedTabId
                val counters = MediaDetectorDiagnostics.snapshot(selected)
                summary = "label=$label entries=${counters.selectedTabMediaCount} stale=${counters.staleCount} validation=${counters.validationErrorCount} discovery=${counters.discovery} anonymous=${counters.anonymousDiscovery}"
            }
            throw AssertionError(summary, timeout)
        }
    }
    private fun open(path: String, profile: String? = null, privateMode: Boolean = false): String {
        var id = ""
        instrumentation.runOnMainSync {
            id = app.components.tabsUseCases.addTab(server.url(path), selectTab = true,
                contextId = profile, private = privateMode)
        }
        tabs.add(id)
        await("engine session") { app.components.store.state.tabs.find { it.id == id }?.engineState?.engineSession != null }
        instrumentation.runOnMainSync {
            view?.release()
            view = app.components.engine.createView(activity)
            activity.setContentView(view!!.asView())
            view!!.render(app.components.store.state.tabs.first { it.id == id }.engineState.engineSession!!)
        }
        return id
    }
    private fun entries(id: String): List<MediaEntry> {
        val tab = app.components.store.state.tabs.find { it.id == id } ?: return emptyList()
        val session = tab.engineState.engineSession ?: return emptyList()
        return MediaDetectorController.uiRepository.value?.currentEntries(MediaTabState(
            MediaSessionBinding(session, tab.id, tab.contextId, tab.content.private), tab.content.url, tab.content.loading
        )).orEmpty()
    }
    private fun expect(path: String, name: String, profile: String? = null, privateMode: Boolean = false) {
        val start = SystemClock.elapsedRealtime()
        val id = open(path, profile, privateMode)
        await(name) { entries(id).any { it.url.contains(name) } }
        val elapsed = SystemClock.elapsedRealtime() - start
        // Only categories/counts/timing; synthetic addresses are not logged either.
        android.util.Log.i("DiscoveryDeviceTest", "case=$path accepted=${entries(id).size} firstResultMs=$elapsed")
    }
    @Test fun mp4BeforePlay() = expect("/basic", "fixture.mp4")
    @Test fun hlsBeforePlay() = expect("/hls", "stream.m3u8")
    @Test fun earlyNetworkBeforeRegistration() = expect("/early", "manifest-early")
    @Test fun dynamicVideo() = expect("/dynamic", "fixture.mp4")
    @Test fun crossOriginIframe() = expect("/cross", "fixture.mp4")
    @Test fun nestedIframe() = expect("/nested", "fixture.mp4")
    @Test fun aboutBlankIframe() = expect("/blank", "fixture.mp4")
    @Test fun srcdocIframe() = expect("/srcdoc", "fixture.mp4")
    @Test fun inheritedBlankNetworkPrincipal() = expect("/blank-network", "manifest-early")
    @Test fun srcdocNetworkPrincipal() = expect("/srcdoc-network", "manifest-early")
    @Test fun redirect() = expect("/redirect-page", "fixture.mp4")
    @Test fun spaPushStateWithoutMutation() = expect("/spa", "manifest-spa")
    @Test fun spaReplaceStateWithoutMutation() = expect("/replace", "manifest-spa")
    @Test fun selectedTabSwitchDoesNotMisassignSameUrl() {
        val a = open("/basic")
        await("first same URL") { entries(a).isNotEmpty() }
        val b = open("/basic")
        await("second same URL") { entries(b).isNotEmpty() }
        instrumentation.runOnMainSync { app.components.tabsUseCases.selectTab(a) }
        assertEquals(1, entries(a).size)
        assertEquals(1, entries(b).size)
    }
    @Test fun lateResponseAfterNavigationIsRejected() {
        val id = open("/late")
        await("late request issued") { server.waiting.get() > 0 }
        instrumentation.runOnMainSync { app.components.sessionUseCases.loadUrl(server.url("/negative")) }
        await("new navigation complete") { app.components.store.state.tabs.first { it.id == id }.content.let { !it.loading && it.url.endsWith("/negative") } }
        server.gate.countDown()
        await("late response completed") { server.completed.get() > 0 }
        assertTrue(entries(id).isEmpty())
    }
    @Test fun removedFrameRetiresOnlyItsOwnResults() {
        val id = open("/remove-frame")
        await("frame initially present") { entries(id).any { it.frameKey != null } }
        server.gate.countDown()
        await("removal applied") { server.completed.get() > 0 && entries(id).none { it.frameKey != null } }
    }
    @Test fun replacementFrameCannotInheritOldMedia() {
        val id = open("/replace-frame")
        await("old frame initially present") { entries(id).any { it.url.contains("fixture.mp4") } }
        server.gate.countDown()
        await("replacement new result") { entries(id).any { it.url.contains("second.mp4") } }
        assertTrue(entries(id).none { it.url.contains("fixture.mp4") })
    }
    @Test fun profileIsolation() {
        val a = open("/basic", "discovery-profile-A")
        await("profile A") { entries(a).isNotEmpty() }
        val b = open("/negative", "discovery-profile-B")
        await("profile B loaded") { app.components.store.state.tabs.first { it.id == b }.content.loading == false }
        assertTrue(entries(b).isEmpty())
        assertTrue(entries(a).isNotEmpty())
    }
    @Test fun privateIsolation() {
        val a = open("/basic")
        await("regular") { entries(a).isNotEmpty() }
        val b = open("/negative", privateMode = true)
        await("private loaded") { !app.components.store.state.tabs.first { it.id == b }.content.loading }
        assertTrue(entries(b).isEmpty())
        assertTrue(entries(a).isNotEmpty())
        expect("/basic", "fixture.mp4", privateMode = true)
    }
    @Test fun closedSessionCannotRetainResultsAndReopenStillWorks() {
        val id = open("/basic")
        await("first open") { entries(id).isNotEmpty() }
        instrumentation.runOnMainSync { app.components.store.dispatch(TabListAction.RemoveTabAction(id)) }
        await("closed") { entries(id).isEmpty() }
        expect("/basic", "fixture.mp4")
    }

    private class FixtureServer(private val mp4: ByteArray) : AutoCloseable {
        val gate = CountDownLatch(1)
        val waiting = AtomicInteger(0)
        val completed = AtomicInteger(0)
        private val socket = ServerSocket(0)
        private val workers = Executors.newFixedThreadPool(4)
        private val acceptor = Thread({
            while (!socket.isClosed) try { val client = socket.accept(); workers.execute {
                try { serve(client) }
                catch (_: java.io.IOException) { client.close() } // expected browser cancellation / broken pipe
                catch (_: InterruptedException) { Thread.currentThread().interrupt(); client.close() } // fixture shutdown
            } }
            catch (_: Exception) { if (!socket.isClosed) throw IllegalStateException("fixture_accept") }
        }, "discovery-fixture").apply { isDaemon = true; start() }
        fun url(path: String) = "http://127.0.0.1:${socket.localPort}$path"
        private fun serve(client: Socket) = client.use {
            client.soTimeout = 10_000
            val reader = client.getInputStream().bufferedReader()
            val request = reader.readLine() ?: return@use
            val path = request.substringAfter(' ').substringBefore(' ').substringBefore('?')
            var headers = 0
            while (headers++ < 64) { if (reader.readLine().isNullOrEmpty()) break }
            var type = "text/html"; var status = "200 OK"; var extra = ""
            val video = "<video preload='none' src='${url("/fixture.mp4")}'></video>"
            val networkScript = "<script>fetch('${url("/manifest-early")}')<\\/script>"
            val body = when (path) {
                "/fixture.mp4", "/second.mp4" -> { type = "video/mp4"; mp4 }
                "/stream.m3u8", "/manifest-early", "/manifest-spa" -> {
                    type = "application/vnd.apple.mpegurl"; "#EXTM3U\n#EXT-X-TARGETDURATION:1\n#EXTINF:1,\nfixture.mp4\n#EXT-X-ENDLIST".toByteArray()
                }
                "/basic", "/frame" -> video.toByteArray()
                "/hls" -> "<video preload='none'><source type='application/vnd.apple.mpegurl' src='${url("/stream.m3u8")}'></video>".toByteArray()
                "/early" -> "<script>fetch('/manifest-early')</script>".toByteArray()
                "/dynamic" -> "<script>document.addEventListener('DOMContentLoaded',()=>{const v=document.createElement('video');v.preload='none';v.src='/fixture.mp4';document.body.append(v);});</script>".toByteArray()
                "/cross" -> "<iframe src='http://localhost:${socket.localPort}/frame'></iframe>".toByteArray()
                "/nested" -> "<iframe src='/cross'></iframe>".toByteArray()
                "/blank" -> "<iframe id='f'></iframe><script>f.contentDocument.open();f.contentDocument.write(\"$video\");f.contentDocument.close();</script>".toByteArray()
                "/blank-network" -> "<iframe id='f'></iframe><script>f.contentDocument.open();f.contentDocument.write(\"$networkScript\");f.contentDocument.close();</script>".toByteArray()
                "/srcdoc-network" -> "<iframe srcdoc=\"<script>fetch('${url("/manifest-early")}')</script>\"></iframe>".toByteArray()
                "/srcdoc" -> "<iframe srcdoc=\"${video.replace("'", "&apos;")}\"></iframe>".toByteArray()
                "/redirect-page" -> "<script>fetch('/redirect-media')</script>".toByteArray()
                "/redirect-media" -> { status = "302 Found"; extra = "Location: /fixture.mp4\r\n"; byteArrayOf() }
                "/replace" -> "<script>document.addEventListener('DOMContentLoaded',()=>{history.replaceState({},'', '/replace-next');fetch('/manifest-spa');});</script>".toByteArray()
                "/late" -> "<script>fetch('/late-response')</script>".toByteArray()
                "/late-response" -> {
                    waiting.incrementAndGet(); gate.await(15, TimeUnit.SECONDS)
                    type = "video/mp4"; completed.incrementAndGet(); mp4
                }
                "/remove-frame", "/replace-frame" -> {
                    val action = if (path == "/remove-frame") "f.remove()" else "f.src='/second-frame'"
                    "<iframe id='f' src='/frame'></iframe><script>fetch('/gate').then(()=>{$action;fetch('/done')});</script>".toByteArray()
                }
                "/second-frame" -> "<video preload='none' src='${url("/second.mp4")}'></video>".toByteArray()
                "/gate" -> { waiting.incrementAndGet(); gate.await(15, TimeUnit.SECONDS); byteArrayOf() }
                "/done" -> { completed.incrementAndGet(); byteArrayOf() }
                "/spa" -> "<script>document.addEventListener('DOMContentLoaded',()=>{history.pushState({},'', '/spa-next');fetch('/manifest-spa');});</script>".toByteArray()
                else -> "<p>no media</p>".toByteArray()
            }
            client.getOutputStream().apply {
                write("HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n${extra}Connection: close\r\n\r\n".toByteArray()); write(body); flush()
            }
        }
        override fun close() { gate.countDown(); socket.close(); workers.shutdownNow() }
    }
}
