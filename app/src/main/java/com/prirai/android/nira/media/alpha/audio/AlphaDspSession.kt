package com.prirai.android.nira.media.alpha.audio

import com.prirai.android.nira.media.alpha.AlphaLuaStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import java.util.UUID

internal enum class AlphaDspResult {
    VERIFIED, MPV_REJECTED, UNSUPPORTED_FILTER, CAPABILITY_UNCONFIRMED, AUDIO_FORMAT,
    BAD_CONFIGURATION, CONFLICT, AF_READ_FAILED, AF_MISMATCH, LUA_EXCEPTION, TIMEOUT,
}
internal data class AlphaDspState(
    val drc:String="UNCONFIRMED",val de:String="UNCONFIRMED",val drcCount:Int=0,val deCount:Int=0,
    val drcAvailable:String="UNCONFIRMED",val deAvailable:String="UNCONFIRMED",val afRead:Boolean=false,
    val sent:Int=0,val commands:Int=0,val verified:Int=0,val rejected:Int=0,val conflicts:Int=0,
    val configurationErrors:Int=0,val rollbacks:Int=0,val rollbackUnconfirmed:Int=0,val timeouts:Int=0,
    val luaExceptions:Int=0,val bridgeOk:Int=0,val notify:Boolean=false,val completion:Long=0,val last:AlphaDspResult?=null,
)

/** Same session/tick/bridge; no new observer, engine, native lifecycle or filter writes. */
internal class AlphaDspSession(
    private val canRead:()->Boolean,private val mailbox:()->String?,
    private val chain:()->List<AlphaAfFilter>?,private val clock:()->Long,
    private val changed:(AlphaDspState)->Unit={},
) {
    private val mutable=MutableStateFlow(AlphaDspState())
    val state=mutable.asStateFlow()
    private data class Pending(val kind:String,val time:Long,var ack:Boolean=false)
    private val pending=linkedMapOf<String,Pending>()
    private var closed=false
    private fun publish(s:AlphaDspState) {mutable.value=s;changed(s)}
    fun prepare(kind:String):String? {
        if(closed || !canRead() || pending.size>=16 || kind !in setOf("drc","de"))return null
        val nonce=UUID.randomUUID().toString()
        pending[nonce]=Pending(kind,clock())
        publish(state.value.copy(sent=state.value.sent+1))
        return nonce
    }
    fun cancel(nonce:String) {pending.remove(nonce)}
    /** Passive callback of the existing CALL_ID/epoch ACK; does not alter bridge transport. */
    fun bridgeResult(nonce:String,result:AlphaLuaStatus) {
        val request=pending[nonce]?:return
        if(result==AlphaLuaStatus.OK) {
            request.ack=true;publish(state.value.copy(bridgeOk=state.value.bridgeOk+1))
        } else {
            pending.remove(nonce)
            val s=state.value
            publish(s.copy(rejected=s.rejected+1,notify=false,completion=s.completion+1,
                last=when(result) {AlphaLuaStatus.TIMEOUT->AlphaDspResult.TIMEOUT;AlphaLuaStatus.SYNTAX_ERROR->AlphaDspResult.BAD_CONFIGURATION;else->AlphaDspResult.LUA_EXCEPTION},
                luaExceptions=s.luaExceptions+if(result==AlphaLuaStatus.RUNTIME_ERROR)1 else 0,
                configurationErrors=s.configurationErrors+if(result==AlphaLuaStatus.SYNTAX_ERROR)1 else 0,
                timeouts=s.timeouts+if(result==AlphaLuaStatus.TIMEOUT)1 else 0))
            // The existing bridge UI owns its error. No second DSP error notification.
        }
    }
    fun poll() {
        if(closed || !canRead())return
        val text=runCatching {mailbox()}.getOrNull()
        if(text!=null && text.length<=40000)runCatching {
            val obj=Json.parseToJsonElement(text) as? JsonObject?:return@runCatching
            if(obj["version"]!=JsonPrimitive(1))return@runCatching
            val results=obj["results"] as? JsonArray?:return@runCatching
            if(results.size>64)return@runCatching
            for(item in results) {
                val r=item as? JsonObject?:continue
                val request=(r["request"] as? JsonPrimitive)?.contentOrNull?:continue
                val expected=pending[request]?:continue // rejects old sessions and duplicate callbacks
                val kind=(r["kind"] as? JsonPrimitive)?.contentOrNull
                if(kind!=expected.kind || !expected.ack)continue
                val status=(r["status"] as? JsonPrimitive)?.contentOrNull
                val result=AlphaDspResult.entries.firstOrNull {it.name==status}?:continue
                fun integer(k:String,max:Int)=(r[k] as? JsonPrimitive)?.takeIf {!it.isString}?.intOrNull?.takeIf {it in 0..max}
                val commands=integer("commands",3)?:continue
                val level=(r["level"] as? JsonPrimitive)?.takeIf {!it.isString}?.intOrNull?:continue
                if(level !in -1..(if(kind=="drc")3 else 5))continue
                val available=(r["available"] as? JsonPrimitive)?.contentOrNull?.takeIf {it in setOf("YES","NO","UNCONFIRMED")}?:continue
                val rollback=(r["rollback"] as? JsonPrimitive)?.contentOrNull?.takeIf {it in setOf("NOT_NEEDED","VERIFIED","UNCONFIRMED")}?:continue
                val af=(r["af"] as? JsonPrimitive)?.takeIf {!it.isString}?.booleanOrNull?:continue
                if(result==AlphaDspResult.VERIFIED && (!af || level<0 || rollback!="NOT_NEEDED"))continue
                pending.remove(request)
                val s=state.value
                publish(s.copy(commands=s.commands+commands,
                    drcAvailable=if(kind=="drc" && available!="UNCONFIRMED")available else s.drcAvailable,
                    deAvailable=if(kind=="de" && available!="UNCONFIRMED")available else s.deAvailable,
                    verified=s.verified+if(result==AlphaDspResult.VERIFIED)1 else 0,
                    rejected=s.rejected+if(result!=AlphaDspResult.VERIFIED)1 else 0,
                    conflicts=s.conflicts+if(result==AlphaDspResult.CONFLICT)1 else 0,
                    configurationErrors=s.configurationErrors+if(result in setOf(AlphaDspResult.BAD_CONFIGURATION,AlphaDspResult.AUDIO_FORMAT,AlphaDspResult.MPV_REJECTED,AlphaDspResult.AF_MISMATCH))1 else 0,
                    rollbacks=s.rollbacks+if(rollback=="VERIFIED")1 else 0,
                    rollbackUnconfirmed=s.rollbackUnconfirmed+if(rollback=="UNCONFIRMED")1 else 0,
                    luaExceptions=s.luaExceptions+if(result==AlphaDspResult.LUA_EXCEPTION)1 else 0,
                    completion=s.completion+1,notify=result!=AlphaDspResult.VERIFIED,last=result))
            }
        }
        val expired=pending.filterValues {it.ack && clock()-it.time>=5000}.keys.toList()
        if(expired.isNotEmpty()) {
            expired.forEach(pending::remove)
            val s=state.value
            publish(s.copy(rejected=s.rejected+expired.size,timeouts=s.timeouts+expired.size,
                completion=s.completion+1,notify=true,last=AlphaDspResult.TIMEOUT)) // never replay
        }
        val actual=runCatching {chain()}.getOrNull()?.takeIf {it.size<=64}
        val s=state.value
        fun count(label:String)=actual?.count {it.label==label}?:0
        publish(s.copy(afRead=actual!=null,
            drc=actual?.let {readLevel(it,"drc") }?:"UNCONFIRMED",
            de=actual?.let {readLevel(it,"de") }?:"UNCONFIRMED",
            drcCount=count(DRC_LABEL),deCount=count(DE_LABEL)))
    }
    fun close() {closed=true;pending.clear();publish(state.value.copy(drc="UNCONFIRMED",de="UNCONFIRMED",drcCount=0,deCount=0,afRead=false))}
    companion object {
        const val PROPERTY="user-data/zavro/drc-de-v1"
        const val DRC_LABEL="zavro_repair_drc_v1"
        const val DE_LABEL="zavro_repair_de_v1"
        // Source of truth is still the native AF node. These are the shipped known graphs only.
        val drcGraphs=listOf(
            "acompressor=ratio=2:threshold=-24dB:attack=20:release=250",
            "acompressor=ratio=3:threshold=-18dB:attack=20:release=250",
            "acompressor=ratio=5:threshold=-12dB:attack=10:release=300")
        val deGraphs=listOf("0.55:voice=2","0.85:voice=3","1.20:voice=4","1.70:voice=6","2.30:voice=8")
            .map {"dialoguenhance=original=1:enhance=$it"}
        fun readLevel(chain:List<AlphaAfFilter>,kind:String):String {
            val mine=chain.filter {it.label==if(kind=="drc")DRC_LABEL else DE_LABEL}
            if(mine.isEmpty())return "OFF"
            val f=mine.singleOrNull()?.raw?:return "UNCONFIRMED"
            if(f["name"]!=JsonPrimitive("lavfi") || f["enabled"]!=JsonPrimitive(true))return "UNCONFIRMED"
            val p=f["params"] as? JsonObject?:return "UNCONFIRMED"
            if(p.size!=1)return "UNCONFIRMED"
            val g=(p["graph"]?:p["@0"]) as? JsonPrimitive?:return "UNCONFIRMED"
            val i=(if(kind=="drc")drcGraphs else deGraphs).indexOf(g.content)
            return if(i>=0)(i+1).toString() else "UNCONFIRMED"
        }
    }
}

/** RAM-only aggregates; no user code, labels, button IDs or request IDs in reports. */
internal object AlphaDspDiagnostics {
    private var owner:Any?=null
    private var current=AlphaDspState()
    @Synchronized fun update(session:Any?,s:AlphaDspState) {owner=session;current=s}
    @Synchronized fun report(session:Any?,unexpectedStarts:Int):String {
        val s=if(session!=null && owner===session)current else AlphaDspState()
        return """
            AUDIO / DRC / DE
            DRC: ${s.drc}; DE: ${s.de}
            Filtr DRC dostępny: ${s.drcAvailable}; filtr DE dostępny: ${s.deAvailable}
            Aktywne własne instancje DRC / DE: ${s.drcCount} / ${s.deCount}
            Wywołania naprawionych przycisków: ${s.sent}; skorelowane EXEC_OK: ${s.bridgeOk}
            Polecenia MPV wysłane: ${s.commands}
            Operacje DSP potwierdzone: ${s.verified}; odrzucone: ${s.rejected}
            Konflikty: ${s.conflicts}; błędy konfiguracji/grafu: ${s.configurationErrors}
            Rollback wykonany / niepotwierdzony: ${s.rollbacks} / ${s.rollbackUnconfirmed}
            Wyjątki Lua: ${s.luaExceptions}; brak potwierdzenia DSP: ${s.timeouts}
            Odczyt rzeczywistego af: ${if(s.afRead)"TAK" else "NIEPOTWIERDZONY"}
            Ostatni wynik DSP: ${s.last?.name?:"UNCONFIRMED"}
            Nieoczekiwany START_FILE: $unexpectedStarts
            Stan dotyczy wyłącznie własnych oznaczonych filtrów.
            EXEC_OK bridge nie potwierdza DSP. OSD API nie potwierdza widoczności na Androidzie.
        """.trimIndent()
    }
}
