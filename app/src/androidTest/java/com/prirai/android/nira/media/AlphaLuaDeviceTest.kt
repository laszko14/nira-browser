package com.prirai.android.nira.media

import android.os.SystemClock
import android.view.LayoutInflater
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prirai.android.nira.R
import com.prirai.android.nira.media.alpha.*
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlinx.serialization.json.JsonPrimitive

/** Real Android libmpv integration, synthetic source only, no URL/media or production owner. */
@RunWith(AndroidJUnit4::class)
class AlphaLuaDeviceTest {
    @Test fun realBridgeReadinessMultistatementErrorsAndCorrelatedRapidCommands() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val ticket="alpha-device-test"
        assertTrue("Close playback before instrumentation",MpvEngineGate.claim(ticket))
        val directory=File(context.cacheDir,"alpha-lua-integration")
        var view:ZavroMpvView?=null
        var controller:AlphaLuaController?=null
        var created=false
        val observer=object:MPVLib.EventObserver {
            override fun eventProperty(property:String) {}
            override fun eventProperty(property:String,value:Long) {}
            override fun eventProperty(property:String,value:Boolean) {}
            override fun eventProperty(property:String,value:Double) {}
            override fun eventProperty(property:String,value:String) {}
            override fun eventProperty(property:String,value:MPVNode) {
                if(property==AlphaLuaBridge.PROPERTY) {
                    val json=value.toJson()
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        controller?.onProperty(property,json)
                    }
                }
            }
            override fun event(eventId:Int,data:MPVNode) {}
        }
        fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
        fun await(condition:()->Boolean) {
            val deadline=SystemClock.elapsedRealtime()+8_000
            var satisfied=false
            while(!satisfied && SystemClock.elapsedRealtime()<deadline) {
                main { controller!!.tick();satisfied=condition() }
                if(!satisfied)SystemClock.sleep(50)
            }
            assertTrue("Real libmpv bridge did not acknowledge",satisfied)
        }
        try {
            main {
                directory.mkdirs()
                // Must remain present but MUST NOT autoload, even before consent.
                val unrelated=File(directory,"scripts/unrelated.lua")
                unrelated.parentFile!!.mkdirs()
                unrelated.writeText("mp.set_property_native('user-data/zavro-unrelated',true)")
                File(directory,"mpv.conf").writeText("load-scripts=yes\nscripts=${unrelated.absolutePath}\n")
                view=LayoutInflater.from(context).inflate(R.layout.zavro_mpv_surface,null,false) as ZavroMpvView
                assertTrue(view!!.prepareAlphaBridge(directory.absolutePath))
                created=true
                view!!.initialize(directory.absolutePath,context.cacheDir.absolutePath)
                controller=AlphaLuaController({MpvEngineGate.owns(ticket)},
                    { MPVLib.command(*it) },{SystemClock.elapsedRealtime()},enabled=true)
                assertTrue(view!!.alphaBridgeLoadConfigured)
                controller!!.start(true,view!!.alphaBridgeLoadConfigured)
            }
            // Simulate losing initial registration/probe events and the old 5 s window.
            SystemClock.sleep(5_500)
            main {
                assertTrue(controller!!.state.value.installed)
                assertFalse(controller!!.state.value.ready)
                MPVLib.addObserver(observer)
                MPVLib.observeProperty(AlphaLuaBridge.PROPERTY,MPVLib.MpvFormat.MPV_FORMAT_NODE)
            }
            await { controller!!.state.value.ready }
            main {
                assertTrue(controller!!.state.value.loaded)
                assertTrue(controller!!.state.value.registered)
                assertTrue(controller!!.state.value.probeAcknowledged)
                assertTrue(MPVLib.getPropertyNode(AlphaLuaBridge.PROPERTY) is MPVNode.MapNode)
                assertEquals("no",MPVLib.getPropertyString("options/load-scripts"))
                assertNull(MPVLib.getPropertyNode("user-data/zavro-unrelated"))
                // Reproduce the v1153 representation defect in the REAL library.
                val payload="""{"version":1}"""
                MPVLib.setPropertyString("user-data/zavro-string-test",payload)
                assertEquals(JsonPrimitive(payload).toString(),MPVLib.getPropertyString("user-data/zavro-string-test"))
                assertEquals(payload,(MPVLib.getPropertyNode("user-data/zavro-string-test") as MPVNode.StringNode).value)
                controller!!.dispatch("local speed=1.25; mp.set_property_number('speed',speed); mp.osd_message('LUA OK - 1.25x',2)")
            }
            await { controller!!.state.value.succeeded==1 }
            main {
                assertEquals(1.25,MPVLib.getPropertyDouble("speed")!!,0.0001)
                controller!!.dispatch("mp.set_property_number('speed',1.5); mp.set_property_bool('pause',false); mp.osd_message('LONG PRESS OK - 1.5x',2)")
            }
            await { controller!!.state.value.succeeded==2 }
            main {
                assertEquals(1.5,MPVLib.getPropertyDouble("speed")!!,0.0001)
                assertEquals(false,MPVLib.getPropertyBoolean("pause"))
                controller!!.dispatch("local u=require('mp.utils'); assert(type(u.format_json)=='function'); "+
                    "local p=mp.command_native({'expand-path','~~/synthetic-resource'}); "+
                    "assert(p=="+com.prirai.android.nira.media.alpha.scripts.CustomLuaRuntime.quote(directory.canonicalPath+"/synthetic-resource")+"); "+
                    "mp.osd_message('synthetic OSD',2)")
            }
            await { controller!!.state.value.succeeded==3 }
            main { controller!!.dispatch("local = broken") }
            await { controller!!.state.value.syntaxErrors==1 }
            main { controller!!.dispatch("error('synthetic failure')") }
            await { controller!!.state.value.runtimeErrors==1 }
            main { controller!!.dispatch("return 1");controller!!.dispatch("return 2") }
            await { controller!!.state.value.succeeded==5 }
            main {
                controller!!.setEnabled(false)
                assertEquals(AlphaLuaStatus.DISABLED,controller!!.dispatch("error('must not run')"))
                assertEquals(7,controller!!.state.value.callbacks)
            }
        } finally {
            main {
                controller?.close();controller=null
                MPVLib.removeObserver(observer)
                if(created)runCatching { view?.destroy() }
                MpvEngineGate.release(ticket)
            }
            directory.deleteRecursively()
        }
    }
}
