package com.prirai.android.nira.browser.tabs

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.prirai.android.nira.browser.tabs.compose.*
import com.prirai.android.nira.browser.tabgroups.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import mozilla.components.browser.state.state.*
import mozilla.components.browser.state.action.*
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.session.middleware.undo.UndoMiddleware
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class TabDropIntegrationTest {
    private fun tab(id: String, context: String? = null) = TabSessionState(id = id, contextId = context, content = ContentState(url = "https://example.org"))
    @Test fun v1123v1124GroupReorderTransferDetachAndReloadHaveRealEffects() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, TabGroupDatabase::class.java).build()
        val owner = ViewModelStore()
        try {
            val tabs = listOf("a","b","c","d","solo").map { tab(it) }
            val groups = UnifiedTabGroupManager(context, db, StandardTestDispatcher(testScheduler)) { tabs }
            groups.initialize(); groups.awaitInitialized()
            val a = groups.createGroup(listOf("a","b"), "A"); val b = groups.createGroup(listOf("c","d"), "B")
            val data = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(context.cacheDir, "drops.preferences_pb").apply { delete() } }
            val order = TabOrderManager(context, groups, data) { "default" }
            val vm = TabViewModel(context, groups, order); owner.put("tabs", vm)
            vm.loadTabsForProfile("default", tabs, "a"); vm.tabs.first { it.size == 5 }
            assertTrue(vm.commitSidePanelDrop("a", "group_tab_b", true))
            assertEquals(listOf("b","a"), db.tabGroupDao().getTabIdsInGroup(a.id))
            assertTrue(vm.commitSidePanelDrop("a", "group_tab_d", false))
            assertEquals(listOf("b"), groups.getGroup(a.id)!!.tabIds)
            assertEquals(listOf("c","a","d"), groups.getGroup(b.id)!!.tabIds)
            assertTrue(vm.commitSidePanelDrop("a", "single_tab_solo", false))
            assertNull(groups.getGroupForTab("a"))
            val loaded = order.loadOrder("default")
            val pos = loaded.getItemPosition("a")!!
            assertEquals(pos + 1, loaded.getItemPosition("solo"))
            val ui = UnifiedItemBuilder.buildItems(loaded, tabs, groups.getAllGroups(), emptySet())
            assertEquals(5, ui.sumOf { if(it is UnifiedItem.GroupContainer) it.children.size else 1 })
        } finally { owner.clear(); db.close(); Dispatchers.resetMain() }
    }
    @Test fun switchingProfileImmediatelyClearsStaleDragSourceBeforeAnySuspendingLoad() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, TabGroupDatabase::class.java).build()
        val owner = ViewModelStore()
        try {
            val tabs = listOf(tab("a", "profile_A"), tab("b", "profile_A"), tab("solo", "profile_A"))
            val groups = UnifiedTabGroupManager(context, db, StandardTestDispatcher(testScheduler)) { tabs }
            groups.initialize(); groups.awaitInitialized()
            val g = groups.createGroup(listOf("a","b"), contextId = "profile_A")
            val data = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(context.cacheDir, "staledrop.preferences_pb").apply { delete() } }
            var active = "A"
            val order = TabOrderManager(context, groups, data) { active }
            val vm = TabViewModel(context, groups, order); owner.put("tabs", vm)
            vm.loadTabsForProfile("A", tabs, "a"); vm.tabs.first { it.size == 3 }
            active = "B"; vm.loadTabsForProfile("B", emptyList(), null)
            assertTrue(vm.tabs.value.isEmpty())
            assertFalse(vm.commitSidePanelDrop("a", "single_tab_solo", true))
            assertEquals(listOf("a","b"), groups.getGroup(g.id)!!.tabIds)
        } finally { owner.clear(); db.close(); Dispatchers.resetMain() }
    }
    @Test fun finalReleaseRechecksPointerInsteadOfCommittingThePreviousDebouncedTarget() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, TabGroupDatabase::class.java).build()
        val owner = ViewModelStore()
        val dropOwner = SupervisorJob(coroutineContext[Job])
        try {
            val tabs = listOf("a","b","c").map { tab(it) }
            val groups = UnifiedTabGroupManager(context, db, StandardTestDispatcher(testScheduler)) { tabs }
            groups.initialize(); groups.awaitInitialized()
            val data = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(context.cacheDir, "release.preferences_pb").apply { delete() } }
            val order = TabOrderManager(context, groups, data) { "default" }
            val vm = TabViewModel(context, groups, order); owner.put("tabs",vm)
            vm.loadTabsForProfile("default",tabs,"a"); vm.tabs.first { it.size == 3 }
            var clock = 1000L
            val coordinator = DragCoordinator(CoroutineScope(coroutineContext + dropOwner),vm,order,now={clock})
            coordinator.registerDropTarget(DropTarget("b",androidx.compose.ui.geometry.Rect(100f,0f,160f,40f),DropTargetType.TAB,mapOf("tabId" to "b")))
            coordinator.registerDropTarget(DropTarget("c",androidx.compose.ui.geometry.Rect(200f,0f,260f,40f),DropTargetType.TAB,mapOf("tabId" to "c")))
            coordinator.startDrag(DraggableItemType.Tab("a"),androidx.compose.ui.geometry.Offset(10f,10f),androidx.compose.ui.geometry.Rect(0f,0f,60f,40f),androidx.compose.ui.unit.IntSize(60,40))
            coordinator.updateDrag(androidx.compose.ui.geometry.Offset.Zero,androidx.compose.ui.geometry.Offset(159f,10f))
            clock++
            coordinator.updateDrag(androidx.compose.ui.geometry.Offset.Zero,androidx.compose.ui.geometry.Offset(259f,10f))
            coordinator.endDrag()
            dropOwner.children.toList().forEach { it.join() }
            assertEquals(listOf("b","c","a"),order.loadOrder("default").getAllTabIds())
        } finally { dropOwner.cancel(); owner.clear(); db.close(); Dispatchers.resetMain() }
    }

    @Test fun creatingGroupOnSoloTargetPreservesTheTargetsSlotAfterRemovingTheDraggedSolo() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context,TabGroupDatabase::class.java).build()
        val owner = ViewModelStore()
        try {
            val tabs = listOf("a","target","b","c").map { tab(it) }
            val groups = UnifiedTabGroupManager(context,db,StandardTestDispatcher(testScheduler)) { tabs }
            groups.initialize(); groups.awaitInitialized()
            val data = PreferenceDataStoreFactory.create(scope=backgroundScope) { File(context.cacheDir,"center.preferences_pb").apply { delete() } }
            val order = TabOrderManager(context,groups,data) { "default" }
            val vm = TabViewModel(context,groups,order); owner.put("tabs",vm)
            vm.loadTabsForProfile("default",tabs,"a"); vm.tabs.first { it.size == 4 }
            val target = DropTarget("target",androidx.compose.ui.geometry.Rect(0f,0f,60f,40f),DropTargetType.TAB,mapOf("tabId" to "target"),DropZone.CENTER)
            assertTrue(vm.commitUnifiedDrop(DraggableItemType.Tab("a"),target,"default"))
            val result = order.loadOrder("default")
            assertTrue(result.primaryOrder.first() is UnifiedTabOrder.OrderItem.TabGroup)
            assertEquals(listOf("a","target","b","c"),result.getAllTabIds())
        } finally { owner.clear(); db.close(); Dispatchers.resetMain() }
    }

    @Test fun profileSwitchDuringGroupCommitFinishesOnlyTheOriginatingFile() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context,TabGroupDatabase::class.java).build()
        val owner = ViewModelStore()
        try {
            val tabs = listOf("a","target","b").map { tab(it,"profile_A") }
            var active = "A"
            var switchDuringMutation = false
            lateinit var vm: TabViewModel
            val groups = UnifiedTabGroupManager(context,db,StandardTestDispatcher(testScheduler)) {
                if (switchDuringMutation) {
                    switchDuringMutation = false
                    active = "B"
                    vm.loadTabsForProfile("B",emptyList(),null)
                }
                tabs
            }
            groups.initialize(); groups.awaitInitialized()
            val data = PreferenceDataStoreFactory.create(scope=backgroundScope) { File(context.cacheDir,"inflight.preferences_pb").apply { delete() } }
            val order = TabOrderManager(context,groups,data) { active }
            vm = TabViewModel(context,groups,order); owner.put("tabs",vm)
            vm.loadTabsForProfile("A",tabs,"a"); vm.tabs.first { it.size == 3 }
            switchDuringMutation = true
            val target = DropTarget("target",androidx.compose.ui.geometry.Rect(0f,0f,60f,40f),DropTargetType.TAB,mapOf("tabId" to "target"),DropZone.CENTER)
            assertTrue(vm.commitUnifiedDrop(DraggableItemType.Tab("a"),target,"A"))
            assertEquals("B",active)
            assertTrue(vm.tabs.value.isEmpty())
            assertNull(data.data.first()[androidx.datastore.preferences.core.stringPreferencesKey("pending_root_A")])
            assertEquals(listOf("a","target","b"),order.loadOrder("A").getAllTabIds())
            order.loadOrder("B")
            assertEquals("B",order.currentOrder.value!!.profileId)
        } finally { owner.clear(); db.close(); Dispatchers.resetMain() }
    }

    @Test fun actualStoreCloseAndUndoPreserveOriginalIdMembershipAndRootSlot() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context,TabGroupDatabase::class.java).build()
        try {
            val tabs = listOf("a","b","solo").map { tab(it) }
            val store = BrowserStore(initialState=BrowserState(tabs=tabs,selectedTabId="a"),middleware=listOf(UndoMiddleware(5000,backgroundScope,backgroundScope)))
            val groups = UnifiedTabGroupManager(context,db,StandardTestDispatcher(testScheduler)) { store.state.tabs }
            groups.initialize(); groups.awaitInitialized()
            val group = groups.createGroup(listOf("a","b"),"Filmy")
            val data = PreferenceDataStoreFactory.create(scope=backgroundScope) { File(context.cacheDir,"undo.preferences_pb").apply { delete() } }
            val order = TabOrderManager(context,groups,data) { "default" }
            order.rebuildOrderForProfile("default",store.state.tabs)
            val before = order.loadOrder("default").primaryOrder
            store.dispatch(TabListAction.RemoveTabAction("a"))
            runCurrent()
            assertEquals(listOf("a"),store.state.undoHistory.tabs.map { it.state.id })
            order.rebuildOrderForProfile("default",store.state.tabs)
            assertEquals(before,order.loadOrder("default").primaryOrder)
            store.dispatch(UndoAction.RestoreRecoverableTabs)
            runCurrent()
            assertEquals(tabs.map { it.id }.toSet(),store.state.tabs.map { it.id }.toSet())
            assertEquals(listOf("a","b"),groups.getGroup(group.id)!!.tabIds)
            assertEquals(before,order.loadOrder("default").primaryOrder)
        } finally { db.close(); Dispatchers.resetMain() }
    }
    @Test fun sideGroupReorderUsesTheSharedPersistentRootOrder() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context,TabGroupDatabase::class.java).build()
        val owner = ViewModelStore()
        try {
            val tabs = listOf("a","b","solo").map { tab(it) }
            val groups = UnifiedTabGroupManager(context,db,StandardTestDispatcher(testScheduler)) { tabs }
            groups.initialize(); groups.awaitInitialized()
            val a = groups.createGroup(listOf("a"),"A"); val b = groups.createGroup(listOf("b"),"B")
            val data = PreferenceDataStoreFactory.create(scope=backgroundScope) { File(context.cacheDir,"rootgroups.preferences_pb").apply { delete() } }
            val order = TabOrderManager(context,groups,data) { "default" }
            val vm = TabViewModel(context,groups,order); owner.put("tabs",vm)
            vm.loadTabsForProfile("default",tabs,"a"); vm.tabs.first { it.size == 3 }
            assertTrue(vm.commitSidePanelGroupDrop(a.id,"single_tab_solo",true,"default"))
            val saved = order.loadOrder("default")
            assertEquals(listOf("b","solo","a"),saved.getAllTabIds())
            order.rebuildOrderForProfile("default",tabs)
            assertEquals(saved.primaryOrder,order.loadOrder("default").primaryOrder)
            assertEquals(listOf("b"),groups.getGroup(b.id)!!.tabIds)
        } finally { owner.clear(); db.close(); Dispatchers.resetMain() }
    }

}
