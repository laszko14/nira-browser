package com.prirai.android.nira.diagnostics.testlab

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/** Only nonzero scroll consumed by the child counts. This observer never consumes input. */
internal class TestLabScrollCapture {
    private var active:Pair<LabCapture,OperationToken>?=null
    fun consumed() {
        if(active!=null)return
        val capture=TestLab.capture()?:return
        val op=capture.operation(LabOperation.PANEL_SCROLL)?:return
        active=capture to op
    }
    fun finish(outcome:OperationOutcome=OperationOutcome.SUCCESS) {
        active?.let{it.first.end(it.second,outcome)};active=null
    }
}
internal class TestLabNestedScrollProbe:NestedScrollConnection {
    private val capture=TestLabScrollCapture()
    override fun onPostScroll(consumed:Offset,available:Offset,source:NestedScrollSource):Offset {
        if(consumed.x!=0f||consumed.y!=0f)capture.consumed()
        return Offset.Zero
    }
    override suspend fun onPostFling(consumed:Velocity,available:Velocity):Velocity {
        capture.finish();return Velocity.Zero
    }
    fun close(){capture.finish(OperationOutcome.INTERRUPTED)}
}
