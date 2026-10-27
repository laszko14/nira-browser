package com.prirai.android.nira.diagnostics.testlab

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.Window
import com.prirai.android.nira.BuildConfig
import com.prirai.android.nira.media.MediaEntry
import com.prirai.android.nira.media.MediaSessionBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Inactive hooks perform a volatile read and return. No startup registration or lazy browser init. */
internal enum class LabStartFailure { CONFLICT, INITIALIZATION }
internal object TestLab {
    @Volatile private var runtime:TestLabRuntime?=null
    private var engine:TestLabEngine?=null
    private var recoveryStore:TestLabRecoveryStore?=null
    fun recovery(app:Application):TestLabRecoveryStore = recoveryStore?:TestLabRecoveryStore(app).also{recoveryStore=it}
    private val mutableUi=MutableStateFlow(LabUiState())
    val ui:StateFlow<LabUiState> = mutableUi
    private val history=MutableStateFlow<Map<TestType,TestLabReport>>(emptyMap())
    val reports:StateFlow<Map<TestType,TestLabReport>> = history
    @Volatile var lastStartFailure:LabStartFailure?=null;private set
    @Volatile var busy=false;private set
    fun start(app:Application,type:TestType):Boolean {
        check(Looper.myLooper()==Looper.getMainLooper())
        if(!DiagnosticSessionGate.shared.acquire(DiagnosticOwner.TESTLAB)){lastStartFailure=LabStartFailure.CONFLICT;return false}
        busy=true;lastStartFailure=null
        var candidate:TestLabRuntime?=null
        try {
        val core=engine?:run {
            val info=app.packageManager.getPackageInfo(app.packageName,0)
            val version=if(android.os.Build.VERSION.SDK_INT>=28)info.longVersionCode.toInt() else info.versionCode
            TestLabEngine({SystemClock.elapsedRealtimeNanos()},LabIdentity(version,BuildConfig.VERSION_NAME)).also{engine=it}
        }
        val owner=TestLabRuntime(app,core,recovery=recovery(app))
        candidate=owner;runtime=owner
        check(owner.prepare(type)){"TestLab initialization failed"}
        val bridge=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        bridge.launch {
            owner.ui.collect {state->
                mutableUi.value=state
                if(state.phase in setOf(LabPhase.COMPLETED,LabPhase.PARTIAL,LabPhase.CANCELLED,LabPhase.FAILED)) {
                    val report=state.report
                    if(report!=null&&(report.status!=LabPhase.CANCELLED||report.durationMs>0))history.value=history.value+(report.testType to report)
                    if(runtime===owner){runtime=null;busy=false;DiagnosticSessionGate.shared.release(DiagnosticOwner.TESTLAB)}
                    bridge.cancel()
                }
            }
        }
        return true
        } catch (_:Exception) {
            runCatching{candidate?.stop()}
            runtime=null;busy=false;lastStartFailure=LabStartFailure.INITIALIZATION
            DiagnosticSessionGate.shared.release(DiagnosticOwner.TESTLAB)
            mutableUi.value=mutableUi.value.copy(phase=LabPhase.FAILED,countdownSeconds=null,remainingSeconds=null)
            return false
        }
    }
    fun capture():LabCapture?=runtime?.capture
    fun counterChange(code:LabCode,current:Int,previous:Int) {
        val capture=runtime?.capture?:return
        val delta=(current.toLong()-previous.toLong()).coerceAtLeast(0L)
        if(delta>0){capture.note(code,count=delta.coerceAtMost(100_000L).toInt());if(delta>100_000L)capture.error(LabError.EVENT_LIMIT)}
    }
    fun geckoError(owner:Any){runtime?.browserObserver?.error(owner)}
    fun discovery(code:String,count:Int){runtime?.mediaObserver?.discovery(code,count)}
    fun mediaObservationActive():Boolean=runtime?.mediaObserver!=null
    fun observedMedia(binding:MediaSessionBinding,entry:MediaEntry,verifiedEvidence:Boolean=false){
        runtime?.mediaObserver?.entry(binding,entry,verifiedEvidence)
    }
    fun mediaCandidate(){runtime?.mediaObserver?.candidate()}
    fun mediaFunnelConflict(kind:MediaFunnelConflict){runtime?.mediaObserver?.conflict(kind)}
    fun mediaDisplayed(binding:MediaSessionBinding,entry:MediaEntry){runtime?.mediaObserver?.displayed(binding,entry)}
    fun mediaNavigation(binding:MediaSessionBinding){runtime?.mediaObserver?.navigation(binding)}
    fun mediaRejectedTitle(binding:MediaSessionBinding,different:Boolean){runtime?.mediaObserver?.rejectedTitle(binding,different)}

    fun browserObserver():TestLabBrowserObserver?=runtime?.browserObserver
    fun stop(){runtime?.stop()}
    fun mark():Boolean=runtime?.mark()?:false
    fun gestureOrigin():GestureOrigin?=runtime?.gestureOrigin()
    fun panelGesture(side:PanelSide,origin:GestureOrigin?):OperationToken?=runtime?.panelGesture(side,origin)
    fun queuePanel(side:PanelSide,op:OperationToken?){runtime?.queuePanel(side,op)}
    fun panelOpening(side:PanelSide):OperationToken?=runtime?.panelOpening(side)
    fun panelClosing(side:PanelSide,knownStart:Boolean):OperationToken?=runtime?.panelClosing(side,knownStart)
    fun operation(op:LabOperation):OperationToken?=runtime?.operation(op)
    fun accept(token:OperationToken?){if(token!=null)runtime?.accept(token)}
    fun end(token:OperationToken?,outcome:OperationOutcome=OperationOutcome.SUCCESS,proof:CompletionProof=CompletionProof.SOURCE_CALLBACK){if(token!=null)runtime?.end(token,outcome,proof)}
    fun note(code:LabCode,value:Double?=null,count:Int=1){runtime?.note(code,value,count)}
    fun invariant(invariant:LabInvariant){runtime?.invariant(invariant)}
    fun invariant(token:OperationToken?,invariant:LabInvariant){if(token!=null)runtime?.invariant(token,invariant)}
    fun attachWindow(window:Window){runtime?.attachWindow(window)}
    fun detachWindow(window:Window){runtime?.detachWindow(window)}
    fun deleteReport(type:TestType?=null) {
        if(busy)return
        if(type==null||engine?.latest()?.testType==type){engine?.deleteReport();mutableUi.value=mutableUi.value.copy(report=null)}
        history.value=if(type==null)emptyMap() else history.value-type
    }
}
