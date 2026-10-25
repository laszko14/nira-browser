package com.prirai.android.nira.media.alpha.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.media.alpha.*
import com.prirai.android.nira.media.alpha.audio.*

/** Host wiring only; the grid, skins, editor and fonts come from canonical Alpha v54. */
@Composable
internal fun AlphaLuaPanel(
    session:AlphaSession,
    controlsVisible:Boolean,
    editRequest:Int,
    onDragging:(Boolean)->Unit,
    onInteraction:()->Unit,
) {
    val context=LocalContext.current
    val repairSource=remember(context) {runCatching {
        AlphaAudioRepairSource(context.assets.open("mpv/zavro_drc_de_repair.lua").bufferedReader().use {it.readText()})
    }.getOrNull()}
    val config=remember(context) { AlphaLuaRepository.get(context) }
    val prefs=remember(config) { AlphaLuaPreferences(config) }
    val snapshot by config.state.collectAsState()
    val state by session.state.collectAsState()
    val error by config.error.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var minimized by remember { mutableStateOf(false) }
    var resetConfirm by remember { mutableStateOf(false) }
    val positionX by prefs.luaButtonPositionX.collectAsState()
    val positionY by prefs.luaButtonPositionY.collectAsState()
    LaunchedEffect(editRequest) {
        if(editRequest>0) { editing=true;minimized=false }
    }
    LaunchedEffect(editing,dragging) { onDragging(editing || dragging) }
    LaunchedEffect(error) {
        if(error!=null) Toast.makeText(context,
            if(error=="recovered_previous") "Odtworzono poprzednią poprawną konfigurację panelu."
            else if(error=="corrupt_snapshot") "Uszkodzona konfiguracja panelu — użyto domyślnej; zapis źródłowy zachowano."
            else "Nie udało się zapisać konfiguracji panelu.",Toast.LENGTH_LONG).show()
    }
    DisposableEffect(Unit) { onDispose { onDragging(false) } }

    fun dismiss() { editing=false;minimized=false;onDragging(false);onInteraction() }
    fun action(id:String,long:Boolean) {
        onInteraction()
        val current=config.state.value
        val b=current.slots.filterNotNull().singleOrNull {it.id==id}?.takeIf {it.enabled}
        val code=b?.let {if(long)it.longPressContent else it.content}
        val kind=code?.let {repairSource?.kind(it)}
        val dsp=session.dsp
        val controller=session.lua
        val result=if(kind!=null && dsp!=null && controller?.state?.value?.enabled==true && controller.state.value.ready) {
            val request=dsp.prepare(kind)
            if(request==null) AlphaLuaStatus.BUSY else {
                val sent=controller.dispatch(repairSource!!.wrap(code!!,request)) {dsp.bridgeResult(request,it)}
                if(sent!=AlphaLuaStatus.SENT)dsp.cancel(request)
                sent
            }
        } else AlphaLuaActions.invoke(id,long,current,session)
        if(AlphaFeedbackPolicy.immediate(result))
            Toast.makeText(context,alphaLuaFeedback(result),Toast.LENGTH_SHORT).show()
    }
    val lua=session.lua
    if(lua!=null) {
        val execution by lua.state.collectAsState()
        var lastCompletion by remember(lua) { mutableLongStateOf(execution.completion) }
        LaunchedEffect(execution.completion) {
            if(execution.completion!=lastCompletion) {
                lastCompletion=execution.completion
                val result=execution.lastCompletion?:execution.status
                if(AlphaFeedbackPolicy.completion(result))
                    Toast.makeText(context,alphaLuaFeedback(result),Toast.LENGTH_LONG).show()
            }
        }
    }
    val dsp=session.dsp
    if(dsp!=null) {
        val feedback by dsp.state.collectAsState()
        var lastDsp by remember(dsp) {mutableLongStateOf(feedback.completion)}
        LaunchedEffect(feedback.completion) {
            if(lastDsp!=feedback.completion) {
                lastDsp=feedback.completion
                feedback.last?.takeIf {feedback.notify && it!=AlphaDspResult.VERIFIED}?.let {
                    Toast.makeText(context,alphaDspError(it),Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val buttons=snapshot.slots.filterNotNull().filter { it.enabled && it.title.isNotBlank() }.map { b ->
        AlphaLuaButtonState(b.id,b.title,true,b.alwaysVisible,b.backgroundColor,b.textColor,
            b.activeBackgroundColor,b.activeTextColor,
            active=snapshot.slots.count { it?.id==b.id }==1 && AlphaNativeButton.active(b,state))
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val top=(positionY+20).coerceIn(112,(maxHeight.value-160).toInt().coerceAtLeast(112))
        val height=(maxHeight.value-top-120).toInt().coerceAtLeast(40)
        LuaButtonGrid(
            preferences=prefs,buttons=buttons,playbackSpeed=(state.speed?:1.0).toFloat(),
            showAllButtons=controlsVisible || editing,editMode=editing,
            onButtonClick={action(it,false)},onButtonLongClick={action(it,true)},
            modifier=Modifier.padding(horizontal=16.dp).offset(y=top.dp),
            onDragStateChange={ dragging=it;onDragging(it || editing) },
            leftOffsetDp=positionX,previewDragging=dragging,availableHeightDp=height,
        )
        if(controlsVisible && !editing) {
            IconButton(
                onClick={editing=true;minimized=false;onInteraction()},
                modifier=Modifier.align(Alignment.TopEnd).padding(top=60.dp,end=12.dp),
            ) { Icon(Icons.Default.Settings,contentDescription="Edytuj panel Lua") }
        }
        if(editing) {
            LuaButtonsInPlayerDialog(
                importActions={
                    com.prirai.android.nira.media.alpha.audio.AlphaAudioSettings(session.audio)
                    CustomLuaScriptsSettings(session.customScripts)
                    AlphaLuaExecutionSettings(session.lua)
                    AlphaSettingsImport(config)
                    AlphaAudioRepairSettings(config,repairSource)
                },
                onReset={resetConfirm=true},
                prefs=prefs,engineStatus="Natywne pause/seek/speed + bridge Lua za zgodą użytkownika",
                onDismiss={dismiss()},hiddenDuringDrag=dragging,
                manualMinimized=minimized,onManualMinimize={minimized=it},
            )
        }
        if(resetConfirm) AlertDialog(
            onDismissRequest={resetConfirm=false},
            title={Text("Przywrócić domyślny panel?")},
            text={Text("Zastąpi to obecne sloty i wygląd panelu. Kod Lua nie zostanie uruchomiony.")},
            confirmButton={TextButton(onClick={config.reset();resetConfirm=false}) {Text("Przywróć")}},
            dismissButton={TextButton(onClick={resetConfirm=false}) {Text("Anuluj")}},
        )
    }
}
