# AI_HANDOFF — Zavro Clean MPV/Play V2, etap 4/6 (11.11.2026)

Kanon aktywnej gałęzi: `docs/ai/ACTIVE_CLEAN_LINE.md`. Archiwum: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`. To checkpoint przed końcowym CI, nie dowód sukcesu. Wyniki sprawdzaj na live GitHub.

```yaml
task_id: ZC-MPV-PLAY-CONTROLS-V2-STAGE4-20261111
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110
base_sha: 36b0a5db2aebbdeaaadc296ab013d97d3f1d7ca2
observed_head_sha: 36b0a5db2aebbdeaaadc296ab013d97d3f1d7ca2
status: CI_PENDING
acceptance_source: DALEJ po zweryfikowanym etapie 3/6; IMPLEMENT i VERIFY wyłącznie etapu 4/6, bez APK.
completed: Potwierdzony brak wyniku transportu dla MARK/PICK/STOP mimo komunikatów UI. Dodano jawny Delivery (SENT_UNCONFIRMED, WAITING_FOR_PAGE, NOT_DELIVERED), per-port controlId i walidowany control_ack łączący dokładnie nonce, dokument, ramkę, numer i rodzaj polecenia. Wersja wbudowanego diagnostycznego WebExtension/handshake 1.0.4. Brak ingerencji w play/pause stron. STOP żądany raz, kończy sesję po zweryfikowanym ACK lub maksymalnie 600ms; nie zawiesza nagrywania na nieobecnym JS, zapisuje sent i ACK osobno. STOP bez ACK nie jest sukcesem JS. ZIP raportuje TOP baseline i kategorie zdarzeń niezależnie od CAPTURING, a także sent/ack dla snapshot/pick/cancel_pick/stop. Menu rozróżnia oczekiwanie na START, migawkę TOP, zdarzenia oraz brak potwierdzenia. MARK zapisuje wyłącznie lokalny znacznik nawet przy braku portu; PICK nie zachęca do klikania, gdy komenda nie została wysłana; komunikaty nie potwierdzają odtwarzania Play. Dodano JS i JVM regresje potwierdzenia komend, złych/starych ID, odpowiedzi STOP, wielokrotnych żądań i bezpiecznych danych.
remaining: Zakończyć SOURCE i PR CI na końcowym SHA, naprawić wszystkie FAIL w tym etapie. Następny etap 5/6 dopiero po osobnym DALEJ i pełnym zielonym CI.
required_ci: SOURCE process-docs, shared-tests JS/Kotlin-Robolectric/AndroidTest compile/lint i lint-regression plus summary; PR shared-tests i error-learning evidence, na jednym końcowym źródłowym SHA.
apk: NOT_BUILT; bez podpisanego APK, bez pobierania binariów, zmiany versionCode/ABI/pakietu/certyfikatu.
device: NOT_TESTED; fizyczny Nothing Phone (1), Android 15 nie był testowany.
known_failures: Pierwszy kodowy SHA fb0c4ea87aaa054a5b2289da969679e21aa2fa27: SOURCE run 43000816707 i PR 43000823979 JS FAIL przez niepoprawne escapowanie slash w nowym teście prywatności (SyntaxError Invalid regular expression flags, joby 130456188469/130456210456), kontrole JVM/AndroidTest/lint skipped, bo JS FAIL. Test fixture naprawiono, zachowując oba sprawdzenia wycieku danych. Historycznie STOP kończył ledger zanim JavaScript mógł potwierdzić wykonanie; MARK i PICK nie miały odpowiedzi JS, a CAPTURING oznaczał dowolną migawkę również z SUBFRAME. Rzeczywisty problem z Play i miniaturkami na stronie pozostaje poza tym etapem.
verification: Etap 3/6 zakończono na 36b0a5db2aebbdeaaadc296ab013d97d3f1d7ca2: SOURCE 42994688345 SUCCESS, PR 42994695409 SUCCESS. Pierwszy SHA etapu 4 fb0c4ea87aaa054a5b2289da969679e21aa2fa27 miał SOURCE 43000816707 i PR 43000823979 FAIL (błąd test-fixture regex); nowy końcowy SHA po naprawie wymaga świeżych SOURCE i PR CI.
verified_before_push: Odczytano live HEAD i AGENTS/ACTIVE_CLEAN_LINE/AI_HANDOFF/STARTUP_PREFLIGHT/CHATGPT_PROJECT_INSTRUCTIONS oraz właściwe polityki z dokładnego 36b0a5db2aebbdeaaadc296ab013d97d3f1d7ca2. Przed update_ref ponownie zweryfikować HEAD, konflikt pracy i gałąź.
blockers: Testy Robolectric/JS nie zastępują realnego GeckoView, UI, orientacji i stron na urządzeniu. Brak odrębnej zewnętrznej recenzji o wysokim ryzyku; self-review nie jest niezależnym review.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/42994688345
  - https://github.com/laszko14/nira-browser/actions/runs/42994695409
  - https://github.com/laszko14/nira-browser/commit/36b0a5db2aebbdeaaadc296ab013d97d3f1d7ca2
  - https://github.com/laszko14/nira-browser/actions/runs/43000816707
  - https://github.com/laszko14/nira-browser/actions/runs/43000823979
  - https://github.com/laszko14/nira-browser/commit/fb0c4ea87aaa054a5b2289da969679e21aa2fa27
changed_paths:
  - app/src/main/assets/extensions/zavro-preview-diagnostics/manifest.json
  - app/src/main/assets/extensions/zavro-preview-diagnostics/content.js
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayDiagnostics.kt
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayPageBridgeController.kt
  - app/src/main/java/com/prirai/android/nira/BrowserFragment.kt
  - app/src/test/js/mpv-play-diagnostics-stage2.test.cjs
  - app/src/test/js/mpv-play-diagnostics-stage5.test.cjs
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayPageBridgeControllerTest.kt
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayDiagnosticsTest.kt
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
next_action: Domknąć etap 4/6 poprzez wymagane końcowe SOURCE i PR CI. Po sukcesie STOP i oczekiwanie na następne DALEJ. Bez APK.
```

## Ograniczenia dowodów

Potwierdzenie `control_ack` jest dowodem uruchomienia polecenia w skrypcie WebExtension, nie dowodem działania Play lub pełnego zeskanowania strony. `STOP` bez ACK oznacza wyłącznie zakończenie lokalnego zapisu. Wszystkie przesyłane pola kontrolne są typu enum/numer/losowy identyfikator, bez treści strony, URL filmów, HTML i poświadczeń.
