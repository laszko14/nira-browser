package com.prirai.android.nira.browser.tabs

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prirai.android.nira.browser.tabgroups.*
import kotlinx.coroutines.*
import mozilla.components.browser.state.state.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android SQLite integration; does not claim to exercise touch gestures or MPV. */
@RunWith(AndroidJUnit4::class)
class TabGroupRoomDeviceTest {
    @Test fun threeGroupsKeepIndependentIdsAndMembersAfterReload() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, TabGroupDatabase::class.java).build()
        val tabs = (0..3).map { TabSessionState(id = "multi$it", content = ContentState(url = "about:homepage")) }
        try {
            val manager = UnifiedTabGroupManager(context, db, Dispatchers.IO) { tabs }
            manager.initialize(); assertTrue(manager.awaitInitialized())
            val first = manager.createGroup(tabs.map { it.id }, "Existing v1159")
            val second = manager.createGroup(listOf(tabs[1].id), "Second")
            val third = manager.createGroup(listOf(tabs[2].id), "Third")
            val reload = UnifiedTabGroupManager(context, db, Dispatchers.IO) { tabs }
            reload.initialize(); assertTrue(reload.awaitInitialized())
            assertEquals(3, reload.getAllGroups().map { it.id }.toSet().size)
            assertEquals(listOf(tabs[0].id, tabs[3].id), reload.getGroup(first.id)!!.tabIds)
            assertEquals(listOf(tabs[1].id), reload.getGroup(second.id)!!.tabIds)
            assertEquals(listOf(tabs[2].id), reload.getGroup(third.id)!!.tabIds)
        } finally { db.close() }
    }

    @Test fun concurrentAddsAndReorderSurviveManagerReload() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context,TabGroupDatabase::class.java).build()
        val tabs = (0..10).map { TabSessionState(id="synthetic$it",content=ContentState(url="about:homepage")) }
        try {
            val manager = UnifiedTabGroupManager(context,db,Dispatchers.IO) { tabs }
            manager.initialize(); assertTrue(manager.awaitInitialized())
            val group = manager.createGroup(listOf(tabs.first().id),"Synthetic")
            tabs.drop(1).map { tab -> async(Dispatchers.Default) { manager.addTabToGroup(tab.id,group.id) } }.awaitAll()
            assertTrue(manager.moveTabRelative(tabs.last().id,group.id,tabs.first().id,false))
            val reload = UnifiedTabGroupManager(context,db,Dispatchers.IO) { tabs }
            reload.initialize(); assertTrue(reload.awaitInitialized())
            assertEquals(tabs.map { it.id }.toSet(),reload.getGroup(group.id)!!.tabIds.toSet())
            assertEquals(tabs.last().id,reload.getGroup(group.id)!!.tabIds.first())
        } finally { db.close() }
    }
}
