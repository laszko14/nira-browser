package com.prirai.android.nira.videosearch

import android.app.Application
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P3 LC-02: selected worker is permanently adopted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchNormalWorkerLeaseTest {
    private val owner = VideoSearchScope.Normal("default")
    private fun lease() = VideoSearchNormalWorkerLease("worker", owner)
    private fun tab(id: String = "worker", context: String? = owner.contextId,
                    private: Boolean = false) = VideoSearchNormalWorkerTab(id, context, private)

    @Test fun boundUnselectedWorkerCanBeReclaimed() {
        val l = lease()
        val s = Any()
        assertTrue(l.bindSession(s, "user"))
        assertTrue(l.mayReclaim(tab(), "user", owner, s, true))
    }

    @Test fun selectingOnceIsPermanentAdoption() {
        val l = lease()
        val s = Any()
        assertTrue(l.bindSession(s, "user"))
        l.observeSelection("worker")
        assertFalse(l.canUse("user"))
        assertFalse(l.bindSession(s, "user"))
        assertFalse(l.mayReclaim(tab(), "user", owner, s, true))
    }

    @Test fun selectionAtRegistrationRejectsWorker() {
        val registry = VideoSearchNormalWorkerRegistry()
        val l = lease()
        registry.register(l, "worker")
        assertFalse(l.bindSession(Any(), "user"))
        assertFalse(l.canUse("user"))
        registry.release(l)
        assertNull(registry.forTab("worker"))
    }

    @Test fun engineReplacementBlocksFurtherUseAndRemoval() {
        val l = lease()
        val s = Any()
        assertTrue(l.bindSession(s, "user"))
        assertFalse(l.bindSession(Any(), "user"))
        assertFalse(l.acceptsSession(null))
        assertFalse(l.mayReclaim(tab(), "user", owner, Any(), true))
    }

    @Test fun strictTabProfileContextAndOriginOwnership() {
        val l = lease()
        val s = Any()
        assertTrue(l.bindSession(s, "user"))
        assertFalse(l.mayReclaim(null, "user", owner, s, true))
        assertFalse(l.mayReclaim(tab("other"), "user", owner, s, true))
        assertFalse(l.mayReclaim(tab(private = true), "user", owner, s, true))
        assertFalse(l.mayReclaim(tab(context = "private"), "user", owner, s, true))
        assertFalse(l.mayReclaim(tab(), "user", VideoSearchScope.Normal("work"), s, true))
        assertFalse(l.mayReclaim(tab(), "user", owner, s, false))
    }

    @Test fun unboundSessionCanOnlyReclaimStillNullSession() {
        val l = lease()
        assertTrue(l.mayReclaim(tab(), "user", owner, null, true))
        assertFalse(l.mayReclaim(tab(), "user", owner, Any(), true))
        val s = Any()
        assertTrue(l.bindSession(s, "user"))
        assertFalse(l.mayReclaim(tab(), "user", owner, null, true))
    }

    @Test fun concurrentWorkersHaveIndependentSelectionState() {
        val registry = VideoSearchNormalWorkerRegistry()
        val a = lease()
        val b = VideoSearchNormalWorkerLease("second", owner)
        registry.register(a, "user")
        registry.register(b, "user")
        registry.observeSelection("worker")
        registry.observeSelection("user")
        assertFalse(a.canUse("user"))
        assertTrue(b.canUse("user"))
        registry.release(a)
        assertNull(registry.forTab("worker"))
        assertSame(b, registry.forTab("second"))
        registry.release(b)
    }

    @Test fun duplicateWorkerIdCannotOverwriteLease() {
        val registry = VideoSearchNormalWorkerRegistry()
        val first = lease()
        registry.register(first, "user")
        try { registry.register(lease(), "user"); fail("Duplicate lease accepted") }
        catch (_: IllegalStateException) {}
        assertSame(first, registry.forTab("worker"))
        registry.release(first)
    }

    @Test fun realBrowserStoreCapturesRapidSelectAndReturn() {
        val registry = VideoSearchNormalWorkerRegistry()
        val store = BrowserStore(
            initialState = BrowserState(),
            middleware = listOf(VideoSearchNormalWorkerSelectionMiddleware(registry)),
        )
        store.dispatch(TabListAction.AddTabAction(
            TabSessionState(id = "user", content = ContentState(url = "https://example.org")),
            true,
        ))
        store.dispatch(TabListAction.AddTabAction(
            TabSessionState(id = "worker", content = ContentState(url = "https://example.org")),
            false,
        ))
        val l = lease()
        registry.register(l, store.state.selectedTabId)
        assertTrue(l.canUse(store.state.selectedTabId))
        store.dispatch(TabListAction.SelectTabAction("worker"))
        store.dispatch(TabListAction.SelectTabAction("user"))
        assertEquals("user", store.state.selectedTabId)
        assertFalse(l.canUse("user"))
        assertFalse(l.mayReclaim(tab(), "user", owner, null, true))
    }

    @Test fun switchingUnrelatedUserTabsDoesNotAdoptWorker() {
        val registry = VideoSearchNormalWorkerRegistry()
        val store = BrowserStore(
            initialState = BrowserState(),
            middleware = listOf(VideoSearchNormalWorkerSelectionMiddleware(registry)),
        )
        store.dispatch(TabListAction.AddTabAction(
            TabSessionState(id = "user", content = ContentState(url = "https://example.org")),
            true,
        ))
        store.dispatch(TabListAction.AddTabAction(
            TabSessionState(id = "other", content = ContentState(url = "https://example.org")),
            false,
        ))
        val l = lease()
        registry.register(l, store.state.selectedTabId)
        store.dispatch(TabListAction.SelectTabAction("other"))
        store.dispatch(TabListAction.SelectTabAction("user"))
        assertTrue(l.canUse("user"))
    }
}
