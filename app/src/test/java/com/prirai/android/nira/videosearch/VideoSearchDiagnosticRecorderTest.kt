package com.prirai.android.nira.videosearch

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchDiagnosticRecorderTest {
    private fun event(
        trace: VideoSearchDiagnosticTrace = VideoSearchDiagnosticTrace(),
        fields: Map<VideoSearchDiagnosticField, String> = emptyMap(),
    ) = VideoSearchDiagnosticEvent(
        stage = VideoSearchDiagnosticStage.CARDS,
        claim = VideoSearchDiagnosticClaim.OBSERVATION,
        decision = VideoSearchDiagnosticDecision.SEEN,
        reason = "VISIBLE_CARDS_COUNTED", trace = trace,
        counts = mapOf(VideoSearchDiagnosticCount.VISIBLE_CARDS to 3),
        fields = fields,
    )
    private fun folder() = File(
        RuntimeEnvironment.getApplication<Application>().cacheDir,
        "diagnostic-test-" + UUID.randomUUID(),
    )
    private fun records(recorder: VideoSearchDiagnosticRecorder): List<JSONObject> =
        recorder.file!!.readLines().map(::JSONObject)

    @Test fun normalSessionHasVersionedCorrelationAndTerminalMarker() {
        val recorder = VideoSearchDiagnosticRecorder(
            VideoSearchScope.Normal("profile-A"), folder())
        val trace = VideoSearchDiagnosticTrace(
            runId = "run-1", siteId = "site-A", documentId = "gecko-1",
            requestId = "request-1", workerId = "worker-1", batchId = "batch-1")
        assertTrue(recorder.record(event(trace)))
        recorder.stop()
        assertTrue(recorder.awaitStopped())
        val list = records(recorder)
        assertEquals(listOf("session_start", "event", "session_end"),
            list.map { it.getString("kind") })
        assertEquals(1, list[1].getInt("schemaVersion"))
        assertEquals(recorder.sessionId, list[1].getString("sessionId"))
        assertEquals(1L, list[1].getLong("sequence"))
        val keys = list[1].getJSONObject("trace")
        assertEquals("site-A", keys.getString("siteId"))
        assertEquals("gecko-1", keys.getString("documentId"))
        assertEquals("request-1", keys.getString("requestId"))
        assertEquals("worker-1", keys.getString("workerId"))
        assertEquals("batch-1", keys.getString("batchId"))
        assertEquals("OBSERVATION", list[1].getString("claim"))
        assertEquals(3, list[1].getJSONObject("counts").getInt("VISIBLE_CARDS"))
        assertEquals(0L, list.last().getLong("dropped"))
        assertEquals("CLOSED", VideoSearchDiagnosticRecorder.diskState(recorder.file!!))
        assertFalse(recorder.record(event()))
    }

    @Test fun concurrentSitesHaveMonotonicUniqueSequences() {
        val recorder = VideoSearchDiagnosticRecorder(
            VideoSearchScope.Normal("profile-A"), folder(), queueCapacity = 2048)
        val start = CountDownLatch(1)
        val threads = (0 until 6).map { worker ->
            thread {
                assertTrue(start.await(3, TimeUnit.SECONDS))
                repeat(90) { i ->
                    assertTrue(recorder.record(event(VideoSearchDiagnosticTrace(
                        runId = "shared-run", siteId = "site-" + worker,
                        documentId = "doc-" + worker, requestId = "req-" + i))))
                }
            }
        }
        start.countDown()
        threads.forEach { it.join() }
        recorder.stop()
        assertTrue(recorder.awaitStopped())
        val list = records(recorder).filter { it.getString("kind") == "event" }
        assertEquals(540, list.size)
        assertEquals((1L..540L).toList(), list.map { it.getLong("sequence") })
        assertEquals(6, list.map { it.getJSONObject("trace").getString("siteId") }
            .toSet().size)
        assertEquals(0L, recorder.state().dropped)
    }

    @Test fun eventLimitDropsAreExplicit() {
        val recorder = VideoSearchDiagnosticRecorder(
            VideoSearchScope.Normal("profile-A"), folder(), maxEvents = 3)
        repeat(25) { recorder.record(event()) }
        recorder.stop("CANCELLED")
        assertTrue(recorder.awaitStopped())
        assertEquals(25L, recorder.state().attempted)
        assertEquals(3L, recorder.state().recorded)
        assertEquals(22L, recorder.state().dropped)
        val tail = records(recorder).last()
        assertEquals("CANCELLED", tail.getString("reason"))
        assertTrue(tail.getBoolean("limited"))
        assertEquals(22L, tail.getLong("dropped"))
    }

    @Test fun byteLimitRetainsEndMarkerAndSignalsLoss() {
        val recorder = VideoSearchDiagnosticRecorder(
            VideoSearchScope.Normal("profile-A"), folder(), maxBytes = 2300)
        repeat(40) { recorder.record(event(fields = mapOf(
            VideoSearchDiagnosticField.TITLE to "X".repeat(1000)))) }
        recorder.stop()
        assertTrue(recorder.awaitStopped())
        assertTrue(recorder.state().dropped > 0)
        assertTrue(recorder.file!!.length() <= 2300)
        assertTrue(records(recorder).last().getBoolean("limited"))
        assertEquals("CLOSED", VideoSearchDiagnosticRecorder.diskState(recorder.file!!))
    }

    @Test fun timeLimitAndCrashStateAreNeverSuccessClaims() {
        val clock = AtomicLong(1000L)
        val recorder = VideoSearchDiagnosticRecorder(
            VideoSearchScope.Normal("profile-A"), folder(),
            now = clock::get, maxDurationMs = 100L)
        assertTrue(recorder.record(event()))
        clock.set(1200L)
        assertTrue(recorder.awaitStopped())
        assertEquals("TIME_LIMIT", records(recorder).last().getString("reason"))
        assertFalse(recorder.record(event()))
        val partial = File(folder(), "incomplete.jsonl")
        partial.parentFile!!.mkdirs()
        partial.writeText("""{"kind":"session_start"}""" + "\n")
        assertEquals("INTERRUPTED", VideoSearchDiagnosticRecorder.diskState(partial))
        partial.appendText("{invalid json}\n")
        assertEquals("INTERRUPTED", VideoSearchDiagnosticRecorder.diskState(partial))
    }

    @Test fun secretsAreScrubbedButNonSensitiveUrlParametersStay() {
        val owner = VideoSearchScope.Private("profile-A", "private-lifetime-A")
        val recorder = VideoSearchDiagnosticRecorder(owner, null)
        assertTrue(recorder.record(event(fields = mapOf(
            VideoSearchDiagnosticField.PAGE_URL to
                "https://user:password@films.example/search?q=space&token=secret123&state=a",
            VideoSearchDiagnosticField.FILM_URL to
                "https://films.example/watch?film=10&lang=pl&access_token=hidden",
            VideoSearchDiagnosticField.SEARCH_QUERY to "space password=topsecret",
            VideoSearchDiagnosticField.DETAIL to "Authorization: Bearer xyz987\nCookie: sid=abc",
            VideoSearchDiagnosticField.DOM_FRAGMENT to
                """<div title="film"><input type="password" value="swordfish"><textarea>mypassword</textarea><span>Film</span></div>""",
        ))))
        recorder.stop()
        assertTrue(recorder.awaitStopped())
        val raw = recorder.privateLines(owner).joinToString("\n")
        for (secret in listOf("secret123", "hidden", "topsecret", "xyz987",
            "sid=abc", "swordfish", "mypassword", "user:password")) {
            assertFalse("leaked: " + secret, raw.contains(secret))
        }
        assertTrue(raw.contains("film=10"))
        assertTrue(raw.contains("lang=pl"))
        assertTrue(raw.contains("Film"))
    }

    @Test fun privateLifetimeIsOnlyRamAndRetirementRejectsLateEvents() {
        val owner = VideoSearchScope.Private("profile-A", "epoch-A")
        val other = VideoSearchScope.Private("profile-A", "epoch-B")
        val recorder = VideoSearchDiagnosticRecorder(owner, null)
        assertNull(recorder.file)
        assertTrue(recorder.record(event(VideoSearchDiagnosticTrace(runId = "same-run"))))
        recorder.stop()
        assertTrue(recorder.awaitStopped())
        assertTrue(recorder.privateLines(owner).isNotEmpty())
        assertTrue(recorder.privateLines(other).isEmpty())
        assertFalse(recorder.retirePrivateLifetime(other))
        assertTrue(recorder.retirePrivateLifetime(owner))
        assertTrue(recorder.privateLines(owner).isEmpty())
        assertFalse(recorder.record(event()))
        assertTrue(recorder.state().retired)
    }

    @Test fun invalidScopeStorageCombinationsAreRejected() {
        val destination = folder()
        try {
            VideoSearchDiagnosticRecorder(
                VideoSearchScope.Private("profile-A", "epoch"), destination)
            fail("private diagnostics must never persist")
        } catch (_: IllegalArgumentException) {
            assertFalse(destination.exists())
        }
        try {
            VideoSearchDiagnosticRecorder(VideoSearchScope.Normal("profile-A"), null)
            fail("normal diagnostics need isolated local storage")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
