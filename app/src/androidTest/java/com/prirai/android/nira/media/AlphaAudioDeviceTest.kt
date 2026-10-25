package com.prirai.android.nira.media

import android.os.SystemClock
import android.view.LayoutInflater
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prirai.android.nira.R
import com.prirai.android.nira.media.alpha.audio.*
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Real Android AF/readback with a single local synthetic audio source and null AO. */
@RunWith(AndroidJUnit4::class)
class AlphaAudioDeviceTest {
    @Test fun canonicalEqOnRealAudioPreservesForeignFiltersAndNeverReloads() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val owner="alpha-audio-device-test"
        assertTrue("Close current playback",MpvEngineGate.claim(owner))
        val dir=File(context.cacheDir,"alpha-audio-integration")
        var view:ZavroMpvView?=null;var created=false
        var audio:AlphaAudioController?=null
        val starts=AtomicInteger()
        val events=object:MPVLib.EventObserver {
            override fun eventProperty(property:String) {}
            override fun eventProperty(property:String,value:Long) {}
            override fun eventProperty(property:String,value:Double) {}
            override fun eventProperty(property:String,value:Boolean) {}
            override fun eventProperty(property:String,value:String) {}
            override fun eventProperty(property:String,value:MPVNode) {}
            override fun event(eventId:Int,data:MPVNode) {
                if(eventId==MPVLib.MpvEvent.MPV_EVENT_START_FILE)starts.incrementAndGet()
            }
        }
        fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
        try {
            main {
                dir.mkdirs()
                view=LayoutInflater.from(context).inflate(R.layout.zavro_mpv_surface,null,false) as ZavroMpvView
                assertTrue(view!!.prepareAlphaBridge(dir.absolutePath));created=true
                view!!.initialize(dir.absolutePath,context.cacheDir.absolutePath)
                assertTrue(MPVLib.setOptionString("ao","null")>=0)
                MPVLib.addObserver(events)
                // One initial test fixture load, never emitted by the audio controller.
                assertNotNull(MPVLib.commandNode("loadfile","av://lavfi:sine=frequency=14000:sample_rate=48000","replace"))
                audio=AlphaAudioController({MpvEngineGate.owns(owner)},AlphaAudioNative)
            }
            val until=SystemClock.elapsedRealtime()+10_000
            var active=false
            while(!active && SystemClock.elapsedRealtime()<until) {
                main {active=(MPVLib.getPropertyDouble("time-pos")?:0.0)>0.05}
                if(!active)SystemClock.sleep(50)
            }
            assertTrue("Synthetic audio did not start",active)
            main {
                assertEquals(1,starts.get())
                audio!!.refresh();assertNull(audio!!.state.value.eq)
                assertNotNull(MPVLib.commandNode("change-list","af","append","@synthetic_foreign:lavfi=[volume=0.5]"))
                val foreign=AlphaAudioNative.read()!!.single {it.label=="synthetic_foreign"}.raw
                val position=MPVLib.getPropertyDouble("time-pos")!!
                val speed=MPVLib.getPropertyDouble("speed")!!
                val paused=MPVLib.getPropertyBoolean("pause")
                for((name,gains) in RexEqualizerAudio.presets) {
                    assertTrue("AF did not confirm canonical preset",audio!!.preset(name))
                    assertEquals(gains,audio!!.state.value.eq!!.gains)
                    val af=AlphaAudioNative.read()!!
                    assertEquals(1,af.count {it.label==AlphaAfFilter.LABEL})
                    assertEquals(foreign,af.single {it.label=="synthetic_foreign"}.raw)
                }
                repeat(10) {assertTrue(audio!!.preset(if(it%2==0)"Rock" else "Treble Boost"))}
                assertTrue(audio!!.apply(null))
                assertEquals(listOf(foreign),AlphaAudioNative.read()!!.map {it.raw})
                assertEquals(speed,MPVLib.getPropertyDouble("speed")!!,0.00001)
                assertEquals(paused,MPVLib.getPropertyBoolean("pause"))
                assertTrue(MPVLib.getPropertyDouble("time-pos")!!>=position)
                assertEquals(1,starts.get())
                val before=AlphaAudioNative.read()!!.map {it.raw}
                assertFalse(AlphaAudioNative.replace("@rex_visual_eq:lavfi=[synthetic_missing_filter_v1155]"))
                assertEquals(before,AlphaAudioNative.read()!!.map {it.raw})
                audio!!.close();assertFalse(audio!!.preset("Rock"))
            }
        } finally {
            main {
                audio?.close();MPVLib.removeObserver(events)
                if(created)runCatching {view?.destroy()}
                MpvEngineGate.release(owner)
            }
            dir.deleteRecursively()
        }
    }
}
