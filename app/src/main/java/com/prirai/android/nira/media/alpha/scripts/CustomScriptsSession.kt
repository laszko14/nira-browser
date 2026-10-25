package com.prirai.android.nira.media.alpha.scripts

import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import com.prirai.android.nira.media.alpha.audio.AlphaAudioNative
import com.prirai.android.nira.media.alpha.AlphaFeedbackPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class CustomClientState(val loaded:Boolean=false,val status:String="LOAD_UNCONFIRMED",
    val errors:Int=0,val handlers:Int=0,val cycle:Boolean=false,val off:Boolean=false,val ambient:Boolean=false,val warnings:Int=0,val logErrors:Int=0,val topErrors:Int=0,val callbackErrors:Int=0)
internal data class CustomScriptsState(val clients:Map<String,CustomClientState> = emptyMap(),val versions:Map<String,String> = emptyMap(),
    val configured:Boolean=false,val model:Boolean=false,val arnndn:Boolean=false,val ambientShader:Boolean=false,
    val filterErrors:Int=0,val optionsAccepted:Int=0,val optionsRejected:Int=0,val feedback:FeedbackCount=FeedbackCount(),
    val osdConfigured:Boolean=false,val osdFontErrors:Int=0,val ambient:AmbientFeedback=AmbientFeedback())

/** Existing owner's tick/log callback only. No observer or playback commands. */
internal class CustomScriptsSession(private val canRead:()->Boolean) {
    @Volatile private var runtime:CustomLuaRuntime?=null
    @Volatile private var closed=false
    private val filterErrors=java.util.concurrent.atomic.AtomicInteger()
    private val errors=mutableMapOf<String,Int>()
    private val warnings=mutableMapOf<String,Int>()
    private val gpuErrors=java.util.concurrent.atomic.AtomicInteger()
    private val fontErrors=java.util.concurrent.atomic.AtomicInteger()
    private var seenShader=false
    private var osdConfigured=false
    private val mutableState=MutableStateFlow(CustomScriptsState())
    val state=mutableState.asStateFlow()
    fun start(value:CustomLuaRuntime?,configured:Boolean,accepted:Int,rejected:Int) {
        if(closed || runtime!=null)return
        runtime=value
        mutableState.value=CustomScriptsState(clients=value?.scripts.orEmpty().associate {it.id to CustomClientState()},
            versions=value?.scripts.orEmpty().associate {it.id to it.hash},
            configured=configured,model=value?.modelAvailable==true,optionsAccepted=accepted,optionsRejected=rejected)
    }
    fun configured(ok:Boolean,accepted:Int,rejected:Int,osd:Boolean=false) {
        osdConfigured=osd
        if(!closed)mutableState.value=state.value.copy(configured=ok,optionsAccepted=accepted,optionsRejected=rejected)
    }
    fun log(prefix:String,level:Int,text:String="") {
        if(closed || level>30)return
        runtime?.scripts?.firstOrNull {CustomLuaStore.clientName(it.name)==prefix}?.let {
            val counts=if(AlphaFeedbackPolicy.warning(level))warnings else errors
            synchronized(errors) {counts[it.id]=((counts[it.id]?:0)+1).coerceAtMost(1000000)}
        }
        if(AlphaFeedbackPolicy.error(level) && prefix.contains("libass"))fontErrors.incrementAndGet()
        if(AlphaFeedbackPolicy.error(level) && prefix.contains("gpu") &&
            text.contains("shader",true) && (text.contains("compil",true) || text.contains("link",true)) &&
            (text.contains("fail",true) || text.contains("error",true)))gpuErrors.incrementAndGet()
        if(AlphaFeedbackPolicy.error(level) && (prefix=="af" || prefix=="lavfi" || prefix.startsWith("ffmpeg/audio")))
            filterErrors.updateAndGet {(it+1).coerceAtMost(1000000)}
    }
    fun poll() {
        if(closed || !canRead())return
        val r=runtime?:return
        var sent=0;var accepted=0;var osdErrors=0
        runCatching {MPVLib.commandNode("script-message-to","mpv_alpha_buttons_bridge","zavro_feedback_probe")}
        val bridgeFeedback=FeedbackCount.parse(runCatching {MPVLib.getPropertyNode("user-data/zavro-feedback/bridge")}.getOrNull())
        sent+=bridgeFeedback.sent;accepted+=bridgeFeedback.accepted;osdErrors+=bridgeFeedback.errors
        var ambientCount=FeedbackCount()
        val clients=r.scripts.associate {row->
            val node=runCatching {MPVLib.getPropertyNode(r.property(row))?.asMap()}.getOrNull()
            fun flag(k:String)=(node?.get(k) as? MPVNode.BooleanNode)?.value==true
            fun number(k:String)=((node?.get(k) as? MPVNode.IntNode)?.value?:0).coerceIn(0,1000000).toInt()
            val matching=(node?.get("epoch") as? MPVNode.StringNode)?.value==r.epoch
            if(matching && state.value.configured)runCatching {
                MPVLib.commandNode("script-message-to",CustomLuaStore.clientName(row.name),"zavro_custom_status",r.epoch)
            }
            val logErrors=synchronized(errors){errors[row.id]?:0}
            val warn=synchronized(errors){warnings[row.id]?:0}
            val count=FeedbackCount.parse(node?.get("feedback"))
            sent+=count.sent;accepted+=count.accepted;osdErrors+=count.errors
            if(flag("ambient"))ambientCount=count
            row.id to if(!matching)CustomClientState(status=if(logErrors>0)"LOAD_ERROR" else "LOAD_UNCONFIRMED",logErrors=logErrors,warnings=warn)
            else CustomClientState(loaded=flag("loaded"),status=AlphaFeedbackPolicy.clientStatus(flag("loaded"),flag("syntax"),
                number("top_errors")>0,number("callback_errors")>0,flag("conflict"),logErrors),
                errors=number("errors"),handlers=number("handlers"),cycle=flag("cycle"),off=flag("off"),ambient=flag("ambient"),
                warnings=warn,logErrors=logErrors,topErrors=number("top_errors"),callbackErrors=number("callback_errors"))
        }
        val af=AlphaAudioNative.read()
        val denoise=af?.any {f->f.raw["enabled"]?.toString()=="true" &&
            f.raw["params"]?.toString()?.let {it.contains("arnndn=") && it.contains("std.rnnn")}==true}==true
        val shader=runCatching {MPVLib.getPropertyNode("glsl-shaders")?.asArray()?.any {
            (it as? MPVNode.StringNode)?.value?.substringAfterLast('/')?.let(AlphaFeedbackPolicy::ambientFile)==true
        }==true}.getOrDefault(false)
        val files=r.ambientFiles()
        val validFile=files.any {file->runCatching {
            file.length() in 1..65536 && file.readText().split("//!HOOK OUTPUT").size==5
        }.getOrDefault(false)}
        if(validFile || shader)seenShader=true
        fun number(property:String)=runCatching {MPVLib.getPropertyDouble(property)}.getOrNull()
        val renderer=AmbientReadback.renderer(runCatching {MPVLib.getPropertyString("current-vo")}.getOrNull())
        val executed=AmbientReadback.executedPass(runCatching {MPVLib.getPropertyNode("vo-passes")}.getOrNull())
        val ambient=AmbientFeedback(renderer,when {validFile->"CREATED";ambientCount.openFailed>0->"FAILED";else->"UNCONFIRMED"},
            shader,when {gpuErrors.get()>0->"FAILED";shader && executed->"VERIFIED";else->"UNCONFIRMED"},
            if(seenShader && files.isEmpty() && !shader && ambientCount.removed>0 && ambientCount.viewRestored)"VERIFIED" else "UNCONFIRMED",
            AmbientReadback.condition(renderer,number("osd-width"),number("osd-height"),number("video-params/aspect")),
            when {ambientCount.openFailed>0->"FILE_OPEN_FAILED";ambientCount.shaderFailed>0->"SHADER_APPLY_FAILED"
                ambientCount.viewFailed>0->"VIEW_OPTION_REJECTED";else->"NO_FAILURE_CONFIRMED"})
        mutableState.value=state.value.copy(clients=clients,arnndn=denoise,ambientShader=shader,filterErrors=filterErrors.get(),
            feedback=FeedbackCount(sent,accepted,osdErrors),osdConfigured=osdConfigured,osdFontErrors=fontErrors.get(),ambient=ambient)
    }
    fun close() {
        if(closed)return
        closed=true;runtime=null
        mutableState.value=state.value.copy(configured=false,versions=emptyMap(),
            clients=state.value.clients.mapValues {it.value.copy(loaded=false,status="SESSION_ENDED")})
    }
}

/** Redacted aggregate snapshot. Filenames, script text and document paths never enter reports. */
internal object CustomScriptsDiagnostics {
    private var owner:Any?=null
    private var current=CustomScriptsState()
    @Synchronized fun update(session:Any,value:CustomScriptsState) {owner=session;current=value}
    @Synchronized fun report(session:Any?,imported:Int):String {
        val s=if(session!=null && session===owner)current else CustomScriptsState()
        val clients=s.clients.values
        return """
            LUA SCRIPTS / CUSTOM
            Zaimportowane: $imported
            Aktywne (wybrane w sesji): ${if(s.configured)clients.size else 0}
            Rzeczywiście załadowane: ${clients.count {it.loaded}}
            Ładowanie: ${if(s.configured)"EXPLICIT_LIST" else "UNCONFIRMED"}
            Błędy ładowania: ${clients.count {it.status=="LOAD_ERROR" || it.status=="CLIENT_CONFLICT" || it.status=="SYNTAX_ERROR" || (!it.loaded && it.status=="TOP_LEVEL_ERROR")}}
            Zarejestrowane handlery: ${clients.sumOf {it.handlers}}
            Cycle/OFF/Ambient: ${clients.count {it.cycle}}/${clients.count {it.off}}/${clients.count {it.ambient}}
            Model std.rnnn: ${if(s.model)"DOSTĘPNY" else "NIEDOSTĘPNY"}
            arnndn + model: ${if(s.arnndn && s.model)"KONFIGURACJA_AF" else "NIEPOTWIERDZONE"}
            Ambient shader: ${if(s.ambientShader)"NA_LIŚCIE_GLSL" else "NIEPOTWIERDZONY"}
            Ostrzeżenia skryptów: ${clients.sumOf {it.warnings}}
            Błędy logów (nie wyjątki Lua): ${clients.sumOf {it.logErrors}}
            Błędy Lua/filtrów: ${clients.sumOf {it.errors}}/${s.filterErrors}
            Opcje mpv.conf przyjęte/odrzucone: ${s.optionsAccepted}/${s.optionsRejected}
            Duplikaty jawnej listy: 0 (walidacja nazw klientów)
            Hot-reload: WYŁĄCZONY; restart silnika: NIEZLECANY
            DSP i obraz GPU wymagają testu telefonu.

            OSD / LUA FEEDBACK
            Native OSD: ${if(s.osdFontErrors>0 || (s.configured && !s.osdConfigured))"UNAVAILABLE" else if(s.osdConfigured && s.feedback.accepted>0)"AVAILABLE (API)" else "UNCONFIRMED"}
            Wiadomości OSD wysłane: ${s.feedback.sent}
            Wiadomości przyjęte: ${s.feedback.accepted}
            Błędy OSD: ${s.feedback.errors+s.osdFontErrors}
            Systemowe Toast sukcesu: 0

            AMBIENT / GPU
            current-vo: ${s.ambient.renderer}
            Klient Lua: ${clients.firstOrNull {it.ambient}?.status?:"UNCONFIRMED"}
            Ostrzeżenia skryptu: ${clients.filter {it.ambient}.sumOf {it.warnings}}
            Wyjątki top-level/callback: ${clients.filter {it.ambient}.sumOf {it.topErrors}}/${clients.filter {it.ambient}.sumOf {it.callbackErrors}}
            Shader file: ${s.ambient.file}
            GLSL list: ${if(s.ambient.list)"ADDED" else "NOT_PRESENT"}
            GPU compilation: ${s.ambient.compilation}
            GPU effect: WYMAGA WZROKOWEGO POTWIERDZENIA
            Cleanup: ${s.ambient.cleanup}
            Warunki obrazu: ${s.ambient.condition}
            Etap błędu API: ${s.ambient.stage}
        """.trimIndent()
    }
}
