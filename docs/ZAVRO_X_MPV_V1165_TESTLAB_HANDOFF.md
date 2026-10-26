# v1165 Zavro TestLab — dokument roboczy

**NIEUKOŃCZONE. Brak APK v1165 i Draft PR.** Stan A/B1/B2: architektura oraz pureKotlin silnik/agregatory z31nowymi testami. Pełny Kotlin/JUnit307/307PASS. Siedem trybów zdefiniowanych w kontrakcie; Androidowe kolektory, hooki, UI/eksport/recovery i pomiary narzutu jeszcze nie są wdrożone.

Baza v1164d890c68bd15d0f79e25ccd2935fb64ee955286b8; gałąźfeat/zavro-x-mpv-v1165-testlab-diagnostics. Dotychczasowy MPV/detektor/karty oraz ustawienia niezmienione. Bez migracji. Wersja Gradle nadal164 do etapu wydania.

[Specyfikacja metryk, źródeł i plan](ZAVRO_TESTLAB_METRICS_SPEC.md), [dokładny checkpoint](CODEX_RESUME_V1165.md). Jedno centrum TestLab,7trybów,60s, monotoniczne tokeny i bounded storage. Rdzeń nie ma referencji Activity/Window ani aktywnych timerów; adaptery Androida będą osobną warstwą.

Przetestowane jednostkowo: deadline60s, preparing/partial/cancel/restart, jednoczesny start, cleanupgate, stare callbacki, coverage wtle, gestSTART≠opened/cancel≠failure, CPUweightedonecore, percentyle/minsamples/overflow, memorysingle/delta/bounds, immutabilityenvironment, missingAPI sources, overlapframecorrelations.

Nieprzetestowane jeszcze: v1165 Android lifecycle/JankStats/Window/gesty, OSsamplery, Gecko/MPV hooki, ekran7przycisków, eksport, recovery, wydajnośćA/B. Nie przedstawiać31testów jako wyniku urządzenia. Fizyczny NothingPhone1 NIEURUCHOMIONY.

Finalny handoff zostanie rozwinięty po etapachC–H o źródła, wszystkie implementowane zdarzenia, formatTXT/JSON, privacy/retention, wyniki emulatora/narzutu/regresji i linki PR/CI/signedAPK. Nie rozpoczynać kolejnej wersji.
