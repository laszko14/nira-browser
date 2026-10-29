package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** Contract tests only: Gecko/Android UI lifecycle still needs device tests. */
class VideoSearchPrivateWorkerLeaseTest {
    private val owner = VideoSearchScope.Private("profile", "epoch-1")
    private fun lease() = VideoSearchPrivateWorkerLease("created", owner)
    private fun tab(id: String = "created", contextId: String? = "private",
                    isPrivate: Boolean = true) =
        VideoSearchPrivateTab(id, contextId, isPrivate)
    private fun reclaim(l: VideoSearchPrivateWorkerLease,
                        tab: VideoSearchPrivateTab? = tab(),
                        selected: String? = "anchor",
                        owner: VideoSearchScope.Private? = this.owner,
                        sameEngine: Boolean = true,
                        safeDocument: Boolean = true) =
        l.mayReclaim(tab, selected, owner, sameEngine, safeDocument)

    @Test fun unselectedCreatedWorkerMayBeReclaimedOnlyUnderExactOwnership() {
        val l = lease()
        assertTrue(reclaim(l))
        assertFalse(reclaim(l, tab = tab("someone-else")))
        assertFalse(reclaim(l, tab = null))
    }

    @Test fun selectionIsStickyAfterFocusMovesAway() {
        val l = lease()
        assertTrue(l.canUse("anchor"))
        l.observeSelection("created")
        assertFalse(l.canUse("anchor"))
        assertFalse(reclaim(l))
    }

    @Test fun selectedAtCreationIsImmediatelyAdopted() {
        val l = lease()
        assertFalse(l.canUse("created"))
        assertFalse(reclaim(l, selected = "anchor"))
    }

    @Test fun lateSelectionPreventsCleanupEvenWithSameEngineAndOrigin() {
        val l = lease()
        assertTrue(l.bindSession(Any()))
        assertFalse(reclaim(l, selected = "created"))
        assertFalse(reclaim(l, selected = "anchor"))
    }

    @Test fun foreignProfileOrEpochCannotReclaimWorker() {
        val l = lease()
        assertFalse(reclaim(l, owner = VideoSearchScope.Private("other", "epoch-1")))
        assertFalse(reclaim(l, owner = VideoSearchScope.Private("profile", "epoch-2")))
        assertFalse(reclaim(l, owner = null))
    }

    @Test fun normalTabAndMismatchedContextCannotBeRemoved() {
        val l = lease()
        assertFalse(reclaim(l, tab = tab(isPrivate = false)))
        assertFalse(reclaim(l, tab = tab(contextId = "default")))
        assertFalse(reclaim(l, tab = tab(contextId = null)))
    }

    @Test fun engineSessionIdentityIsStickyForWorkerRun() {
        val l = lease()
        val first = Any()
        assertTrue(l.bindSession(first))
        assertTrue(l.hasBoundSession)
        assertTrue(l.acceptsSession(first))
        assertFalse(l.acceptsSession(Any()))
        assertFalse(l.acceptsSession(null))
        assertFalse(l.bindSession(Any()))
        assertTrue(l.acceptsSession(first))
    }

    @Test fun nullSessionCannotBeBound() {
        val l = lease()
        assertFalse(l.bindSession(null))
        assertFalse(l.hasBoundSession)
    }

    @Test fun changedOrUnsafeDocumentCannotBeReclaimed() {
        val l = lease()
        assertFalse(reclaim(l, safeDocument = false))
        assertFalse(reclaim(l, sameEngine = false))
        assertTrue(reclaim(l))
    }

    @Test fun tabReusedInNewPrivateEpochIsNeverOwnedByOldLease() {
        val l = lease()
        assertFalse(reclaim(l, owner = VideoSearchScope.Private("profile", "epoch-2")))
        assertTrue(reclaim(l, owner = owner))
    }

    @Test fun unrelatedUserSelectionDoesNotAdoptWorker() {
        val l = lease()
        l.observeSelection("other-user-tab")
        assertTrue(l.canUse("anchor"))
        assertTrue(reclaim(l))
    }
}
