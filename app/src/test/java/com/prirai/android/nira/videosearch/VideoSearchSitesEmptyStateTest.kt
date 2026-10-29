package com.prirai.android.nira.videosearch

import android.app.Application
import android.view.View
import android.widget.TextView
import com.prirai.android.nira.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** P16 UI-05: only an observed zero-row list may display the welcome hint. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VideoSearchSitesEmptyStateTest {
    @Test fun zeroRowsAfterSuccessfulLoadShowInstructions() {
        val state = VideoSearchSitesListState.loaded(0)
        assertEquals(VideoSearchSitesListState.Empty, state)
        assertTrue(state.message!!.contains("Dodaj"))
        assertTrue(state.message!!.contains("Rozpoznaj"))
        assertTrue(state.message!!.contains("Wskaż"))
    }

    @Test fun nonemptyListDoesNotShowEmptyState() {
        assertEquals(VideoSearchSitesListState.HasSites, VideoSearchSitesListState.loaded(1))
        assertEquals(VideoSearchSitesListState.HasSites, VideoSearchSitesListState.loaded(24))
        assertNull(VideoSearchSitesListState.loaded(24).message)
    }

    @Test fun loadingAndErrorAreNeverMisrepresentedAsEmpty() {
        assertNotEquals(VideoSearchSitesListState.Empty, VideoSearchSitesListState.Loading)
        assertNotEquals(VideoSearchSitesListState.Empty, VideoSearchSitesListState.Error)
        assertTrue(VideoSearchSitesListState.Loading.message!!.contains("Wczytywanie"))
        assertTrue(VideoSearchSitesListState.Error.message!!.contains("Nie udało"))
        assertFalse(VideoSearchSitesListState.Error.message!!.contains("Dodaj"))
    }

    @Test fun invalidLoadedCountCannotShowAFalseEmptyState() {
        try {
            VideoSearchSitesListState.loaded(-1)
            fail("Negative row count is not a valid observed empty list")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun actualActivityStartsWithoutAFalseZeroSitesHint() {
        val c = Robolectric.buildActivity(MyVideoSitesActivity::class.java)
        c.get().setTheme(R.style.AppThemeSettings)
        c.create().start().visible()
        try {
            val empty = ReflectionHelpers.getField<TextView>(c.get(), "normalEmptyState")
            assertEquals(View.GONE, empty.visibility)
            assertFalse(empty.text.contains("Nie masz"))
        } finally { c.stop().destroy() }
    }

    @Test fun actualActivityRendersFourDistinctStatesAndClearsEmptyOnAdd() {
        val c = Robolectric.buildActivity(MyVideoSitesActivity::class.java)
        c.get().setTheme(R.style.AppThemeSettings)
        c.create().start().visible()
        try {
            val activity = c.get()
            val field = ReflectionHelpers.getField<TextView>(activity, "normalEmptyState")
            val render = activity.javaClass.getDeclaredMethod(
                "showNormalSitesState", VideoSearchSitesListState::class.java
            ).apply { isAccessible = true }
            render.invoke(activity, VideoSearchSitesListState.Loading)
            assertEquals(View.VISIBLE, field.visibility)
            assertTrue(field.text.contains("Wczytywanie"))
            assertFalse(field.text.contains("Nie masz"))
            render.invoke(activity, VideoSearchSitesListState.loaded(0))
            assertTrue(field.text.contains("Dodaj"))
            assertTrue(field.text.contains("Rozpoznaj"))
            render.invoke(activity, VideoSearchSitesListState.loaded(1))
            assertEquals(View.GONE, field.visibility)
            render.invoke(activity, VideoSearchSitesListState.Error)
            assertEquals(View.VISIBLE, field.visibility)
            assertTrue(field.text.contains("Nie udało"))
            assertFalse(field.text.contains("Nie masz"))
        } finally { c.stop().destroy() }
    }
}
