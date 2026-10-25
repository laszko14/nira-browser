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
            override fun eventProperty(property:String,value:MPVNode) {}
            override fun eventProperty(property:String,value:String) {
                if(property==AlphaLuaBridge.PROPERTY)android.os.Handler(android.os.Looper.getMainLooper()).post {
                    controller?.onProperty(property,value)
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
                view=LayoutInflater.from(context).inflate(R.layout.zavro_mpv_surface,null,false) as ZavroMpvView
                assertTrue(view!!.prepareAlphaBridge(directory.absolutePath))
                created=true
                view!!.initialize(directory.absolutePath,context.cacheDir.absolutePath)
                controller=AlphaLuaController({MpvEngineGate.owns(ticket)},
                    { MPVLib.command(*it) },{SystemClock.elapsedRealtime()},enabled=true)
                MPVLib.addObserver(observer)
                MPVLib.observeProperty(AlphaLuaBridge.PROPERTY,MPVLib.MpvFormat.MPV_FORMAT_STRING)
                controller!!.start(true)
            }
            await { controller!!.state.value.ready }
            main { controller!!.dispatch("local x=2; x=x+3; mp.set_property('user-data/zavro-test', tostring(x))") }
            await { controller!!.state.value.succeeded==1 }
            main { assertEquals("5",MPVLib.getPropertyString("user-data/zavro-test")) }
            main { controller!!.dispatch("local = broken") }
            await { controller!!.state.value.syntaxErrors==1 }
            main { controller!!.dispatch("error('synthetic failure')") }
            await { controller!!.state.value.runtimeErrors==1 }
            main { controller!!.dispatch("return 1");controller!!.dispatch("return 2") }
            await { controller!!.state.value.succeeded==3 }
            main {
                controller!!.setEnabled(false)
                assertEquals(AlphaLuaStatus.DISABLED,controller!!.dispatch("error('must not run')"))
                assertEquals(5,controller!!.state.value.callbacks)
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
