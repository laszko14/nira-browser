// Visual extraction from MPV Alpha v54 AudioEffectsSection.kt / RexEqualizerSheet.kt,
// c6107ce14a9f1768c73f975c5d60b1d8bd71aaed, Apache-2.0.
// Koin/PlayerSheet/preferences replaced by the existing host's session/callbacks.
package com.prirai.android.nira.media.alpha.audio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun AlphaAudioSettings(controller:AlphaAudioController?) {
    var open by remember {mutableStateOf(false)}
    TextButton(onClick={open=true},enabled=controller!=null) {Text("Audio Alpha")}
    if(open && controller!=null) {
        LaunchedEffect(controller) {controller.refresh()}
        Dialog(onDismissRequest={open=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
            Surface(Modifier.fillMaxWidth(.94f).heightIn(max=560.dp),shape=RoundedCornerShape(16.dp),
                color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("Audio Alpha",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        TextButton(onClick={open=false}) {Text("Zamknij")}
                    }
                    AudioEffectsSection(controller)
                    RexEqualizerSheet(controller)
                }
            }
        }
    }
}

/** Original AudioLevelChips layout. Missing original model is explicit, never substituted. */
@Composable
internal fun AudioEffectsSection(controller:AlphaAudioController) {
    val state by controller.state.collectAsState()
    Text("Filtry audio",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
    AudioLevelChips("Szum (Lua)",0,7,{ },false)
    Text(if(state.noiseExternal)"Szum: filtr zewnętrznego Lua obecny, niezarządzany przez ten panel."
        else "Szum 1–7 wymaga oryginalnego std.rnnn. Model nie jest dostarczony w tej wersji; nie zastosowano zamiennika.",style=MaterialTheme.typography.bodySmall)
    if(state.voiceExternal)Text("Głos Lua: filtr zewnętrzny obecny, parametry niezweryfikowane.",style=MaterialTheme.typography.bodySmall)
    Text("Głos Lua 1–4: brak definicji w źródle v54. Potwierdzone profile głosu EQ są dostępne poniżej; to ten sam filtr EQ, nie niezależny Głos Lua.",style=MaterialTheme.typography.bodySmall)
}

@Composable
private fun AudioLevelChips(title:String,value:Int,max:Int,onSelected:(Int)->Unit,available:Boolean) {
    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(title,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold)
            Text(if(available)"$value/$max" else "Niedostępny",style=MaterialTheme.typography.labelMedium)
        }
        LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            items((0..max).toList()) {level->
                FilterChip(selected=available && value==level,onClick={onSelected(level)},enabled=available,
                    label={Text(if(level==0)"OFF" else level.toString())})
            }
        }
    }
}

/** Original preset chips, EQ switch and band readout, without starting another player. */
@Composable
internal fun RexEqualizerSheet(controller:AlphaAudioController) {
    val state by controller.state.collectAsState()
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Text("EQ",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        Switch(checked=state.eq!=null,onCheckedChange={controller.apply(if(it)state.choice else null)},
            enabled=state.ready && state.consistent)
    }
    LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        items(RexEqualizerAudio.presets,key={it.first}) {preset->
            FilterChip(selected=state.eq?.gains==preset.second,
                onClick={controller.preset(preset.first)},label={Text(preset.first)},
                enabled=state.ready && state.consistent && state.eq!=null)
        }
    }
    val eq=state.eq
    Text(if(!state.consistent)"EQ: stan niepotwierdzony / konflikt. Nie zmieniono obcych filtrów."
        else if(eq==null)"EQ: OFF" else "EQ: ${eq.preset()}")
    if(eq!=null)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
        RexEqualizerAudio.frequencies.forEachIndexed {i,f->
            Text("$f Hz\n${eq.gains[i]} dB",style=MaterialTheme.typography.labelSmall)
        }
    }
    if(state.lastFailure!=null)Text("Ostatnia operacja nie powiodła się: ${state.lastFailure}",color=MaterialTheme.colorScheme.error)
    Text("Stan: ${state.status}. Odczyt af potwierdza konfigurację, nie efekt słuchowy.",style=MaterialTheme.typography.bodySmall)
    Text("Inne filtry: ${state.externalCount}; ich parametry nie są pokazywane.",style=MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        TextButton(onClick={controller.apply(null)},enabled=state.ready && state.consistent && eq!=null) {Text("Wyłącz EQ")}
        TextButton(onClick={controller.apply(null)},enabled=state.ready && state.consistent) {Text("Wyłącz wszystkie własne")}
    }
    Text("Kontroler zarządza tylko @rex_visual_eq. Szum/Głos Lua i inne filtry pozostają nietknięte. Na starcie OFF; brak zapisu ustawień audio między sesjami.",style=MaterialTheme.typography.bodySmall)
}
