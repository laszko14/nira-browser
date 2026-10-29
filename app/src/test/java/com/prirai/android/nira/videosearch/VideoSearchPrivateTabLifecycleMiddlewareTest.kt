package com.prirai.android.nira.videosearch

import android.app.Application
import com.prirai.android.nira.browser.profile.ProfileManager
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** LC-01: exercise real BrowserStore actions without Activity or timers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchPrivateTabLifecycleMiddlewareTest {
    private class F {
        val ram = VideoSearchPrivateStore()
        var minted = 0
        var profile = "default"
        val registry = VideoSearchPrivateSessionRegistry(ram) { "epoch-${++minted}" }
        val store = BrowserStore(
            initialState = BrowserState(),
            middleware = listOf(VideoSearchPrivateTabLifecycleMiddleware({ profile }, registry)),
        )
        val sites = VideoSearchPrivateSitesAccess(registry)
        fun descriptor(id: String) = VideoSearchPrivateTab(id, "private", true)
        fun add(id: String, private: Boolean = true, context: String? =
            if (private) "private" else null) {
            store.dispatch(TabListAction.AddTabAction(
                TabSessionState(id = id, contextId = context,
                    content = ContentState(url = "https://example.org/", private = private)),
                true,
            ))
        }
        fun remove(id: String) {
            store.dispatch(TabListAction.RemoveTabAction(
                tabId = id, selectParentIfExists = false,
            ))
        }
    }

    @Test fun closeAndImmediateReopenDoesNotInheritRamWithinOld300MsGrace() {
        val f = F()
        f.add("a")
        val old = f.registry.ownerForTab(f.descriptor("a"), "default")!!
        assertEquals(true, f.sites.addSite(f.descriptor("a"), "default", "one.example.org"))
        f.remove("a")
        assertNull(f.registry.activeScope())
        f.add("b") // no timer: the two dispatches are consecutive
        val fresh = f.registry.ownerForTab(f.descriptor("b"), "default")!!
        assertNotEquals(old, fresh)
        assertEquals(2, f.minted)
        assertTrue(f.sites.snapshot(f.descriptor("b"), "default")!!.sites.isEmpty())
        try { f.ram.settings(old); fail("Retired RAM readable") }
        catch (_: IllegalStateException) {}
    }

    @Test fun sameTabIdAfterLastCloseIsAlsoANewEpoch() {
        val f = F()
        f.add("same")
        val old = f.registry.ownerForTab(f.descriptor("same"), "default")!!
        f.remove("same")
        f.add("same")
        assertNotEquals(old, f.registry.ownerForTab(f.descriptor("same"), "default"))
        try { f.ram.start(old); fail("Stale worker resurrected epoch") }
        catch (_: IllegalStateException) {}
    }

    @Test fun lastPrivateTabClosureRevokesAccessWithoutReopen() {
        val f = F()
        f.add("p")
        assertEquals(true, f.sites.addSite(f.descriptor("p"), "default", "one.example.org"))
        f.remove("p")
        assertNull(f.registry.ownerForTab(f.descriptor("p"), "default"))
        assertNull(f.sites.snapshot(f.descriptor("p"), "default"))
    }

    @Test fun livingPrivateTabSurvivesOtherClosureAndActivityRecreation() {
        val f = F()
        f.add("p1")
        f.add("p2")
        val before = f.registry.ownerForTab(f.descriptor("p2"), "default")!!
        assertEquals(true, f.sites.addSite(f.descriptor("p2"), "default", "one.example.org"))
        f.remove("p1")
        // BrowserStore/registry are process-owned; no Activity is created in this test.
        f.add("ordinary", private = false)
        assertEquals(before, f.registry.ownerForTab(f.descriptor("p2"), "default"))
        assertEquals(1, f.sites.snapshot(f.descriptor("p2"), "default")!!.sites.size)
    }

    @Test fun switchedNormalProfileCannotReadOrClaimOldLifetime() {
        val f = F()
        f.add("p")
        val owner = f.registry.ownerForTab(f.descriptor("p"), "default")!!
        f.profile = "work"
        f.add("regular", private = false)
        assertNull(f.sites.snapshot(f.descriptor("p"), "work"))
        assertEquals(owner, f.registry.activeScope())
        f.profile = "default"
        assertEquals(owner, f.registry.ownerForTab(f.descriptor("p"), "default"))
    }

    @Test fun explicitPrivateToNormalMigrationRetiresTheOldOwner() {
        val f = F()
        f.add("p")
        val old = f.registry.ownerForTab(f.descriptor("p"), "default")!!
        val manager = ProfileManager(RuntimeEnvironment.getApplication(), { f.store }, {})
        assertTrue(manager.migrateTabToProfile("p", "default"))
        assertNull(f.registry.activeScope())
        assertFalse(f.store.state.tabs.single().content.private)
        try { f.ram.settings(old); fail("Migrated tab still owns private RAM") }
        catch (_: IllegalStateException) {}
    }

    @Test fun migrationOfOneOfTwoPrivateTabsPreservesSurvivorEpoch() {
        val f = F()
        f.add("p1")
        f.add("p2")
        val owner = f.registry.ownerForTab(f.descriptor("p2"), "default")!!
        val manager = ProfileManager(RuntimeEnvironment.getApplication(), { f.store }, {})
        assertTrue(manager.migrateTabToProfile("p1", "default"))
        assertEquals(owner, f.registry.ownerForTab(f.descriptor("p2"), "default"))
        assertNull(f.registry.ownerForTab(f.descriptor("p1"), "default"))
    }

    @Test fun invalidPrivateGeckoContextCannotKeepOldRamAlive() {
        val f = F()
        f.add("forged", private = true, context = "profile_foreign")
        assertNull(f.registry.activeScope())
        f.add("valid")
        val old = f.registry.ownerForTab(f.descriptor("valid"), "default")!!
        f.remove("valid")
        assertNull(f.registry.activeScope())
        try { f.ram.settings(old); fail("Malformed tab kept RAM active") }
        catch (_: IllegalStateException) {}
    }
}
