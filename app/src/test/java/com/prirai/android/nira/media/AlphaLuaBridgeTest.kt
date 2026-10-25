package com.prirai.android.nira.media

import com.prirai.android.nira.media.alpha.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class AlphaLuaBridgeTest {
    private class Fixture(enabled:Boolean=true) {
        var time=0L
        var owner=true
        val commands=mutableListOf<Array<String>>()
        val bridge=AlphaLuaController({owner},{commands.add(it)},{time},enabled=enabled)
        fun ready() {
            bridge.start(true)
            val epoch=commands.first()[2]
            bridge.onProperty(AlphaLuaBridge.PROPERTY,"""{"version":1,"epoch":"$epoch","ready":true,"results":[]}""")
        }
        fun reply(vararg statuses:String) {
            val evals=commands.filter { it[1]=="mpv_alpha_eval" }
            val epoch=commands.first()[2]
            val entries=statuses.mapIndexed { i,s -> """{"id":"${evals[i][3]}","status":"$s"}""" }.joinToString(",")
            bridge.onProperty(AlphaLuaBridge.PROPERTY,"""{"version":1,"epoch":"$epoch","ready":true,"results":[$entries]}""")
        }
    }
    @Test fun installationUsesConfigScriptsIsIdempotentAndLeavesUnrelatedFiles() {
        val root=Files.createTempDirectory("alpha-test").toFile()
        try {
            val other=root.resolve("scripts/other.lua");other.parentFile!!.mkdirs();other.writeText("-- untouched")
            assertTrue(AlphaLuaBridge.install(root))
            val file=root.resolve("scripts/${AlphaLuaBridge.FILE_NAME}")
            val content=file.readText();val time=file.lastModified()
            assertTrue(AlphaLuaBridge.install(root))
            assertEquals(time,file.lastModified());assertEquals(content,file.readText())
            assertEquals("-- untouched",other.readText())
            assertEquals(2,other.parentFile!!.listFiles()!!.size)
        } finally { root.deleteRecursively() }
    }
    @Test fun installationFailureDoesNotTouchOutsideScriptsDirectory() {
        val root=Files.createTempDirectory("alpha-test").toFile()
        val outside=Files.createTempDirectory("alpha-outside").toFile()
        try {
            Files.createSymbolicLink(root.resolve("scripts").toPath(),outside.toPath())
            assertFalse(AlphaLuaBridge.install(root));assertEquals(0,outside.listFiles()!!.size)
        } finally { root.resolve("scripts").delete();root.deleteRecursively();outside.deleteRecursively() }
    }
    @Test fun readinessRequiresSessionHandshakeAndStartIsOnceOnly() {
        val f=Fixture();f.bridge.start(true)
        assertFalse(f.bridge.state.value.ready)
        f.bridge.onProperty(AlphaLuaBridge.PROPERTY,"""{"version":1,"epoch":"","ready":true,"results":[]}""")
        assertTrue(f.bridge.state.value.registered);assertFalse(f.bridge.state.value.ready)
        f.bridge.start(true);assertEquals(1,f.commands.size)
        f.ready();assertTrue(f.bridge.state.value.ready)
    }
    @Test fun noBridgeAndDisabledDispatchDoNotExecuteAnything() {
        val f=Fixture(false);f.ready()
        assertEquals(AlphaLuaStatus.DISABLED,f.bridge.dispatch("return 1"))
        assertEquals(1,f.commands.size)
        f.bridge.setEnabled(true)
        assertEquals(AlphaLuaStatus.SENT,f.bridge.dispatch("return 1"))
        f.bridge.setEnabled(false)
        assertEquals(AlphaLuaStatus.DISABLED,f.bridge.dispatch("return 2"))
        val unavailable=Fixture();unavailable.bridge.start(false)
        assertEquals(AlphaLuaStatus.UNAVAILABLE,unavailable.bridge.dispatch("return 1"))
        assertTrue(unavailable.commands.isEmpty())
    }
    @Test fun sentIsNotSuccessAndCoalescedCallbacksAreCorrelatedExactlyOnce() {
        val f=Fixture();f.ready()
        assertEquals(AlphaLuaStatus.SENT,f.bridge.dispatch("local x=1; x=x+1; return x"))
        f.bridge.dispatch("return 2")
        assertEquals(0,f.bridge.state.value.succeeded)
        f.reply("ok","runtime")
        assertEquals(1,f.bridge.state.value.succeeded);assertEquals(1,f.bridge.state.value.runtimeErrors)
        assertEquals(2,f.bridge.state.value.callbacks)
        f.reply("ok","runtime");assertEquals(2,f.bridge.state.value.callbacks)
    }
    @Test fun syntaxAndRuntimeAndTimeoutRemainDistinctAndLateRepliesAreIgnored() {
        val f=Fixture();f.ready();f.bridge.dispatch("bad");f.bridge.dispatch("error('synthetic')")
        f.reply("syntax","runtime")
        assertEquals(1,f.bridge.state.value.syntaxErrors);assertEquals(1,f.bridge.state.value.runtimeErrors)
        f.bridge.dispatch("return 3");f.time=5_001;f.bridge.tick()
        assertEquals(1,f.bridge.state.value.timeouts)
        f.reply("syntax","runtime","ok");assertEquals(0,f.bridge.state.value.succeeded)
    }
    @Test fun oldSessionMalformedOrForgedCallbacksCannotCompleteCurrentInvocation() {
        val f=Fixture();f.ready();f.bridge.dispatch("return 1")
        for(value in listOf("invalid","{}","""{"version":{},"ready":true}""",
            """{"version":1,"ready":true,"epoch":"other","results":[]}"""))
            f.bridge.onProperty(AlphaLuaBridge.PROPERTY,value)
        assertEquals(0,f.bridge.state.value.callbacks)
        f.reply("ok");assertEquals(1,f.bridge.state.value.succeeded)
    }
    @Test fun ownerRevocationAndClosePreventCommandsAndLateCallbacks() {
        val f=Fixture();f.ready();f.owner=false
        assertEquals(AlphaLuaStatus.UNAVAILABLE,f.bridge.dispatch("return 1"))
        f.owner=true;f.bridge.dispatch("return 2");f.bridge.close();f.reply("ok")
        assertEquals(0,f.bridge.state.value.succeeded);assertFalse(f.bridge.state.value.ready)
        assertEquals(AlphaLuaStatus.UNAVAILABLE,f.bridge.dispatch("return 3"))
        val fresh=Fixture();fresh.ready();assertEquals(0,fresh.bridge.state.value.sent)
    }
    @Test fun resolvesCurrentClickAndLongPressByIdWithoutStartupOrDisabledActions() {
        val f=Fixture();f.ready()
        val native=mutableListOf<AlphaCommand>()
        val session=AlphaSessionAdapter({true},f.bridge) {native.add(it)}
        val b=AlphaLuaButton("one","unused title","return 1","return 2","error('startup must never run')")
        val slots=MutableList<AlphaLuaButton?>(43){null};slots[0]=b
        fun prefs()=AlphaPreferences(slots=slots.toList())
        assertEquals(AlphaLuaStatus.SENT,AlphaLuaActions.invoke("one",false,prefs(),session))
        assertEquals("return 1",f.commands.last()[2])
        AlphaLuaActions.invoke("one",true,prefs(),session);assertEquals("return 2",f.commands.last()[2])
        slots[0]=b.copy(content="return 3")
        AlphaLuaActions.invoke("one",false,prefs(),session);assertEquals("return 3",f.commands.last()[2])
        slots[0]=b.copy(enabled=false)
        assertEquals(AlphaLuaStatus.MISSING_BUTTON,AlphaLuaActions.invoke("one",false,prefs(),session))
        slots[0]=b.copy(content=" ")
        assertEquals(AlphaLuaStatus.EMPTY,AlphaLuaActions.invoke("one",false,prefs(),session))
        slots[0]=null
        assertEquals(AlphaLuaStatus.MISSING_BUTTON,AlphaLuaActions.invoke("one",true,prefs(),session))
        assertEquals(3,f.bridge.state.value.sent);assertTrue(native.isEmpty())
    }
    @Test fun nativeCommandsStillWorkWithLuaDisabledAndNoDispatch() {
        val f=Fixture(false);f.ready()
        val native=mutableListOf<AlphaCommand>()
        val session=AlphaSessionAdapter({true},f.bridge) {native.add(it)}
        session.onFlag("pause",false)
        for(code in listOf("mp.commandv('cycle', 'pause')","mp.commandv('seek', 10, 'relative')","mp.set_property_number('speed', 2)")) {
            val slots=MutableList<AlphaLuaButton?>(43){null};slots[0]=AlphaLuaButton("n","n",code)
            assertEquals(AlphaLuaStatus.NATIVE_APPLIED,AlphaLuaActions.invoke("n",false,AlphaPreferences(slots=slots),session))
        }
        assertEquals(3,native.size);assertEquals(0,f.bridge.state.value.sent)
    }
    @Test fun consentStorageFailureCannotEnableAndDisableImmediatelyBlocks() {
        val bridge=AlphaLuaController({true},{},{0L},saveConsent={false})
        assertFalse(bridge.setEnabled(true));assertFalse(bridge.state.value.enabled)
        val f=Fixture();f.ready();assertTrue(f.bridge.setEnabled(false))
        assertEquals(AlphaLuaStatus.DISABLED,f.bridge.dispatch("return 1"))
    }
    @Test fun queueBoundAndDispatchFailureCannotReportSuccessOrLeakSource() {
        val f=Fixture();f.ready()
        repeat(16) { assertEquals(AlphaLuaStatus.SENT,f.bridge.dispatch("return 1")) }
        assertEquals(AlphaLuaStatus.BUSY,f.bridge.dispatch("return 1"))
        assertFalse(f.bridge.state.value.toString().contains("return 1"))
        assertEquals(0,f.bridge.state.value.succeeded)
    }
    @Test fun sendingAgainCannotOverwritePreviousCompletionWithSentNotification() {
        val f=Fixture();f.ready();f.bridge.dispatch("return 1");f.reply("syntax")
        f.bridge.dispatch("return 2")
        assertEquals(AlphaLuaStatus.SENT,f.bridge.state.value.status)
        assertEquals(AlphaLuaStatus.SYNTAX_ERROR,f.bridge.state.value.lastCompletion)
        assertEquals(1L,f.bridge.state.value.completion)
    }
    @Test fun sendFailureAndMissingReadinessStayUnconfirmedAndRecoverByProbe() {
        var time=0L
        var fail=false
        val commands=mutableListOf<Array<String>>()
        val bridge=AlphaLuaController({true},{if(fail)error("synthetic") else commands.add(it)},{time},enabled=true)
        bridge.start(true)
        assertEquals(AlphaLuaStatus.UNAVAILABLE,bridge.dispatch("return 1"))
        time=500;bridge.tick();assertEquals(2,commands.size)
        val epoch=commands.first()[2]
        bridge.onProperty(AlphaLuaBridge.PROPERTY,"""{"version":1,"epoch":"$epoch","ready":true,"results":[]}""")
        fail=true
        assertEquals(AlphaLuaStatus.SEND_FAILED,bridge.dispatch("return 2"))
        time=6_000;bridge.tick()
        assertEquals(0,bridge.state.value.sent);assertEquals(0,bridge.state.value.timeouts)
        assertEquals(0,bridge.state.value.succeeded)
    }

    @Test fun ambiguousIdsNeverExecuteEvenWhenOnlyOneCopyIsEnabled() {
        val f=Fixture();f.ready()
        val session=AlphaSessionAdapter({true},f.bridge) {}
        val slots=MutableList<AlphaLuaButton?>(43){null}
        slots[0]=AlphaLuaButton("same","first","return 1")
        slots[35]=slots[0]!!.copy(enabled=false)
        assertEquals(AlphaLuaStatus.MISSING_BUTTON,AlphaLuaActions.invoke("same",false,AlphaPreferences(slots=slots),session))
        assertEquals(0,f.bridge.state.value.sent)
    }
    @Test fun aggregateDiagnosticsAreSessionBoundAndContainOnlyCountsAndFlags() {
        val owner=Any()
        AlphaLuaDiagnostics.update(owner,AlphaLuaState(installed=true,ready=true,enabled=true,
            sent=2,succeeded=1,runtimeErrors=1))
        val report=AlphaLuaDiagnostics.report(owner)
        assertTrue(report.contains("Bridge gotowy: TAK"));assertTrue(report.contains("Wywołania: 2"))
        assertTrue(report.contains("Błędy wykonania: 1"))
        assertTrue(AlphaLuaDiagnostics.report(Any()).contains("Wywołania: 0"))
        assertFalse(report.contains("mpv_alpha_eval"))
    }

}
