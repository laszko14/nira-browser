## 2026-10-24 — ZAVRO v1115, PR #28 — podpisany ARM64 APK

- Naprawiono animację top-toolbar, która ukrywała dolny pasek Compose; odseparowano pomiary viewportu i przeliczanie po rozmiarze dolnego paska.
- Osadzono nową ikonę ZAVRO użytkownika i dopasowano adaptive launcher foreground.
- VersionCode 1115 / 0.5.22-alpha21, podpis i pakiet zachowane.
- CI run 40073101224: build/test/lint SUCCESS; walidacja signer/ABI/package/version SUCCESS; APK artefakt 12551941931.
- SHA256 APK: 5e2f5bd95bd2baae2eed23320350aab017ed6805a6af8c8a3be06784a41e9981.
- Test na fizycznym Nothing Phone (1) i sprawdzenie paska po scrollu/obrocie pozostają do wykonania.

## 2026-10-23 — v1109 podpisany ARM64 APK, PR #22

- Weryfikacja bazy v1108 `9a44af0af5415c22c686ba348bfcfa97e5bf59f0`. Wdrożono stabilne logical stream identity HLS/DASH z bounded signed revisions i provenance observed/derived.
- Network event preferowany nad pomocniczym fetch; parser manifestu nie awansuje DASH BaseURL na pseudo-MP4; HLS lineage grupuje warianty i dynamicznie podpisane URL, frame scope zachowany.
- UI wybiera bieżące źródło przy kliknięciu; Nira przesyła bezpieczny Referer przez istniejący kontrakt `headers` MPV Alpha. Bez zmian w repo MPV Alpha.
- CI run 39877337815: build/test/lint SUCCESS, 45/45 Node PASS; zweryfikowany APK ARM64, versionCode 1109, cert SHA256 df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00.
- Artefakt https://github.com/laszko14/nira-browser/actions/runs/39877337815/artifacts/12469470813 ; APK SHA256 bfe33809d2d4ccf18dc788a0ec3a4320b2437012d44a428bf1cf0f3b8eeccab9.
- Test realnego serwisu fulltaboo.tv i test urządzenia jeszcze niewykonane; wymagają potwierdzenia. P1/P2 z raportu pozostają otwarte.

## 2026-10-22 — v1105 / PR #18 — stabilizacja UI

- Przeanalizowano zgłoszenia: ukrywanie grup, zagęszczenie ikon/menu ⋮ w landscape, drag w lewym panelu, losowo niedostępny pull-to-refresh.
- Nowa gałąź `feat/nira-v1105-stability-toolbar-drag` od zatwierdzonej bazy v1104 (PR #17), brak merge.
- Wprowadzono persystencję ustawień paska, przełącznik chipów grup, drobne suwaki, drag akcji, kompaktowy side DnD z menu pod ⋮.
- `BaseBrowserFragment.fullScreenChanged` wyłączał gest po wyjściu z fullscreen; usunięto bezwarunkowe wyłączenie i poprawiono pozycjonowanie SwipeRefreshLayout.
- CI #75: FAIL na kompilatorze Kotlin (brak importu `background`); poprawka została dopisana. Wyniki najnowszego przebiegu są do weryfikacji przed publikacją.
- Plan QA: zielony build, JVM/JS, lint, podpis APK i testy ręczne na Android 15.

## 2026-10-22 — Nira v1104: rozpoczęte wdrożenie pięciu zmian

- Start z gałęzi v1103 (PR #16); stworzono gałąź `feat/nira-v1104-auto-bars-media-tabs` i draft PR #17, bez merge.
- Implementacja obu pasków auto-hide/reveal, pełnych tytułów i miniatur z dostępnych źródeł, zmian w bocznym menu kart i przenoszenia grup, uniwersalnego wykrywania wideo oraz SPA.
- Zwiększono versionCode 1103 → 1104 (ARM64) i versionName `0.5.22-alpha9` → `0.5.22-alpha10`; pozostawiono `com.prirai.android.nira.debug` i dotychczasowy certyfikat podpisu, fail-closed Secrets.
- Pierwsze CI 39844837800: kompilacja nie przeszła przez brak importów ikon menu, naprawione `6024bd6`.
- Drugie CI 39845560383: build podpisał APK prawidłowo, testy JVM przeszły; trzy regresje testów JS po dynamicznym DOM, poprawione w `0174536` przez ponowne uruchomienie głębokiego skanu po zmianie strony.
- Najnowszy CI i artefakt APK wymagają końcowej weryfikacji. Nie było fizycznego testu na telefonie.

## 2026-10-22 — Nira v1103: hybryda grup i szybki przełącznik

- Z bazy PR #15 utworzono osobną gałąź `feat/nira-hybrid-tab-groups-v1103`; nie scalano żadnego PR.
- Dodano górny pasek nazwanych kolorowych grup i liczników oraz rząd kart aktywnej grupy, z zachowaniem istniejącego systemu Room, gestów i menedżera kart.
- Podniesiono versionCode dla ARM64 do 1103, versionName do 0.5.22-alpha9, skonfigurowano ten sam certyfikat CI i testy z fail-closed.
- Status CI, APK i testów sprzętowych: do zweryfikowania przed publikacją.

## 2026-10-22 — Nira v1102: zebrane poprawki interfejsu i bezpośredni MPV Alpha

- Wprowadzono zmiany w `feat/nira-v1102-tab-panel-player-fixes` z bazy PR #14, PR #15 otwarty jako draft (bez merge).
- Pierwsza kompilacja zakończyła się błędem braku importu delegata `setValue` w Compose; poprawiono w commicie `74e37889ea98c1903d5f174674347c804f51806f`.
- Powtórna kompilacja: GitHub Actions 39830853219 — build signed PASS, unit tests PASS, JavaScript 30 PASS; skrypt `verify-update-apk.sh` potwierdził pakiet, wersję, certyfikat i pojedyncze arm64-v8a APK. Link: https://github.com/laszko14/nira-browser/actions/runs/39830853219/artifacts/12448218095.
- Lint i QA telefonu do weryfikacji. Zgodność z aktualną instalacją wyłącznie pod warunkiem tego samego certyfikatu. Brak testu fizycznego.

## 2026-10-22 — Etap 4.0.3: pływający licznik znika po ładowaniu

- Objaw: przycisk pokazuje liczbę filmów, ale po chwili znika.
- Przyczyna w kodzie: UI i MediaDetectorController obserwują BrowserStore niezależnie. W MediaRepository.currentEntries sprawdzanie loading == scope.loading zwracało pusty wynik po zmianie true → false, jeśli widok otrzymał aktualizację szybciej niż repozytorium. Reconcile nie wysyłało revision przy samym zakończeniu ładowania, więc guzik mógł pozostać ukryty.
- Fix: currentEntries toleruje opóźnione loading=true w repozytorium przy aktualnym loading=false w UI, wyłącznie przy identycznej sesji, karcie, profilu, trybie prywatnym, URL i zarejestrowanym dokumencie. Gdy rozpoczyna się nowy load, stare wpisy nadal są ukryte.
- Widoczny przycisk dodatkowo wywołuje bringToFront dla dynamicznego rodzeństwa WebView. Dodano test regresji na przejście true → false bez wcześniejszego observe i nowy reload.
- Baza: ci/stable-apk-signing 9d18d6e; branch fix/media-floating-disappears-after-load. Wersja 0.5.22-alpha5, versionCode ARM64 1099, podpis stały bez zmian. Nie wykonywano merge.
- CI i test na telefonie: wymagają potwierdzenia, nie zakładać sukcesu bez wyników.

## 2026-10-22 — Etap 4.0: Floating Video Detector + Media Panel

- Bazowy HEAD `30cdde1e7cf550106abe452693473e0f5c04eb37` potwierdzony w GitHub API; otwarto [PR #8](https://github.com/laszko14/nira-browser/pull/8) do gałęzi 3.3, bez #6 i bez merge.
- Natywny `MediaFloatingUi`: 56dp Android View w `browserLayout`, badge, gest przeciągania, pozycja w SharedPreferences bez danych filmów, ukrywanie we fullscreen, BottomSheet z identyfikacją formatu/DOM/Sieć, bezpiecznym opisem i świadomym kopiowaniem URL.
- Bez przebudowy 3.0–3.3: `MediaRepository.revision` jako event-driven StateFlow i `currentEntries` z kontrolą dokumentu; `MediaDetectorController.uiRepository` udostępnia ten sam RAM-only obiekt. Przed uruchomieniem lub kopiowaniem dodatkowa weryfikacja aktualnego wpisu i jego serialu.
- Ujednolicono wywołanie istniejącego Intent ACTION_VIEW do MPV Alpha w trybie ręcznym i z listy; zachowano chooser fallback. Nie dodano downloadera/DRM/parsowania manifestów.
- Wersja 0.5.22-alpha1, ARM64 versionCode 1095. Rozbudowano istniejący GitHub Actions o PR target 3.3 i pojedynczy job ARM64 Stage40 (bez emulatora) z warunkowym istniejącym certyfikatem CI lub oznaczonym tymczasowym podpisem.
- Dodano 9 testów JVM w `MediaFloatingStage40Test.kt`. Pierwszy [run 39737877731](https://github.com/laszko14/nira-browser/actions/runs/39737877731) wystartował; wynik obowiązkowo zweryfikować dla aktualnego HEAD. Bez testu na urządzeniu; zgodność podpisu z aktualną instalacją nie jest potwierdzona.

---

## 2026-10-22 — Etap 3.3: Media State Repository

- Zweryfikowano podstawę Etapu 3.2: `ab29e263386b180c373f69b2f7020bd14721f168`. Utworzono `feat/media-detector-stage-3-3-state` i PR #7 do gałęzi 3.2; PR #6 pozostał nietknięty.
- Dodano natywne, ograniczone pamięciowo repozytorium wyników `MediaRepository`, przypisanie do kontekstu BrowserStore i generacji dokumentu, bez zapisów URL/tokenów w logach lub pamięci trwałej.
- WebExtension łączy anonimowe `onBeforeRequest` z `onHeadersReceived` przez wygenerowane w rozszerzeniu klucze dokumentu; zdarzenia niepowiązane są odrzucane. Utrzymano filtr segmentów, istniejącą diagnostykę i obsługiwane formaty.
- Rozszerzono diagnostykę o liczniki stale/navigation; dodano 14 testów repozytorium i 4 JS ponad 13 wcześniejszych regresji JS. Cała wcześniejsza grupa testów Kotlin pozostała w projekcie.
- **Systematic Debugging:** pierwszy przebieg [39730501963](https://github.com/laszko14/nira-browser/actions/runs/39730501963) ujawnił 2 błędy przy SPA i reload (stary `documentKey`). Naprawiono unieważnienie `registered` i w następnym [39730681284](https://github.com/laszko14/nira-browser/actions/runs/39730681284) potwierdzono 36 Kotlin PASS + 17 JS PASS, lint SUCCESS, jedna kompilacja arm64 SUCCESS. Następnie przegląd kodu wykrył przypadek rebind tej samej sesji do innego profilu/prywatności, dodano ochronę high-water i nowy test; finalny run wymaga weryfikacji.
- Brak emulatorów i zdalnych testów, brak Etapu 4.0. APK z tego etapu nie jest wydany: certyfikat zainstalowanej wersji nieznany, podpis aktualizacyjny niepotwierdzony.

---

# Alpha Browser — postępy

## 2026-10-22 — Etap 1 (Europe/Warsaw)

- Sprawdzono `AGENTS.md`, historię repozytorium i istniejący workflow. Oryginalny udany run `39686116222` dostarczył zweryfikowany APK arm64.
- Poprawiono warunek joba `release` w commitcie `5b8c9c5`: ręczne uruchomienie na main bez tagu nie buduje signed release.
- Zapisano konfigurację środowiska. Po jej opublikowaniu przywrócił się dostęp do GitHuba; dokumentacja i poprawka wypchnięte na main.
- Run `39688896991` zakończył się success dla build/lint/test jobów; release skipped. Artefakt `debug-apks` id `12388435254`: https://github.com/laszko14/nira-browser/actions/runs/39688896991/artifacts/12388435254. Zadanie testów jednostkowych miało `NO-SOURCE`.
- APK arm64: package `com.prirai.android.nira.debug`, version `0.5.15`, minSdk 27, targetSdk 37. Podpis debug v2 zweryfikowany. Nie testowano na fizycznym telefonie.
- Lokalny build nie został potwierdzony po HTTP 502 Google Maven; poprawny build CI jest potwierdzony. Kod aplikacji i zależności nie były zmieniane.
- Dokumentację przekazania poprawiono; potwierdź końcowy zdalny HEAD przez `git ls-remote origin HEAD`.
- Następny etap w osobnej sesji: integracja ręcznego publicznego URL z MPV Alpha po sprawdzeniu jego package name i Intent filter. Bez implementacji wykrywania wideo w Etapie 1.

## 2026-10-22 — Etap 2 (Europe/Warsaw)

- Potwierdzono czysty stan bazowy `7eff7d91611e0184e8126a4abde13c94ec434aa2` i udane ostatnie CI Etapu 1.
- W REX-Player (commit `8c4f9ed2159c397317ceb1d58e8223f8cdf2b222`) ustalono pakiet `xyz.mpv.rex` i eksportowaną aktywność `xyz.mpv.rex.ui.player.PlayerActivity` obsługującą `ACTION_VIEW` / HTTPS / `video/*`.
- Commit `0d8f30012e9a0c0ebd57da908beb90bf543592e1`: menu ręcznego URL, walidacja HTTPS, bezpośrednie otwarcie w MPV Alpha, fallback chooser, wersja `0.5.16-alpha1` / arm64 code 1080. Commit wypchnięty na main.
- CI https://github.com/laszko14/nira-browser/actions/runs/39690196016: build/lint/test job success, release skipped. Artefakt `debug-apks` id `12388142872` zawiera zweryfikowany APK arm64: https://github.com/laszko14/nira-browser/actions/runs/39690196016/artifacts/12388142872. SHA-256 `11776236649598defc4a1ec37bea391ce634f93051af46f5178948a6d6e42d85`.
- `testDebugUnitTest` ma `NO-SOURCE`. Lokalny pełny build zakończył się success, ale uruchomiono go przed zmianą wersji, więc APK lokalny miał jeszcze 1079; ponowne `assembleDebug` zakończyło się sukcesem w 24 s, a APK arm64 ma według `aapt` versionCode 1080 i poprawny podpis według `apksigner`. Lokalny raport lint: 1626 błędów, 368 ostrzeżeń; `abortOnError=false`. Nie testowano na telefonie.
- Kolejny krok: test na Nothing Phone (1) z MPV Alpha i bez niego. Etap 3 (wykrywanie wideo) pozostaje osobno.

- Commit `1a23bbedf23f7c6a2cfd1c071f165d288535b318`: zamieniono `Uri.parse` na `toUri()`; lokalne `assembleDebug lintDebug` success (1m 42s), raport lint po zmianie: 1626 błędów, 367 ostrzeżeń; ostrzeżenie z nowej linii zniknęło.
- Końcowy run CI https://github.com/laszko14/nira-browser/actions/runs/39691305749: build/lint/test job success, release skipped. Artefakt https://github.com/laszko14/nira-browser/actions/runs/39691305749/artifacts/12388986024; pobrany APK arm64 zweryfikowano przez `aapt` i `apksigner`, SHA-256 `8e1a5ecaa4ac74b35db5327275f7c8a9b59485c36fc76ebe9c0d82cbd39018d4`. Test jednostkowy nadal NO-SOURCE.

## 2026-10-22 — Etap 3.0, checkpoint implementacji

- Środowisko Cloud uruchomiło się; czysta baza `b9fd4c5` zgodna ze zdalnym main. Nie powtórzono wcześniejszych etapów.
- Dodano built-in WebExtension, session-bound bridge Kotlin, walidator i natywne przypisanie karty/partycji; bez UI, parserów, pobierania i trwałego stanu.
- Przed implementacją testy JVM: 8 uruchomionych, 4 porażki; po implementacji 8/8 PASS w bezpośrednim harnessie Kotlin/JUnit. Testy JavaScript: 4/4 PASS.
- Pierwszy start Gradle bez skonfigurowanego GRADLE_USER_HOME próbował pobrać dystrybucję i zgłosił Connection refused. Wskazanie przygotowanych `/workspace/.gradle` i Android SDK pozwoliło rozpocząć konfigurację; nie zmieniono zależności projektu.
- Android build/lint/test, code review i CI nowej gałęzi w toku. Nie przeprowadzono testu telefonu. Szczegóły i ograniczenia w AI_HANDOFF.md.

### Etap 3.0 — wynik CI i APK

- Implementacja `55cba5c` wypchnięta; PR https://github.com/laszko14/nira-browser/pull/2. Niezależny code review: brak potwierdzonych problemów Critical/Important; pozostaje realny test Gecko na urządzeniu.
- CI https://github.com/laszko14/nira-browser/actions/runs/39703241830: build/lint/test SUCCESS, release SKIPPED; `debug-apks` id 12394132474 niewygasły. Artefakt istnieje, lecz pobranie archiwum w Cloud zwracało 403; brak twierdzenia o weryfikacji pobranego APK CI.
- Lokalny oficjalny testDebugUnitTest SUCCESS (6m47s): XML potwierdza 8 testów Kotlin, 0 failures/errors; 4 testy JS również PASS. Assemble debug ukończone; aapt potwierdził pakiet debug, arm64-v8a, code 1081 / 0.5.17-alpha1; podpis Android Debug poprawny według apksigner.
- Zbiorcze wymagane polecenie Gradle uruchomiono. Przy presji limitu 8 GB zatrzymano dopiero bezczynny daemon Kotlin po kompilacji i testach, zwalniając około 2 GB; bez zmiany konfiguracji projektu. Końcowy lokalny wynik i raport lint należy odczytać z opisu PR; CI lint jest potwierdzone, ale nie oznacza braku zastanych problemów.
- Bez merge i bez testu telefonu. Aktualizacje dokumentacji po commicie implementacji mają własne kontrole widoczne w PR.

- Końcowy lokalny zbiorczy przebieg `assembleDebug lintDebug testDebugUnitTest --max-workers=4 --console=plain`: BUILD SUCCESSFUL w 9m28s. Raport XML testów: 8 wykonanych, 0 failures/errors. Lint: 1626 Error/Fatal, 367 Warning, 12 Hint; liczniki błędów/ostrzeżeń zgodne z udokumentowaną bazą, brak zgłoszeń w nowych plikach. Zastany MediaSessionService.kt nie był zmieniany.

## 2026-10-22 — Etap 3.1 (checkpoint, weryfikacja w toku)

- Na bazie b6b46a4 (PR #2) utworzono feat/media-detector-stage-3-1 i [PR #3](https://github.com/laszko14/nira-browser/pull/3), docelowo main wyłącznie dla istniejącego CI.
- f110b95e: HTML5 MP4/WebM, SPA, DetectedMedia, numer wersji. 81d60cfc: testy JS i Kotlin. Bez panelu, repozytorium, HLS/DASH, klasyfikacji sieci i downloadera.
- Oczekuje potwierdzenia: assembleDebug, lintDebug, testDebugUnitTest, node --test app/src/test/js/*.test.cjs. Testy dopisano, ale nie ma jeszcze udanego przebiegu. Nie przypisywać wyniku Etapu 3.0.
- Nie testowano na urządzeniu; aktualizacja statusu po CI i niezależnym code review.

### Etap 3.1 — zweryfikowane CI i artefakt (2026-10-22)

- Poprawka 64300832: nie klasyfikuj manifestów .m3u8/.mpd jako wideo nawet przy mylącym DOM MIME (test regresji).
- CI początkowe https://github.com/laszko14/nira-browser/actions/runs/39706343598: build/lint/test SUCCESS, release SKIPPED; 8 Node PASS, HTML raport 14 JVM PASS, failures/skipped 0.
- CI po poprawce https://github.com/laszko14/nira-browser/actions/runs/39706925625: build/lint/test SUCCESS; release SKIPPED. Artefakt debug-apks https://github.com/laszko14/nira-browser/actions/runs/39706925625/artifacts/12395875886 istnieje według GitHub API. Nie przypisywać ZIP-owi sumy SHA niezweryfikowanego APK.
- Lint task SUCCESS z abortOnError=false; nie twierdzić, że jest czysto. Bez testu fizycznego, bez scalenia PR #2 i #3.

## 2026-10-22 — Etap 3.1D: test diagnostyki bez ADB i stabilne aktualizacje

- [PR #4](https://github.com/laszko14/nira-browser/pull/4) odgałęziony od PR #3, dodaje lokalną diagnostykę w menu, kopię raportu bez URL i izolację liczników per profil/prywatność.
- Kod/build: ograniczenie ABI tylko do arm64-v8a, app version 0.5.19-alpha1 / code 1089 arm64, podpis aktualizacyjny wyłącznie z istniejących secrets. Reguła zapisana w AGENTS.md.
- [CI 39709690259](https://github.com/laszko14/nira-browser/actions/runs/39709690259): unit test PASS, lint PASS, build FAILED ponieważ brak sekretu ANDROID_KEYSTORE_BASE64. Nie wyprodukowano potwierdzonego update-safe APK. Dane i podpis z już zainstalowanej wersji niezweryfikowane.
- Potrzebne jednorazowe dodanie 4 GitHub Secrets, opis w docs/ANDROID_SIGNING_SETUP.md. Nie wyciągać kluczy do PR, logów ani repo.

## 2026-10-22 — Etap 3.2, checkpoint gałęzi network

- Weryfikacja repo: #2/#3/#4 otwarte, niescalone; HEAD #4 `52973f9`. Utworzono gałąź `feat/media-detector-stage-3-2-network` na tym SHA, bez zmiany main.
- Stwierdzono, że probe webRequest ograniczał się do MDN flower.mp4; brak klasyfikacji MIME i bezpiecznego powiązania odpowiedzi z EngineSession. Dokumentacja GeckoView potwierdza, że background message nie posiada natywnej sesji.
- Dodano pasywne onHeadersReceived URL/MIME (bez blocking), detekcję manifestów HLS/DASH, filtr segmentów oraz fail-closed transport przez tabs.sendMessage → content → session-bound Kotlin. Anonimowe zdarzenia są raportowane bez URL. Deduplikacja TTL ograniczona do RAM, rozdzielenie trybu private i profilów w licznikach, nowe testy JS/JVM.
- Osobny arm64-only test job z efemerycznym podpisem bez Secrets, + wersja 0.5.20-alpha1 / code 1093. Jest to **wyłącznie test**, nie zgodne z gwarancją aktualizacji APK.
- CI i test na telefonie niepotwierdzone w momencie tego wpisu. Zweryfikować testDebugUnitTest, lintDebug, assembleDebug, node --test, podpis oraz artefakty po utworzeniu PR.

### Etap 3.2 — wynik końcowego CI kodu

## Finalne CI Etapu 3.2 (commit kodu `9c4c7b9`)

- [GitHub Actions 39721818021](https://github.com/laszko14/nira-browser/actions/runs/39721818021) **SUCCESS**: `temporary_arm64_stage32` (realne `assembleDebug` i `apksigner verify`), `test` (realne `testDebugUnitTest` + **13/13 Node PASS**), `lint` SUCCESS, release i podpisany build wymagający Secrets SKIPPED. `lintDebug` ma `abortOnError=false` i bazowe ostrzeżenia; nie nazywać go czystym.
- [APK arm64 TEST — ZIP](https://github.com/laszko14/nira-browser/actions/runs/39721818021/artifacts/12401945824): artefakt 145467029 B (archiwum, może zawierać README); zgodnie z jobem tylko jeden `app-arm64-v8a-debug.apk`, cert Android Debug v2 poprawny, fingerprint SHA-256 `c020e311221efd89b76eeab9597e255c9a77e42941f5089f8e0785b81d424aff`.
- [Osobny artefakt logu Gradle](https://github.com/laszko14/nira-browser/actions/runs/39721818021/artifacts/12401866005), [raporty testów](https://github.com/laszko14/nira-browser/actions/runs/39721818021/artifacts/12401446989), [raport lint](https://github.com/laszko14/nira-browser/actions/runs/39721818021/artifacts/12402085239).
- Cert z PR #4 testowego APK: `86a30a10f2ab90053939a547b429fabc31fd0153cb30dd07216b9764776ef8b6`. **Różni się od Stage 3.2**, co uniemożliwia traktowanie go jako aktualizacji tej instalacji. Faktyczny fingerprint aplikacji z telefonu nie został odczytany, więc nie twierdzić, że jest znany. Nie odinstalowywać, nie czyścić danych.
- W trybie private sieć przekazuje `isPrivate` wyłącznie z metadanych Gecko, a Kotlin wymaga zgodności z zaufanym `EngineSession`; przy nieznanym incognito pomija niepewną odpowiedź. Nie można jeszcze zapewnić, że GeckoView 156.0.1 na fizycznym urządzeniu udostępnia `responseHeaders`, `details.tabId` i `tabs.sendMessage` we wszystkich przypadkach. Potwierdzony wcześniej callback probe dotyczył tylko testowego pliku.
- Status: **kod + automatyczne testy + testowy APK w CI — wykonane; aktualizacja instalacji i test telefonu — niepotwierdzone / blokada przez certyfikat**. Jedynym kolejnym krokiem użytkownika jest bezpieczna weryfikacja podpisu na telefonie i, jeżeli zgodny, wykonanie scenariuszy diagnostyki. Nie rozpoczynać 3.3.


## 2026-10-22 — Stage 4.0.1: odkrywanie filmów przed Play

- Bazowy HEAD Etapu 4.0: 2f973dd. Przyczyna: content.js pomijał video.paused, background.js odrzucał inne frameId niż 0.
- Utworzono gałąź feat/media-detector-stage-4-0-1-early-discovery i zaimplementowano bounded DOM/JSON-LD/meta/static-player discovery, bez dodatkowego ruchu sieciowego.
- Dodano handshake podramki, korelację frame/root document i filtr dla webRequest z ramek; odrzucone niepewne przypisania.
- MediaSource.PAGE, HLS/DASH z jawnych adresów manifestów, UI odróżniający źródło ze strony od network; grupowanie alternatywnych URL z jednego odtwarzacza.
- Testy JS/JVM rozszerzone o pre-Play i iframe, wyniki CI do potwierdzenia po PR. Jedno APK ARM64, brak deklaracji update-safe bez porównania podpisu telefonu.
- Nie rozpoczęto parsowania jakości 4.1; bez merge.


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

## 2026-10-22 — stały podpis aktualizacyjny

- Audyt najnowszej gałęzi 4.0.2, wszystkich otwartych PR-ów i workflow potwierdził brak stałego klucza oraz różne certyfikaty efemerycznych APK.
- Wygenerowano poza repozytorium jeden klucz PKCS#12 (`nira-update`, RSA 4096, SHA256withRSA); publiczny cert SHA-256: `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`.
- Utworzono prywatną kopię odzyskiwania keystore + hasła. Żaden sekret nie został dodany do Git ani dokumentacji.
- Dodano test-first walidator APK: certyfikat, package, versionCode/versionName oraz wyłącznie arm64-v8a. Test najpierw zakończył się brakiem skryptu (RED), po implementacji przeszedł wraz z przypadkami złego certyfikatu i obcego ABI (GREEN).
- Podniesiono wersję do 0.5.22-alpha4 / arm64 1098 i zaostrzono signed build: fail-closed Secrets, publiczny pin certyfikatu, usuwanie tymczasowego keystore, jeden artefakt arm64. Po review usunięto wszystkie historyczne joby publikujące efemeryczne APK oraz objęto tym samym pinem i cleanupem tagowane wydanie.
- PR #11 (Draft, bez merge): https://github.com/laszko14/nira-browser/pull/11. Pierwszy pełny przebieg ze stałym kluczem: https://github.com/laszko14/nira-browser/actions/runs/39807671100 — build, test i lint SUCCESS. Artefakt APK: https://github.com/laszko14/nira-browser/actions/runs/39807671100/artifacts/12439380190. Walidator potwierdził `com.prirai.android.nira.debug`, versionCode `1098`, versionName `0.5.22-alpha4`, tylko `arm64-v8a` i certyfikat `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`.
- Pierwsza próba CI ujawniła zmianę formatu `apksigner` (`V2 Signer` zamiast `Signer #1`); poprawkę odtworzono testem regresji i zweryfikowano w rzeczywistym CI. Test aktualizacji na fizycznym telefonie nadal nie został wykonany.
# 2026-10-23 — v1107 Event-First Media Engine P0

- Zweryfikowano v1106 HEAD `3fcbd8c71817e66881dd4421fdb64bbd43a6e5ee`, utworzono gałąź `feat/nira-v1107-event-first-media-engine` i draft PR #20 do v1106; bez merge.
- Root cause: v1106 rozpoznawał URL manifestu, lecz nie analizował wariantów; płaski URL model rozdzielał jakości; brakowało limitowanej analizy body manifestu. Dodano parsery HLS/DASH o jawnie ograniczonym zakresie oraz logiczne grupowanie.
- Systematic Debugging: CI run 39862525764 wykrył błąd kompilacji `unaryPlus` w Kotlin; poprawka oparta na dokładnym logu. Review wykrył niebezpieczne credentialed fetch, niepełny limit body, globalny cache, rozdzielenie grup i XML entity/redirect base; wszystkie poprawiono testami. Drugi review wykrył redirect do segmentu bez MIME i niestabilny groupId; również poprawiono.
- Finalny kod aplikacji `60f9e3896390cb735e75caaa3b3bfb330a2844aa`: Node 41/41 PASS; CI https://github.com/laszko14/nira-browser/actions/runs/39863514031 build/test/lint SUCCESS; signed APK artefakt 12463321069. Fizyczny test telefonu i benchmark 1DM nie wykonane.

---



### 2026-10-23 — v1110: pasek systemowy, pusty pas, tytuły
- Utworzona gałąź na v1109 HLS (PR #22), bez edycji modułu detektora.
- Usunięto trwałe ukrywanie Android navigation bar oraz podwójne odsunięcie GeckoView od status bar / dynamic toolbar.
- Poszerzono wyszukiwanie tytułów miniatur o kontener nadrzędny dla osobnego sibling-caption, dodano test w Node.
- Przygotowano unikalny build v1110 alpha16 i kontrolę wersji/certyfikatu w GitHub Actions.
- Sprawdzenie na rzeczywistym Nothing Phone 1 pozostaje do wykonania przez użytkownika.

### 2026-10-23 — v1111: podpisane HLS, iframe i weryfikacja segmentu
- Przyczyna: Nira zachowywała `frameUrl`, ale do MPV przekazywała Referer strony nadrzędnej; anonimowy parser manifestu nie rozróżniał wskazówki, dostępnego manifestu i dostępnego segmentu.
- Dodano stan weryfikacji bez zapisywania tokenów, ograniczony probe pierwszego segmentu `Range: bytes=0-0`, iframe Referer, obsługę 401/403/404/410 oraz zachowanie pełnego oryginalnego URL.
- 49/49 testów JavaScript PASS. Testy Gradle i fizyczny fulltaboo/MPV pozostają do wykonania w CI/na telefonie.


### 2026-10-25 — v1127 Zavro x MPV, oddzielna instalacja
- Zweryfikowano libmpv z MPV Alpha (Maven 0.0.9), udostępnione BaseMPVView/MPVLib i gotowy podpisujący CI browser.
- Branch feat/zavro-x-mpv-v1127-standalone (PR #40 draft) na bazie v1126. Wprowadzono osobny identyfikator com.prirai.android.nira.mpv, natywny odtwarzacz, 1.5×, seek -10 s, +10 s, +30 s; otwieranie strumieni z listy i próba fullscreen.
- Pierwszy GitHub Actions 40142799308: poprawny podpisany build, sześć testów JS historycznie sprawdzało na sztywno v1126. Korekta sprawdzeń na nowy pakiet i wersję w tym commicie. Test na urządzeniu nadal niewykonany; nie mylić statycznego testu z działaniem mpv na stronie.

### 2026-10-25 — v1146
Poprawiono mapowanie visual viewportu Gecko na powierzchnię MPV, clipping i wycofanie geometrii usuniętego iframe. Node 135/135 PASS. Gradle offline zablokowany brakującą zależnością. Weryfikacja APK przez Actions; test urządzenia pozostaje po stronie użytkownika.

V1146: PR #59 draft, CI https://github.com/laszko14/nira-browser/actions/runs/40170281650 BUILD/TEST/LINT SUCCESS (dbd7c31). Artefakt zavro-x-mpv-arm64-v1146; kontrola podpisu/wersji/ABI PASS. Test telefonu nadal wymagany.

### 2026-10-25 — v1147
Dodano przekazywanie pionowego drag po Android touch slop do Gecko, zachowując kliknięcia MPV i blokadę fullscreen. Nagłówki raportów v1147. Node 138/138 PASS, klasyfikator JVM 3/3 PASS; finalne Gradle/CI i telefon opisuje handoff v1147.

V1147: draft PR https://github.com/laszko14/nira-browser/pull/60; CI https://github.com/laszko14/nira-browser/actions/runs/40172217252 BUILD/TEST/LINT SUCCESS, commit implementacji 3b771e5. Podpisany artefakt zavro-x-mpv-arm64-v1147; lokalnie Node 138/138, JUnit 83/83 i lint PASS. Test telefonu nadal wymagany.

### 2026-10-25 — v1148
Dodano fullscreen seekbar/HUD/prędkość/gesty na istniejącej powierzchni MPV, bez zmiany ręcznej Activity. Node 140/140, JVM 85/85 PASS; pełne lokalne Gradle/CI i ograniczenia opisuje handoff v1148.

### 2026-10-25 — v1149
Dodano izolowany adapter istniejącego observera i typowane komendy, snapshot preferencji oraz zachowanie 43 slotów. Bridge Lua wyłącznie jako nieaktywny szablon; brak nowych UI/filtrów/importu XML. Node 141/141 PASS; końcowe Gradle i GitHub opisuje handoff v1149.

### 2026-10-25 — v1150
Przeniesiono HUD i wyodrębnione oryginalne wizualne kontrolki Alpha do fullscreen ComposeView, zasilane AlphaSession. Mini i silnik bez przebudowy. Node 143/143, JVM 96/96 PASS; wynik lint/CI i ograniczenia wizualne opisuje handoff v1150.

### 2026-10-25 — v1151
Przeniesiono oryginalny panel Lua, style, edytory i 20 fontów Alpha v54; dodano prywatny zapis, backup i bezpieczne komendy przez AlphaSession. Node 146/146 i JUnit 102/102 PASS. Końcowe lokalne Gradle/CI, ograniczenia i telefon opisuje handoff v1151.

### 2026-10-25 — v1152
Dodano limitowany lokalny importer XML Alpha v54, podgląd bez mutacji, trzy zakresy, atomowy snapshot i osobny undo oraz mapowanie widocznych preferencji Lua/HUD. Syntetyczne Node 148/148, JUnit 115/115 i lint PASS; build/CI i instrukcję telefonu opisuje handoff v1152.

### 2026-10-25 — v1153
Wprowadzono stały bridge Lua Alpha v54 przed inicjalizacją dotychczasowego silnika, osobną zgodę i skorelowane odpowiedzi/timeout, aktualne click/long-press po ID i diagnostykę liczbową. Dotychczasowe odtwarzanie, wygląd, XML, mini scroll i fullscreen bez przebudowy. Node 150/150, JUnit 131/131 i prawdziwe systemowe Lua z syntetycznym MPV API 12/12 PASS. Instrumentacja rzeczywistego Android libmpv przygotowana, nieuruchomiona (brak urządzenia). Lokalne testDebugUnitTest/lintDebug/assembleDebug/compileDebugAndroidTestKotlin: BUILD SUCCESSFUL (5m 19s), code review i diff --check PASS. Końcowe CI opisuje handoff v1153. Nie rozpoczęto v1154.

### 2026-10-25 — v1154
Naprawiono podwójną serializację userdata przez native mapę/NODE, sprawdzanie nil/error z settera, odzyskiwanie późnego handshake, diagnostykę etapów i izolację stałego bridge od scripts/mpv.conf. Lua obecne w faktycznym ARM64 AAR 0.0.9 (Lua5.2, -Dlua=enabled, symbole runtime); binarne hashe i źródła odpowiadające wersji w handoffie. JUnit 136/136, JS 152/152 i systemowe Lua z syntetycznym mp API 17/17 PASS. Rzeczywisty Android test rozszerzony o tę klasę błędu, spóźniony observer i obcy skrypt/config; nieuruchomiony, brak urządzenia/emulatora. Końcowe testDebugUnitTest/lintDebug/assembleDebug/compileDebugAndroidTestKotlin BUILD SUCCESSFUL (7m31s), code review i diff --check PASS. Końcowe CI opisuje handoff v1154. Bez zmian odtwarzania i bez audio/Auto Audio.

### 2026-10-25 — v1155
Port oryginalnych 13 presetów EQ Alpha v54 i kontrolek Compose na istniejącej sesji. CommandNode + natywny af readback, celowane label/rollback, konflikt zamiast usuwania obcych filtrów, agregaty AUDIO / ALPHA. Brak modelu std.rnnn i definicji Głos Lua: pozostały jawnie niedostępne; bez afftdn/zgadywania tytułów. Bridge/import/sloty/CA/TLS/Gecko/mini bez zmian. Systemowe DSP 13 grafów i 17 Lua PASS; końcowe JUnit 149/149, JS 153/153, Gradle test/lint/assemble/compile Android test PASS (5m3s), code review i diff --check PASS; CI i ograniczenia urządzenia w handoff v1155. Auto Audio poza planem; bez nowego etapu.

### 2026-10-25 — v1156
Własne .lua jako osobne natywne klienty, jawne opt-in i snapshot na sesję, SAF/reimport/edycja/undo rewizji, prywatny import std.rnnn, bezpieczny podgląd mpv.conf i canonical customButtons XML. Oryginalne prywatne skrypty/model testowane wyłącznie poza Git. Dotychczasowy bridge NODE, Lua consent przycisków, Gecko/HLS/TLS, 43 sloty, mini scroll i fullscreen bez przebudowy. Systemowe Lua i host FFmpeg nie oznaczają Android DSP/GPU; końcowe wyniki oraz ograniczenia w handoff v1156. Bez subagentów i bez Auto Audio.

### 2026-10-25 — v1157
Ciche SENT/EXEC_OK i pojedyncze błędy, osobne WARN/top-level/callback; poprawiono config=no kontra resolver ~~ bez autoload i bez zmiany źródeł użytkownika. Natywne OSD z Roboto i skalą/marginesem HUD/mini, rzeczywisty odczyt renderer/file/GLSL/vo-passes/cleanup. Edytor ma jeden footer i scroll/IME. Lokalne JUnit179, JS157, Lua17+15+12, DSP13+31 PASS; pełny Gradle build/lint/AndroidTest compile PASS 8m50s. Urządzenia brak — GPU/OSD/dotyk nie zaliczone jako PASS. Handoff v1157 zawiera ograniczenia i instrukcję, bez nowego etapu audio.

### 2026-10-25 — v1158
Opcjonalne, odwracalne rewizje istniejących przycisków DRC/DE: kopie oryginałów, zachowane sloty/styl i zgody; DRC bez kumulacji, pięć rzeczywistych profili DE z kanonicznej Alpha v54. Native command + AF readback, ochrona obcych filtrów, celowany rollback i osobne skorelowane DSP receipts; brak uznawania samego EXEC_OK za DSP. Review poprawił znikający wpis po nieudanym reinit, dodatkowe obce parametry etykiety i deduplikację błędów ACK/timeout. JUnit194/JS159/Lua77/DSP28 + prywatne regresje PASS; Android test kompilowany, nieuruchomiony. Pełne wyniki i ograniczenia w handoff v1158. Bez merge, prywatnych plików w Git/APK i bez Auto Audio.

## 2026-10-25 — Zavro x MPV v1159 (Europe/Warsaw)

- Audyt z bazy 09f708ee981d255c4bf9d649f60b2137a1181416, bez merge i subagentów; PR36/37 sprawdzone.
- Naprawy transakcji grup, profili/prywatności, trwałości kolejności, drag/drop, zwinięcia i cyklu życia UI. Handoff: docs/ZAVRO_X_MPV_V1159_HANDOFF.md.
- Testy obejmują rzeczywisty Room in-memory, plik DataStore po ponownym otwarciu i kontrolowane wyścigi; gesty/urządzenie NIEURUCHOMIONE.
- Dokładny commit, Draft PR i wynik podpisującego CI w końcowym opisie PR. Nie pobierać APK do Codex; nie rozpoczynać następnej wersji.

- Końcowy Gradle po review PASS4m41s: test/lint/assemble/compileAndroidTest; JUnit230/230, JS162/162, Lua77/77, DSP28/28. Brak destrukcyjnej migracji i resetu preferencji. Review autora,92 chronione pliki bez zmian + tylko numer w diagnostyce MPV.

- Review szczegółowego lint: dwa nowe RestrictedApi w ręcznym ViewModelStore.put. Poprawione publicznym ViewModelProvider; końcowy Gradle PASS4m28s, JUnit230/230 i JS162/162,0 tych naruszeń. Pierwsze CI40233363285 anulowane, konieczny drugi push do tego samego Draft PR72. Bez zmian MPV/Lua/DSP.


## 2026-10-25 — v1160, diagnoza i implementacja
- Baza finalnego v1159 fdf3a98; bez ponownego audytu, gałąź fix/zavro-x-mpv-v1160-multigroup-fab-layout.
- FAB pomijał detached bottom tab strip i nie reagował na zmianę chrome bez resize rodzica; RED layout364vs454. Poprawiono rzeczywiste granice i preDraw/cleanup.
- Aktywne menu boczne nie miało tworzenia nowej grupy z karty. Dodano dialog i użyto istniejącej atomowej operacji createGroup bez wcześniejszego osobnego remove.
- Celowane5/5 GREEN: wiele grup, Room/DataStore close-reopen, profile, native strip i pełny hit target. Brak limitu jednej grupy w Room potwierdzają testy.
- Pełna końcowa walidacja w toku, wyniki w handoff v1160. Android/MPV na telefonie NIEURUCHOMIONE.

- Końcowy review/Gradle PASS2m55s: JUnit235/235, JS162/162, Lua77/77, DSP28/28; assemble i compileAndroidTest PASS. Pierwszy pełny4m31s ujawnił1nowy CheckResult; naprawiony przed pierwszym push. Lint final:0nowych, dokładnie stan v1159 (1462Fatal/258Error/444Warning/19Hint). Kontrola92plików i etykiet MPV PASS. CI/certyfikat/artefakt potwierdzić po push w opisie PR.

## 2026-10-25 — v1161, diagnoza i naprawa otwierania kart
- Baza finalnego v1160 3075ba7, gałąź fix/zavro-x-mpv-v1161-tab-opening-group-audit; bez powtórnego pełnego audytu.
- RED: parentId dopisuje10kart do grupy; opóźniony URL tworzy grupę; źródło default/A przypisywane do aktywnegoB.
- Zwykłe BrowserActions nie grupują. Oddzielny jawny intent z menu „w grupie” zachowany; parentId zostaje nawigacją.
- Naprawiono dziedziczenie partycji dla Gecko WindowFeature, jawny default i flagę private osobnego menu; ręczne członkostwo dziecka ma pierwszeństwo nad opóźnionym intentem.
- Nowe celowane testy efektów GREEN, JS163/163; pełna walidacja, lint delta i podpisujące CI są kolejnym krokiem. Szczegóły wznowienia: CODEX_RESUME_V1161.md.

- Końcowe lokalne test/lint/assemble/compileAndroidTest BUILD SUCCESSFUL4m35s. JUnit244/244, JS163/163, Lua77/77, DSP28/28; failure/error/skip0. Lint dokładnie stan v1160,0nowych/0usuniętych (1462Fatal/258Error/444Warning/19Hint). Review autora i kontrola integralności92+10plików PASS. Następnie jeden push/Draft PR do v1160 i podpisujące CI; metadane końcowe w opisie PR.

## 2026-10-26 — v1162 audyt aplikacji
- GitHub potwierdza v1161 a3fa123 / PR74 / CI40241236669 SUCCESS jako najnowszą sprawdzoną wersję. Nowa gałąź fix/zavro-x-mpv-v1162-app-wide-reliability-audit.
- Katalog309plików,14aktywności,4usług,76kluczy XML i dynamicznych ustawień. Audyt granic startup/lifecycle/UI/gesty/download/cookies/PWA/profil/JS/media/JNI.
- Potwierdzone RED: obcy profil z LRU, CANCEL→finish, OAuth log, błędny Content-Range i oversized part, przedwczesny cookie ACK, zbędny engine-init, unsupported PWA scheme.
- Wdrożono minimalne poprawki i zabezpieczenia własności download jobów, opóźnionych animacji oraz CAS policy. Celowane efekty GREEN; pełna walidacja kolejna. Wznowienie CODEX_RESUME_V1162.md; inwentarz ZAVRO_X_MPV_V1162_INVENTORY.md.

- Pełny Gradle PASS4m11s, JUnit261/261, JS164/164, Lua77/77, DSP28/28. Jeden nowy UseKtx poprawiony równoważnym toUri; post-review PWA1/1+lint/assemble/compileAndroidTest PASS3m53s. Lint0nowych/0usuniętych (1462Fatal/258Error/444Warning/19Hint). Review autora i integralność92+10 PASS.12naprawionych usterek; brak urządzenia, bez twierdzeń o FPS/RAM. CI/artefakt i końcowy SHA uzupełnić w opisie PR po push.

## 2026-10-26 — v1163, diagnoza detektora
- Baza b6908c9 / PR75 / CI40277823346 SUCCESS; gałąź feat/zavro-x-mpv-v1163-max-video-discovery.
- Kontrolowane Chromium DOM fixtures i rzeczywiste listenery background: RED pominięte lazy/source/wrappers/ogon listy/shadow +4błędy frame/redirect/status/lifecycle.
- Minimalne rozszerzenia istniejących skanów i walidacji, limity HTTP512KiB/4s/2active/8queue zachowane, brak nowych uprawnień i MPV zmian.
- Celowane JS56/56 GREEN; pierwszy pomiar DOM po zmianach w toku; dokładny następny krok CODEX_RESUME_V1163.md.

- v1163 checkpoint2: JS176/176, mediaKotlin75/75, Lua77/77,DSP28/28 GREEN. Końcowy Gradle/lint w toku.
- Finalna macierz89filmów:61→88 przedPlay,62→89 poPlay; FP3→0,duplikaty wariantów1→0,bez błędnychscaleń; HTTP7→7. Warstwafixtures, nie Android.
- Dodano bezpieczne VIDEO DISCOVERY do istniejącejdiagnostyki/Supertestu; byte-identical MPV poza tąsekcją/etykietąwersji. Checkpoint CODEX_RESUME_V1163.md.

- v1163 final przedCI: JUnit265/265,JS179/179,Lua77/77,DSP28/28. PełnyGradle PASS4m42s,reviewdiagnostics7testów+assemble PASS33s,ostatniJS-onlyMIMEfix179/179.
- Lint0nowych/0usuniętych1462Fatal/258Error/444Warning/19Hint. Końcoweidentycznefixtures89filmów88preeligible:61→88pre/62→89post,FP3→0,duplikaty1→0,HTTP7→7; kosztiwiadomościwJSON,bezdeklaracjiprzyspieszenia.
- Przygotowano finalhandoff/checkpoint3;następne1commit/push+DraftPRwzględemv1162i1podpisująceCI.FaktyczneSHA/CI/APKdoaneksuwopisiePR,bezkolejnejwersji.

## 2026-10-26 — v1164 etap A
- Baza1c5ae61/v1163, gałąźfeat/zavro-x-mpv-v1164-gecko-advanced-discovery. StareA89/89,88/88pre,JS179/179,Kotlin75/75 odtworzone.
- Zamrożone noweB/CzSHA przed zmianądetektora;Bnetwork10PASS/11FAIL,DOM_B wynikiwJSON,Cnieruszane.
- Potwierdzono sourceGecko156.0.20260921121718:UUIDruntime/webRequest,parentDocumentId,webNavigation; oficjalna dokumentacja odczytana.
- TestowyAPKx86/testyGeckocompilePASS1m38s;6JNIzależności obsługujeABI. LokalnyemulatorAPI35bezKVMstarted leczbootniepotwierdzony; faktyczna instrumentacjaNIEURUCHOMIONA.
- DodanyosobnyworkflowGeckoKVMbezpublikacjiAPK/continue-on-error. WIP [gecko-baseline] uruchamia tylko instrumentację; checkpointCODEX_RESUME_V1164.md.

- Gecko baselineCI40301729041: emulatorKVMAPI35 uruchomiony37,7s, faktycznie rozpoczęto instrumentację; crash1/13 przed detektorem zClassNotFound testhostActivity. To błąd konfiguracji harnessu(Java sourceSet nie skompilował dodanego.kt), nie dowód regresji silnika. Host przeniesiony doJava, bez zmianApp/MPV. PonownybaselineCI po tej konkretnej poprawce, nie identyczny rerun.
- LokalnyTCG emulator jednakboot_completed=1po>17min; instalacja pierwszego x86APK trwa. Nie kwalifikować infrastruktury jako niedostępnej na podstawie wcześniejszego wolnego startu.

- Gecko baseline2CI40302900024 po naprawieJavahost: rzeczywiście13wykonanych,10PASS/3RED(srcdoc,aboutblank,SPA pushState); bezwyłączania testów. MP4/HLS/early/basic/cross/nested/profiles/private/reopen działają na bazie.
- B/C/E/G implementacja: early128/2.5sUUIDbuffer+Native-ready, trusted getFrame ancestry/opaque, zdarzenioweSPA, NativeIPC4active/128queued/64per5s/retry1, metadata/dashliteral.
- Native frameTracking opt-in w v1 +media_frame lifecycle, odrzucanie retiredFrame/wrongURL/profile, rootHLSbinding iambiguoussameURLframes; bridgeACK/player bez zmian. DesignV1164_FRAME_IDENTITY_DESIGN.md.
- LokalneB21/21,DOM_B5/5,A89/89(88/88pre),Kotlin82/82,JS57/57celowane. CnadalNIEURUCHOMIONE. Source version1.7.0+webNavigation uzasadnioneimplementacjąGecko, bezMAIN/originfallback/cookies.
- Lokalnyemulatorboot957s,potemhangingQEMUCPU iutrataADBprzyinstalacji:braklokalnejinstrumentacji. CIzKVMdowiódłdostępnościGecko, kontynuowaćtam,nieudawaćwynikufizycznegotelefonu.

- v1164 checkpointD/H: realGecko40305856842=11/13PASS,srcdocGREEN; sender.url zamrożony przy utworzeniu context i brak deklaratywnegoinitialblank potwierdzone sourceGecko. Zaimplementowano trustedgetFrameSPA oraz isolatedblankfallback zUUIDancestry, bez MAINworld.
- BlobAUTO otrzymuje dokładnydoc/frameKey poNativeACK, bezzmianplaybackACK/rollback. JS67/67,AndroidTest18metodcompilePASS;Kotlin82/82 poidentity. NowyWIPuruchamia18realtestów. IndependentC/finalwalidacja/APKjeszczeniegotowe.

- v1164 checkpointE: Cnetwork12/12,B21/21;C DOM5/5URLbezpoprawyrecall(qualityvariantsnieosobnefilmy). JS195/195,JUnitraport272/272,Lua77/77,DSP28/28. FinalGradlelintjeszczewtoku.
- CI40313078091 nieukończone18testów:Brokenpipewserwerzetestowymprzycelowymcancel,procesAndroidcrashed. NaprawionowyłącznieobsługętransportowegoIOException;testzachowany. KolejnyGeckoWIPwymagany; brakgotowegoAPK.

- v1164 realGecko40314069715:16/18PASS,aboutblank/srcdoc/lifecycle/profileiprivatepositiveGREEN;pozostałypush/replace. Earlyambiguitybezstale/protocolerror,wymaganedalszerozbiciesanitizedcounters.ZawężonyWIP[gecko-spa]niezastępujefinal18.
- Reviewregistry257ramek:evictionpo mintedkeyRED/GREEN. JUnit273full,3nativePorttestPASS,11targeted+AndroidTestPASS34s,JS195/195. FullGradlepoevictionPASS4m35s. APK/PRnadalniegotowe.

- v1164 sourceChannelWrapperpotwierdzaznaczeniedocumentUrl=loadingprincipalURI. Nowe3RED/GREENoddzielająURIprincipalodUUIDwSPA/opaque;starepochodrzucany,parent/private/partitionzachowane. JS199/199,B21/21,C12/12,AndroidTest20metodcompilePASS4s. Nowyreal20WIPwymagany,dotychczas16/18PASSi2diagnosticSPAwtoku.
