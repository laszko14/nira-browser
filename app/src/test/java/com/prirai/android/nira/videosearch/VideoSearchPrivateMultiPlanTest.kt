package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** Stage 10E: pure private multi-site ownership and planning contracts. */
class VideoSearchPrivateMultiPlanTest {
    private val owner = VideoSearchScope.Private("default", "epoch-1")

    private fun site(id: String, position: Int = 0, enabled: Boolean = true,
                     scope: VideoSearchScope = owner) = VideoSearchSite(
        id = id, scope = scope, siteUrl = "https://$id.example.org/",
        displayName = id, position = position, enabled = enabled,
        createdAtMs = position.toLong() + 1,
    )

    @Test fun selectedSitesKeepSavedOrdering() {
        val sites = listOf(site("third", 3), site("first", 1), site("second", 2))
        val prefs = VideoSearchSettings(owner, selectedSiteIds = sites.map { it.id }.toSet())
        val plan = VideoSearchPrivateMultiPlan.create(owner, "film", prefs, sites)
        assertEquals(listOf("first", "second", "third"), plan.sites.map { it.id })
        assertEquals(owner, plan.owner)
    }

    @Test fun unselectedAndDisabledSitesNeverEnterWorkQueue() {
        val sites = listOf(site("enabled"), site("off", 1, false), site("notselected", 2))
        val settings = VideoSearchSettings(owner, selectedSiteIds = setOf("enabled", "off"))
        val plan = VideoSearchPrivateMultiPlan.create(owner, "q", settings, sites)
        assertEquals(listOf("enabled"), plan.sites.map { it.id })
    }

    @Test fun independentParallelismDoesNotDiscardSelection() {
        val sites = (0 until 7).map { site("site$it", it) }
        val settings = VideoSearchSettings(owner, sites.map { it.id }.toSet(),
            parallelism = VideoSearchParallelism.ALL)
        val plan = VideoSearchPrivateMultiPlan.create(owner, "q", settings, sites)
        assertEquals(7, plan.sites.size)
    }

    @Test fun planNeverPrintsPrivateQueryOrUrls() {
        val plan = VideoSearchPrivateMultiPlan.create(owner,
            "private search phrase",
            VideoSearchSettings(owner, setOf("site")),
            listOf(site("site")))
        assertFalse(plan.toString().contains("private search phrase"))
        assertFalse(plan.toString().contains("example.org"))
    }

    @Test fun scopeFromDifferentPrivateEpochIsRejected() {
        val other = VideoSearchScope.Private("default", "epoch-2")
        try {
            VideoSearchPrivateMultiPlan.create(owner, "q",
                VideoSearchSettings(owner, setOf("site")),
                listOf(site("site", scope = other)))
            fail("Mixed private lifetimes accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun normalProfileSiteIsRejected() {
        try {
            VideoSearchPrivateMultiPlan.create(owner, "q",
                VideoSearchSettings(owner, setOf("site")),
                listOf(site("site", scope = VideoSearchScope.Normal("default"))))
            fail("Normal Room site mixed into private queue")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun settingsFromAnotherProfileCannotPlan() {
        try {
            VideoSearchPrivateMultiPlan.create(owner, "q",
                VideoSearchSettings(VideoSearchScope.Private("work", "epoch-1"),
                    setOf("site")), listOf(site("site")))
            fail("Foreign settings accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun duplicatedSiteIdentityIsRejected() {
        try {
            VideoSearchPrivateMultiPlan.create(owner, "q",
                VideoSearchSettings(owner, setOf("site")),
                listOf(site("site"), site("site", 1)))
            fail("Duplicated identity accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun noEnabledSelectedSitesRejectsLaunch() {
        try {
            VideoSearchPrivateMultiPlan.create(owner, "q",
                VideoSearchSettings(owner, setOf("disabled")),
                listOf(site("disabled", enabled = false)))
            fail("Empty queue accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun controlCharactersInQueryRejectLaunch() {
        try {
            VideoSearchPrivateMultiPlan.create(owner, "q\nsecret",
                VideoSearchSettings(owner, setOf("site")),
                listOf(site("site")))
            fail("Control characters accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun moreThanMaximumPrivateSitesIsRejected() {
        val sites = (0..200).map { site("site$it", it) }
        try {
            VideoSearchPrivateMultiPlan.create(owner, "q",
                VideoSearchSettings(owner, sites.map { it.id }.toSet()),
                sites)
            fail("Unbounded site count accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun exactMaximumTwoHundredIsSupported() {
        val sites = (0 until 200).map { site("site$it", it) }
        val plan = VideoSearchPrivateMultiPlan.create(owner, "q",
            VideoSearchSettings(owner, sites.map { it.id }.toSet()), sites)
        assertEquals(200, plan.sites.size)
    }

    @Test fun prepareMultiRequiresCurrentPrivateOwner() {
        val store = VideoSearchPrivateStore()
        val registry = VideoSearchPrivateSessionRegistry(store) { "epoch-1" }
        val tab = VideoSearchPrivateTab("tab", "private", true)
        registry.reconcile(listOf(tab), "default")
        val access = VideoSearchPrivateRunAccess(registry)
        store.putSite(site("one"))
        store.selectSite(owner, "one", true)
        val plan = access.prepareMulti(tab, "default", "q")
        assertEquals("one", plan!!.sites.single().id)
        assertNull(access.prepareMulti(tab, "work", "q"))
    }

    @Test fun terminatedEpochRejectsQueuedReentry() {
        val store = VideoSearchPrivateStore()
        var counter = 0
        val registry = VideoSearchPrivateSessionRegistry(store) { "epoch-${++counter}" }
        val tab = VideoSearchPrivateTab("tab", "private", true)
        registry.reconcile(listOf(tab), "default")
        val access = VideoSearchPrivateRunAccess(registry)
        store.putSite(site("one"))
        store.selectSite(owner, "one", true)
        assertNotNull(access.prepareMulti(tab, "default", "q"))
        registry.reconcile(emptyList(), "default")
        assertNull(access.prepareMulti(tab, "default", "q"))
        registry.reconcile(listOf(tab), "default")
        assertNull(access.prepareMulti(tab, "default", "q"))
    }

    @Test fun planRefusesUnknownSelectedSiteIds() {
        val plan = VideoSearchPrivateMultiPlan.create(
            owner, "q", VideoSearchSettings(owner, setOf("site", "stale")),
            listOf(site("site")))
        assertEquals(listOf("site"), plan.sites.map { it.id })
    }
}
