package com.prirai.android.nira.diagnostics.testlab

import mozilla.components.browser.state.action.*
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store
import java.util.concurrent.ConcurrentHashMap

/** Observes the reducer, never dispatches, selects, groups or persists a tab. IDs die at cleanup. */
internal class TestLabBrowserObserver(private val capture:LabCapture):Middleware<BrowserState,BrowserAction> {
    private val loads=ConcurrentHashMap<String,OperationToken>()
    @Volatile private var closed=false
    val pendingLoads:Int get()=loads.size
    override fun invoke(store:Store<BrowserState,BrowserAction>,next:(BrowserAction)->Unit,action:BrowserAction) {
        if(closed){next(action);return}
        val before=store.state
        val kind=when(action) {
            is TabListAction.AddTabAction->if(action.tab.parentId!=null)LabOperation.LINK_OPEN else LabOperation.TAB_OPEN
            is TabListAction.AddMultipleTabsAction->LabOperation.TAB_OPEN
            is TabListAction.SelectTabAction->LabOperation.TAB_SWITCH
            is TabListAction.RemoveTabAction,is TabListAction.RemoveTabsAction,
            is TabListAction.RemoveAllTabsAction,is TabListAction.RemoveAllNormalTabsAction,
            is TabListAction.RemoveAllPrivateTabsAction->LabOperation.TAB_CLOSE
            is TabListAction.MoveTabsAction->LabOperation.TAB_MOVE
            is TabListAction.RestoreAction->if(action.tabs.isNotEmpty()&&action.tabs.all{tab->before.undoHistory.tabs.any{it.state.id==tab.state.id}})LabOperation.TAB_UNDO else LabOperation.SESSION_RESTORE
            is EngineAction.GoBackAction,is EngineAction.GoForwardAction,is EngineAction.GoToHistoryIndexAction->LabOperation.HISTORY_NAVIGATION
            else->null
        }
        val op=kind?.let(capture::operation)
        try {next(action)} catch (failure:Throwable) {capture.end(op,OperationOutcome.FAILED);throw failure}
        if(closed)return
        val after=store.state
        if(op!=null) {
            when(action) {
                is TabListAction.AddTabAction->{
                    val added=after.tabs.firstOrNull{it.id==action.tab.id}
                    if(added!=null) {
                        capture.check(op,after.tabs.count{it.id==added.id}==1,LabInvariant.DUPLICATE_COMMIT)
                        fun canonical(id:String?)=id?.takeUnless{it=="profile_default"}
                        // Only explicit request scope is asserted; parent/selected id never imply grouping or private mode.
                        capture.check(op,added.content.private==action.tab.content.private&&canonical(added.contextId)==canonical(action.tab.contextId),LabInvariant.PROFILE_MISMATCH)
                        if(before.tabs.none{it.id==added.id})capture.check(op,
                            before.tabs.map{it.id}==after.tabs.filterNot{it.id==added.id}.map{it.id},LabInvariant.EXISTING_TABS_REORDERED)
                    }
                }
                is TabListAction.RestoreAction->capture.check(op,action.tabs.all{restored->after.tabs.any{it.id==restored.state.id}},LabInvariant.UNDO_MISMATCH)
                else->Unit
            }
            capture.end(op,if(before!=after)OperationOutcome.SUCCESS else OperationOutcome.CANCELLED,CompletionProof.REDUCER_COMMITTED)
        }
        if(before.selectedTabId!=after.selectedTabId) {
            val first=before.tabs.firstOrNull{it.id==before.selectedTabId}
            val second=after.tabs.firstOrNull{it.id==after.selectedTabId}
            if(first!=null&&second!=null) {
                val change=when {
                    first.content.private!=second.content.private->LabOperation.PRIVATE_SWITCH
                    first.contextId!=second.contextId->LabOperation.PROFILE_SWITCH
                    else->null
                }
                change?.let{capture.end(capture.operation(it,measureLatency=false),proof=CompletionProof.REDUCER_COMMITTED)}
            }
        }
        when(action) {
            is ContentAction.UpdateLoadingStateAction->{
                val live=after.tabs.any{it.id==action.sessionId}
                if(live&&action.loading) {
                    if(!loads.containsKey(action.sessionId)) {
                        if(loads.size>=128) {capture.error();loads.keys.firstOrNull()?.let{loads.remove(it)?.let{op->capture.end(op,OperationOutcome.INTERRUPTED)}}}
                        capture.operation(LabOperation.PAGE_LOAD)?.let{loads[action.sessionId]=it}
                        capture.note(LabCode.LOAD_START)
                    }
                } else if(live&&!action.loading) {
                    loads.remove(action.sessionId)?.let{capture.end(it);capture.note(LabCode.LOAD_COMPLETE)}
                }
            }
            is ContentAction.UpdateProgressAction->if(action.progress in 0..100)capture.note(LabCode.LOAD_PROGRESS,action.progress.toDouble())
            is ContentAction.UpdateUrlAction->{
                val old=before.tabs.firstOrNull{it.id==action.sessionId}
                if(old!=null&&old.content.url!=action.url)capture.note(if(old.content.loading)LabCode.LOAD_REDIRECT else LabCode.URL_CHANGE_IDLE)
            }
            is EngineAction.LinkEngineSessionAction->capture.note(LabCode.ENGINE_SESSION_READY)
            is EngineAction.UnlinkEngineSessionAction->capture.note(LabCode.ENGINE_SESSION_LOST)
            else->Unit
        }
        if(kind==LabOperation.TAB_CLOSE)loads.keys.filter{key->after.tabs.none{it.id==key}}.forEach{key->loads.remove(key)?.let{capture.end(it,OperationOutcome.CANCELLED)}}
    }
    fun close(){closed=true;loads.values.forEach{capture.end(it,OperationOutcome.INTERRUPTED)};loads.clear()}
}

internal class TestLabMiddleware:Middleware<BrowserState,BrowserAction> {
    override fun invoke(store:Store<BrowserState,BrowserAction>,next:(BrowserAction)->Unit,action:BrowserAction) {
        val observer=TestLab.browserObserver()
        if(observer==null)next(action) else observer.invoke(store,next,action)
    }
}
