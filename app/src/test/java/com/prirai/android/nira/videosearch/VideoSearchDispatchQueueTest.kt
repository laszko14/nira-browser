package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** Stage 5A tests are pure; no worker, network, Gecko tab, or run created. */
class VideoSearchDispatchQueueTest {
    private val owner = VideoSearchScope.Normal("default")
    private val foreign = VideoSearchScope.Normal("work")
    private fun site(
        id: String,
        position: Int,
        scope: VideoSearchScope.Normal = owner,
        enabled: Boolean = true,
        configured: Boolean = true,
        resultLimit: Int = 200,
    ): VideoSearchSite = VideoSearchSite(
        id = id, scope = scope,
        siteUrl = "https://$id.example.org/search",
        displayName = "Witryna $id",
        position = position, enabled = enabled, resultLimit = resultLimit,
        configurationState = if (configured) VideoSiteConfigurationState.CONFIGURED
            else VideoSiteConfigurationState.NEEDS_CONFIGURATION,
        configuration = if (configured) VideoSiteConfiguration(
            "input.search", ".film-card",
            submission = VideoSearchSubmission.FORM_SUBMIT
        ) else null,
        createdAtMs = 10 + position.toLong(),
    )

    private fun plan(
        sites: List<VideoSearchSite>,
        chosen: Set<String> = sites.map { it.id }.toSet(),
        mode: VideoSearchParallelism = VideoSearchParallelism.ONE,
        query: String = "test",
    ) = VideoSearchDispatchPlan.create(
        owner, query,
        VideoSearchSettings(owner, selectedSiteIds = chosen, parallelism = mode),
        sites,
    )

    @Test fun selectedSiteCountIndependentFromOneWorker() {
        val list = (1..8).map { site("s$it", it) }
        val queue = plan(list, mode = VideoSearchParallelism.ONE).createQueue()
        assertEquals(8, queue.plan.selectedCount)
        assertEquals(listOf("s1"), queue.lease(8).map { it.id })
        assertTrue(queue.lease(8).isEmpty())
        assertTrue(queue.resolve("s1", VideoSearchQueueStatus.DONE))
        assertEquals(listOf("s2"), queue.lease(8).map { it.id })
    }

    @Test fun twoThreeFourAndFiveModesRespectLogicalCap() {
        val sites = (1..12).map { site("s$it", it) }
        for ((mode, maximum) in listOf(
            VideoSearchParallelism.TWO to 2,
            VideoSearchParallelism.THREE to 3,
            VideoSearchParallelism.FOUR to 4,
            VideoSearchParallelism.FIVE to 5,
        )) {
            val queue = plan(sites, mode = mode).createQueue()
            assertEquals(maximum, queue.lease(12).size)
            assertTrue(queue.lease(12).isEmpty())
            assertEquals(12, queue.snapshot().selected)
        }
    }

    @Test fun allModeNotArtificiallyLimitedToFive() {
        val sites = (1..11).map { site("s$it", it) }
        val q = plan(sites, mode = VideoSearchParallelism.ALL).createQueue()
        assertEquals(11, q.plan.selectedCount)
        assertEquals(9, q.lease(9).size)
        assertTrue(q.lease(9).isEmpty())
        assertEquals(2, q.lease(11).size)
        assertEquals(11, q.snapshot().active)
    }

    @Test fun physicalBackpressureCanBeZeroAndLowerThanSelectedMode() {
        val q = plan((1..6).map { site("s$it", it) },
            mode = VideoSearchParallelism.FIVE).createQueue()
        assertTrue(q.lease(0).isEmpty())
        assertEquals(2, q.lease(2).size)
        assertTrue(q.lease(1).isEmpty()) // existing leases count toward permits
        assertEquals(1, q.lease(3).size)
        assertEquals(3, q.snapshot().active)
    }

    @Test fun disabledSiteStaysInPreferencesButNotLaunchSnapshot() {
        val q = plan(listOf(
            site("a", 0, enabled = true),
            site("b", 1, enabled = false),
            site("c", 2, enabled = true),
        ), chosen = setOf("a", "b", "c"), mode = VideoSearchParallelism.ALL).createQueue()
        assertEquals(2, q.snapshot().selected)
        assertEquals(listOf("a", "c"), q.lease(10).map { it.id })
    }

    @Test fun unconfiguredSelectedSiteNeedsAttentionInsteadOfSilentLaunch() {
        val q = plan(listOf(
            site("good", 0), site("unready", 1, configured = false),
            site("last", 2)
        ), mode = VideoSearchParallelism.ALL).createQueue()
        assertEquals(VideoSearchQueueStatus.NEEDS_ATTENTION, q.statusOf("unready"))
        assertEquals(listOf("good", "last"), q.lease(8).map { it.id })
        assertEquals(1, q.snapshot().needsAttention)
    }

    @Test fun dragOrderNotSetOrderDefinesFairFifo() {
        val items = listOf(site("third", 30), site("first", 5), site("second", 20))
        val q = plan(items, chosen = setOf("second", "third", "first"),
            mode = VideoSearchParallelism.ALL).createQueue()
        assertEquals(listOf("first", "second", "third"), q.lease(10).map { it.id })
    }

    @Test fun resolvePermitsNextWorkerWithoutClaimingFalseCompletion() {
        val q = plan((1..3).map { site("s$it", it) },
            mode = VideoSearchParallelism.TWO).createQueue()
        assertEquals(2, q.lease(10).size)
        assertEquals(0, q.snapshot().finished)
        assertTrue(q.resolve("s1", VideoSearchQueueStatus.DONE))
        assertEquals(listOf("s3"), q.lease(10).map { it.id })
        assertEquals(1, q.snapshot().finished)
    }

    @Test fun cannotResolveUnknownSiteOrResolveTwice() {
        val q = plan(listOf(site("s1", 0))).createQueue()
        assertFalse(q.resolve("s1", VideoSearchQueueStatus.DONE))
        assertFalse(q.resolve("alien", VideoSearchQueueStatus.FAILED))
        q.lease(1)
        assertTrue(q.resolve("s1", VideoSearchQueueStatus.FAILED))
        assertFalse(q.resolve("s1", VideoSearchQueueStatus.DONE))
        assertEquals(1, q.snapshot().failed)
    }

    @Test fun pauseStopsNewLeasesWithoutCancellingActiveWork() {
        val q = plan((1..3).map { site("s$it", it) },
            mode = VideoSearchParallelism.TWO).createQueue()
        q.lease(2)
        q.pause()
        assertEquals(2, q.snapshot().active)
        assertTrue(q.lease(2).isEmpty())
        q.resolve("s1", VideoSearchQueueStatus.DONE)
        assertTrue(q.lease(2).isEmpty())
        q.resume()
        assertEquals(listOf("s3"), q.lease(2).map { it.id })
    }

    @Test fun stopPendingDoesNotClaimThatLeasedTasksWereCancelled() {
        val q = plan((1..4).map { site("s$it", it) },
            mode = VideoSearchParallelism.TWO).createQueue()
        q.lease(2)
        q.stopPending()
        assertTrue(q.lease(100).isEmpty())
        assertEquals(2, q.snapshot().active)
        assertEquals(2, q.snapshot().stopped)
        q.resume()
        assertTrue(q.lease(100).isEmpty())
        assertTrue(q.resolve("s1", VideoSearchQueueStatus.STOPPED))
        assertEquals(3, q.snapshot().stopped)
    }

    @Test fun invalidResultStatusCannotBeUsedToResolveAJob() {
        val q = plan(listOf(site("s1", 0))).createQueue()
        q.lease(1)
        for (invalid in listOf(VideoSearchQueueStatus.QUEUED,
            VideoSearchQueueStatus.LEASED)) {
            try {
                q.resolve("s1", invalid)
                fail("Invalid completion accepted")
            } catch (_: IllegalArgumentException) {}
        }
        assertEquals(1, q.snapshot().active)
    }

    @Test fun snapshotsClampSiteLimitTo200WithoutEditingOriginal() {
        val original = site("s", 0, resultLimit = 10_000)
        val p = plan(listOf(original))
        assertEquals(200, p.selectedSites.single().resultLimit)
        assertEquals(10_000, original.resultLimit)
        assertEquals("test", p.query)
    }

    @Test fun invalidInputOrEmptySelectedSitesFailClosed() {
        for (phrase in listOf("", "  ", "bad\u0000text", "x".repeat(251))) {
            try {
                plan(listOf(site("s", 0)), query = phrase)
                fail("Invalid query accepted")
            } catch (_: IllegalArgumentException) {}
        }
        try {
            plan(listOf(site("s", 0)), chosen = emptySet())
            fail("Empty selection accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun crossProfileOrDuplicateIdsNeverFormPlan() {
        try {
            plan(listOf(site("normal", 0), site("foreign", 1, scope = foreign)))
            fail("Cross-profile plan accepted")
        } catch (_: IllegalArgumentException) {}
        try {
            plan(listOf(site("same", 0), site("same", 1)))
            fail("Duplicate site ID accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun queryAndSiteUrlsAreRedactedInDebugStrings() {
        val q = plan(listOf(site("secret", 1)), query = "not public").createQueue()
        assertFalse(q.plan.toString().contains("not public"))
        assertFalse(q.plan.selectedSites.single().toString().contains("secret"))
        assertFalse(q.toString().contains("secret"))
        assertFalse(q.snapshot().toString().contains("secret"))
    }

    @Test fun invalidResourceBudgetRejectedWithoutClaimingSites() {
        val q = plan(listOf(site("s", 0))).createQueue()
        try {
            q.lease(-1)
            fail("Negative budget permitted")
        } catch (_: IllegalArgumentException) {}
        assertEquals(0, q.snapshot().active)
        assertEquals(1, q.snapshot().queued)
    }
}
