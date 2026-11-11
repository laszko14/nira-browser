# AI_HANDOFF — Zavro Clean, diagnostyka przycisku MPV/Play (11.11.2026)

Kanon: `docs/ai/ACTIVE_CLEAN_LINE.md`; historia: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`. To checkpoint przygotowany przed końcowym CI, nie dowód PASS.

```yaml
task_id: ZC-MPV-PLAY-BUTTON-P0-20261111
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110
base_sha: ed1bc243224893f8eedfc39cd504942915c7a823
observed_head_sha: ed1bc243224893f8eedfc39cd504942915c7a823
status: CI_PENDING
acceptance_source: Użytkownik zlecił dokładny audyt oraz potwierdzone minimalne naprawy przycisku MPV/Play; bez APK, merge, release i force-push.
completed: Odczytano G1 i bieżący kod; potwierdzono deterministyczny błąd drugiego START bez odświeżenia strony — STOP czyści READY na żywym porcie. Przygotowano utrzymanie READY do disconnect, regresję JVM z jednym portem i późnym starym ACK, pasywny reconnect JS po STOP i korektę nieaktualnego tekstu statusu w menu. Zapisano nowy fingerprint w ERROR_CASES. Poprzedni ed1bc243 SOURCE 42914453857 i PR 42914462417 były SUCCESS, nie potwierdzają obecnej poprawki.
remaining: Potwierdzić brak równoległych zapisów, commit/push na tej samej gałęzi, sprawdzić pełny final SHA i komplet SOURCE/PR CI. Po potwierdzonym CI jeden test fizycznego telefonu jest konieczny dla pierwszego NO_ACK, miniaturek i realnego GeckoView. W razie FAIL naprawić przyczynę w tym zadaniu.
required_ci: SOURCE process-docs + shared-tests (JavaScript, Kotlin/Robolectric, AndroidTest compile, Android lint, lint-regression) + summary, oraz odrębne PR shared-tests + error-learning evidence; oba na końcowym źródłowym SHA.
device: NOT_TESTED; brak nowego fizycznego raportu Nothing Phone (1) z tej poprawki. Historyczny v1227 miał NO_ACK i zero page events, a obecna diagnoza nie ustala jeszcze zachowania pierwszej sesji na urządzeniu.
apk: NOT_BUILT_IN_THIS_TASK; ostatni zweryfikowany zdalny prywatny artefakt przed zadaniem to v1228, run 42916351673, nazwa zavro-arm64-update-apk-v1228, SHA ed1bc243224893f8eedfc39cd504942915c7a823; nowe APK jawnie zabronione.
known_failures: Historyczne NO_ACK ze starszej wersji wymaga testu telefonu. Pierwszy bieżący SOURCE run 42937132990 process-docs FAIL w job 130249861699: rejestr wskazał niedozwolony adres blob i niekanoniczną ścieżkę regresji. Przyczyna fixture jest potwierdzona i wpis skorygowano bez osłabiania walidatora; pozostałe kontrole wymagają sprawdzenia.
verification: Źródłowy SHA df565a9938ba4a61d3b7ae8616dd7160500fed76: SOURCE 42937132990 i PR 42937140900 JS SUCCESS, SOURCE process-docs FAIL (fixture) z konkretnego kroku error-learning; Kotlin/Robolectric, AndroidTest, lint i finalne CI jeszcze niepotwierdzone. Nowy SHA po poprawce wymaga pełnej weryfikacji. Nie twierdzić, że Play z witryny pochodzi z dawnego MPV.
verified_before_push: GitHub HEAD i pięć plików G1 odczytane z ed1bc243224893f8eedfc39cd504942915c7a823, polityki testów/CI/error-learning/checkpoint odczytane; ponowny odczyt live HEAD wymagany bezpośrednio przed update_ref.
blockers: Pierwsze NO_ACK wymaga rzeczywistego raportu telefonu po nowym APK, którego to zadanie nie buduje; nawet zielone CI nie daje DEVICE_VERIFIED.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/140
  - https://github.com/laszko14/nira-browser/actions/runs/42914453857
  - https://github.com/laszko14/nira-browser/actions/runs/42914462417
  - https://github.com/laszko14/nira-browser/actions/runs/42916351673
  - https://github.com/laszko14/nira-browser/actions/runs/42937132990
  - https://github.com/laszko14/nira-browser/actions/runs/42937140900
changed_paths:
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayPageBridgeController.kt
  - app/src/main/assets/extensions/zavro-preview-diagnostics/content.js
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayPageBridgeControllerTest.kt
  - app/src/test/js/mpv-play-diagnostics-stage5.test.cjs
  - app/src/main/java/com/prirai/android/nira/BrowserFragment.kt
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
next_action: Sprawdź finalny SOURCE i PR CI, napraw ewentualny FAIL. Bez APK ani kolejnego etapu. Zewnętrzne MPV Alpha i nira-media-detector mają pozostać niezmienione.
```

## Rozpoznany błąd i ograniczenia

Android `command("stop")` i `stopPorts()` wywoływały `clearPort()`, który usuwał `readyDocuments` dla nadal połączonego `Port`. Content script wysyła `READY` tylko przy nawiązaniu połączenia. Kolejny START na tej samej stronie pomijał wysyłkę i mógł zakończyć się `NO_READY`. Odrębna dziura: po STOP i późniejszym rozłączeniu JS blokował próbę pasywnego ponownego podłączenia (pozostawiając brak gotowości dla następnej sesji). Naprawa nie wykonuje odtwarzania, nie przechwytuje danych strony ponad obecne limity i nie zmienia profili.

Nowa regresja JVM sprawdza dwa kolejne START na tym samym porcie bez ponownego READY i odrzucenie spóźnionego ACK; regresja JS sprawdza, że port łączy się ponownie po STOP bez aktywacji nasłuchów strony. Pierwszy `NO_ACK` na fizycznym urządzeniu wymaga nadal nowego ZIP z `transport.json`, `summary.json` i `page-events.json`.
