# AI_HANDOFF — Zavro Clean MPV/Play V2, etap 5/6 (11.11.2026)

Kanon aktywnej gałęzi: `docs/ai/ACTIVE_CLEAN_LINE.md`. Archiwum: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`. Historyczny checkpoint złożony przed CI nie jest dowodem bieżącego sukcesu. Sprawdź live HEAD i runs.

```yaml
task_id: ZC-MPV-PLAY-OBSERVABILITY-STAGE5-20261111
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110
base_sha: f4f7d9e9673d9cba5a1af9ac1b054ec9d9b61f96
observed_head_sha: f4f7d9e9673d9cba5a1af9ac1b054ec9d9b61f96
status: CI_PENDING
acceptance_source: Użytkownik wydał DALEJ po etapie 4/6, IMPLEMENT+VERIFY etapu 5/6, bez APK.
completed: Zdiagnozowano luki w obserwacji niekomponowanych zdarzeń video w otwartym Shadow DOM, dynamicznie powstających korzeni i kart miniatur oraz ryzyko wyparcia zdarzeń TOP przez iframe. Rozszerzenie diagnostyczne (nie playback) w wersji 1.0.5 obserwuje otwarte korzenie (maks. 5 obok dokumentu), nowo wstawiane elementy (budżet 48 skanowanych i 3 próbek na batch), touchstart, play/pause/playing/waiting/stalled/error, hover i tylko istnienie atrybutów deklarujących podgląd — bez odczytu źródeł filmów. Zdarzenia z shadow/document są deduplikowane; STOP i disconnect usuwają obserwatory. Baseline SUBFRAME ma limit 16 węzłów, cała ramka 160 zdarzeń JS. Native ledger rezerwuje 64 z 160 wpisów dla TOP poprzez limit 96 SUBFRAME razem oraz 24 na jeden poddokument; odrzucenie jest jawne. ZIP odróżnia ACK TOP/subframe od pełnej obserwacji, zamknięte Shadow DOM, about:blank, cross-origin frame bez udowodnionego ACK, ograniczenia skanowania i utratę zdarzeń. Analiza nie oznacza diagnostyki jako kompletnej przy utraconych zdarzeniach lub braku prawdziwych interakcji. Dodano testy syntetycznego Shadow DOM/dynamicznej miniatury i Kotlin/Robolectric ramkowej sprawiedliwości; poszerzono ochronę prywatności. Zbadano granice preview-assist.js, nira-bg-play, media detector i MpvPlaybackHeaders; nie zmieniono ich ani odtwarzania, nawigacji, gestów stron, zewnętrznego MPV Alpha.
remaining: SOURCE i PR CI na nowym końcowym SHA; przy FAIL analiza konkretnego joba/kroku/logu i naprawa w tym etapie. Etap 6/6 dopiero po osobnym DALEJ i wszystkich zielonych bramkach.
required_ci: SOURCE process-docs + JavaScript + Kotlin/Robolectric JVM + AndroidTest compile + lint/lint-regression + summary; PR osobne shared-tests + error learning evidence, na tym samym końcowym source SHA.
device: NOT_TESTED. Brak realnego testu na Nothing Phone (1) Android 15 ani konkretnych witrynach, nie potwierdzono przyczyny starego NO_ACK.
apk: NOT_BUILT, zgodnie z jawnym zakresem bez APK. Bez pobierania binariów, zmiany certyfikatu, pakietu, ABI, versionCode, merge czy release.
known_failures: Po pierwszym SHA etapu 5 54c33ec09253c512f060253eb8e65a3968b58b0b SOURCE run 43006478803 i PR run 43006487600 dały FAIL w JavaScript (job 130475109091 / 130475138135): trzy testy FAIL, 670 PASS. Dwie regresje Shadow wykryły pominięte pole shadow w 16-kluczowym priorytecie metryk VIDEO (produkcja), trzecia to stary test tekstowego formatu PICK/guard po legalnej rozbudowie metadanych (fixture). Zmieniono priorytet VIDEO zachowując 16 pól i ulepszono asercje prywatności w Stage4, zarejestrowano oba fingerprinty w ERROR_CASES.json; potrzebny nowy SHA i pełne CI. Obserwowanie document nie wystarcza dla niekomponowanych play/pause wewnątrz open Shadow DOM; przypisane ramkom zdarzenia mogą być niedostępne z powodu zakresu host_permissions, about:blank, closed Shadow DOM i opóźnionej injekcji. Kontrprzykłady są syntetyczne/źródłowe, nie realne przebiegi na telefonie.
verification: Etap 4/6 SHA f4f7d9e9673d9cba5a1af9ac1b054ec9d9b61f96 ma SOURCE https://github.com/laszko14/nira-browser/actions/runs/43002025546 i PR https://github.com/laszko14/nira-browser/actions/runs/43002035672 — oba completed SUCCESS. To potwierdza poprzedni SHA, nie etap 5/6. Pierwsze uruchomienia etapu 5 na 54c33ec09253c512f060253eb8e65a3968b58b0b: SOURCE 43006478803 i PR 43006487600 — JS FAIL, kolejne joby pominięte. Poprawki źródła i fixture wymagają nowego pełnego SHA i obu CI.
verified_before_push: Odczytano live HEAD f4f7d9e9673d9cba5a1af9ac1b054ec9d9b61f96 i z niego AGENTS.md, docs/ai/ACTIVE_CLEAN_LINE.md, docs/AI_HANDOFF.md, docs/ai/STARTUP_PREFLIGHT.md, docs/ai/CHATGPT_PROJECT_INSTRUCTIONS.md oraz polityki. Przed aktualizacją ref sprawdź ponownie HEAD i konflikty.
blockers: Realny GeckoView, permission boundaries cross-origin/closed roots oraz strony z DRM wymagają testu na urządzeniu. Brak niezależnego recenzenta wysokiego ryzyka — self-review i CI nie zastępują oddzielnego review. Nie wykonuj żadnych działań wpływających na odtwarzanie lub dane profili.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/43002025546
  - https://github.com/laszko14/nira-browser/actions/runs/43002035672
  - https://github.com/laszko14/nira-browser/commit/f4f7d9e9673d9cba5a1af9ac1b054ec9d9b61f96
  - https://github.com/laszko14/nira-browser/actions/runs/43006478803
  - https://github.com/laszko14/nira-browser/actions/runs/43006487600
  - https://github.com/laszko14/nira-browser/commit/54c33ec09253c512f060253eb8e65a3968b58b0b
changed_paths:
  - app/src/main/assets/extensions/zavro-preview-diagnostics/content.js
  - app/src/main/assets/extensions/zavro-preview-diagnostics/manifest.json
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayPageBridgeController.kt
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayDiagnostics.kt
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayEvidenceReport.kt
  - app/src/test/js/mpv-play-diagnostics-stage2.test.cjs
  - app/src/test/js/mpv-play-diagnostics-stage4.test.cjs
  - app/src/test/js/mpv-play-diagnostics-stage5.test.cjs
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayPageBridgeControllerTest.kt
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayDiagnosticsTest.kt
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayEvidenceReportTest.kt
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
next_action: Zamknąć etap 5/6 po pełnym zielonym SOURCE/PR CI na finalnym SHA, zarejestrować nowe incydenty lub weryfikację, następnie STOP. Etap 6 tylko na kolejne DALEJ.
```

## Ograniczenia dowodowe

`PLAYING` i typowane zmiany/hover są obserwacjami (nie dowodami, kto wywołał Play). Cross-origin iframe jest obserwowalny tylko, jeśli w nim rzeczywiście działa wstrzyknięte rozszerzenie i potwierdził port. Zamknięte Shadow DOM i handled `play()` rejection nie są dostępne bez ingerencji; nie obchodzimy tych granic. Diagnostyka nie przejmuje sterowania wideo, nie wykonuje `play()`, nie modyfikuje zewnętrznego MPV i nie zapisuje treści DOM, URL wideo, cookies, poświadczeń ani identyfikatorów obcych dodatków.
