package com.prirai.android.nira.diagnostics.testlab

import kotlinx.serialization.Serializable

@Serializable enum class CompletionProof { SOURCE_CALLBACK, FIRST_PREDRAW, ANIMATOR_END, REDUCER_COMMITTED, ROOM_COMMITTED, DATASTORE_COMMITTED }

/** Never called per frame except primitive counter updates. Histogram is allocated on first completion. */
internal class OperationStats(private val operation:LabOperation) {
    var started=0L;var accepted=0L;var succeeded=0L;var cancelled=0L;var failed=0L
    var timedOut=0L;var interrupted=0L;var unknown=0L
    private var histogram:LongArray?=null
    private var sum=0.0;private var maximum=0L
    private var frames=0L;private var jank=0L;private var maxFrame=0L
    private val proofs=LongArray(CompletionProof.entries.size)
    fun finish(outcome:OperationOutcome,nanos:Long,proof:CompletionProof){
        when(outcome){
            OperationOutcome.SUCCESS->{
                succeeded++;sum+=nanos;maximum=maxOf(maximum,nanos)
                val h=histogram?:LongArray(4096).also{histogram=it}
                h[((nanos+999_999)/1_000_000).coerceIn(0,4095).toInt()]++
                proofs[proof.ordinal]++
            }
            OperationOutcome.CANCELLED->cancelled++
            OperationOutcome.FAILED->failed++
            OperationOutcome.TIMEOUT->timedOut++
            OperationOutcome.INTERRUPTED->interrupted++
            OperationOutcome.UNKNOWN->unknown++
        }
    }
    fun frame(nanos:Long,isJank:Boolean){frames++;if(isJank)jank++;maxFrame=maxOf(maxFrame,nanos)}
    private fun p95():Double? {
        if(succeeded<20)return null
        val target=kotlin.math.ceil(succeeded*0.95).toLong();var total=0L
        histogram?.forEachIndexed{index,count->total+=count;if(total>=target)return if(index==4095)null else index.toDouble()}
        return null
    }
    fun snapshot()=OperationSummary(operation,started,accepted,succeeded,cancelled,failed,timedOut,interrupted,unknown,
        if(succeeded>0)LatencySummary(succeeded,sum/succeeded/1_000_000.0,maximum/1_000_000.0,p95())else null,
        frames,jank,if(frames>0)maxFrame/1_000_000.0 else null,
        CompletionProof.entries.filter{proofs[it.ordinal]>0}.associateWith{proofs[it.ordinal]})
}
