package com.prirai.android.nira.media.alpha

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import java.util.UUID

internal enum class AlphaLuaStatus {
    IDLE, REGISTERED, READY, SENT, OK, SYNTAX_ERROR, RUNTIME_ERROR, TIMEOUT,
    DISABLED, UNAVAILABLE, EMPTY, MISSING_BUTTON, SEND_FAILED, BUSY, NATIVE_APPLIED, NATIVE_FAILED
}
internal data class AlphaLuaState(
    val installed:Boolean=false, val registered:Boolean=false, val ready:Boolean=false,
    val loadConfigured:Boolean=false, val loaded:Boolean=false, val probeSent:Int=0,
    val probeAcknowledged:Boolean=false, val publishFailures:Int=0, val invalidMessages:Int=0,
    val enabled:Boolean=false, val status:AlphaLuaStatus=AlphaLuaStatus.IDLE,
    val sent:Int=0, val callbacks:Int=0, val succeeded:Int=0, val syntaxErrors:Int=0,
    val runtimeErrors:Int=0, val timeouts:Int=0, val blocked:Int=0,
    val completion:Long=0, val lastCompletion:AlphaLuaStatus?=null,
)

/** No engine lifecycle/observer ownership. Called on the existing owner's main thread. */
internal class AlphaLuaController(
    private val canSend:()->Boolean,
    private val send:(Array<String>)->Unit,
    private val now:()->Long,
    private val saveConsent:(Boolean)->Boolean={true},
    enabled:Boolean=false,
    private val changed:(AlphaLuaState)->Unit={},
) {
    private val mutableState=MutableStateFlow(AlphaLuaState(enabled=enabled))
    val state=mutableState.asStateFlow()
    private val epoch=UUID.randomUUID().toString()
    private val pending=linkedMapOf<String,Long>()
    private var sequence=0L
    private var closed=false
    private var started=false
    private var probeUntil=0L
    private var nextProbe=0L
    private fun update(next:AlphaLuaState) { mutableState.value=next;changed(next) }
    private fun status(value:AlphaLuaStatus) {
        update(state.value.copy(status=value))
    }
    fun setEnabled(value:Boolean):Boolean {
        if(closed)return false
        // Disable dispatch immediately, even if persistent storage fails.
        if(!value)update(state.value.copy(enabled=false))
        val saved=runCatching { saveConsent(value) }.getOrDefault(false)
        if(saved)update(state.value.copy(enabled=value))
        return saved
    }
    fun start(installed:Boolean,configured:Boolean=true) {
        if(started || closed)return
        started=true
        update(state.value.copy(installed=installed,loadConfigured=installed && configured))
        if(installed && configured) { probeUntil=now()+5_000;nextProbe=now();tick() }
        else status(AlphaLuaStatus.UNAVAILABLE)
    }
    /** Retry only the readiness probe, never load/reload a script or run user source. */
    fun tick() {
        if(closed)return
        val time=now()
        val expired=pending.filterValues { time-it>=5_000 }.keys.toList()
        if(expired.isNotEmpty()) {
            expired.forEach(pending::remove)
            update(state.value.copy(timeouts=state.value.timeouts+expired.size,
                status=AlphaLuaStatus.TIMEOUT,lastCompletion=AlphaLuaStatus.TIMEOUT,completion=state.value.completion+1))
        }
        if(started && state.value.loadConfigured && !state.value.ready && time>=nextProbe && canSend()) {
            // Slow, bounded-frequency recovery after the initial burst, even if the
            // one-time registration announcement preceded observer installation.
            nextProbe=time+if(time<=probeUntil)500 else 5_000
            runCatching { send(arrayOf("script-message","mpv_alpha_probe",epoch)) }
                .onSuccess { update(state.value.copy(probeSent=state.value.probeSent+1)) }
        }
    }
    fun onPublishFailure() {
        if(closed || !started || !state.value.loadConfigured)return
        // Only the fixed log emitted by this bridge reaches this callback.
        update(state.value.copy(loaded=true,registered=true,ready=false,
            publishFailures=state.value.publishFailures+1))
    }
    fun onPropertyInvalid() {
        if(closed || !started || !state.value.loadConfigured)return
        update(state.value.copy(ready=false,invalidMessages=state.value.invalidMessages+1))
    }
    fun onProperty(property:String,value:String) {
        if(closed || !started || !state.value.loadConfigured || property!=AlphaLuaBridge.PROPERTY)return
        if(value.length>20_000) {onPropertyInvalid();return}
        val message=runCatching { Json.parseToJsonElement(value) as? JsonObject }.getOrNull()
        val version=message?.get("version") as? JsonPrimitive
        val ready=message?.get("ready") as? JsonPrimitive
        val session=message?.get("epoch") as? JsonPrimitive
        val results=message?.get("results") as? JsonArray
        if(message==null || version==null || version.isString || version.intOrNull!=1 ||
            ready==null || ready.isString || ready.booleanOrNull!=true ||
            session==null || !session.isString || results==null || results.size>64) {
            onPropertyInvalid();return
        }
        if(session.content.isEmpty()) {
            // A valid native map proves code ran AFTER both handlers registered,
            // but it does not prove a probe reached this session's callback.
            if(!state.value.registered) {
                update(state.value.copy(loaded=true,registered=true,status=AlphaLuaStatus.REGISTERED))
                probeUntil=now()+5_000;nextProbe=now()
            }
            return
        }
        if(session.content!=epoch)return
        if(!state.value.ready)update(state.value.copy(loaded=true,registered=true,ready=true,
            probeAcknowledged=true,status=AlphaLuaStatus.READY))
        for(element in results) {
            val entry=element as? JsonObject?:continue
            val id=(entry["id"] as? JsonPrimitive)?.contentOrNull?:continue
            val outcome=when((entry["status"] as? JsonPrimitive)?.contentOrNull) {
                "ok"->AlphaLuaStatus.OK
                "syntax"->AlphaLuaStatus.SYNTAX_ERROR
                "runtime"->AlphaLuaStatus.RUNTIME_ERROR
                else->continue
            }
            if(pending.remove(id)==null)continue // Late, duplicate or other-session ACK.
            val old=state.value
            update(old.copy(status=outcome,lastCompletion=outcome,callbacks=old.callbacks+1,
                succeeded=old.succeeded+if(outcome==AlphaLuaStatus.OK)1 else 0,
                syntaxErrors=old.syntaxErrors+if(outcome==AlphaLuaStatus.SYNTAX_ERROR)1 else 0,
                runtimeErrors=old.runtimeErrors+if(outcome==AlphaLuaStatus.RUNTIME_ERROR)1 else 0,
                completion=old.completion+1))
        }
    }
    fun dispatch(code:String):AlphaLuaStatus {
        tick()
        val rejected=when {
            closed || !canSend()->AlphaLuaStatus.UNAVAILABLE
            !state.value.enabled->AlphaLuaStatus.DISABLED
            !state.value.ready->AlphaLuaStatus.UNAVAILABLE
            code.isBlank()->AlphaLuaStatus.EMPTY
            code.toByteArray(Charsets.UTF_8).size>1_048_576->AlphaLuaStatus.SEND_FAILED
            pending.size>=16->AlphaLuaStatus.BUSY
            else->null
        }
        if(rejected!=null) {
            update(state.value.copy(status=rejected,blocked=state.value.blocked+1))
            return rejected
        }
        val id="$epoch-${++sequence}"
        pending[id]=now()
        return try {
            send(arrayOf("script-message","mpv_alpha_eval",code,id,epoch))
            update(state.value.copy(status=AlphaLuaStatus.SENT,sent=state.value.sent+1))
            AlphaLuaStatus.SENT // Never presented as successful execution.
        } catch (_:Exception) { pending.remove(id);status(AlphaLuaStatus.SEND_FAILED);AlphaLuaStatus.SEND_FAILED }
          catch (_:LinkageError) { pending.remove(id);status(AlphaLuaStatus.SEND_FAILED);AlphaLuaStatus.SEND_FAILED }
    }
    fun close() {
        if(closed)return
        closed=true;pending.clear()
        update(state.value.copy(registered=false,ready=false,status=AlphaLuaStatus.UNAVAILABLE))
    }
}

/** Resolve current code by stable ID on every click. Appearance/title are never commands. */
internal object AlphaLuaActions {
    fun invoke(id:String,longPress:Boolean,current:AlphaPreferences,session:AlphaSession):AlphaLuaStatus {
        val button=current.slots.filterNotNull().singleOrNull { it.id==id }?.takeIf {it.enabled}
            ?:return AlphaLuaStatus.MISSING_BUTTON
        val code=if(longPress)button.longPressContent else button.content
        if(code.isBlank())return AlphaLuaStatus.EMPTY
        val command=AlphaNativeButton.command(code,session.state.value)
        if(command!=null)return if(session.execute(command)==AlphaCommandResult.APPLIED)
            AlphaLuaStatus.NATIVE_APPLIED else AlphaLuaStatus.NATIVE_FAILED
        return session.lua?.dispatch(code)?:AlphaLuaStatus.UNAVAILABLE
    }
}
