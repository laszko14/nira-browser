package com.prirai.android.nira.media

import com.prirai.android.nira.media.alpha.*
import com.prirai.android.nira.media.alpha.scripts.*
import `is`.xyz.mpv.MPVNode
import org.junit.Assert.*
import org.junit.Test

class AlphaFeedbackTest {
    @Test fun sentSuccessReadyAndProbeAreSilent() {
        for(s in listOf(AlphaLuaStatus.SENT,AlphaLuaStatus.OK,AlphaLuaStatus.NATIVE_APPLIED,AlphaLuaStatus.READY,AlphaLuaStatus.REGISTERED)) {
            assertFalse(AlphaFeedbackPolicy.immediate(s));assertFalse(AlphaFeedbackPolicy.completion(s))
        }
    }
    @Test fun errorsHaveExactlyOneNotificationChannel() {
        for(s in AlphaLuaStatus.entries)assertFalse(AlphaFeedbackPolicy.immediate(s) && AlphaFeedbackPolicy.completion(s))
        assertTrue(AlphaFeedbackPolicy.completion(AlphaLuaStatus.RUNTIME_ERROR))
        assertTrue(AlphaFeedbackPolicy.completion(AlphaLuaStatus.SYNTAX_ERROR))
        assertTrue(AlphaFeedbackPolicy.completion(AlphaLuaStatus.TIMEOUT))
        assertTrue(AlphaFeedbackPolicy.immediate(AlphaLuaStatus.DISABLED))
    }
    @Test fun warnDoesNotBecomeRuntimeEvenWithNativeErrorLog() {
        assertTrue(AlphaFeedbackPolicy.warning(30));assertFalse(AlphaFeedbackPolicy.error(30))
        assertEquals("LOADED",AlphaFeedbackPolicy.clientStatus(true,false,false,false,false,9))
        assertEquals("LOAD_ERROR",AlphaFeedbackPolicy.clientStatus(false,false,false,false,false,1))
    }
    @Test fun topLevelCallbackSyntaxAndClientConflictAreSeparate() {
        assertEquals("TOP_LEVEL_ERROR",AlphaFeedbackPolicy.clientStatus(false,false,true,false,false,0))
        assertEquals("CALLBACK_ERROR",AlphaFeedbackPolicy.clientStatus(true,false,false,true,false,0))
        assertEquals("SYNTAX_ERROR",AlphaFeedbackPolicy.clientStatus(false,true,false,false,false,0))
        assertEquals("CLIENT_CONFLICT",AlphaFeedbackPolicy.clientStatus(false,false,false,false,true,0))
    }
    @Test fun actualAmbientFilenameMatchesAndUnrelatedFilesDoNot() {
        assertTrue(AlphaFeedbackPolicy.ambientFile("rex_ambient_plus_v4_123_1.glsl"))
        for(name in listOf("other.glsl","rex_ambient_plus_v4_123_1Xglsl","../rex_ambient_plus_v4_123_1.glsl"))assertFalse(AlphaFeedbackPolicy.ambientFile(name))
    }
    @Test fun rendererAndFreeBarsNeedActualReadback() {
        assertEquals("unknown",AmbientReadback.renderer(null));assertEquals("other",AmbientReadback.renderer("private-renderer-name"))
        assertEquals("BARS_PRESENT",AmbientReadback.condition("gpu",2400.0,1080.0,16.0/9))
        assertEquals("NO_BARS",AmbientReadback.condition("gpu",1920.0,1080.0,16.0/9))
        assertEquals("WAIT_VIDEO",AmbientReadback.condition("gpu",Double.NaN,1080.0,16.0/9))
        assertEquals("UNSUPPORTED_RENDERER",AmbientReadback.condition("gpu-next",2400.0,1080.0,16.0/9))
    }
    @Test fun gpuCompilationNeedsRecordedPassNotListOrArbitraryText() {
        assertFalse(AmbientReadback.executedPass(MPVNode.StringNode("REX Ambient+")))
        assertFalse(AmbientReadback.executedPass(MPVNode.MapNode(mapOf("path" to MPVNode.StringNode("REX Ambient+ private path")))))
        val pass=MPVNode.MapNode(mapOf("fresh" to MPVNode.ArrayNode(arrayOf(MPVNode.MapNode(mapOf("desc" to MPVNode.StringNode("user shader: REX Ambient+ v4 downsample")))))))
        assertTrue(AmbientReadback.executedPass(pass))
    }
    @Test fun feedbackKeepsOnlyNumericCounters() {
        val n=MPVNode.MapNode(mapOf("sent" to MPVNode.IntNode(3),"accepted" to MPVNode.IntNode(2),"errors" to MPVNode.IntNode(1),"text" to MPVNode.StringNode("synthetic secret")))
        val value=FeedbackCount.parse(n);assertEquals(3,value.sent);assertEquals(2,value.accepted);assertEquals(1,value.errors)
        assertFalse(value.toString().contains("secret"))
    }

    @Test fun nativeOsdKeepsPhysicalSizeInMiniPortraitAndLandscapeAndClearsHudOffset() {
        for(h in listOf(240,1080,2400)) {
            val plain=OsdGeometry.options(h,2.5,0.0)
            assertEquals(35.0,plain.getValue("osd-font-size").toDouble()*h/720.0,0.001)
            val withHud=OsdGeometry.options(h,2.5,120.0)
            assertTrue(withHud.getValue("osd-margin-y").toInt()>plain.getValue("osd-margin-y").toInt())
            assertEquals(plain,OsdGeometry.options(h,2.5,0.0))
        }
        assertTrue(OsdGeometry.options(0,2.5,0.0).isEmpty())
    }
}
