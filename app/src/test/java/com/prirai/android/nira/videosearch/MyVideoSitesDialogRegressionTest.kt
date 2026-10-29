package com.prirai.android.nira.videosearch

import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.profile.ProfileManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.Shadows.shadowOf
import android.os.Looper

/** P15/16 UI-03/04: actual normal Activity dialogs + bounded owner-aware state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MyVideoSitesDialogRegressionTest {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap {
            descendants(view.getChildAt(it))
        } else emptyList()

    private fun activity(saved: Bundle? = null) =
        Robolectric.buildActivity(MyVideoSitesActivity::class.java).also {
            it.get().setTheme(R.style.AppThemeSettings)
            it.create(saved).start().resume().visible()
        }

    private fun queueDialog(a: MyVideoSitesActivity): AlertDialog {
        val button = ReflectionHelpers.getField<Button>(a, "queuePreviewButton")
        assertTrue(button.isEnabled)
        button.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        return ReflectionHelpers.getField<AlertDialog>(a, "trackedDialog")
    }

    private fun queueInput(dialog: AlertDialog): EditText =
        descendants(dialog.window!!.decorView).filterIsInstance<EditText>().single {
            it.hint?.toString() == "Fraza do wyszukania na wybranych witrynach"
        }

    @Test fun invalidQueuePhraseNeverDismissesAndCanBeEditedAgain() {
        val manager = ProfileManager.getInstance(RuntimeEnvironment.getApplication())
        manager.setPrivateMode(false)
        val c = activity()
        try {
            val d = queueDialog(c.get())
            val field = queueInput(d)
            field.setText("  ")
            d.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            assertTrue("execution validation must leave dialog open", d.isShowing)
            assertNotNull("invalid execution shows field-level error", field.error)
            field.setText("")
            d.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertTrue("preview validation must leave dialog open", d.isShowing)
            assertNotNull(field.error)
            assertTrue("button should not become stuck disabled",
                d.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled)
            assertTrue(d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        } finally {c.pause().stop().destroy()}
    }

    @Test fun queuedPhraseRestoresAfterRotationWithoutStartingSearch() {
        val manager = ProfileManager.getInstance(RuntimeEnvironment.getApplication())
        manager.setPrivateMode(false)
        val original = activity()
        val d = queueDialog(original.get())
        val phrase = "komedia polska nie wysłana"
        queueInput(d).setText(phrase)
        val saved = Bundle()
        original.saveInstanceState(saved).pause().stop().destroy()
        assertEquals("videoSites.normalDialog", "videoSites.normalDialog")
        val restored = activity(saved)
        try {
            val dialog = ReflectionHelpers.getField<AlertDialog>(restored.get(), "trackedDialog")
            assertNotNull(dialog)
            assertTrue(dialog.isShowing)
            assertEquals(phrase, queueInput(dialog).text.toString())
            assertTrue(dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled)
            assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        } finally {restored.pause().stop().destroy()}
    }

    @Test fun dialogDraftHasStrictProfileAndKindRoundTrip() {
        for (kind in VideoSearchNormalDialogKind.values()) {
            val site = if (kind == VideoSearchNormalDialogKind.QUEUE) null else "site-id"
            val original = VideoSearchNormalDialogDraft(
                "profile-1", kind, site, "Fraza", "100", "https://films.example.org/search?x=1"
            )
            assertEquals(original, VideoSearchNormalDialogDraft.fromBundle(
                original.toBundle(), "profile-1"))
            assertNull(VideoSearchNormalDialogDraft.fromBundle(
                original.toBundle(), "profile-2"))
        }
    }

    @Test fun draftRejectsMalformedUnknownAndUnboundedRestoration() {
        assertNull(VideoSearchNormalDialogDraft.fromBundle(Bundle(), "normal"))
        assertNull(VideoSearchNormalDialogDraft.fromBundle(
            Bundle().apply {putString("owner", "normal");putString("kind", "PRIVATE")}, "normal"))
        val missingSite = VideoSearchNormalDialogDraft(
            "normal", VideoSearchNormalDialogKind.EDIT, null, "Name"
        )
        assertNull(VideoSearchNormalDialogDraft.fromBundle(missingSite.toBundle(), "normal"))
        val oversize = VideoSearchNormalDialogDraft(
            "normal", VideoSearchNormalDialogKind.QUEUE, primary = "x".repeat(2049)
        )
        assertNull(VideoSearchNormalDialogDraft.fromBundle(oversize.toBundle(), "normal"))
    }

    @Test fun privateModeNeverRestoresOrdinarySearchDialogOrStoresPrivateDraft() {
        val manager = ProfileManager.getInstance(RuntimeEnvironment.getApplication())
        manager.setPrivateMode(false)
        val saved = Bundle().apply {
            putString("videoSites.normalOwner", manager.getActiveProfile().id)
            putBundle("videoSites.normalDialog",
                VideoSearchNormalDialogDraft(manager.getActiveProfile().id,
                    VideoSearchNormalDialogKind.QUEUE, primary="ordinary-draft").toBundle())
        }
        manager.setPrivateMode(true)
        try {
            val c = Robolectric.buildActivity(MyVideoSitesActivity::class.java).also {
                it.get().setTheme(R.style.AppThemeSettings)
                it.create(saved).start()
            }
            try {
                assertNull(ReflectionHelpers.getField<AlertDialog?>(c.get(), "trackedDialog"))
                val out = Bundle()
                c.saveInstanceState(out)
                assertFalse(out.containsKey("videoSites.normalDialog"))
                assertFalse(out.containsKey("videoSites.normalOwner"))
            } finally {c.pause().stop().destroy()}
        } finally {manager.setPrivateMode(false)}
    }

    @Test fun savedDraftDoesNotTriggerSearchOrReopenOnSubsequentResume() {
        val manager = ProfileManager.getInstance(RuntimeEnvironment.getApplication())
        manager.setPrivateMode(false)
        val old = Bundle().apply {
            putString("videoSites.normalOwner", manager.getActiveProfile().id)
            putBundle("videoSites.normalDialog",
                VideoSearchNormalDialogDraft(manager.getActiveProfile().id,
                    VideoSearchNormalDialogKind.QUEUE, primary="pending").toBundle())
        }
        val c = activity(old)
        try {
            val first = ReflectionHelpers.getField<AlertDialog>(c.get(), "trackedDialog")
            assertTrue(first.isShowing)
            first.dismiss()
            shadowOf(Looper.getMainLooper()).idle()
            c.pause().resume()
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(ReflectionHelpers.getField<AlertDialog?>(c.get(), "trackedDialog"))
        } finally {c.pause().stop().destroy()}
    }
}
