package com.prirai.android.nira.media

import com.prirai.android.nira.media.alpha.*
import com.prirai.android.nira.media.alpha.ui.*
import org.junit.Assert.*
import org.junit.Test

class AlphaLuaPanelTest {
    class Memory(var text:String?=null):AlphaLuaStorage {
        var fail=false
        override fun read()=text
        override fun write(value:String):Boolean { if(fail)return false; text=value; return true }
    }
    @Test fun saveReloadSwapAndAppearancePreserveEveryScriptField() {
        val disk=Memory()
        val config=AlphaLuaConfig(disk)
        val original=AlphaLuaButton("id","custom","not_supported()", "long()", "startup()",false,true,1,2,3,4,"icon")
        val slots=MutableList<AlphaLuaButton?>(43){null};slots[0]=original
        config.update { it.copy(slots=slots) }
        config.update { it.copy(slots=swapLuaSlotPositions(it.slots,0,42)) }
        val prefs=AlphaLuaPreferences(config)
        prefs.luaButtonFontSize.set(18)
        val reread=AlphaLuaConfig(disk).state.value
        assertNull(reread.slots[0]);assertEquals(original,reread.slots[42])
        assertEquals(18,AlphaLuaPreferences(AlphaLuaConfig(disk)).luaButtonFontSize.get())
        assertFalse(reread.luaExecutionEnabled)
    }
    @Test fun legacyAndCorruptionNeverOverwriteDiskOnRead() {
        val disk=Memory("""[{"id":"old","title":"old","content":"startup()"},null]""")
        val config=AlphaLuaConfig(disk)
        assertEquals("old",config.state.value.slots[0]?.id)
        assertEquals(43,config.state.value.slots.size)
        disk.text="{broken";val raw=disk.text
        val recovered=AlphaLuaConfig(disk)
        assertEquals(raw,disk.text);assertNotNull(recovered.error.value)
        disk.fail=true
        assertFalse(recovered.update { it.copy(slots=List(43){null}) })
        assertNotNull(recovered.state.value.slots[0])
    }
    @Test fun onlySingleWhitelistedNativeStatementIsRecognized() {
        assertEquals(AlphaCommand.Speed(1.5),AlphaNativeButton.command("""mp.set_property_number("speed", 1.5)""",AlphaPlaybackState()))
        assertEquals(AlphaCommand.Pause(false),AlphaNativeButton.command("""mp.commandv("cycle", "pause")""",AlphaPlaybackState(paused=true)))
        assertNull(AlphaNativeButton.command("""mp.set_property_number("speed", 1.5); evil()""",AlphaPlaybackState()))
        assertNull(AlphaNativeButton.command("""mp.commandv("loadfile","url")""",AlphaPlaybackState()))
        assertNull(AlphaNativeButton.command("""mp.set_property_number("speed", 4)""",AlphaPlaybackState()))
    }
    @Test fun originalSoftAlgorithmsAndNativeActiveState() {
        assertEquals(.55f,luaSoftV2BackgroundOpacity(0),.001f)
        assertEquals(0f,luaSoftV2BackgroundOpacity(100),.001f)
        assertTrue(luaSoftContrastOutlinePx(3)>luaSoftContrastOutlinePx(1))
        assertEquals("×1.5",luaSafeIconLabel("1.5x",null,2))
        val b=AlphaLuaButton("x","misleading","mp.set_property_number(\"speed\", 1.5)")
        assertTrue(AlphaNativeButton.active(b,AlphaPlaybackState(speed=1.5)))
        assertFalse(AlphaNativeButton.active(b.copy(content="audio()"),AlphaPlaybackState(speed=1.5)))
    }
    @Test fun backupAndResetKeepWritesExplicit() {
        val previous=AlphaLuaConfig.defaults().copy(slots=List(43){null})
        val raw=kotlinx.serialization.json.Json.encodeToString(AlphaPreferences.serializer(),previous)
        val disk=object:AlphaLuaStorage {
            override fun read()="{broken"
            override fun backup()=raw
            override fun write(value:String)=true
        }
        val config=AlphaLuaConfig(disk)
        assertTrue(config.state.value.slots.all { it==null })
        assertEquals("recovered_previous",config.error.value)
        assertTrue(config.reset())
        assertEquals(43,config.state.value.slots.size)
        assertFalse(config.state.value.luaExecutionEnabled)
    }
    @Test fun holesAndHistoricalOverflowAreNotDropTargets() {
        val a=AlphaLuaButton("a","a","")
        val slots=MutableList<AlphaLuaButton?>(43){null};slots[0]=a;slots[35]=a.copy(id="b")
        val moved=swapLuaSlotPositions(slots,0,35)
        assertEquals("b",moved[0]?.id);assertEquals("a",moved[35]?.id)
        assertEquals(slots,swapLuaSlotPositions(slots,0,30))
        assertEquals(30,LUA_MAIN_SLOT_COUNT);assertEquals(8,LUA_EXTRA_SLOT_COUNT)
    }
}
