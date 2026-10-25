package com.prirai.android.nira.media

import com.prirai.android.nira.media.alpha.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import kotlinx.serialization.builtins.nullable

class AlphaSettingsImportTest {
    private fun xml(vararg prefs:String,db:String="") =
        """<?xml version="1.0" encoding="UTF-8"?><mpvExSettings exportDate="synthetic"><preferences>${prefs.joinToString("")}</preferences>$db</mpvExSettings>"""
    private fun pref(key:String,type:String,value:String)=
        """<preference key="$key" type="$type" value="${value.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;")}"/>"""
    private fun parse(s:String)=AlphaSettingsImporter.parse(ByteArrayInputStream(s.toByteArray()))
    @Test fun canonicalFormatTypesAndDatabaseAreFiltered() {
        val plan=parse(xml(pref("lua_panel_columns","int","3"),
            pref("lua_soft_bg_argb","int","-16777216"),
            pref("lua_button_minimal_soft_v2","boolean","true"),
            pref("alpha_hud_position_x","float","0.25"),
            pref("alpha_hud_clock_color","string","#ABCDEF"),
            pref("unknown_long","long","123"),pref("unknown_set","stringSet","one|||two"),
            db="""<database><networkConnections><networkConnection host="PRIVATE_SENTINEL" password="SECRET_SENTINEL"/></networkConnections></database>"""))
        assertEquals(5,plan.matchedCount);assertEquals(2,plan.skippedCount)
        assertFalse(plan.toString().contains("SENTINEL"))
        val old=AlphaLuaConfig.defaults()
        val next=plan.applyTo(old,AlphaImportScope.ALL)
        assertEquals(AlphaPreferenceValue.IntValue(3),next.buttonLayout["lua_panel_columns"])
        assertEquals(AlphaPreferenceValue.IntValue(-16777216),next.luaAppearance["lua_soft_bg_argb"])
        assertEquals(old.slots,next.slots)
    }
    @Test fun previewPartialAndRepeatedImportPreserveCodesHolesAndIds() {
        val json="""{"slots":[{"id":"id","title":"custom","content":"unknown()","longPressContent":"long()","onStartup":"start()","alwaysVisible":true,"activeTextColor":-1},null]}"""
        val plan=parse(xml(pref("custom_buttons_json","string",json),pref("lua_panel_columns","int","3"),pref("lua_soft_shape","int","1")))
        val original=AlphaLuaConfig.defaults()
        val buttons=plan.applyTo(original,AlphaImportScope.BUTTONS_LAYOUT)
        assertEquals("id",buttons.slots[0]?.id);assertNull(buttons.slots[1])
        assertEquals("start()",buttons.slots[0]?.onStartup)
        assertEquals(original.luaAppearance,buttons.luaAppearance)
        val look=plan.applyTo(original,AlphaImportScope.APPEARANCE)
        assertEquals(original.slots,look.slots);assertEquals(original.buttonLayout,look.buttonLayout)
        val full=plan.applyTo(original,AlphaImportScope.ALL)
        assertEquals(full,plan.applyTo(full,AlphaImportScope.ALL))
        assertNotEquals(original,full);assertEquals(43,full.slots.size)
    }
    @Test fun boundsWarnWithoutDestroyingSourceValue() {
        val plan=parse(xml(pref("lua_button_scale_percent","int","999")))
        assertTrue(plan.warnings.isNotEmpty())
        assertEquals(AlphaPreferenceValue.IntValue(999),plan.applyTo(AlphaLuaConfig.defaults(),AlphaImportScope.ALL).luaAppearance["lua_button_scale_percent"])
        assertEquals(150,AlphaImportSchema.renderInt("lua_button_scale_percent",999))
    }
    @Test fun malformedDuplicateAndNonFiniteInputsFailClosed() {
        for (s in listOf(
            xml(pref("lua_panel_columns","int","3"),pref("lua_panel_columns","int","4")),
            xml(pref("alpha_hud_position_x","float","NaN")),
            xml(pref("alpha_hud_position_x","float","Infinity")),
            xml(pref("custom_buttons_json","string","x".repeat(1_048_577))),
            xml(pref("lua_panel_columns","string","3")),
            xml(pref("custom_buttons_json","string","{bad")),
            xml(pref("custom_buttons_json","string","""[{"id":"a","id":"b","title":"a","content":""}]""")),
            "<wrong/>",
            "<mpvExSettings version=\"2\"><preferences/></mpvExSettings>",
        )) {
            try { parse(s);fail("must reject invalid export") } catch (_:AlphaImportException) {}
        }
    }
    @Test fun dtdAndOversizeInputCannotResolveExternalEntities() {
        try {
            parse("""<!DOCTYPE mpvExSettings [<!ENTITY xxe SYSTEM "file:///synthetic-secret">]><mpvExSettings><preferences/></mpvExSettings>""")
            fail("must reject DTD")
        } catch(e:AlphaImportException) { assertEquals(AlphaImportError.UNSAFE_XML,e.code) }
        try {
            AlphaSettingsImporter.parse(ByteArrayInputStream(ByteArray(AlphaSettingsImporter.MAX_XML+1){32}))
            fail("must reject oversized input")
        } catch(e:AlphaImportException) { assertEquals(AlphaImportError.TOO_LARGE,e.code) }
    }

    @Test fun atomicImportUndoSurvivesManualEditAndFailure() {
        val disk=object:AlphaLuaStorage {
            var current:String?=null
            var undo:String?=null
            var fail=false
            var imports=0
            override fun read()=current
            override fun importBackup()=undo
            override fun write(value:String):Boolean { current=value;return true }
            override fun writeImport(value:String,before:String):Boolean {
                if(fail)return false
                current=value;undo=before;imports++;return true
            }
            override fun restoreImport(value:String):Boolean {
                if(fail)return false
                current=value;undo=null;return true
            }
        }
        val config=AlphaLuaConfig(disk)
        val before=config.state.value
        val plan=parse(xml(pref("lua_panel_columns","int","3")))
        disk.fail=true
        assertFalse(config.applyImport(plan,AlphaImportScope.ALL))
        assertEquals(before,config.state.value);assertNull(disk.undo)
        disk.fail=false
        assertTrue(config.applyImport(plan,AlphaImportScope.ALL))
        assertTrue(config.applyImport(plan,AlphaImportScope.ALL))
        assertEquals(1,disk.imports)
        disk.fail=true
        val imported=config.state.value
        assertFalse(config.restoreBeforeImport())
        assertEquals(imported,config.state.value);assertNotNull(disk.undo)
        disk.fail=false
        config.update { it.copy(hud=mapOf("manual" to AlphaPreferenceValue.Bool(true))) }
        val restarted=AlphaLuaConfig(disk)
        assertTrue(restarted.importBackupAvailable.value)
        assertTrue(restarted.restoreBeforeImport())
        assertEquals(before,restarted.state.value);assertNull(disk.undo)
    }

    @Test fun everyAllowedPrimitiveIsMappedAndUnknownValuesAreNotRetained() {
        for((key,rule) in AlphaImportSchema.rules) {
            val raw=when(rule.type) {
                "boolean" -> "false"
                "float" -> "0.25"
                "int" -> if("argb" in key)"-1" else (rule.min?.toInt()?:0).toString()
                else -> if(key.endsWith("_color") || key.endsWith("_color_hex"))"#AABBCC" else "clock|speed"
            }
            val plan=parse(xml(pref(key,rule.type,raw),pref("unknown_secret","string","SENTINEL")))
            val result=plan.applyTo(AlphaLuaConfig.defaults(),AlphaImportScope.ALL)
            assertNotNull(key,AlphaRenderedValues(result).value(key))
            assertFalse(result.toString().contains("SENTINEL"))
        }
    }
    @Test fun hudOrderColorsAndLegacyFallbackReachRenderedValues() {
        val plan=parse(xml(pref("alpha_hud_layout_v2","string","clock,speed|remaining"),
            pref("alpha_hud_scale_percent","int","999"),
            pref("alpha_hud_position_y","float","-10"),
            pref("alpha_hud_clock_color","string","white"),
            pref("three_finger_speed_panel_size_pct","int","120")))
        val result=plan.applyTo(AlphaLuaConfig.defaults(),AlphaImportScope.ALL)
        val rendered=AlphaRenderedValues(result)
        assertEquals(300,rendered.int("alpha_hud_scale_percent",100))
        assertEquals(0f,rendered.fraction("alpha_hud_position_y",.03f),0f)
        assertEquals("clock,speed|remaining",rendered.text("alpha_hud_layout_v2",""))
        assertEquals(120,rendered.int("lua_button_scale_percent",100))
        assertEquals(AlphaPreferenceValue.IntValue(999),result.hud["alpha_hud_scale_percent"])
    }
    @Test fun appearanceOnlyChangesExplicitOverridesForMatchingIds() {
        val old=AlphaLuaButton("same","old","keep()","long()","start()",backgroundColor=4,textColor=5)
        val base=AlphaPreferences(slots=List(43){if(it==2)old else null})
        val json="""{"slots":[{"id":"same","title":"new","content":"replace()","textColor":-1}]}"""
        val plan=parse(xml(pref("custom_buttons_json","string",json)))
        val result=plan.applyTo(base,AlphaImportScope.APPEARANCE)
        assertEquals(old.copy(textColor=-1),result.slots[2])
        assertNull(result.slots[0])
    }
    @Test fun migrationIsIdempotentAndExistingGroupsSurviveMissingPreferences() {
        val slots=MutableList<AlphaLuaButton?>(35){null}
        slots[30]=AlphaLuaButton("archived","archived","code()")
        val raw=kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(AlphaLuaButton.serializer().nullable),slots)
        val plan=parse(xml(pref("custom_buttons_json","string",raw)))
        val base=AlphaPreferences(hud=mapOf("keep" to AlphaPreferenceValue.Text("existing")))
        val once=plan.applyTo(base,AlphaImportScope.ALL)
        assertNull(once.slots[30]);assertEquals("archived",once.slots[35]?.id)
        assertEquals(once,plan.applyTo(once,AlphaImportScope.ALL))
        assertEquals(base.hud,once.hud)
    }
    @Test fun canonicalUtf8AndEscapedCodeSurviveWithoutExecution() {
        val raw="""[{"id":"a","title":"test","content":"line1\nline2 < &","onStartup":"never()"}]"""
        val parsed=parse("\uFEFF"+xml(pref("custom_buttons_json","string",raw)))
        assertEquals("line1\nline2 < &",parsed.slots!![0]!!.content)
        assertEquals("never()",parsed.slots!![0]!!.onStartup)
    }

    @Test fun full43SlotObjectKeepsEveryIdAndHistoricalOverflow() {
        val slots=List(43){AlphaLuaButton("id-$it","button","code-$it",onStartup="never-$it")}
        val plan=parse(xml(pref("custom_buttons_json","string",AlphaSlots.encode(slots))))
        assertEquals(slots,plan.slots)
        val result=plan.applyTo(AlphaLuaConfig.defaults(),AlphaImportScope.ALL)
        assertEquals(slots,result.slots)
        assertFalse(result.luaExecutionEnabled)
    }

    @Test fun canonicalEmptySlotObjectAndZeroLengthReadsAreSupported() {
        val source=xml(pref("custom_buttons_json","string","{}"))
        val bytes=source.toByteArray()
        val input=object:ByteArrayInputStream(bytes) {
            var first=true
            override fun read(b:ByteArray,off:Int,len:Int):Int {
                if(first) { first=false;return 0 }
                return super.read(b,off,len)
            }
        }
        val plan=AlphaSettingsImporter.parse(input)
        assertEquals(43,plan.slots!!.size)
        assertTrue(plan.slots!!.all { it==null })
        assertTrue(plan.applyTo(AlphaLuaConfig.defaults(),AlphaImportScope.ALL).slots.all { it==null })
    }
}
