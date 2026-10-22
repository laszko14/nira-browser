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
