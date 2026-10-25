package com.prirai.android.nira.media.alpha.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.media.alpha.*
import com.prirai.android.nira.media.alpha.audio.*

/** Explicit revision chooser: never infers behaviour from the user's button title. */
@Composable
internal fun AlphaAudioRepairSettings(config:AlphaLuaConfig,source:AlphaAudioRepairSource?) {
    val snapshot by config.state.collectAsState()
    var open by remember {mutableStateOf(false)}
    var kind by remember {mutableStateOf("drc")}
    var selected by remember {mutableStateOf<AlphaLuaButton?>(null)}
    var menu by remember {mutableStateOf(false)}
    var restoring by remember {mutableStateOf(false)}
    var result by remember {mutableStateOf<String?>(null)}
    TextButton(onClick={open=true;result=null;selected=null},enabled=source!=null) {Text("Zastosuj poprawkę DRC/DE")}
    if(open && source!=null) AlertDialog(
        onDismissRequest={open=false},
        title={Text("Poprawione kody DRC / DE")},
        text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Wskaż istniejący przycisk. Nazwa, slot, styl, zgoda Lua i onStartup pozostają bez zmian. Zastąpimy wyłącznie kod click i long-press, zachowując oryginały w prywatnej kopii.")
            Text("Nie uruchamiamy kodu ani filtrów podczas zapisu. Po zastosowaniu rozpocznij świeży Play, aby uniknąć pozostawionych przez oryginał kompresorów bez etykiet. Nie usuwamy ich w ciemno.")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=!restoring,onClick={restoring=false;selected=null},label={Text("Poprawka")})
                FilterChip(selected=restoring,onClick={restoring=true;selected=null},label={Text("Oryginał")})
            }
            if(!restoring) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=kind=="drc",onClick={kind="drc"},label={Text("DRC 1–3")})
                    FilterChip(selected=kind=="de",onClick={kind="de"},label={Text("DE 1–5")})
                }
                Text(if(kind=="drc")"Oryginalne ratio/threshold/attack/release. Jeden nazwany acompressor przez lavfi, kontrola af i rollback."
                    else "Pięć różnych profili RexDialogueAudio z Alpha v54. Wymagane potwierdzone stereo; inne formaty zostaną jawnie odrzucone. Odsłuch na telefonie jest konieczny.")
            } else Text("Przywrócimy zapisany kod click/long-press, także jeśli był potem ręcznie edytowany. Pozostałe aktualne pola przycisku pozostaną. Nie zmieniamy trwających filtrów; rozpocznij nowy Play.")
            Box {
                TextButton(onClick={menu=true}) {Text(selected?.title?:"Wybierz przycisk i slot…")}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false},modifier=Modifier.heightIn(max=240.dp)) {
                    snapshot.slots.forEachIndexed {i,b->
                        if(b!=null && ((b.id in snapshot.audioRepairs)==restoring)) DropdownMenuItem(
                            text={Text("${i+1}. ${b.title}")},onClick={selected=b;menu=false})
                    }
                }
            }
            Text("Kliknięcie przełącza poziomy do OFF, przytrzymanie wyłącza tylko własny filtr. Sukces OSD wymaga odczytu af; widoczność natywnego OSD zależy od Androida.")
            result?.let {Text(it)}
        }},
        confirmButton={TextButton(enabled=selected!=null,onClick={
            val b=selected?:return@TextButton
            val ok=config.update {if(restoring)source.restore(it,b) else source.apply(it,b,kind)}
            result=if(ok) "Zapisano. Kod nie został wykonany. Rozpocznij świeży Play." else "Nie zapisano — konfiguracja zmieniła się lub wystąpił błąd. Wybierz przycisk ponownie."
            selected=null
        }) {Text(if(restoring)"Przywróć oryginał" else "Zastosuj poprawkę")}},
        dismissButton={TextButton(onClick={open=false}) {Text("Zamknij")}},
    )
}

internal fun alphaDspError(result:AlphaDspResult):String=when(result) {
    AlphaDspResult.UNSUPPORTED_FILTER -> "FFmpeg nie udostępnia wymaganego filtra. Poprzedni stan zachowano, jeśli odczyt to potwierdza."
    AlphaDspResult.CAPABILITY_UNCONFIRMED -> "Nie można potwierdzić dostępności filtra. Nie zmieniono DSP."
    AlphaDspResult.AUDIO_FORMAT -> "Dialogue Enhance wymaga potwierdzonego dźwięku stereo. Nie wykonano automatycznego downmixu."
    AlphaDspResult.CONFLICT -> "Konflikt łańcucha audio. Nie nadpisano obcych filtrów; sprawdź SUPERTEST i rozpocznij świeży Play."
    AlphaDspResult.AF_READ_FAILED,AlphaDspResult.AF_MISMATCH -> "Nie potwierdzono rzeczywistego filtra w af. Sprawdź stan rollbacku w SUPERTEST."
    AlphaDspResult.MPV_REJECTED -> "MPV odrzucił konfigurację lub inicjalizację filtra DSP. Sprawdź rollback w SUPERTEST."
    AlphaDspResult.BAD_CONFIGURATION -> "Niepoprawna konfiguracja poprawki audio. Zastosuj ją ponownie świadomie w ustawieniach."
    AlphaDspResult.LUA_EXCEPTION -> "Rzeczywisty wyjątek Lua w poprawce audio. Sprawdź stan DSP w SUPERTEST."
    AlphaDspResult.TIMEOUT -> "Brak potwierdzenia DSP. Polecenie nie zostanie wykonane ponownie automatycznie."
    AlphaDspResult.VERIFIED -> "" // success is deliberately silent on Android
}
