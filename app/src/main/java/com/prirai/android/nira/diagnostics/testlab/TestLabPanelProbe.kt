package com.prirai.android.nira.diagnostics.testlab

import androidx.core.view.isVisible
import android.view.ViewTreeObserver
import android.view.Window

internal enum class PanelSide(val openOperation:LabOperation,val closeOperation:LabOperation) {
    LEFT(LabOperation.LEFT_PANEL_OPEN,LabOperation.LEFT_PANEL_CLOSE),
    RIGHT(LabOperation.RIGHT_PANEL_OPEN,LabOperation.RIGHT_PANEL_CLOSE)
}
internal data class GestureOrigin(val session:SessionToken,val offsetNanos:Long)

/** One Fragment-owned probe; no timer invents visibility or animation completion. */
internal class TestLabPanelProbe(private val side:PanelSide) {
    private var window:Window?=null
    private var observed=false
    private var listener:ViewTreeObserver.OnPreDrawListener?=null
    private var opened:OperationToken?=null
    private var closing:OperationToken?=null
    fun start(value:Window) {
        if(!TestLab.busy)return
        stop()
        observed=true;window=value;TestLab.attachWindow(value)
        opened=TestLab.panelOpening(side)
        val decor=value.decorView
        val draw=object:ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw():Boolean {
                if(decor.isShown&&decor.isVisible&&decor.width>0&&decor.height>0&&decor.alpha>0f) {
                    TestLab.end(opened,proof=CompletionProof.FIRST_PREDRAW);opened=null
                    if(decor.viewTreeObserver.isAlive)decor.viewTreeObserver.removeOnPreDrawListener(this)
                    listener=null
                }
                return true
            }
        }
        listener=draw;decor.viewTreeObserver.addOnPreDrawListener(draw)
    }
    fun closing(){if(observed&&closing==null)closing=TestLab.panelClosing(side,true)}
    fun cancelled(){TestLab.end(opened,OperationOutcome.CANCELLED);opened=null}
    fun dismissed() {
        if(!observed)return
        observed=false
        TestLab.end(opened,OperationOutcome.INTERRUPTED);opened=null
        val close=closing?:TestLab.panelClosing(side,false)
        TestLab.end(close);closing=null
        stop()
    }
    fun stop() {
        val old=window
        if(old!=null){
            listener?.let{if(old.decorView.viewTreeObserver.isAlive)old.decorView.viewTreeObserver.removeOnPreDrawListener(it)}
            TestLab.detachWindow(old)
        }
        TestLab.end(opened,OperationOutcome.INTERRUPTED);opened=null
        window=null;listener=null
    }
}
