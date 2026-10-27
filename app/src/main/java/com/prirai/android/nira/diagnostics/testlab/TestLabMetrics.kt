package com.prirai.android.nira.diagnostics.testlab

import kotlinx.serialization.Serializable

/** Only scalars and safe enum values cross into report storage. All times are relative. */
@Serializable
data class FrameSummary(
    val count: Long, val jankCount: Long, val jankPercent: Double,
    val meanMs: Double, val maxMs: Double,
    val p50Ms: Double?, val p90Ms: Double?, val p95Ms: Double?, val p99Ms: Double?,
    val meanOverrunMs: Double?, val maxOverrunMs: Double?, val overrunSamples: Long,
    val overflowCount: Long, val invalidSamples: Long,
    val percentileMethod: PercentileMethod = PercentileMethod.HISTOGRAM_UPPER_BOUND_0_25_MS,
    val cpuSamples:Long=0,val meanCpuMs:Double?=null,val maxCpuMs:Double?=null,
)
@Serializable enum class PercentileMethod { HISTOGRAM_UPPER_BOUND_0_25_MS }

/** Fixed histogram across ALL frames; no object allocation, sorting or I/O per frame. */
class FrameAccumulator {
    private val histogram = LongArray(4096)
    private var count = 0L
    private var jank = 0L
    private var total = 0.0
    private var maximum = 0L
    private var cpuCount=0L;private var cpuTotal=0.0;private var cpuMaximum=0L
    private var overrunCount = 0L
    private var overrunTotal = 0.0
    private var overrunMaximum = Long.MIN_VALUE
    var invalidCount = 0L; private set

    fun add(durationNanos: Long, isJank: Boolean, overrunNanos: Long? = null) {
        addMeasured(durationNanos,isJank,overrunNanos?:0L,overrunNanos!=null)
    }
    fun addMeasured(durationNanos:Long,isJank:Boolean,overrunNanos:Long,hasOverrun:Boolean,cpuNanos:Long=0L,hasCpu:Boolean=false) {
        if (durationNanos !in 1L..60_000_000_000L) { invalidCount++; return }
        count++; if (isJank) jank++
        total += durationNanos
        maximum = maxOf(maximum, durationNanos)
        val bucket = ((durationNanos + STEP - 1) / STEP).coerceAtMost(4095).toInt()
        histogram[bucket]++
        if(hasCpu) {
            if(cpuNanos in 0L..60_000_000_000L){cpuCount++;cpuTotal+=cpuNanos;cpuMaximum=maxOf(cpuMaximum,cpuNanos)}
            else invalidCount++
        }
        if (hasOverrun) {
            if (overrunNanos in -60_000_000_000L..60_000_000_000L) {
                overrunCount++; overrunTotal += overrunNanos
                overrunMaximum = maxOf(overrunMaximum, overrunNanos)
            } else invalidCount++
        }
    }
    private fun percentile(percent: Int, minimumSamples: Int): Double? {
        if (count < minimumSamples) return null
        val rank = kotlin.math.ceil(count * percent / 100.0).toLong()
        var observed = 0L
        for (index in histogram.indices) {
            observed += histogram[index]
            if (observed >= rank) return if (index == 4095) null else index * STEP / 1_000_000.0
        }
        return null
    }
    fun snapshot(): FrameSummary? = if (count == 0L) null else FrameSummary(
        count,jank,jank*100.0/count,total/count/1_000_000.0,maximum/1_000_000.0,
        percentile(50,2),percentile(90,10),percentile(95,20),percentile(99,100),
        if (overrunCount>0) overrunTotal/overrunCount/1_000_000.0 else null,
        if (overrunCount>0) overrunMaximum/1_000_000.0 else null,
        overrunCount,histogram.last(),invalidCount,
        cpuSamples=cpuCount,meanCpuMs=if(cpuCount>0)cpuTotal/cpuCount/1_000_000.0 else null,
        maxCpuMs=if(cpuCount>0)cpuMaximum/1_000_000.0 else null,
    )
    private companion object { const val STEP = 250_000L }
}

@Serializable
data class ResourceSample(
    val offsetNanos: Long,
    val javaHeapBytes: Long? = null,
    val nativeHeapBytes: Long? = null,
    val pssBytes: Long? = null,
    val ussBytes: Long? = null,
    val cpuTimeMs: Long? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val thermalStatus: Int? = null,
    val powerSave: Boolean? = null,
    val openTabs: Int? = null,
    val activeSessions: Int? = null,
)
@Serializable
data class ResourceSummary(
    val samples: List<ResourceSample>, val totalSamples: Long, val evictedSamples: Long,
    val javaHeapDeltaBytes: Long?, val javaHeapMaxBytes: Long?,
    val nativeHeapMaxBytes: Long?, val pssMaxBytes: Long?, val ussMaxBytes: Long?,
    val cpuDeltaMs: Long?, val cpuObservedWallMs: Double?,
    val cpuMeanOneCorePercent: Double?, val cpuMaxOneCorePercent: Double?,
    val invalidFields: Long, val outOfOrderSamples: Long,
    val nativeHeapDeltaBytes:Long?=null,val pssDeltaBytes:Long?=null,val ussDeltaBytes:Long?=null,
)

/** Sample ring is bounded; first baseline and extrema are independent of ring eviction. */
class ResourceAccumulator(
    private val capacity: Int = 180,
    private val maxOffsetNanos: Long = TestLabEngine.DURATION_NS,
) {
    // A future timestamp is invalid input, not an out-of-order callback.
    init { require(maxOffsetNanos in TestLabEngine.DURATION_NS..TestLabEngine.FULL_DURATION_NS) }
    init { require(capacity in 1..180) }
    private val samples = ArrayDeque<ResourceSample>()
    private var total = 0L
    private var javaSamples = 0L
    private var firstJava: Long? = null
    private var lastJava: Long? = null
    private var javaMax: Long? = null
    private class Range {
        var maximum:Long?=null;private var first=0L;private var last=0L;private var count=0L
        fun add(value:Long?){if(value!=null){if(count++==0L)first=value;last=value;maximum=maxOf(maximum?:value,value)}}
        fun delta():Long?=if(count>=2)last-first else null
    }
    private val native=Range();private val pss=Range();private val uss=Range()
    private var lastCpu: Long? = null
    private var lastCpuOffset = 0L
    private var cpuMs = 0L
    private var cpuWallNanos = 0L
    private var cpuMax: Double? = null
    private var invalid = 0L
    private var outOfOrder = 0L
    private var lastOffset = -1L

    private fun nonNegative(value: Long?): Long? {
        if (value != null && value !in 0L..9_007_199_254_740_991L) { invalid++; return null }
        return value
    }
    private fun bounded(value: Int?, low: Int, high: Int): Int? {
        if (value != null && value !in low..high) { invalid++; return null }
        return value
    }
    fun add(input: ResourceSample) {
        if (input.offsetNanos !in 0L..maxOffsetNanos) {
            invalid++; return
        }
        if (input.offsetNanos <= lastOffset) {
            outOfOrder++; return
        }
        val sample = input.copy(
            javaHeapBytes=nonNegative(input.javaHeapBytes),nativeHeapBytes=nonNegative(input.nativeHeapBytes),
            pssBytes=nonNegative(input.pssBytes),ussBytes=nonNegative(input.ussBytes),cpuTimeMs=nonNegative(input.cpuTimeMs),
            batteryPercent=bounded(input.batteryPercent,0,100),thermalStatus=bounded(input.thermalStatus,0,6),
            openTabs=bounded(input.openTabs,0,1_000_000),activeSessions=bounded(input.activeSessions,0,1_000_000),
        )
        lastOffset=sample.offsetNanos;total++
        samples.addLast(sample)
        if(samples.size>capacity)samples.removeFirst()
        sample.javaHeapBytes?.let {
            javaSamples++
            if(firstJava==null)firstJava=it
            lastJava=it;javaMax=maxOf(javaMax?:it,it)
        }
        native.add(sample.nativeHeapBytes);pss.add(sample.pssBytes);uss.add(sample.ussBytes)
        val cpu=sample.cpuTimeMs
        if(cpu!=null) {
            val previous=lastCpu
            if(previous!=null) {
                val delta=cpu-previous
                val wall=sample.offsetNanos-lastCpuOffset
                if(delta>=0 && wall>0) {
                    cpuMs+=delta;cpuWallNanos+=wall
                    val percent=delta*100_000_000.0/wall
                    cpuMax=maxOf(cpuMax?:percent,percent)
                } else invalid++
            }
            lastCpu=cpu;lastCpuOffset=sample.offsetNanos
        }
    }
    fun snapshot() = ResourceSummary(
        samples.toList(),total,(total-capacity).coerceAtLeast(0),
        if(javaSamples>=2)lastJava!!-firstJava!! else null,
        javaMax,native.maximum,pss.maximum,uss.maximum,
        if(cpuWallNanos>0)cpuMs else null,
        if(cpuWallNanos>0)cpuWallNanos/1_000_000.0 else null,
        if(cpuWallNanos>0)cpuMs*100_000_000.0/cpuWallNanos else null,
        cpuMax,invalid,outOfOrder,
        nativeHeapDeltaBytes=native.delta(),pssDeltaBytes=pss.delta(),ussDeltaBytes=uss.delta(),
    )
}
