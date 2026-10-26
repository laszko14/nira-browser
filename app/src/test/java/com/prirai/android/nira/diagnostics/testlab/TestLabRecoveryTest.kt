package com.prirai.android.nira.diagnostics.testlab

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,manifest=Config.NONE)
class TestLabRecoveryTest {
    @Test fun optInAtomicBoundedThrottledAndOldGenerationCannotWriteAfterDisable() {
        val app=RuntimeEnvironment.getApplication();var now=0L;var wall=1_000_000_000L
        val store=TestLabRecoveryStore(app,{now},{wall})
        val input=TestLabRecovery(TestType.COMPLEX,LabIdentity(1165,"0.5.22-alpha71-mpv39"),LabPhase.RUNNING,3000.0,
            emptyMap(),(1L..1024).map{LabEvent(it,it,LabCode.MAIN_STALL,operationTags=LabOperation.entries.toList())})
        val gen=store.begin()
        assertFalse(store.save(gen,input));assertNull(store.read())
        assertTrue(store.setEnabled(true));assertTrue(store.save(gen,input))
        val file=File(app.filesDir,"testlab-recovery.json")
        assertTrue(file.length() in 1..65_536);assertTrue(store.read()!!.incomplete)
        now=4_999_999_999;assertFalse(store.save(gen,input));now=5_000_000_000;assertTrue(store.save(gen,input))
        store.setEnabled(false);assertFalse(file.exists());assertFalse(store.save(gen,input));assertNull(store.read())
        store.setEnabled(true);val next=store.begin();assertFalse(store.save(gen,input));assertTrue(store.save(next,input))
        store.end(gen);assertNotNull(store.read());store.end(next);assertNull(store.read())
        store.setEnabled(false)
    }
    @Test fun expiredCorruptOrOversizedRecoveryIsRemovedWithoutReadingExternalLogs() {
        val app=RuntimeEnvironment.getApplication();var wall=1_000_000_000L
        val store=TestLabRecoveryStore(app,{0L},{wall});store.setEnabled(true)
        val gen=store.begin();val snapshot=TestLabRecovery(TestType.STABILITY,LabIdentity(1165,"0.5.22-alpha71-mpv39"),LabPhase.RUNNING,0.0,emptyMap(),emptyList())
        store.save(gen,snapshot);wall+=86_400_001;assertNull(store.read())
        val file=File(app.filesDir,"testlab-recovery.json")
        file.writeText("invalid");file.setLastModified(wall);assertNull(store.read());assertFalse(file.exists())
        file.writeBytes(ByteArray(65_537));file.setLastModified(wall);assertNull(store.read());assertFalse(file.exists())
        store.setEnabled(false)
    }
}
