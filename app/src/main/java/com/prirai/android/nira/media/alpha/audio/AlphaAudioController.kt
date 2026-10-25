package com.prirai.android.nira.media.alpha.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*

internal data class AlphaEqSetting(val gains:List<Int>,val boost:Int=0) {
    fun valid()=gains.size==5 && gains.all {it in -15..15} && boost in 0..10
    fun spec()=RexEqualizerAudio.specification(gains,boost)
    fun preset()=RexEqualizerAudio.presets.filter {it.second==gains}.joinToString(" / ") {it.first}.ifEmpty {"Custom"}
}
internal class AlphaAfFilter(val raw:JsonObject) {
    val label get()=(raw["label"] as? JsonPrimitive)?.contentOrNull
    fun eq():AlphaEqSetting? {
        if(label!=LABEL || raw["name"]!=JsonPrimitive("lavfi") || raw["enabled"]!=JsonPrimitive(true))return null
        val params=raw["params"] as? JsonObject?:return null
        if(params.size!=1)return null
        val graph=((params["graph"]?:params["@0"]) as? JsonPrimitive)?.contentOrNull?:return null
        val parts=graph.split(',')
        if(parts.size !in 5..6)return null
        val gains=RexEqualizerAudio.frequencies.mapIndexed { i,f ->
            val prefix="equalizer=f=$f:t=q:w=1:g="
            if(!parts[i].startsWith(prefix))return null
            parts[i].removePrefix(prefix).toIntOrNull()?:return null
        }
        val boost=if(parts.size==6) {
            Regex("volume=([0-9]+)dB").matchEntire(parts[5])?.groupValues?.get(1)?.toIntOrNull()?:return null
        } else 0
        return AlphaEqSetting(gains,boost).takeIf {it.valid()}
    }
    override fun toString()="AlphaAfFilter(<redacted>)"
    companion object {const val LABEL="rex_visual_eq"}
}
internal interface AlphaAudioBackend {
    fun read():List<AlphaAfFilter>?
    fun replace(spec:String):Boolean
    fun remove():Boolean
}
internal enum class AlphaAudioStatus { UNAVAILABLE, VERIFIED, REJECTED, CONFLICT, FAILED_ROLLED_BACK, ROLLBACK_UNCONFIRMED }
internal data class AlphaAudioState(
    val ready:Boolean=false,val consistent:Boolean=false,val eq:AlphaEqSetting?=null,
    val choice:AlphaEqSetting=AlphaEqSetting(List(5){0}),
    val externalCount:Int=0,val noiseExternal:Boolean=false,val voiceExternal:Boolean=false,
    val status:AlphaAudioStatus=AlphaAudioStatus.UNAVAILABLE,val lastFailure:AlphaAudioStatus?=null,
    val sent:Int=0,val accepted:Int=0,val rejected:Int=0,val errors:Int=0,val changes:Int=0,val conflicts:Int=0,
)

/** Session/main-thread controller. Never owns an engine or runs arbitrary Lua. */
internal class AlphaAudioController(
    private val canControl:()->Boolean,private val backend:AlphaAudioBackend,
    private val changed:(AlphaAudioState)->Unit={},
) {
    private val mutableState=MutableStateFlow(AlphaAudioState())
    val state=mutableState.asStateFlow()
    private var closed=false
    private var mutating=false
    private fun publish(value:AlphaAudioState) {mutableState.value=value;changed(value)}
    private fun read()=runCatching {backend.read()?.takeIf {it.size<=64}}.getOrNull()
    private fun own(chain:List<AlphaAfFilter>)=chain.filter {it.label==AlphaAfFilter.LABEL}
    private fun foreign(chain:List<AlphaAfFilter>)=chain.filter {it.label!=AlphaAfFilter.LABEL}.map {it.raw}
    private fun same(a:List<AlphaAfFilter>?,b:List<AlphaAfFilter>?)=a!=null && b!=null && a.map {it.raw}==b.map {it.raw}
    private fun observe(chain:List<AlphaAfFilter>?,status:AlphaAudioStatus?=null) {
        val old=state.value
        if(chain==null) {publish(old.copy(ready=false,consistent=false,eq=null,status=status?:AlphaAudioStatus.UNAVAILABLE));return}
        val mine=own(chain)
        val eq=mine.singleOrNull()?.eq()
        val conflict=mine.size>1 || (mine.size==1 && eq==null)
        publish(old.copy(ready=!closed && canControl(),consistent=!conflict,eq=eq,
            choice=eq?:old.choice,externalCount=chain.size-mine.size,
            noiseExternal=chain.any {it.label=="rex_szum"},voiceExternal=chain.any {it.label=="rex_glos"},
            status=status?:if(conflict)AlphaAudioStatus.CONFLICT else AlphaAudioStatus.VERIFIED))
    }
    fun refresh() {
        if(closed || mutating)return
        if(!canControl()) {observe(null);return}
        observe(read())
    }
    fun preset(name:String):Boolean {
        if(closed)return false
        val gains=RexEqualizerAudio.presets.firstOrNull {it.first==name}?.second
        if(gains==null) {publish(state.value.copy(rejected=state.value.rejected+1,status=AlphaAudioStatus.REJECTED,lastFailure=AlphaAudioStatus.REJECTED));return false}
        return apply(AlphaEqSetting(gains,state.value.choice.boost))
    }
    /** null means OFF; all parameters validated before any backend mutation. */
    fun apply(target:AlphaEqSetting?):Boolean {
        if(closed)return false
        if(mutating || !canControl() || target?.valid()==false) {
            publish(state.value.copy(rejected=state.value.rejected+1,status=AlphaAudioStatus.REJECTED,lastFailure=AlphaAudioStatus.REJECTED));return false
        }
        val before=read()
        if(before==null) {observe(null);publish(state.value.copy(rejected=state.value.rejected+1));return false}
        val previous=own(before)
        if(previous.size>1 || (previous.size==1 && previous.single().eq()==null)) {
            observe(before,AlphaAudioStatus.CONFLICT)
            publish(state.value.copy(rejected=state.value.rejected+1,conflicts=state.value.conflicts+1,lastFailure=AlphaAudioStatus.CONFLICT));return false
        }
        val old=previous.singleOrNull()?.eq()
        if(old==target) {observe(before);return true}
        mutating=true
        try {
            val result=command(target)
            val after=read()
            val mine=after?.let(::own)
            val verified=after!=null && foreign(after)==foreign(before) &&
                if(target==null)mine!!.isEmpty() else mine!!.size==1 && mine.single().eq()==target &&
                    after.indexOf(mine.single())==(if(previous.isEmpty())before.size else before.indexOf(previous.single()))
            if(result && verified) {
                observe(after,AlphaAudioStatus.VERIFIED)
                publish(state.value.copy(accepted=state.value.accepted+1,changes=state.value.changes+1,lastFailure=null));return true
            }
            // Rollback only our label, and only if nobody replaced its current value.
            // Never overwrite the whole AF chain or undo another writer's filters.
            val foreignChanged=after==null || foreign(after)!=foreign(before)
            var restored=same(after,before)
            val oursStillExpected=after!=null && (if(target==null)own(after).isEmpty()
                else own(after).size==1 && own(after).single().eq()==target)
            if(!restored && oursStillExpected) {
                command(old)
                restored=same(read(),before)
            }
            observe(read(),if(restored)AlphaAudioStatus.FAILED_ROLLED_BACK else AlphaAudioStatus.ROLLBACK_UNCONFIRMED)
            publish(state.value.copy(errors=state.value.errors+1,rejected=state.value.rejected+1,
                conflicts=state.value.conflicts+if(foreignChanged || !restored)1 else 0,
                consistent=restored && state.value.consistent,
                lastFailure=if(restored)AlphaAudioStatus.FAILED_ROLLED_BACK else AlphaAudioStatus.ROLLBACK_UNCONFIRMED))
            return false
        } finally {mutating=false}
    }
    private fun command(target:AlphaEqSetting?):Boolean {
        publish(state.value.copy(sent=state.value.sent+1))
        return runCatching {if(target==null)backend.remove() else backend.replace(target.spec())}.getOrDefault(false)
    }
    fun close() {closed=true;publish(state.value.copy(ready=false,consistent=false,eq=null,choice=AlphaEqSetting(List(5){0})))}
}
