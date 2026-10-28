

## 2026-10-27 — Zavro Clean v1180, metadata engine (prace w toku)

- Baza v1179: `856281be65561c4fd54c0b97fd7ba28ca6018f51` (PR #94), niezależna gałąź `feat/zavro-clean-v1180-metadata-engine-20261027` bez merge.
- Parser DASH: dziedziczenie width/height/fps/kodeków, hierarchiczne BaseURL i okresy; SegmentTemplate/SegmentList nie stają się fałszywymi MP4.
- Parser HLS: oddzielne peak/average bitrate, FPS i grupy ścieżek; WebExtension przekazuje rozdzielczość 2D i ilość wariantów do walidowanego modelu Android.
- Miniatury: usunięto bezwarunkowy pierwszy IMG; słabszy kandydat nie zastępuje mocniejszego.
- Estimate rozmiaru tylko gdy bitrate jawnie oznaczony jako średni; nie stosuj peak bitrate jako średniego.
- VersionName 0.5.22-alpha85-clean, kod Gradle 180 -> finalne ARM64 versionCode=1180.
- Testy: nowe testy Node/DASH/HLS i Kotlin walidacji kontraktu; wynik CI i APK do potwierdzenia.
- Nie ukończono: pełny tryb rozszerzonej diagnostyki strony ZIP, pełny model każdej ścieżki audio/napisów i UI wyboru jakości; nie oznaczać tego jako complete.

---
# Checkpoint — ZAVRO v1115 (2026-10-24)

- Baza v1114: `feat/zavro-v1114-settings-stability`, stan `281b1bc0e21e1f452641d8ba7ca03071aba8c7fa`.
- Kod wdrożenia: `feat/zavro-v1115-bottom-tabs-icon`, commit `1d7a7ba5d8084d71e34c4121989143a578a3026c`, draft PR #28: https://github.com/laszko14/nira-browser/pull/28 — brak merge.
- Fix dolnego paska: animacja minimalnego górnego toolbaru nie ukrywa wydzielonego paska kart/grup; oddzielny bottom Compose view resetuje stan przy podpięciu; GeckoView przelicza margines względem jego rzeczywistej wysokości po obrocie/zmianie grup.
- Ikona: grafika dostarczona przez użytkownika, przekonwertowana na WebP 192×192 (zasób `drawable-nodpi/zavro_app_icon.webp`), foreground adaptive launcher icon.
- Wersja: `com.prirai.android.nira.debug`, `versionCode=1115`, `versionName=0.5.22-alpha21`, tylko `arm64-v8a`.
- GitHub Actions #104, run https://github.com/laszko14/nira-browser/actions/runs/40073101224 — build SUCCESS / test SUCCESS / lint SUCCESS. Zadanie walidacji APK potwierdziło certyfikat SHA256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`.
- Artefakt `zavro-arm64-update-apk-v1115`: https://github.com/laszko14/nira-browser/actions/runs/40073101224/artifacts/12551941931. SHA256 wypakowanego APK: `5e2f5bd95bd2baae2eed23320350aab017ed6805a6af8c8a3be06784a41e9981`.
- Niezweryfikowane na fizycznym telefonie: renderowanie i dotyk dolnego paska po przewinięciu w pionie/poziomie oraz wygląd ikony w launcherze. Nie usuwać danych aplikacji; aktualizacja tylko przy zgodnym pakiecie/certyfikacie i niższym `versionCode`.
- Następny krok: instalacja jako aktualizacja na Nothing Phone (1), sprawdzić pasek u dołu (przewijanie/obrót/wybór kart/grup) oraz nową ikonę. W razie dalszego czarnego paska zebrać logcat i wymiar/widoczność ComposeView.

---

# Checkpoint — Nira Browser v1109 (2026-10-23)

- Base v1108: `9a44af0af5415c22c686ba348bfcfa97e5bf59f0`, gałąź `feat/nira-v1108-player-preview-landscape`; kod v1109 do podpisanego APK: `2c7f2011ff7611d62be6ed176afd719ea1f2410b`.
- Nowa gałąź: `feat/nira-v1109-signed-stream-revisions`, draft PR #22 https://github.com/laszko14/nira-browser/pull/22. Brak merge.
- Wykonane: RAM-only `MediaStreamIdentity` i do 8 kolejnych `MediaUrlRevision` na logiczny wariant HLS/DASH; provenance observed/derived, preferencja prawdziwego requestu, bez rekonstrukcji podpisów, frameKey, HLS lineage, naprawa zmyślonych DASH child-video, odczyt świeżego URL z repozytorium w chwili Play/Kopiuj, ograniczony Referer przez istniejące legacy MPV `headers`.
- Testy: GitHub Actions run https://github.com/laszko14/nira-browser/actions/runs/39877337815: build SUCCESS, test SUCCESS (Node 45/45 PASS, Gradle unit tests SUCCESS), lint SUCCESS. Signed APK `nira-arm64-update-apk-v1109`: https://github.com/laszko14/nira-browser/actions/runs/39877337815/artifacts/12469470813.
- CI signer potwierdził: pakiet `com.prirai.android.nira.debug`, code 1109, name `0.5.22-alpha15`, ABI `arm64-v8a`, publiczny cert SHA256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`. SHA256 samego APK: `bfe33809d2d4ccf18dc788a0ec3a4320b2437012d44a428bf1cf0f3b8eeccab9`.
- NIEZWERYFIKOWANE: fizyczna instalacja na Nothing Phone (1), działanie na fulltaboo.tv, poprawność wszystkich iframe w realnym GeckoView, zgodność certyfikatu z faktycznie zainstalowaną aplikacją.
- Poza obecnym zakresem: pełny aktywny bounded validator 200/403/404 i segment probe, automatyczny retry MPV→Nira, pełny parser DASH XML, przegląd i utwardzenie osobnego binarium MPV Alpha. Nie przedstawiać v1109 jako kompletnego wdrożenia wszystkich punktów audytu.
- Następny krok: ręczny test A/B signed HLS (oryginalny problem), zbieranie diagnostyki bez tokenów, potem P1 bounded validation / iframe referer i osobne testy MPV header handling. Zachować ten sam podpis i rosnący versionCode.

---

# Checkpoint — Nira Browser v1107, Event-First Media Engine P0 (2026-10-23)

- **Gałąź / PR:** `feat/nira-v1107-event-first-media-engine`, draft PR #20 https://github.com/laszko14/nira-browser/pull/20, baza wyłącznie v1106 `3fcbd8c71817e66881dd4421fdb64bbd43a6e5ee`, bez merge.
- **Zakres wdrożony:** ograniczony parser HLS master/media (warianty, rozdzielczość, bitrate, kodeki, audio/napisy, URL względne, czas VOD) oraz podstawowy DASH (MPD/AdaptationSet/Representation/BaseURL, osobne audio, czas); dodatkowa analiza manifestu bez segmentów; stabilne logiczne grupy manifest+wideo-warianty; jakość i bitrate w panelu.
- **Granice bezpieczeństwa/wydajności:** dodatkowy fetch manifestu jest anonimowy (`credentials=omit`) — nigdy nie używa globalnie cookies profilu; limit strumieniowy 512 KiB, 4 s, 8 wariantów, kolejka 8, 2 równolegle; cache jest per karta/dokument/private, zadania anulowane przy nawigacji/zamknięciu; redirect do segmentu lub nie-manifestu odrzucany przed czytaniem body; signed URL pozostaje niezmieniony.
- **Testy:** lokalnie 41/41 Node PASS. Pierwszy CI wykrył błąd składni operatora Kotlin i został naprawiony. Niezależny review wykrył problem credential isolation oraz cztery problemy Important; naprawiono je i wykonano re-review. Run końcowego kodu aplikacji https://github.com/laszko14/nira-browser/actions/runs/39863514031: build SUCCESS, test SUCCESS, lint SUCCESS (historyczne `abortOnError=false`).
- **APK kodu aplikacji:** artefakt `nira-arm64-update-apk-v1107` https://github.com/laszko14/nira-browser/actions/runs/39863514031/artifacts/12463321069. Walidator potwierdził `com.prirai.android.nira.debug`, `versionCode=1107`, `versionName=0.5.22-alpha13`, tylko `arm64-v8a`, certyfikat SHA-256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`.
- **Świadome ograniczenia:** to nie jest pełna specyfikacja HLS/DASH. Brak DASH SegmentTemplate/SegmentTimeline, dynamicznego odświeżania MPD, pełnej prezentacji list audio/napisów, potwierdzonej widoczności Service Workerów i przekazywania headers/cookies do MPV Alpha. Manifest wymagający cookies zostanie wykryty jako źródło, ale anonimowa analiza wariantów może się nie udać. Brak testu fizycznego Nothing Phone (1) oraz benchmarku 1DM.
- **Następny krok:** test urządzeniowy: direct MP4, HLS master 2–3 jakości, HLS wymagający sesji, DASH osobne audio, iframe/SPA, reload/karty/private/fullscreen/powrót z MPV; zebrać precision/recall oraz p50/p95 względem 1DM. Potem P1 według wyników, bez deklarowania przewagi bez pomiaru.

---

# Checkpoint — Nira Browser v1105, stabilizacja interfejsu (2026-10-22)

- **Gałąź:** `feat/nira-v1105-stability-toolbar-drag`, PR #18 https://github.com/laszko14/nira-browser/pull/18, baza PR #17 / `feat/nira-v1104-auto-bars-media-tabs`. Bez merge.
- **Zmiany:** przełącznik rzędu chipów grup `Wszystkie/Grupa` (bez wyłączenia grup i paska kart); suwaki szerokości przycisków 40–52 dp i minimalnej szerokości pola adresu 64–220 dp; przeciąganie długim przytrzymaniem przycisków górnego paska z trwałą kolejnością; przeliczenie widoczności akcji przy zmianie szerokości, priorytet dostępności Ustawień; DnD kart w kompaktowym wysuwanym panelu po long press, menu pod ⋮, przenoszenie do grup i zmiana kolejności wewnątrz grup; pull-to-refresh: odświeżanie przywracane po wyjściu z fullscreen oraz wyrównanie obszaru gestu z treścią strony.
- **Wersja:** `0.5.22-alpha11`, efektywny kod ARM64 `1105`, aplikacja `com.prirai.android.nira.debug`, wyłącznie ARM64. Dotychczasowy pin certyfikatu SHA256: `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`, Secrets GitHub Actions bez modyfikacji.
- **Audyt:** zdiagnozowano brak drag w `CompactSideTabRow` (wyłącznie `combinedClickable`), sztywne jednorazowe przycinanie akcji paska oraz bezwarunkowe `swipeRefresh.isEnabled=false` po fullscreen. Stan bazowy v1104: CI PASS, test urządzenia NIE wykonany. Historycznie lint ustawiony na `abortOnError=false`.
- **CI v1105:** pierwszy przebieg #75 wykrył jeden brakujący import `background` w Compose; poprawiono w kolejnym commicie. Najnowszy HEAD wymaga oddzielnego potwierdzenia zielonego build/test/lint i artefaktu: https://github.com/laszko14/nira-browser/actions?query=branch%3Afeat%2Fnira-v1105-stability-toolbar-drag .
- **Niezakończone / ryzyko:** konieczne sprawdzenie na Nothing Phone (1) po instalacji: przeciąganie między grupami, zmiana kolejności w obrębie grup i bez grup, przechwytywanie long press przez Compose, menu ⋮ w landscape i po obrocie, pull-to-refresh (po fullscreen/zmianie karty, różne strony), oraz zachowanie detektora mediów. Drop na kartę należącą do innej grupy przenosi do grupy (bez obietnicy dokładnego indeksu); pozostałe testy automatyczne nie zastępują testów UI.
- **Bezpieczeństwo aktualizacji:** nie odinstalowywać poprzedniej wersji. Aktualizacja bez kasowania danych wymaga pakietu/certyfikatu identycznych jak zainstalowana Nira i większego versionCode; same wyniki CI nie weryfikują starej instalacji.
- **Następny krok:** potwierdzić sukces najnowszego GitHub Actions, zobaczyć `nira-arm64-update-apk-v1105`, zbadać ewentualne błędy i poprawić, sprawdzić podpis, potem przekazać artefakt ZIP. PR pozostaje draft.

---

# Checkpoint — Nira Browser v1104: pasek scroll, tytuły, grupy i wykrywanie mediów (2026-10-22)

- **Gałąź:** `feat/nira-v1104-auto-bars-media-tabs`; PR #17 https://github.com/laszko14/nira-browser/pull/17; baza v1103 / PR #16, bez merge.
- **Zmiany 1–5:** przesuwanie całego górnego toolbara (adres i karty) przy przewijaniu w dół/górę; rozszerzanie nazw obok miniatur filmów w DOM (bez sztucznego limitu wierszy); ulepszenie nazw/miniatur panelu detektora z danych odtwarzacza/JSON-LD; dłuższe menu kart po przytrzymaniu z przenoszeniem/tworzeniem grup; uniwersalne skanowanie player JSON, metadanych i dynamicznego DOM, bez priorytetu CDA.
- **Pliki:** `ModernScrollBehavior.kt`, `TabMenuCompose.kt`, `TabViewModel.kt`, `TabSheetListCompose.kt`, `GroupContainerCompose.kt`, `extensions/nira-media-detector/*`, `MediaMessageProtocol.kt`, `MediaRepository.kt`, `MediaFloatingUi.kt`.
- **APK:** `com.prirai.android.nira.debug`, `arm64-v8a`, efektywny `versionCode=1104`, `versionName=0.5.22-alpha10`; stały certyfikat SHA-256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00` przez wymagane GitHub Secrets, bez zmiany wartości kluczy.
- **Dotychczasowe CI:** pierwszy run https://github.com/laszko14/nira-browser/actions/runs/39844837800 — FAIL na brakujących importach ikon; poprawiono w `6024bd6`. Drugi run https://github.com/laszko14/nira-browser/actions/runs/39845560383 — build z podpisem PASS, testy Kotlin PASS, JS FAIL (3 regresje dynamicznego skanu JSON-LD); poprawiono przez wymuszanie skanu po zmianie DOM w `0174536`. Ostateczny wynik dla najnowszego HEAD należy sprawdzić; nie nazywać APK zweryfikowanym przed SUCCESS i istnieniem artefaktu.
- **Testy sprzętowe:** nie przeprowadzono na Nothing Phone (1). Ostateczną kompatybilność aktualizacji sprawdza zgodność certyfikatu już zainstalowanej aplikacji, jej `applicationId` i niższy `versionCode`. Nie odinstalowywać ani nie usuwać danych. Nie ma gwarancji działania na każdej stronie ani obsługi DRM.
- **Kolejny krok:** sprawdzić ostatni Actions build, unit tests, JS, lint, `verify-update-apk.sh`, pobrać link do aktualnego artefaktu; przetestować przewijanie i otwieranie menu/grup oraz uniwersalny detektor na urządzeniu.

---

# Checkpoint — Nira Browser v1103: hybrydowe grupy kart (2026-10-22)

- **Gałąź:** `feat/nira-hybrid-tab-groups-v1103`; baza: PR #15 / commit `47d63e4554b4cca44c038e8463301e80c008423e`. Brak merge.
- **Zakres:** nad dotychczasowym górnym paskiem kart pojawia się przewijany pasek `(n) Wszystkie` i nazwanych, kolorowych grup z licznikami aktywnych kart. Dotknięcie grupy przełącza aktywną kartę; długie przytrzymanie wywołuje istniejące menu edycji. W aktywnej grupie drugi rząd pokazuje karty tej grupy (favicon, tytuł, zamknij), a `+` otwiera nową kartę w grupie przez istniejący TabOrderManager. W trybie Wszystkie działa dotychczasowy pasek z drag & drop; pełny panel kart zachowano.
- **Kod:** `HybridGroupStrip.kt`, `ComposeTabBarWithProfileSwitcher.kt`; bez zmian detektora mediów i MPV Alpha.
- **Wersja:** `0.5.22-alpha9`, efektywny ARM64 `versionCode=1103`, `com.prirai.android.nira.debug`. Istniejący pin certyfikatu SHA-256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00` pozostaje.
- **Kontrole wymagane:** GitHub Actions build/test/lint i pojedynczy podpisany ARM64 APK; dopiero po ich sukcesie podać artefakt. Sprawdzić na Nothing Phone (1) wybieranie grup, przejście z zewnętrznego linku, zmianę grup, zamykanie kart, tryb poziomy, gesty 1DM i przycisk filmów.
- **Ograniczenie:** kompatybilność instalacji bez utraty danych wymaga zgodnego certyfikatu już zainstalowanej wersji. Nie odinstalowywać, nie scalać automatycznie.

---

# Checkpoint — Nira Browser v1102: boczne karty / grupy / MPV Alpha (2026-10-22)

- **Repo/gałąź:** `laszko14/nira-browser`, PR #15 https://github.com/laszko14/nira-browser/pull/15, `feat/nira-v1102-tab-panel-player-fixes`, bez merge; baza PR #14 `feat/nira-compact-groups-immersive-v1101` (42872c3).
- **Zmiany:** w poziomie zapobieganie obcinaniu przycisku Ustawienia; w panelu bocznym kompaktowe jednowierszowe karty z favicon, numerem, X i zwijanymi grupami; ukrycie przełącznika widoku, Search/New Tab i profili wyłącznie w tym panelu; przeciągany prawy brzeg z osobną szerokością na orientację, kliknięcie poza panelem zamyka go; synchronizacja stanu rozwinięcia grup Compose / Room; pozycja opcji grupy pod „Otwórz w nowej karcie”; domyślne otwieranie w MPV Alpha z opcją wyświetlania systemowego wyboru.
- **Kod APK:** commit `74e37889ea98c1903d5f174674347c804f51806f`; `com.prirai.android.nira.debug`, `versionCode=1102`, `versionName=0.5.22-alpha8`, tylko `arm64-v8a`.
- **CI:** https://github.com/laszko14/nira-browser/actions/runs/39830853219 — podpisany build SUCCESS, JVM test SUCCESS, JavaScript 30/30 PASS; wynik lint sprawdzić w runie. Artefakt signed update ZIP: https://github.com/laszko14/nira-browser/actions/runs/39830853219/artifacts/12448218095.
- **Podpis zweryfikowany w CI:** SHA-256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`, bez sekretów w repo. Poprzedni podpis aplikacji na telefonie musi być zgodny; jeśli jest inny, instalacja bez utraty danych nie jest gwarantowana.
- **Ograniczenia:** nie testowano osobiście na Nothing Phone (1), gestów, działania grup, landscape i integracji odtwarzacza na sprzęcie. Nie twierdzić, że fizyczny test przeszedł. Nie scalać przed ręcznym sprawdzeniem. Dokumentacja jest następnym commitem bez zmiany kodu aplikacji.

---

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

## 2026-10-22 — poprawka znikającego pływającego guzika (Etap 4.0.3)

- Base ci/stable-apk-signing HEAD 9d18d6e500c2505a96779d5006db7e055c3e4517; roboczy branch fix/media-floating-disappears-after-load; bez merge.
- Zgłoszenie: przycisk z liczbą filmów pojawia się, a po chwili znika. Wyścig: BrowserFragment selectedDocument aktualizuje loading=false przed MediaDetectorController.observe(), a MediaRepository.currentEntries odrzuca wynik przez strict loading mismatch. Zmiana stanu true→false nie powoduje repository revision.
- Poprawa: currentEntries pozwala UI widzieć już zweryfikowane wpisy tego samego dokumentu po zakończeniu ładowania, w tym krótkim okresie przed reconcile; nowy load nadal ukrywa stare wpisy. MediaFloatingUi.render przenosi widoczne kółko nad rodzeństwo.
- Test regresji w MediaFloatingStage40Test sprawdza wynik przed/po reconcile i nowy reload.
- Nowy arm64 APK: 0.5.22-alpha5, versionCode 1099; ta sama konfiguracja stałego podpisu co PR #11, bez odinstalowywania i kasowania danych. CI i działanie na urządzeniu wymagają oddzielnej weryfikacji.


## 2026-10-23 — Nira v1110 (pozostałe poprawki UI, bez zmian detektora)
- Bazowa gałąź: `feat/nira-v1109-signed-stream-revisions` (PR #22; commit `dd06c26057395341d103bfb672583010f5a5b6bc`). Należy zachować wykonane już tam poprawki HLS; nie powtarzać prac detektora.
- Nowa gałąź: `feat/nira-v1110-toolbar-titles-system-nav`, cel PR: gałąź v1109. Żadnego merge do `main`.
- Poprawki: BrowserActivity wymusza widoczność normalnych `navigationBars()` przy starcie i nie ukrywa ich po fokusie; WebContentPositionManager nie dodaje powtórnie statusBarHeight do GeckoView; BaseBrowserFragment nie rezerwuje w Gecko drugiego dynamic-toolbar; `full-titles.js` znajduje podpis pod miniaturą w nadrzędnej karcie i neutralizuje zagnieżdżony clamp.
- Wersja: `0.5.22-alpha16`, ARM64 `versionCode=1110`, pakiet `com.prirai.android.nira.debug`, nadal CI-stały certyfikat `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`.
- Regresja: działające menu ⋮, przyciski paska i inline podglądy wideo powinny pozostać bez zmian; nowe testy JS dla podpisów.
- Status testów i APK: czeka na uruchomienie/ukończenie workflow PR; NIE deklarować urządzeniowej weryfikacji do ręcznych testów.

# Checkpoint — Nira Browser v1111 (2026-10-23)

- Gałąź `feat/nira-v1111-signed-hls-playback`, baza v1110/PR #23, bez merge.
- Naprawiono propagację `frameUrl` do Referer MPV, statusy weryfikacji HLS oraz ograniczony test pierwszego segmentu. Pełne podpisane URL pozostają nietknięte; fetch nie dołącza cookies.
- Błędy HTTP 401/403 są oznaczane jako błąd autoryzacji, 404/410 jako wygasły link. Takich wpisów nie można uruchomić z panelu.
- Wersja `0.5.22-alpha17`, efektywny ARM64 `versionCode=1111`, oczekiwany pakiet `com.prirai.android.nira.debug` i stały certyfikat `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`.
- Testy JS: 49/49 PASS. Lokalne Gradle zablokowane brakiem dostępu do dystrybucji; wymagane CI. Test realnej strony i telefonu NIE wykonany.

## 2026-10-28 — Zavro Clean v1184 integracja UI v1183 + MME etapów 1–6

- **Zainstalowana wersja użytkownika:** v1183. Baza UI: `feat/zavro-clean-v1183-toolbar-personalization-20261027` commit `389dbfe8287408b65f8b29673ffc7b9c423276db` (Draft PR #102).
- MME etapy 1–4 są wspólną bazą UI v1183 i dalszej pracy MME; gałąź MME etapów 5–6 kończy się commitem `f5beeedcf571cd67e8599d51b32ef537f8866c5d` (PR #104). Merge do **osobnej** gałęzi `feat/zavro-clean-v1184-mme-integrated-update-20261028` wykonał PR #105, commit `8bd414e3c1664751de994a38f6f09df072358bf4`. **Bez merge do main.**
- Efektywny `arm64-v8a versionCode=1184` (`app/build.gradle` bazowy `versionCode=184` + ABI offset 1000), `versionName=0.5.22-alpha89-clean`, pakiet `com.prirai.android.nira.debug`, oczekiwany certyfikat SHA256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`.
- Zachowano wszystkie zmiany UI v1183: personalizację pięciu przycisków w pionie i poziomie, okrągłe favicon oraz licznik kart w polu adresu. Porównanie gałęzi v1183 i v1184 nie wykazało nadpisania plików UI (tylko MME 5–6, Gradle i workflow).
- v1184 CI używa wyłącznie stałego sekretnego klucza GitHub Secrets, a `scripts/verify-update-apk.sh` sprawdza pakiet, wersję, ABI i odcisk certyfikatu; błędny podpis lub brak sekretów mają zakończyć job błędem.
- MME 1–6: TestLab 120 s, shadow identity, indywidualne tytuły, przypisywanie miniaturek, poster provenance, pasywny network correlation shadow. Nie przypisuje niepewnych źródeł sieciowych do innego filmu i nie zmienia odtwarzania.
- **Status w chwili checkpointu:** gałąź integracyjna z wersją v1184 przygotowana; pełny PR build/test/lint CI zostanie uruchomiony. Nie deklarować gotowego APK przed rzeczywistym sukcesem joba `build` i obecnością podpisanego artefaktu.
- **Dalszy krok:** test+lint+signed ARM64 APK → sprawdzenie raportów CI → podanie prawidłowego linku do ZIP z APK. Test fizycznego telefonu i rzeczywistych stron nadal należy do użytkownika. Bez publikacji Google Play/main.

## 2026-10-28 — smart tab scrolling v1188: verified delivery

- Branch `feat/zavro-clean-v1188-smart-tab-scroll-20261028`, Draft PR #110:
  https://github.com/laszko14/nira-browser/pull/110. Base Clean v1187 PR #109,
  `dea9f3bdaad785a4cb312d42d0096cc23cd11022`. No merge/main/x MPV edits.
- Verified source/test commit: `35d5ab3aab5f965398793987fc3a0314929ba118`.
  Following checkpoint commit changes docs only and skips unnecessary CI rerun.
- Shared measured visibility policy integrated with native and Compose strips.
  Full visible group wins; comfortable icon does not move; clipped targets center
  within real range; collapsed group stays collapsed; one tab resets left.
  Latest checks cancel previous ones; native child touch and Compose fling protected.
- Final CI https://github.com/laszko14/nira-browser/actions/runs/40752492399 SUCCESS:
  build/test/lint SUCCESS; release skipped. Downloaded test report: 371 Kotlin,
  zero failed/skipped (6 policy + 6 native visibility); JS 212/212 PASS.
  Lint job PASS does not imply zero findings (`abortOnError=false` is pre-existing).
- APK https://github.com/laszko14/nira-browser/actions/runs/40752492399/artifacts/12845111607
  (`zavro-arm64-update-apk-v1188`). ZIP CRC and exactly one APK independently checked.
  Existing verifier/apksigner/aapt independently PASS after download: package
  `com.prirai.android.nira.debug`, code `1188`, name `0.5.22-alpha93-clean`, arm64-v8a only,
  certificate SHA256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`.
  APK SHA256 `17636c4d36e88cbd21ab8f72cd16df7e1483f3b99af8e44f40af5360b0b95231`.
- Detailed criterion statuses: `docs/SMART_TAB_SCROLL.md`. Independent review's two
  gesture issues fixed. One subsequent test fixture failure (looper drained before
  touch) corrected; final suite green. Local Android Gradle cache was incomplete;
  full Android verification came from CI, not a claimed local build.
- NOT_TESTED: physical-phone UI in both orientations, actual gestures/drag, 120 Hz,
  and installation over the user's existing app. No device is connected.
- Next: install the verified APK as an update on Nothing Phone (1), exercise the
  device scenarios in SMART_TAB_SCROLL.md. Do not repeat completed CI/unit work;
  change code only if device evidence exposes a defect. Keep PR draft/unmerged.

## 2026-10-28 — Zavro Clean: regresja wykrywania i tytułów MME (bez APK)

- Cel: niezależność poprawnych źródeł od opcjonalnych metadanych, indywidualne tytuły,
  warianty jednego filmu, bezpieczny fallback i kontynuacja skanowania przed Play.
- Baza potwierdzona lokalnie i na GitHub: v1188 / draft PR #110,
  `41f24ed4c04e783ffd2f1ca447f3aff043fc9dcd`; PR pozostaje otwarty, bez merge.
  Początkowy checkout `work` / `0823f24eb48e2eefbe38c7616bc1d55e73917b37` był czysty
  i nie zawierał bieżącego Clean. Nie użyto main jako bazy. Odczytano AGENTS.md v1.5.
- Gałąź: `fix/zavro-clean-mme-detection-titles-20261028`.
- Zapisany i testowany kod/testy: `21abfc1fe78d3d2855368c8afd9cc1584c1b5b93`.
  Następny commit dodaje wyłącznie ten checkpoint; jego SHA podaje końcowy raport/Git.

Zweryfikowane przyczyny i poprawki:
- Selektywne porównanie Clean v1184 `c7a4d17e`, v1185 `6e6f08d3`, v1186 `1f418851`:
  v1186 usunęło fallback plikowy z kafelka. Przywrócono go z widocznym prefiksem
  „Plik (niepotwierdzony):”, filtrami nazw technicznych, hashy, UUID i długich tokenów.
- Wywołanie selectGroup uzależniało tytuł strony od liczby rekordów. Liczy teraz
  grupy filmów wraz z frameKey. Content dopuszcza stronę tylko dla jednego
  jednoznacznego odtwarzacza; kilka deklaracji jakości nie zwiększa liczby filmów.
  Konflikt lokalny nie jest maskowany przez tytuł strony; sieć nie pożycza document.title.
- cardTitle traktowało dodatkowy opis jako równorzędny tytuł. Nazwane lokalne tytuły
  i nagłówki mają pierwszeństwo przed ogólnym podpisem/opisem. Równorzędne sprzeczne
  nazwy pozostają nieznane. Zagnieżdżona karta nie pożycza tytułu od nadrzędnej.
  Tytuł lokalnej karty ma pierwszeństwo przed tekstem bezpośredniego linku.
- Mutation handler pomijał część obsługiwanych klas kart i zwykłe etykiety
  aria-labelledby. Późny tytuł wznawia skanowanie własnej karty, zachowując grupę.
- Błąd sendMessage przy metadanych HLS master (dodanych w v1186) przerywał warianty.
  Dostarczanie metadanych/każdego wariantu jest izolowane od kolejnych źródeł.
  Nieprawidłowy opcjonalny CODECS jest pomijany u producenta komunikatu, bez
  rozluźniania walidacji IPC. Test przechodzi przez rzeczywisty walidator content.
- Limity 4 ms / 96 elementów nie były stałym odcięciem skanowania; zachowywały indeks.
  Natomiast producent mógł przepełnić kolejkę natywną. Skaner czeka teraz na miejsce
  w IPC i wznawia pracę z kursora, bez pętli odpytywania i dodatkowych skanów/sieci.
  Zastany limit 64 rekordów/kartę uniemożliwiał zachowanie 100 filmów; podniesiono go
  do 128, utrzymując globalny limit 384 i istniejące pozostałe ograniczenia.
- Grupa elementu była ponownie używana po zmianie filmu. Nowy zestaw źródeł HTML5,
  deklaracji i linków dostaje nową grupę; zmiana samego tytułu lub wybór istniejącej
  jakości zachowuje grupę. Źródło src ma pierwszeństwo przed opóźnionym currentSrc.
- Warianty parsowanego mastera mogły pozostać osobnym kafelkiem obok tytułowanego
  źródła DOM. Dokładnie zaobserwowane źródło wiąże grupę manifestu z jednym lokalnym
  właścicielem w tej samej ramce. Nie zastępuje jego identyfikatora ani nie łączy
  przez tekst tytułu. Dwóch sprzecznych właścicieli nie nadaje nazwy wariantowi.

Weryfikacja końcowa:
- PASS: `node --test app/src/test/js/*.test.cjs` — 218/218, zero pominiętych.
- PASS: `./gradlew testDebugUnitTest --max-workers=2 --console=plain` — 376/376,
  66 raportów klas, zero błędów/pominięć. Obejmuje grupowanie, tytuły, 100 rekordów,
  dokładne źródła, deduplikację, ramki, nawigację, profile i prywatność.
  Pełne testy lokalne wymagały środowiskowego proxy JVM i zaufanego magazynu CA:
  `JAVA_TOOL_OPTIONS="-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/workspace/setup/cacerts"`,
  `GRADLE_USER_HOME=/workspace/.gradle`, `ANDROID_HOME=/workspace/android-sdk`.
  Pierwsze uruchomienia ujawniły brak cache, potem brak proxy/CA dla Robolectric;
  końcowy pełny przebieg zakończył się BUILD SUCCESSFUL. Nie zmieniono konfiguracji repo.
- PASS: `MME_CHROMIUM=/usr/bin/chromium node --test app/src/test/browser/mme-regression.cjs`
  — 9/9. Runner używa istniejącego Playwright/Chromium, niczego nie instaluje i nie
  dodaje zależności projektu. Uruchamia rzeczywisty content/background i protokół,
  zastępując granicę WebExtension/native. Wszystkie żądania fixture są przechwycone.
  Realny DOM: 10/100/200 filmów przed Play, kolejka, indywidualne tytuły/opisy,
  późny DOM/aria, warianty HLS, SPA, Shadow DOM, iframe HTTP, deklaracja HLS obok
  blob/MSE, konflikty, brak tytułu i wymiana źródeł/deklaracji/linków.
  To nie jest test GeckoView ani potwierdzenie odtwarzania lub rzeczywistych witryn.
- Reprodukcje RED/GREEN: tytuł + opis, późny video-tile/aria, źródło/grupa,
  master/pojedynczy wariant po błędzie IPC, odrzucony CODECS, utrata przy 200 filmach,
  page fallback i warianty tytułowanego mastera. Zaktualizowano stare oczekiwania
  fallbacku; zastąpiono tekstowy regex kafelka testem faktycznej prezentacji Kotlin.
- PASS: przegląd końcowego diffu i `git diff --check`.
- NOT_TESTED: osobny lint, fizyczny telefon, rzeczywiste witryny i zachowanie GeckoView.
- APK: nie kompilowano instalacyjnego APK, nie zmieniano wersji/podpisu.
  Nie modyfikowano Zavro x MPV, GeckoView, 120 Hz ani workflow.
- CI/PR: NOT_TESTED; brak nowego run ID. Push tej gałęzi nie pasuje do triggerów
  istniejących workflow; nowy PR wymusiłby APK w apk-release.yml. Nie tworzono PR,
  nie zmieniano workflow i nie uruchamiano CI ręcznie. Bez merge i bez odpytywania Actions.

Zmienione pliki:
- `app/src/main/assets/extensions/nira-media-detector/content.js`
- `app/src/main/assets/extensions/nira-media-detector/background.js`
- `app/src/main/java/com/prirai/android/nira/media/MediaFloatingUi.kt`
- `app/src/main/java/com/prirai/android/nira/media/MediaRepository.kt`
- `app/src/test/java/com/prirai/android/nira/media/MediaFrameGroupCorrelationStage5Test.kt`
- `app/src/test/java/com/prirai/android/nira/media/MediaRepositoryTest.kt`
- `app/src/test/java/com/prirai/android/nira/media/MediaTileTitleEvidenceTest.kt`
- `app/src/test/java/com/prirai/android/nira/media/MediaTitleCorrelationV1171Test.kt`
- `app/src/test/js/individual-titles-v1175.test.cjs`
- `app/src/test/js/media-detector.test.cjs`
- `app/src/test/browser/mme-regression.cjs`
- `app/src/test/fixtures/mme-regression.html`
- `docs/AI_HANDOFF.md`

Dalsze sprawdzenie po osobnym upoważnieniu do dostarczenia APK: Nothing Phone (1),
Android 15 — wykrywanie przed Play i nazwy na zgłoszonych witrynach, odtwarzacze
SPA/iframe/Shadow DOM/MSE, zmiana filmu/jakości, miniaturki, formaty, profile,
prywatność i płynność 120 Hz. Nie deklarować naprawy konkretnej strony bez testu.
Wznowienie: pobrać gałąź, sprawdzić HEAD/status i ten checkpoint. Nie powtarzać
zielonych kontroli bez nowych zmian; nie otwierać PR uruchamiającego APK bez
odpowiedniego upoważnienia lub uzgodnionej zmiany CI.

## 2026-10-28 — Zavro Clean v1189 (podpisany APK z poprawkami MME)

- Baza: naprawa MME i testy na gałęzi `fix/zavro-clean-mme-detection-titles-20261028`, commit `f7f5e59c32057b81c5c4540fed27489d05dd6ba6` (kod `21abfc1f`), po v1188 / PR #110.
- Gałąź wydaniowa: `build/zavro-clean-v1189-mme-regression-fix-20261028`. Wyłącznie zmiany numeru i metadanych wersji: Gradle 189 / effective ARM64 1189, `0.5.22-alpha94-clean`, workflow artefakt `zavro-arm64-update-apk-v1189` oraz zależne asercje wersji w testach.
- Docelowy pakiet `com.prirai.android.nira.debug`, ABI `arm64-v8a`, certyfikat SHA256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`. Build powinien się przerwać, gdy brak klucza lub podpis jest inny.
- Zweryfikowane wcześniej dla kodu MME przez Codex: Kotlin 376/376, JS 218/218, Chromium 9/9. Nowy build i lint wymagają weryfikacji w oddzielnym CI dla v1189.
- PR ma pozostać Draft, bez merge do main i bez modyfikacji Zavro x MPV. Po uruchomieniu CI nie odpytywać w pętli; jeśli build trwa, zakończyć `CI_PENDING` i wznowić z faktycznego run ID. Zweryfikować przed dystrybucją podpis, metadane, ABI i artefakt. Test telefonu oraz rzeczywistych witryn pozostaje osobno `NOT_TESTED`.


---

## 2026-10-28 — ETAP 1 PASS: Zavro Clean, panele 1DM-like (v2.1)

**Zlecenie:** tylko analiza/projekt techniczny, potem STOP i oczekiwanie na „Dalej”. Nie kompilować APK/nie uruchamiać CI do osobnego „KOMPILUJ” lub „JUŻ”. Kolejne „Dalej” = dokładnie jeden etap/podetap. Nie merge, nie zmieniać Zavro x MPV.

**Baza zweryfikowana:** v1189 Clean, Draft PR #111, gałąź \`build/zavro-clean-v1189-mme-regression-fix-20261028\`, HEAD \`2919b1c946407a3c9eca4c00d8dc024b89187a45\`, PR base v1188 \`41f24ed4c04e783ffd2f1ca447f3aff043fc9dcd\`. Własna oddzielna gałąź: \`feat/zavro-clean-side-panels-1dm-20261028\`; dokładny checkpoint HEAD ustalić po commicie tego dokumentu. GitHub pozwala czytać/pisać; brak lokalnego checkoutu Android, Gradle/ADB i fizycznego telefonu. Nie można potwierdzić stanu obcego lokalnego working tree. \`AGENTS.md\` v1.5 przeczytany.

**Wyniki czytania źródeł v1189:**
- \`BrowserFragment.kt:185-255\`: \`setupEdgePanelGestures()\` nasłuchuje od krawędzi (pref \`niraEdgeWidthDp\` domyślnie 36 dp, landscape min. 80 dp), po przekroczeniu 12 dp i kierunku 1,5:1 zbiera drogę, lecz czeka na koniec gestu i próg 64 dp, a dopiero potem wywołuje \`show()\` jednego z dialogów; prędkość \`velocityX\` nie zmienia decyzji. \`ToolbarGestureHandler\` rejestrowany przed gestem panelu (\`BrowserFragment.kt:118\`), więc nie wolno odebrać mu obszaru paska.
- \`SwipeGestureLayout.kt\`: istnieje publiczny interfejs start/update/finish/cancel, wspólny \`GestureDetectorCompat\` i obserwacja pionowego scrolla strony bez intercepcji; \`onFling\` kończy gest przed \`ACTION_UP\`. Wymagana własność pojedynczego zakończenia, testy \`ACTION_CANCEL\`, wielodotyku i początkowego \`MotionEvent\` (obecny \`e1!!\`).
- \`TabsBottomSheetFragment.kt\` jest \`DialogFragment\` używany w obu trybach: normalny sheet oraz \`nira_side_panel\`; osobne \`width_portrait\`/\`width_landscape\`, min. 220 dp, zapas 48 dp; 20 dp grip po prawej reguluje szerokość przez \`dialog.window.setLayout\`. Są Compose listy, DnD, profile, wysokości wierszy, menu i wiele ścieżek \`dismiss()\`. Nie wolno zmieniać danych, wyglądu ani zachowania trybu standardowego.
- \`BookmarksBottomSheetFragment.kt\` dziedziczy \`BottomSheetDialogFragment\`; tryb boczny ma szerokość 78% ekranu, \`isDraggable=false\`, \`STATE_EXPANDED\` i osobne okno; normalny bottom sheet musi zachować działanie.
- \`fragment_browser.xml\`: korzeń \`SwipeGestureLayout : FrameLayout\`, w środku \`browserWindow\`, \`browserLayout\` i GeckoView; możliwe utworzenie pojedynczego hosta overlay bez ruszania silnika.
- \`BaseBrowserFragment.kt\`: na fullscreen ustawia \`gestureLayout.isSwipeEnabled=false\`; klasyczny przycisk licznika kart wciąż otwiera normalny dialog (nie zastępować go automatycznie). \`BrowserActivity.kt\` deleguje Back fragmentom, zaś \`BrowserFragment.onBackPressed()\` dzisiaj najpierw wchodzi w historię/fullscreen — po migracji otwarty panel musi obsłużyć Back przed historią.
- \`TestLabPanelProbe.kt\` dowodzi tylko \`FIRST_PREDRAW\` dialogowego okna, a nie zakończenia pozycyjnej animacji. Diagnostykę trzeba przenieść na faktyczne przejścia kontrolera.
- \`UserPreferences.kt\`: \`niraEdgePanelsEnabled\` i \`niraEdgeWidthDp\` są funkcjonalnymi ustawieniami do zachowania.
- \`.github/workflows/apk-release.yml\`: \`pull_request\` na dowolną gałąź uruchomi build podpisanego APK; \`push\` tylko na \`main\` i tagi v*. Osobny branch bez PR oraz commit dokumentacji nie uruchamia tego workflow. \`testlab-device.yml\` ma osobny ograniczony push filter i dispatch. PR/CI odroczyć do autoryzowanego etapu 9.

**Porównanie architektur:** pełny \`DrawerLayout\` zapewnia dojrzałe przesuwanie, scrim i obsługę velocity, ale ingeruje w hierarchię root/GeckoView, domyślne przejmowanie poziomych gestów może kolidować z 20 dp grip i DnD wewnątrz panelu, a istniejąca konfigurowalna strefa krawędzi wymagałaby dodatkowej adaptacji. Sterowanie \`Window\` obecnych dialogów pozostawia problem przełączenia okna w środku strumienia dotyku oraz zimnego pojawiania się dialogu. **Wybór:** niewielki wspólny \`SidePanelHost\` overlay wewnątrz istniejącego \`SwipeGestureLayout\` i jeden kontroler przesunięcia obu paneli (otwarcie od lewej/prawej, scrim, state machine \`CLOSED/DRAGGING/SETTLING/OPEN\`); po rozpoznaniu gestu aktualizować pozycję \`translationX\` bez przebudowy layoutu; po puszczeniu animować do 0 lub 1 z velocity, możliwością przerwania oraz jednokrotnym zakończeniem. Wygląd/insety/rozmiary i gest resize pozostają niezależne od fizyki. Zachować priorytet toolbar, klawiatury, strony, systemu i fullscreen. Brak wyłączeń gestów systemowych w etapie 1; ewentualne lokalne wyłączenia tylko po testach. Parametry 1DM (256/600 ms, 50%, ~400 dp/s, 20 dp, quintic ease-out) to historyczna analiza statyczna, nie pomiary ani wartości implementacyjne bez walidacji.
**Migracja zawartości:** AndroidX \`DialogFragment\` można osadzić jako zwykły fragment przez \`FragmentTransaction.add(containerId,...)\` zamiast \`show()\` (dokumentacja AndroidX DialogFragment, metoda \`setShowsDialog\`). Preferować ponowne użycie istniejących Fragmentów i ich layoutów w trybie bocznym, z kontrolowaną adaptacją odwołań do \`dialog.window\`, \`dismiss()\`, grip, insets i TestLab; standardowe dialogi pozostają bez zmian. Weryfikować technicznie i testować lifecycle. Jeśli osadzenie konkretnych Fragmentów okaże się zawodnym kontraktem, wydzielić UI do współdzielonego komponentu bez zmiany funkcji.

**Plan jednostek / sprawdzalne kryteria** (adaptacyjnie korygować po kodzie, bez mikroetapów):
- 2A HOST i model: kontener/scrim po obu stronach oraz cztery stany, jeden panel naraz; test inwariantów i brak ingerencji w GeckoView.
- 2B Motion: wspólny kontroler fractional displacement/velocity i animacji, anulowanie/przejęcie w trakcie settle; testy kierunków, granic, jednokrotnego finish.
- 2C Integracja infrastruktury: podpięcie do \`SwipeGestureLayout\`, lifecycle, hit testing, host Back/fullscreen i ochrona przed nakładającymi się panelami; testy bez zmiany zawartości paneli.
- 3A Lewy content: osadzony fragment z zachowaniem profili, list, wyglądu, szerokości pion/poziom i grip; testy inicjalizacji, orientacji i rozmiarów.
- 3B Lewy drag: otwieranie/partial/settle/reverse/close i scrim, brak dublowania fragmentów, testy gestów.
- 3C Lewy regresje: DnD, grupy, sortowanie, wyszukiwanie, wysokość wierszy, menu i profile; testy + brak regressu zwykłego sheet.
- 4A Prawy content: osadzona zawartość zakładek bez poziomego \`BottomSheetBehavior\`, normalny bottom sheet nietknięty; testy folderów/edit/sort/search.
- 4B Prawy drag: ta sama fizyka, kierunek od prawej, anulowania oraz zamknięcie i rotacja; testy symetrii.
- 5 Dopasowanie fizyki: sprawdzić progi, easing, velocity, czas zależny od drogi, 120 Hz; testy granic, bez kopiowania zasobów 1DM.
- 6A Konflikty strona/UI: GeckoView poziom/pion, toolbar, formularze, klawiatura, DnD, resize, overlay mediów/fullscreen; macierz regresji.
- 6B System/lifecycle: 3 przyciski vs gestowa nawigacja, Back, outside, multitou ch, ACTION_CANCEL, rotation, zmiana aktywności; testy końcowych stanów i usuwania callbacków.
- 7A TestLab: rzeczywisty start gestu, pierwszy ruch, settle finish/interruption/cancel; bez prywatnych danych i bez błędnego FIRST_PREDRAW.
- 7B Funkcjonalne: automatyczne testy stanów/gestów/orientacji w dostępnej instrumentacji; status urządzenia oddzielny.
- 7C Wydajność: 60 s scenariusz lewy/prawy, jank, frames, cold/warm, alokacje, bez mylenia 120 Hz z 120 FPS; realne pomiary tylko na wykonanym środowisku.
- 8 Audyt: końcowy diff, lifecycle, pamięć, regresje, błędy P0/P1, gotowość do podpisania; STOP jeśli fail.
- 9 Budowa: **wyłącznie** po „KOMPILUJ”/„JUŻ”, kolejny versionCode >1189, arm64-v8a, dokładny applicationId i SHA-256 podpisu, CI/artifact, opcjonalny Draft PR (bez merge).

**Weryfikacja ETAP 1:** PASS: odczyt remote HEAD/PR, instrukcje, kluczowe źródła, schemat UI, wyzwalacze CI, udokumentowana decyzja architektoniczna. NOT_TESTED: kompilacja, testy jednostkowe, emulator i telefon; kod aplikacji nie był zmieniany. Następna jednostka: **2A**, wyłącznie po poleceniu „Dalej”. Przy wznowieniu sprawdzić aktualny HEAD własnej gałęzi, nowsze zmiany w bazie, \`AGENTS.md\` i ten checkpoint; nie powtarzać pełnego audytu bez potrzeby.


### 2026-10-28 — ETAP 2A: inertny host paneli i model stanów

- **Baza przed 2A:** branch \`feat/zavro-clean-side-panels-1dm-20261028\` @ \`4a0a60eee1526be1b5ceb2430069db5a4e5c0f9e\` (v1189 Clean). Bez PR, merge, APK i CI. Nowy commit obejmuje tylko 4 pliki infrastruktury/testów oraz niniejszy checkpoint.
- **Zaimplementowano:** \`browser/panels/SidePanelStateMachine.kt\` z CLOSED/DRAGGING/SETTLING/OPEN, jednym właścicielem LEFT lub RIGHT, postępem 0..1, odrzucaniem NaN/Infinity, możliwością przejęcia SETTLING przez DRAGGING, ochroną przed dwukrotnym zakończeniem i resetem lifecycle; \`browser/panels/SidePanelHost.kt\` jako DOMYŚLNIE GONE i niepodłączany \`FrameLayout\` ze scrimem, dwoma kontenerami z identyfikatorami fragmentów, oddzielnymi szerokościami i prezentacją translationX/alpha tylko dla jednego panelu. Szerokość musi zostać ustawiona przed renderowaniem; host nie steruje fizyką ani nie zmienia dotyku GeckoView.
- **Testy repozytorium:** \`SidePanelStateMachineTest.kt\` (5 przypadków) i \`SidePanelHostTest.kt\` (3 przypadki Robolectric) dodane, **NOT_TESTED** w Gradle/Robolectric z powodu braku Android SDK/dependencies/lokalnego checkoutu. Model przetestowano osobno lokalnym \`kotlinc\` 1.9.0: kompilacja PASS, 25 własnych asercji stanów i przejść PASS; to nie jest wykonanie repozytoryjnego JUnit ani Android build. Host: przegląd źródła PASS, wykonanie NOT_TESTED.
- **Weryfikacja bezpieczeństwa:** brak edycji \`BrowserFragment.kt\`, \`SwipeGestureLayout.kt\`, \`fragment_browser.xml\`, istniejących paneli, preferencji, silnika GeckoView, fullscreen, 120 Hz, Gradle/workflows, numerów wersji i podpisu. Infrastruktura nie jest włączona do aktywnej ścieżki, więc obecne zachowanie UI pozostaje bez zmian. Testy telefonu NOT_TESTED.
- **Kolejna jednostka:** ETAP **2B** — wspólny kontroler ruchu, obliczenia postępu względem szerokości, velocity, settle, przerwanie i spójna obsługa anulowania; jego kod współpracuje z przygotowanym modelem i hostem, nadal bez włączenia do UI do 2C. Przed pracą sprawdzić branch HEAD, AGENTS.md i ten checkpoint. Rozpoczynać tylko po „Dalej”; kompilacja APK wyłącznie po „KOMPILUJ”/„JUŻ”.


### 2026-10-28 — ETAP 2B: kontroler ruchu paneli (oddzielny podetap)

- **Baza:** `8961408ece18d6e191ea02270a8a057cee01c6f2`, `feat/zavro-clean-side-panels-1dm-20261028` (v1189 Clean). Wdrożenie nadal niepodłączone do UI. Nie zmieniono APK, wersji, certyfikatu, GeckoView, gestów paska, ekranów paneli ani istniejących Fragmentów.
- **Kod:** `SidePanelMotionController.kt` i `ValueAnimatorSidePanelDriver.kt`. Wspólna obsługa LEFT/RIGHT, normalizacja *całkowitego* deltaX względem aktualnie podanej szerokości (bez skoków na początku drag), próg odległości 50%, fling 400 dp/s z uwzględnieniem density i znaku gestu, kwintyczny ease-out; nominalnie 256 ms pełnej drogi, ograniczenie 80..600 ms dla niezerowego settle. Parametry są wstępnymi wartościami z analizy 1DM i podlegają tuningowi ETAP 5, NIE są pomiarem urządzenia. Zmiana kierunku, przejęcie settle w połowie, programatyczne odwrócenie przy zachowaniu bieżącej pozycji, cancel → poprzedni cel, reset lifecycle i tokeny generacji chroniące przed opóźnionymi callbackami.
- **Testy:** `SidePanelMotionControllerTest.kt` 7 metod w Kotlin (oba kierunki, odległość, fling, anulowanie, reentrancy/interruption, brak dwóch stron, reset, easing, density). Lokalnie `kotlinc-jvm 1.9.0` PASS: 7/7 metod uruchomionych przez lekki uruchamiacz z zastępczymi asercjami kompatybilnymi z JUnit; **nie** jest to wykonanie oficjalnego JUnit w Gradle. Adapter ValueAnimator: syntaktyczny typecheck Kotlin PASS z minimalnymi stubami Android API, lecz realny framework Android, Robolectric/Gradle i animacja klatkowa pozostają **NOT_TESTED**. Nie wykonano APK ani CI. Testy Robolectric 2A pozostają NOT_TESTED.
- **Instrukcja wznowienia:** na `Dalej` rozpocząć WYŁĄCZNIE ETAP 2C: podłączenie hosta i motion kontrolera do istniejącej warstwy gestów/fragment lifecycle z bezpieczną bramką, bez migracji zawartości obu paneli (to dopiero 3A/4A). Najpierw odczytać nowy HEAD i checkpoint; przegląd zmienionego kontraktu i testów. Nie uruchamiać builda instalacyjnego/CI/PR bez polecenia `KOMPILUJ` lub `JUŻ`.


### 2026-10-28 — ETAP 2C: integracja infrastruktury (zachowany tryb legacy)

- **Baza:** \`feat/zavro-clean-side-panels-1dm-20261028\` @ \`2a6787d8013e632b2958ace16fc25943d70b86e0\`; dalej tylko Clean, bez PR, merge, APK lub CI.
- **Nowy adapter:** \`browser/panels/SidePanelInfrastructure.kt\` tworzy pojedynczy, DOMYŚLNIE \`GONE\` host na korzeniu \`SwipeGestureLayout\`, model i kontroler 2A/2B; rejestruje listener dopiero po toolbarowym i przed dotychczasowym edge listenerem. Żaden nowy gest nie zostanie przechwycony, dopóki \`activate(side,width)\` nie zostanie jawnie wywołane i nie ma w kontenerze widoku fragmentu. ETAP 2C nigdzie nie woła \`activate\` — oba panele nadal otwierają się starymi DialogFragmentami po progu 64 dp.
- **Cykl życia:** \`BrowserFragment.setupEdgePanelGestures\` podłącza właściciela view, \`onDestroyView\` go odpina wraz z listenerem i callbackiem scrim, \`onFullScreenModeChanged(true)\` resetuje animacje, \`onBackPressed\` najpierw pyta o aktywny nowy panel (na obecnym etapie zawsze zwraca false). \`SwipeGestureLayout.removeGestureListener\` usuwa listener i bezpiecznie anuluje aktywną sekwencję bez oddawania jej innemu listenerowi w trakcie dotyku. Callbacki i animacje nie powinny przetrwać zniszczenia view.
- **Bezpieczeństwo:** bez migracji tabów, zakładek, standardowych BottomSheet, DnD, XML i zachowania GeckoView, 120 Hz, preferencji, danych, numerów wersji i podpisu. Wyłączenia gestów systemowych nie są dodawane. W przyszłej migracji \`activate\` wywołać dopiero po gotowym kontenerze i testach; nie włączać automatycznie w ETAPIE 3A.
- **Testy:** dodano \`SidePanelInfrastructureTest.kt\` (4 scenariusze Robolectric: inertność, gate+edge, cleanup/reattach, fullscreen swipe-off); testy i Android Gradle **NOT_TESTED**, jeżeli niedostępne pełne zależności/SDK. Weryfikacja źródła i checkpointu GitHub osobno.
- **Następny podetap:** 3A — osadzić zawartość lewego panelu w host z zachowaniem całego UI/funkcji; bez automatycznego włączania gestów do czasu 3B. Po „Dalej” najpierw sprawdzić HEAD, instrukcje i ten checkpoint. \`KOMPILUJ\` / \`JUŻ\` dopiero etap 9.

- **Doprecyzowanie ETAPU 2C:** usuwanie poprzedniego legacy listenera przy ponownym `initializeUI` i `onDestroyView`, bez duplikacji handlerów krawędzi po ponownej inicjalizacji; gate sprawdza także `niraEdgePanelsEnabled`. Test dotyku `SidePanelInfrastructureTest` wykonuje pomiar/layout dziecka przed dispatch, żeby nie zakładać błędnie gotowego rozmiaru.


### 2026-10-28 — ETAP 3A: tryb osadzony zawartości lewego panelu (bez aktywnych gestów)

- Baza \`feat/zavro-clean-side-panels-1dm-20261028\` HEAD \`0e445200fd7f30fd5969dda2e61245478c1b63d3\` (Zavro Clean v1189). Tylko niniejsza gałąź, bez PR/main merge, CI, APK.
- \`TabsBottomSheetFragment\` zachowuje ten sam Compose UI, TabViewModel, grupy, profile, DnD i oryginalny dialog. Dodano \`newEmbeddedSidePanel()\` z \`setShowsDialog(false)\` (AndroidX DialogFragment jako zwykły child Fragment osadzony w kontenerze). \`dismiss()\` w trybie osadzonym prosi host o unmount, a standardowy dialog nadal woła \`super.dismiss()\`. Grip szerokości w trybie osadzonym zmienia width hosta; standardowy dialog nadal zmienia width okna; zapis dotychczasowych \`width_portrait\`/\`width_landscape\` bez zmian. Rotacja ustawia szerokość właściwej orientacji. TestLab nadal ma FIRST_PREDRAW, rzeczywisty settle -> etap 7A.
- \`SidePanelHost\` ma stabilne zasobowe ID kontenerów z \`res/values/ids.xml\`; \`SidePanelInfrastructure.mountLeftContent(childFragmentManager)\` potrafi zamontować oryginalny tab sheet jako embedded fragment, powiązać callbacki width/dismiss i jest idempotentny po ponownym wywołaniu. \`unmountLeftContent\` czyści bindingi i fragment, bez ruszania dotychczasowych dialogów. \`BrowserFragment.mountEmbeddedSideTabs()\` jest *tylko hakiem* do użycia w 3B; przy \`onDestroyView\` unmount przed zwolnieniem hosta. **Nie montujemy ukrytego fragmentu automatycznie w \`initializeUI\`**, bo wywołałoby to drugi ViewModel/Compose i skutki uboczne profilu równolegle do legacy; nie aktywujemy gestów do 3B. Implementacja ścieżki montażu gotowa, ale pełny render/zachowanie na telefonie NIEPOTWIERDZONE.
- Dodano \`EmbeddedSideTabsContractTest.kt\` 3 przypadki Robolectric: fabryki (embedded vs side dialog vs normal sheet), stabilność ID hosta, brak aktywacji bez zawartości. Testy Robolectric/Gradle i rzeczywista migracja contentu **NOT_TESTED**, brak Android SDK w bieżącym runnerze. Odczyty kodu, weryfikacja diffu/GitHub PASS. Nie zmieniono GeckoView, 120 Hz, nr wersji, signing ani workflow.
- Przed etapem **3B**: bezwzględnie skontrolować synchronizację \`DialogFragment\` child lifecycle / \`commitNow\`, render w hoście po odblokowaniu, callback zamknięcia z DnD i profili, przejęcie animacji, kompatybilność danych/orientacji. Etap 3B wyłącznie na kolejne \`Dalej\`. Budowa instalacyjnego APK wyłącznie na \`KOMPILUJ\`/\`JUŻ\`.

- **Poprawka bezpieczeństwa 3A:** `BrowserFragment.onDestroyView` nie uruchamia `commitNowAllowingStateLoss` podczas lifecycle dispatch. Nowe `releaseLeftContentBinding(childFragmentManager)` wyłącza side state i callbacki, a naturalne usunięcie widoku dziecka pozostawia FragmentManager; `unmountLeftContent` jest wyłącznie explicit-close poza teardown, przy `!isStateSaved`. W 3B zweryfikować przywracanie child Fragment po rotacji/lifecycle przed otwarciem panelu.


### 2026-10-28 — ETAP 3B: lewy panel podpięty pod gest i animację

- Baza: \`feat/zavro-clean-side-panels-1dm-20261028\` @ \`9150f5f309d9fdab3ac7cf81103d46740d030a40\` (Clean v1189), bez zmian Zavro x MPV.
- **Gesture LEFT:** \`SidePanelInfrastructure\` otrzymuje callback \`prepareLeftOnGesture\`; dopiero po zaakceptowanym poziomym ruchu od lewej krawędzi \`BrowserFragment.mountEmbeddedSideTabs()\` montuje istniejący \`TabsBottomSheetFragment.newEmbeddedSidePanel()\` i aktywuje jego host. Od razu wykorzystuje pierwsze przesunięcie i ignoruje duplikat pierwszego \`onSwipeUpdate\`, bez skoku. W OPEN/SETTLING można gestem w lewo wewnątrz panelu zacząć domykanie/przerwać settle. Velocity/distance obsługuje wspólny kontroler 2B.
- **CLOSE:** Back, scrim i \`dismiss()\` panelu zlecają animowane zamknięcie z aktualnego progress. Po domknięciu do CLOSED, \`post\` odmontowuje embedded child Fragment, zamiast utrzymywać drugi ViewModel. \`onDestroyView\` zeruje callbacki bez commitNow w trakcie lifecycle dispatch, fullscreen resetuje model. Programatyczny \`SidePanelMotionController.command(..., false)\` akceptuje teraz DRAGGING (bez skoku). Tokeny generacji nadal chronią przed spóźnionymi animacjami.
- **Legacy i prawa strona:** stare otwieranie LEFT po 64 dp nie działa równolegle z zamontowanym embedded; jeśli przygotowanie nie uda się, stary dialog ma fallback. Dotychczasowy RIGHT nadal otwiera stary bookmarks dialog. Unikamy nakładania po sprawdzeniu znanych tagów legacy, także zwykłego tab sheet. Aktywacja RIGHT z gestu nie jest uruchamiana.
- **Testy:** \`LeftSidePanelGestureTest.kt\` (4 Robolectric: lazy mounting, initial delta, reverse, collision, cancel/back/fullscreen) + test nowej funkcji \`command\` w \`SidePanelMotionControllerTest.kt\`; kontrola struktury zmian. Android SDK/Gradle i test telefonu NOT_TESTED; nie deklarować FPS/120 Hz ani wykonania Robolectric.
- **Następny etap:** tylko 3C na kolejne \`Dalej\`: audyt DnD/grup/menu/profili/szerokości/legacy, testy i naprawy regresji. Nie kompilować APK ani nie otwierać PR/CI bez \`KOMPILUJ\` lub \`JUŻ\`.


### 2026-10-28 — ETAP 3C: regresje interakcji lewego panelu
- Baza gałęzi `feat/zavro-clean-side-panels-1dm-20261028` / `04d32151b8e521a9a666c42e6a2711be6c1b0762` (Clean v1189). Bez PR/CI/APK.
- Audyt istniejącego `CompactSideTabs` potwierdził, że długie przytrzymanie i reorder kart/grup współdzielą dotyk z nowym listenerem przesuwania; grip ma osobną ścieżkę ACTION_DOWN/MOVE/UP/CANCEL; bez zabezpieczenia panel mógłby przejąć poziomy ruch podczas DnD/resize. Poprawka: `setEmbeddedChildInteraction` wymusza `requestDisallowInterceptTouchEvent` i sygnalizuje blokadę `SidePanelInfrastructure` podczas drag i resize; zwalnia ją przy finish/cancel/onDestroyView także poza TestLab.
- Zabezpieczono w `TabsBottomSheetFragment` tryb embedded na odtwarzanie stanu procesu: `nira_embedded_side` w argumentach i `setShowsDialog(false)` w `onCreate`; zwykły sheet i boczny legacy nadal mają dialog.
- Po ponownej lekturze zachowano istniejące implementacje `CompactSideTabs`, grup/hover/TabViewModel, przełączników profili, wyszukiwania i kontekst menu, wysokości wierszy oraz rozdzielne klucze szerokości `width_portrait`/`width_landscape`. Nie przenoszono samego kodu DnD do hosta; host nie zmienia struktury listy.
- Testy: `LeftSidePanelGestureTest.kt` +2 nowe metody (priorytet drag/resize i brak przejęcia gestu prawej krawędzi); `EmbeddedSideTabsContractTest.kt` waliduje odrębny znacznik embedded. Rzeczywisty Robolectric/Gradle, Compose, gesty multi-touch i profil ZIP NOT_TESTED (brak kompletnego SDK/checkoutu Android); statyczny przegląd kodu i weryfikacja commitu GitHub PASS.
- Pozostałe ryzyka: `DialogFragment` jako child, touch dispatch GestureDetector/Compose, zachowanie szerokości w rotacji, systemowy gest Back i płynność wymagają środowiska Android; zostaną objęte etapami 6A/6B/7. Nie przypisuj poprzednich zielonych wyników nowemu SHA.
- Następnie po jednym `Dalej` **ETAP 4A**: osadzenie zawartości prawego panelu zakładek bez zmiany działającego dialogu, bez aktywowania nowego gestu (to 4B). Nie robić merge/main, PR, CI ani APK do autoryzowanego etapu 9 (`KOMPILUJ`/`JUŻ`).


### 2026-10-28 — ETAP 4A: prawa zawartość zakładek osadzana na żądanie

- Baza: gałąź \`feat/zavro-clean-side-panels-1dm-20261028\`, commit \`26a6ae4682ea6d3c1b3c38eba893eaf7b03cd965\` (Zavro Clean v1189). Bez merge, PR, CI i APK.
- \`BookmarksBottomSheetFragment\` zachowuje RecyclerView, foldery, dodawanie/edycję/usuwanie, sortowanie, menu, multi-select, otwieranie odnośników i obecny BottomSheetDialog. Osobna fabryka \`newEmbeddedSidePanel()\` ustawia \`nira_embedded_bookmarks\` i \`setShowsDialog(false)\` (również w onCreate po odtwarzaniu procesu). Tylko embedded \`dismiss()\` może wywołać callback hosta; standardowy i side dialog nadal \`super.dismiss()\`. Przy odtwarzaniu fragmentu przywracane jest ID folderu z savedInstanceState; brak folderu -> root.
- Szerokość prawego embedded nadal odpowiada dotychczasowym 78% ekranu i reaguje na zmianę orientacji przez callback hosta; istniejące ustawienia lewego panelu \`width_portrait\`/\`width_landscape\` są nietknięte. Nie dodano prawego uchwytu resize ani innych funkcji wykraczających poza 4A.
- \`SidePanelInfrastructure.mountRightContent(childFragmentManager)\` montuje existing BookmarksBottomSheetFragment w stabilnym \`SidePanelHost.rightContainer\` z osobnym tagiem, bez duplikatów, dopiero po jawnej prośbie. \`unmountRightContent\` czyści fragment tylko gdy FragmentManager nie zapisuje stanu; callback \`dismiss\` odkłada transakcję poza aktualny touch-dispatch. \`releaseRightContentBinding\` i \`BrowserFragment.onDestroyView\` czyszczą referencje bez commitNow w dispatch lifecycle; \`dispose\` zwalnia managera.
- **Prawe gesty nadal wyłączone:** brak \`prepareRightOnGesture\`, \`activate(RIGHT)\` nie jest nigdzie wywołane; nowego fragmentu NIE montujemy automatycznie w BrowserFragment.initializeUI. Stary prawy bookmarks dialog pozostaje działający. Lewy panel, GeckoView, 120 Hz, versionCode, podpis, workflow niezmienione.
- Testy: trzy metody \`EmbeddedSideBookmarksContractTest.kt\` sprawdzają rozdzielenie fabryk, utrwalenie flag w argumentach oraz niewidoczny host. **NOT_TESTED**: wykonanie Gradle/Robolectric, rzeczywisty fragment/recycler, pliki folderów i telefon (brak lokalnego Android SDK/checkoutu). Przegląd implementacji i potwierdzenie commitu GitHub są kontrolą statyczną, nie testem Androida.
- Na kolejne \`Dalej\` tylko **ETAP 4B** — aktywacja prawego gestu, symetryczna fizyka, otwieranie/domykanie, konflikt z lewym, anulowanie, rotacja; aktywować dopiero po ukończeniu widoku i kontroli dialogów legacy. Nie robić PR/CI/builda instalacyjnego do autoryzowanego etapu 9 (\`KOMPILUJ\`/\`JUŻ\`).


### 2026-10-28 — ETAP 4B: prawy gest podłączony do współdzielonej animacji

- **Baza:** \`feat/zavro-clean-side-panels-1dm-20261028\` HEAD \`f92957eb46bbf2540d84d0f8b112366fe4ab8e44\`, Zavro Clean v1189. Bez PR, merge, APK, CI, zmian podpisu i Zavro x MPV.
- **RIGHT:** \`BrowserFragment.mountEmbeddedSideBookmarks()\` sprawdza aktywność i bieżące dialogi legacy, następnie wywołuje \`SidePanelInfrastructure.mountRightContent(childFragmentManager)\` i \`activate(RIGHT,width)\` tylko po zaakceptowanym ruchu od prawej krawędzi. Wszelkie foldery, lista, menu, multi-select i akcje zachowują dotychczasowy \`BookmarksBottomSheetFragment\`. Nie montuje się listy podczas startu przeglądarki.
- **Fizyka:** to sam \`SidePanelMotionController\` z ETAPU 2B; początkowy prawy ruch w lewo ma postęp dodatni, prawy panel tłumaczony jest w prawo, próg/velocity/fling/easing wspólne. W OPEN/SETTLING gest do prawej od wnętrza prawego panelu przejmuje bieżącą animację i może go zamknąć; Back i dotknięcie scrim również uruchamiają domknięcie. Po CLOSED następuje odroczone odmontowanie fragmentu, bez transakcji z onEnd/touch-dispatch. Zewnętrzny \`dismiss()\` bookmarks jest odroczony przez post, bez niszczenia itemów przy kliknięciu.
- **Wzajemne wykluczanie:** \`leftIsEngaged\`/\`rightIsEngaged\` obejmują także zamontowaną zawartość przed asynchronicznym unmount; walidacja w mount i activate nie pozwala obu stronom mieć dwóch widocznych paneli. Stary listener obu stron rezygnuje, gdy dowolny embedded host jest zaangażowany; przy nieudanym montowaniu zwykły dialog nadal może obsłużyć gest. Ukryte dialogi/przypadki lifecycle dalej wymagają testów.
- **Życie widoku:** \`releaseRightContentBinding\` i \`onDestroyView\` z ETAPU 4A, \`dispose\` zeruje callback right, \`resetForFullscreen\` resetuje obie strony. Zmiana orientacji przelicza szerokość prawej zawartości w \`onConfigurationChanged\`. Systemowe gesty nawigacji nie zostały wyłączone.
- **Testy:** nowy \`RightSidePanelGestureTest.kt\` (5 Robolectric: pierwszy delta, reverse/Back, lewy-prawy konflikt, fullscreen, cancel). Kod i strukturalne asercje repo GitHub PASS, **Gradle/Robolectric NOT_TESTED** (brak kompletnego środowiska Android). Ekran/120 Hz/realna płynność/funkcje folderów NOT_TESTED; wymagana weryfikacja w ETAPACH 6–7. Nie przypisywać wyników starych runów nowemu HEAD.
- **Następny pojedynczy etap po „Dalej”: 5**, dopracowanie progów, czasu animacji i easing oraz ich testy. NIE przechodzić do 6 bez kolejnego „Dalej”; APK/CI/PR dopiero po osobnym \`KOMPILUJ\` lub \`JUŻ\`.


### 2026-10-28 — ETAP 5: fizyka i wyhamowanie obu paneli (bez Android build/CI)

- Baza: \`feat/zavro-clean-side-panels-1dm-20261028\` @ \`1ec57d47c19a3ecefa23db678b90cfa6f1bfae99\`; nadal Clean v1189, bez PR/main merge/APK/CI i zmian Zavro x MPV.
- \`SidePanelMotionPolicy\`: bez zmiany istniejącego progu otwarcia 50%, progu flingu 400 dp/s, intuicji kierunku, gestu bezpośrednio za palcem i modelu LEFT/RIGHT. Długość końcowego settle jest teraz nominalnie **224 ms dla pełnej drogi**, **90–280 ms dla niezerowego fragmentu**, \`BASE*sqrt(distance)\`, skracana wraz z prędkością flingu (\`0.85 * distancePx / speedPxPerSec * 1000\`), nigdy wydłużana szybkim flingiem. Fragment drogi <=1 fizycznego px jest kończony natychmiast, bez ValueAnimator. Easing zmieniono z \`quintic ease-out\` na łagodniejszy \`cubic ease-out\` (1-(1-t)^3), wspólny na obie strony.
- \`ValueAnimatorSidePanelDriver\` nadal korzysta z domyślnego Android ValueAnimator, którego callbacki są wyzwalane przez VSYNC i animator duration scale. **Nie ustawiono na sztywno 60/90/120 Hz ani nie obiecano 120 kl./s**. Zachowano poprzednie tokeny cancel, możliwość przerwania, reset i velocity dla obu kierunków.
- Testy: \`SidePanelMotionControllerTest.kt\` rozszerzono z 8 do 13 testów: spójność krzywej 120 kroków, adaptacyjna długość, fling szybko/dwukierunkowo, NaN i zła geometria, domknięcie <=1 px bez animatora, plus dotychczasowe testy gestów/ownership. Odrębne testy Robolectric gesture nadal istnieją. **Weryfikacja rzeczywistego 120 Hz/jank i dopasowania do 1DM wymaga pomiaru na urządzeniu (etap 7C); parametry są propozycją projektową, nie zmierzonymi własnościami 1DM.**
- Po pojedynczym \`Dalej\` WYŁĄCZNIE **ETAP 6A**: konflikty przesuwania z GeckoView/paskami/Compose. Nadal bez APK/CI i bez nowych PR, dopóki użytkownik nie powie \`KOMPILUJ\` lub \`JUŻ\`.


### 2026-10-28 — ETAP 6A: arbitraż gestów WWW, toolbar i paneli
- Baza: feat/zavro-clean-side-panels-1dm-20261028 @ 7717fabd2ceff32ad81531170170db0168f5c359. Clean v1189. Bez PR/CI/APK/merge, bez zmian MPV.
- SwipeGestureLayout: naprawiono podwójne przekazywanie tego samego ACTION_MOVE po przechwyceniu ViewGroup przez porównanie znaczników czasu, współrzędnych i liczby wskaźników; bez alokacji obiektów per klatkę. Zachowano pierwszeństwo toolbarowego SwipeGestureListener oraz niezajęte gesty strony/Compose.
- Multi-touch: ACTION_POINTER_DOWN albo dwa pointery blokują przejęcie gestu panelu/toolbaru, a aktywny handler jest anulowany. Brak nowego odbierania pinch-zoom z GeckoView. requestDisallowInterceptTouchEvent child nadal jest honorowany.
- Przewijanie panelu nie uruchamia auto-hide toolbaru: allowPageScrollObservation kontroluje pasywne onVerticalSwipe; BrowserFragment włącza tę obserwację tylko gdy nie ma zamontowanego/aktywnego panelu. Callback czyszczony przy onDestroyView.
- Dodano 6 testów Robolectric SwipeGestureLayoutArbitrationTest.kt: pionowy scroll, poziomy poza krawędzią, child disallow intercept, pojedynczy MOVE na przejęcie, priorytet toolbaru, pionowy scroll panelu. Kompilacja Gradle/Robolectric i test GeckoView/Compose na telefonie nadal NOT_TESTED; weryfikacja strukturalna i commit nie potwierdzają 120 Hz.
- ETAP 6B po kolejnym DALEJ: systemowe gesty Back, fullscreen, lifecycle i insets. Nie uruchamiać instalacyjnego APK/CI/PR do osobnej komendy KOMPILUJ/JUŻ.
