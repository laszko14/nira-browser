package com.prirai.android.nira.integration

import android.app.Application
import android.view.View
import androidx.room.Room
import com.prirai.android.nira.browser.profile.*
import com.prirai.android.nira.browser.tabgroups.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import mozilla.components.browser.state.action.*
import mozilla.components.browser.state.state.*
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.HitResult
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.window.WindowRequest
import mozilla.components.feature.tabs.*
import mozilla.components.lib.state.Middleware
import mozilla.components.ui.widgets.DefaultSnackbarDelegate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.*
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,manifest=Config.NONE)
class ContextMenuLinkTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun parent(partition: String?)=TabSessionState(id="parent",contextId=partition,
        content=ContentState(url="https://example.org",private=partition=="private"))

    // Native Gecko loading is a recorded boundary, not executed on the host.
    private fun engineBoundary(loads: MutableList<EngineAction.LoadUrlAction>): Middleware<BrowserState,BrowserAction> =
        { _, next, action ->
            if (action is EngineAction.LoadUrlAction) loads += action
            else if (action !is EngineAction) next(action)
        }

    @Test fun actualMenuActionsPreserveDefaultNamedPrivateAndAncestryEvenAfterSourceClosed() = runTest {
        val profiles=ProfileManager(context); profiles.setPrivateMode(true)
        for(partition in listOf(null,"profile_A","private")) {
            val source=parent(partition)
            val loads=mutableListOf<EngineAction.LoadUrlAction>()
            val store=BrowserStore(initialState=BrowserState(tabs=listOf(source),selectedTabId=source.id),
                middleware=listOf(engineBoundary(loads),ProfileMiddleware(profiles),TabGroupMiddleware(lazy { error("ordinary menu must not open Room") },backgroundScope)))
            val tabs=TabsUseCases(store)
            val regular=createOpenLinkInNewTabCandidate(context,tabs,View(context),DefaultSnackbarDelegate())
            regular.action(source,HitResult.UNKNOWN("https://example.org/link"))
            val child=store.state.tabs.single {it.id!=source.id}
            assertEquals(partition,child.contextId);assertEquals(source.content.private,child.content.private)
            assertEquals(source.id,child.parentId);assertEquals(source.id,store.state.selectedTabId)
            assertEquals(child.id,loads.single().tabId)
            // Menu snapshot survives a source close: default must not be replaced by active private mode.
            store.dispatch(TabListAction.RemoveTabAction(source.id))
            regular.action(source,HitResult.IMAGE_SRC("https://example.org/image.png","https://example.org/image-link"))
            val imageChild=store.state.tabs.single {it.id!=child.id}
            assertEquals("https://example.org/image-link",imageChild.content.url)
            assertEquals(partition,imageChild.contextId);assertEquals(source.content.private,imageChild.content.private)
        }
    }

    @Test fun onlyDistinctGroupActionGroupsExactlyItsReturnedTabsIncludingPrivateAndLegacyDefault() = runTest {
        for(partition in listOf(null,"private")) {
            val database=Room.inMemoryDatabaseBuilder(context,TabGroupDatabase::class.java).build()
            try {
                val source=parent(partition)
                val loads=mutableListOf<EngineAction.LoadUrlAction>()
                lateinit var store: BrowserStore
                val manager=UnifiedTabGroupManager(context,database,StandardTestDispatcher(testScheduler)) {store.state.tabs}
                val middleware=TabGroupMiddleware(lazy {manager},backgroundScope)
                store=BrowserStore(initialState=BrowserState(tabs=listOf(source),selectedTabId=source.id),
                    middleware=listOf(engineBoundary(loads),middleware,ProfileMiddleware(ProfileManager(context))))
                manager.initialize();manager.awaitInitialized()
                // Old default group aliases must remain usable without moving its source.
                val group=manager.createGroup(listOf(source.id),"Source",contextId=partition?:"profile_default")
                val tabs=TabsUseCases(store)
                val regular=createOpenLinkInNewTabCandidate(context,tabs,View(context),DefaultSnackbarDelegate())
                val explicitIds=mutableListOf<String>()
                val explicit=createOpenLinkInGroupCandidate(context,tabs,View(context),DefaultSnackbarDelegate()) { id, parent, url ->
                    explicitIds+=id
                    middleware.groupExplicitlyOpenedTab(id,url,parent.id,parent.content.url,parent.contextId)
                }
                assertNotEquals(regular.id,explicit.id);assertNotEquals(regular.label,explicit.label)
                repeat(3) { n ->
                    regular.action(source,HitResult.UNKNOWN("https://example.org/solo$n"))
                    explicit.action(source,HitResult.UNKNOWN("https://example.org/group$n"))
                }
                backgroundScope.coroutineContext.job.children.toList().joinAll()
                assertEquals(listOf(source.id)+explicitIds,manager.getGroup(group.id)!!.tabIds)
                assertEquals(1,manager.getAllGroups().size)
                assertEquals(3,store.state.tabs.count {it.id!=source.id && manager.getGroupForTab(it.id)==null})
                assertTrue(store.state.tabs.all {it.content.private==source.content.private})
                assertEquals(6,loads.size);assertTrue(loads.none {it.tabId==source.id})
            } finally {database.close()}
        }
    }

    @Test fun realMozillaWindowFeatureCreatesSoloChildInItsSourceProfile() = runTest {
        val profiles=ProfileManager(context)
        profiles.setPrivateMode(true) // Opposite to the still-live normal source.
        val request=mock(WindowRequest::class.java)
        `when`(request.type).thenReturn(WindowRequest.Type.OPEN)
        `when`(request.prepare()).thenReturn(mock(EngineSession::class.java))
        val source=parent("profile_A").let {it.copy(content=it.content.copy(windowRequest=request))}
        val store=BrowserStore(initialState=BrowserState(tabs=listOf(source),selectedTabId=source.id),
            middleware=listOf(ProfileMiddleware(profiles),TabGroupMiddleware(lazy {error("window.open must not group")},backgroundScope)))
        val window=WindowFeature(store,TabsUseCases(store),StandardTestDispatcher(testScheduler))
        try {
            window.start();runCurrent()
            val child=store.state.tabs.single {it.id!=source.id}
            assertEquals(source.id,child.parentId);assertEquals("profile_A",child.contextId)
            assertFalse(child.content.private)
            verify(request).start()
        } finally {window.stop()}
    }
}
