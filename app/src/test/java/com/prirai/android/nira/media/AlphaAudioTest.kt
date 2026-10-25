package com.prirai.android.nira.media

import com.prirai.android.nira.media.alpha.audio.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AlphaAudioTest {
    private fun eq(setting:AlphaEqSetting)=entry("lavfi",AlphaAfFilter.LABEL,setting.spec().substringAfter("lavfi=[").dropLast(1))
    private fun entry(name:String,label:String?,graph:String)=AlphaAfFilter(buildJsonObject {
        put("name",name);if(label!=null)put("label",label);put("enabled",true)
        put("params",buildJsonObject {put("graph",graph)})
    })
    private inner class Backend:AlphaAudioBackend {
        var chain=listOf<AlphaAfFilter>();var calls=0;var unavailable=false;var fail=false
        var mutateThenFail=false;var foreignRace=false
        override fun read()=if(unavailable)null else chain.toList()
        override fun replace(spec:String):Boolean {
            calls++;if(fail)return false
            val filter=entry("lavfi",AlphaAfFilter.LABEL,spec.substringAfter("lavfi=[").dropLast(1))
            val index=chain.indexOfFirst {it.label==AlphaAfFilter.LABEL}
            chain=chain.toMutableList().apply {if(index<0)add(filter) else set(index,filter)}
            if(foreignRace) {foreignRace=false;chain=chain+entry("lavfi","synthetic-race","volume=0.5")}
            if(mutateThenFail) {mutateThenFail=false;return false}
            return true
        }
        override fun remove():Boolean {calls++;if(fail)return false;chain=chain.filter {it.label!=AlphaAfFilter.LABEL};return true}
    }
    @Test fun exactCanonicalFrequenciesPresetsAndNoiseParameters() {
        assertEquals(listOf(60,230,910,3600,14000),RexEqualizerAudio.frequencies)
        assertEquals(13,RexEqualizerAudio.presets.size)
        assertEquals(listOf(-2,-1,0,3,5),RexEqualizerAudio.presets.single {it.first=="Treble Boost"}.second)
        assertEquals(listOf(-2,-1,1,2,-1),RexEqualizerAudio.presets.single {it.first=="Głos Naturalny"}.second)
        val mixes=listOf("0.70","0.35","0.50","0.65","0.20","0.85","0.95")
        for(i in 1..7)assertEquals("@rex_szum:lavfi=[arnndn=m='~~/script-opts/std.rnnn':mix=${mixes[i-1]}]",RexSzumAudio.specificationForLevel(i))
        assertNull(RexSzumAudio.specificationForLevel(0));assertNull(RexSzumAudio.specificationForLevel(8))
    }
    @Test fun everyOriginalPresetRoundTripsAndNoAutomaticActivation() {
        val b=Backend();val c=AlphaAudioController({true},b)
        c.refresh();assertNull(c.state.value.eq);assertEquals(0,b.calls)
        for((name,gains) in RexEqualizerAudio.presets) {
            assertTrue(c.preset(name));assertEquals(AlphaEqSetting(gains),c.state.value.eq)
            assertTrue(c.state.value.consistent);assertEquals(1,b.chain.size)
        }
        assertTrue(c.apply(null));assertTrue(b.chain.isEmpty())
    }
    @Test fun repeatedLevelsReplaceInPlaceWithoutDuplicatesOrForeignFilterLoss() {
        val b=Backend()
        val noise=entry("lavfi","rex_szum","synthetic opaque noise")
        val voice=entry("lavfi","rex_glos","synthetic opaque voice")
        b.chain=listOf(noise,eq(AlphaEqSetting(List(5){0})),voice)
        val c=AlphaAudioController({true},b)
        repeat(20) {assertTrue(c.preset(if(it%2==0)"Treble Boost" else "Rock"))}
        assertEquals(3,b.chain.size);assertEquals(noise.raw,b.chain[0].raw);assertEquals(voice.raw,b.chain[2].raw)
        c.refresh();assertTrue(c.state.value.noiseExternal);assertTrue(c.state.value.voiceExternal)
        assertTrue(c.apply(null));assertEquals(listOf(noise.raw,voice.raw),b.chain.map {it.raw})
    }
    @Test fun activationBeforeOrAfterForeignFiltersAndOffAreIndependent() {
        for(first in listOf(true,false)) {
            val b=Backend();val c=AlphaAudioController({true},b)
            val f=entry("lavfi","synthetic-user","volume=0.5")
            if(first)b.chain=listOf(f)
            assertTrue(c.preset("Pop"));if(!first)b.chain=b.chain+f
            c.refresh();assertEquals(1,c.state.value.externalCount)
            assertTrue(c.apply(null));assertEquals(listOf(f.raw),b.chain.map {it.raw})
        }
    }
    @Test fun falseCommandIsNotSuccessAndRestoresPreviousState() {
        val b=Backend();val c=AlphaAudioController({true},b)
        assertTrue(c.preset("Rock"));val before=b.chain.map {it.raw};val accepted=c.state.value.accepted
        b.fail=true;assertFalse(c.preset("Treble Boost"))
        assertEquals(before,b.chain.map {it.raw});assertEquals(accepted,c.state.value.accepted)
        assertEquals(AlphaAudioStatus.FAILED_ROLLED_BACK,c.state.value.status)
    }
    @Test fun partialBackendFailureUsesOnlyLabelScopedRollback() {
        val b=Backend();val c=AlphaAudioController({true},b);assertTrue(c.preset("Rock"))
        val foreign=entry("lavfi",null,"synthetic private \\ value")
        b.chain=b.chain+foreign;val before=b.chain.map {it.raw}
        b.mutateThenFail=true;assertFalse(c.preset("Pop"))
        assertEquals(before,b.chain.map {it.raw});assertEquals(AlphaAudioStatus.FAILED_ROLLED_BACK,c.state.value.status)
    }
    @Test fun externalRaceIsPreservedAndReportedRatherThanOverwritten() {
        val b=Backend();val c=AlphaAudioController({true},b);assertTrue(c.preset("Rock"))
        b.foreignRace=true;assertFalse(c.preset("Pop"))
        assertTrue(b.chain.any {it.label=="synthetic-race"})
        assertEquals(AlphaAudioStatus.ROLLBACK_UNCONFIRMED,c.state.value.status)
        assertTrue(c.state.value.conflicts>0)
    }
    @Test fun unknownOrDuplicateSameLabelIsNotHijacked() {
        val b=Backend();val c=AlphaAudioController({true},b)
        b.chain=listOf(entry("lavfi",AlphaAfFilter.LABEL,"unknown synthetic graph"))
        assertFalse(c.apply(null));assertEquals(0,b.calls)
        b.chain=listOf(eq(AlphaEqSetting(List(5){0})),eq(AlphaEqSetting(List(5){1})))
        assertFalse(c.preset("Pop"));assertEquals(0,b.calls)
    }
    @Test fun invalidParametersAndMissingReadbackNeverMutate() {
        val b=Backend();val c=AlphaAudioController({true},b)
        assertFalse(c.apply(AlphaEqSetting(listOf(1,2))))
        assertFalse(c.apply(AlphaEqSetting(List(5){16})))
        assertFalse(c.apply(AlphaEqSetting(List(5){0},11)))
        assertFalse(c.preset("unknown"));assertEquals(0,b.calls)
        b.unavailable=true;assertFalse(c.preset("Rock"));assertEquals(0,b.calls)
        assertFalse(c.state.value.ready);assertFalse(c.state.value.consistent)
    }
    @Test fun actualExternalEqChangesUpdateUiWithoutCommandOrCodeExecution() {
        val b=Backend();val c=AlphaAudioController({true},b)
        b.chain=listOf(eq(AlphaEqSetting(listOf(-2,-1,0,3,5))))
        c.refresh();assertEquals("Treble Boost",c.state.value.eq?.preset());assertEquals(0,b.calls)
        b.chain=emptyList();c.refresh();assertNull(c.state.value.eq)
        assertEquals(listOf(-2,-1,0,3,5),c.state.value.choice.gains)
    }
    @Test fun sessionRevocationCloseAndNewSessionAreIsolated() {
        var owner=true;val b=Backend();val c=AlphaAudioController({owner},b)
        assertTrue(c.preset("Rock"));owner=false
        assertFalse(c.apply(null));val calls=b.calls;c.close();owner=true
        assertFalse(c.preset("Pop"));assertEquals(calls,b.calls)
        val next=AlphaAudioController({true},Backend());next.refresh();assertNull(next.state.value.eq)
        assertEquals(0,next.state.value.sent)
    }
    @Test fun customBoostAndPrivateForeignValuesAreNotInDiagnostics() {
        val b=Backend();val owner=Any()
        val c=AlphaAudioController({true},b) {AlphaAudioDiagnostics.update(owner,it)}
        b.chain=listOf(entry("lavfi","private-label","synthetic secret"))
        assertTrue(c.apply(AlphaEqSetting(listOf(1,2,3,4,5),2)))
        assertEquals(2,c.state.value.eq!!.boost)
        val report=AlphaAudioDiagnostics.report(owner)
        assertFalse(report.contains("private-label"));assertFalse(report.contains("synthetic secret"))
        assertTrue(report.contains("Operacje potwierdzone: 1"))
        assertTrue(AlphaAudioDiagnostics.report(Any()).contains("Polecenia wysłane: 0"))
    }
    @Test fun lateClosedControllerCannotOverwriteNewSessionDiagnostics() {
        val first=Any();val next=Any()
        val old=AlphaAudioController({true},Backend()) {AlphaAudioDiagnostics.update(first,it)}
        old.refresh();old.close()
        val current=AlphaAudioController({true},Backend()) {AlphaAudioDiagnostics.update(next,it)}
        current.refresh();assertTrue(current.preset("Rock"))
        assertFalse(old.preset("Pop"));assertFalse(old.preset("unknown"));assertFalse(old.apply(null))
        assertTrue(AlphaAudioDiagnostics.report(next).contains("EQ: Rock"))
        assertTrue(AlphaAudioDiagnostics.report(next).contains("Operacje potwierdzone: 1"))
    }

}
