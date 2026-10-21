# Alpha Browser — przekazanie Etapu 2

Data: 2026-10-22 (Europe/Warsaw).

## Stan i wynik

Etap 1 jest zakończony. W Etapie 2 dodano w menu Niry pozycję „Otwórz link wideo w MPV Alpha”. Otwiera okno ręcznego wpisania adresu HTTPS. Puste lub niepoprawne adresy pozostają w oknie z komunikatem błędu. Poprawny URI jest przekazywany jako `ACTION_VIEW` z `video/*` do `xyz.mpv.rex.ui.player.PlayerActivity`; gdy aktywności nie ma lub system odmawia jej uruchomienia, Nira pokazuje wybór odtwarzacza. Nira nie pobiera wideo przed przekazaniem linku.

Kontrakt potwierdzono w repozytorium `laszko14/REX-Player`, commit `8c4f9ed2159c397317ceb1d58e8223f8cdf2b222`: `app/build.gradle.kts` ustawia `applicationId = "xyz.mpv.rex"`, manifest eksportuje `PlayerActivity` dla `ACTION_VIEW`, `https`, `video/*`, a `PlayerIntentHandler` odczytuje `intent.data`.

Kod Niry: commity `0d8f30012e9a0c0ebd57da908beb90bf543592e1` i `1a23bbedf23f7c6a2cfd1c071f165d288535b318` (usunięcie nowego ostrzeżenia lint). Wersja debug: `0.5.16-alpha1`, `versionCode 1080` dla arm64-v8a (bazowy `versionCode 80`). Identyfikator pakietu pozostaje `com.prirai.android.nira.debug`.

## Kompilacja i ograniczenia

GitHub Actions: https://github.com/laszko14/nira-browser/actions/runs/39691305749 — build, lint i test job success; release skipped. Artefakt: https://github.com/laszko14/nira-browser/actions/runs/39691305749/artifacts/12388986024. W ZIP wybierz `app-arm64-v8a-debug.apk` dla Nothing Phone (1). Pobrany APK CI ma ABI `arm64-v8a`, minSdk 27, targetSdk 37, poprawny podpis Android Debug v2. SHA-256 APK: `8e1a5ecaa4ac74b35db5327275f7c8a9b59485c36fc76ebe9c0d82cbd39018d4`.

Zadanie `testDebugUnitTest` zgłosiło `NO-SOURCE`: testów jednostkowych nie wykonano. Lokalny `assembleDebug lintDebug testDebugUnitTest` zakończył się sukcesem, lecz uruchomiono go przed zmianą numeru wersji; lokalny APK z tamtego przebiegu miał 1079. Ponowny lokalny `assembleDebug` zakończył się sukcesem w 24 s, a `aapt` potwierdził arm64 i versionCode 1080; `apksigner verify` przeszedł. Po poprawce `toUri()` lokalne `assembleDebug lintDebug` zakończyło się sukcesem w 1m 42s; nowe ostrzeżenie `UseKtx` z BrowserFragment zniknęło. Raport lokalnego lint zawiera 1626 błędów i 367 ostrzeżeń, ale projekt ma `abortOnError = false`; sukces zadania nie oznacza czystego lint. Nie wykonano testu odtwarzania na fizycznym telefonie ani emulatorze. Klucz debug z CI może różnić się od poprzedniego; przy konflikcie podpisu trzeba świadomie rozwiązać instalację, bez automatycznego usuwania danych.

## Następny krok

Po zapisaniu dokumentacji sprawdzić `git log -1`, `git status --short` i `git ls-remote origin HEAD`; dokładny hash końcowego commitu dokumentacji ustala Git.

Na Nothing Phone (1) zainstalować `app-arm64-v8a-debug.apk`, wpisać publiczny adres HTTPS filmu i sprawdzić start MPV Alpha; powtórzyć bez MPV Alpha, by potwierdzić wybór innego odtwarzacza. Etap 3 w osobnej sesji: automatyczne wykrywanie filmów na stronach. Nie dodano tego wykrywania, panelu filmów ani obsługi HLS/DASH w Etapie 2.
