package com.prirai.android.nira.diagnostics.testlab

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.ext.enableEdgeToEdgeMode
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.*
import java.util.Locale

class TestLabActivity:AppCompatActivity() {
    private val model:TestLabViewModel by viewModels()
    private var sharing by mutableStateOf(false)
    private var recoveryEnabled by mutableStateOf(false)
    private var recovered by mutableStateOf<TestLabRecovery?>(null)
    private val txtExport=registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain"),::exportResult)
    private val jsonExport=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json"),::exportResult)

    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        enableEdgeToEdgeMode()
        onBackPressedDispatcher.addCallback(this) {
            if(model.detail.value!=null||model.rendering.value)model.closeReport()
            else {isEnabled=false;onBackPressedDispatcher.onBackPressed()}
        }
        val store=TestLab.recovery(application)
        recoveryEnabled=store.enabled
        lifecycleScope.launch {
            recovered=withContext(Dispatchers.IO){store.read()}
            TestLabExports.clean(applicationContext,expiredOnly=true)
        }
        setContent {
            NiraTheme(darkTheme=ThemeManager.isDarkMode(this),amoledMode=ThemeManager.isAmoledActive(this),
                dynamicColor=ThemeManager.shouldUseDynamicColors(this)) {Screen()}
        }
    }
    override fun onResume(){super.onResume();ThemeManager.applySystemBarsTheme(this,false)}
    private fun message(text:String)=Toast.makeText(this,text,Toast.LENGTH_LONG).show()
    private fun returnToBrowser() {
        // Settings' ActivityResult callback finishes Settings. Do not send BrowserActivity.onNewIntent:
        // that can navigate/recreate BrowserFragment and disturb the existing inline player.
        setResult(Activity.RESULT_OK);finish()
    }
    private fun start(type:TestType) {
        if(TestLab.start(application,type))returnToBrowser()
        else message(if(TestLab.lastStartFailure==LabStartFailure.CONFLICT)
            "Inny test jest aktywny. Zakończ aktywny TestLab." else "Nie udało się przygotować pomiaru. Możesz spróbować ponownie.")
    }
    private fun copy(text:String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Zavro TestLab",text))
        message("Raport skopiowany")
    }
    private fun export(format:LabExportFormat) {
        if(!model.queueExport(format))return
        val type=model.detail.value?.report?.testType?:return
        try {
            val name="zavro-testlab-${type.name.lowercase(Locale.ROOT)}.${format.extension}"
            if(format==LabExportFormat.TXT)txtExport.launch(name) else jsonExport.launch(name)
        } catch(_:Exception){model.takeExport();message("Brak aplikacji do zapisu dokumentu")}
    }
    private fun exportResult(uri:Uri?) {
        val payload=model.takeExport()
        if(uri==null)return
        if(payload==null){message("Raport nie jest już dostępny; wybierz eksport ponownie");return}
        lifecycleScope.launch {
            try {TestLabExports.write(applicationContext,uri,payload);message("Raport zapisany")}
            catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){message("Nie udało się zapisać raportu")}
        }
    }
    private fun share(bundle:TestLabReportBundle) {
        if(sharing)return
        sharing=true
        lifecycleScope.launch {
            try {
                val uri=TestLabExports.share(applicationContext,bundle)
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type="text/plain";putExtra(Intent.EXTRA_STREAM,uri)
                    clipData=ClipData.newRawUri("Zavro TestLab",uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },"Udostępnij raport"))
            } catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){message("Udostępnianie raportu jest niedostępne")}
            finally {sharing=false}
        }
    }
    private fun delete(type:TestType?=null) {
        if(sharing)return
        TestLab.deleteReport(type);model.closeReport()
        lifecycleScope.launch {
            TestLabExports.clean(applicationContext,type)
            if(type==null){withContext(Dispatchers.IO){TestLab.recovery(application).delete()};recovered=null}
        }
    }
    private fun recoveryText(value:TestLabRecovery)=buildString {
        appendLine("Zavro TestLab — NIEKOMPLETNY POMIAR")
        appendLine("${value.testType}; ${value.appVersion.versionName}; ${value.status}; ${value.durationMs} ms")
        appendLine("Przerwanie nie dowodzi awarii. Brak metryk, których nie udało się zachować.")
        appendLine("Błędy: ${value.errorCounts}")
        value.timeline.forEach{appendLine("${it.offsetNanos/1_000_000.0} ms ${it.code} ${it.operation?:""}")}
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun Screen() {
        val status by TestLab.ui.collectAsState()
        val reports by TestLab.reports.collectAsState()
        val detail by model.detail.collectAsState()
        val rendering by model.rendering.collectAsState()
        val renderFailed by model.renderFailed.collectAsState()
        val active=status.phase in setOf(LabPhase.PREPARING,LabPhase.RUNNING,LabPhase.FINALIZING)
        var chosen by remember {mutableStateOf<TestType?>(null)}
        val lines=remember(detail){detail?.text?.lineSequence()?.toList().orEmpty()}
        Scaffold(topBar={TopAppBar(title={Text("Zavro TestLab")},navigationIcon={
            IconButton(onClick={onBackPressedDispatcher.onBackPressed()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,contentDescription="Wstecz")}
        })}) {padding->
            LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("testlab-list"),
                contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Dobrowolny pomiar lokalny • 60 s",style=MaterialTheme.typography.titleMedium)
                    Text("Raporty domyślnie pozostają w RAM. Brak adresów, nazw profili, cookies i treści stron. Test nie steruje kartami ani odtwarzaniem.",style=MaterialTheme.typography.bodyMedium)
                    Text("Zakończony pomiar nie oznacza sprawności całej aplikacji. Klatki dotyczą Android UI Window; obraz Gecko i wideo nie są bezpośrednio mierzone.",style=MaterialTheme.typography.bodySmall)
                }
                if(active)item {
                    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(status.type?.labTitle().orEmpty(),style=MaterialTheme.typography.titleMedium)
                        Text(when(status.phase){
                            LabPhase.PREPARING->"Przygotowanie. Odliczanie 3 s ruszy w przeglądarce."
                            LabPhase.FINALIZING->"Kończenie i zwalnianie kolektorów…"
                            else->"Pozostało ${labClock(status.remainingSeconds?:60)}"
                        },Modifier.testTag("testlab-status"))
                        Button(onClick=::returnToBrowser,enabled=status.phase!=LabPhase.FINALIZING,modifier=Modifier.fillMaxWidth()){Text("Wróć do przeglądarki")}
                        OutlinedButton(onClick={if(TestLab.mark())message("Znacznik zapisany")},enabled=status.phase==LabPhase.RUNNING,modifier=Modifier.fillMaxWidth()){Text("Zacięło się teraz")}
                        OutlinedButton(onClick={TestLab.stop()},enabled=status.phase!=LabPhase.FINALIZING,modifier=Modifier.fillMaxWidth()){Text("Zakończ wcześniej • raport częściowy")}
                    }}
                }
                if(rendering)item{CircularProgressIndicator();Text("Przygotowywanie raportu…")}
                if(renderFailed)item{Text("Nie udało się przygotować raportu. Poprzednie wyniki pozostają dostępne.")}
                if(detail!=null) {
                    item {
                        Text("${detail!!.report.testType.labTitle()} • ${detail!!.report.status} • ${String.format(Locale.ROOT,"%.1f",detail!!.report.durationMs/1000)} s",style=MaterialTheme.typography.titleMedium)
                        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Button(onClick={detail?.let{copy(it.text)}},enabled=!active,modifier=Modifier.fillMaxWidth()){Text("Kopiuj raport")}
                            OutlinedButton(onClick={export(LabExportFormat.TXT)},enabled=!active,modifier=Modifier.fillMaxWidth()){Text("Eksportuj TXT")}
                            OutlinedButton(onClick={export(LabExportFormat.JSON)},enabled=!active,modifier=Modifier.fillMaxWidth()){Text("Eksportuj JSON")}
                            OutlinedButton(onClick={detail?.let(::share)},enabled=!active&&!sharing,modifier=Modifier.fillMaxWidth()){Text(if(sharing)"Przygotowywanie pliku…" else "Udostępnij raport")}
                            TextButton(onClick={detail?.report?.testType?.let(::delete)},enabled=!active&&!sharing,modifier=Modifier.fillMaxWidth()){Text("Usuń raport")}
                            TextButton(onClick=model::closeReport,modifier=Modifier.fillMaxWidth()){Text("Wróć do testerów")}
                        }
                    }
                    items(lines){line->SelectionContainer {Text(line,style=MaterialTheme.typography.bodySmall)}}
                } else {
                    items(TestType.entries,key={it.name}) {type->
                        Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(type.labTitle(),style=MaterialTheme.typography.titleMedium)
                            Text(type.labInstruction(),style=MaterialTheme.typography.bodySmall)
                            Button(onClick={chosen=type},enabled=!active,modifier=Modifier.fillMaxWidth().testTag("testlab-start-${type.name}")){Text("${type.labTitle()} — 60 s")}
                            reports[type]?.let{previous->
                                Text("Poprzedni wynik: ${previous.status} • ${String.format(Locale.ROOT,"%.1f",previous.durationMs/1000)} s",style=MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick={model.showReport(previous)},enabled=!active,modifier=Modifier.fillMaxWidth().testTag("testlab-report-${type.name}")){Text("Otwórz raport")}
                            }
                        }}
                    }
                    item {
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("Zachowaj minimalne dane po awarii",Modifier.weight(1f))
                            Switch(checked=recoveryEnabled,enabled=!active,onCheckedChange={value->
                                lifecycleScope.launch {
                                    val store=TestLab.recovery(application)
                                    val saved=withContext(Dispatchers.IO){store.setEnabled(value)}
                                    recoveryEnabled=store.enabled
                                    if(!value)recovered=null
                                    if(!saved)message("Nie udało się zapisać ustawienia")
                                }
                            },modifier=Modifier.testTag("testlab-recovery"))
                        }
                        Text("Domyślnie wyłączone. Prywatny plik bez kopii w chmurze, maks. 64 KiB, zapis najwyżej co 5 s. Wygasa po 24 h i jest usuwany przy następnym odczycie. Zwykły koniec testu usuwa plik.",style=MaterialTheme.typography.bodySmall)
                    }
                    recovered?.let{value->item {
                        Text("Znaleziono niekompletny pomiar. Nie przesądza to o awarii aplikacji.")
                        OutlinedButton(onClick={copy(recoveryText(value))},enabled=!active){Text("Kopiuj dane niekompletnego pomiaru")}
                        TextButton(onClick={lifecycleScope.launch{withContext(Dispatchers.IO){TestLab.recovery(application).delete()};recovered=null}},enabled=!active){Text("Usuń dane niekompletnego pomiaru")}
                    }}
                    if(reports.isNotEmpty()||recovered!=null)item{TextButton(onClick={delete()},enabled=!active){Text("Usuń wszystkie raporty TestLab")}}
                }
            }
        }
        chosen?.let{type->AlertDialog(onDismissRequest={chosen=null},title={Text("${type.labTitle()} • 60 s")},text={
            Text(type.labInstruction()+"\n\nPo powrocie do przeglądarki nastąpi odliczanie 3 s, nieuwzględnione w pomiarze. Przycisk „Zacięło się teraz” i wcześniejsze zakończenie znajdziesz też w menu ⋮. Bez próby danego działania wynik pozostanie NIEPRZETESTOWANY.")
        },confirmButton={TextButton(onClick={chosen=null;start(type)}){Text("Rozpocznij i wróć")}},dismissButton={TextButton(onClick={chosen=null}){Text("Anuluj")}})}
    }
}
