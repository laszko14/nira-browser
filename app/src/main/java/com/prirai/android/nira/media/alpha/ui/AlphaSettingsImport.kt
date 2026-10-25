package com.prirai.android.nira.media.alpha.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.media.alpha.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class LocalAlphaXml:ActivityResultContracts.OpenDocument() {
    override fun createIntent(context:Context,input:Array<String>):Intent =
        super.createIntent(context,input).putExtra(Intent.EXTRA_LOCAL_ONLY,true)
}

/** Ephemeral SAF result: no XML/URI is persisted, no persistent grants or playback commands. */
@Composable
internal fun AlphaSettingsImport(config:AlphaLuaConfig) {
    val context=LocalContext.current
    val coroutine=rememberCoroutineScope()
    val current by config.state.collectAsState()
    val canRestore by config.importBackupAvailable.collectAsState()
    var plan by remember { mutableStateOf<AlphaImportPlan?>(null) }
    var selection by remember { mutableStateOf(AlphaImportScope.ALL) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val picker=rememberLauncherForActivityResult(remember { LocalAlphaXml() }) { uri:Uri? ->
        if(uri!=null)coroutine.launch {
            busy=true;error=null;notice=null
            try {
                val parsed=withContext(Dispatchers.IO) {
                    require(uri.scheme=="content")
                    context.contentResolver.openInputStream(uri)?.use { AlphaSettingsImporter.parse(it) }
                        ?:throw AlphaImportException(AlphaImportError.INVALID_XML)
                }
                plan=parsed;selection=AlphaImportScope.ALL
            } catch(e:AlphaImportException) {
                error=when(e.code) {
                    AlphaImportError.TOO_LARGE -> "Plik przekracza limit 8 MiB."
                    AlphaImportError.UNSAFE_XML -> "Eksport zawiera niedozwolone deklaracje XML."
                    AlphaImportError.BAD_VERSION -> "Nieobsługiwana wersja formatu eksportu."
                    AlphaImportError.BAD_TYPE,AlphaImportError.BAD_VALUE -> "Niezgodny typ lub wartość ustawienia."
                    AlphaImportError.DUPLICATE_KEY -> "Eksport zawiera powtórzone klucze."
                    AlphaImportError.BAD_BUTTONS -> "Niepoprawne dane przycisków."
                    else -> "Plik nie jest poprawnym eksportem ustawień MPV Alpha."
                }
            } catch(_:Exception) { error="Nie można odczytać pliku ustawień." }
            finally {
                runCatching { context.revokeUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                busy=false
            }
        }
    }
    Column {
        TextButton(enabled=!busy,onClick={
            plan=null;error=null;notice=null
            picker.launch(arrayOf("application/xml","text/xml","application/octet-stream"))
        }) { Text(if(busy)"Odczytywanie…" else "Importuj ustawienia MPV Alpha") }
        if(canRestore)TextButton(enabled=!busy,onClick={
            coroutine.launch {
                busy=true
                val ok=withContext(Dispatchers.IO) { config.restoreBeforeImport() }
                notice=if(ok)"Przywrócono ustawienia sprzed importu." else "Nie udało się przywrócić ustawień."
                busy=false
            }
        }) {Text("Przywróć ustawienia sprzed importu")}
        notice?.let { Text(it,style=MaterialTheme.typography.labelSmall) }
    }
    error?.let { message -> AlertDialog(
        onDismissRequest={error=null},title={Text("Import ustawień")},
        text={Text(message)},confirmButton={TextButton(onClick={error=null}) {Text("OK")}},
    ) }
    plan?.takeIf { error==null }?.let { preview ->
        val candidate=preview.applyTo(current,selection)
        val validCandidate=remember(candidate) {
            runCatching { AlphaLuaConfig.decode(Json.encodeToString(candidate)) }.isSuccess
        }
        val p=AlphaRenderedValues(candidate)
        val found=preview.slots
        val font=luaTypefaces.getOrNull(p.int("lua_button_typeface",0))?.name?:"Domyślna"
        AlertDialog(
            onDismissRequest={if(!busy)plan=null},
            title={Text("Podgląd importu MPV Alpha")},
            text={
                Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState())) {
                    Text("Format zgodny z eksportem MPV Alpha v54."+
                        if(preview.versionDeclared)" Wersja schematu: 1." else " Eksporter nie zapisał numeru schematu.")
                    Text("Przyciski w pliku: ${found?.count { it!=null }?:0}")
                    if(found==null)Text("Brak przycisków w pliku — obecne pozostaną.")
                    Text("Po zastosowaniu: lewo ${candidate.slots.take(30).count { it!=null }}, "+
                        "prawo ${candidate.slots.drop(35).count { it!=null }}, archiwalne ${candidate.slots.slice(30..34).count { it!=null }}")
                    Text("Kolumny: ${p.int("lua_panel_columns",5)} / ${p.int("lua_right_columns",4)}")
                    Text("Styl: "+if(!p.bool("lua_button_minimal_soft",true))"Klasyczny"
                        else if(p.bool("lua_button_minimal_soft_v2",true))"Minimal Soft v2" else "Minimal Soft v1")
                    Text("Font: $font, ${p.int("lua_button_font_sp",11)} sp; "+
                        "skala ${p.int("lua_button_scale_percent",100)}%, "+
                        "${p.int("lua_button_width_dp",52)} × ${p.int("lua_button_height_dp",27)} dp")
                    Text("Zgodne preferencje w pliku: ${preview.matchedCount}; pominięte: ${preview.skippedCount}.")
                    Text("Pominięte wpisy połączeń bazy: ${preview.databaseRecords}.")
                    if(preview.warnings.isNotEmpty())Text(
                        "Ostrzeżenia: ${preview.warnings.size}. Wartości poza zakresem widoku będą ograniczone podczas renderowania; oryginały pozostaną w danych.")
                    if(preview.matchedCount==0)Text("Brak ustawień obsługiwanych przez v1152.")
                    if(!validCandidate)Text("Wybrany zakres przekracza limit modelu konfiguracji lub jest niepoprawny.")
                    Text("Natywna ścieżka prędkości obsługuje 0,5–3×. Dowolny Lua wymaga osobnej zgody w ustawieniach panelu; import jej nie włącza.")
                    Text("Transfer pozostaje ukryty bez rzeczywistego pomiaru. Układ może zostać dopasowany do dostępnego obszaru ekranu.")
                    if(selection==AlphaImportScope.APPEARANCE)
                        Text("Indywidualne kolory i ikony są przenoszone tylko dla zgodnych ID; kody i kolejność pozostają.")
                    Text("Import nie uruchamia kodu ani akcji startowych. Poprzednie ustawienia będzie można przywrócić.")
                    listOf(
                        AlphaImportScope.ALL to "Wszystko obsługiwane przez v1152",
                        AlphaImportScope.BUTTONS_LAYOUT to "Tylko przyciski i układ",
                        AlphaImportScope.APPEARANCE to "Tylko wygląd Lua i HUD",
                    ).forEach { (scope,label) ->
                        Row(Modifier.fillMaxWidth().clickable(enabled=!busy) {selection=scope}) {
                            RadioButton(selected=selection==scope,onClick={if(!busy)selection=scope})
                            Text(label,Modifier.padding(top=12.dp))
                        }
                    }
                }
            },
            confirmButton={TextButton(enabled=!busy && validCandidate && preview.matchedCount>0,onClick={
                coroutine.launch {
                    busy=true
                    val unchanged=candidate==current
                    val ok=withContext(Dispatchers.IO) { config.applyImport(preview,selection) }
                    if(ok) {
                        plan=null
                        notice=if(unchanged)"Konfiguracja już była zgodna z wybranym zakresem."
                            else "Ustawienia zaimportowane."
                    }
                    else error="Nie udało się zapisać importu. Bieżąca konfiguracja nie została zmieniona."
                    busy=false
                }
            }) {Text("Zastosuj")}},
            dismissButton={TextButton(enabled=!busy,onClick={plan=null}) {Text("Anuluj")}},
        )
    }
}
