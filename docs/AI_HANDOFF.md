# AI_HANDOFF — Zavro Clean MPV/Play, V2 etap 3/6 (11.11.2026)

Kanon aktywnej linii: `docs/ai/ACTIVE_CLEAN_LINE.md`. Archiwum: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`. Checkpoint sporządzony przed końcowym CI, nie dowodzi ukończenia bramek.

```yaml
task_id: ZC-MPV-PLAY-TRANSPORT-V2-STAGE3-20261111
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110
base_sha: a0c1c3f540f43c933f4158e2783c661114a01452
observed_head_sha: a0c1c3f540f43c933f4158e2783c661114a01452
status: CI_PENDING
acceptance_source: Użytkownik zatwierdził DALEJ etap 3/6 z planu kompleksowej naprawy MPV/Play; IMPLEMENT+VERIFY wyłącznie tego etapu, bez APK.
completed: Skryptów WebExtension z etapu 2 nie zmieniano. Bridge zapisuje oryginalnego właściciela Port (profil+tab), raportuje disconnect do pierwotnej sesji również przy zmianie profilu/prywatności, czyszczeniu EngineSession i wyrzucaniu ramki; wycofuje stare porty i ignoruje ich dane. START bez karty/EngineSession/uprawnionego właściciela raportuje osobne TAB_MISSING, ENGINE_SESSION_MISSING lub OWNER_MISMATCH. Ledger nie maskuje fatalnego błędu późniejszymi callbackami; w summary/analysis/page-coverage/transport ZIP dodano wyłącznie bezpieczną kategorię etapu, liczbę otwartych portów, potwierdzenie TOP/SUBFRAME i liczniki uzyskanych zdarzeń. Nowe testy Kotlin/Robolectric sprawdzają zmianę trybu prywatnego, stare callbacki, trzy rodzaje braku sesji oraz treść ZIP i ograniczenia. Rejestr incydentów zaktualizowano bez duplikacji.
remaining: Wymagane SOURCE i PR CI na końcowym źródłowym SHA; przy FAIL konkretne logi, przyczyna i poprawka w ramach etapu 3. Nie rozpoczynać etapu 4/6 przed zielonymi bramkami i kolejnym DALEJ.
required_ci: SOURCE process-docs + shared-tests (JS, komplet Kotlin/Robolectric, AndroidTest compile, Android lint+lint-regression) + final summary oraz niezależne PR CI shared-tests i error-learning evidence, wszystkie na końcowym source SHA.
device: NOT_TESTED; brak rzeczywistego testu Nothing Phone (1) z Android 15.
apk: NOT_BUILT; zakaz budowy/pobierania APK, bez zmiany versionCode/certyfikatu/ABI/pakietu.
known_failures: Poprzednio onPortDisconnected brał trustedOwner(session) przy zdarzeniu, co po zmianie aktywnego profilu lub private mode gubiło pierwotne rozłączenie; ciche return przy START bez tab/EngineSession/owner uniemożliwiały precyzyjną diagnozę. Starsze NO_ACK z telefonu nadal nie ma potwierdzonej przyczyny.
verification: Etap 2 na SHA a0c1c3f540f43c933f4158e2783c661114a01452 miał SOURCE run 42947680582 SUCCESS i PR run 42947687156 SUCCESS. To dowodzi starego SHA, nie nowego kodu etapu 3.
verified_before_push: GitHub live HEAD i pełny SHA a0c1c3f540f43c933f4158e2783c661114a01452; AGENTS, ACTIVE_CLEAN_LINE, AI_HANDOFF, STARTUP_PREFLIGHT, CHATGPT_PROJECT_INSTRUCTIONS i właściwe polityki odczytane z tego samego SHA. Ponowna kontrola HEAD przed update_ref obowiązkowa.
blockers: Rzeczywiste interakcje w GeckoView wymagają późniejszej zatwierdzonej kompilacji i testu urządzenia. Brak niezależnej osobnej recenzji poza wymaganą kontrolą CI; self-review nie jest niezależną recenzją.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/42947680582
  - https://github.com/laszko14/nira-browser/actions/runs/42947687156
  - https://github.com/laszko14/nira-browser/commit/a0c1c3f540f43c933f4158e2783c661114a01452
changed_paths:
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayPageBridgeController.kt
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayDiagnostics.kt
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayPageBridgeControllerTest.kt
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayDiagnosticsTest.kt
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
next_action: Zamknąć etap 3/6 wymaganym SOURCE/PR CI na końcowym SHA, potem STOP. Etap 4/6 wyłącznie na kolejne DALEJ.
```

## Zakres dowodowy

Przypisanie portu do pierwotnego profilu zapobiega zaliczeniu zdarzeń do innej karty, ale nie obiecuje działającego Play na prawdziwej stronie. ZIP rozróżnia transport/READY/START/ACK/obserwacje i błędy środowiska bez adresów, HTML, selektorów, ciasteczek, danych uwierzytelniania czy źródeł wideo. Weryfikacja portów w Robolectric nie zastępuje rzeczywistego GeckoView.
