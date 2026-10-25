package com.prirai.android.nira.media

import android.os.SystemClock
import android.view.LayoutInflater
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prirai.android.nira.R
import com.prirai.android.nira.media.alpha.scripts.*
import com.prirai.android.nira.media.alpha.audio.AlphaAudioNative
import `is`.xyz.mpv.MPVLib
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real native Lua clients and directed messages. No private files or real media in this test. */
@RunWith(AndroidJUnit4::class)
class CustomLuaDeviceTest {
    @Test fun explicitIndependentClientsRegisterMessagesAndKeepFrozenSessionOnUpdate() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val owner="custom-lua-device-test"
        assertTrue("Close playback before testing",MpvEngineGate.claim(owner))
        val root=File(context.cacheDir,"custom-lua-device-${System.nanoTime()}")
        val config=File(root,"config").also {it.mkdirs()}
        val store=CustomLuaStore(File(root,"store"))
        val first="""assert(mp.get_script_name()=='synthetic_first')
            mp.register_script_message('cycle',function()
                local v=mp.get_property_number('user-data/synthetic-first',0)
                mp.set_property_number('user-data/synthetic-first',v+1)
            end)
            mp.register_event('file-loaded',function() mp.set_property_native('user-data/synthetic-event',true) end)
            mp.register_event('start-file',function()
                mp.set_property_number('user-data/synthetic-starts',mp.get_property_number('user-data/synthetic-starts',0)+1)
            end)
        """.trimIndent()
        store.importScripts(listOf("synthetic_first.lua" to first.toByteArray(),
            "synthetic_second.lua" to "mp.register_script_message('cycle',function() mp.set_property_number('user-data/synthetic-second',7) end)".toByteArray(),
            "synthetic_bad.lua" to "local = syntax_error".toByteArray(),
            "unselected.lua" to "mp.set_property_native('user-data/should-never-run',true)".toByteArray()))
        store.importModel(syntheticModel())
        store.state.value.scripts.filter {it.name!="unselected.lua"}.forEach {store.enable(it.id,true,true)}
        var view:ZavroMpvView?=null
        var created=false
        var monitor:CustomScriptsSession?=null
        fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
        fun number(key:String):Double? {
            val n=MPVLib.getPropertyNode(key)
            return n?.asDouble()?:n?.asInt()?.toDouble()
        }
        fun await(check:()->Boolean) {
            var good=false;val until=SystemClock.elapsedRealtime()+10_000
            while(!good && SystemClock.elapsedRealtime()<until) {
                main {monitor!!.poll();good=check()};if(!good)SystemClock.sleep(50)
            }
            assertTrue("Native custom script load/message confirmation missing",good)
        }
        try {
            main {
                // An unrelated file must stay on disk without autoloading.
                File(config,"scripts").mkdirs()
                File(config,"scripts/unrelated.lua").writeText("mp.set_property_native('user-data/should-never-run',true)")
                view=LayoutInflater.from(context).inflate(R.layout.zavro_mpv_surface,null,false) as ZavroMpvView
                assertTrue(view!!.prepareAlphaBridge(config.absolutePath,store))
                val runtime=view!!.customRuntime!!
                assertEquals(3,runtime.paths.size)
                monitor=CustomScriptsSession {MpvEngineGate.owns(owner)}
                monitor!!.start(runtime,false,0,0)
                created=true;view!!.initialize(config.absolutePath,context.cacheDir.absolutePath)
                monitor!!.configured(view!!.customScriptsConfigured,0,0)
            }
            await {monitor!!.state.value.clients.values.count {it.loaded}==2 &&
                monitor!!.state.value.clients.values.any {it.status=="SYNTAX_ERROR"}}
            main {
                assertNull(MPVLib.getPropertyNode("user-data/should-never-run"))
                assertEquals("no",MPVLib.getPropertyString("options/load-scripts"))
                assertNotNull(MPVLib.commandNode("script-message-to","synthetic_first","cycle"))
                assertNotNull(MPVLib.commandNode("script-message-to","synthetic_second","cycle"))
            }
            await {number("user-data/synthetic-first")==1.0 &&
                number("user-data/synthetic-second")==7.0}
            store.importScripts(listOf("synthetic_first.lua" to "error('updated must not run yet')".toByteArray()))
            store.state.value.scripts.forEach {store.enable(it.id,false,false)}
            main {assertNotNull(MPVLib.commandNode("script-message-to","synthetic_first","cycle"))}
            await {number("user-data/synthetic-first")==2.0}
            main {
                assertEquals(3,monitor!!.state.value.clients.size)
                assertTrue(MPVLib.setOptionString("ao","null")>=0)
                // One synthetic fixture load. Production script manager never loads media.
                assertNotNull(MPVLib.commandNode("loadfile","av://lavfi:sine=frequency=1000:sample_rate=48000","replace"))
            }
            await {number("user-data/synthetic-starts")==1.0 && (MPVLib.getPropertyDouble("time-pos")?:0.0)>0.05}
            main {
                assertTrue(monitor!!.state.value.model)
                assertNotNull(MPVLib.commandNode("af","add","@synthetic_foreign:lavfi=[volume=0.5]"))
                val position=MPVLib.getPropertyDouble("time-pos")!!
                val speed=MPVLib.getPropertyDouble("speed")!!
                for(mix in listOf("0.70","0.35","0.50","0.65","0.20","0.85","0.95")) {
                    val model=File(config,"script-opts/std.rnnn").absolutePath
                    assertNotNull(MPVLib.commandNode("af","add","@synthetic_noise:lavfi=[arnndn=m='$model':mix=$mix]"))
                    val af=AlphaAudioNative.read()!!
                    assertEquals(1,af.count {it.label=="synthetic_noise"})
                    assertEquals(1,af.count {it.label=="synthetic_foreign"})
                }
                assertNotNull(MPVLib.commandNode("af","remove","@synthetic_noise"))
                assertEquals(listOf("synthetic_foreign"),AlphaAudioNative.read()!!.map {it.label})
                // Generic removal regression: no-label removal without parameters does not
                // match a parameterized compressor. The manager must not silently rewrite it.
                assertNotNull(MPVLib.commandNode("af","add","acompressor=ratio=2:threshold=0.25"))
                val before=AlphaAudioNative.read()!!.map {it.raw}
                MPVLib.commandNode("af","remove","acompressor")
                assertEquals(before,AlphaAudioNative.read()!!.map {it.raw})
                assertNotNull(MPVLib.commandNode("af","remove","acompressor=ratio=2:threshold=0.25"))
                assertEquals(speed,MPVLib.getPropertyDouble("speed")!!,0.0001)
                assertTrue(MPVLib.getPropertyDouble("time-pos")!!>=position)
                assertEquals(1.0,number("user-data/synthetic-starts")!!,0.0)
            }
        } finally {
            main {
                monitor?.close()
                if(created)runCatching {view?.destroy()}
                view?.cleanupCustomScripts()
                MpvEngineGate.release(owner)
            }
            root.deleteRecursively()
        }
    }
    /** Public format, zero weights only: no private user model or learned weights in Git/APK. */
    private fun syntheticModel():ByteArray {
        val out=StringBuilder("rnnoise-nu model file version 1\n")
        listOf(42 to 24,24 to 24,90 to 48,114 to 96,96 to 22,24 to 1).forEachIndexed {index,(inputs,neurons)->
            val gru=index in 1..3;val mul=if(gru)3 else 1
            out.append("$inputs $neurons 0\n")
            out.append(List(inputs*neurons*mul){"0"}.joinToString(" ")).append('\n')
            if(gru)out.append(List(neurons*neurons*3){"0"}.joinToString(" ")).append('\n')
            out.append(List(neurons*mul){"0"}.joinToString(" ")).append('\n')
        }
        return out.toString().toByteArray()
    }
}
