package com.prirai.android.nira.browser.tabs

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.prirai.android.nira.browser.tabs.compose.*
import com.prirai.android.nira.browser.tabgroups.*
import com.prirai.android.nira.components.toolbar.modern.PortraitNativeTabStrip
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import mozilla.components.browser.state.state.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class ManualGroupsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun tab(id: String, profile: String = "default") = TabSessionState(id = id,
        contextId = when(profile) { "default" -> null; "private" -> "private"; else -> "profile_$profile" },
        content = ContentState(url = "about:homepage", title = id, private = profile == "private"))
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun activeMenuOperationSplitsExistingGroupIntoThreeAndAllProjectionsKeepLiveCounts() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val db = Room.inMemoryDatabaseBuilder(context, TabGroupDatabase::class.java).build()
        val owner = ViewModelStore()
        try {
            val tabs = listOf("a", "b", "c", "d").map { tab(it) }
            val manager = UnifiedTabGroupManager(context, db, StandardTestDispatcher(testScheduler)) { tabs }
            manager.initialize(); assertTrue(manager.awaitInitialized())
            val initial = manager.createGroup(tabs.map { it.id }, "Filmy") // Existing v1159 data.
            val data = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(context.cacheDir, "manual-${UUID.randomUUID()}.preferences_pb") }
            val order = TabOrderManager(context, manager, data) { "default" }
            val vm = TabViewModel(context, manager, order); owner.put("tabs", vm)
            vm.loadTabsForProfile("default", tabs, "a"); vm.tabs.first { it.size == tabs.size }
            val publishedMemberCounts = mutableListOf<Int>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                manager.groupsState.collect { publishedMemberCounts += it.sumOf { group -> group.tabIds.size } }
            }
            vm.moveTabToNewGroup("b", "Wiadomości")
            vm.groups.first { it.size == 2 }
            vm.moveTabToNewGroup("c", "Muzyka")
            val groups = vm.groups.first { it.size == 3 }
            assertTrue("A manual split must never publish a temporarily orphaned tab", publishedMemberCounts.all { it == 4 })
            assertEquals(3, groups.map { it.id }.distinct().size)
            assertEquals(setOf("Filmy", "Wiadomości", "Muzyka"), groups.map { it.name }.toSet())
            assertEquals(listOf("a", "d"), db.tabGroupDao().getTabIdsInGroup(initial.id))
            assertEquals(3, db.tabGroupDao().getAllActiveGroups().first().size)
            val loaded = order.loadOrder("default")
            assertEquals(3, loaded.primaryOrder.filterIsInstance<UnifiedTabOrder.OrderItem.TabGroup>().size)
            val items = UnifiedItemBuilder.buildItems(loaded, tabs, groups, groups.map { it.id }.toSet())
            assertEquals(3, items.filterIsInstance<UnifiedItem.GroupContainer>().size)
            assertEquals(4, items.filterIsInstance<UnifiedItem.GroupContainer>().sumOf { it.children.size })
            val strip = PortraitNativeTabStrip(context)
            strip.updateGroups(groups); strip.updateOrder(loaded); strip.updateTabs(tabs, "a")
            for (name in listOf("(2) Filmy", "(1) Wiadomości", "(1) Muzyka")) {
                assertTrue(name, descendants(strip).filterIsInstance<TextView>().any { name in it.text.toString() })
            }
            // Close / Undo affect live counts; saved membership is retained for restoration.
            strip.updateTabs(tabs.filterNot { it.id == "d" }, "a")
            assertTrue(descendants(strip).filterIsInstance<TextView>().any { "(1) Filmy" in it.text.toString() })
            strip.updateTabs(tabs, "d")
            assertTrue(descendants(strip).filterIsInstance<TextView>().any { "(2) Filmy" in it.text.toString() })
        } finally { owner.clear(); db.close(); Dispatchers.resetMain() }
    }

    @Test fun threeGroupsAndInterleavedOrderSurviveRealRoomAndDataStoreCloseReopen() = runTest {
        val dbName = "v1159-upgrade-${UUID.randomUUID()}.db"
        val file = File(context.cacheDir, "restart-${UUID.randomUUID()}.preferences_pb")
        val dispatcher = StandardTestDispatcher(testScheduler)
        var database = Room.databaseBuilder(context, TabGroupDatabase::class.java, dbName).build()
        var dataScope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val tabs = listOf("a", "b", "c", "d", "solo").map { tab(it) }
            var manager = UnifiedTabGroupManager(context, database, dispatcher) { tabs }
            manager.initialize(); assertTrue(manager.awaitInitialized())
            val a = manager.createGroup(listOf("a", "d"), "Old v1159", color = com.prirai.android.nira.theme.ColorConstants.TabGroups.BLUE)
            // Open the pre-existing v3 database through another manager: no schema migration.
            database.close()
            database = Room.databaseBuilder(context, TabGroupDatabase::class.java, dbName).build()
            manager = UnifiedTabGroupManager(context, database, dispatcher) { tabs }
            manager.initialize(); assertTrue(manager.awaitInitialized())
            assertEquals(a, manager.getGroup(a.id))
            val b = manager.createGroup(listOf("b"), "B")
            val c = manager.createGroup(listOf("c"), "C")
            var data = PreferenceDataStoreFactory.create(scope = dataScope) { file }
            var order = TabOrderManager(context, manager, data) { "default" }
            order.rebuildOrderForProfile("default", tabs)
            order.moveRootRelative(c.id, a.id, false)
            order.moveRootRelative("solo", c.id, true)
            order.toggleGroupExpansion(b.id)
            manager.setGroupCollapsed(b.id, true)
            assertTrue(manager.addTabToGroup("d", b.id))
            assertTrue(manager.addTabToGroup("b", c.id))
            assertTrue(manager.removeTabFromGroup("b"))
            order.rebuildOrderForProfile("default", tabs)
            val saved = order.loadOrder("default")
            val members = manager.getAllGroups().associate { it.id to it.tabIds }
            dataScope.cancel(); dataScope.coroutineContext.job.join(); database.close()
            database = Room.databaseBuilder(context, TabGroupDatabase::class.java, dbName).build()
            manager = UnifiedTabGroupManager(context, database, dispatcher) { tabs }
            manager.initialize(); assertTrue(manager.awaitInitialized())
            dataScope = CoroutineScope(SupervisorJob() + dispatcher)
            data = PreferenceDataStoreFactory.create(scope = dataScope) { file }
            order = TabOrderManager(context, manager, data) { "default" }
            order.rebuildOrderForProfile("default", emptyList()) // Incomplete BrowserStore restore.
            order.rebuildOrderForProfile("default", tabs)
            assertEquals(saved.primaryOrder, order.loadOrder("default").primaryOrder)
            assertEquals(members, manager.getAllGroups().associate { it.id to it.tabIds })
            assertTrue(manager.getGroup(b.id)!!.isCollapsed)
            assertEquals(3, manager.getAllGroups().size)
        } finally { dataScope.cancel(); dataScope.coroutineContext.job.join(); database.close(); context.deleteDatabase(dbName) }
    }

    @Test fun rapidIndependentCreationsStayUniqueAndProfileSwitchesNeverMixGroups() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val database = Room.inMemoryDatabaseBuilder(context, TabGroupDatabase::class.java).build()
        val owner = ViewModelStore()
        try {
            val tabs = (0..5).map { tab("A$it", "A") } + listOf(tab("B", "B"), tab("P", "private"))
            val dispatcher = StandardTestDispatcher(testScheduler)
            val manager = UnifiedTabGroupManager(context, database, dispatcher) { tabs }
            manager.initialize(); manager.awaitInitialized()
            val ids = (0..5).map { n -> async { manager.createGroup(listOf("A$n"), "A$n", contextId = "profile_A").id } }.awaitAll()
            assertEquals(6, ids.distinct().size)
            manager.createGroup(listOf("B"), "B", contextId = "profile_B")
            manager.createGroup(listOf("P"), "P", contextId = "private")
            var active = "A"
            val data = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(context.cacheDir, "profiles-${UUID.randomUUID()}.preferences_pb") }
            val order = TabOrderManager(context, manager, data) { active }
            val vm = TabViewModel(context, manager, order); owner.put("tabs", vm)
            for (profile in listOf("A", "B", "private", "A")) {
                active = profile
                vm.loadTabsForProfile(profile, tabs, null)
                val expected = if (profile == "A") 6 else 1
                val visible = vm.groups.first { it.size == expected }
                assertTrue(visible.all { it.contextId == if(profile == "private") "private" else "profile_$profile" })
                assertEquals(expected, order.loadOrder(profile).primaryOrder.filterIsInstance<UnifiedTabOrder.OrderItem.TabGroup>().size)
            }
            // A dialog belonging to a different profile must not move the old tab.
            vm.moveTabToNewGroup("B", "Wrong profile"); runCurrent()
            assertEquals(8, manager.getAllGroups().size)
        } finally { owner.clear(); database.close(); Dispatchers.resetMain() }
    }
}
