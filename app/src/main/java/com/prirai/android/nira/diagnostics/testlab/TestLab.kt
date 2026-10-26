package com.prirai.android.nira.diagnostics.testlab

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.Window
import com.prirai.android.nira.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Inactive hooks perform a volatile read and return. No startup registration or lazy browser init. */
internal object TestLab {
    @Volatile private var runtime:TestLabRuntime?=null
    private var engine:TestLabEngine?=null
    private var recoveryStore:TestLabRecoveryStore?=null
    fun recovery(app:Application):TestLabRecoveryStore = recoveryStore?:TestLabRecoveryStore(app).also{recoveryStore=it}
    private val mutableUi=MutableStateFlow(LabUiState())
    val ui:StateFlow<LabUiState> = mutableUi
    @Volatile var busy=false;private set
    fun start(app:Application,type:TestType):Boolean {
        check(Looper.myLooper()==Looper.getMainLooper())
        if(!DiagnosticSessionGate.shared.acquire(DiagnosticOwner.TESTLAB))return false
        busy=true
        val core=engine?:run {
            val info=app.packageManager.getPackageInfo(app.packageName,0)
            val version=if(android.os.Build.VERSION.SDK_INT>=28)info.longVersionCode.toInt() else info.versionCode
            TestLabEngine({SystemClock.elapsedRealtimeNanos()},LabIdentity(version,BuildConfig.VERSION_NAME)).also{engine=it}
        }
        val owner=TestLabRuntime(app,core,recovery=recovery(app))
        runtime=owner
        if(!owner.prepare(type)) {runtime=null;busy=false;DiagnosticSessionGate.shared.release(DiagnosticOwner.TESTLAB);return false}
        val bridge=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        bridge.launch {
            owner.ui.collect {state->
                mutableUi.value=state
                if(state.phase in setOf(LabPhase.COMPLETED,LabPhase.PARTIAL,LabPhase.CANCELLED,LabPhase.FAILED)) {
                    if(runtime===owner){runtime=null;busy=false;DiagnosticSessionGate.shared.release(DiagnosticOwner.TESTLAB)}
                    bridge.cancel()
                }
            }
        }
        return true
    }
    fun capture():LabCapture?=runtime?.capture
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
    fun deleteReport(){if(!busy){engine?.deleteReport();mutableUi.value=mutableUi.value.copy(report=null)}}
}
