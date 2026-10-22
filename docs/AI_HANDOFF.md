# Checkpoint — Nira Browser Etap 4.0 (2026-10-22)

- **Repo/PR:** [PR #8](https://github.com/laszko14/nira-browser/pull/8), HEAD na gałęzi `feat/media-detector-stage-4-0-floating-ui`; baza wyłącznie `feat/media-detector-stage-3-3-state` o HEAD `30cdde1e7cf550106abe452693473e0f5c04eb37`. PR #6 nietknięty. Brak merge.
- **Zakres kodu:** `MediaFloatingUi.kt` — okrągłe 56dp, turkusowe kółko Android View ponad stroną, czerwony licznik unikalnych źródeł, przeciąganie i zapamiętywanie WYŁĄCZNIE znormalizowanej pozycji, przycięcie do widocznego obszaru, zarezerwowane miejsce toolbarów, ukrywanie w fullscreen, Material BottomSheet z formatami i DOM/Sieć. Nie używa SYSTEM_ALERT_WINDOW, HTML, pobierania, DRM, parsera jakości, zewnętrznego emulatora ani nowych źródeł danych.
- **Źródło prawdy:** tylko istniejący `MediaRepository` (3.3), dodano `StateFlow` rewizji i `currentEntries` z walidacją EngineSession/karty/profilu/trybu private, URL i stanu loading. `MediaEntry.id` to nietrwały identyfikator rekordu; po nawigacji, reloadzie, usunięciu lub wymianie wpisu stary callback UI nie powinien otworzyć źródła. URI w repo pozostają w RAM bez logowania.
- **Otwieranie:** `BrowserFragment` wykorzystuje tę samą akcję `ACTION_VIEW` do `xyz.mpv.rex.ui.player.PlayerActivity` co ręczny URL Etapu 2, z fallback chooser. HLS/DASH może wymagać niedostępnych headers/cookies. Kopiowanie oryginalnego URL jest świadomą akcją i może przenieść podpisany link do schowka (Android 13+ oznaczenie sensitive).
- **Testy:** `MediaFloatingStage40Test.kt` zawiera 9 nowych testów Kotlin: puste repo, deduplikacja, przełączanie kart, nawigacja, reload+stale, izolacja profil/incognito, zamknięcie karty, StateFlow i segmenty. Zachowano regresje 3.0–3.3 i testy JS. Testy UI na fizycznym telefonie NIE wykonane w tym środowisku.
- **APK:** `0.5.22-alpha1`, bazowy versionCode `95`, ARM64 versionCode `1095`, `com.prirai.android.nira.debug`, jedyne ABI arm64-v8a. Workflow `stage40_arm64_apk` buduje jeden APK, sprawdza `apksigner`, publikuje artefakt z README. Używa istniejących czterech GitHub Secrets, jeśli kompletne; inaczej jedynie tymczasowy podpis Android Debug z ostrzeżeniem. Nie modyfikuje Secrets. Samo `apksigner verify` NIE dowodzi możliwości aktualizacji: porównaj fingerprint z aktualnie zainstalowanym APK na Nothing Phone (1).
- **CI:** [run 39737877731](https://github.com/laszko14/nira-browser/actions/runs/39737877731) — rozpoczęty po utworzeniu PR; bieżący status sprawdzić na GitHubie (późniejsze commity mogą uruchomić nowy run). Zawsze podawać status aktualnego HEAD, wyniki JVM/JS/lint, signed APK i artefakt dopiero po weryfikacji. `lintDebug` ma historyczne `abortOnError=false`.
- **Dalszy krok:** zweryfikować najnowszy run #8, naprawić konkretne błędy kompilacji/testów z logów, sprawdzić artefakt, skontrolować kod i wykonać test na telefonie (MP4, HLS, drag, fullscreen, przeskok kart, stale URL). **Nie rozpoczynać 4.1, nie scalać.**

---

# Checkpoint — Nira Browser Etap 3.3 (2026-10-22)

- **PR:** https://github.com/laszko14/nira-browser/pull/7 (head `feat/media-detector-stage-3-3-state`, baza `feat/media-detector-stage-3-2-network`, bazowy commit `ab29e263386b180c373f69b2f7020bd14721f168`). PR #6 nie jest bazą; nie wykonano merge.
- **Zaimplementowano:** `MediaRepository.kt` wyłącznie w RAM, natywna izolacja `EngineSession + tabId + contextId + private + BrowserStore URL/loading`, generacje dokumentów WebExtension, deduplikacja DOM/NETWORK z flagami źródeł, oryginalne podpisane URL (bez logowania lub zapisu trwałego), limity 64/karta, 384 globalnie i 128 sesji z danymi. Nawigacje, przeładowania i zamykanie kart usuwają stare dane; karta aktywna nie wpływa na nawigację.
- **Ochrona opóźnionych zdarzeń:** rozszerzenie przy `document_started` tworzy klucz dokumentu; pasywne `onBeforeRequest` przypina żądanie do klucza; `onHeadersReceived` dostarcza tylko powiązane wyniki do ramki top-level. Native bridge dodatkowo sprawdza `EngineSession`, URL BrowserStore, profil i incognito. Przy braku metadanych requestId / wsparcia API zdarzenie jest odrzucane zamiast przypisywane do aktywnej karty. GeckoView na telefonie wciąż nieprzetestowany dla nowej korelacji.
- **Testy / CI:** pierwsza wersja w [run 39730501963](https://github.com/laszko14/nira-browser/actions/runs/39730501963) wykryła 2 błędy repozytorium, poprawione. [Run 39730681284](https://github.com/laszko14/nira-browser/actions/runs/39730681284) na commicie `23f2c1a`: 36/36 Kotlin, 17/17 JS, `lintDebug` SUCCESS i `assembleDebug` arm64 + `apksigner verify` SUCCESS. **Po tym przebiegu** doszła poprawka ponownego użycia sesji przy zmianie profilu/prywatności, optymalizacja ograniczenia pamięci i osobny test Kotlin — końcowe CI dla najnowszego SHA wymaga sprawdzenia w PR.
- **APK:** `com.prirai.android.nira.debug`, `0.5.21-alpha1`, bazowy versionCode 94 (arm64 APK 1094); tylko `arm64-v8a`. W Etapie 3.3 CI sprawdza kompilację, ale NIE publikuje tymczasowo podpisanego APK. Nie znamy certyfikatu wersji faktycznie zainstalowanej na Nothing Phone (1); brak dowodu bezpiecznej aktualizacji, więc nie publikować ani nie instalować jako update. Bez odinstalowywania lub czyszczenia danych.
- **Brak w zakresie:** panel Etapu 4.0, downloader, parser wariantów, player, DRM, emulatory/Appetize/Firebase. Użytkownik testuje telefon co drugą kompilację; nie jest to bramka zakończenia 3.3.
- **Ograniczenia i kolejny krok:** sprawdzić końcowy run GitHub Actions PR #7, 37 testów Kotlin + 17 JS, lint i arm64; skontrolować diff i potwierdzić brak regresji 3.0–3.2. Dopiero po zatwierdzeniu etapów 3.2/3.3 przejść do osobnego Etapu 4.0. Gdy podpis telefonu będzie znany, porównać go ze stałym certyfikatem CI przed jakimkolwiek wydaniem aktualizacyjnym. `lintDebug` ma w projekcie `abortOnError=false`, więc jego sukces nie oznacza czystego raportu lint.

---

# Etap 3.2 — network detection (2026-10-22, CI zweryfikowane)

- Gałąź: `feat/media-detector-stage-3-2-network` dokładnie z HEAD PR #4 `52973f9`. Zależności: PR #2 → #3 → #4 → Etap 3.2. Żaden PR nie jest scalony; nie scalać w niewłaściwej kolejności. Baza nowego PR będzie `main` wyłącznie na potrzeby istniejącego CI dla pull_request.
- W GeckoView / Mozilla Components 156.0.1 działanie `onHeadersReceived` na Nothing Phone (1) jest potwierdzone wyłącznie dla dawnego pliku testowego. Odpowiedzi HLS/DASH, `responseHeaders`, `tabId`, `tabs.sendMessage` i routowanie do sesji wymagają dopiero urządzenia; nie przypisywać im statusu „potwierdzone na telefonie”.
- Rozszerzenie w Etapie 3.2 pasywnie przegląda URL oraz Content-Type bez odczytu body, cookies, Authorization i bez blokowania/modyfikacji odpowiedzi. Klasyfikuje MP4, WebM, OGG, MOV, HLS m3u8 i DASH mpd; pliki segmentów są ignorowane. Jawny sprzeczny MIME niemediowy blokuje dopasowanie po rozszerzeniu. `blob:` i `data:` nie stają się URL mediów.
- Zdarzenie sieciowe z `details.tabId >= 0` jest *próbnie* dostarczane przez `browser.tabs.sendMessage(tabId, ..., {frameId:0})` do content script. Dopiero jego `runtime.sendNativeMessage`, rozpoznane przez `EngineSession` po stronie Kotlin, wiarygodnie przypisuje kartę i profil. Jeśli `tabId` nie istnieje, brak API lub brak potwierdzenia: wyłącznie anonimowy licznik, bez URL i bez przypisania do aktywnej karty. `incognito` odseparowane; anonimowe segmenty i kandydaci nie są przypisywane do żadnego profilu.
- Deduplikacja DOM+NETWORK jest przejściowa: 20 s TTL, maksymalnie 64 unikalne źródła na natywną sesję (plus ograniczone 128 wpisów / 20 s w JS). Etap 3.3 będzie osobną implementacją stanu z obsługą nawigacji; tutaj nie ma listy, parsowania manifestów, downloadera ani modyfikacji MPV.
- Wersja testowa `0.5.20-alpha1`, bazowy code 93 / docelowy kod APK arm64 1093; niezmienny `com.prirai.android.nira.debug`. Osobny PR-only job `temporary_arm64_stage32` bez sekretów z testowym efemerycznym certyfikatem debug, logiem Gradle i weryfikacją apksigner. Ten certyfikat NIE daje gwarancji aktualizacji zainstalowanej wersji; nie usuwać aplikacji ani jej danych.
- Stan z chwili zapisu: kod i testy dodane; wynik CI i rzeczywiste APK **jeszcze niezweryfikowane**. Należy sprawdzić workflow, Kotlin/JVM, testy Node, lint, pojedynczy APK, podpis i artefakty; skorygować błędy bez pomijania dowodów.

## Test urządzenia Etapu 3.2 (po prawidłowym buildzie)

1. Jeśli podpis testowego APK jest zgodny z zainstalowaną Nirą, zaktualizuj bez usuwania danych; jeśli nie, **nie instaluj jako aktualizacja** i przekaż wynik, aby ustalić bezpieczną ścieżkę testu.
2. Na Nothing Phone (1) w Nirze włącz stronę z legalnym publicznym MP4 i WebM, a potem HLS i DASH; po odtworzeniu otwórz menu → **Diagnostyka filmów** → Odśwież.
3. Sprawdź licznik webRequest, HLS, DASH, duplikaty i segmenty. Otwórz drugą kartę, drugi profil, a prywatną kartę tylko przy zezwoleniu Gecko. Sprawdź, czy niesparowany ruch pojawia się wyłącznie jako „bez przypisania”.
4. Wklej wyłącznie zredagowany raport diagnostyczny; nie publikuj adresów z tokenami, cookies ani logów innych modułów.

---
# Etap 3.1D — diagnostyka bez ADB + jedno APK arm64 (2026-10-22)

- Nowa gałąź feat/media-detector-on-device-diagnostics oparta na HEAD PR #3 4328fd5; [PR #4](https://github.com/laszko14/nira-browser/pull/4) stacked względem PR #2 i #3. Nie scalać bez sprawdzenia zależności.
- W menu BrowserFragment dodano **Diagnostyka filmów** z przyciskami Odśwież i Kopiuj raport. Odczytuje lokalny tylko-RAM MediaDetectorDiagnostics: status instalacji extension, stan Gecko incognito, testowy callback webRequest, wykrycia MP4/WebM w bieżącej karcie/profilu, odrzucone zdarzenia. Bez URL/tokenów, bez zapisu, liczniki usuwane po zamknięciu kart; odseparowana prywatność i partycje profili.
- Testy: MediaDetectorDiagnosticsTest.kt (JVM), bez ingerencji w DOM/bridge 3.1; zmiana scope wyłącznie diagnostyczna.
- AGENTS.md — trwała polityka: tylko arm64-v8a dla Nothing Phone (1), jeden APK i ten sam certyfikat podpisu między kolejnymi wydaniami, stałe com.prirai.android.nira.debug, inkrementacja versionCode. app/build.gradle: wyłącznie arm64-v8a, version 0.5.19-alpha1 / bazowy 89 / arm64 code 1089.
- CI build podpisuje aktualizacyjne debug APK wyłącznie przy udostępnionych GitHub Secrets ANDROID_KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD; bez sekretów fail-closed zamiast publikacji losowo podpisanego debug APK. **Nie ma możliwości wymuszenia zgodności z już zainstalowanym certyfikatem, jeśli poprzednie wydanie było podpisane losowym kluczem.**
- [Pierwszy CI PR #4, run 39709690259](https://github.com/laszko14/nira-browser/actions/runs/39709690259): test SUCCESS, lint SUCCESS, **build FAILED** na braku ANDROID_KEYSTORE_BASE64. To problem konfiguracji, nie potwierdzona regresja kompilatora. W repo NIE zapisano klucza ani hasła; do wydania update-safe APK użytkownik musi jednorazowo dodać Secrets.
- Instrukcja stworzenia klucza na Androidzie (Termux) i dodania Secrets: docs/ANDROID_SIGNING_SETUP.md. Nie testowano na telefonie. Nie nazywać APK gotowym bez kompletnego builda i weryfikacji podpisu.

## Następny krok

1. Użytkownik jednorazowo konfiguruje klucz w Settings → Secrets and variables → Actions repo laszko14/nira-browser wg docs/ANDROID_SIGNING_SETUP.md (lub dostarcza poprzedni zgodny keystore z kopii zapasowej).
2. Ponownie uruchom CI PR #4, potwierdź, że powstał dokładnie jeden APK app-arm64-v8a-debug.apk; porównaj fingerprint certyfikatu z zainstalowaną wersją, zanim obiecasz aktualizację bez usuwania.
3. Zainstaluj na Nothing Phone (1), uruchom testowy film HTML5, otwórz menu → Diagnostyka filmów → Odśwież, prześlij zrzut lub zredagowany raport. Test dodatkowej karty/profilu później.

---
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


## Finalne CI Etapu 3.2 (commit kodu `9c4c7b9`)

- [GitHub Actions 39721818021](https://github.com/laszko14/nira-browser/actions/runs/39721818021) **SUCCESS**: `temporary_arm64_stage32` (realne `assembleDebug` i `apksigner verify`), `test` (realne `testDebugUnitTest` + **13/13 Node PASS**), `lint` SUCCESS, release i podpisany build wymagający Secrets SKIPPED. `lintDebug` ma `abortOnError=false` i bazowe ostrzeżenia; nie nazywać go czystym.
- [APK arm64 TEST — ZIP](https://github.com/laszko14/nira-browser/actions/runs/39721818021/artifacts/12401945824): artefakt 145467029 B (archiwum, może zawierać README); zgodnie z jobem tylko jeden `app-arm64-v8a-debug.apk`, cert Android Debug v2 poprawny, fingerprint SHA-256 `c020e311221efd89b76eeab9597e255c9a77e42941f5089f8e0785b81d424aff`.
- [Osobny artefakt logu Gradle](https://github.com/laszko14/nira-browser/actions/runs/39721818021/artifacts/12401866005), [raporty testów](https://github.com/laszko14/nira-browser/actions/runs/39721818021/artifacts/12401446989), [raport lint](https://github.com/laszko14/nira-browser/actions/runs/39721818021/artifacts/12402085239).
- Cert z PR #4 testowego APK: `86a30a10f2ab90053939a547b429fabc31fd0153cb30dd07216b9764776ef8b6`. **Różni się od Stage 3.2**, co uniemożliwia traktowanie go jako aktualizacji tej instalacji. Faktyczny fingerprint aplikacji z telefonu nie został odczytany, więc nie twierdzić, że jest znany. Nie odinstalowywać, nie czyścić danych.
- W trybie private sieć przekazuje `isPrivate` wyłącznie z metadanych Gecko, a Kotlin wymaga zgodności z zaufanym `EngineSession`; przy nieznanym incognito pomija niepewną odpowiedź. Nie można jeszcze zapewnić, że GeckoView 156.0.1 na fizycznym urządzeniu udostępnia `responseHeaders`, `details.tabId` i `tabs.sendMessage` we wszystkich przypadkach. Potwierdzony wcześniej callback probe dotyczył tylko testowego pliku.
- Status: **kod + automatyczne testy + testowy APK w CI — wykonane; aktualizacja instalacji i test telefonu — niepotwierdzone / blokada przez certyfikat**. Jedynym kolejnym krokiem użytkownika jest bezpieczna weryfikacja podpisu na telefonie i, jeżeli zgodny, wykonanie scenariuszy diagnostyki. Nie rozpoczynać 3.3.


## 2026-10-22 — Etap 4.0.1: early discovery

- Bazowa gałąź: feat/media-detector-stage-4-0-floating-ui; HEAD: 2f973dd497c500ccc806993124a1f8dd981e27f7. Robocza: feat/media-detector-stage-4-0-1-early-discovery. PR skierować wyłącznie do 4.0, bez merge.
- Content: bez Play analizuje video.src/currentSrc, wszystkie source, linki, wybrane atrybuty, Open Graph, JSON-LD VideoObject, wybrane literalne konfiguracje JW Player/Video.js/Plyr/HLS.js.
- Ramki: all_frames plus frame_started; klucze frameKey są wydawane przez background dla root pageUrl/documentKey, a routing pasywnych requestów uwzględnia frameId.
- Kotlin: nowy typ PAGE dla źródła deklarowanego przed Play; rozpoznanie jawnych HLS/DASH; deduplikacja exact URL, grupowanie wielu źródeł jednego elementu video; network znaczy potwierdzoną odpowiedź HTTP. Bez manifest parsing etapu 4.1.
- Nie wprowadzono odtwarzania automatycznego, fetch/HEAD, obchodzenia DRM, przechwytywania ciasteczek, logowania podpisanych URL ani utrwalania listy mediów.
- Limit: 48 video per scan, 16 source na video, maks. 12 skryptów po 12 kB, do 128 kandydatów i debounce 240 ms. Repo nadal ograniczone pamięciowo i izolowane od incognito.
- Wersja 0.5.22-alpha2, bazowy versionCode 96, ARM64 1096, package com.prirai.android.nira.debug, tylko arm64-v8a.
- Job stage401_arm64_apk sprawdza apksigner/ID/wersję. Wymaga porównania certyfikatu APK z instalacją na Nothing Phone (1); bez takiego porównania zgodność podpisu i możliwość aktualizacji NIEPOTWIERDZONE.
- Wyniki CI należy odczytać z rzeczywistego PR/run po push. Test na fizycznym telefonie nie wykonany.


## Potwierdzone CI Etapu 4.0.1 (2026-10-22)

- Kod commit 74d9ddf76a6f99ba18764916f0f09c009b57dbec; PR #9 (Draft, base 4.0, bez merge): https://github.com/laszko14/nira-browser/pull/9
- GitHub Actions run 39754764327: https://github.com/laszko14/nira-browser/actions/runs/39754764327 — conclusion SUCCESS; stage401_arm64_apk SUCCESS, test SUCCESS, lint SUCCESS, pozostałe wcześniejsze etapowe joby oraz release SKIPPED.
- Node.js media-detector.test.cjs: 24/24 PASS, 0 fail. Kotlin: task testDebugUnitTest SUCCESS (faktycznie wykonany). Gradle assembleDebug BUILD SUCCESSFUL.
- Dokładnie jeden APK arm64-v8a: com.prirai.android.nira.debug, versionName 0.5.22-alpha2, arm64 versionCode 1096, poprawny APK Signature v2 (apksigner verify).
- Artefakt APK: https://github.com/laszko14/nira-browser/actions/runs/39754764327/artifacts/12415316320 (ZIP, 145504033 B); lint report: https://github.com/laszko14/nira-browser/actions/runs/39754764327/artifacts/12415241782
- WAŻNE: BRAK CI signing secrets; podpis tymczasowego debug certyfikatu SHA-256 = 12655dac69723cfb6d21466615c2971c5d8c265ec198117ba9661e1ca1942423. Ostatnie APK Etapu 4.0 z run 39738739919 ma certyfikat SHA-256 = 857e1fbbb34adabed890f863e07b4a96e288c7203df730f866367275f9c0c02d. Podpisy NIE SĄ zgodne. Nie instalować jako aktualizacja tamtego APK, nie usuwać danych użytkownika. Bez niezależnego porównania z certyfikatem faktycznie zainstalowanej aplikacji nie obiecywać kompatybilności.
- Lint task SUCCESS, ale w projekcie ustawiono abortOnError=false, więc to nie dowodzi braku ostrzeżeń/naruszeń; pozostały bazowe ostrzeżenia Kotlin i workflow.
- Nie wykonano testu na Nothing Phone (1), GeckoView iframe na rzeczywistych stronach wymaga walidacji urządzeniowej. Nie rozpoczęto parsowania wariantów Etapu 4.1.


## 2026-10-22 — Etap 4.0.2: czytelne nazwy i rozmiary w panelu wideo

- Bazowy HEAD Etapu 4.0.1: 6f9c2e237ddc9ddbe313b4dea6b3bda4c40ccc11; nowa gałąź feat/media-detector-stage-4-0-2-metadata-ui, PR wyłącznie do 4.0.1, bez merge.
- Przyczyna UX: panel podawał „Film N”, format i serwer, bez nazwy/rozmiaru; przycisk „Kopiuj adres” miał biały tekst o słabym kontraście.
- Nowe tytuły z video data-video-title/data-title/aria-label/title i nagłówków najbliższego kontenera, JSON-LD VideoObject.name, opisów linków. Awaryjnie og:title/twitter:title/document.title (wyraźnie oznaczone jako tytuł strony), nazwa pliku z URI bez query lub „tytuł nieznany”.
- Rozmiar TYLKO z już pasywnie dostępnych nagłówków istniejących odpowiedzi HTTP: Content-Length dla HTTP 200, Content-Range total dla 206 (limit 1 TB). Dla HLS/DASH nie podawać rozmiaru manifestu jako rozmiaru filmu. Bez pobierania pliku, bez HEAD i bez parsera manifestu. Czas trwania tylko z video.duration, jeśli znany.
- Nowy schemat z ograniczonym title/titleKind/durationSeconds dla page/dom oraz sizeBytes z webRequest/network. Rozszerzenie przekazuje stronie zweryfikowany rozmiar; Kotlin waliduje typy, przedziały i źródła danych. MediaRepository zachowuje bardziej wiarygodną nazwę i rozmiar przy deduplikacji tego samego dokładnego URL; nie przechowuje wyników na dysku.
- Panel: tytuł jako nagłówek grupy; krótki wiersz format/MB/czas; status potwierdzenia i host, jeden poziomy rząd przycisków „▶ MPV Alpha” i „Kopiuj” z ciemnym tekstem. Bez fałszywych rozmiarów.
- Jeden arm64-v8a debug APK, applicationId com.prirai.android.nira.debug, 0.5.22-alpha3, arm64 versionCode 1097. Nie gwarantować aktualizacji podpisem ephemeral CI. Nie usuwać instalacji ani danych.
- Testy: rozszerzone JS o nazwę, JSON-LD, Content-Length/Range, brak rozmiaru manifestu, walidację i wzbogacenie po loadedmetadata; Kotlin o protokół, priorytet nazw i izolację nawigacji. CI wynik dopisać po uruchomieniu; test urządzenia bez APK niepotwierdzony.

## Etap 4.0.2 — zweryfikowane wyniki CI (2026-10-22)

- PR #10 (Draft; base Etap 4.0.1; bez merge): https://github.com/laszko14/nira-browser/pull/10
- Ostatni commit kodu po poprawkach: a841fb75f0d0b825cb225980fa1e6499640c7b24.
- GitHub Actions https://github.com/laszko14/nira-browser/actions/runs/39759954056 — conclusion SUCCESS; stage402_arm64_apk SUCCESS, test SUCCESS, lint SUCCESS; starsze warianty jobów i release SKIPPED.
- 30/30 Node.js testów media-detector PASS, 0 fail; testDebugUnitTest Kotlin BUILD SUCCESSFUL; assembleDebug BUILD SUCCESSFUL; lintDebug SUCCESS. Lint ma abortOnError=false, więc nie jest to deklaracja braku wszystkich ostrzeżeń.
- Jedno APK ARM64: com.prirai.android.nira.debug, versionName 0.5.22-alpha3, arm64 versionCode 1097, podpis APK v2 zweryfikowany przez apksigner.
- Finalny APK ZIP: https://github.com/laszko14/nira-browser/actions/runs/39759954056/artifacts/12417572991.
- Certyfikat CI SHA-256 d076 dla starszego builda 39759508327; UWAGA najnowszy certyfikat z run 39759954056 to 3973e0d0a19a6cc3f2b982de6c7842cd90f862e750cf52f7915134a660126e61. Brak GitHub Secrets; efemeryczny Android Debug. Podpis nie jest gwarantowany zgodny z poprzednim ani z aplikacją użytkownika. Nie odinstalowywać ani nie kasować danych. Wymagane sprawdzenie fingerprintu urządzenia.
- Systematic Debugging: pierwszy build run 39759187428 wykrył uszkodzoną linię validMime (przy edycji regex), naprawa commit 8c1d5cc; kolejny run 39759508327 ujawnił błąd kolejności deklaracji sizeBytes w JS (20/30 PASS), poprawka a841fb7; ostatni run 39759954056 30/30 PASS.
- Funkcje i ograniczenia: tytuł konkretny preferowany ponad tytuł strony, gdy brak metadanych awaryjna nazwa pliku lub „tytuł nieznany”; rozmiar tylko nagłówek sieciowy i tylko bezpośredni materiał; HLS/DASH rozmiar nieznany; czas z video.duration; brak nowych requestów/autoPlay/DRM bypass. Panel kompaktowy, przycisk Kopiuj czytelny.
- Test na Nothing Phone (1) nie wykonany. Brak parsera manifestów Etapu 4.1; nie merge.

## 2026-10-22 — stały klucz podpisywania APK

- Baza: `feat/media-detector-stage-4-0-2-metadata-ui` HEAD `b217038a58d563bac7e601683f923007472dca25`; gałąź robocza `ci/stable-apk-signing`. Bez zmian funkcji detektora i bez merge.
- Audyt potwierdził `com.prirai.android.nira.debug`, wyłącznie `arm64-v8a` oraz brak istniejącego trwałego klucza. Wcześniejsze certyfikaty CI są efemeryczne i wzajemnie różne.
- Utworzono nowy stały klucz PKCS#12: alias `nira-update`, RSA 4096, SHA256withRSA. Publiczny SHA-256 certyfikatu: `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`.
- Prywatny klucz i hasło nie znajdują się w repozytorium. Utworzono dwuczęściową prywatną kopię odzyskiwania poza GitHubem; nadal zalecana jest dodatkowa kopia offline.
- Wersja instalacyjna: `0.5.22-alpha4`, bazowy versionCode 98, arm64 versionCode 1098.
- Jedyny job budujący instalacyjny APK działa fail-closed dla wszystkich PR-ów i pushy, wymaga czterech Secrets, usuwa tymczasowy keystore, a skrypt walidacyjny sprawdza certyfikat, pakiet, wersję oraz pojedyncze ABI arm64-v8a przed publikacją artefaktu. Usunięto historyczne joby z efemerycznym podpisem. Job tagowanego release ma te same kontrole i cleanup.
- PR #11 (Draft, bez merge): https://github.com/laszko14/nira-browser/pull/11. CI https://github.com/laszko14/nira-browser/actions/runs/39807671100 zakończyło build/test/lint SUCCESS; zweryfikowany APK: https://github.com/laszko14/nira-browser/actions/runs/39807671100/artifacts/12439380190. Log potwierdza pakiet, wersję, wyłącznie arm64-v8a i przypięty certyfikat projektu.
- Test aktualizacji na Nothing Phone (1) nie został wykonany. Nowy certyfikat nie jest zgodny z udokumentowanymi efemerycznymi certyfikatami wcześniejszych APK; zgodność z faktycznie zainstalowaną aplikacją wymaga sprawdzenia na urządzeniu.
