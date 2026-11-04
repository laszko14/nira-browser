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
        "FRAME_REPLACED" ->
            "Ramka strony została podmieniona. Odśwież witrynę i ponownie sprawdź wyszukiwanie."
        "LOW_CONFIDENCE" ->
            "Znaleziono karty, ale podobieństwo jest zbyt słabe. Wskaż właściwe wyniki ręcznie."
        "SCAN_LIMIT" ->
            "Strona przekroczyła bezpieczny limit analizy. Nie zmieniaj reguł bez weryfikacji."
        "CARDS_NOT_FOUND" ->
            "Brak pewnego kandydata. To może być pusta strona, opóźniony AJAX " +
                "lub zmieniona struktura. Nie nadpisuj reguł bez testowego wyszukiwania."
        else -> "Nie można potwierdzić struktury strony. Otwórz witrynę i użyj Wskaż."
    }
}
