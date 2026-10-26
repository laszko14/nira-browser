package com.prirai.android.nira.diagnostics.testlab

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import androidx.lifecycle.viewModelScope
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestLabViewModelTest {
    private fun report(type:TestType):TestLabReport {
        var now=0L;val e=TestLabEngine({now},LabIdentity(1165,"0.5.22-alpha71-mpv39"));val t=e.start(type)!!
        e.begin(t);now=1_000_000_000;e.stop(t);e.complete(t);return e.latest()!!
    }
    @Test fun pendingSafPayloadIsTheRequestedImmutableReportEvenAfterSelectionChanges()=runTest {
        val dispatcher=StandardTestDispatcher(testScheduler);Dispatchers.setMain(dispatcher)
        val vm=TestLabViewModel(dispatcher)
        try {
            assertFalse(vm.queueExport(LabExportFormat.TXT))
            vm.showReport(report(TestType.TABS_GROUPS));advanceUntilIdle()
            assertTrue(vm.queueExport(LabExportFormat.TXT))
            val first=vm.detail.value!!.text
            vm.showReport(report(TestType.RESOURCES));advanceUntilIdle()
            assertEquals(first,vm.takeExport()!!.content)
            assertNull(vm.takeExport())
            assertNotEquals(first,vm.detail.value!!.text)
        } finally {vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
    @Test fun sixtySecondsUsesValidMinuteSecondNotation() {
        assertEquals("01:00",labClock(60));assertEquals("00:42",labClock(42));assertEquals("00:00",labClock(0))
    }
}
