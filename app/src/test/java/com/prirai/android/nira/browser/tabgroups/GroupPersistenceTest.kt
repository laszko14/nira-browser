package com.prirai.android.nira.browser.tabgroups

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.ContentState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class GroupPersistenceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun tab(id: String, context: String? = null, private: Boolean = false) =
        TabSessionState(id = id, contextId = context, content = ContentState(url = "https://example.org", private = private))
    private fun db() = Room.inMemoryDatabaseBuilder(context, TabGroupDatabase::class.java).build()
    @Test fun concurrentAddsKeepEveryMemberAndDensePositionsAfterReload() = runTest {
        val database = db()
        try {
            val tabs = (0..10).map { tab("t$it") }
            val manager = UnifiedTabGroupManager(context, database, StandardTestDispatcher(testScheduler)) { tabs }
            manager.initialize(); assertTrue(manager.awaitInitialized())
            val group = manager.createGroup(listOf("t0"), "Filmy")
            (1..10).map { n -> async { manager.addTabToGroup("t$n", group.id) } }.awaitAll()
            val ids = database.tabGroupDao().getTabIdsInGroup(group.id)
            assertEquals(tabs.map { it.id }.toSet(), ids.toSet())
            assertEquals(11, ids.size)
            assertEquals(ids, manager.getGroup(group.id)!!.tabIds)
            val reloaded = UnifiedTabGroupManager(context, database, StandardTestDispatcher(testScheduler)) { tabs }
            reloaded.initialize(); assertTrue(reloaded.awaitInitialized())
            assertEquals(ids, reloaded.getGroup(group.id)!!.tabIds)
        } finally { database.close() }
    }
    @Test fun rapidExplicitChildrenCreateOneGroup() = runTest {
        val database = db()
        try {
            val tabs = listOf(tab("parent")) + (1..10).map { tab("c$it") }
            val manager = UnifiedTabGroupManager(context, database, StandardTestDispatcher(testScheduler)) { tabs }
            manager.initialize(); assertTrue(manager.awaitInitialized())
            (1..10).map { n -> async { manager.handleNewTabFromLink("c$n", "https://example.org/child", "parent", "https://example.org", null) } }.awaitAll()
            assertEquals(1, manager.getAllGroups().size)
            assertEquals(tabs.map { it.id }.toSet(), manager.getAllGroups().single().tabIds.toSet())
        } finally { database.close() }
    }
    @Test fun managerRejectsPrivateOrForeignProfileAndAlreadyClosedTabs() = runTest {
        val database = db()
        try {
            var tabs = listOf(tab("a"), tab("b"), tab("secret", "private", true), tab("foreign", "profile_B"))
            val manager = UnifiedTabGroupManager(context, database, StandardTestDispatcher(testScheduler)) { tabs }
            manager.initialize(); assertTrue(manager.awaitInitialized())
            val g = manager.createGroup(listOf("a"))
            assertFalse(manager.addTabToGroup("secret", g.id))
            assertFalse(manager.addTabToGroup("foreign", g.id))
            tabs = tabs.filterNot { it.id == "b" }
            assertFalse(manager.addTabToGroup("b", g.id))
            assertEquals(listOf("a"), manager.getGroup(g.id)!!.tabIds)
        } finally { database.close() }
    }
    @Test fun groupMoveAndReorderPersistAcrossReloadWithoutOtherMemberships() = runTest {
        val database = db()
        try {
            val tabs = listOf("a","b","c","d").map { tab(it) }
            val manager = UnifiedTabGroupManager(context, database, StandardTestDispatcher(testScheduler)) { tabs }
            manager.initialize(); manager.awaitInitialized()
            val a = manager.createGroup(listOf("a","b")); val b = manager.createGroup(listOf("c","d"))
            assertTrue(manager.addTabToGroup("b", b.id, 1))
            assertTrue(manager.updateGroup(b.id, tabIds = listOf("d","b","c")))
            val reloaded = UnifiedTabGroupManager(context, database, StandardTestDispatcher(testScheduler)) { tabs }
            reloaded.initialize(); reloaded.awaitInitialized()
            assertEquals(listOf("a"), reloaded.getGroup(a.id)!!.tabIds)
            assertEquals(listOf("d","b","c"), reloaded.getGroup(b.id)!!.tabIds)
            assertTrue(reloaded.removeTabFromGroup("b"))
            assertNull(reloaded.getGroupForTab("b"))
        } finally { database.close() }
    }
    @Test fun failedTransferRollsBackSourceTargetAndPublishedCache() = runTest {
        val database = db()
        try {
            val tabs = listOf("a","b","c").map { tab(it) }
            val manager = UnifiedTabGroupManager(context, database, StandardTestDispatcher(testScheduler)) { tabs }
            manager.initialize(); manager.awaitInitialized()
            val source = manager.createGroup(listOf("a","b"))
            val target = manager.createGroup(listOf("c"))
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER refuse_transfer BEFORE INSERT ON tab_group_members " +
                "WHEN NEW.tabId = 'b' AND NEW.groupId = '${target.id}' BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END"
            )
            var failed = false
            try { manager.addTabToGroup("b", target.id) } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertEquals(listOf("a","b"), database.tabGroupDao().getTabIdsInGroup(source.id))
            assertEquals(listOf("c"), database.tabGroupDao().getTabIdsInGroup(target.id))
            assertEquals(source.tabIds, manager.getGroup(source.id)!!.tabIds)
            assertEquals(target.tabIds, manager.getGroup(target.id)!!.tabIds)
        } finally { database.close() }
    }

    @Test fun explicitGroupMigrationRequiresMatchingLiveTabsAndCanReturnToNullDefaultPartition() = runTest {
        val database = db()
        try {
            var tabs = listOf(tab("a"),tab("b"))
            val manager = UnifiedTabGroupManager(context,database,StandardTestDispatcher(testScheduler)) { tabs }
            manager.initialize(); manager.awaitInitialized()
            val group = manager.createGroup(listOf("a","b"),"Filmy")
            assertFalse(manager.reassignGroupContext(group.id,"profile_B"))
            tabs = tabs.map { it.copy(contextId="profile_B") }
            assertTrue(manager.reassignGroupContext(group.id,"profile_B"))
            assertEquals("profile_B",database.tabGroupDao().getGroupById(group.id)!!.contextId)
            tabs = tabs.map { it.copy(contextId=null) }
            assertTrue(manager.reassignGroupContext(group.id,null))
            assertNull(database.tabGroupDao().getGroupById(group.id)!!.contextId)
            assertEquals(listOf("a","b"),manager.getGroup(group.id)!!.tabIds)
            assertEquals("Filmy",manager.getGroup(group.id)!!.name)
        } finally { database.close() }
    }

    @Test fun renameColorCollapseAndDeleteSurviveRoomReloadWithoutOrphanedMembers() = runTest {
        val database = db()
        try {
            val tabs = listOf(tab("a"),tab("b"),tab("c"))
            val manager = UnifiedTabGroupManager(context,database,StandardTestDispatcher(testScheduler)) { tabs }
            manager.initialize(); manager.awaitInitialized()
            val edited = manager.createGroup(listOf("a","b"),"Old")
            val untouched = manager.createGroup(listOf("c"),"Keep")
            assertTrue(manager.renameGroup(edited.id,"Filmy"))
            assertTrue(manager.changeGroupColor(edited.id,0xffe57373.toInt()))
            assertTrue(manager.setGroupCollapsed(edited.id,true))
            val reload = UnifiedTabGroupManager(context,database,StandardTestDispatcher(testScheduler)) { tabs }
            reload.initialize(); reload.awaitInitialized()
            assertEquals("Filmy",reload.getGroup(edited.id)!!.name)
            assertEquals(0xffe57373.toInt(),reload.getGroup(edited.id)!!.color)
            assertTrue(reload.getGroup(edited.id)!!.isCollapsed)
            assertTrue(reload.deleteGroup(edited.id))
            assertTrue(database.tabGroupDao().getTabIdsInGroup(edited.id).isEmpty())
            assertFalse(database.tabGroupDao().getGroupById(edited.id)!!.isActive)
            assertNull(reload.getGroupForTab("a"))
            assertEquals(untouched,reload.getGroup(untouched.id))
        } finally { database.close() }
    }

}
