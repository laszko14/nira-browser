package com.prirai.android.nira.diagnostics.testlab

/** Bound to one measurement. Deferred commits can never be relabelled as the next test. */
internal class LabCapture(private val engine:TestLabEngine,internal val session:SessionToken,
    private val changed:()->Unit={}) {
    fun operation(op:LabOperation,startOffsetNanos:Long?=null,measureLatency:Boolean=true):OperationToken? =
        engine.operation(session,op,startOffsetNanos,measureLatency).also{if(it!=null)changed()}
    fun end(op:OperationToken?,outcome:OperationOutcome=OperationOutcome.SUCCESS,proof:CompletionProof=CompletionProof.SOURCE_CALLBACK) {
        if(op!=null){engine.end(op,outcome,proof);changed()}
    }
    fun check(op:OperationToken?,correct:Boolean,invariant:LabInvariant){if(op!=null&&op.session==session.serial&&!correct)engine.invariant(session,invariant)}
    fun note(code:LabCode,value:Double?=null,count:Int=1){if(engine.currentType().includes(code.domain()))engine.note(session,code,value,count)}
    fun error(){engine.error(session,LabError.COLLECTOR_FAILURE)}
}
