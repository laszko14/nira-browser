# Etap 3.1 — kod i CI SUCCESS; test telefonu nadal NIEPOTWIERDZONY (2026-10-22)

- Zakres: tylko 3.1, na bazie niescalonego PR #2 (b6b46a4). Gałąź feat/media-detector-stage-3-1, [PR #3](https://github.com/laszko14/nira-browser/pull/3) skierowany do main, aby uruchomić istniejące CI. Do scalenia PR #2 diff obejmuje także 3.0. Nie scalać #3 przed #2.
- Commity f110b95e (implementacja), 81d60cfc (testy), 64300832 (odrzucanie .m3u8/.mpd mimo mylących MIME); końcowy HEAD dokumentacji i final CI sprawdź w PR #3.
- HTML5: play/playing/loadedmetadata, skan tylko odtwarzanych video, ograniczony MutationObserver (120 ms, maks. 48 wideo na skan) na zmiany src i source/SPA; zmiana nawigacji ponawia sygnał, duplikaty tego samego źródła danego elementu są tłumione.
- Walidowany protokół 3.0 otrzymał opcjonalny contentType z source type (do 128 B); Kotlin tworzy nietrwały DetectedMedia dla MP4/WebM i odrzuca typowe segmenty oraz HLS/DASH. Zachowano podpisany URL bez zmian, lecz diagnostyka i toString nie podają URL/tokenów. HTTP Content-Type to etap 3.2.
- Zachowano natywne przypisanie EngineSession do karty/profilu, Gecko-controlled incognito i ograniczony webRequest probe. Limit 1/s zmieniono na 24 zdarzenia/5s na sesję, aby nie zgubić dwóch różnych filmów.
- Brak panelu, MediaRepository (3.3), parserów i downloadera. DetectedMedia jest obiektem chwilowym: nie jest wyświetlany ani trwale przechowywany.
- Wersja 0.5.18-alpha1, bazowy versionCode 85, przewidziany arm64 debug code 1085; bez modyfikacji identyfikatora/podpisywania. Build CI sukces, artefakt ZIP istnieje; podpis/manifest samego APK pozostają bez niezależnej weryfikacji narzędziami aapt/apksigner i bez testu instalacji.
- Sprawdzone CI: [run 39706343598](https://github.com/laszko14/nira-browser/actions/runs/39706343598) (build/lint/test success; 14 JVM według raportu HTML, 8 Node pass), a po poprawce MIME [run 39706925625](https://github.com/laszko14/nira-browser/actions/runs/39706925625) (build/lint/test success, release skipped). Testy 3 klas JVM i 8 testów JS. Bazowe lint Etapu 3.0: 1626 błędów, 367 ostrzeżeń; lintDebug raportu 3.1 ukończone, ale abortOnError=false i nie potwierdzono, że jest czysty.
- Nie testowano na telefonie ani Etapu 3.0, ani 3.1; do potwierdzenia Gecko DOM → Kotlin, przypisanie profili/kart, callback webRequest i SPA. Bez UI nie ma widocznej listy.

## Następny krok

Kod Etapu 3.1 przeszedł CI (ostatni run kodu 39706925625); artefakt [debug-apks 12395875886](https://github.com/laszko14/nira-browser/actions/runs/39706925625/artifacts/12395875886) według GitHub API jest niewygasły, dla Nothing Phone (1) wybierz app-arm64-v8a-debug.apk. Zweryfikować APK przez aapt/apksigner przed instalacją, bo konfiguracja debug signing może różnić się od poprzednich buildów. Dokończyć oddzielny test Gecko DOM → Kotlin na telefonie i niezależny code review. PR #2 i #3 pozostawić bez merge.

---

# Etap 3.0 — kod i CI zweryfikowane, oczekuje na telefon (2026-10-22 UTC)

- Baza: `main` / `b9fd4c5`; gałąź robocza `feat/media-detector-stage-3-0`. Etapy 1–2 zachowane.
- Dodano minimalny `nira-media-detector`: zaufany DOM `playing` / `video.currentSrc` → walidacja background → odpowiedź do content → session-bound native messaging → Kotlin. Background mozac nie dostarcza sesji, dlatego końcowy transport korzysta z `registerContentMessageHandler(EngineSession, ...)`.
- Przypisanie pochodzi z tożsamości EngineSession i bieżących `TabSessionState.id`, `contextId`, `content.private`. `contextId=null` oznacza domyślną partycję. Brak polegania na aktywnej karcie/profilu lub identyfikatorach strony. Zakres: zwykłe karty, główna ramka, publiczny MP4; custom tabs/iframe i stan kandydatów poza etapem 3.0.
- Potwierdzone źródła API dla tagu `FIREFOX-ANDROID_156_0_1_RELEASE`: concept-engine WebExtension/MessageHandler oraz GeckoWebExtension; Components `156.0.1`, zależność GeckoView `geckoview-omni:156.0.20260921121718`.
- Bez utrwalania URL, cookies, nagłówków i tokenów; limit 16 KiB wiadomości / 8 KiB URL; HTTP/S; diagnostyka tylko debug, bez URL/hostów/tytułów. Kandydat ma zredagowane `toString()`.
- Tryb prywatny pozostaje pod kontrolą uprawnienia Gecko. Kod nie włącza go automatycznie; log `private_disabled` oznacza brak detekcji incognito. Przypisanie prywatnej sesji jest testowane jednostkowo, ale nie dowodzi działania rozszerzenia w incognito.
- Pasywny probe `onHeadersReceived` ograniczony do publicznego pliku MDN flower.mp4, jeden sygnał bez URL. Dostępność callbacku w uruchomionym APK pozostaje do potwierdzenia.
- Wersja `0.5.17-alpha1`, bazowy code 81; lokalny `aapt` potwierdza arm64-v8a code 1081, `com.prirai.android.nira.debug`, minSdk 27 / targetSdk 37. `apksigner verify` zakończył się kodem 0 (Android Debug). ApplicationId i konfiguracja podpisywania bez zmian. Klucze lokalny/CI nie muszą być identyczne.
- Testy JVM: 8 wykonanych w bezpośrednim harnessie Kotlin/JUnit, PASS; przed implementacją zaobserwowano 4 porażki. JS: 4 testy Node PASS; dołączone do istniejącego workflow.
- Commit implementacji: `55cba5c086aa65bd55483723d2864a5b0dece994`, wypchnięty. PR: https://github.com/laszko14/nira-browser/pull/2 (bez merge). Niezależny code review nie znalazł potwierdzonych problemów Critical/Important.
- CI implementacji: https://github.com/laszko14/nira-browser/actions/runs/39703241830 — build/lint/test SUCCESS, release SKIPPED. Artefakt `debug-apks`, niewygasły: https://github.com/laszko14/nira-browser/actions/runs/39703241830/artifacts/12394132474. Dla Nothing Phone (1) wybrać `app-arm64-v8a-debug.apk`. Obecność artefaktu potwierdzono przez API; pobranie archiwum w Cloud zwracało 403, więc nie przypisywać mu sumy SHA lokalnego APK.
- Lokalny `testDebugUnitTest`: SUCCESS, 8 testów Kotlin (5 protokołu + 3 przypisania), failures/errors 0, 6m47s. `assembleDebug` ukończone i lokalny APK zweryfikowany; SHA-256 lokalnego pliku `879f887052da43e07beea8ad2f159cf446f6ee2655acf11cb0228efeabd53169`.
- Pełne lokalne `assembleDebug lintDebug testDebugUnitTest --max-workers=4 --console=plain` z przygotowanym GRADLE_USER_HOME/SDK: BUILD SUCCESSFUL, 9m28s. XML: 8 testów Kotlin, failures/errors 0. Lint: 1626 Error/Fatal, 367 Warning i 12 Hint; Error/Fatal oraz Warning odpowiadają udokumentowanej bazie. Brak zgłoszeń w dodanych plikach; kompilator nie zgłosił ostrzeżeń w nowym kodzie. Lint ma `abortOnError=false`, więc raport nie jest czysty.
- Ostatni commit dokumentacji ustala `git log -1`; bieżące kontrole końcowego HEAD są widoczne w PR. Powyższy run dotyczy commitu implementacji, nie późniejszych zmian dokumentacji.
- Telefon: nie testowano. Sprawdzić start, `candidate_received` dla publicznego MP4, właściwą kartę/profil również po zmianie aktywnej karty, `webrequest_callback_received`, brak URL w logach. Nie ma jeszcze panelu ani otwierania automatycznie wykrytego URL w MPV.

## Dokładny kolejny krok

Na Nothing Phone (1) sprawdzić transport i diagnostykę tagu `NiraMediaDetector` po uruchomieniu publicznego `https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4`. Oczekiwane: `installed`, `candidate_received` z właściwym natywnym tab/context oraz niezależnie `webrequest_callback_received`. Powtórzyć dla dwóch zwykłych kart i profilu dodatkowego, przywróconej karty i zmiany aktywnej karty. `private_disabled` oznacza wyłączone przez Gecko incognito, nie potwierdzenie detekcji prywatnej. Nie publikować surowego Logcat zawierającego logi innych modułów przeglądarki.

Prompt kontynuacji: „Kontynuujemy Nira Browser, repo laszko14/nira-browser. Sprawdź main, PR #2, CI oraz AGENTS.md i dokumenty przekazania. Etap 3.0 ma minimalny session-bound bridge; test telefonu pozostaje osobnym kryterium. Wykonaj WYŁĄCZNIE Etap 3.1 z VIDEO_DETECTION_PLAN.md: DOM HTML5/MP4/WebM i DetectedMedia. Zachowaj natywne przypisanie i walidację; bez panelu, webRequest klasyfikującego, HLS/DASH i downloadera. Nie powtarzaj etapów 1–3.0. Dodaj właściwe testy, build/lint/test, PR i checkpointy. Nie deklaruj testu telefonu bez dowodu.”

Poniżej zachowano dowody Etapu 2.

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
