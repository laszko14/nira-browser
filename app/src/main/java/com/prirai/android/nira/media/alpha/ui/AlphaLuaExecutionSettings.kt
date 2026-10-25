package com.prirai.android.nira.media.alpha.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.prirai.android.nira.media.alpha.AlphaLuaController
import com.prirai.android.nira.media.alpha.AlphaLuaStatus

@Composable
internal fun AlphaLuaExecutionSettings(controller:AlphaLuaController?) {
    if(controller==null) { Text("Bridge Lua niedostępny w tej sesji.");return }
    val state by controller.state.collectAsState()
    val context=LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    fun enable(value:Boolean) {
        if(!controller.setEnabled(value))Toast.makeText(context,
            "Nie udało się zapisać zgody. Wyłączenie obowiązuje natychmiast w tej sesji.",Toast.LENGTH_LONG).show()
    }
    TextButton(onClick={settings=true}) {
        Text("Wykonywanie skryptów Lua: "+if(state.enabled)"WŁĄCZONE" else "WYŁĄCZONE")
    }
    if(settings && !confirm)AlertDialog(
        onDismissRequest={settings=false},title={Text("Wykonywanie skryptów Lua")},
        text={Column {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Włączone",Modifier.weight(1f))
                Switch(checked=state.enabled,onCheckedChange={if(it)confirm=true else enable(false)})
            }
            Text(when {
                state.ready->"Bridge gotowy. onStartup wyłączone."
                !state.installed->"Bridge niezainstalowany. Uruchom następny film; bieżący nie będzie restartowany."
                !state.loadConfigured->"Nie potwierdzono konfiguracji ładowania bridge w libmpv."
                state.publishFailures>0->"Bridge uruchomiony, ale wystąpił błąd publikacji odpowiedzi. Brak potwierdzenia gotowości."
                state.registered->"Bridge zarejestrowany; oczekiwanie na potwierdzenie gotowości."
                else->"Bridge niegotowy. Poczekaj; jeśli nadal niedostępny, uruchom następny film. Bieżący film nie będzie restartowany."
            })
        }},
        confirmButton={TextButton(onClick={settings=false}) {Text("Zamknij")}},
    )
    if(confirm)AlertDialog(
        onDismissRequest={confirm=false},title={Text("Wykonywanie skryptów Lua")},
        text={Text("Skrypty Lua mogą zmieniać ustawienia odtwarzacza oraz korzystać z uprawnień aplikacji. Włączaj tylko zaufane skrypty.")},
        confirmButton={TextButton(onClick={enable(true);confirm=false}) {Text("Włącz wykonywanie Lua")}},
        dismissButton={TextButton(onClick={confirm=false}) {Text("Anuluj")}},
    )
}

internal fun alphaLuaFeedback(status:AlphaLuaStatus):String=when(status) {
    AlphaLuaStatus.OK->"Lua wykonane bez błędu. Efekt filtra lub zewnętrznego skryptu nie jest potwierdzony."
    AlphaLuaStatus.SYNTAX_ERROR->"Błąd składni Lua. Kod nie został wykonany."
    AlphaLuaStatus.RUNTIME_ERROR->"Błąd wykonania Lua; sprawdź kod i wymagane zależności. Część instrukcji mogła się wykonać."
    AlphaLuaStatus.TIMEOUT->"Brak potwierdzenia Lua. Nie ponawiaj w ciemno — kod mógł się wykonać."
    AlphaLuaStatus.SENT->"Wysłano do bridge; oczekiwanie na potwierdzenie."
    AlphaLuaStatus.DISABLED->"Wykonywanie Lua jest wyłączone. Włącz je w ustawieniach panelu dla zaufanych skryptów."
    AlphaLuaStatus.UNAVAILABLE->"Bridge Lua niedostępny lub niegotowy w tej sesji."
    AlphaLuaStatus.EMPTY->"Brak przypisanej akcji."
    AlphaLuaStatus.MISSING_BUTTON->"Przycisk został usunięty, wyłączony lub ma niejednoznaczne ID."
    AlphaLuaStatus.SEND_FAILED->"Nie udało się wysłać polecenia Lua."
    AlphaLuaStatus.BUSY->"Za dużo oczekujących poleceń Lua. Poczekaj na potwierdzenia."
    AlphaLuaStatus.NATIVE_FAILED->"Polecenie niedostępne lub odrzucone przez sesję MPV."
    AlphaLuaStatus.NATIVE_APPLIED->"Polecenie natywne wysłane."
    else->"Bridge Lua: ${status.name}"
}
