package com.prirai.android.nira.browser.toolbar

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Application
import android.view.View
import androidx.core.view.isVisible
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Android ValueAnimator cancel/end callbacks with attached Robolectric Views. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class ToolbarSwipeAnimationOwnerTest {
    private class Fixture {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val content = View(activity)
        val preview = View(activity)
        val parent = FrameLayout(activity)
        val owner: ToolbarSwipeAnimationOwner

        init {
            parent.addView(content)
            parent.addView(preview)
            activity.setContentView(parent)
            owner = ToolbarSwipeAnimationOwner(content, preview)
        }

        fun animation() = ValueAnimator.ofFloat(0f, 100f).apply { duration = 1000L }
    }

    @Test fun newGestureInvalidatesAlreadyRunningAnimatorBeforeCancelEnd() {
        val f = Fixture()
        var oldCommits = 0
        val previous = f.owner.beginGesture()
        val first = f.animation()
        f.owner.play(previous, first) { oldCommits++ }
        val current = f.owner.beginGesture()
        first.end() // Old end callback cannot select a tab after cancellation.
        assertEquals(0, oldCommits)
        assertFalse(f.owner.isCurrent(previous))
        assertTrue(f.owner.isCurrent(current))
        assertEquals(0f, f.content.translationX)
        assertFalse(f.preview.isVisible)
        f.activity.finish()
    }

    @Test fun latestValueAnimatorCommitsOnceButCanceledReverseDoesNot() {
        val f = Fixture()
        var selection = 0
        val token = f.owner.beginGesture()
        val success = f.animation()
        f.owner.play(token, success) { selection++ }
        success.end()
        assertEquals(1, selection)
        val cancel = f.animation()
        f.owner.play(token, cancel) { selection++ }
        f.owner.beginGesture()
        cancel.end()
        assertEquals(1, selection)
        f.activity.finish()
    }

    @Test fun replacingSettlementInSameGenerationDiscardsFirstCallback() {
        val f = Fixture()
        val token = f.owner.beginGesture()
        var stale = 0
        var latest = 0
        val first = f.animation()
        f.owner.play(token, first) { stale++ }
        val second = f.animation()
        f.owner.play(token, second) { latest++ }
        first.end()
        second.end()
        assertEquals(0, stale)
        assertEquals(1, latest)
        f.activity.finish()
    }

    @Test fun detachInvalidatesGenerationAndRestoresTranslations() {
        val f = Fixture()
        val token = f.owner.beginGesture()
        val first = f.animation()
        var commits = 0
        f.owner.play(token, first) { commits++ }
        f.content.translationX = 56f
        f.parent.removeView(f.preview)
        first.end()
        assertFalse(f.owner.isCurrent(token))
        assertEquals(0, commits)
        assertEquals(0f, f.content.translationX)
        f.activity.finish()
    }
}
