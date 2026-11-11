# AI_HANDOFF — Zavro Clean, MPV/Play V2 etap 2/6 (11.11.2026)

Kanon gałęzi: `docs/ai/ACTIVE_CLEAN_LINE.md`. Historyczne archiwum: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`. Ten checkpoint zapisano przed ukończeniem CI, dlatego wynik końcowy zawsze potwierdź na live Actions.

```yaml
task_id: ZC-MPV-PLAY-PORT-RECONNECT-20261111
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110
base_sha: 115995192a55f421642cd2950d7c9487618e8a56
observed_head_sha: 115995192a55f421642cd2950d7c9487618e8a56
status: CI_PENDING
acceptance_source: Użytkownik polecił DALEJ po zakończonym etapie 1/6; upoważnienie IMPLEMENT + VERIFY dotyczy wyłącznie etapu 2/6: reconnect i cykl życia portów, bez APK.
completed: Zmieniono mechanizm ponawiania pasywnego Gecko content port (jedna oczekująca próba, rosnące opóźnienie do 30 s, reset po READY, brak stałego limitu prób, ignorowanie starych/zdublowanych callbacków), bilans natywnych połączeń przy duplikacie callbacka i przekroczeniu 24 ramek, bilans po błędzie POST. Testy JS/JVM obejmują długą niedostępność, 8 odrębnych rozłączeń, prywatność i brak uruchamiania playback, stare callbacki, duplikat portu i 25 ramek. Wersja wbudowanego dodatku/handshake wzrosła do 1.0.3.
remaining: Zakończyć komplet wymaganych testów i regresji oraz SOURCE i PR CI na końcowym source SHA. Każdy FAIL diagnozować i naprawić w tym etapie. Następny etap 3/6 tylko po kolejnym DALEJ i zielonych bramkach.
required_ci: SOURCE process-docs i shared-tests (pełny JavaScript, Kotlin/Robolectric, AndroidTest compile, lint i lint-regression) oraz final summary; PR shared-tests i error-learning evidence, wszystkie na końcowym source SHA.
device: NOT_TESTED; fizyczny Nothing Phone (1), Android 15, strony produkcyjne i aktualizacja istniejącego dodatku wymagają późniejszych testów na telefonie.
apk: NOT_BUILT; bez APK i bez zmiany APK versionCode, pakietu, ABI ani certyfikatu. Historyczny zdalny APK v1228 run 42916351673 dotyczy starego SHA i nie zawiera kodu tego etapu.
known_failures: Potwierdzony błąd content.js, gdy reconnect attempt przechodził do callbacka onDisconnect i po kilku odrębnych sukcesach retry kończyło się na stałe; brak rejestracji starych portów bez zmian w czasie. Natywny callback dwa razy liczył ten sam Port; wyrzucenie 25. portu nie rozliczało połączenia w ledger. Dotychczasowy historyczny NO_ACK na telefonie nie ma potwierdzonej przyczyny. Nie naprawiano jeszcze pełnego owner-attribution ani rozróżnienia statusu iframe/baseline — przewidziane w etapach 3-5.
verification: Etap 1/6 zweryfikowano na live SHA 115995192a55f421642cd2950d7c9487618e8a56: SOURCE 42944535458 completed/success, PR 42944544375 completed/success. Ten SHA jest punktem wyjścia, a nie dowodem dla zmian etapu 2/6. Nowy końcowy SHA i pełne bramki oczekują na CI.
verified_before_push: Pobierano live HEAD, AGENTS.md, ACTIVE_CLEAN_LINE.md, AI_HANDOFF.md, STARTUP_PREFLIGHT.md, CHATGPT_PROJECT_INSTRUCTIONS.md oraz adekwatne polityki z dokładnego 115995192a55f421642cd2950d7c9487618e8a56. Przed update_ref ponownie sprawdzić aktywny HEAD i konflikt pracy równoległej.
blockers: Brak dowodu DEVICE_VERIFIED; bez upoważnienia do APK nie weryfikuje się rzeczywistego GeckoView na telefonie. Brak niezależnego zewnętrznego review poza standardowym CI; wykonano krytyczne self-review.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/42944535458
  - https://github.com/laszko14/nira-browser/actions/runs/42944544375
  - https://github.com/laszko14/nira-browser/commit/115995192a55f421642cd2950d7c9487618e8a56
changed_paths:
  - app/src/main/assets/extensions/zavro-preview-diagnostics/manifest.json
  - app/src/main/assets/extensions/zavro-preview-diagnostics/content.js
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayPageBridgeController.kt
  - app/src/test/js/mpv-play-diagnostics-stage2.test.cjs
  - app/src/test/js/mpv-play-diagnostics-stage5.test.cjs
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayPageBridgeControllerTest.kt
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
next_action: Dokończyć wymagane SOURCE/PR CI etapu 2 na końcowym SHA. Po sukcesie kolejne DALEJ oznacza etap 3/6. Bez APK, merge, release i force-push.
```

## Uwagi diagnostyczne

Wykrycie portu oznacza wyłącznie transport, nie wykonanie Play ani poprawne działanie witryny. Testy JS operują na symulowanym DOM/Gecko, Kotlin/Robolectric na atrapach Port; bez realnego GeckoView. Pasywny retry pozostaje aktywny w istniejącym dokumencie, lecz ogranicza częstotliwość prób do jednej co maksymalnie 30 sekund w razie długiej awarii; nie zbiera danych strony, dopóki użytkownik nie zainicjuje START.
