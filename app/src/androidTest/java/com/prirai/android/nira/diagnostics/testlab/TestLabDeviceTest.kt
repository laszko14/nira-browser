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
    @Test fun allSevenUiModesUseRealCollectorsOneCompletesSixtySecondsAndOthersStopPartially() {
        try { runSevenModes() } catch(t:Throwable) {
            // Preserve the original scenario failure if a test-rule teardown also fails.
            println("TESTLAB_FIXTURE_FAILURE stage=$stage type=${t.javaClass.simpleName}")
            t.printStackTrace()
            throw t
        }
    }
    private fun runSevenModes() {
        val baseline=trial("OFF",5000)
        val results=mutableMapOf<TestType,TestLabReport>()
        for(type in TestType.entries) {
            stage="${type.name}:start-ui"
            screen=instrumentation.startActivitySync(Intent(app,TestLabActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TestLabActivity
            clickUi("${type.labTitle()} — 60 s")
            clickUi("Rozpocznij i wróć",scroll=false)
            screen=null
            stage="${type.name}:await-running"
            await(30_000){TestLab.ui.value.phase==LabPhase.RUNNING}
            assertEquals(type,TestLab.ui.value.type)
            val counter=onMain {browser.window.decorView.findViewWithTag<TextView>(TestLabRuntime.COUNTER_TAG)}
            assertNotNull(counter);assertFalse(counter.isClickable);assertFalse(counter.isFocusable)
            if(type==TestType.COMPLEX) {
                stage="COMPLEX:tabs-room"
                onMain {repeat(10){tabs.add(app.components.tabsUseCases.addTab("about:blank",selectTab=true,contextId="profile_default"))}}
                group=runBlocking {withTimeout(15_000){
                    app.components.tabGroupManager.awaitInitialized()
                    app.components.tabGroupManager.createGroup(tabs.take(2),name="TESTLAB_PRIVATE_FIXTURE",contextId=null).id
                }}
                stage="COMPLEX:left-panel"
                swipeFromLeftEdge()
                await(10_000){onMain {
                    val nav=browser.supportFragmentManager.primaryNavigationFragment
                    val panel=nav?.childFragmentManager?.findFragmentByTag("NiraSideTabs") as? androidx.fragment.app.DialogFragment
                    panel?.dialog?.window?.decorView?.let{it.isShown&&it.isLaidOut}==true
                }}
                // The final report below must contain FIRST_PREDRAW completion, not merely show().
                onMain {
                    val nav=browser.supportFragmentManager.primaryNavigationFragment
                    nav?.childFragmentManager?.findFragmentByTag("NiraSideTabs")?.let{(it as? androidx.fragment.app.DialogFragment)?.dismiss()}
                }
                onMain {TestLab.mark();browser.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
                stage="COMPLEX:rotation-gap"
                SystemClock.sleep(500)
                onMain {browser.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
                // A real ineligible Activity creates a coverage gap, without sending a new Browser intent.
                screen=instrumentation.startActivitySync(Intent(app,TestLabActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TestLabActivity
                SystemClock.sleep(1000)
                onMain{screen?.finish();screen=null}
                stage="COMPLEX:await-completed"
                await(75_000){TestLab.ui.value.phase==LabPhase.COMPLETED}
            } else {
                if(type==TestType.RESOURCES)trial("ON_RESOURCES",5000) else SystemClock.sleep(1200)
                onMain{TestLab.stop()}
                await(15_000){!TestLab.busy}
                assertEquals(LabPhase.PARTIAL,TestLab.ui.value.phase)
            }
            stage="${type.name}:report"
            val report=TestLab.reports.value[type]?:error("No report for completed mode")
            results[type]=report
            assertTrue(report.resourceMetrics.totalSamples>0)
            assertNotNull(report.resourceMetrics.javaHeapMaxBytes)
            assertNotNull(report.frameMetrics)
            assertTrue(report.frameMetrics!!.count>0)
            assertEquals(Availability.NOT_EXPOSED_BY_PLATFORM,report.metricAvailability[LabMetric.GECKO_COMPOSITOR_FRAMES])
            assertEquals(Availability.NOT_EXPOSED_BY_PLATFORM,report.metricAvailability[LabMetric.GECKO_COMPOSITOR_FRAMES])
            assertNull(onMain{browser.window.decorView.findViewWithTag<View>(TestLabRuntime.COUNTER_TAG)})
            val json=TestLabReportGenerator.generate(report).json
            assertFalse(json.contains("TESTLAB_PRIVATE_FIXTURE"));tabs.forEach{assertFalse(json.contains(it))}
            val root=Json.parseToJsonElement(json).jsonObject
            assertEquals(1,root.getValue("schemaVersion").jsonPrimitive.int)
        }
        stage="final-assertions"
        assertEquals(7,results.size)
        val full=results.getValue(TestType.COMPLEX)
        assertEquals(60_000.0,full.durationMs,0.0)
        assertTrue(full.noCoverageMs>=500.0)
        assertTrue(full.anomalyMarkers.isNotEmpty())
        assertTrue(full.operationMetrics.first{it.operation==LabOperation.TAB_OPEN}.succeeded>=10)
        assertTrue(full.operationMetrics.first{it.operation==LabOperation.GROUP_CREATE}.succeeded>=1)
        val panel=full.operationMetrics.first{it.operation==LabOperation.LEFT_PANEL_OPEN}
        assertTrue(panel.succeeded>=1);assertTrue((panel.completionProofs[CompletionProof.FIRST_PREDRAW]?:0)>0)
        assertTrue(full.frameMetrics!!.cpuSamples>0)
        assertNotNull(full.resourceMetrics.cpuMeanOneCorePercent)
        assertEquals(Availability.MEASURED,results.getValue(TestType.STABILITY).metricAvailability[LabMetric.HISTORICAL_EXITS])
        assertTrue(baseline.draws>0)
        // Export controls and a configuration recreation operate on the completed RAM report.
        screen=instrumentation.startActivitySync(Intent(app,TestLabActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TestLabActivity
        clickUi("Otwórz raport")
        assertNotNull(uiNode("Eksportuj JSON"))
        onMain{screen?.recreate()}
        clickUi("Kopiuj raport")
        val clip=app.getSystemService(android.content.ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text.toString()
        assertTrue(clip.contains("Zavro TestLab"));assertFalse(clip.contains("TESTLAB_PRIVATE_FIXTURE"))
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
    private fun swipeFromLeftEdge() {
        val density=browser.resources.displayMetrics.density
        val bounds=onMain {
            val view=browser.findViewById<View>(com.prirai.android.nira.R.id.gestureLayout)
            val location=IntArray(2);view.getLocationOnScreen(location)
            intArrayOf(location[0],location[1],view.width,view.height)
        }
        // Inside the app's 36dp edge, beyond Android's typical system-back strip.
        val start=SystemClock.uptimeMillis();val x=bounds[0]+27*density;val screenY=bounds[1]+bounds[3]*0.5f
        fun send(action:Int,atX:Float) {
            val pointer=MotionEvent.PointerProperties().apply{id=0;toolType=MotionEvent.TOOL_TYPE_FINGER}
            val coords=MotionEvent.PointerCoords().apply{this.x=atX;this.y=screenY;pressure=if(action==MotionEvent.ACTION_UP)0f else 1f;size=1f}
            val event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,1,arrayOf(pointer),arrayOf(coords),
                0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
            try{instrumentation.sendPointerSync(event)}finally{event.recycle()}
        }
        send(MotionEvent.ACTION_DOWN,x)
        repeat(20){i->send(MotionEvent.ACTION_MOVE,(x+(i+1)*8*density).coerceAtMost(bounds[0]+bounds[2]-20f));SystemClock.sleep(12)}
        send(MotionEvent.ACTION_UP,(x+160*density).coerceAtMost(bounds[0]+bounds[2]-20f))
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
