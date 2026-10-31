package com.prirai.android.nira.diagnostics.geckog3

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prirai.android.nira.BrowserApp
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

/**
 * G3 PREPARATION ONLY. Compiled by CI, NOT executed there.
 *
 * Opt-in instrumentation args:
 *   gv_g3_opt_in=synthetic-only
 *   gv_g3_origin=https://fully.trusted.fixture.example
 *
 * Read-only BrowserStore sampling: no tabs or profiles created, navigated,
 * removed or modified; no Gecko storage reset; no app installation/backup.
 * No extension restart, OriginAttributes or native documentKey can be inferred.
 */
@RunWith(AndroidJUnit4::class)
class GeckoG3PassiveObservationTest {
    @Test fun sampleAlreadyOpenedSyntheticFixtureSessionsOnly() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("G3 requires explicit synthetic-only opt-in",
            args.getString("gv_g3_opt_in") == "synthetic-only")

        val origin = runCatching { URI(args.getString("gv_g3_origin") ?: "") }.getOrNull()
        assumeTrue("G3 requires a trusted HTTPS origin with no credentials/path/query",
            origin?.scheme == "https" && !origin.host.isNullOrBlank() &&
                origin.rawUserInfo == null && origin.rawQuery == null &&
                origin.rawFragment == null && (origin.rawPath.isNullOrEmpty() || origin.rawPath == "/"))
        val expected = origin!!

        fun fixtureOnly(raw: String): Boolean {
            val parsed = runCatching { URI(raw) }.getOrNull() ?: return false
            return parsed.scheme == "https" &&
                parsed.host.equals(expected.host, ignoreCase = true) &&
                parsed.port == expected.port &&
                parsed.rawUserInfo == null && parsed.rawQuery == null &&
                parsed.path in setOf("/", "/index.html", "/popup.html", "/video.html")
        }

        val app = instrumentation.targetContext.applicationContext as BrowserApp
        val trace = GeckoG3RedactedTrace(limit = 128)
        val previous = mutableMapOf<String, Any>()
        var fixtureFound = false
        repeat(20) { sampleIndex ->
            val matches = app.components.store.state.tabs.filter { fixtureOnly(it.content.url) }
            matches.forEach { tab ->
                fixtureFound = true
                val session = tab.engineState.engineSession
                val was = previous.put(tab.id, session ?: NoSession)
                val kind = when {
                    session == null -> GeckoG3RedactedTrace.Kind.SESSION_MISSING
                    was == null -> GeckoG3RedactedTrace.Kind.SESSION_BOUND
                    was !== session -> GeckoG3RedactedTrace.Kind.SESSION_REPLACED
                    sampleIndex == 19 -> GeckoG3RedactedTrace.Kind.SESSION_STABLE
                    else -> null
                }
                if (kind != null) {
                    trace.record(kind, SystemClock.elapsedRealtime(), session, tab.id,
                        tab.contextId, tab.content.private)
                }
            }
            if (sampleIndex < 19) SystemClock.sleep(250L)
        }
        assumeTrue("No pre-existing synthetic fixture tab; did not touch user tabs", fixtureFound)
        assertTrue(trace.entries().isNotEmpty())
        trace.entries().forEach { Log.i("ZavroG3Redacted", it.redactedLine()) }
        Log.i("ZavroG3Redacted",
            "G3 passive snapshot only: Gecko OriginAttributes, extension epoch, native decision NOT_OBSERVED")
    }

    private object NoSession
}
