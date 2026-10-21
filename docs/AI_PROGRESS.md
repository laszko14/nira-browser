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
