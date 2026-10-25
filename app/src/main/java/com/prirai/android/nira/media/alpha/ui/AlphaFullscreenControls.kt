package com.prirai.android.nira.media.alpha.ui

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateUtils
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prirai.android.nira.media.alpha.AlphaCommand
import com.prirai.android.nira.media.alpha.AlphaSession
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Session/Android wiring only. Visual children are extracted Alpha v54 components.
 * No player/surface ownership, network sampling, Lua, settings import or audio effects.
 */
@Composable
internal fun AlphaFullscreenControls(
    session:AlphaSession,
    controlsVisible:Boolean,
    onInteraction:()->Unit,
    onDragging:(Boolean)->Unit,
    onBack:()->Unit,
    luaEditRequest:Int=0,
) {
    val state by session.state.collectAsState()
    val context=LocalContext.current
    var clock by remember { mutableStateOf("") }
    var battery by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    var pendingSeek by remember { mutableStateOf<Float?>(null) }
    var speedMenu by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        var ticks=0
        while (true) {
            clock=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date())
            if (ticks++%30==0) {
                val info=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level=info?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1
                val scale=info?.getIntExtra(BatteryManager.EXTRA_SCALE,-1)?:-1
                battery=if (level>=0 && scale>0) (level*100/scale).coerceIn(0,100) else -1
                val status=info?.getIntExtra(BatteryManager.EXTRA_STATUS,-1)
                charging=status==BatteryManager.BATTERY_STATUS_CHARGING || status==BatteryManager.BATTERY_STATUS_FULL
            }
            delay(1000L)
        }
    }
    DisposableEffect(Unit) {
        onDispose { onDragging(false) } // collectAsState/LaunchedEffect cancel with composition.
    }
    val duration=state.durationSeconds?.toFloat()?:Float.NaN
    val position=state.positionSeconds?.toFloat()?:Float.NaN
    val speed=state.speed?.toFloat()?:Float.NaN
    val remaining=hudRemainingTimes(duration,position,speed)
    fun interact(command:AlphaCommand) { onInteraction(); session.execute(command) }
    MaterialTheme(colorScheme=darkColorScheme(
        primary=Color(0xFFADC6FF),surfaceVariant=Color(0xFF44474F),
    )) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width=maxWidth
            HudBarContent(
                model=HudBarModel(
                    battery>=0,clock.isNotEmpty(),remaining.adjustedSeconds!=null,
                    remaining.originalSeconds!=null,state.speed!=null,
                    battery,charging,clock,
                    remaining.adjustedSeconds?.let { "⏱ "+DateUtils.formatElapsedTime(it) }?:"",
                    remaining.originalSeconds?.let { "↓ "+DateUtils.formatElapsedTime(it) }?:"",
                    speed,
                    when (battery) {
                        in 0..9 -> Color(0xFFEF4444)
                        in 10..19 -> Color(0xFFF59E0B)
                        else -> Color(0xFF4ADE80)
                    },Color(0xFFE2E8F0),Color(0xFFA7F3D0),
                    Color.White,Color(0xFF60E2F2),16.sp,18.dp,3.dp,
                    FontWeight.SemiBold,false,12.dp,1f,
                ),
                modifier=Modifier.align(Alignment.TopStart).padding(8.dp)
                    .background(Color(0xFF10151C).copy(alpha=.70f),RoundedCornerShape(10.dp))
                    .padding(8.dp),
                maxWidth=width-16.dp,
            )
            if (controlsVisible) {
                ControlsButton(
                    Icons.AutoMirrored.Filled.ArrowBack,onBack,
                    Modifier.align(Alignment.TopStart).padding(top=60.dp,start=16.dp).size(48.dp),
                    title="Wróć do mini MPV",
                )
                ControlsButton(
                    if (state.paused==true) Icons.Default.PlayArrow else Icons.Default.Pause,
                    { state.paused?.let { interact(AlphaCommand.Pause(!it)) } },
                    Modifier.align(Alignment.Center).size(64.dp),
                    title=if(state.paused==true) "Odtwórz" else "Pauza",
                )
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal=18.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        ControlsButton(Icons.Default.Replay10,{ interact(AlphaCommand.SeekRelative(-10.0)) },
                            Modifier.size(48.dp),title="Cofnij 10 s")
                        ControlsButton(Icons.Default.Forward10,{ interact(AlphaCommand.SeekRelative(10.0)) },
                            Modifier.size(48.dp),title="Do przodu 10 s")
                        Box {
                            Row(Modifier.clickable { speedMenu=true;onDragging(true);onInteraction() },
                                verticalAlignment=Alignment.CenterVertically) {
                                ControlsButton(Icons.Default.Speed,{ speedMenu=true;onDragging(true);onInteraction() },
                                    Modifier.size(48.dp),title="Prędkość")
                                Text(state.speed?.let { String.format(Locale.getDefault(),"%.2f×",it) }?:"--×",
                                    color=Color.White)
                            }
                            DropdownMenu(speedMenu,{speedMenu=false;onDragging(false);onInteraction()}) {
                                listOf(.5,1.0,1.25,1.5,1.75,2.0,2.5,3.0).forEach { value ->
                                    DropdownMenuItem(
                                        text={Text("$value×")},
                                        onClick={
                                            interact(AlphaCommand.Speed(value))
                                            speedMenu=false;onDragging(false);onInteraction()
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(56.dp).pointerInput(Unit) {
                        // Observe the entire touch, including a stationary hold before drag slop.
                        // Do not consume events: the original seekbar owns tap/drag.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                            onDragging(true);onInteraction()
                            try { waitForUpOrCancellation(pass=PointerEventPass.Final) }
                            finally { onDragging(false);onInteraction() }
                        }
                    },verticalAlignment=Alignment.CenterVertically) {
                        VideoTimer(pendingSeek?:position,false,Modifier.widthIn(min=56.dp))
                        StandardSeekbar(
                            position={pendingSeek?:position},duration=duration,
                            readAheadValue={position},
                            onSeek={
                                pendingSeek=it
                                onDragging(true)
                                onInteraction()
                            },
                            onSeekFinished={
                                pendingSeek?.let { interact(AlphaCommand.SeekAbsolute(it.toDouble())) }
                                pendingSeek=null;onDragging(false);onInteraction()
                            },
                            onSeekCancelled={pendingSeek=null;onDragging(false);onInteraction()},
                            modifier=Modifier.weight(1f).padding(horizontal=12.dp),
                        )
                        VideoTimer(if(duration.isFinite() && position.isFinite()) (duration-(pendingSeek?:position)).coerceAtLeast(0f) else Float.NaN,
                            true,Modifier.widthIn(min=56.dp))
                    }
                }
            }
            MiniProgressBar(position,duration,!controlsVisible,0.dp)
            AlphaLuaPanel(session,controlsVisible,luaEditRequest,onDragging,onInteraction)
        }
    }
}

/** Visual extraction from Alpha v54 Seekbar.kt / VideoTimer, without Koin or timer toggles. */
@Composable
internal fun VideoTimer(value:Float,isInverted:Boolean,modifier:Modifier=Modifier) {
    Text(
        text=if (value.isFinite() && value>=0) (if(isInverted) "-" else "")+
            DateUtils.formatElapsedTime(value.toLong()) else "--:--",
        modifier=modifier,
        color=Color.White,
        style=LocalTextStyle.current.copy(fontFeatureSettings="tnum"),
    )
}
