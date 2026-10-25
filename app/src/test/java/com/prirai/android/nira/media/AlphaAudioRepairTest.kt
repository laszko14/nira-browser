package com.prirai.android.nira.media

import com.prirai.android.nira.media.alpha.*
import com.prirai.android.nira.media.alpha.audio.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AlphaAudioRepairTest {
    private val source=AlphaAudioRepairSource(File("src/main/assets/mpv/zavro_drc_de_repair.lua").readText())
    private class Storage:AlphaLuaStorage {
        var text:String?=null;var fail=false
        override fun read()=text
        override fun write(value:String):Boolean {if(fail)return false;text=value;return true}
    }
    @Test fun explicitRepairAndRestoreKeepSlotsAppearanceStartupAndOriginalsAfterRestart() {
        val store=Storage();val config=AlphaLuaConfig(store)
        val b=AlphaLuaButton("synthetic","not a DRC title","private original()","private long()","neverRun()",true,true,-1,-2,-3,-4,"icon")
        config.update {it.copy(slots=it.slots.toMutableList().apply {this[42]=b},hud=mapOf("synthetic" to AlphaPreferenceValue.Text("keep")))}
        val before=config.state.value
        assertTrue(config.update {source.apply(it,b,"drc")})
        val reload=AlphaLuaConfig(store)
        val patched=reload.state.value.slots[42]!!
        assertEquals(b,patched.copy(content=b.content,longPressContent=b.longPressContent))
        assertEquals(43,reload.state.value.slots.size)
        assertEquals(before.slots.take(42),reload.state.value.slots.take(42))
        assertEquals(before.hud,reload.state.value.hud)
        assertEquals("drc",source.kind(patched.content))
        assertTrue(reload.update {source.restore(it,patched)})
        assertEquals(before,AlphaLuaConfig(store).state.value)
    }
    @Test fun failureLeavesBothCodesAndBackupUntouched() {
        val store=Storage();val config=AlphaLuaConfig(store);val b=config.state.value.slots[0]!!
        val old=config.state.value;store.fail=true
        assertFalse(config.update {source.apply(it,b,"de")})
        assertEquals(old,config.state.value);assertTrue(config.state.value.audioRepairs.isEmpty())
    }
    @Test fun staleAndDuplicateSelectionsDoNotOverwriteOriginalBackup() {
        val store=Storage();val config=AlphaLuaConfig(store);val b=config.state.value.slots[0]!!
        config.update {it.copy(slots=it.slots.map {v->if(v?.id==b.id)v.copy(title="edited") else v})}
        assertFalse(config.update {source.apply(it,b,"drc")})
        val edited=config.state.value.slots[0]!!
        assertTrue(config.update {source.apply(it,edited,"de")})
        val fixed=config.state.value.slots[0]!!
        assertFalse(config.update {source.apply(it,fixed,"drc")})
        assertEquals(edited.content,config.state.value.audioRepairs[b.id]!!.content)
    }
    @Test fun manualCodeEditsDoNotLoseOriginalOrCurrentStylesOnExplicitRestore() {
        val store=Storage();val config=AlphaLuaConfig(store);val b=config.state.value.slots[0]!!
        config.update {source.apply(it,b,"de")}
        config.update {it.copy(slots=it.slots.map {v->if(v?.id==b.id)v.copy(content="user edit",textColor=123) else v})}
        val edited=config.state.value.slots[0]!!
        assertTrue(config.update {source.restore(it,edited)})
        assertEquals(b.content,config.state.value.slots[0]!!.content)
        assertEquals(123,config.state.value.slots[0]!!.textColor)
    }
    @Test fun repairRecognitionRequiresExactShippedSourceNotTitleOrMarker() {
        assertNull(source.kind("-- Zavro audio repair v1 drc cycle\nunknown()"))
        assertNull(source.kind(source.code("drc",false)+"extra()"))
        assertEquals("de",source.kind(source.code("de",true)))
        assertTrue(source.wrap(source.code("de",false),"synthetic-call").startsWith("local zavro_repair_request="))
    }
    private class Harness {
        var time=0L;var mailbox:String?=null;var af:List<AlphaAfFilter>?=emptyList()
        val session=AlphaDspSession({true},{mailbox},{af},{time})
        fun prepare(kind:String="drc")=session.prepare(kind)!!
        fun receipt(id:String,kind:String="drc",status:String="VERIFIED",level:Int=1,rollback:String="NOT_NEEDED")=buildJsonObject {
            put("request",id);put("kind",kind);put("status",status);put("level",level)
            put("commands",1);put("rollback",rollback);put("available","YES");put("af",true)
        }
        fun respond(vararg r:JsonObject) {mailbox=buildJsonObject {put("version",1);put("results",JsonArray(r.toList()))}.toString();session.poll()}
        fun ack(id:String) {session.bridgeResult(id,AlphaLuaStatus.OK)}
    }
    @Test fun dspReceiptWaitsForExactBridgeAckAndRealAfIsIndependent() {
        val h=Harness();val id=h.prepare();val r=h.receipt(id)
        h.respond(r);assertEquals(0,h.session.state.value.verified)
        h.ack(id);h.session.poll()
        assertEquals(1,h.session.state.value.bridgeOk);assertEquals(1,h.session.state.value.verified)
        assertEquals("OFF",h.session.state.value.drc) // latest real read is NOT optimistic receipt
        h.session.poll();assertEquals(1,h.session.state.value.verified)
    }
    @Test fun wrongIdKindAndMalformedReceiptsCannotConfirmDsp() {
        val h=Harness();val id=h.prepare();h.ack(id)
        h.respond(h.receipt("old-session"),h.receipt(id,"de"))
        assertEquals(0,h.session.state.value.verified)
        h.mailbox="{bad";h.session.poll();assertEquals(0,h.session.state.value.verified)
        h.time=5001;h.session.poll();assertEquals(1,h.session.state.value.timeouts)
        h.respond(h.receipt(id));assertEquals(0,h.session.state.value.verified)
    }
    @Test fun rapidCallsPreserveCorrelationAndOnlyErrorsNotify() {
        val h=Harness();val a=h.prepare();val b=h.prepare("de");h.ack(a);h.ack(b)
        h.respond(h.receipt(a),h.receipt(b,"de","MPV_REJECTED",0,"VERIFIED"))
        val s=h.session.state.value
        assertEquals(1,s.verified);assertEquals(1,s.rejected);assertEquals(1,s.rollbacks)
        assertTrue(s.notify);assertEquals(AlphaDspResult.MPV_REJECTED,s.last)
    }
    @Test fun bridgeFailureHasOnlyExistingBridgeNotificationNoDspDuplicate() {
        val h=Harness();val a=h.prepare()
        h.session.bridgeResult(a,AlphaLuaStatus.TIMEOUT)
        h.time=6000;h.session.poll()
        assertEquals(1,h.session.state.value.timeouts);assertFalse(h.session.state.value.notify)
    }
    @Test fun actualBridgeLuaFailureIsCountedSeparatelyWithoutSecondNotification() {
        val h=Harness();val id=h.prepare()
        h.session.bridgeResult(id,AlphaLuaStatus.RUNTIME_ERROR)
        assertEquals(1,h.session.state.value.luaExceptions)
        assertFalse(h.session.state.value.notify)
        val next=h.prepare();h.session.bridgeResult(next,AlphaLuaStatus.SYNTAX_ERROR)
        assertEquals(1,h.session.state.value.configurationErrors)
        assertEquals(AlphaDspResult.BAD_CONFIGURATION,h.session.state.value.last)
    }
    @Test fun newSessionRejectsPreviousReceiptAndClosePreventsMoreCommands() {
        val h=Harness();val id=h.prepare()
        val next=Harness();next.respond(h.receipt(id));assertEquals(0,next.session.state.value.verified)
        h.session.close();assertNull(h.session.prepare("drc"))
    }
    private fun filter(kind:String,level:Int)=AlphaAfFilter(buildJsonObject {
        put("label",if(kind=="drc")AlphaDspSession.DRC_LABEL else AlphaDspSession.DE_LABEL)
        put("name","lavfi");put("enabled",true)
        put("params",buildJsonObject {put("graph",(if(kind=="drc")AlphaDspSession.drcGraphs else AlphaDspSession.deGraphs)[level-1])})
    })
    @Test fun nativeChainLevelAllProfilesDuplicatesMissingAndUnknownParams() {
        for(graph in AlphaDspSession.drcGraphs+AlphaDspSession.deGraphs)assertTrue(source.code("drc",false).contains(graph))
        for(i in 1..3)assertEquals("$i",AlphaDspSession.readLevel(listOf(filter("drc",i)),"drc"))
        for(i in 1..5)assertEquals("$i",AlphaDspSession.readLevel(listOf(filter("de",i)),"de"))
        assertEquals("UNCONFIRMED",AlphaDspSession.readLevel(listOf(filter("de",1),filter("de",1)),"de"))
        assertEquals("OFF",AlphaDspSession.readLevel(emptyList(),"de"))
        val h=Harness();h.af=null;h.session.poll();assertFalse(h.session.state.value.afRead)
    }
    @Test fun reportIsSessionScopedAndContainsNoReceiptOrPrivateData() {
        val h=Harness();val owner=Any();val id=h.prepare();h.ack(id);h.respond(h.receipt(id))
        AlphaDspDiagnostics.update(owner,h.session.state.value)
        val report=AlphaDspDiagnostics.report(owner,0)
        assertFalse(report.contains(id));assertFalse(report.contains("threshold"));assertFalse(report.contains("synthetic"))
        assertTrue(report.contains("DSP potwierdzone: 1"))
        assertTrue(AlphaDspDiagnostics.report(Any(),0).contains("DSP potwierdzone: 0"))
    }
}
