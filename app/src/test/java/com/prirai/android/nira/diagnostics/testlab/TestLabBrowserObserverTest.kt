package com.prirai.android.nira.diagnostics.testlab

import android.app.Application
import mozilla.components.browser.state.action.*
import mozilla.components.browser.state.state.*
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,manifest=Config.NONE)
class TestLabBrowserObserverTest {
    @Test fun observesTenRealReducerCommitsAndLoadCompletionWithoutSavingPrivateIdentifiers() {
        var now=0L;val engine=TestLabEngine({now},LabIdentity(1165,"0.5.22-alpha71-mpv39"))
        val token=engine.start(TestType.COMPLEX)!!;engine.begin(token)
        val observer=TestLabBrowserObserver(LabCapture(engine,token))
        val store=BrowserStore(middleware=listOf(observer))
        val parent=TabSessionState(id="SECRET_ID",content=ContentState(url="https://secret.example/?token=PRIVATE"))
        store.dispatch(TabListAction.AddTabAction(parent))
        repeat(10) {i->store.dispatch(TabListAction.AddTabAction(parent.copy(id="child_$i",parentId=parent.id)))}
        store.dispatch(ContentAction.UpdateLoadingStateAction(parent.id,true));now=200_000_000
        store.dispatch(ContentAction.UpdateLoadingStateAction(parent.id,false))
        assertEquals(11,store.state.tabs.size);observer.close()
        engine.stop(token);engine.complete(token)
        val report=engine.latest()!!
        assertEquals(10L,report.operationMetrics.first{it.operation==LabOperation.LINK_OPEN}.succeeded)
        assertEquals(1L,report.operationMetrics.first{it.operation==LabOperation.PAGE_LOAD}.succeeded)
        assertEquals(0,observer.pendingLoads)
        val json=TestLabReportGenerator.generate(report).json
        assertFalse(json.contains("SECRET"));assertFalse(json.contains("secret.example"));assertFalse(json.contains("child_"))
    }
    @Test fun staleLoadCancellationAndSelectedIdDoNotInventSuccessfulLoadOrSourceParent() {
        val engine=TestLabEngine({0L},LabIdentity(1165,"0.5.22-alpha71-mpv39"))
        val token=engine.start(TestType.COMPLEX)!!;engine.begin(token)
        val observer=TestLabBrowserObserver(LabCapture(engine,token));val store=BrowserStore(middleware=listOf(observer))
        val tab=TabSessionState(id="x",content=ContentState(url="about:blank"))
        store.dispatch(TabListAction.AddTabAction(tab));store.dispatch(ContentAction.UpdateLoadingStateAction("x",true))
        store.dispatch(TabListAction.RemoveTabAction("x"));store.dispatch(ContentAction.UpdateLoadingStateAction("x",false))
        engine.stop(token);observer.close();engine.complete(token)
        assertEquals(0L,engine.latest()!!.operationMetrics.first{it.operation==LabOperation.PAGE_LOAD}.succeeded)
        assertEquals(0L,engine.latest()!!.operationMetrics.first{it.operation==LabOperation.LINK_OPEN}.started)
    }
    @Test fun geckoErrorFailsOnlyTheBoundEngineAndNeverTheCurrentlySelectedOtherTab() {
        val e=TestLabEngine({0L},LabIdentity(1165,"0.5.22-alpha71-mpv39"));val t=e.start(TestType.COMPLEX)!!;e.begin(t)
        val observer=TestLabBrowserObserver(LabCapture(e,t));val store=BrowserStore(middleware=listOf(observer))
        val first=org.mockito.Mockito.mock(mozilla.components.concept.engine.EngineSession::class.java)
        val second=org.mockito.Mockito.mock(mozilla.components.concept.engine.EngineSession::class.java)
        store.dispatch(TabListAction.AddTabAction(TabSessionState(id="first",content=ContentState(url="about:blank"),engineState=EngineState(engineSession=first))))
        store.dispatch(TabListAction.AddTabAction(TabSessionState(id="second",content=ContentState(url="about:blank"),engineState=EngineState(engineSession=second))))
        store.dispatch(ContentAction.UpdateLoadingStateAction("first",true));store.dispatch(ContentAction.UpdateLoadingStateAction("second",true))
        observer.error(first)
        store.dispatch(ContentAction.UpdateLoadingStateAction("first",false));store.dispatch(ContentAction.UpdateLoadingStateAction("second",false))
        observer.close();e.stop(t);e.complete(t)
        val loads=e.latest()!!.operationMetrics.first{it.operation==LabOperation.PAGE_LOAD}
        assertEquals(1L,loads.failed);assertEquals(1L,loads.succeeded)
    }
}
