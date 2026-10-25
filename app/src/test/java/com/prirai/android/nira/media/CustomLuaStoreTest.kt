package com.prirai.android.nira.media

import com.prirai.android.nira.media.alpha.scripts.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CustomLuaStoreTest {
    private fun store(block:(CustomLuaStore,File)->Unit) {
        val dir=Files.createTempDirectory("custom-lua-test").toFile()
        try {block(CustomLuaStore(dir),dir)} finally {dir.deleteRecursively()}
    }
    private fun src(s:String="mp.register_script_message('cycle',function() end)")=s.toByteArray()
    @Test fun importIsInertAndDurableAndExplicitlyEnabled()=store {s,d->
        s.importScripts(listOf("synthetic.lua" to src()))
        val row=s.state.value.scripts.single();assertFalse(row.enabled);assertEquals(0,s.selected().size)
        s.enable(row.id,true,true);assertTrue(CustomLuaStore(d).state.value.scripts.single().enabled)
        assertEquals(1,s.selected().size)
    }
    @Test fun updateNeedsNewConsentPreservesIdentityAndCanRestoreOriginal()=store {s,_->
        s.importScripts(listOf("synthetic.lua" to src()));val first=s.state.value.scripts.single()
        s.enable(first.id,true,true);s.importScripts(listOf("synthetic.lua" to src("return 2")))
        val next=s.state.value.scripts.single();assertEquals(first.id,next.id);assertFalse(next.enabled)
        assertEquals(first.hash,next.previous);s.restore(first.id);assertEquals(first.hash,s.state.value.scripts.single().hash)
    }
    @Test fun duplicateImportIsIdempotentAndDoesNotRevokeConsent()=store {s,_->
        val input=listOf("synthetic.lua" to src());s.importScripts(input);val id=s.state.value.scripts.single().id
        s.enable(id,true,true);val before=s.state.value;s.importScripts(input);assertEquals(before,s.state.value)
    }
    @Test fun batchImportAndClientNameCollisionsAreAtomic()=store {s,_->
        try{s.importScripts(listOf("a-b.lua" to src(),"a_b.lua" to src()));fail()}catch(_:CustomImportException){}
        assertTrue(s.state.value.scripts.isEmpty())
        s.importScripts(listOf("a.lua" to src(),"b.lua" to src()));assertEquals(2,s.state.value.scripts.size)
    }
    @Test fun traversalBridgeNamesInvalidUtf8NulAndOversizeRejected()=store {s,_->
        for(name in listOf("../evil.lua","/evil.lua","mpv_alpha_buttons_bridge.lua","a:b.lua","a.LUA/evil","a".repeat(61)+".lua","osc.lua"))
            try{s.importScripts(listOf(name to src()));fail(name)}catch(_:CustomImportException){}
        for(bytes in listOf(byteArrayOf(-1),byteArrayOf(0),ByteArray(CustomLuaStore.MAX_SCRIPT+1){65}))
            try{s.importScripts(listOf("x.lua" to bytes));fail()}catch(_:CustomImportException){}
        assertTrue(s.state.value.scripts.isEmpty())
    }
    @Test fun symlinkedObjectAndManifestCannotBeReadOrOverwritten()=store {s,d->
        val outside=Files.createTempFile("outside",".txt").toFile();outside.writeText("untouched")
        try {
            Files.createSymbolicLink(File(d,"manifest.json").toPath(),outside.toPath())
            try{s.importScripts(listOf("x.lua" to src()));fail()}catch(_:Exception){}
            assertEquals("untouched",outside.readText())
            assertFalse(CustomLuaStore(d).state.value.valid)
        }finally {outside.delete()}
    }
    @Test fun corruptManifestFailsClosedAndDoesNotOverwriteData()=store {_,d->
        File(d,"manifest.json").writeText("{broken")
        val s=CustomLuaStore(d);assertFalse(s.state.value.valid);assertTrue(s.selected().isEmpty())
        try{s.importScripts(listOf("x.lua" to src()));fail()}catch(_:Exception){}
        assertEquals("{broken",File(d,"manifest.json").readText())
    }
    @Test fun disableAndDeleteDoNotChangeUnrelatedEntries()=store {s,_->
        s.importScripts(listOf("a.lua" to src(),"b.lua" to src()))
        val a=s.state.value.scripts[0];val b=s.state.value.scripts[1]
        s.enable(a.id,true,true);s.enable(a.id,false,false);s.delete(a.id)
        assertEquals(listOf(b),s.state.value.scripts)
    }
    @Test fun activationRequiresIndependentExplicitConsent()=store {s,_->
        s.importScripts(listOf("a.lua" to src()));val id=s.state.value.scripts.single().id
        try{s.enable(id,true,false);fail()}catch(_:CustomImportException){}
        assertFalse(s.state.value.scripts.single().enabled)
    }
    @Test fun configAllowlistBoundsCacheAndRejectsSecurityOptions() {
        val plan=CustomMpvConf.parse("cache-secs=3600\ndemuxer-max-bytes=1024MiB\nspeed=1.5\nload-scripts=yes\ntls-verify=no\ninclude=/private\n".byteInputStream())
        assertEquals("300",plan.values["cache-secs"]);assertEquals("256MiB",plan.values["demuxer-max-bytes"])
        assertEquals("1.5",plan.values["speed"]);assertEquals(3,plan.skipped);assertEquals(2,plan.limited)
        assertFalse(plan.values.containsKey("tls-verify"))
    }
    @Test fun configMalformedDuplicateNanAndOversizeRejected() {
        for(text in listOf("speed=NaN","speed=1\nspeed=2","[profile]\nspeed=1","hwdec=malicious"))
            try{CustomMpvConf.parse(text.byteInputStream());fail(text)}catch(_:CustomImportException){}
    }
    @Test fun configIsInertUntilConfirmedAndPersists()=store {s,d->
        val plan=CustomMpvConf.parse("speed=1.25".byteInputStream());assertTrue(s.state.value.options.isEmpty())
        s.saveOptions(plan);assertEquals(plan.values,CustomLuaStore(d).state.value.options)
        s.saveOptions(CustomConfPlan(emptyMap(),0,0));assertTrue(s.state.value.options.isEmpty())
    }
    @Test fun malformedModelRejectedWithoutReplacingPrevious()=store {s,_->
        for(bytes in listOf(src("not a model"),src("rnnoise-nu model file version 1\n1 2 0\n0")))
            try{s.importModel(bytes);fail()}catch(_:CustomImportException){}
        assertNull(s.state.value.modelHash)
    }

    private fun model():ByteArray {
        val out=StringBuilder("rnnoise-nu model file version 1\n")
        val dimensions=listOf(42 to 1,1 to 1,44 to 1,44 to 1,1 to 22,1 to 1)
        dimensions.forEachIndexed {index,(inputs,neurons)->
            val gru=index in 1..3;val mul=if(gru)3 else 1
            out.append("$inputs $neurons 0\n")
            out.append(List(inputs*neurons*mul){"0"}.joinToString(" ")).append('\n')
            if(gru)out.append(List(neurons*neurons*3){"0"}.joinToString(" ")).append('\n')
            out.append(List(neurons*mul){"0"}.joinToString(" ")).append('\n')
        }
        return out.toString().toByteArray()
    }
    @Test fun modelValidationDurabilityAndBadReplacementAreAtomic()=store {s,d->
        val original=model();s.importModel(original);assertArrayEquals(original,CustomLuaStore(d).model())
        try{s.importModel(src("rnnoise-nu model file version 1\ninvalid"));fail()}catch(_:Exception){}
        assertArrayEquals(original,s.model())
    }
    @Test fun runtimeFreezesEnabledOriginalClientsAndModelBeforeInitialization()=store {s,_->
        s.importScripts(listOf("synthetic_client.lua" to src()));val row=s.state.value.scripts.single()
        s.enable(row.id,true,true);s.importModel(model())
        val config=Files.createTempDirectory("runtime-config").toFile()
        try {
            val template="source=@SOURCE@;property=@PROPERTY@;epoch=@EPOCH@"
            val r=CustomLuaRuntime.prepare(s,config,template)
            assertTrue(r.prepared);assertTrue(r.modelAvailable)
            assertEquals("synthetic_client.lua",File(r.paths.single()).name)
            val original=File(File(r.paths.single()).parentFile!!.parentFile,"originals/synthetic_client.lua")
            assertArrayEquals(src(),original.readBytes());assertArrayEquals(model(),File(config,"script-opts/std.rnnn").readBytes())
            s.importScripts(listOf("synthetic_client.lua" to src("return 2")))
            s.delete(row.id);assertArrayEquals(src(),original.readBytes())
            val unrelated=File(config,"unrelated.glsl");unrelated.writeText("keep")
            val shader=File(config,"rex_ambient_plus_v4_123_1.glsl");shader.writeText("temporary")
            r.cleanup();assertFalse(original.exists());assertFalse(shader.exists());assertEquals("keep",unrelated.readText())
        }finally {config.deleteRecursively()}
    }
    @Test fun runtimeNeverAutoloadsDisabledFilesAndRejectsSymlinkedModelDestination()=store {s,_->
        s.importScripts(listOf("disabled.lua" to src()));s.importModel(model())
        val config=Files.createTempDirectory("runtime-model").toFile()
        val outside=Files.createTempDirectory("outside-model").toFile()
        try {
            Files.createSymbolicLink(File(config,"script-opts").toPath(),outside.toPath())
            val rejected=CustomLuaRuntime.prepare(s,config,"@SOURCE@")
            assertFalse(rejected.prepared);assertTrue(rejected.paths.isEmpty());assertTrue(outside.listFiles()!!.isEmpty())
            Files.delete(File(config,"script-opts").toPath())
            val r=CustomLuaRuntime.prepare(s,config,"@SOURCE@")
            assertTrue(r.prepared);assertTrue(r.paths.isEmpty());r.cleanup()
        }finally {config.deleteRecursively();outside.deleteRecursively()}
    }
    @Test fun modifiedObjectCannotExecuteAndSourceNeverAppearsInStringRepresentation()=store {s,d->
        s.importScripts(listOf("x.lua" to src("synthetic_secret=1")))
        val row=s.state.value.scripts.single();s.enable(row.id,true,true)
        assertFalse(row.toString().contains("synthetic_secret"))
        File(d,"object-${row.hash}").writeText("tampered")
        try{s.selected();fail()}catch(_:Exception){}
    }

    @Test fun staleApprovalCannotEnableNewCodeAndSnapshotGroupsAreFrozen()=store {s,_->
        s.importScripts(listOf("x.lua" to src("return 1")))
        val old=s.state.value.scripts.single()
        s.enable(old.id,true,true,old.hash)
        val frozen=s.snapshot()
        s.importScripts(listOf("x.lua" to src("return 2")))
        try{s.enable(old.id,true,true,old.hash);fail()}catch(_:CustomImportException){}
        assertFalse(s.state.value.scripts.single().enabled)
        assertArrayEquals(src("return 1"),frozen.scripts.single().second)
    }

    @Test fun luaPathQuotingIsUtf8AndIndependentOfNumericLocale() {
        val previous=java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar"))
            assertEquals("\"\\047\\197\\130\"",CustomLuaRuntime.quote("/ł"))
        }finally {java.util.Locale.setDefault(previous)}
    }
}
