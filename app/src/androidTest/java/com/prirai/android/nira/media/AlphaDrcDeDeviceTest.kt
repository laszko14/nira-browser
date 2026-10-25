package com.prirai.android.nira.media

import android.os.SystemClock
import android.view.LayoutInflater
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prirai.android.nira.R
import com.prirai.android.nira.media.alpha.*
import com.prirai.android.nira.media.alpha.audio.*
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Requires a real ARM64 device. Real Lua -> native af -> independent JNI readback.
 * Synthetic stereo signal/null AO. Compile alone does not verify Android DSP or listening. */
@RunWith(AndroidJUnit4::class)
class AlphaDrcDeDeviceTest {
    @Test fun repairedButtonsRealBridgeAndAfNeverDuplicateOrReload() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val owner="alpha-drc-de-device-test"
        assertTrue("Close current playback",MpvEngineGate.claim(owner))
        val dir=File(context.cacheDir,"alpha-drc-de-integration")
        var view:ZavroMpvView?=null;var created=false
        var lua:AlphaLuaController?=null;var dsp:AlphaDspSession?=null
        val starts=AtomicInteger()
        val events=object:MPVLib.EventObserver {
            override fun eventProperty(property:String) {}
            override fun eventProperty(property:String,value:Long) {}
            override fun eventProperty(property:String,value:Double) {}
            override fun eventProperty(property:String,value:Boolean) {}
            override fun eventProperty(property:String,value:String) {}
            override fun eventProperty(property:String,value:MPVNode) {
                if(property==AlphaLuaBridge.PROPERTY) {
                    val text=value.toJson()
                    android.os.Handler(android.os.Looper.getMainLooper()).post {lua?.onProperty(property,text)}
                }
            }
            override fun event(eventId:Int,data:MPVNode) {if(eventId==MPVLib.MpvEvent.MPV_EVENT_START_FILE)starts.incrementAndGet()}
        }
        fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
        fun await(test:()->Boolean) {
            val deadline=SystemClock.elapsedRealtime()+10000
            var ok=false
            while(!ok && SystemClock.elapsedRealtime()<deadline) {
                main {lua!!.tick();dsp!!.poll();ok=test()}
                if(!ok)SystemClock.sleep(50)
            }
            assertTrue("Native DSP/bridge did not confirm",ok)
        }
        val source=AlphaAudioRepairSource(context.assets.open("mpv/zavro_drc_de_repair.lua").bufferedReader().use {it.readText()})
        fun invoke(kind:String,long:Boolean=false) {
            var completion=0L
            main {
                completion=dsp!!.state.value.completion
                val request=dsp!!.prepare(kind)!!
                assertEquals(AlphaLuaStatus.SENT,lua!!.dispatch(source.wrap(source.code(kind,long),request)) {dsp!!.bridgeResult(request,it)})
            }
            await {dsp!!.state.value.completion>completion}
            main {assertEquals(AlphaDspResult.VERIFIED,dsp!!.state.value.last)}
        }
        try {
            main {
                dir.mkdirs();view=LayoutInflater.from(context).inflate(R.layout.zavro_mpv_surface,null,false) as ZavroMpvView
                assertTrue(view!!.prepareAlphaBridge(dir.absolutePath));created=true
                view!!.initialize(dir.absolutePath,context.cacheDir.absolutePath)
                assertTrue(MPVLib.setOptionString("ao","null")>=0)
                lua=AlphaLuaController({MpvEngineGate.owns(owner)},{MPVLib.command(*it)},{SystemClock.elapsedRealtime()},enabled=true)
                dsp=AlphaDspSession({MpvEngineGate.owns(owner)},
                    {MPVLib.getPropertyNode(AlphaDspSession.PROPERTY)?.toJson()},AlphaAudioNative::read,{SystemClock.elapsedRealtime()})
                MPVLib.addObserver(events)
                MPVLib.observeProperty(AlphaLuaBridge.PROPERTY,MPVLib.MpvFormat.MPV_FORMAT_NODE)
                lua!!.start(true,view!!.alphaBridgeLoadConfigured)
                // Exactly one initial source, stereo. No controller may emit another load.
                assertNotNull(MPVLib.commandNode("loadfile","av://lavfi:aevalsrc=0.2*sin(2*PI*900*t)|0.2*sin(2*PI*900*t):s=48000","replace"))
            }
            await {lua!!.state.value.ready && (MPVLib.getPropertyDouble("time-pos")?:0.0)>0.1}
            var foreign:List<AlphaAfFilter>?=null
            var speed=0.0;var paused=false;var position=0.0
            main {
                assertEquals(2,MPVLib.getPropertyInt("audio-params/channel-count"))
                assertNotNull(MPVLib.commandNode("af","add","@synthetic_foreign_compressor:lavfi=[acompressor=ratio=1.2:threshold=0.9]"))
                assertNotNull(MPVLib.commandNode("af","add",RexEqualizerAudio.specification(RexEqualizerAudio.presets.first().second,0)))
                foreign=AlphaAudioNative.read()!!
                speed=MPVLib.getPropertyDouble("speed")!!;paused=MPVLib.getPropertyBoolean("pause")==true
                position=MPVLib.getPropertyDouble("time-pos")!!
            }
            repeat(3) {
                repeat(4) {level->invoke("drc");main {assertEquals(if(level==3)"OFF" else "${level+1}",dsp!!.state.value.drc)}}
                repeat(6) {level->invoke("de");main {assertEquals(if(level==5)"OFF" else "${level+1}",dsp!!.state.value.de)}}
            }
            invoke("drc");invoke("de");invoke("drc",true);invoke("de",true)
            main {
                assertEquals(foreign!!.map {it.raw},AlphaAudioNative.read()!!.map {it.raw})
                assertEquals(1,starts.get())
                assertEquals(speed,MPVLib.getPropertyDouble("speed")!!,0.000001)
                assertEquals(paused,MPVLib.getPropertyBoolean("pause")==true)
                assertTrue(MPVLib.getPropertyDouble("time-pos")!!>=position)
                val before=AlphaAudioNative.read()!!.map {it.raw}
                assertNull(MPVLib.commandNode("af","add","@synthetic_bad:lavfi=[dialoguenhance=enhance=99]"))
                assertEquals(before,AlphaAudioNative.read()!!.map {it.raw})
                assertEquals(0,lua!!.state.value.runtimeErrors)
            }
        } finally {
            main {
                dsp?.close();lua?.close();MPVLib.removeObserver(events)
                if(created)runCatching {view?.destroy()}
                MpvEngineGate.release(owner)
            }
            dir.deleteRecursively()
        }
    }
}
