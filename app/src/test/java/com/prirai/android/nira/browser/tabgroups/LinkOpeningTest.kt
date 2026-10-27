package com.prirai.android.nira.browser.tabgroups

import android.app.Application
import androidx.room.Room
import com.prirai.android.nira.browser.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import mozilla.components.browser.state.action.*
import mozilla.components.browser.state.state.*
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class LinkOpeningTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun tab(id: String, parent: String? = null, url: String = "https://example.org", profile: String? = null) =
        TabSessionState(id=id, parentId=parent, contextId=profile, content=ContentState(url=url, private=profile=="private"))

    @Test fun tenParentedLinksLeaveThreeExistingGroupsAndSourcePositionsUntouched() = runTest {
        val database = Room.inMemoryDatabaseBuilder(context, TabGroupDatabase::class.java).build()
        try {
            val original = listOf("before","source","after","b","c","solo").map { tab(it) }
            lateinit var store: BrowserStore
            val manager = UnifiedTabGroupManager(context,database,StandardTestDispatcher(testScheduler)) { store.state.tabs }
            val middleware = TabGroupMiddleware(lazy { manager }, backgroundScope)
            store = BrowserStore(initialState=BrowserState(tabs=original,selectedTabId="source"),middleware=listOf(middleware))
            manager.initialize(); assertTrue(manager.awaitInitialized())
            manager.createGroup(listOf("before","source","after"),"Sklep")
            manager.createGroup(listOf("b"),"B");manager.createGroup(listOf("c"),"C")
            val before = manager.getAllGroups()
            (1..10).map { n -> async { store.dispatch(TabListAction.AddTabAction(tab("link$n","source"),false)) } }.awaitAll()
            backgroundScope.coroutineContext.job.children.toList().joinAll()
            assertEquals(16,store.state.tabs.size)
            assertEquals(before,manager.getAllGroups())
            assertTrue((1..10).all { manager.getGroupForTab("link$it") == null })
        } finally { database.close() }
    }

    @Test fun blankThenRedirectedChildAndSelectedTabChangesNeverCreateAGroup() = runTest {
        val database = Room.inMemoryDatabaseBuilder(context,TabGroupDatabase::class.java).build()
        try {
            lateinit var store: BrowserStore
            val manager = UnifiedTabGroupManager(context,database,StandardTestDispatcher(testScheduler)) { store.state.tabs }
            store=BrowserStore(initialState=BrowserState(tabs=listOf(tab("parent"),tab("unrelated")),selectedTabId="parent"),middleware=listOf(TabGroupMiddleware(lazy {manager},backgroundScope)))
            manager.initialize();manager.awaitInitialized()
            store.dispatch(TabListAction.AddTabAction(tab("child","parent","about:blank"),false))
            store.dispatch(TabListAction.SelectTabAction("unrelated"))
            for(url in listOf("https://example.org/redirect","https://example.org/final")) {
                store.dispatch(ContentAction.UpdateUrlAction("child",url)); backgroundScope.coroutineContext.job.children.toList().joinAll()
            }
            assertTrue(manager.getAllGroups().isEmpty())
            assertEquals("parent",store.state.tabs.first {it.id=="child"}.parentId)
        } finally { database.close() }
    }

    @Test fun geckoParentedChildInheritsSourcePartitionRatherThanNewlySelectedProfile() {
        context.getSharedPreferences("profile_manager",0).edit()
            .putString("profiles","[{\"id\":\"B\",\"name\":\"B\",\"isDefault\":false}]")
            .putString("active_profile_id","B").apply()
        val profiles=ProfileManager(context)
        for (partition in listOf(null,"profile_A")) {
            val parent=tab("source",profile=partition)
            val store=BrowserStore(initialState=BrowserState(tabs=listOf(parent)))
            var result: TabSessionState?=null
            ProfileMiddleware(profiles).invoke(store,{result=(it as TabListAction.AddTabAction).tab},TabListAction.AddTabAction(tab("child","source"),false))
            assertEquals("parent partition",partition,result!!.contextId)
            assertEquals("source",result!!.parentId)
        }
    }
    @Test fun delayedExplicitRequestCannotStealManuallyGroupedChildOrUseClosedForeignSource() = runTest {
        val database=Room.inMemoryDatabaseBuilder(context,TabGroupDatabase::class.java).build()
        try {
            var live=listOf(tab("source"),tab("child"),tab("solo"))
            val manager=UnifiedTabGroupManager(context,database,StandardTestDispatcher(testScheduler)) {live}
            manager.initialize();manager.awaitInitialized()
            manager.createGroup(listOf("source"),"Source")
            manager.createGroup(listOf("child"),"Later manual move")
            val saved=manager.getAllGroups()
            manager.groupExplicitlyOpenedLink("child","https://example.org","source","https://example.org")
            assertEquals(saved,manager.getAllGroups())
            live=live.filterNot {it.id=="source"}
            manager.groupExplicitlyOpenedLink("solo","https://example.org","source","https://example.org")
            assertEquals(saved,manager.getAllGroups())
            live=live+tab("source",profile="private")
            manager.groupExplicitlyOpenedLink("solo","https://example.org","source","https://example.org")
            assertEquals(saved,manager.getAllGroups())
        } finally {database.close()}
    }

}
