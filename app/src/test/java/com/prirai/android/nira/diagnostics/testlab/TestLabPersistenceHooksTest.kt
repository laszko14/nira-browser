package com.prirai.android.nira.diagnostics.testlab

import android.app.Application
import androidx.room.Room
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.prirai.android.nira.browser.tabgroups.*
import com.prirai.android.nira.browser.tabs.compose.*
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
@Config(sdk=[35],application=Application::class,manifest=Config.NONE)
class TestLabPersistenceHooksTest {
    @Test fun realRoomMovesCommitBeforeSuccessAndRejectedPrivateMoveIsCancellation()=runTest {
        val app=RuntimeEnvironment.getApplication();val db=Room.inMemoryDatabaseBuilder(app,TabGroupDatabase::class.java).build()
        var now=0L;val e=TestLabEngine({now},LabIdentity(1165,"0.5.22-alpha71-mpv39"))
        val token=e.start(TestType.TABS_GROUPS)!!;e.begin(token);val capture=LabCapture(e,token)
        val tabs=listOf("a","b","c").map{TabSessionState(id=it,content=ContentState(url="about:blank"))}+
            TabSessionState(id="private",contextId="private",content=ContentState(url="about:blank",private=true))
        val groups=UnifiedTabGroupManager(app,db,StandardTestDispatcher(testScheduler),lab={capture}){tabs}
        try {
            groups.initialize();groups.awaitInitialized()
            val first=groups.createGroup(listOf("a","b"));val second=groups.createGroup(listOf("c"))
            assertTrue(groups.addTabToGroup("b",second.id));assertTrue(groups.removeTabFromGroup("b"))
            assertFalse(groups.addTabToGroup("private",first.id))
            assertEquals(listOf("a"),db.tabGroupDao().getTabIdsInGroup(first.id))
            assertEquals(listOf("c"),db.tabGroupDao().getTabIdsInGroup(second.id))
            now=1_000_000;e.stop(token);e.complete(token)
            val report=e.latest()!!
            assertEquals(2L,report.operationMetrics.first{it.operation==LabOperation.GROUP_CREATE}.succeeded)
            val move=report.operationMetrics.first{it.operation==LabOperation.TAB_MOVE}
            assertEquals(2L,move.succeeded);assertEquals(1L,move.cancelled)
            assertEquals(2L,move.completionProofs[CompletionProof.ROOM_COMMITTED])
            assertTrue(report.anomalies.isEmpty())
        } finally {db.close()}
    }
    @Test fun suspendedDataStoreWriteIsNotCompletedEarlyAndReadbackMismatchIsReported()=runTest {
        val app=RuntimeEnvironment.getApplication();val db=Room.inMemoryDatabaseBuilder(app,TabGroupDatabase::class.java).build()
        val groups=UnifiedTabGroupManager(app,db,StandardTestDispatcher(testScheduler)){emptyList()}
        var now=0L;val e=TestLabEngine({now},LabIdentity(1165,"0.5.22-alpha71-mpv39"))
        val token=e.start(TestType.TABS_GROUPS)!!;e.begin(token);val capture=LabCapture(e,token)
        val file=File(app.cacheDir,"lab-order.preferences_pb").apply{delete()}
        val delegate=PreferenceDataStoreFactory.create(scope=backgroundScope){file}
        val gate=CompletableDeferred<Unit>();val entered=CompletableDeferred<Unit>()
        val controlled=object:DataStore<Preferences> {
            override val data=delegate.data
            override suspend fun updateData(transform:suspend(Preferences)->Preferences):Preferences {
                entered.complete(Unit);gate.await();return delegate.updateData(transform)
            }
        }
        try {
            groups.initialize();groups.awaitInitialized()
            val order=TabOrderManager(app,groups,controlled,lab={capture}){"default"}
            val target=UnifiedTabOrder("default",listOf(UnifiedTabOrder.OrderItem.SingleTab("a")))
            val pending=launch{order.saveOrder(target)};entered.await()
            assertTrue(e.activeOperationMask and (1L shl LabOperation.ORDER_SAVE.ordinal)!=0L)
            now=20_000_000;gate.complete(Unit);pending.join()
            assertEquals(target,order.currentOrder.value)
            val broken=object:DataStore<Preferences> {
                override val data=flowOf(emptyPreferences())
                override suspend fun updateData(transform:suspend(Preferences)->Preferences)=emptyPreferences()
            }
            TabOrderManager(app,groups,broken,lab={capture}){"default"}.saveOrder(target)
            e.stop(token);e.complete(token)
            assertEquals(2L,e.latest()!!.operationMetrics.first{it.operation==LabOperation.ORDER_SAVE}.succeeded)
            assertEquals(LabInvariant.ORDER_NOT_PERSISTED,e.latest()!!.anomalies.single().invariant)
        } finally {db.close()}
    }
}
