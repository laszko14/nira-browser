# Zavro x MPV v1148 — pełny interfejs fullscreen

## Baza i przyczyna
Baza sprawdzona na telefonie: v1147, 26ee7adcc32cae8eb68b508bf401e8e71c9f1d4f, gałąź feat/zavro-x-mpv-v1147-inline-scroll, PR https://github.com/laszko14/nira-browser/pull/60.
Przyczyna ograniczenia: updateControlsForWidth wybierał tryb 0 zarówno dla szerokiego mini, jak i fullscreen. toggleFullscreen powiększał tylko kontener; nie instalował seekbara, HUD ani gestów istniejącej ręcznej Activity.

## Architektura
Nowa gałąź feat/zavro-x-mpv-v1148-fullscreen-ui, bez merge.
Ten sam InlineMpvUi, decor parent, ZavroMpvView, SurfaceView, sesja i silnik. Przejście zmienia tylko granice kontenera oraz usuwa/dodaje widoki kontrolek. Nadal dokładnie jedno initialize i jedno playFile w open; przejścia nie ustawiają pozycji, pauzy ani prędkości. Powrót Back używa istniejącego exitFullscreen, geometrii v1146 i routera v1147.

Fullscreen: osobny dolny panel, seekbar 0–1000 (absolute+exact dopiero po puszczeniu), czas aktualny/całkowity/pozostały oraz prędkość, Play/Pauza, −10/+10/+30 s, wybór 1/1,5/2/2,5/3× i powrót do mini. Nieznana długość blokuje seek. Pauza, długość i prędkość pochodzą z observera istniejącego playera; czas HUD odświeżany najwyżej co 250 ms.

Gesty na powierzchni wyłącznie w fullscreen, zgodne z Activity: double-tap lewa 1/3 ustawia 1,5×, środkowa przełącza 2→2,5→3→2×, prawa nie zmienia prędkości. Hold po 300 ms dodaje 0,5× do maks. 3×; UP/CANCEL, ruch >20 dp, dodatkowy palec, Back i detach anulują hold i przywracają poprzednią prędkość. Gest nie modyfikuje pauzy. Dotyk pokazuje kontrolki; autoukrywanie po 3 s nie przerywa przeciągania seekbara ani aktywnego hold.

Mini zachowuje kompaktowy pasek i pionowy scroll Gecko. W wąskim pasku prędkość/skoki dostępne również w menu. Usunięto wejścia do napisów z mini; nowy panel nie zawiera napisów, audio, filtrów ani Lua. Ręczna ścieżka ZavroMpvActivity.kt pozostaje nietknięta. Powielono tylko małą logikę gestów/UI zamiast refaktoryzować działającą Activity.

## Zmienione pliki
- InlineMpvUi.kt: przełączanie panelu, obserwacje właściwości, seekbar, HUD, gesty i cleanup.
- InlineFullscreenUi.kt: czyste obliczenia czasu, seek i reguły double-tap/prędkości.
- MpvDebugTrace.kt: aktualne nagłówki v1148; nadal jeden SUPERTEST 40 s i raport RAM bez sekretów.
- app/build.gradle, .github/workflows/apk-release.yml: code bazowy 148 / ARM64 1148, name 0.5.22-alpha54-mpv22, artefakt zavro-x-mpv-arm64-v1148.
- InlineFullscreenUiTest.kt, zavro-x-mpv-v1148-fullscreen.test.cjs: obliczenia i kontrakty lifecycle/gestów.
- zavro-x-mpv-v1147-touch.test.cjs: aktualizacja oczekiwanego nagłówka raportu.
- Ten handoff, AI_HANDOFF.md i AI_PROGRESS.md.

## Testy, review i dostarczenie
- Node 140/140 PASS; nowy kontrakt fullscreen wcześniej FAIL.
- JVM: 85 testów, zero failures/errors; obliczenia seek, nieznana długość, czas i reguły gestów pokryte testami.
- Lokalne testDebugUnitTest + lintDebug offline: BUILD SUCCESSFUL (3m 24s). Brak nowych zależności.
- Ręczny code review: brak drugiego loadfile/initialize, brak otwierania Activity, reparentingu i zmian TLS/ACK/DRM; sprawdzono cleanup hold, ochronę seek podczas drag, izolację gestów fullscreen i zachowanie scroll mini. git diff --check PASS.
- Cały kod, testy i dokumentacja zapisane przed pierwszym push. Jedno CI przez Draft PR. Stan CI przy tworzeniu dokumentu: jeszcze nieuruchomione; wynik dostępny w checks PR i raporcie końcowym, bez dodatkowego commita wyłącznie na wynik CI.
- PR gałęzi: https://github.com/laszko14/nira-browser/pulls?q=is%3Apr+head%3Afeat%2Fzavro-x-mpv-v1148-fullscreen-ui
- CI gałęzi: https://github.com/laszko14/nira-browser/actions?query=branch%3Afeat%2Fzavro-x-mpv-v1148-fullscreen-ui
- Dokładne linki PR/CI i końcowy SHA: raport końcowy; również head.sha PR / git rev-parse HEAD. Własnego SHA nie zapisuje się dosłownie w jego commicie.
- Artefakt: zavro-x-mpv-arm64-v1148. Workflow wymusza package com.prirai.android.nira.mpv, code 1148, name 0.5.22-alpha54-mpv22, arm64-v8a oraz certyfikat SHA256 df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00. Nie pobierać APK do sesji; nie uznawać go za gotowe bez sukcesu BUILD/TEST/LINT.

## Ograniczenia i krótki test telefonu
Nie wykonano testu na fizycznym urządzeniu/emulatorze. JVM testuje obliczenia, Node kontrakty źródła; nie zastępują testów dotyku, Android ViewGroup, renderowania SurfaceView i Gecko/APZ. Nie zmieniono wymuszania orientacji; fullscreen działa w aktualnej orientacji.

1. Mini: Play/Pauza, ±10 s, prędkość, pionowy scroll przez obraz.
2. Zapauzować, ustawić 2,5×, wejść w fullscreen: pozycja/pauza/prędkość bez zmiany; wznowić.
3. Przeciągnąć seekbar i puścić, sprawdzić HUD i pozostały czas; przytrzymać seekbar ponad 3 s.
4. Double-tap lewa/środek; hold >300 ms i puszczenie/przesunięcie: chwilowy boost wraca do bazowej prędkości.
5. Poczekać na autoukrycie i dotknąć obraz. Fullscreen nie przewija strony.
6. Back podczas odtwarzania i podczas pauzy, również podczas hold: powrót do tej samej pozycji na stronie, jedna ścieżka audio, brak restartu, działający scroll mini.
7. SUPERTEST 40 s: nagłówek v1148, zero dodatkowych START_FILE/błędów HTTP/TLS; zamknięcie mini przywraca Gecko.

Następny krok: sprawdzenie gotowego CI/artefaktu oraz powyższy test telefonu. Dalsze zmiany tylko według dowodów z testu.
