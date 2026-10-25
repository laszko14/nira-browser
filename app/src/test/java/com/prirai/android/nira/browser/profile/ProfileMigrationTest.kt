package com.prirai.android.nira.browser.profile

import android.app.Application
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import mozilla.components.feature.session.middleware.undo.UndoMiddleware
import mozilla.components.browser.state.action.*
import mozilla.components.browser.state.state.*
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class ProfileMigrationTest {
    @Test fun crossingPrivacyBoundaryDoesNotRelinkTheOldGeckoSession() = runBlocking {
        val session = mock(EngineSession::class.java)
        val tab = TabSessionState(id = "a", content = ContentState(url = "https://example.org"),
            engineState = EngineState(engineSession = session))
        val actions = java.util.concurrent.CopyOnWriteArrayList<BrowserAction>()
        val store = BrowserStore(initialState = BrowserState(tabs = listOf(tab), selectedTabId = tab.id),
            middleware = listOf({ _, next, action -> actions.add(action); next(action) }))
        val profiles = ProfileManager(RuntimeEnvironment.getApplication(), { store }, {})
        assertTrue(profiles.migrateTabToProfile("a", "private"))
        store.dispatch(TabListAction.SelectTabAction("a"))
        val moved = store.state.tabs.single()
        assertTrue(moved.content.private)
        assertEquals("private", moved.contextId)
        assertNull("A fresh engine must be created in the destination partition", moved.engineState.engineSession)
        assertNull(moved.engineState.engineSessionState)
        assertFalse(actions.any { it is EngineAction.LinkEngineSessionAction })
        assertFalse(actions.any { it is EngineAction.UnlinkEngineSessionAction })
    }
    @Test fun explicitMoveToDefaultIsNotReassignedToTheCurrentlyActiveNamedProfile() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("profile_manager", 0).edit()
            .putString("profiles", "[{\"id\":\"B\",\"name\":\"B\",\"isDefault\":false}]")
            .putString("active_profile_id", "B").apply()
        val state = ProfileManager(context)
        val middleware = ProfileMiddleware(state)
        val tab = TabSessionState(id = "a", contextId = "profile_A", content = ContentState(url = "https://example.org"))
        val store = BrowserStore(initialState = BrowserState(tabs = listOf(tab)), middleware = listOf(middleware))
        val profiles = ProfileManager(context, { store }, middleware::markAsGuestTab)
        assertTrue(profiles.migrateTabToProfile("a", "default"))
        store.dispatch(TabListAction.SelectTabAction("a"))
        assertNull(store.state.tabs.single().contextId)
        assertFalse(store.state.tabs.single().content.private)
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun relocationDoesNotReplaceTheExistingUndoHistory() = runTest {
        val tab = TabSessionState(id = "a", content = ContentState(url = "https://example.org"))
        val undo = ProfileMigrationGuard.undoMiddleware(UndoMiddleware(5000,backgroundScope,backgroundScope))
        val store = BrowserStore(initialState = BrowserState(tabs = listOf(tab), undoHistory = UndoHistoryState(tag="previous-close")), middleware=listOf(undo))
        val profiles = ProfileManager(RuntimeEnvironment.getApplication(), { store }, {})
        assertTrue(profiles.migrateTabToProfile("a","private"))
        runCurrent()
        assertEquals("previous-close",store.state.undoHistory.tag)
        assertTrue(store.state.undoHistory.tabs.isEmpty())
    }

}
