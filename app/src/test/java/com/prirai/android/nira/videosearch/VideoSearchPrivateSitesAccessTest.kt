package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/**
 * Stage 10B: temporary UI/data authorization without Room, filesystem or
 * Gecko network. Nothing here claims private search submission works yet.
 */
class VideoSearchPrivateSitesAccessTest {
    private class Fixture {
        val store = VideoSearchPrivateStore()
        var next = 0
        val registry = VideoSearchPrivateSessionRegistry(store) { "private-${++next}" }
        val access = VideoSearchPrivateSitesAccess(registry)
        val tab = VideoSearchPrivateTab("tab", "private", true)
        init { registry.reconcile(listOf(tab), "default") }
    }

    @Test fun privateUrlIsSavedInRamOnlyAndAutomaticallySelected() {
        val f = Fixture()
        assertEquals(true, f.access.addSite(f.tab, "default", "example.org/search?q=a"))
        val snapshot = f.access.snapshot(f.tab, "default")!!
        assertEquals(1, snapshot.sites.size)
        assertEquals("https://example.org/search?q=a", snapshot.sites.single().siteUrl)
        assertEquals(setOf(snapshot.sites.single().id), snapshot.selectedIds)
        assertTrue(snapshot.sites.single().scope is VideoSearchScope.Private)
    }

    @Test fun urlDuplicateDoesNotProduceSecondEntry() {
        val f = Fixture()
        assertEquals(true, f.access.addSite(f.tab, "default", "https://example.org"))
        assertEquals(false, f.access.addSite(f.tab, "default", "https://example.org"))
        assertEquals(1, f.access.snapshot(f.tab, "default")!!.sites.size)
    }

    @Test fun differentExactQueriesRemainDistinctSites() {
        val f = Fixture()
        assertEquals(true, f.access.addSite(f.tab, "default", "https://example.org/?q=1"))
        assertEquals(true, f.access.addSite(f.tab, "default", "https://example.org/?q=2"))
        assertEquals(2, f.access.snapshot(f.tab, "default")!!.sites.size)
    }

    @Test fun unsafeUrlIsRejectedBeforeRamWrite() {
        val f = Fixture()
        try {
            f.access.addSite(f.tab, "default", "javascript:alert(1)")
            fail("Bad URL accepted")
        } catch (_: IllegalArgumentException) {}
        assertTrue(f.access.snapshot(f.tab, "default")!!.sites.isEmpty())
    }

    @Test fun privateSitesRejectCredentialedUrl() {
        val f = Fixture()
        try {
            f.access.addSite(f.tab, "default", "https://user:pass@example.org")
            fail("Unsafe credentials persisted in RAM")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun selectionChangesInOnlyOneLifetime() {
        val f = Fixture()
        f.access.addSite(f.tab, "default", "example.org")
        val site = f.access.snapshot(f.tab, "default")!!.sites.single()
        assertEquals(true, f.access.setSelected(f.tab, "default", site.id, false))
        assertTrue(f.access.snapshot(f.tab, "default")!!.selectedIds.isEmpty())
        assertEquals(true, f.access.setSelected(f.tab, "default", site.id, true))
        assertEquals(setOf(site.id), f.access.snapshot(f.tab, "default")!!.selectedIds)
    }

    @Test fun selectingStaleSiteIdIsDenied() {
        val f = Fixture()
        assertEquals(false, f.access.setSelected(f.tab, "default", "unknown", true))
        assertEquals(false, f.access.removeSite(f.tab, "default", "unknown"))
    }

    @Test fun selectAllAndClearAllNeverReadNormalDatabase() {
        val f = Fixture()
        f.access.addSite(f.tab, "default", "one.example.org")
        f.access.addSite(f.tab, "default", "two.example.org")
        assertEquals(true, f.access.selectAll(f.tab, "default", false))
        assertTrue(f.access.snapshot(f.tab, "default")!!.selectedIds.isEmpty())
        assertEquals(true, f.access.selectAll(f.tab, "default", true))
        assertEquals(2, f.access.snapshot(f.tab, "default")!!.selectedIds.size)
    }

    @Test fun deletionAlsoRemovesItsSelection() {
        val f = Fixture()
        f.access.addSite(f.tab, "default", "one.example.org")
        f.access.addSite(f.tab, "default", "two.example.org")
        val first = f.access.snapshot(f.tab, "default")!!.sites.first()
        assertEquals(true, f.access.removeSite(f.tab, "default", first.id))
        val after = f.access.snapshot(f.tab, "default")!!
        assertEquals(1, after.sites.size)
        assertFalse(first.id in after.selectedIds)
    }

    @Test fun onlyTheOriginalProfileCanReadOrModifyPrivateSites() {
        val f = Fixture()
        assertNull(f.access.snapshot(f.tab, "work"))
        assertNull(f.access.addSite(f.tab, "work", "other.example.org"))
        assertNull(f.access.selectAll(f.tab, "work", true))
        assertTrue(f.access.snapshot(f.tab, "default")!!.sites.isEmpty())
    }

    @Test fun normalTabCannotReadOrModifyPrivateSites() {
        val f = Fixture()
        val normal = VideoSearchPrivateTab("tab", null, false)
        assertNull(f.access.snapshot(normal, "default"))
        assertNull(f.access.addSite(normal, "default", "example.org"))
    }

    @Test fun forgedTabCannotModifyPrivateRam() {
        val f = Fixture()
        val forged = VideoSearchPrivateTab("foreign", "private", true)
        assertNull(f.access.addSite(forged, "default", "example.org"))
        assertNull(f.access.snapshot(forged, "default"))
    }

    @Test fun afterLastPrivateTabClosesNothingCanBeReadOrWritten() {
        val f = Fixture()
        f.access.addSite(f.tab, "default", "example.org")
        f.registry.reconcile(emptyList(), "default")
        assertNull(f.access.snapshot(f.tab, "default"))
        assertNull(f.access.addSite(f.tab, "default", "example.org"))
    }

    @Test fun closingSessionDiscardsPrivateListAndCreatesNewEmptyList() {
        val f = Fixture()
        f.access.addSite(f.tab, "default", "example.org")
        f.registry.reconcile(emptyList(), "default")
        f.registry.reconcile(listOf(f.tab), "default")
        assertTrue(f.access.snapshot(f.tab, "default")!!.sites.isEmpty())
    }

    @Test fun invalidOwnerCannotBeUsedEvenIfPreviouslyAuthorized() {
        val f = Fixture()
        f.access.addSite(f.tab, "default", "example.org")
        f.registry.reconcile(listOf(f.tab), "work")
        assertNull(f.access.snapshot(f.tab, "work"))
        assertEquals(1, f.access.snapshot(f.tab, "default")!!.sites.size)
    }

    @Test fun currentlyRegisteredAndSelectedSitesArePurelyEphemeral() {
        val f = Fixture()
        f.access.addSite(f.tab, "default", "example.org")
        val owner = f.registry.ownerForTab(f.tab, "default")!!
        assertNull(owner.persistentProfileId)
        assertEquals("private", owner.contextId)
        assertEquals(1, f.store.sites(owner).size)
    }
}
