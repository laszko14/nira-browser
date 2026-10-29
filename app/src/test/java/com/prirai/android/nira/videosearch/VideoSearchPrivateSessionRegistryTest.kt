package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** Stage 10A: BrowserStore-based private lifetime and RAM cleanup policy. */
class VideoSearchPrivateSessionRegistryTest {
    private fun privateTab(id: String) =
        VideoSearchPrivateTab(id, "private", true)
    private fun normalTab(id: String) =
        VideoSearchPrivateTab(id, null, false)
    private fun fixture(): Triple<VideoSearchPrivateSessionRegistry, VideoSearchPrivateStore, List<String>> {
        val store = VideoSearchPrivateStore()
        val ids = mutableListOf<String>()
        var sequence = 0
        val registry = VideoSearchPrivateSessionRegistry(store) {
            (++sequence).let { "epoch-$it" }.also(ids::add)
        }
        return Triple(registry, store, ids)
    }

    @Test fun firstPrivateTabStartsFreshRamSession() {
        val (registry, _, ids) = fixture()
        val tab = privateTab("one")
        registry.reconcile(listOf(tab), "default")
        assertEquals("epoch-1", registry.ownerForTab(tab, "default")?.sessionId)
        assertEquals(listOf("epoch-1"), ids)
    }

    @Test fun secondPrivateTabSharesSessionButNotNormalTab() {
        val (registry, _, ids) = fixture()
        registry.reconcile(listOf(privateTab("a")), "default")
        registry.reconcile(listOf(privateTab("a"), privateTab("b"),
            normalTab("ordinary")), "default")
        assertEquals("epoch-1", registry.ownerForTab(privateTab("b"), "default")?.sessionId)
        assertNull(registry.ownerForTab(normalTab("ordinary"), "default"))
        assertEquals(1, ids.size)
    }

    @Test fun switchingToNormalUiWithPrivateTabsDoesNotEndLifetime() {
        val (registry, store, _) = fixture()
        val tab = privateTab("a")
        registry.reconcile(listOf(tab), "default")
        val owner = registry.ownerForTab(tab, "default")!!
        registry.reconcile(listOf(normalTab("other"), tab), "default")
        store.putSettings(VideoSearchSettings(owner))
        assertEquals(owner, store.settings(owner).scope)
        assertEquals(owner, registry.ownerForTab(tab, "default"))
    }

    @Test fun removingOneOfTwoPrivateTabsPreservesRamData() {
        val (registry, store, _) = fixture()
        registry.reconcile(listOf(privateTab("a"), privateTab("b")), "default")
        val owner = registry.ownerForTab(privateTab("a"), "default")!!
        store.putSettings(VideoSearchSettings(owner))
        registry.reconcile(listOf(privateTab("b")), "default")
        assertNotNull(registry.ownerForTab(privateTab("b"), "default"))
        assertEquals(owner, store.settings(owner).scope)
    }

    @Test fun closingLastPrivateTabClearsRamAndRetiresOldSessionId() {
        val (registry, store, _) = fixture()
        registry.reconcile(listOf(privateTab("a")), "default")
        val owner = registry.ownerForTab(privateTab("a"), "default")!!
        store.putSettings(VideoSearchSettings(owner))
        registry.reconcile(listOf(normalTab("ordinary")), "default")
        assertNull(registry.ownerForTab(privateTab("a"), "default"))
        try {
            store.settings(owner)
            fail("Closed private session still readable")
        } catch (_: IllegalStateException) {}
        try {
            store.start(owner)
            fail("Retired private session reused")
        } catch (_: IllegalStateException) {}
    }

    @Test fun reopenedPrivateSessionCannotAccessPreviousEpoch() {
        val (registry, _, ids) = fixture()
        registry.reconcile(listOf(privateTab("a")), "default")
        val first = registry.ownerForTab(privateTab("a"), "default")!!
        registry.reconcile(emptyList(), "default")
        registry.reconcile(listOf(privateTab("b")), "default")
        val next = registry.ownerForTab(privateTab("b"), "default")!!
        assertNotEquals(first, next)
        assertEquals(listOf("epoch-1", "epoch-2"), ids)
    }

    @Test fun foreignProfileCannotClaimExistingPrivateTabs() {
        val (registry, _, _) = fixture()
        val tab = privateTab("a")
        registry.reconcile(listOf(tab), "default")
        registry.reconcile(listOf(tab), "work")
        assertNull(registry.ownerForTab(tab, "work"))
        assertEquals("default", registry.ownerForTab(tab, "default")?.profileId)
    }

    @Test fun newPrivateTabDoesNotRebindExistingSessionToNewProfile() {
        val (registry, _, ids) = fixture()
        registry.reconcile(listOf(privateTab("a")), "default")
        registry.reconcile(listOf(privateTab("a"), privateTab("b")), "work")
        assertNull(registry.ownerForTab(privateTab("b"), "work"))
        assertEquals(1, ids.size)
    }

    @Test fun malformedGeckoContextIsNeverAnAuthorizedPrivateTab() {
        val (registry, _, _) = fixture()
        val forged = VideoSearchPrivateTab("a", "profile_default", true)
        registry.reconcile(listOf(forged), "default")
        assertNull(registry.ownerForTab(forged, "default"))
    }

    @Test fun normalTabWithPrivateContextIsNotAuthorized() {
        val (registry, _, _) = fixture()
        registry.reconcile(listOf(privateTab("a")), "default")
        assertNull(registry.ownerForTab(
            VideoSearchPrivateTab("a", "private", false), "default"))
    }

    @Test fun unrelatedNormalTabsDoNotCreatePrivateSession() {
        val (registry, _, ids) = fixture()
        registry.reconcile(listOf(normalTab("a")), "default")
        assertNull(registry.activeScope())
        assertTrue(ids.isEmpty())
    }

    @Test fun staleTabAfterRemovalCannotAccessActiveEpoch() {
        val (registry, _, _) = fixture()
        registry.reconcile(listOf(privateTab("a"), privateTab("b")), "default")
        registry.reconcile(listOf(privateTab("b")), "default")
        assertNull(registry.ownerForTab(privateTab("a"), "default"))
    }

    @Test fun invalidProfileCannotStartSession() {
        val (registry, _, ids) = fixture()
        registry.reconcile(listOf(privateTab("a")), "private")
        assertNull(registry.activeScope())
        assertTrue(ids.isEmpty())
    }

    @Test fun duplicatePrivateTabIdsCannotInitializeLifetime() {
        val (registry, _, ids) = fixture()
        registry.reconcile(listOf(privateTab("a"), privateTab("a")), "default")
        assertNull(registry.activeScope())
        assertTrue(ids.isEmpty())
    }

    @Test fun processOnlyHostHasNoPersistedSessionId() {
        val registry = VideoSearchPrivateSessionRegistry()
        assertNull(registry.activeScope())
    }
}
