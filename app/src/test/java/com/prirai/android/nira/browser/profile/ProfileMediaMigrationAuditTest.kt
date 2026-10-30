package com.prirai.android.nira.browser.profile

import android.app.Application
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.MediaSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.browser.state.action.TabListAction
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,manifest=Config.NONE)
class ProfileMediaMigrationAuditTest {
    @Test fun movingPlayingTabDropsOldControllerInDestination() {
        val controller=mock(MediaSession.Controller::class.java)
        val tab=TabSessionState(
            id="playing", contextId="profile_A",
            content=ContentState(url="https://example.org"),
            mediaSessionState=MediaSessionState(controller=controller,playbackState=MediaSession.PlaybackState.PLAYING))
        val store=BrowserStore(initialState=BrowserState(tabs=listOf(tab),selectedTabId=tab.id))
        val manager=ProfileManager(RuntimeEnvironment.getApplication(), {store}, {})
        assertTrue(manager.migrateTabToProfile("playing","private"))
        val moved=store.state.tabs.single()
        assertEquals("private",moved.contextId)
        assertTrue(moved.content.private)
        assertNull(moved.engineState.engineSession)
        assertNull("Old PLAYING/controller must not survive destination engine",moved.mediaSessionState)
    }
}
