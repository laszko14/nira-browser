package com.prirai.android.nira.diagnostics.testlab

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.os.*
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.metrics.performance.FrameDataApi24
import androidx.metrics.performance.FrameDataApi31
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.media.ZavroMpvActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.IdentityHashMap

/** This owner exists only for one session; no persistent Activity/Window references survive cleanup. */
internal class TestLabRuntime(
    private val app: Application,
    val engine: TestLabEngine,
    private val eligible: (Activity)->Boolean = { it is BrowserActivity || it is ZavroMpvActivity },
    private val probesEnabled: Boolean = true,
    private val recovery: TestLabRecoveryStore? = null,
) : Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private var session: SessionToken? = null
    @Volatile var capture:LabCapture?=null;private set
    @Volatile var mediaObserver:TestLabMediaObserver?=null;private set
    @Volatile var browserObserver:TestLabBrowserObserver?=null;private set
    private var countdownAt: Long? = null
    private var cleaned = false
    private val activities = IdentityHashMap<Activity,Window>()
    private val windows = IdentityHashMap<Window,WindowProbe>()
    @Volatile private var coverageEpoch=0L
    private var lastOrientation: Int?=null
    private var nativeWindowCovered=false
    private var worker: Job?=null
    private var recoveryGeneration=0L
    private val panelOpeners=arrayOfNulls<OperationToken>(2)
    private val mutableUi = MutableStateFlow(LabUiState())
    val ui: StateFlow<LabUiState> = mutableUi
    val trackedWindowCount: Int get() = windows.size
    private data class PendingHeartbeat(val epoch:Long,val foregroundGeneration:Long,val postedNanos:Long)
    @Volatile private var heartbeatPending: PendingHeartbeat?=null
    private var lastRefresh:Double?=null
    private var lastKeyboard:Boolean?=null
    private data class WindowProbe(val counter:TextView,var jank:JankStats?=null,
        var attachListener:View.OnAttachStateChangeListener?=null,var lastMask:Long=0,
        var keyboardListener:android.view.ViewTreeObserver.OnGlobalLayoutListener?=null,
        @Volatile var attached:Boolean=true)

    fun prepare(type:TestType):Boolean {
        check(Looper.myLooper()==Looper.getMainLooper())
        session=engine.start(type)?:return false
        recoveryGeneration=recovery?.begin()?:0L
        app.registerActivityLifecycleCallbacks(this)
        app.registerComponentCallbacks(this)
        mutableUi.value=LabUiState(phase=engine.phase,type=type,countdownSeconds=3)
        main.post(tick)
        return true
    }
    fun stop() {
        val s=session?:return
        if(engine.phase==LabPhase.PREPARING)engine.cancel(s) else engine.stop(s)
        finishIfNeeded()
    }
    fun mark():Boolean=session?.let(engine::mark)?:false
    fun gestureOrigin():GestureOrigin?=session?.let{s->engine.elapsedNanos(s)?.let{GestureOrigin(s,it)}}
    fun panelGesture(side:PanelSide,origin:GestureOrigin?):OperationToken? {
        if(origin==null)return null
        return engine.operation(origin.session,side.openOperation,origin.offsetNanos).also{syncTags()}
    }
    fun queuePanel(side:PanelSide,op:OperationToken?) {
        if(op==null||op.session!=session?.serial)return
        panelOpeners[side.ordinal]?.let{engine.end(it,OperationOutcome.INTERRUPTED)}
        panelOpeners[side.ordinal]=op
    }
    fun panelOpening(side:PanelSide):OperationToken? {
        val pending=panelOpeners[side.ordinal];panelOpeners[side.ordinal]=null
        val op=pending?:session?.let{engine.operation(it,side.openOperation,measureLatency=false)}
        if(op!=null)accept(op)
        return op
    }
    fun panelClosing(side:PanelSide,knownStart:Boolean):OperationToken? {
        val op=session?.let{engine.operation(it,side.closeOperation,measureLatency=knownStart)}
        if(op!=null)accept(op)
        return op
    }
    fun operation(op:LabOperation):OperationToken?=session?.let {engine.operation(it,op).also{syncTags()}}
    fun accept(op:OperationToken)=engine.accept(op).also{syncTags()}
    fun end(op:OperationToken,outcome:OperationOutcome,proof:CompletionProof)=engine.end(op,outcome,proof).also{syncTags()}
    private fun syncTags() {
        if(Looper.myLooper()!=Looper.getMainLooper()){main.post{if(!cleaned)updateTags()}} else if(!cleaned)updateTags()
    }
    private fun updateTags() {
        val mask=engine.activeOperationMask
        windows.forEach{(window,probe)->
            if(mask!=probe.lastMask){
                val state=PerformanceMetricsState.getHolderForHierarchy(window.decorView).state
                for(op in LabOperation.entries) {
                    val bit=1L shl op.ordinal
                    if(mask and bit != probe.lastMask and bit) {
                        if(mask and bit!=0L)state?.putState(op.tag,"active") else state?.removeState(op.tag)
                    }
                }
                probe.lastMask=mask
            }
        }
    }
    fun note(code:LabCode,value:Double?=null,count:Int=1) {
        capture?.note(code,value,count)
    }
    fun invariant(value:LabInvariant) {session?.let {engine.invariant(it,value)}}
    fun invariant(token:OperationToken,value:LabInvariant) {engine.invariant(SessionToken(token.session),value)}

    private val tick=object:Runnable {
        override fun run() {
            val s=session?:return
            if(cleaned)return
            if(engine.phase==LabPhase.PREPARING) {
                if(activities.isNotEmpty()) {
                    val start=countdownAt?:SystemClock.elapsedRealtimeNanos().also{countdownAt=it}
                    val elapsed=SystemClock.elapsedRealtimeNanos()-start
                    if(elapsed>=3_000_000_000L) {
                        val rate=activities.keys.firstOrNull()?.window?.decorView?.display?.refreshRate?.toDouble()
                        val rates=rate?.takeIf{it.isFinite()&&it in 1.0..1000.0}?.let{listOf(it)}?:emptyList()
                        engine.begin(s,LabEnvironment(Build.VERSION.SDK_INT,Runtime.getRuntime().availableProcessors(),rates))
                        engine.foreground(s,true)
                        capture=LabCapture(engine,s,::syncTags)
                        browserObserver=TestLabBrowserObserver(capture!!)
                        if(engine.currentType().includes(LabDomain.VIDEO)||engine.currentType().includes(LabDomain.GECKO))mediaObserver=TestLabMediaObserver(capture!!)
                        if(Build.VERSION.SDK_INT<31)engine.metricUnavailable(s,LabMetric.FRAME_OVERRUN,Availability.NOT_SUPPORTED_BY_API)
                        activities.keys.forEach(::observeNativeWindow)
                        if(probesEnabled)startWorker(s)
                        windows.forEach{(window,probe)->attachJank(window,probe,s)}
                    }
                } else countdownAt=null
            }
            engine.poll(s)
            finishIfNeeded()
            if(cleaned)return
            publish()
            updateTags()
            val label=counterText()
            windows.forEach{(window,probe)->
                if(!android.text.TextUtils.equals(probe.counter.text,label))probe.counter.text=label
                val rate=window.decorView.display?.refreshRate?.toDouble()
                if(rate!=null&&rate.isFinite()&&rate in 1.0..1000.0&&rate!=lastRefresh){
                    lastRefresh=rate;engine.note(s,LabCode.REFRESH_RATE_CHANGED,rate)
                }
            }
            if(engine.phase!=LabPhase.PREPARING||activities.isNotEmpty())main.postDelayed(this,if(activitiesVisible)250L else 1000L)
        }
    }
    private fun counterText():String = if(engine.phase==LabPhase.PREPARING)"TESTLAB • START ${mutableUi.value.countdownSeconds?:3}"
        else "TESTLAB • ${labClock(mutableUi.value.remainingSeconds?:0)}"
    private fun publish() {
        val s=session?:return
        val now=SystemClock.elapsedRealtimeNanos()
        mutableUi.value=LabUiState(engine.phase,engine.currentType(),
            if(engine.phase==LabPhase.PREPARING)countdownAt?.let{((3_000_000_000L-(now-it)).coerceAtLeast(0)+999_999_999L).div(1_000_000_000L).toInt()}?:3 else null,
            engine.elapsedNanos(s)?.let{((TestLabEngine.DURATION_NS-it+999_999_999L)/1_000_000_000L).toInt()},engine.latest())
    }
    private fun startWorker(s:SessionToken) {
        worker=scope.launch {
            var iteration=0L
            if(engine.currentType().includes(LabDomain.STABILITY))collectHistoricalExits(s)
            while(isActive&&engine.phase==LabPhase.RUNNING) {
                if(engine.poll(s)) {withContext(Dispatchers.Main){finishIfNeeded()};break}
                // One outstanding main-loop ACK; an old window epoch cannot report a false stall.
                if(heartbeatPending==null&&activitiesVisible) {
                    val marker=PendingHeartbeat(coverageEpoch,engine.foregroundGeneration,SystemClock.elapsedRealtimeNanos());heartbeatPending=marker
                    main.post {
                        if(heartbeatPending===marker)heartbeatPending=null
                        if(activitiesVisible&&marker.epoch==coverageEpoch)engine.heartbeat(s,SystemClock.elapsedRealtimeNanos()-marker.postedNanos)
                    }
                }
                val pending=heartbeatPending
                if(pending!=null&&activitiesVisible&&pending.epoch==coverageEpoch) {
                    val age=SystemClock.elapsedRealtimeNanos()-pending.postedNanos
                    if(heartbeatPending===pending&&pending.epoch==coverageEpoch)engine.pendingHeartbeat(s,age,pending.foregroundGeneration)
                }
                try {
                    collectResources(s,iteration++)
                    if(recovery?.isDue(recoveryGeneration)==true)engine.recovery(s)?.let{recovery.save(recoveryGeneration,it)}
                } catch (_:RuntimeException) {engine.error(s,LabError.COLLECTOR_FAILURE)}
                delay(1000L)
            }
        }
    }
    @Volatile private var activitiesVisible=false
    private fun collectResources(s:SessionToken,iteration:Long) {
        if(engine.elapsedNanos(s)==null)return
        val runtime=Runtime.getRuntime()
        var pss:Long?=null;var uss:Long?=null
        if(iteration%(if(engine.currentType()==TestType.RESOURCES)2 else 5)==0L) {
            try {
                val info=Debug.MemoryInfo();Debug.getMemoryInfo(info)
                pss=info.totalPss.toLong()*1024L
                uss=(info.totalPrivateClean.toLong()+info.totalPrivateDirty.toLong())*1024L
            } catch (_:RuntimeException) {
                engine.metricUnavailable(s,LabMetric.PROCESS_PSS,Availability.COLLECTOR_FAILED)
                engine.metricUnavailable(s,LabMetric.PROCESS_USS,Availability.COLLECTOR_FAILED)
            }
        }
        var battery:Int?=null;var charging:Boolean?=null
        try {
            val data=app.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level=data?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1
            val scale=data?.getIntExtra(BatteryManager.EXTRA_SCALE,-1)?:-1
            if(level>=0&&scale>0)battery=(100L*level/scale).toInt()
            val status=data?.getIntExtra(BatteryManager.EXTRA_STATUS,-1)
            if(status!=null&&status!=-1)charging=status==BatteryManager.BATTERY_STATUS_CHARGING||status==BatteryManager.BATTERY_STATUS_FULL
        } catch (_:RuntimeException) {engine.error(s,LabError.COLLECTOR_FAILURE)}
        val power=app.getSystemService(PowerManager::class.java)
        val state=(app as? com.prirai.android.nira.BrowserApp)?.components?.store?.state
        val offset=engine.elapsedNanos(s)?:return
        engine.resource(s,ResourceSample(offset,javaHeapBytes=runtime.totalMemory()-runtime.freeMemory(),
            nativeHeapBytes=Debug.getNativeHeapAllocatedSize(),pssBytes=pss,ussBytes=uss,cpuTimeMs=Process.getElapsedCpuTime(),
            batteryPercent=battery,charging=charging,thermalStatus=if(Build.VERSION.SDK_INT>=29)power.currentThermalStatus else null,
            powerSave=power.isPowerSaveMode,openTabs=state?.tabs?.size,
            activeSessions=state?.tabs?.count{it.engineState.engineSession!=null}))
    }
    private fun collectHistoricalExits(s:SessionToken) {
        if(Build.VERSION.SDK_INT<30){engine.metricUnavailable(s,LabMetric.HISTORICAL_EXITS,Availability.NOT_SUPPORTED_BY_API);return}
        try {
            val exits=app.getSystemService(android.app.ActivityManager::class.java)
                .getHistoricalProcessExitReasons(app.packageName,0,8).filter{it.processName==app.packageName}
            engine.note(s,LabCode.PROCESS_EXIT_HISTORY_READ,exits.size.toDouble())
            exits.forEach {exit->
                    // Observation of historical reason only; never description, trace, pid or process name.
                    if(exit.reason in 0..32)engine.note(s,LabCode.PROCESS_EXIT,exit.reason.toDouble())
                    engine.note(s,LabCode.PROCESS_EXIT_STATUS,exit.status.toDouble())
                }
        } catch (_:RuntimeException){engine.error(s,LabError.COLLECTOR_FAILURE);engine.metricUnavailable(s,LabMetric.HISTORICAL_EXITS,Availability.COLLECTOR_FAILED)}
    }
    private fun attachJank(window:Window,probe:WindowProbe,s:SessionToken) {
        if(probe.jank!=null||engine.phase!=LabPhase.RUNNING)return
        if(!ViewCompat.isAttachedToWindow(window.decorView))return
        try {
            probe.jank=JankStats.createAndTrack(window) { frame ->
                // Never retain FrameData/states. No formatting, I/O or per-frame record object.
                if(probe.attached) {
                    var tags=0L;var i=0
                    while(i<frame.states.size) {
                        val state=frame.states[i++];var j=0
                        while(j<LabOperation.entries.size) {
                            if(state.key==LabOperation.entries[j].tag&&state.value=="active")tags=tags or (1L shl j)
                            j++
                        }
                    }
                    val hasOverrun=Build.VERSION.SDK_INT>=31&&frame is FrameDataApi31
                    engine.frameMeasured(s,frame.frameDurationUiNanos,frame.isJank,
                        if(hasOverrun)(frame as FrameDataApi31).frameOverrunNanos else 0L,hasOverrun,tags,
                        if(frame is FrameDataApi24)frame.frameDurationCpuNanos else 0L,frame is FrameDataApi24)
                }
            }
        } catch (_:RuntimeException) {engine.metricUnavailable(s,LabMetric.ANDROID_UI_FRAMES,Availability.COLLECTOR_FAILED)}
    }
    /** Called for actual Dialog windows too; callers must detach at onStop/onDestroyView. */
    fun attachWindow(window:Window) {
        if(cleaned||windows.containsKey(window))return
        val root=window.decorView as? ViewGroup?:return
        val counter=TextView(window.context).apply {
            tag=COUNTER_TAG;text=counterText();textSize=10f
            setTextColor(Color.WHITE);setBackgroundColor(0xB0202020.toInt())
            isClickable=false;isFocusable=false;isLongClickable=false
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(4,1,4,1)
        }
        // A passive View returns false on touch; no overlay window and no input interception.
        root.addView(counter,FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            val bar=ViewCompat.getRootWindowInsets(root)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top?:0
            topMargin=((bar-14*root.resources.displayMetrics.density)/2).toInt().coerceAtLeast(0)
        })
        val probe=WindowProbe(counter)
        windows[window]=probe
        val s=session?:return
        val listener=object:View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v:View){probe.attached=true;attachJank(window,probe,s)}
            override fun onViewDetachedFromWindow(v:View){probe.attached=false;probe.jank?.isTrackingEnabled=false;probe.jank=null}
        }
        probe.attachListener=listener;root.addOnAttachStateChangeListener(listener)
        val keyboard=android.view.ViewTreeObserver.OnGlobalLayoutListener {
            if(!root.hasWindowFocus())return@OnGlobalLayoutListener
            val visible=ViewCompat.getRootWindowInsets(root)?.isVisible(WindowInsetsCompat.Type.ime())?:return@OnGlobalLayoutListener
            if(visible!=lastKeyboard){lastKeyboard=visible;engine.note(s,if(visible)LabCode.KEYBOARD_SHOWN else LabCode.KEYBOARD_HIDDEN)}
        }
        probe.keyboardListener=keyboard;root.viewTreeObserver.addOnGlobalLayoutListener(keyboard)
        attachJank(window,probe,s)
    }
    fun detachWindow(window:Window) {
        val probe=windows.remove(window)?:return
        probe.attached=false;probe.jank?.isTrackingEnabled=false;probe.jank=null
        probe.attachListener?.let{window.decorView.removeOnAttachStateChangeListener(it)}
        probe.keyboardListener?.let{if(window.decorView.viewTreeObserver.isAlive)window.decorView.viewTreeObserver.removeOnGlobalLayoutListener(it)}
        val state=PerformanceMetricsState.getHolderForHierarchy(window.decorView).state
        LabOperation.entries.forEach {state?.removeState(it.tag)}
        (probe.counter.parent as? ViewGroup)?.removeView(probe.counter)
    }
    private fun finishIfNeeded() {
        val s=session?:return
        if(cleaned||engine.phase!=LabPhase.FINALIZING)return
        cleaned=true;worker?.cancel();main.removeCallbacksAndMessages(null)
        browserObserver?.close();browserObserver=null
        mediaObserver?.close();mediaObserver=null;capture=null
        app.unregisterActivityLifecycleCallbacks(this);app.unregisterComponentCallbacks(this)
        windows.keys.toList().forEach(::detachWindow);activities.clear();activitiesVisible=false
        heartbeatPending=null;countdownAt=null;panelOpeners.fill(null)
        publish()
        if(worker==null&&recovery==null){engine.complete(s);publish();scope.cancel()}
        else scope.launch(Dispatchers.Main.immediate) {
            worker?.join()
            withContext(Dispatchers.IO){recovery?.end(recoveryGeneration)}
            engine.complete(s);publish();scope.cancel()
        }
    }
    private fun observeNativeWindow(activity:Activity) {
        val capture=capture?:return
        if(activity !is ZavroMpvActivity){nativeWindowCovered=false;return}
        if(nativeWindowCovered)return
        val op=capture.operation(LabOperation.MPV_FULLSCREEN,measureLatency=false)?:return
        nativeWindowCovered=true
        androidx.core.view.OneShotPreDrawListener.add(activity.window.decorView){capture.end(op,proof=CompletionProof.FIRST_PREDRAW)}
    }
    override fun onActivityResumed(activity:Activity) {
        if(!eligible(activity)||cleaned)return
        if(engine.phase==LabPhase.PREPARING&&activities.isEmpty())countdownAt=SystemClock.elapsedRealtimeNanos()
        activities[activity]=activity.window;coverageEpoch++;activitiesVisible=true
        attachWindow(activity.window);session?.let{engine.foreground(it,true)}
        observeNativeWindow(activity)
        main.removeCallbacks(tick);main.post(tick)
        val orientation=activity.resources.configuration.orientation
        if(lastOrientation!=null&&lastOrientation!=orientation)note(LabCode.ORIENTATION_CHANGED,orientation.toDouble())
        lastOrientation=orientation
    }
    override fun onActivityPaused(activity:Activity) {
        activities.remove(activity)?.let(::detachWindow)
        if(activities.isEmpty()){coverageEpoch++;activitiesVisible=false;countdownAt=null;session?.let{engine.foreground(it,false)}}
    }
    override fun onActivityDestroyed(activity:Activity){onActivityPaused(activity)}
    override fun onActivityCreated(activity:Activity,state:Bundle?){}
    override fun onActivityStarted(activity:Activity){}
    override fun onActivityStopped(activity:Activity){onActivityPaused(activity)}
    override fun onActivitySaveInstanceState(activity:Activity,outState:Bundle){}
    override fun onConfigurationChanged(configuration:Configuration) {
        val orientation=configuration.orientation
        if(lastOrientation!=null&&lastOrientation!=orientation)note(LabCode.ORIENTATION_CHANGED,orientation.toDouble())
        lastOrientation=orientation
    }
    override fun onLowMemory(){note(LabCode.MEMORY_TRIM,80.0)}
    override fun onTrimMemory(level:Int){note(LabCode.MEMORY_TRIM,level.toDouble())}
    companion object {const val COUNTER_TAG="zavro_testlab_passive_counter"}
}

data class LabUiState(val phase:LabPhase=LabPhase.IDLE,val type:TestType?=null,val countdownSeconds:Int?=null,
    val remainingSeconds:Int?=null,val report:TestLabReport?=null)

internal fun LabCode.domain():LabDomain = when {
    name.startsWith("GECKO_")||name.startsWith("LOAD_")||name.startsWith("ENGINE_")||name.startsWith("EXTENSION_")||this==LabCode.SPA_NAVIGATION||this==LabCode.URL_CHANGE_IDLE->LabDomain.GECKO
    name.startsWith("HANDOFF_")||name.startsWith("LUA_")||name.startsWith("DSP_")||name.startsWith("DETECTOR_")||name.startsWith("MPV_")||name.startsWith("BLOB_")||name.startsWith("ACK_")||this in setOf(LabCode.PREPARE,LabCode.AUTO_OFFER,LabCode.ROLLBACK,LabCode.FRAME_ELEMENT_MISMATCH,LabCode.FRAME_LOOKUP_FAILED,LabCode.FRAME_UNSUPPORTED,LabCode.AUDIO_FOCUS_FAILED,LabCode.LUA_ERROR,LabCode.DSP_ERROR)->LabDomain.VIDEO
    name.startsWith("PROCESS_EXIT")||this==LabCode.STABILITY_ERROR->LabDomain.STABILITY
    else->LabDomain.UI
}
