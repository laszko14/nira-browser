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
