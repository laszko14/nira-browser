package com.prirai.android.nira.audit

import android.app.Activity
import android.app.Application
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import com.prirai.android.nira.browser.*
import com.prirai.android.nira.browser.toolbar.*
import com.prirai.android.nira.browser.tabs.TabLRUManager
import com.prirai.android.nira.request.AppRequestInterceptor
import mozilla.components.browser.state.state.*
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.request.RequestInterceptor
import mozilla.components.feature.tabs.TabsUseCases
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,manifest=Config.NONE)
class AppBoundariesTest {
    @Test fun oauthRedirectDoesNotDiscloseQuerySecretsInLogs() {
        ShadowLog.clear()
        val secret="synthetic-private-code-58293"
        val interceptor=AppRequestInterceptor(RuntimeEnvironment.getApplication())
        interceptor.fxaInterceptor=object:RequestInterceptor {
            override fun onLoadRequest(engineSession:EngineSession,uri:String,lastUri:String?,hasUserGesture:Boolean,
                isSameDomain:Boolean,isRedirect:Boolean,isDirectNavigation:Boolean,isSubframeRequest:Boolean)=RequestInterceptor.InterceptionResponse.Deny
        }
        interceptor.onLoadRequest(
            mock(EngineSession::class.java),"https://accounts.firefox.com/oauth?code=$secret&state=private",null,
            true,false,false,false,false
        )
        assertFalse(ShadowLog.getLogs().any { secret in it.msg })
    }
    @Test fun cancelledEdgeGestureNeverCommitsPanelOpening() {
        val layout=SwipeGestureLayout(RuntimeEnvironment.getApplication())
        var committed=0;var cancelled=0
        layout.addGestureListener(object:SwipeGestureListener {
            override fun onSwipeStarted(start:PointF,next:PointF)=true
            override fun onSwipeUpdate(distanceX:Float,distanceY:Float){}
            override fun onSwipeFinished(velocityX:Float,velocityY:Float){committed++}
            override fun onSwipeCancelled(){cancelled++}
        })
        fun event(action:Int,x:Float,time:Long)=MotionEvent.obtain(1,time,action,x,20f,0)
        event(MotionEvent.ACTION_DOWN,1f,1).let{layout.onInterceptTouchEvent(it);it.recycle()}
        event(MotionEvent.ACTION_MOVE,120f,40).let{assertTrue(layout.onInterceptTouchEvent(it));it.recycle()}
        event(MotionEvent.ACTION_CANCEL,120f,50).let{assertTrue(layout.onTouchEvent(it));it.recycle()}
        assertEquals(0,committed);assertEquals(1,cancelled)
        event(MotionEvent.ACTION_DOWN,1f,100).let{layout.onInterceptTouchEvent(it);it.recycle()}
        event(MotionEvent.ACTION_MOVE,120f,150).let{assertTrue(layout.onInterceptTouchEvent(it));it.recycle()}
        event(MotionEvent.ACTION_UP,120f,400).let{assertTrue(layout.onTouchEvent(it));it.recycle()}
        assertEquals(1,committed)
    }
    @Test fun toolbarHistoryNeverSelectsAnotherNormalProfile() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        fun tab(id:String,profile:String)=TabSessionState(id=id,contextId=profile,content=ContentState(url="https://example.org"))
        val tabs=listOf(tab("a","profile_A"),tab("otherA","profile_A"),tab("foreign","profile_B"))
        val store=BrowserStore(initialState=BrowserState(tabs=tabs,selectedTabId="a"))
        val lru=TabLRUManager.getInstance(activity)
        lru.synchronizeWithTabs(tabs.map{it.id})
        listOf("otherA","foreign","a").forEach(lru::onTabSelected)
        val handler=ToolbarGestureHandler(activity,View(activity),mock(FakeTab::class.java),View(activity),store,TabsUseCases(store).selectTab)
        val destination:Any=ReflectionHelpers.callInstanceMethod(handler,"getDestination")
        val target:TabSessionState=ReflectionHelpers.getField(destination,"tab")
        assertEquals("otherA",target.id)
        activity.finish()
    }
    @Test fun completedAnimationCannotOverrideANewerProfileSelection() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val source=TabSessionState(id="a",contextId="profile_A",content=ContentState(url="https://example.org"))
        val target=source.copy(id="nextA");val foreign=source.copy(id="b",contextId="profile_B")
        val store=BrowserStore(initialState=BrowserState(tabs=listOf(source,target,foreign),selectedTabId=source.id))
        val preview=mock(FakeTab::class.java)
        val animation=org.mockito.Mockito.mock(android.view.ViewPropertyAnimator::class.java,org.mockito.Mockito.RETURNS_SELF)
        org.mockito.Mockito.`when`(preview.animate()).thenReturn(animation)
        val handler=ToolbarGestureHandler(activity,View(activity),preview,View(activity),store,TabsUseCases(store).selectTab)
        ReflectionHelpers.callInstanceMethod<Unit>(handler,"animateToNextTab",ReflectionHelpers.ClassParameter.from(TabSessionState::class.java,target))
        store.dispatch(mozilla.components.browser.state.action.TabListAction.SelectTabAction(foreign.id))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(2,java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(foreign.id,store.state.selectedTabId)
        activity.finish()
    }

    @Test fun finishingActivityCannotCommitAPendingTabSwitch() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val source=TabSessionState(id="a",contextId="profile_A",content=ContentState(url="https://example.org"))
        val target=source.copy(id="nextA")
        val store=BrowserStore(initialState=BrowserState(tabs=listOf(source,target),selectedTabId=source.id))
        val preview=mock(FakeTab::class.java)
        org.mockito.Mockito.`when`(preview.animate()).thenReturn(org.mockito.Mockito.mock(android.view.ViewPropertyAnimator::class.java,org.mockito.Mockito.RETURNS_SELF))
        val handler=ToolbarGestureHandler(activity,View(activity),preview,View(activity),store,TabsUseCases(store).selectTab)
        ReflectionHelpers.callInstanceMethod<Unit>(handler,"animateToNextTab",ReflectionHelpers.ClassParameter.from(TabSessionState::class.java,target))
        activity.finish()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(2,java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(source.id,store.state.selectedTabId)
    }

}
