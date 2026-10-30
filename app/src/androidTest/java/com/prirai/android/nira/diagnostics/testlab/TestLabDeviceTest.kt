package com.prirai.android.nira.diagnostics.testlab

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Color
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.BrowserApp
import com.prirai.android.nira.preferences.UserPreferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Actual Android Window/JankStats/BrowserStore/Room, controlled UI workload. No native playback/frame-compositor claim. */
@RunWith(AndroidJUnit4::class)
class TestLabDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val app get()=instrumentation.targetContext.applicationContext as BrowserApp
    private lateinit var browser:BrowserActivity
    private var screen:TestLabActivity?=null
    private var paint:WorkloadView?=null
    private var firstLaunch=true
    private var migrationDone=false
    private var migrationPopup=false
    private var edgeEnabled=true
    private val tabs=mutableListOf<String>()
    private var group:String?=null
    private var selectedBefore:String?=null
    private var stage="setup"
    private val browserLifecycle=androidx.test.runner.lifecycle.ActivityLifecycleCallback { activity,state ->
        if(activity is BrowserActivity && state==androidx.test.runner.lifecycle.Stage.RESUMED) {
            // Returning from the tester may apply the browser's saved orientation and recreate
            // the Activity. Never inspect an old Window or keep driving its detached workload.
            browser=activity
            if(paint?.context!==activity) {
                paint?.running=false;(paint?.parent as? android.view.ViewGroup)?.removeView(paint)
                paint=WorkloadView(activity)
                activity.addContentView(paint,FrameLayout.LayoutParams(48,48,android.view.Gravity.TOP or android.view.Gravity.END))
            }
        }
    }

    @Before fun setup() {
        val prefs=UserPreferences(app)
        firstLaunch=prefs.firstLaunch;migrationDone=prefs.defaultProfileMigrationDone
        migrationPopup=prefs.pendingDefaultMigrationPopup;edgeEnabled=prefs.niraEdgePanelsEnabled
        prefs.firstLaunch=false;prefs.defaultProfileMigrationDone=true;prefs.pendingDefaultMigrationPopup=false;prefs.niraEdgePanelsEnabled=true
        TestLab.deleteReport()
        onMain {androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(browserLifecycle)}
        browser=instrumentation.startActivitySync(Intent(app,BrowserActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as BrowserActivity
        await(20_000){app.components.store.state.restoreComplete}
        selectedBefore=app.components.store.state.selectedTabId
        val page=onMain {app.components.tabsUseCases.addTab("about:blank",selectTab=true,contextId="profile_default").also{tabs.add(it)}}
        await(15_000){app.components.store.state.selectedTabId==page}
        onMain {
            val host=browser.supportFragmentManager.primaryNavigationFragment
            println("TESTLAB_FIXTURE_INITIAL fragment=${host?.childFragmentManager?.primaryNavigationFragment?.javaClass?.simpleName}")
            // An empty restored session navigates to ComposeHomeFragment. Adding a Store tab
            // alone does not navigate back, and that homepage has no Browser edge-panel listener.
            browser.openToBrowser(com.prirai.android.nira.BrowserDirection.FromGlobal)
        }
        await(20_000){onMain {
            val pageFragment=browser.supportFragmentManager.primaryNavigationFragment?.childFragmentManager?.primaryNavigationFragment
            pageFragment is com.prirai.android.nira.BrowserFragment &&
                pageFragment.view?.findViewById<com.prirai.android.nira.browser.SwipeGestureLayout>(com.prirai.android.nira.R.id.gestureLayout)?.let{it.isLaidOut&&it.isSwipeEnabled}==true
        }}
        onMain {browser.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
        // Warm-up belongs to the experiment, never to the 60-second report.
        SystemClock.sleep(3000)
    }
    @After fun cleanup() {
        onMain {
            TestLab.stop()
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                .filterIsInstance<TestLabActivity>().forEach{it.finish()}
            screen=null
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(browserLifecycle)
            paint?.running=false;(paint?.parent as? android.view.ViewGroup)?.removeView(paint);paint=null
            if(::browser.isInitialized)browser.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        await(15_000){!TestLab.busy}
        TestLab.reports.value.values.forEach { report ->
            println("TESTLAB_FIXTURE_RESULT type=${report.testType} phase=${report.status} durationMs=${report.durationMs} frames=${report.frameMetrics?.count} operations="+
                report.operationMetrics.map{"${it.operation}:${it.started}/${it.accepted}/${it.succeeded}/${it.cancelled}"})
        }
        group?.let{id->runBlocking {app.components.tabGroupManager.deleteGroup(id)}}
        onMain {
            tabs.forEach{app.components.tabsUseCases.removeTab(it)}
            selectedBefore?.takeIf{id->app.components.store.state.tabs.any{it.id==id}}?.let{app.components.tabsUseCases.selectTab(it)}

        }
        TestLab.deleteReport()
        val prefs=UserPreferences(app)
        prefs.firstLaunch=firstLaunch;prefs.defaultProfileMigrationDone=migrationDone
        prefs.pendingDefaultMigrationPopup=migrationPopup;prefs.niraEdgePanelsEnabled=edgeEnabled
        // Keep the root BrowserActivity alive: its intentional task-removal shutdown kills the process.
        // The instrumentation runner closes its isolated app at the end of the suite.
    }
    @Test fun fullAuditUiRunsFor120SecondsAndEarlyStopIsPartial() {
        try { runCurrentFullAudit() } catch(t:Throwable) {
            println("TESTLAB_FIXTURE_FAILURE stage=$stage type=${t.javaClass.simpleName}")
            t.printStackTrace()
            throw t
        }
    }
    private fun currentBrowserFragment():com.prirai.android.nira.BrowserFragment? =
        browser.supportFragmentManager.primaryNavigationFragment
            ?.childFragmentManager?.primaryNavigationFragment as? com.prirai.android.nira.BrowserFragment

    private fun panelVisible(tag:String):Boolean=onMain {
        currentBrowserFragment()?.childFragmentManager?.findFragmentByTag(tag)?.view
            ?.let { it.isShown && it.isLaidOut }==true
    }
    private fun closePanel(tag:String) {
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        await(15_000) { !panelVisible(tag) }
    }
    private fun runCurrentFullAudit() {
        val baseline=trial("OFF",3000)
        stage="FULL_AUDIT:start-ui"
        screen=instrumentation.startActivitySync(Intent(app,TestLabActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TestLabActivity
        clickUi("URUCHOM JEDEN TEST • 120 s")
        clickUi("Rozpocznij i wróć",scroll=false)
        screen=null
        stage="FULL_AUDIT:await-running"
        await(30_000){TestLab.ui.value.phase==LabPhase.RUNNING}
        assertEquals(TestType.FULL_AUDIT,TestLab.ui.value.type)
        assertTrue((TestLab.ui.value.remainingSeconds?:0) in 1..120)
        val counter=onMain{browser.window.decorView.findViewWithTag<TextView>(TestLabRuntime.COUNTER_TAG)}
        assertNotNull(counter);assertFalse(counter.isClickable);assertFalse(counter.isFocusable)

        // Exercise only isolated fixture tabs: never modify real user data.
        stage="FULL_AUDIT:tabs-and-group"
        onMain{repeat(10){tabs.add(app.components.tabsUseCases.addTab("about:blank",selectTab=true,contextId="profile_default"))}}
        group=runBlocking{withTimeout(15_000){
            app.components.tabGroupManager.awaitInitialized()
            app.components.tabGroupManager.createGroup(tabs.take(2),name="TESTLAB_PRIVATE_FIXTURE",contextId=null).id
        }}
        stage="FULL_AUDIT:embedded-left"
        swipeFromEdge(left=true)
        await(15_000){panelVisible("NiraEmbeddedSideTabs")}
        // Embedded content is not a DialogFragment and has no dialog Window.
        assertTrue(panelVisible("NiraEmbeddedSideTabs"))
        closePanel("NiraEmbeddedSideTabs")

        stage="FULL_AUDIT:embedded-right"
        swipeFromEdge(left=false)
        await(15_000){panelVisible("NiraEmbeddedSideBookmarks")}
        assertTrue(panelVisible("NiraEmbeddedSideBookmarks"))
        closePanel("NiraEmbeddedSideBookmarks")

        stage="FULL_AUDIT:rotation"
        onMain{TestLab.mark();browser.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
        await(15_000){onMain{browser.resources.configuration.orientation==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE}}
        onMain{browser.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
        await(15_000){onMain{browser.resources.configuration.orientation==
            android.content.res.Configuration.ORIENTATION_PORTRAIT}}
        assertEquals(LabPhase.RUNNING,TestLab.ui.value.phase)
        assertNotNull(onMain{browser.window.decorView.findViewWithTag<View>(TestLabRuntime.COUNTER_TAG)})

        // Ineligible foreground Activity creates a real coverage gap, not a synthetic metric.
        stage="FULL_AUDIT:coverage-gap"
        screen=instrumentation.startActivitySync(Intent(app,TestLabActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TestLabActivity
        SystemClock.sleep(1200)
        onMain{screen?.finish();screen=null}
        stage="FULL_AUDIT:await-completed"
        await(150_000){TestLab.ui.value.phase==LabPhase.COMPLETED}
        await(15_000){!TestLab.busy}
        val full=TestLab.reports.value[TestType.FULL_AUDIT]?:error("No FULL_AUDIT report")
        assertEquals(LabPhase.COMPLETED,full.status)
        assertEquals(120_000.0,full.durationMs,0.0)
        val installed=app.packageManager.getPackageInfo(app.packageName,0)
        val expectedCode=if(android.os.Build.VERSION.SDK_INT>=28)installed.longVersionCode.toInt() else installed.versionCode
        assertEquals(expectedCode,full.appVersion.versionCode)
        assertEquals(com.prirai.android.nira.BuildConfig.VERSION_NAME,full.appVersion.versionName)
        assertTrue(full.resourceMetrics.totalSamples>0)
        assertNotNull(full.resourceMetrics.javaHeapMaxBytes)
        assertNotNull(full.frameMetrics)
        assertTrue(full.frameMetrics!!.count>0)
        assertTrue(full.noCoverageMs>=500.0)
        assertTrue(full.anomalyMarkers.isNotEmpty())
        assertTrue(full.operationMetrics.first{it.operation==LabOperation.TAB_OPEN}.succeeded>=10)
        assertTrue(full.operationMetrics.first{it.operation==LabOperation.GROUP_CREATE}.succeeded>=1)
        assertTrue(full.operationMetrics.first{it.operation==LabOperation.LEFT_PANEL_OPEN}.succeeded>=1)
        assertTrue(full.operationMetrics.first{it.operation==LabOperation.RIGHT_PANEL_OPEN}.succeeded>=1)
        assertEquals(Availability.NOT_EXPOSED_BY_PLATFORM,full.metricAvailability[LabMetric.GECKO_COMPOSITOR_FRAMES])
        assertEquals(Availability.NOT_EXPOSED_BY_PLATFORM,full.metricAvailability[LabMetric.POPUP_WINDOW_FRAMES])
        assertNull(onMain{browser.window.decorView.findViewWithTag<View>(TestLabRuntime.COUNTER_TAG)})
        assertTrue(baseline.draws>0)
        val json=TestLabReportGenerator.generate(full).json
        assertFalse(json.contains("TESTLAB_PRIVATE_FIXTURE"))
        tabs.forEach{assertFalse(json.contains(it))}
        assertEquals(1,Json.parseToJsonElement(json).jsonObject.getValue("schemaVersion").jsonPrimitive.int)

        // Start via current UI again, then stop after RUNNING: PARTIAL is not COMPLETED.
        stage="FULL_AUDIT:early-stop"
        screen=instrumentation.startActivitySync(Intent(app,TestLabActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TestLabActivity
        clickUi("URUCHOM JEDEN TEST • 120 s")
        clickUi("Rozpocznij i wróć",scroll=false)
        screen=null
        await(20_000){TestLab.ui.value.phase==LabPhase.RUNNING}
        onMain{TestLab.stop()}
        await(15_000){!TestLab.busy}
        assertEquals(LabPhase.PARTIAL,TestLab.ui.value.phase)
        val partial=TestLab.reports.value[TestType.FULL_AUDIT]?:error("No PARTIAL report")
        assertEquals(LabPhase.PARTIAL,partial.status)
        assertTrue(partial.durationMs<120_000.0)
        assertNull(onMain{browser.window.decorView.findViewWithTag<View>(TestLabRuntime.COUNTER_TAG)})

        stage="FULL_AUDIT:report-controls"
        screen=instrumentation.startActivitySync(Intent(app,TestLabActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TestLabActivity
        clickUi("Otwórz jeden raport")
        assertNotNull(uiNode("Eksportuj JSON"))
        onMain{screen?.recreate()}
        clickUi("Kopiuj raport")
        val clip=app.getSystemService(android.content.ClipboardManager::class.java)
            .primaryClip?.getItemAt(0)?.text.toString()
        assertTrue(clip.contains("Zavro TestLab"))
        assertFalse(clip.contains("TESTLAB_PRIVATE_FIXTURE"))
    }
    /** Real accessibility actions keep production recomposers on Main and use real elapsed time. */
    private fun uiNode(text:String,scroll:Boolean=true):android.view.accessibility.AccessibilityNodeInfo {
        val until=SystemClock.elapsedRealtime()+20_000
        while(SystemClock.elapsedRealtime()<until) {
            val root=instrumentation.uiAutomation.rootInActiveWindow
            // Compose's virtual provider implements createNodeInfo/performAction, but not
            // findAccessibilityNodeInfosByText. Traverse its exposed children like UiAutomator.
            fun findText(node:android.view.accessibility.AccessibilityNodeInfo):android.view.accessibility.AccessibilityNodeInfo? {
                if(node.isVisibleToUser&&node.text?.toString()==text)return node
                for(i in 0 until node.childCount)node.getChild(i)?.let{child->findText(child)?.let{return it}}
                return null
            }
            val match=root?.let(::findText)
            if(match!=null)return match
            if(scroll&&root!=null) {
                fun scrollable(node:android.view.accessibility.AccessibilityNodeInfo):android.view.accessibility.AccessibilityNodeInfo? {
                    if(node.isScrollable)return node
                    for(i in 0 until node.childCount)node.getChild(i)?.let{child->scrollable(child)?.let{return it}}
                    return null
                }
                scrollable(root)?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
            SystemClock.sleep(150)
        }
        error("TestLab UI text unavailable: $text (stage=$stage)")
    }
    private fun clickUi(text:String,scroll:Boolean=true) {
        var node=uiNode(text,scroll)
        while(!node.isClickable)node=node.parent?:error("TestLab UI text has no clickable parent: $text")
        assertTrue(node.isEnabled)
        assertTrue(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun swipeFromEdge(left:Boolean) {
        val density=browser.resources.displayMetrics.density
        val bounds=onMain {
            val view=browser.findViewById<View>(com.prirai.android.nira.R.id.gestureLayout)
            val location=IntArray(2);view.getLocationOnScreen(location)
            intArrayOf(location[0],location[1],view.width,view.height)
        }
        val start=SystemClock.uptimeMillis()
        val x=if(left)bounds[0]+27*density else bounds[0]+bounds[2]-27*density
        val direction=if(left)1f else -1f
        val screenY=bounds[1]+bounds[3]*0.5f
        fun send(action:Int,atX:Float) {
            val pointer=MotionEvent.PointerProperties().apply{id=0;toolType=MotionEvent.TOOL_TYPE_FINGER}
            val coords=MotionEvent.PointerCoords().apply{this.x=atX;this.y=screenY;pressure=if(action==MotionEvent.ACTION_UP)0f else 1f;size=1f}
            val event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,1,arrayOf(pointer),arrayOf(coords),
                0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
            try{instrumentation.sendPointerSync(event)}finally{event.recycle()}
        }
        send(MotionEvent.ACTION_DOWN,x)
        repeat(20){i->
            send(MotionEvent.ACTION_MOVE,
                (x+direction*(i+1)*8*density).coerceIn(bounds[0]+20f,bounds[0]+bounds[2]-20f))
            SystemClock.sleep(12)
        }
        send(MotionEvent.ACTION_UP,
            (x+direction*160*density).coerceIn(bounds[0]+20f,bounds[0]+bounds[2]-20f))
    }
    private data class Trial(val draws:Long)
    private fun trial(label:String,ms:Long):Trial {
        val before=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()
        val native=Debug.getNativeHeapAllocatedSize();val cpu=Process.getElapsedCpuTime();val draw=paint!!.draws.get()
        val start=SystemClock.elapsedRealtimeNanos();SystemClock.sleep(ms)
        val elapsed=SystemClock.elapsedRealtimeNanos()-start
        val signal=CountDownLatch(1);val posted=SystemClock.elapsedRealtimeNanos();val delay=AtomicLong(-1)
        android.os.Handler(android.os.Looper.getMainLooper()).post{delay.set(SystemClock.elapsedRealtimeNanos()-posted);signal.countDown()}
        assertTrue(signal.await(5,TimeUnit.SECONDS))
        val draws=paint!!.draws.get()-draw
        println("TESTLAB_ANDROID_EMULATOR_ONLY $label wallNs=$elapsed cpuMs=${Process.getElapsedCpuTime()-cpu} javaDeltaBytes=${Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()-before} nativeDeltaBytes=${Debug.getNativeHeapAllocatedSize()-native} drawCallbacks=$draws mainProbeNs=${delay.get()}")
        return Trial(draws)
    }
    private fun await(timeoutMs:Long,condition:()->Boolean) {
        val until=SystemClock.elapsedRealtime()+timeoutMs
        while(SystemClock.elapsedRealtime()<until){if(condition())return;SystemClock.sleep(50)}
        assertTrue("Android condition timed out",condition())
    }
    private fun <T> onMain(block:()->T):T {
        val result=AtomicReference<T>();instrumentation.runOnMainSync{result.set(block())};return result.get()
    }
    private class WorkloadView(activity:Activity):View(activity) {
        val draws=AtomicLong();var running=true
        override fun onDraw(canvas:Canvas) {
            canvas.drawColor(if(draws.incrementAndGet()%2==0L)Color.DKGRAY else Color.GRAY)
            if(running)postInvalidateOnAnimation()
        }
    }
}
