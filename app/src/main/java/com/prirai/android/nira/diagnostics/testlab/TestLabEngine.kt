package com.prirai.android.nira.diagnostics.testlab

import kotlin.math.abs

/**
 * No Android objects, background jobs, persistence, messages or external callbacks.
 * A runtime owns collectors. Every writer must present the current session token.
 */
class TestLabEngine(
    private val clock:NanoClock,
    private val identity:LabIdentity,
    private val maxEvents:Int=1024,
) {
    init { require(maxEvents in 1..1024); require(LabOperation.entries.size<=63) }
    @Volatile var phase=LabPhase.IDLE; private set
    @Volatile private var lastReport:TestLabReport?=null
    @Volatile var activeOperationMask=0L; private set
    private var serial=0L
    private var token:SessionToken?=null
    private var type=TestType.COMPLEX
    private var environment=LabEnvironment()
    private var started:Long?=null
    private var endedOffset=0L
    private var targetStatus=LabPhase.COMPLETED
    private var isForeground=false
    private var covered=0L
    private var coveredStart=0L
    private var frames=FrameAccumulator()
    private var resources=ResourceAccumulator()
    private val counts=LongArray(LabCode.entries.size)
    private val errors=LongArray(LabError.entries.size)
    private val timeline=ArrayDeque<LabEvent>()
    private var eventSerial=0L
    private var evicted=0L
    private var rateWindow=0L
    private var rateCount=0
    private var operationSerial=0L
    private val operations=linkedMapOf<Long,ActiveOperation>()
    private val stats=arrayOfNulls<OperationStats>(LabOperation.entries.size)
    private var droppedOperations=0L
    private val markerTimes=ArrayList<Long>(32)
    private val observations=ArrayList<Observation>(32)
    private val unavailable=mutableMapOf<LabMetric,Availability>()
    private var heartbeatCount=0L
    private var heartbeatMax:Long?=null
    private var lastSlowFrameOffset=-1_000_000_000L
    private data class ActiveOperation(val operation:LabOperation,val offset:Long,var accepted:Boolean=false)

    @Synchronized fun start(testType:TestType):SessionToken? {
        if(phase in setOf(LabPhase.PREPARING,LabPhase.RUNNING,LabPhase.FINALIZING))return null
        token=SessionToken(++serial);type=testType;environment=LabEnvironment();started=null;endedOffset=0L
        covered=0;coveredStart=0;isForeground=false;frames=FrameAccumulator();resources=ResourceAccumulator()
        counts.fill(0);errors.fill(0);stats.fill(null);operations.clear();timeline.clear();markerTimes.clear()
        observations.clear();unavailable.clear();evicted=0;eventSerial=0;rateWindow=0;rateCount=0
        operationSerial=0;droppedOperations=0;heartbeatCount=0;heartbeatMax=null;activeOperationMask=0
        lastSlowFrameOffset=-1_000_000_000L
        phase=LabPhase.PREPARING
        return token
    }
    @Synchronized fun begin(session:SessionToken,device:LabEnvironment=LabEnvironment()):Boolean {
        if(token!=session||phase!=LabPhase.PREPARING)return false
        environment=device.copy(refreshRatesHz=device.refreshRatesHz.toList());started=clock.nowNanos();phase=LabPhase.RUNNING;return true
    }
    private fun offset():Long? = started?.let { start -> (clock.nowNanos()-start).takeIf{it>=0} }
    private fun writable(session:SessionToken):Long? {
        if(token!=session||phase!=LabPhase.RUNNING)return null
        return offset()?.takeIf{it<DURATION_NS}
    }
    @Synchronized fun elapsedNanos(session:SessionToken):Long? = if(token==session&&phase==LabPhase.RUNNING)offset()?.coerceAtMost(DURATION_NS) else null
    @Synchronized fun currentType():TestType=type
    @Synchronized fun foreground(session:SessionToken,visible:Boolean):Boolean {
        val at=writable(session)?:return false
        if(visible==isForeground)return true
        if(isForeground)covered+=at-coveredStart
        isForeground=visible
        if(visible)coveredStart=at
        append(at,if(visible)LabCode.FOREGROUND else LabCode.BACKGROUND)
        return true
    }
    private fun append(at:Long,code:LabCode,operation:LabOperation?=null,value:Double?=null,tags:List<LabOperation> = emptyList(),count:Int=1) {
        counts[code.ordinal]+=count
        val window=at/1_000_000_000L
        if(window!=rateWindow){rateWindow=window;rateCount=0}
        val budget=if(type==TestType.COMPLEX)64 else 128
        if(rateCount>=budget){evicted++;errors[LabError.EVENT_LIMIT.ordinal]++;return}
        rateCount++
        timeline.addLast(LabEvent(++eventSerial,at,code,operation,value,tags,count))
        if(timeline.size>maxEvents){timeline.removeFirst();evicted++}
    }
    @Synchronized fun note(session:SessionToken,code:LabCode,value:Double?=null,count:Int=1):Boolean {
        val at=writable(session)?:return false
        if(count !in 1..100_000 || value!=null&&(!value.isFinite()||kotlin.math.abs(value)>9_007_199_254_740_991.0)){errors[LabError.INVALID_SAMPLE.ordinal]++;return false}
        append(at,code,value=value,count=count);return true
    }
    @Synchronized fun error(session:SessionToken,error:LabError):Boolean {
        val at=writable(session)?:return false
        errors[error.ordinal]++;append(at,LabCode.COLLECTOR_ERROR);return true
    }
    @Synchronized fun metricUnavailable(session:SessionToken,metric:LabMetric,reason:Availability):Boolean {
        if(writable(session)==null||reason in setOf(Availability.MEASURED,Availability.MEASURED_WITH_GAPS))return false
        unavailable[metric]=reason;return true
    }
    private fun opStats(operation:LabOperation):OperationStats = stats[operation.ordinal]
        ?:OperationStats(operation).also{stats[operation.ordinal]=it}
    @Synchronized fun operation(session:SessionToken,operation:LabOperation):OperationToken? {
        val at=writable(session)?:return null
        if(!type.includes(operation.domain))return null
        opStats(operation).started++
        if(operations.size>=128){droppedOperations++;errors[LabError.OPERATION_LIMIT.ordinal]++;return null}
        val serial=++operationSerial
        operations[serial]=ActiveOperation(operation,at)
        append(at,LabCode.OP_START,operation)
        updateMask()
        return OperationToken(session.serial,serial)
    }
    @Synchronized fun accept(operation:OperationToken):Boolean {
        val session=token?:return false
        val at=writable(session)?:return false
        if(operation.session!=session.serial)return false
        val active=operations[operation.serial]?:return false
        if(active.accepted)return false
        active.accepted=true;opStats(active.operation).accepted++
        append(at,LabCode.OP_ACCEPT,active.operation);updateMask();return true
    }
    @Synchronized fun end(operation:OperationToken,outcome:OperationOutcome,proof:CompletionProof=CompletionProof.SOURCE_CALLBACK):Boolean {
        val session=token?:return false
        val at=writable(session)?:return false
        if(operation.session!=session.serial)return false
        val active=operations.remove(operation.serial)?:return false
        val valid=outcome!=OperationOutcome.SUCCESS||!active.operation.needsAccept||active.accepted
        val actual=if(valid)outcome else OperationOutcome.UNKNOWN
        if(!valid)errors[LabError.INSTRUMENTATION_PROTOCOL.ordinal]++
        opStats(active.operation).finish(actual,at-active.offset,proof)
        if(actual==OperationOutcome.TIMEOUT)errors[LabError.OPERATION_TIMEOUT.ordinal]++
        if(actual==OperationOutcome.FAILED)errors[LabError.OPERATION_FAILED.ordinal]++
        append(at,if(actual==OperationOutcome.CANCELLED)LabCode.OP_CANCEL else LabCode.OP_FINISH,active.operation)
        updateMask();return valid
    }
    private fun updateMask() {
        var mask=0L
        for(active in operations.values)if(active.accepted||!active.operation.needsAccept)
            mask=mask or (1L shl active.operation.ordinal)
        activeOperationMask=mask
    }
    @Synchronized fun frame(session:SessionToken,durationNanos:Long,isJank:Boolean,overrunNanos:Long?=null,tags:Long=0L):Boolean {
        return frameMeasured(session,durationNanos,isJank,overrunNanos?:0L,overrunNanos!=null,tags)
    }
    /** Primitive-only entry point for JankStats; ordinary frames allocate no TestLab record. */
    @Synchronized fun frameMeasured(session:SessionToken,durationNanos:Long,isJank:Boolean,overrunNanos:Long,hasOverrun:Boolean,tags:Long):Boolean {
        if(token!=session||phase!=LabPhase.RUNNING||!isForeground)return false
        val start=started?:return false
        val at=clock.nowNanos()-start
        if(at !in 0 until DURATION_NS)return false
        frames.addMeasured(durationNanos,isJank,overrunNanos,hasOverrun)
        if(durationNanos !in 1..DURATION_NS)return false
        var bits=tags
        while(bits!=0L){
            val bit=java.lang.Long.numberOfTrailingZeros(bits)
            if(bit<LabOperation.entries.size){
                val op=LabOperation.entries[bit]
                if(type.includes(op.domain))opStats(op).frame(durationNanos,isJank)
            }
            bits=bits and (bits-1)
        }
        if(isJank&&durationNanos>=100_000_000L&&at-lastSlowFrameOffset>=1_000_000_000L){
            lastSlowFrameOffset=at
            val tagged=LabOperation.entries.filter{tags and (1L shl it.ordinal)!=0L&&type.includes(it.domain)}
            append(at,LabCode.SLOW_FRAME,value=durationNanos/1_000_000.0,tags=tagged)
            observe(Observation(ObservationKind.OBSERVED_PERFORMANCE_ANOMALY,LabCode.SLOW_FRAME,at,durationNanos/1_000_000.0))
        }
        return true
    }
    @Synchronized fun resource(session:SessionToken,sample:ResourceSample):Boolean {
        val at=writable(session)?:return false
        if(sample.offsetNanos>at)return false
        resources.add(sample);return true
    }
    @Synchronized fun heartbeat(session:SessionToken,delayNanos:Long):Boolean {
        val at=writable(session)?:return false
        if(!isForeground||delayNanos !in 0..DURATION_NS)return false
        heartbeatCount++;heartbeatMax=maxOf(heartbeatMax?:delayNanos,delayNanos)
        if(delayNanos>=250_000_000L){
            append(at,LabCode.MAIN_STALL,value=delayNanos/1_000_000.0)
            observe(Observation(ObservationKind.OBSERVED_PERFORMANCE_ANOMALY,LabCode.MAIN_STALL,at,delayNanos/1_000_000.0))
        }
        return true
    }
    @Synchronized fun mark(session:SessionToken):Boolean {
        val at=writable(session)?:return false
        append(at,LabCode.USER_STUTTER)
        if(markerTimes.size<32)markerTimes.add(at)else errors[LabError.EVENT_LIMIT.ordinal]++
        return true
    }
    @Synchronized fun invariant(session:SessionToken,invariant:LabInvariant):Boolean {
        val at=writable(session)?:return false
        append(at,LabCode.INVARIANT_FAILURE)
        observe(Observation(ObservationKind.CONFIRMED_INVARIANT_FAILURE,LabCode.INVARIANT_FAILURE,at,invariant=invariant))
        return true
    }
    private fun observe(observation:Observation) {
        val same=observations.indexOfFirst{it.kind==observation.kind&&it.code==observation.code&&it.invariant==observation.invariant}
        if(same>=0) {
            if((observation.value?:0.0)>(observations[same].value?:0.0))observations[same]=observation
            return
        }
        if(observations.size<32){observations.add(observation);return}
        val worst=observations.indices.maxBy{observations[it].kind.ordinal}
        if(observation.kind.ordinal<observations[worst].kind.ordinal)observations[worst]=observation
    }
    @Synchronized fun poll(session:SessionToken):Boolean {
        if(token!=session||phase!=LabPhase.RUNNING||(offset()?:0)<DURATION_NS)return false
        closing(LabPhase.COMPLETED);return true
    }
    @Synchronized fun stop(session:SessionToken):Boolean {
        if(token!=session||phase!=LabPhase.RUNNING)return false
        closing(if((offset()?:0)>=DURATION_NS)LabPhase.COMPLETED else LabPhase.PARTIAL);return true
    }
    @Synchronized fun cancel(session:SessionToken):Boolean {
        if(token!=session||phase !in setOf(LabPhase.PREPARING,LabPhase.RUNNING))return false
        closing(LabPhase.CANCELLED);return true
    }
    @Synchronized fun fail(session:SessionToken):Boolean {
        if(token!=session||phase !in setOf(LabPhase.PREPARING,LabPhase.RUNNING))return false
        errors[LabError.COLLECTOR_FAILURE.ordinal]++;closing(LabPhase.FAILED);return true
    }
    private fun closing(status:LabPhase){
        endedOffset=(offset()?:0).coerceIn(0,DURATION_NS)
        if(isForeground)covered+=(endedOffset-coveredStart).coerceAtLeast(0)
        isForeground=false;targetStatus=status;activeOperationMask=0;phase=LabPhase.FINALIZING
    }
    /** Call only after collectors have been detached, outside their callbacks/on background. */
    @Synchronized fun complete(session:SessionToken):Boolean {
        if(token!=session||phase!=LabPhase.FINALIZING)return false
        val incomplete=operations.size
        for(op in operations.values)opStats(op.operation).interrupted++
        operations.clear()
        val frame=frames.snapshot();val resource=resources.snapshot()
        val coverage=LabOperation.entries.associateWith{operation->
            val s=stats[operation.ordinal]
            when{
                !type.includes(operation.domain)->Coverage.NOT_SELECTED
                s==null||s.started==0L->Coverage.NOT_TESTED
                s.succeeded>0->Coverage.OBSERVED_COMPLETION
                s.cancelled==s.started->Coverage.CANCELLED_ONLY
                else->Coverage.ATTEMPTED
            }
        }
        fun measured(metric:LabMetric,present:Boolean)=if(present){
            if(metric in unavailable)Availability.MEASURED_WITH_GAPS else Availability.MEASURED
        }else unavailable[metric]?:Availability.NOT_OBSERVED
        val availability=mapOf(
            LabMetric.ANDROID_UI_FRAMES to (if(frame==null&&frames.invalidCount>0)Availability.COLLECTOR_FAILED else measured(LabMetric.ANDROID_UI_FRAMES,frame!=null)),
            LabMetric.FRAME_OVERRUN to measured(LabMetric.FRAME_OVERRUN,(frame?.overrunSamples?:0)>0),
            LabMetric.JAVA_HEAP to measured(LabMetric.JAVA_HEAP,resource.javaHeapMaxBytes!=null),
            LabMetric.NATIVE_HEAP to measured(LabMetric.NATIVE_HEAP,resource.nativeHeapMaxBytes!=null),
            LabMetric.PROCESS_PSS to measured(LabMetric.PROCESS_PSS,resource.pssMaxBytes!=null),
            LabMetric.PROCESS_USS to measured(LabMetric.PROCESS_USS,resource.ussMaxBytes!=null),
            LabMetric.PROCESS_CPU to measured(LabMetric.PROCESS_CPU,resource.cpuMeanOneCorePercent!=null),
            LabMetric.MAIN_HEARTBEAT to measured(LabMetric.MAIN_HEARTBEAT,heartbeatCount>0),
        )+listOf(LabMetric.GECKO_COMPOSITOR_FRAMES,LabMetric.MPV_PRESENTED_FRAMES,LabMetric.APP_ENERGY,LabMetric.DUAL_AUDIO)
            .associateWith{Availability.NOT_EXPOSED_BY_PLATFORM}
        val measuredSources=mapOf(
            LabMetric.ANDROID_UI_FRAMES to MetricSource.JANKSTATS_WINDOW,
            LabMetric.FRAME_OVERRUN to MetricSource.JANKSTATS_API31,
            LabMetric.JAVA_HEAP to MetricSource.RUNTIME_HEAP,LabMetric.NATIVE_HEAP to MetricSource.DEBUG_NATIVE_HEAP,
            LabMetric.PROCESS_PSS to MetricSource.DEBUG_MEMORY_INFO,LabMetric.PROCESS_USS to MetricSource.DEBUG_MEMORY_INFO,
            LabMetric.PROCESS_CPU to MetricSource.PROCESS_CPU_TIME,LabMetric.MAIN_HEARTBEAT to MetricSource.MAIN_LOOPER_ACK,
        )
        val sources=LabMetric.entries.associateWith{measuredSources[it]?:MetricSource.UNAVAILABLE}
        val events=timeline.toList()
        val markers=markerTimes.map{time->AnomalyMarker(time,events.filter{abs(it.offsetNanos-time)<=3_000_000_000L}
            .sortedBy{abs(it.offsetNanos-time)}.take(32).sortedBy{it.offsetNanos}.map{it.serial})}
        val limitations=mutableListOf(Limitation.ANDROID_WINDOW_ONLY,Limitation.NO_GECKO_COMPOSITOR,
            Limitation.NO_MPV_PRESENTED_FRAMES,Limitation.MAIN_PROCESS_RESOURCES_ONLY,Limitation.NO_AUDIO_PATH_MEASUREMENT,
            Limitation.NO_PRECISE_ENERGY,Limitation.HISTOGRAM_PERCENTILES,Limitation.NO_CRASH_DOES_NOT_PROVE_STABILITY)
        if(covered<endedOffset)limitations.add(Limitation.COVERAGE_GAPS)
        if(evicted>0)limitations.add(Limitation.BOUNDED_TIMELINE)
        val finalErrors=errors.copyOf()
        finalErrors[LabError.INVALID_SAMPLE.ordinal]+=frames.invalidCount+resource.invalidFields+resource.outOfOrderSamples
        lastReport=TestLabReport(
            testType=type,appVersion=identity,durationMs=endedOffset/1_000_000.0,status=targetStatus,environment=environment,
            metricAvailability=availability,metricSources=sources,frameMetrics=frame,
            operationMetrics=LabOperation.entries.filter{type.includes(it.domain)}.map{stats[it.ordinal]?.snapshot()?:OperationStats(it).snapshot()},
            resourceMetrics=resource,errorCounts=LabError.entries.filter{finalErrors[it.ordinal]>0}.associateWith{finalErrors[it.ordinal]},
            eventCounts=LabCode.entries.filter{counts[it.ordinal]>0}.associateWith{counts[it.ordinal]},timeline=events,
            anomalyMarkers=markers,anomalies=observations.toList(),testCoverage=coverage,
            foregroundMs=covered/1_000_000.0,noCoverageMs=(endedOffset-covered).coerceAtLeast(0)/1_000_000.0,
            evictedEvents=evicted,droppedOperations=droppedOperations,incompleteOperations=incomplete,
            maxHeartbeatDelayMs=heartbeatMax?.div(1_000_000.0),heartbeatSamples=heartbeatCount,limitations=limitations,
        )
        phase=targetStatus;return true
    }
    @Synchronized internal fun recovery(session:SessionToken):TestLabRecovery? {
        if(token!=session||phase!=LabPhase.RUNNING)return null
        return TestLabRecovery(type,identity,phase,(offset()?:0).coerceIn(0,DURATION_NS)/1_000_000.0,
            LabError.entries.filter{errors[it.ordinal]>0}.associateWith{errors[it.ordinal]},timeline.takeLast(64))
    }
    fun latest():TestLabReport?=lastReport
    @Synchronized fun deleteReport(){lastReport=null}
    companion object {const val DURATION_NS=60_000_000_000L}
}
