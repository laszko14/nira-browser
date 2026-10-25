package com.prirai.android.nira.media.alpha.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.prirai.android.nira.media.alpha.scripts.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class LocalLuaFiles:ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context:Context,input:Array<String>):Intent =
        super.createIntent(context,input).putExtra(Intent.EXTRA_LOCAL_ONLY,true)
}
private class LocalLuaResource:ActivityResultContracts.OpenDocument() {
    override fun createIntent(context:Context,input:Array<String>):Intent =
        super.createIntent(context,input).putExtra(Intent.EXTRA_LOCAL_ONLY,true)
}
private fun documentName(context:Context,uri:Uri):String {
    require(uri.scheme=="content")
    return context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
        require(it.moveToFirst());it.getString(0)
    }?:throw CustomImportException("UNREADABLE")
}
private fun release(context:Context,uri:Uri) {
    runCatching {context.revokeUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
}

/** Checkbox/list idiom from Alpha v54 LuaScriptsScreen/LuaScriptsPanel (c6107ce, Apache-2.0).
 * SAF, isolated persistence, next-session consent and redacted native status belong to Zavro.
 */
@Composable internal fun CustomLuaScriptsSettings(runtime:CustomScriptsSession?) {
    val context=LocalContext.current
    val store=remember(context) {CustomLuaRepository.get(context)}
    val manifest by store.state.collectAsState()
    val scope=rememberCoroutineScope()
    var open by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var note by remember {mutableStateOf<String?>(null)}
    var confirmEnable by remember {mutableStateOf<CustomScript?>(null)}
    var confirmDelete by remember {mutableStateOf<CustomScript?>(null)}
    var incoming by remember {mutableStateOf<List<Pair<String,ByteArray>>?>(null)}
    var model by remember {mutableStateOf<ByteArray?>(null)}
    var conf by remember {mutableStateOf<CustomConfPlan?>(null)}
    var editor by remember {mutableStateOf<CustomScript?>(null)}
    var text by remember {mutableStateOf("")}
    var kind by remember {mutableStateOf("model")}
    fun work(action:()->Unit) {
        scope.launch {
            busy=true;note=null
            try {withContext(Dispatchers.IO){action()};note="Zapisano. Zmiany obowiązują od następnej sesji MPV; bieżący film nie zostanie zrestartowany."}
            catch(_:Exception){note="Operacja odrzucona. Sprawdź format, nazwę, rozmiar i dostęp do pliku; ustawienia nie zostały częściowo zastosowane."}
            finally {busy=false}
        }
    }
    val picker=rememberLauncherForActivityResult(remember {LocalLuaFiles()}) {uris->
        if(uris.isNotEmpty())scope.launch {
            busy=true;note=null
            try {
                incoming=withContext(Dispatchers.IO) {
                    require(uris.size<=32)
                    val files=uris.map {uri->
                        val name=documentName(context,uri);CustomLuaStore.name(name)
                        val bytes=context.contentResolver.openInputStream(uri)?.use {CustomLuaStore.read(it,CustomLuaStore.MAX_SCRIPT)}
                            ?:throw CustomImportException("UNREADABLE")
                        CustomLuaStore.decode(bytes,CustomLuaStore.MAX_SCRIPT);name to bytes
                    }
                    require(files.map {CustomLuaStore.clientName(it.first)}.toSet().size==files.size)
                    files
                }
            }catch(_:Exception){note="Nie można odczytać skryptów. Wybierz do 32 plików .lua, każdy do 1 MiB, UTF-8; nazwa do 60 znaków ASCII (litery/cyfry/_/-), bez nazw zastrzeżonych."}
            finally {uris.forEach {release(context,it)};busy=false}
        }
    }
    val resourcePicker=rememberLauncherForActivityResult(remember {LocalLuaResource()}) {uri->
        if(uri!=null)scope.launch {
            busy=true;note=null
            try {
                withContext(Dispatchers.IO) {
                    val name=documentName(context,uri)
                    val bytes=context.contentResolver.openInputStream(uri)?.use {
                        CustomLuaStore.read(it,if(kind=="model")CustomLuaStore.MAX_MODEL else 65536)
                    }?:throw CustomImportException("UNREADABLE")
                    if(kind=="model") {
                        require(name=="std.rnnn");RnnnModel.validate(bytes);model=bytes
                    }else {require(name.endsWith(".conf"));conf=CustomMpvConf.parse(bytes.inputStream())}
                }
            }catch(_:Exception){note="Niepoprawny lub zbyt duży plik. Model: std.rnnn RNNoise v1 do 4 MiB; konfiguracja: .conf UTF-8 do 64 KiB."}
            finally {release(context,uri);busy=false}
        }
    }
    TextButton(onClick={open=true}) {Text("Skrypty Lua i zasoby")}
    if(open)Dialog(onDismissRequest={if(!busy)open=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.95f).heightIn(max=600.dp),shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Skrypty Lua",style=MaterialTheme.typography.titleLarge)
                    TextButton(onClick={open=false},enabled=!busy){Text("Zamknij")}
                }
                Text("Oddzielne pliki Lua; włączenie, wyłączenie, usunięcie i aktualizacja działają od następnego filmu. Zaimportowane pliki są wyłączone. Rozpakuj ZIP lokalnie przed wyborem plików.",style=MaterialTheme.typography.bodySmall)
                Text("Zgoda na kody przycisków jest oddzielna i pozostaje bez zmian. onStartup przycisków nie jest uruchamiane.",style=MaterialTheme.typography.bodySmall)
                if(!manifest.valid)Text("Uszkodzony magazyn: ładowanie i zapis zablokowane. Zachowano pliki; nie resetuj danych aplikacji.",color=MaterialTheme.colorScheme.error)
                TextButton(enabled=!busy && manifest.valid,onClick={picker.launch(arrayOf("*/*"))}) {Text("Importuj pliki .lua")}
                val execution=runtime?.state?.collectAsState()?.value
                manifest.scripts.forEach {row->
                    HorizontalDivider()
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=row.enabled,enabled=!busy,onCheckedChange={
                            if(it)confirmEnable=row else work {store.enable(row.id,false,false)}
                        })
                        Column(Modifier.weight(1f)) {
                            Text(row.name,style=MaterialTheme.typography.titleSmall)
                            Text("Następna sesja: "+if(row.enabled)"WŁĄCZONY" else "WYŁĄCZONY",style=MaterialTheme.typography.labelSmall)
                            Text("Teraz: "+(execution?.clients?.get(row.id)?.status?:"NIEZAŁADOWANY"),style=MaterialTheme.typography.labelSmall)
                            if(execution?.versions?.get(row.id)?.let {it!=row.hash}==true)
                                Text("W bieżącej sesji działa poprzednia rewizja.",style=MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row {
                        TextButton(enabled=!busy,onClick={scope.launch {
                            busy=true
                            try {text=withContext(Dispatchers.IO){store.source(row.id)};editor=row}
                            catch(_:Exception){note="Nie można odczytać skryptu."}finally {busy=false}
                        }}){Text("Treść / edytuj")}
                        if(row.previous!=null)TextButton(enabled=!busy,onClick={work {store.restore(row.id)}}){Text("Poprzednia wersja")}
                        TextButton(enabled=!busy,onClick={confirmDelete=row}){Text("Usuń")}
                    }
                }
                HorizontalDivider()
                Text("Model std.rnnn",style=MaterialTheme.typography.titleMedium)
                Text(if(manifest.modelHash==null)"Niezaimportowany" else "Zapisany prywatnie; docelowo ~~ / script-opts / std.rnnn. Użycie DSP wymaga odczytu af i testu słuchowego.")
                TextButton(enabled=!busy,onClick={kind="model";resourcePicker.launch(arrayOf("*/*"))}){Text("Importuj std.rnnn")}
                Text("mpv.conf — tylko obsługiwane opcje",style=MaterialTheme.typography.titleMedium)
                Text("Wybrane opcje: ${manifest.options.size}. Cache do 300 s / 256 MiB + 64 MiB wstecz. Skrypty, TLS, filtry, include i profile z pliku są pomijane.",style=MaterialTheme.typography.bodySmall)
                TextButton(enabled=!busy,onClick={kind="conf";resourcePicker.launch(arrayOf("*/*"))}){Text("Importuj mpv.conf")}
                if(manifest.options.isNotEmpty())TextButton(enabled=!busy,onClick={work {store.saveOptions(CustomConfPlan(emptyMap(),0,0))}}){Text("Wyłącz opcje z mpv.conf")}
                note?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
            }
        }
    }
    confirmEnable?.let {row->AlertDialog(onDismissRequest={confirmEnable=null},title={Text("Włączyć skrypt?")},
        text={Text("Skrypty Lua mogą zmieniać ustawienia odtwarzacza oraz korzystać z uprawnień aplikacji. Włączaj tylko zaufane skrypty. Skrypt może sam uruchamiać akcje przy file-loaded. Włączenie obowiązuje od następnej sesji MPV.")},
        confirmButton={TextButton(onClick={work {store.enable(row.id,true,true,row.hash)};confirmEnable=null}){Text("Włącz skrypt")}},
        dismissButton={TextButton(onClick={confirmEnable=null}){Text("Anuluj")}})}
    confirmDelete?.let {row->AlertDialog(onDismissRequest={confirmDelete=null},title={Text("Usunąć skrypt?")},
        text={Text("Usunięcie z listy nie przerywa kopii działającej w bieżącej sesji. Nie usuwa filtrów innych skryptów.")},
        confirmButton={TextButton(onClick={work {store.delete(row.id)};confirmDelete=null}){Text("Usuń")}},
        dismissButton={TextButton(onClick={confirmDelete=null}){Text("Anuluj")}})}
    incoming?.let {files->AlertDialog(onDismissRequest={incoming=null},title={Text("Import skryptów")},
        text={Column(Modifier.heightIn(max=300.dp).verticalScroll(rememberScrollState())) {
            files.forEach {Text(it.first)}
            Text("Pliki: ${files.size}. Nowe i zaktualizowane wersje będą wyłączone. Poprzednia wersja aktualizowanego pliku pozostanie do przywrócenia. Import nie wykonuje kodu.")
        }},confirmButton={TextButton(onClick={work {store.importScripts(files)};incoming=null}){Text("Importuj")}},
        dismissButton={TextButton(onClick={incoming=null}){Text("Anuluj")}})}
    model?.let {bytes->AlertDialog(onDismissRequest={model=null},title={Text("Import std.rnnn")},
        text={Text("Format RNNoise v1 zgodny; ${bytes.size} B. Model pozostanie w prywatnych danych aplikacji. Sam import nie włącza Szum. Aktualizacja modelu obowiązuje od następnej sesji.")},
        confirmButton={TextButton(onClick={work {store.importModel(bytes)};model=null}){Text("Importuj model")}},
        dismissButton={TextButton(onClick={model=null}){Text("Anuluj")}})}
    conf?.let {plan->AlertDialog(onDismissRequest={conf=null},title={Text("Podgląd mpv.conf")},
        text={Column(Modifier.heightIn(max=340.dp).verticalScroll(rememberScrollState())) {
            plan.values.forEach {(k,v)->Text("$k=$v")}
            Text("Pominięte: ${plan.skipped}; ograniczone/ujednolicone: ${plan.limited}. Duże cache są ograniczone. Biblioteka zweryfikuje opcje przy następnej inicjalizacji; odrzucone opcje nie będą uznane za zastosowane.")
        }},confirmButton={TextButton(onClick={work {store.saveOptions(plan)};conf=null}){Text("Zastosuj od następnej sesji")}},
        dismissButton={TextButton(onClick={conf=null}){Text("Anuluj")}})}
    editor?.let {row->Dialog(onDismissRequest={if(!busy)editor=null},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.96f),shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp)) {
                Text(row.name,style=MaterialTheme.typography.titleMedium)
                val editable=text.length<=262144
                Text("Zapis tworzy nową wyłączoną rewizję; oryginał można przywrócić. Kod nie będzie wykonywany.")
                OutlinedTextField(value=if(editable)text else text.take(262144),onValueChange={if(it.toByteArray().size<=CustomLuaStore.MAX_SCRIPT)text=it},
                    readOnly=!editable,modifier=Modifier.fillMaxWidth().heightIn(max=330.dp),textStyle=LocalTextStyle.current.copy(fontFamily=FontFamily.Monospace))
                if(!editable)Text("Podgląd pierwszych 256 Ki znaków. Większy plik aktualizuj przez ponowny import.")
                Row {
                    TextButton(enabled=!busy && editable,onClick={val updated=text;work {store.importScripts(listOf(row.name to updated.toByteArray()))};editor=null}){Text("Zapisz rewizję")}
                    TextButton(onClick={editor=null}){Text("Zamknij")}
                }
            }
        }
    }}
}
