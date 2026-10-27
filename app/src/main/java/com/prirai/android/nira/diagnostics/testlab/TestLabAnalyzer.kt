package com.prirai.android.nira.diagnostics.testlab

/** Fixed, documented heuristics. No score, automatic repairs, ANR or leak verdicts. */
internal object TestLabAnalyzer {
    fun analyze(report:TestLabReport):TestLabReport {
        val findings=report.anomalies.toMutableList()
        for(op in report.operationMetrics) {
            val latency=op.latency // Cancellations and unknown origins have no success latency.
            val threshold=when(op.operation.domain) {
                LabDomain.GECKO->5000.0
                LabDomain.VIDEO->3000.0
                LabDomain.TABS->1000.0
                else->250.0
            }
            if(latency!=null&&latency.maxMs>threshold)findings.add(Observation(ObservationKind.OBSERVED_PERFORMANCE_ANOMALY,
                LabCode.OP_FINISH,null,latency.maxMs,operation=op.operation,unit=LabUnit.MILLISECONDS,
                threshold=threshold,evidenceCount=latency.samples))
            if((op.maxTaggedFrameMs?:0.0)>=100.0)findings.add(Observation(ObservationKind.SUSPECTED_CORRELATION,
                LabCode.SLOW_FRAME,null,op.maxTaggedFrameMs,operation=op.operation,unit=LabUnit.MILLISECONDS,
                threshold=100.0,evidenceCount=op.taggedFrames))
        }
        fun counted(code:LabCode) {
            val count=report.eventCounts[code]?:return
            if(count>0)findings.add(Observation(ObservationKind.SUSPECTED_CORRELATION,code,
                report.timeline.lastOrNull{it.code==code}?.offsetNanos,count.toDouble(),unit=LabUnit.COUNT,
                threshold=1.0,evidenceCount=count))
        }
        listOf(LabCode.ACK_FAILED,LabCode.ACK_TIMEOUT,LabCode.ROLLBACK,LabCode.MPV_UNEXPECTED_RELOAD,
            LabCode.DETECTOR_REJECTED,LabCode.DETECTOR_QUEUE_LIMIT,LabCode.MPV_HTTP_ERROR,LabCode.MPV_TLS_ERROR,
            LabCode.LUA_RUNTIME_ERROR,LabCode.LUA_ACK_TIMEOUT,LabCode.DSP_TIMEOUT,LabCode.DSP_CONFIG_ERROR).forEach(::counted)
        val memory=report.resourceMetrics
        fun growth(code:LabCode,delta:Long?) {
            if(delta!=null&&delta>=16L*1024*1024)findings.add(Observation(ObservationKind.SUSPECTED_CORRELATION,
                code,null,delta.toDouble(),unit=LabUnit.BYTES,threshold=16.0*1024*1024,evidenceCount=memory.totalSamples))
        }
        growth(LabCode.JAVA_HEAP_GROWTH,memory.javaHeapDeltaBytes)
        growth(LabCode.NATIVE_HEAP_GROWTH,memory.nativeHeapDeltaBytes)
        growth(LabCode.PSS_GROWTH,memory.pssDeltaBytes)
        // Different units are never compared as one arbitrary health score.
        val ordered=findings.sortedWith(compareBy<Observation>{it.kind.ordinal}.thenBy{it.offsetNanos?:Long.MAX_VALUE})
            .distinctBy{listOf(it.kind,it.code,it.operation,it.invariant)}.take(32)
        return report.copy(anomalies=ordered)
    }
}
