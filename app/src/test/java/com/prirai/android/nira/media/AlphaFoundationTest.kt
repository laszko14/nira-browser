package com.prirai.android.nira.media

import com.prirai.android.nira.media.alpha.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AlphaFoundationTest {
    @Test fun mapsOnlyFinitePlaybackStateAndNeverOptimisticallyChangesIt() {
        val calls=mutableListOf<String>()
        val adapter=AlphaSessionAdapter({true}) { calls.add(it.toString()) }
        adapter.onNumber("time-pos",12.5)
        adapter.onNumber("duration",100.0)
        adapter.onNumber("speed",2.5)
        adapter.onFlag("pause",true)
        adapter.onNumber("time-pos",Double.NaN)
        assertEquals(12.5,adapter.state.value.positionSeconds!!,0.0)
        assertTrue(adapter.state.value.paused!!)
        assertEquals(AlphaCommandResult.APPLIED,adapter.execute(AlphaCommand.Speed(2.0)))
        assertEquals(2.5,adapter.state.value.speed!!,0.0)
        assertEquals(1,calls.size)
    }
    @Test fun rejectsInvalidCommandsAndRevokedOwner() {
        var owner=true
        var calls=0
        val adapter=AlphaSessionAdapter({owner}) { calls++ }
        assertEquals(AlphaCommandResult.INVALID,adapter.execute(AlphaCommand.Speed(Double.NaN)))
        assertEquals(AlphaCommandResult.INVALID,adapter.execute(AlphaCommand.SeekAbsolute(20.0)))
        adapter.onNumber("duration",100.0)
        assertEquals(AlphaCommandResult.INVALID,adapter.execute(AlphaCommand.SeekAbsolute(101.0)))
        assertEquals(AlphaCommandResult.INVALID,adapter.execute(AlphaCommand.SeekRelative(1000.0)))
        owner=false
        assertEquals(AlphaCommandResult.UNAVAILABLE,adapter.execute(AlphaCommand.Pause(false)))
        assertEquals(0,calls)
        adapter.invalidate()
        owner=true
        assertEquals(AlphaCommandResult.UNAVAILABLE,adapter.execute(AlphaCommand.Speed(2.0)))
    }
    @Test fun backendFailureDoesNotEscapeOrChangeState() {
        val adapter=AlphaSessionAdapter({true}) { error("native unavailable") }
        assertEquals(AlphaCommandResult.FAILED,adapter.execute(AlphaCommand.Pause(true)))
        assertNull(adapter.state.value.paused)
    }
    @Test fun inertPreferencesRoundTripKeepsTypesAndNeverEnablesLua() {
        val prefs=AlphaPreferences(
            hud=mapOf("alpha_hud_layout_v2" to AlphaPreferenceValue.Text("synthetic")),
            luaAppearance=mapOf("lua_button_base_argb" to AlphaPreferenceValue.IntValue(-1)),
            gestures=mapOf("hold_speed_quarter_steps" to AlphaPreferenceValue.Bool(true)))
        assertEquals(prefs,Json.decodeFromString<AlphaPreferences>(Json.encodeToString(prefs)))
        try {
            Json.decodeFromString<AlphaPreferences>("""{"luaExecutionEnabled":true}""")
            fail("Unknown execution flags must not be accepted")
        } catch (_:kotlinx.serialization.SerializationException) { }
    }
    @Test fun invalidatedAdapterCannotBeRevivedAndLinkageFailureIsContained() {
        val adapter=AlphaSessionAdapter({true}) { throw UnsatisfiedLinkError() }
        assertEquals(AlphaCommandResult.FAILED,adapter.execute(AlphaCommand.Pause(true)))
        adapter.onReady()
        assertTrue(adapter.state.value.ready)
        adapter.invalidate()
        adapter.onReady()
        adapter.onNumber("speed",2.0)
        assertEquals(AlphaPlaybackState(),adapter.state.value)
    }
    @Test fun gateStillHasExactlyOneOwner() {
        assertTrue(MpvEngineGate.claim("alpha-test"))
        try {
            assertTrue(MpvEngineGate.owns("alpha-test"))
            assertFalse(MpvEngineGate.claim("other"))
            MpvEngineGate.release("other")
            assertTrue(MpvEngineGate.owns("alpha-test"))
        } finally { MpvEngineGate.release("alpha-test") }
    }
    @Test fun slotsRoundTripPreservesHolesIdsScriptsAndOverrides() {
        val slots=MutableList<AlphaLuaButton?>(43) { null }
        slots[0]=AlphaLuaButton("id","test","return 1",backgroundColor=0x12345678)
        slots[35]=AlphaLuaButton("right","right","",onStartup="do_not_run()")
        val decoded=AlphaSlots.decode(AlphaSlots.encode(slots))
        assertEquals(slots,decoded)
        assertNull(decoded[1])
        assertFalse(AlphaPreferences().luaExecutionEnabled)
        assertFalse(AlphaLuaState().enabled)
    }
    @Test fun legacyMigrationMovesHistoricalSlotsWithoutDroppingOverflow() {
        val slots=MutableList<AlphaLuaButton?>(35) { null }
        slots[30]=AlphaLuaButton("old","old","")
        val migrated=AlphaSlots.normalize(slots)
        assertEquals(43,migrated.size)
        assertNull(migrated[30])
        assertEquals("old",migrated[35]?.id)
        val full=List(43) { AlphaLuaButton("$it","button","") }
        assertEquals(full,AlphaSlots.normalize(full))
        assertEquals(43,AlphaSlots.decode("""[{"id":"a","title":"a","content":""},null]""").size)
    }
}
