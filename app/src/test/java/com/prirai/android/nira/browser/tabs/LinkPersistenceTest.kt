package com.prirai.android.nira.browser.tabs

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.prirai.android.nira.browser.tabgroups.*
import com.prirai.android.nira.browser.tabs.compose.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import mozilla.components.browser.state.action.*
import mozilla.components.browser.state.state.*
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.feature.session.middleware.undo.UndoMiddleware
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,manifest=Config.NONE)
class LinkPersistenceTest {
    @Test fun soloLinkUndoManualTransferAndRealOrderFileReloadPreserveThreeGroupsAndSourceSlot() = runTest {
        val context=RuntimeEnvironment.getApplication()
        val db=Room.inMemoryDatabaseBuilder(context,TabGroupDatabase::class.java).build()
        val dispatcher=StandardTestDispatcher(testScheduler)
        var dataScope=CoroutineScope(SupervisorJob()+dispatcher)
        try {
            val original=listOf("before","source","after","B","C").map { TabSessionState(id=it,content=ContentState(url="https://example.org")) }
            lateinit var store: BrowserStore
            val manager=UnifiedTabGroupManager(context,db,dispatcher) {store.state.tabs}
            store=BrowserStore(initialState=BrowserState(tabs=original,selectedTabId="source"),middleware=listOf(
                TabGroupMiddleware(lazy {manager},backgroundScope),UndoMiddleware(10000,backgroundScope,backgroundScope),
                {_,next,action -> if(action !is EngineAction) next(action)}
            ))
            manager.initialize();manager.awaitInitialized()
            val a=manager.createGroup(listOf("before","source","after"),"Sklep")
            val b=manager.createGroup(listOf("B"),"B");manager.createGroup(listOf("C"),"C")
            val file=File(context.cacheDir,"link-restart-${UUID.randomUUID()}.preferences_pb")
            var data=PreferenceDataStoreFactory.create(scope=dataScope) {file}
            var order=TabOrderManager(context,manager,data) {"default"}
            order.rebuildOrderForProfile("default",original)
            order.toggleGroupExpansion(b.id);manager.setGroupCollapsed(b.id,true)
            val rootGroups=order.loadOrder("default").primaryOrder
            val originalGroups=manager.getAllGroups()
            val tabs=TabsUseCases(store)
            val childId=tabs.addTab("about:blank",selectTab=false,startLoading=false,parentId="source")
            val menuId=tabs.addTab("about:homepage",selectTab=true,startLoading=false)
            store.dispatch(ContentAction.UpdateUrlAction(childId,"https://example.org/link"))
            order.rebuildOrderForProfile("default",store.state.tabs)
            assertEquals(originalGroups,manager.getAllGroups())
            assertEquals(rootGroups,order.loadOrder("default").primaryOrder.filterIsInstance<UnifiedTabOrder.OrderItem.TabGroup>())
            assertNull(manager.getGroupForTab(childId));assertNull(manager.getGroupForTab(menuId))
            val items=UnifiedItemBuilder.buildItems(order.loadOrder("default"),store.state.tabs,manager.getAllGroups(),emptySet())
            assertEquals(setOf(childId,menuId),items.filterIsInstance<UnifiedItem.SingleTab>().map {it.tab.id}.toSet())
            val saved=order.loadOrder("default").primaryOrder
            tabs.removeTab(childId);runCurrent()
            order.rebuildOrderForProfile("default",store.state.tabs)
            tabs.undo();runCurrent()
            assertTrue(store.state.tabs.any {it.id==childId && it.parentId=="source"})
            order.rebuildOrderForProfile("default",store.state.tabs)
            assertEquals(saved,order.loadOrder("default").primaryOrder)
            assertNull(manager.getGroupForTab(childId))
            assertTrue(manager.addTabToGroup(childId,a.id))
            assertTrue(manager.removeTabFromGroup(childId))
            store.dispatch(ContentAction.UpdateUrlAction(childId,"https://example.org/redirect"))
            assertEquals(originalGroups,manager.getAllGroups())
            order.rebuildOrderForProfile("default",store.state.tabs)
            val afterManual=order.loadOrder("default").primaryOrder
            dataScope.cancel();dataScope.coroutineContext.job.join()
            dataScope=CoroutineScope(SupervisorJob()+dispatcher)
            data=PreferenceDataStoreFactory.create(scope=dataScope) {file}
            val reloaded=UnifiedTabGroupManager(context,db,dispatcher) {store.state.tabs}
            reloaded.initialize();assertTrue(reloaded.awaitInitialized())
            order=TabOrderManager(context,reloaded,data) {"default"}
            order.rebuildOrderForProfile("default",emptyList())
            order.rebuildOrderForProfile("default",store.state.tabs)
            assertEquals(afterManual,order.loadOrder("default").primaryOrder)
            assertEquals(originalGroups.associateBy {it.id},reloaded.getAllGroups().associateBy {it.id})
            assertEquals(listOf("before","source","after"),db.tabGroupDao().getTabIdsInGroup(a.id))
        } finally {dataScope.cancel();dataScope.coroutineContext.job.join();db.close()}
    }
}
