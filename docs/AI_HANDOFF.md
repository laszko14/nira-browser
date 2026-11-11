# AI_HANDOFF — Zavro Clean MPV/Play V2, etap 6/6 (11.11.2026)

Aktywna gałąź: `docs/ai/ACTIVE_CLEAN_LINE.md`. Historyczny handoff: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`. Ten checkpoint powstał PRZED końcowym CI — odczytuj zawsze live GitHub; `CI_PENDING` nie jest sukcesem.

```yaml
task_id: ZC-MPV-PLAY-V2-STAGE6-INTEGRATION-20261111
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean, bez x MPV
branch: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110
base_sha: ad41702077ed7f155781c9ff66d443bf61c772ff
observed_head_sha: ad41702077ed7f155781c9ff66d443bf61c772ff
status: CI_PENDING
acceptance_source: DALEJ po zakończonym etapie 5/6. Użytkownik następnie nakazał STOP; w tej turze wyraźnie polecił sprawdzić ostatni stan i KONTYNUOWAĆ. Autoryzacja: tylko etap 6/6 IMPLEMENT + VERIFY, bez APK.
completed: Preflight live HEAD i niezbędne pliki kanoniczne odczytane z ad41702077ed7f155781c9ff66d443bf61c772ff; SOURCE 43006921141 i PR 43006930215 completed SUCCESS na tym SHA (końcowy etap 5/6). Przerwane działania etapu 6 nie miały commita, push ani CI. Kontrola integracyjna ujawniła, że content.js używał tylko jednego stoppedId — po STOP(A), START(B), STOP(B) spóźniony START(A) mógł odtworzyć obserwację. Zamieniono to na ograniczony pamięcią zbiór 128 zakończonych nonce; podniesiono manifest/content/native handshake do 1.0.6. Dodano regresje dwóch STOP/START i pełny syntetyczny przepływ TOP+SUBFRAME+Shadow+obserwacje+PICK/MARK ACK+STOP+ponowny START, sprawdzające granice prywatności i brak wywołań play/pause przez diagnostykę. Dodano JVM test spójności 9 plików ZIP (README semantyki transportu i różnica między TOP baseline, zdarzeniami, sukcesem Play). Przygotowano procedurę testu telefonu docs/ai/MPV_PLAY_V2_STAGE6_DEVICE_TEST.md, z 12 przypadkami, testami profili/orientacji/miniaturs i warunkami DEVICE_VERIFIED. Zarejestrowano fingerprint regresji w docs/ai/ERROR_CASES.json. Nie zmieniano istniejących modułów odtwarzania, GeckoView, profili ani danych.
remaining: Końcowy krytyczny przegląd diffu, testy JS i Kotlin/Robolectric, AndroidTest compile, lint oraz wymagane osobne SOURCE/PR CI na jednym nowym końcowym źródłowym SHA. Przy FAIL pobierz konkretny job/krok/log i usuń przyczynę bez obniżania testów; nie deklaruj CODE_VERIFIED przy CI_PENDING. Nie buduj APK ani nie uruchamiaj następnego etapu automatycznie.
required_ci: SOURCE process-docs + shared-tests (JavaScript, JVM/Robolectric, AndroidTest compile, Android lint i lint-regression) + final summary, oraz PR shared-tests + error-learning evidence; komplet SUCCESS na dokładnym końcowym source SHA.
device: NOT_TESTED — Nothing Phone (1), Android 15; brak wykonanych przypadków D01-D12. Docelowy protokół: docs/ai/MPV_PLAY_V2_STAGE6_DEVICE_TEST.md. Realny wynik DEVICE_VERIFIED wymaga późniejszej zgody i testu użytkownika.
apk: NOT_BUILT_IN_THIS_TASK. Nie budowano ani nie pobierano APK/ZIP binariów. Nie zmieniono versionCode, versionName, podpisu, ABI ani pakietu; brak merge/release/force-push.
known_failures: Stary START dla pierwszej zakończonej sesji mógł wznowić obserwację po STOP kolejnej sesji. Retencja 128 nonce ogranicza pamięć; po ich przekroczeniu pojedynczy ekstremalnie stary START nie jest gwarantowanie rozpoznawany. Zgłoszone historyczne NO_ACK na telefonie nadal nie ma zweryfikowanej przyczyny. Closed Shadow DOM, niewstrzyknięte/cross-origin iframe, handled play Promise rejection i DRM pozostają poza granicami obserwacji.
verification: Ostatni zakończony etap 5/6 jest potwierdzony przez SOURCE https://github.com/laszko14/nira-browser/actions/runs/43006921141 i PR https://github.com/laszko14/nira-browser/actions/runs/43006930215 na ad41702077ed7f155781c9ff66d443bf61c772ff. Wyniki nie weryfikują nowego etapu 6/6. Finalne CI w toku/nieuruchomione w chwili checkpointu; najpierw utwórz nowy SHA.
verified_before_push: Potwierdzono live HEAD ad41702077ed7f155781c9ff66d443bf61c772ff, AGENTS.md, docs/ai/ACTIVE_CLEAN_LINE.md, docs/AI_HANDOFF.md, docs/ai/STARTUP_PREFLIGHT.md, docs/ai/CHATGPT_PROJECT_INSTRUCTIONS.md oraz polityki workflow/test/quality/error/checkpoint. Przed update_ref powtórz kontrolę HEAD i konfliktu równoległej pracy.
blockers: Testy syntetyczne i Robolectric nie zastępują prawdziwego urządzenia, a bez osobnego polecenia nie wolno budować APK. Brak odrębnego recenzenta wysokiego ryzyka ogranicza niezależną ocenę; wymagany self-review i CI nie są testem telefonu.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/43006921141
  - https://github.com/laszko14/nira-browser/actions/runs/43006930215
  - https://github.com/laszko14/nira-browser/commit/ad41702077ed7f155781c9ff66d443bf61c772ff
changed_paths:
  - app/src/main/assets/extensions/zavro-preview-diagnostics/content.js
  - app/src/main/assets/extensions/zavro-preview-diagnostics/manifest.json
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayPageBridgeController.kt
  - app/src/test/js/mpv-play-diagnostics-stage2.test.cjs
  - app/src/test/js/mpv-play-diagnostics-stage5.test.cjs
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayPageBridgeControllerTest.kt
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayDiagnosticsTest.kt
  - docs/ai/MPV_PLAY_V2_STAGE6_DEVICE_TEST.md
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
next_action: Zatwierdzić etap 6/6 tylko po pełnym zielonym finalnym SOURCE i PR CI dla jednego HEAD. Wtedy raport CODE_VERIFIED i STOP. APK oraz DEVICE_VERIFIED wymagają osobnych poleceń i rzeczywistych dowodów.
```

## Granice interpretacji

Kod wykrywa i klasyfikuje zdarzenia, nie przejmuje playback. `START_ACK` potwierdza komunikację skryptu, `top_frame_baseline_confirmed` potwierdza migawkę głównego dokumentu, a `page_events_observed` potwierdza tylko zaobserwowanie skategoryzowanych interakcji. Żaden z tych stanów sam nie dowodzi naprawy Play ani przyczyny niedziałających miniaturek. Do rozstrzygnięcia potrzebne są późniejsze testy telefonu z konkretnymi stronami.
