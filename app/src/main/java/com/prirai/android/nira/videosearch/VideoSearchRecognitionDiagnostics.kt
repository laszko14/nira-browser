package com.prirai.android.nira.videosearch

/** Safe diagnostic labels; never display page text, query, selector or URL. */
internal object VideoSearchRecognitionDiagnostics {
    fun explain(report: VideoSearchBridgeMessage.Recognition?): String = when (report?.reason) {
        "CANDIDATE_FOUND" ->
            "Wykryto kandydatów filmowych. Potwierdź wyniki wyszukiwaniem."
        "AMBIGUOUS_CARDS" ->
            "Wykryto kilka podobnych grup kart. Wybierz wyniki ręcznie."
        "SHADOW_RESULTS" ->
            "Wyniki znajdują się w Shadow DOM. Automatyczny zapis nie jest bezpieczny."
        "FRAME_RESULTS" ->
            "Wyniki znajdują się w ramce. Automatyczny zapis wymaga osobnej weryfikacji."
        "CARDS_NOT_FOUND" ->
            "Nie rozpoznano kart filmowych. Otwórz wyniki wyszukiwania lub użyj Wskaż."
        else -> "Nie można potwierdzić struktury strony. Otwórz witrynę i użyj Wskaż."
    }
}
