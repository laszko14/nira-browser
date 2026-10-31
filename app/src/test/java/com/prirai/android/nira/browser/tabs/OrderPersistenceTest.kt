package com.prirai.android.nira.browser.tabs

import android.app.Application
import androidx.room.Room
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.prirai.android.nira.browser.tabs.compose.*
import com.prirai.android.nira.browser.tabgroups.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.ContentState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class OrderPersistenceTest {
    private fun tab(id: String, context: String? = null) = TabSessionState(id = id, contextId = context, content = ContentState(url = "https://example.org"))
    private class ControlledStore(val delegate: DataStore<Preferences>): DataStore<Preferences> {
        var readGate: CompletableDeferred<Unit>? = null
        var writeGate: CompletableDeferred<Unit>? = null
        val readEntered = CompletableDeferred<Unit>()
        val writeEntered = CompletableDeferred<Unit>()
        override val data: Flow<Preferences> get() = delegate.data.onEach {
            readGate?.let { gate -> readGate = null; readEntered.complete(Unit); gate.await() }
        }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            writeGate?.let { gate -> writeGate = null; writeEntered.complete(Unit); gate.await() }
            return delegate.updateData(transform)
        }
    }
    @Test fun suspendedOldProfileLoadCannotReplaceActiveProfileOrder() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, TabGroupDatabase::class.java).build()
        val store = ControlledStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(context.cacheDir, "switch.preferences_pb").apply { delete() } })
        val groups = UnifiedTabGroupManager(context, db, StandardTestDispatcher(testScheduler)) { emptyList() }
        groups.initialize(); groups.awaitInitialized()
        var active = "A"
        val manager = TabOrderManager(context, groups, store) { active }
        try {
            val gate = CompletableDeferred<Unit>(); store.readGate = gate
            val old = async { manager.loadOrder("A") }
            store.readEntered.await()
            active = "B"
            // The second operation can wait behind A in the fixed implementation.
            val new = async { manager.saveOrder(UnifiedTabOrder("B", listOf(UnifiedTabOrder.OrderItem.SingleTab("b")))) }
            runCurrent(); gate.complete(Unit); old.await(); new.await()
            assertEquals("B", manager.currentOrder.value!!.profileId)
            // Delayed inactive-profile writes must remain scoped in storage and UI.
            manager.saveOrder(UnifiedTabOrder("A", listOf(UnifiedTabOrder.OrderItem.SingleTab("a"))))
            assertEquals("B", manager.currentOrder.value!!.profileId)
            assertEquals(listOf("a"), manager.loadOrder("A").getAllTabIds())
            assertEquals("B", manager.currentOrder.value!!.profileId)
        } finally { db.close() }
    }
    @Test fun simultaneousExpansionChangesDoNotLoseAnUpdate() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, TabGroupDatabase::class.java).build()
        val store = ControlledStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(context.cacheDir, "toggle.preferences_pb").apply { delete() } })
        val groups = UnifiedTabGroupManager(context, db, StandardTestDispatcher(testScheduler)) { emptyList() }
        groups.initialize(); groups.awaitInitialized()
        val manager = TabOrderManager(context, groups, store) { "default" }
        try {
            manager.saveOrder(UnifiedTabOrder("default", listOf(UnifiedTabOrder.OrderItem.TabGroup("g", "G", 1, true, listOf("a")))))
            val gate = CompletableDeferred<Unit>(); store.writeGate = gate
            val first = async { manager.toggleGroupExpansion("g") }; store.writeEntered.await()
            val second = async { manager.toggleGroupExpansion("g") }; runCurrent()
            gate.complete(Unit); first.await(); second.await()
            assertTrue((manager.currentOrder.value!!.primaryOrder.single() as UnifiedTabOrder.OrderItem.TabGroup).isExpanded)
        } finally { db.close() }
    }
    @Test fun incompleteRestoreCannotEraseSavedPositionsAndGroups() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, TabGroupDatabase::class.java).build()
        val tabs = listOf(tab("a"), tab("solo"), tab("b"))
        val groups = UnifiedTabGroupManager(context, db, StandardTestDispatcher(testScheduler)) { tabs }
        groups.initialize(); groups.awaitInitialized()
        val g = groups.createGroup(listOf("b"), "Filmy")
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(context.cacheDir, "restore.preferences_pb").apply { delete() } }
        val manager = TabOrderManager(context, groups, store) { "default" }
        try {
            val original = UnifiedTabOrder("default", listOf(UnifiedTabOrder.OrderItem.SingleTab("solo"), UnifiedTabOrder.OrderItem.TabGroup(g.id, g.name, g.color, false, g.tabIds), UnifiedTabOrder.OrderItem.SingleTab("a")))
            manager.saveOrder(original)
            manager.rebuildOrderForProfile("default", emptyList())
            manager.rebuildOrderForProfile("default", tabs)
            assertEquals(original.primaryOrder, manager.loadOrder("default").primaryOrder)
        } finally { db.close() }
    }
    @Test fun interruptionAfterRoomCommitReplaysRootPlacementFromDurableIntent() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, TabGroupDatabase::class.java).build()
        val tabs = listOf(tab("a"), tab("b"), tab("solo"))
        val groups = UnifiedTabGroupManager(context, db, StandardTestDispatcher(testScheduler)) { tabs }
        groups.initialize(); groups.awaitInitialized()
        val group = groups.createGroup(listOf("a","b"))
        val data = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(context.cacheDir, "interruption.preferences_pb").apply { delete() } }
        val before = TabOrderManager(context, groups, data) { "default" }
        try {
            before.rebuildOrderForProfile("default", tabs)
            before.rememberRootMove("default", "a", "solo", true)
            groups.removeTabFromGroup("a") // process stops before updating order_<profile>
            val restarted = TabOrderManager(context, groups, data) { "default" }
            restarted.rebuildOrderForProfile("default", tabs)
            val recovered = restarted.loadOrder("default")
            assertEquals(recovered.getItemPosition("solo")!! + 1, recovered.getItemPosition("a"))
            assertNull(groups.getGroupForTab("a"))
            assertEquals(listOf("b"), groups.getGroup(group.id)!!.tabIds)
            assertNull(data.data.first()[stringPreferencesKey("pending_root_default")])
        } finally { db.close() }
    }

    @Test fun interruptionBeforeRoomCommitAbortsIntentWithoutDetachingTheTab() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, TabGroupDatabase::class.java).build()
        val tabs = listOf(tab("a"), tab("b"), tab("solo"))
        val groups = UnifiedTabGroupManager(context, db, StandardTestDispatcher(testScheduler)) { tabs }
        groups.initialize(); groups.awaitInitialized()
        val group = groups.createGroup(listOf("a","b"))
        val data = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(context.cacheDir, "abort.preferences_pb").apply { delete() } }
        val order = TabOrderManager(context, groups, data) { "default" }
        try {
            order.rebuildOrderForProfile("default", tabs)
            val before = order.loadOrder("default").primaryOrder
            order.rememberRootMove("default", "a", "solo", true)
            order.rebuildOrderForProfile("default", tabs)
            assertEquals(before, order.loadOrder("default").primaryOrder)
            assertEquals(listOf("a","b"), groups.getGroup(group.id)!!.tabIds)
            assertNull(data.data.first()[stringPreferencesKey("pending_root_default")])
        } finally { db.close() }
    }
    @Test fun savedOrderSurvivesClosingAndReopeningTheActualDataStoreFile() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, TabGroupDatabase::class.java).build()
        val tabs = listOf(tab("a"), tab("solo"), tab("b"))
        val groups = UnifiedTabGroupManager(context, db, StandardTestDispatcher(testScheduler)) { tabs }
        groups.initialize(); groups.awaitInitialized()
        val file = File(context.cacheDir, "disk-restart.preferences_pb").apply { delete() }
        var io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = PreferenceDataStoreFactory.create(scope = io) { file }
            val manager = TabOrderManager(context, groups, first) { "default" }
            val original = UnifiedTabOrder("default", listOf(UnifiedTabOrder.OrderItem.SingleTab("b"), UnifiedTabOrder.OrderItem.SingleTab("solo"), UnifiedTabOrder.OrderItem.SingleTab("a")))
            manager.saveOrder(original)
            io.cancel(); io.coroutineContext[Job]!!.join()
            io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val reopened = TabOrderManager(context, groups, PreferenceDataStoreFactory.create(scope = io) { file }) { "default" }
            reopened.rebuildOrderForProfile("default", emptyList()) // startup before session restore
            reopened.rebuildOrderForProfile("default", tabs)
            assertEquals(original.primaryOrder, reopened.loadOrder("default").primaryOrder)
        } finally { io.cancel(); io.coroutineContext[Job]!!.join(); db.close() }
    }
    @Test fun terminalCloseRetainsUndoBeforePruningRoomGroupAndOrder() = runTest {
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, TabGroupDatabase::class.java).build()
        var live = listOf(tab("a"), tab("b"), tab("solo"))
        val groups = UnifiedTabGroupManager(ctx, db, StandardTestDispatcher(testScheduler)) { live }
        groups.initialize(); assertTrue(groups.awaitInitialized())
        val group = groups.createGroup(listOf("a", "b"), "Stored")
        val data = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(ctx.cacheDir, "terminal-" + java.util.UUID.randomUUID() + ".preferences_pb")
        }
        val manager = TabOrderManager(ctx, groups, data) { "default" }
        try {
            manager.saveOrder(UnifiedTabOrder("default", listOf(
                UnifiedTabOrder.OrderItem.SingleTab("solo"),
                UnifiedTabOrder.OrderItem.TabGroup(group.id, group.name, group.color, false, listOf("a", "b"))
            )))
            live = listOf(tab("a"), tab("solo"))
            manager.finalizeClosedTabs(live, emptySet(), false)
            assertEquals(listOf("a", "b"), groups.getGroup(group.id)!!.tabIds)
            manager.finalizeClosedTabs(live, setOf("b"), true)
            assertEquals(listOf("a", "b"), groups.getGroup(group.id)!!.tabIds)
            manager.finalizeClosedTabs(live, emptySet(), true)
            assertEquals(listOf("a"), groups.getGroup(group.id)!!.tabIds)
            assertEquals(listOf("a"), db.tabGroupDao().getTabIdsInGroup(group.id))
            val final = manager.loadOrder("default")
            assertEquals(listOf("solo", "a"), final.getAllTabIds())
            assertFalse((final.primaryOrder[1] as UnifiedTabOrder.OrderItem.TabGroup).isExpanded)
        } finally { db.close() }
    }

    @Test fun historicalClosedSlotsStopGrowingAfterUndoAndRebuildRestoresLiveIds() = runTest {
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, TabGroupDatabase::class.java).build()
        var live = listOf(tab("active"))
        val groups = UnifiedTabGroupManager(ctx, db, StandardTestDispatcher(testScheduler)) { live }
        groups.initialize(); assertTrue(groups.awaitInitialized())
        val data = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(ctx.cacheDir, "prune-" + java.util.UUID.randomUUID() + ".preferences_pb")
        }
        val manager = TabOrderManager(ctx, groups, data) { "default" }
        try {
            manager.saveOrder(UnifiedTabOrder("default",
                (0 until 300).map { UnifiedTabOrder.OrderItem.SingleTab("closed" + it) } +
                    UnifiedTabOrder.OrderItem.SingleTab("active")))
            manager.finalizeClosedTabs(live, emptySet(), false)
            assertEquals(301, manager.loadOrder("default").getAllTabIds().size)
            manager.finalizeClosedTabs(live, emptySet(), true)
            assertEquals(listOf("active"), manager.loadOrder("default").getAllTabIds())
            assertFalse(data.data.first()[stringPreferencesKey("order_default")]!!.contains("closed299"))
            // Recovered live tab with the former ID must not be blocked.
            live = listOf(tab("active"), tab("closed299"))
            manager.rebuildOrderForProfile("default", live)
            assertEquals(listOf("active", "closed299"), manager.loadOrder("default").getAllTabIds())
        } finally { db.close() }
    }

    @Test fun rapidProfileAndPrivateSwitchKeepsAllFilesAndTheirVisibleOrdersIsolated() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, TabGroupDatabase::class.java).build()
        val groups = UnifiedTabGroupManager(context, db, StandardTestDispatcher(testScheduler)) { emptyList() }
        groups.initialize(); groups.awaitInitialized()
        val data = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(context.cacheDir, "isolation.preferences_pb").apply { delete() } }
        var active = "A"
        val order = TabOrderManager(context, groups, data) { active }
        try {
            for (profile in listOf("A", "B", "private")) {
                active = profile
                order.saveOrder(UnifiedTabOrder(profile, listOf(UnifiedTabOrder.OrderItem.SingleTab(profile + "2"), UnifiedTabOrder.OrderItem.SingleTab(profile + "1"))))
            }
            active = "B"
            order.rebuildOrderForProfile("B", listOf(tab("A3", "profile_A")))
            assertEquals(listOf("B2","B1"), order.loadOrder("B").getAllTabIds())
            for (profile in listOf("A", "B", "private", "A")) {
                active = profile
                assertEquals(listOf(profile + "2", profile + "1"), order.loadOrder(profile).getAllTabIds())
                assertEquals(profile, order.currentOrder.value!!.profileId)
            }
            assertEquals(setOf("order_A", "order_B", "order_private"), data.data.first().asMap().keys.map { it.name }.toSet())
        } finally { db.close() }
    }

}
