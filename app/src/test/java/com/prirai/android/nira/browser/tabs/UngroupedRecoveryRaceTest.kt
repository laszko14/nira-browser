package com.prirai.android.nira.browser.tabs

import android.app.Application
import androidx.room.Room
import com.prirai.android.nira.browser.tabgroups.TabGroupDatabase
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.TabSessionState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class UngroupedRecoveryRaceTest {
    private fun tab(id: String, scope: String? = null) =
        TabSessionState(id = id, contextId = scope, content = ContentState(url = "https://example.org"))

    @Test fun recoveryRejectsPartialMoveWithoutRemovingOldMembership() = runTest {
        val ctx = RuntimeEnvironment.getApplication<Application>()
        val db = Room.inMemoryDatabaseBuilder(ctx, TabGroupDatabase::class.java).build()
        val tabs = listOf(tab("a"), tab("b"), tab("c"))
        val manager = UnifiedTabGroupManager(ctx, db, StandardTestDispatcher(testScheduler)) { tabs }
        try {
            manager.initialize()
            assertTrue(manager.awaitInitialized())
            val old = manager.createGroup(listOf("a", "b"), "Already moved")
            val refused = manager.createGroupIfUngrouped(listOf("a", "c"), "Recovery", "default", null)
            assertNull(refused)
            assertEquals(listOf("a", "b"), manager.getGroup(old.id)!!.tabIds)
            assertEquals(listOf("a", "b"), db.tabGroupDao().getTabIdsInGroup(old.id))
            assertEquals(1, manager.getAllGroups().size)
            assertNull(manager.getGroupForTab("c"))
            val created = manager.createGroupIfUngrouped(listOf("c"), "Recovery", "default", null)
            assertNotNull(created)
            assertEquals(listOf("c"), manager.getGroup(created!!.id)!!.tabIds)
        } finally { db.close() }
    }

    @Test fun queuedMoveWinsAndRecoveryAbortsAtomically() = runTest {
        val ctx = RuntimeEnvironment.getApplication<Application>()
        val db = Room.inMemoryDatabaseBuilder(ctx, TabGroupDatabase::class.java).build()
        val tabs = listOf(tab("a"), tab("b"), tab("c"))
        val manager = UnifiedTabGroupManager(ctx, db, StandardTestDispatcher(testScheduler)) { tabs }
        try {
            manager.initialize()
            assertTrue(manager.awaitInitialized())
            // Recovery was offered an ungrouped snapshot. A queued Room mutation
            // reaches the mutex first, so recovery must not detach its member.
            val moved = async { manager.createGroup(listOf("a", "b"), "New owner") }
            val recovered = async { manager.createGroupIfUngrouped(listOf("a", "c"), "Recovery", "default", null) }
            runCurrent()
            val owner = moved.await()
            assertNull(recovered.await())
            assertEquals(listOf("a", "b"), manager.getGroup(owner.id)!!.tabIds)
            assertEquals(1, db.tabGroupDao().getAllActiveGroups().size)
        } finally { db.close() }
    }

    @Test fun staleOrMixedProfileIdsCannotCreateRecoveryGroup() = runTest {
        val ctx = RuntimeEnvironment.getApplication<Application>()
        val db = Room.inMemoryDatabaseBuilder(ctx, TabGroupDatabase::class.java).build()
        val tabs = listOf(tab("a", "profile_A"), tab("b", "profile_B"))
        val manager = UnifiedTabGroupManager(ctx, db, StandardTestDispatcher(testScheduler)) { tabs }
        try {
            manager.initialize()
            assertTrue(manager.awaitInitialized())
            assertNull(manager.createGroupIfUngrouped(listOf("a", "b"), "Wrong", "A", "profile_A"))
            assertNull(manager.createGroupIfUngrouped(listOf("missing"), "Gone", "A", "profile_A"))
            assertTrue(manager.getAllGroups().isEmpty())
        } finally { db.close() }
    }
}
