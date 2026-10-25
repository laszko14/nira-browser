package com.prirai.android.nira.media.alpha.scripts

import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import com.prirai.android.nira.media.alpha.audio.AlphaAudioNative
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class CustomClientState(val loaded:Boolean=false,val status:String="LOAD_UNCONFIRMED",
    val errors:Int=0,val handlers:Int=0,val cycle:Boolean=false,val off:Boolean=false,val ambient:Boolean=false)
internal data class CustomScriptsState(val clients:Map<String,CustomClientState> = emptyMap(),val versions:Map<String,String> = emptyMap(),
    val configured:Boolean=false,val model:Boolean=false,val arnndn:Boolean=false,val ambientShader:Boolean=false,
    val filterErrors:Int=0,val optionsAccepted:Int=0,val optionsRejected:Int=0)

/** Existing owner's tick/log callback only. No observer or playback commands. */
internal class CustomScriptsSession(private val canRead:()->Boolean) {
    @Volatile private var runtime:CustomLuaRuntime?=null
    @Volatile private var closed=false
    private val filterErrors=java.util.concurrent.atomic.AtomicInteger()
    private val errors=mutableMapOf<String,Int>()
    private val mutableState=MutableStateFlow(CustomScriptsState())
    val state=mutableState.asStateFlow()
    fun start(value:CustomLuaRuntime?,configured:Boolean,accepted:Int,rejected:Int) {
        if(closed || runtime!=null)return
        runtime=value
        mutableState.value=CustomScriptsState(clients=value?.scripts.orEmpty().associate {it.id to CustomClientState()},
            versions=value?.scripts.orEmpty().associate {it.id to it.hash},
            configured=configured,model=value?.modelAvailable==true,optionsAccepted=accepted,optionsRejected=rejected)
    }
    fun configured(ok:Boolean,accepted:Int,rejected:Int) {
        if(!closed)mutableState.value=state.value.copy(configured=ok,optionsAccepted=accepted,optionsRejected=rejected)
    }
    fun log(prefix:String,level:Int) {
        if(closed || level>30)return
        runtime?.scripts?.firstOrNull {CustomLuaStore.clientName(it.name)==prefix}?.let {
            synchronized(errors) {errors[it.id]=((errors[it.id]?:0)+1).coerceAtMost(1000000)}
        }
        if(prefix=="af" || prefix=="lavfi" || prefix.startsWith("ffmpeg/audio"))
            filterErrors.updateAndGet {(it+1).coerceAtMost(1000000)}
    }
    fun poll() {
        if(closed || !canRead())return
        val r=runtime?:return
        val clients=r.scripts.associate {row->
            val node=runCatching {MPVLib.getPropertyNode(r.property(row))?.asMap()}.getOrNull()
            fun flag(k:String)=(node?.get(k) as? MPVNode.BooleanNode)?.value==true
            fun number(k:String)=((node?.get(k) as? MPVNode.IntNode)?.value?:0).coerceIn(0,1000000).toInt()
            val matching=(node?.get("epoch") as? MPVNode.StringNode)?.value==r.epoch
            if(matching && state.value.configured)runCatching {
                MPVLib.commandNode("script-message-to",CustomLuaStore.clientName(row.name),"zavro_custom_status",r.epoch)
            }
            val logErrors=synchronized(errors){errors[row.id]?:0}
            row.id to if(!matching)CustomClientState(status=if(logErrors>0)"LOAD_ERROR" else "LOAD_UNCONFIRMED",errors=logErrors)
            else CustomClientState(flag("loaded"),when {
                flag("conflict")->"CLIENT_CONFLICT";flag("syntax")->"SYNTAX_ERROR";flag("runtime") || logErrors>0->"RUNTIME_ERROR"
                flag("loaded")->"LOADED";else->"LOAD_UNCONFIRMED"
            },maxOf(number("errors"),logErrors),number("handlers"),flag("cycle"),flag("off"),flag("ambient"))
        }
        val af=AlphaAudioNative.read()
        val denoise=af?.any {f->f.raw["enabled"]?.toString()=="true" &&
            f.raw["params"]?.toString()?.let {it.contains("arnndn=") && it.contains("std.rnnn")}==true}==true
        val shader=runCatching {MPVLib.getPropertyNode("glsl-shaders")?.asArray()?.any {
            (it as? MPVNode.StringNode)?.value?.substringAfterLast('/')?.matches(
                Regex("rex_ambient_plus_v4_[0-9]+_[0-9]+\\.glsl"))==true
        }==true}.getOrDefault(false)
        mutableState.value=state.value.copy(clients=clients,arnndn=denoise,ambientShader=shader,filterErrors=filterErrors.get())
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
            Błędy ładowania: ${clients.count {it.status=="LOAD_ERROR" || it.status=="CLIENT_CONFLICT" || it.status=="SYNTAX_ERROR" || (!it.loaded && it.status=="RUNTIME_ERROR")}}
            Zarejestrowane handlery: ${clients.sumOf {it.handlers}}
            Cycle/OFF/Ambient: ${clients.count {it.cycle}}/${clients.count {it.off}}/${clients.count {it.ambient}}
            Model std.rnnn: ${if(s.model)"DOSTĘPNY" else "NIEDOSTĘPNY"}
            arnndn + model: ${if(s.arnndn && s.model)"KONFIGURACJA_AF" else "NIEPOTWIERDZONE"}
            Ambient shader: ${if(s.ambientShader)"NA_LIŚCIE_GLSL" else "NIEPOTWIERDZONY"}
            Błędy Lua/filtrów: ${clients.sumOf {it.errors}}/${s.filterErrors}
            Opcje mpv.conf przyjęte/odrzucone: ${s.optionsAccepted}/${s.optionsRejected}
            Duplikaty jawnej listy: 0 (walidacja nazw klientów)
            Hot-reload: WYŁĄCZONY; restart silnika: NIEZLECANY
            DSP i obraz GPU wymagają testu telefonu.
        """.trimIndent()
    }
}
