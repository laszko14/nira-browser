package com.prirai.android.nira.diagnostics.geckog3

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prirai.android.nira.BrowserApp
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.media.MediaDetectorController
import com.prirai.android.nira.media.MediaSessionBinding
import mozilla.components.browser.engine.gecko.GeckoEngineSession
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI
import java.util.UUID

/** Only read existing synthetic fixtures; compiled, but not run in CI. */
@RunWith(AndroidJUnit4::class)
class GeckoG3NativeEvidenceTest {
    @Test fun readOnlyNativeSettingsAndMediaCandidates() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("gv_g3_opt_in") == "synthetic-only")
        val origin = runCatching { URI(args.getString("gv_g3_origin") ?: "") }.getOrNull()
        assumeTrue(origin?.scheme == "https" && !origin.host.isNullOrBlank() &&
            origin.rawUserInfo == null && origin.rawQuery == null &&
            origin.rawFragment == null &&
            (origin.rawPath.isNullOrEmpty() || origin.rawPath == "/"))
        val expected = origin!!

        fun fixturePath(raw: String): String? {
            val uri = runCatching { URI(raw) }.getOrNull() ?: return null
            if (uri.scheme != "https" || !uri.host.equals(expected.host, true) ||
                uri.port != expected.port || uri.rawUserInfo != null ||
                uri.rawQuery != null || uri.rawFragment != null) return null
            return uri.path.takeIf { it in setOf(
                "/", "/index.html", "/popup.html", "/video.html",
                "/media/a.mp4", "/media/b.mp4"
            ) }
        }
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as BrowserApp
        val eligible = app.components.store.state.tabs.filter { tab ->
            fixturePath(tab.content.url) in setOf(
                "/", "/index.html", "/popup.html", "/video.html")
        }
        assumeTrue("No synthetic fixture tab", eligible.isNotEmpty())
        val trace = GeckoG3RedactedTrace(128)
        val repo = MediaDetectorController.uiRepository.value
        eligible.forEach { tab ->
            val engine = tab.engineState.engineSession
            val gecko = (engine as? GeckoEngineSession)?.geckoSession
            val context = GeckoG3NativeFacts.context(
                tab.contextId, gecko?.settings?.contextId, gecko != null
            )
            val privacy = GeckoG3NativeFacts.privacy(
                tab.content.private, gecko?.settings?.usePrivateMode
            )
            val entry = trace.record(GeckoG3RedactedTrace.Kind.SESSION_BOUND,
                android.os.SystemClock.elapsedRealtime(), engine, tab.id,
                tab.contextId, tab.content.private)
            Log.i("ZavroG3Native", entry.redactedLine() +
                " geckoSettingsContext=" + context.name +
                " geckoSettingsPrivate=" + privacy.name +
                " originAttributes=NOT_OBSERVED")
            if (engine != null && repo != null) {
                val binding = MediaSessionBinding(engine, tab.id, tab.contextId,
                    tab.content.private)
                val synthetic = repo.forTab(binding).mapNotNull { media ->
                    val path = fixturePath(media.url) ?: return@mapNotNull null
                    val label = GeckoG3NativeFacts.mediaLabel(path)
                    if (label == GeckoG3NativeFacts.MediaLabel.OTHER) return@mapNotNull null
                    label to GeckoG3NativeFacts.metadata(
                        label, media.width, media.height, media.durationSeconds)
                }
                synthetic.take(128).forEach { (label, result) ->
                    Log.i("ZavroG3Native", "session=" + entry.sessionLabel +
                        " media=" + label.name + " metadata=" + result.name +
                        " geckoEventOrder=NOT_OBSERVED")
                }
                Log.i("ZavroG3Native", "syntheticCandidateCount=" +
                    synthetic.size.coerceAtMost(128) +
                    " extensionEpoch=NOT_OBSERVED documentDecision=NOT_OBSERVED")
            }
        }
        val id = args.getString("gv_g3_deleted_profile_id")
        if (!id.isNullOrBlank()) {
            assumeTrue(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false))
            val tombstone = app.getSharedPreferences("profile_manager", Context.MODE_PRIVATE)
                .getStringSet("deleted_profile_ids", emptySet())?.contains(id) == true
            val registered = ProfileManager.getInstance(app).getAllProfiles().any { it.id == id }
            Log.i("ZavroG3Native", "syntheticTombstone=" + tombstone +
                " stillRegistered=" + registered + " durableGeckoErasure=NOT_OBSERVED")
        }
        assertTrue(trace.entries().isNotEmpty())
    }
}
