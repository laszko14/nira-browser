package com.prirai.android.nira.diagnostics.testlab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal enum class LabExportFormat(val mime:String,val extension:String) { TXT("text/plain","txt"), JSON("application/json","json") }
internal data class LabExportPayload(val content:String,val format:LabExportFormat,val type:TestType)

/** No Activity/URI references, and no report blobs in savedInstanceState. A pending SAF request survives rotation. */
internal class TestLabViewModel(private val renderer:CoroutineDispatcher=Dispatchers.Default):ViewModel() {
    private val mutableDetail=MutableStateFlow<TestLabReportBundle?>(null)
    val detail:StateFlow<TestLabReportBundle?> = mutableDetail
    val renderFailed=MutableStateFlow(false)
    val rendering=MutableStateFlow(false)
    private var generation=0L
    private var pending:LabExportPayload?=null
    fun showReport(report:TestLabReport) {
        val request=++generation
        mutableDetail.value=null;renderFailed.value=false;rendering.value=true
        viewModelScope.launch {
            try {
                val bundle=withContext(renderer){TestLabReportGenerator.generate(report)}
                if(request==generation)mutableDetail.value=bundle
            } catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){if(request==generation)renderFailed.value=true}
            finally {if(request==generation)rendering.value=false}
        }
    }
    fun closeReport(){generation++;mutableDetail.value=null;renderFailed.value=false;rendering.value=false}
    fun queueExport(format:LabExportFormat):Boolean {
        val report=mutableDetail.value?:return false
        if(pending!=null)return false
        pending=LabExportPayload(if(format==LabExportFormat.TXT)report.text else report.json,format,report.report.testType)
        return true
    }
    fun takeExport():LabExportPayload?=pending.also{pending=null}
}

internal fun labClock(seconds:Int):String {
    val bounded=seconds.coerceIn(0,60)
    return (bounded/60).toString().padStart(2,'0')+":"+(bounded%60).toString().padStart(2,'0')
}
internal fun TestType.labTitle():String=when(this) {
    TestType.COMPLEX->"Test kompleksowy"
    TestType.PANELS_GESTURES->"Panele i gesty"
    TestType.TABS_GROUPS->"Karty i grupy"
    TestType.GECKO_PAGES->"GeckoView i strony"
    TestType.VIDEO_DISCOVERY->"Wideo i wykrywanie"
    TestType.VIDEO_METADATA->"VIDEO_METADATA • Czas i rozmiar filmów"
    TestType.RESOURCES->"Pamięć, CPU i wydajność"
    TestType.STABILITY->"Stabilność i błędy"
}
internal fun TestType.labInstruction():String=when(this) {
    TestType.COMPLEX->"Korzystaj z przeglądarki: otwórz panele, przełącz karty, przewiń stronę i opcjonalnie odtwórz film."
    TestType.PANELS_GESTURES->"Otwieraj i zamykaj oba panele, przewijaj listy, zmień szerokość panelu kart i obracaj telefon."
    TestType.TABS_GROUPS->"Otwieraj linki, przełączaj karty, przenoś je między grupami, zmień profil i sprawdź Zamknij → Cofnij."
    TestType.GECKO_PAGES->"Ładuj strony, użyj Wstecz/Dalej, przewijaj i przełączaj karty. Dane kompozytora Gecko są niedostępne."
    TestType.VIDEO_DISCOVERY->"Otwieraj strony z filmami, panel wykrywania oraz Play i menu ⋮. Test rejestruje detekcję i żądania otwarcia zewnętrznego odtwarzacza, a nie jego klatki."
    TestType.VIDEO_METADATA->"Przez 60 s odwiedzaj strony z MP4, HLS i DASH, otwieraj kafelki. Raport sprawdzi czas, rozmiar, pochodzenie metadanych i braki — bez adresów URL."
    TestType.RESOURCES->"Korzystaj z wielu kart i stron WWW. Rejestrujemy pamięć, CPU własnego procesu, opóźnienia Main i częstotliwość ekranu — nie FPS silnika Gecko."
    TestType.STABILITY->"Przełączaj funkcje, obracaj telefon i wracaj z tła. Odczyt historii zakończeń procesu nie dowodzi nowej awarii."
}
