# AI_HANDOFF — Zavro Clean, MPV/Play V2 etap 1/6 (11.11.2026)

Kanon: `docs/ai/ACTIVE_CLEAN_LINE.md`. To checkpoint sprzed końcowego CI; aktualny wynik musi pochodzić z live GitHub Actions.

```yaml
task_id: ZC-MPV-PLAY-EXTENSION-VERSION-20261111
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110
base_sha: d5cf05362c67fd3b836f7dc8e71ad8be93084a6b
observed_head_sha: d5cf05362c67fd3b836f7dc8e71ad8be93084a6b
status: CI_PENDING
acceptance_source: Użytkownik zatwierdził naprawę MPV/Play V2 w sześciu etapach; DALEJ upoważnia do IMPLEMENT + VERIFY wyłącznie etapu 1, bez APK.
completed: Etap 1 koduje manifest 1.0.2, statyczną wersję content.js w READY, sprawdzanie wersji przed START, osobny SCRIPT_VERSION_MISMATCH i bezpieczny zapis wersji w ZIP; rejestracja handlera może zostać ponowiona po niepowodzeniu. Dodano regresje JS/JVM dla zgodności wersji, starszego READY, błędnego READY, ponownej rejestracji i opóźnionej instalacji.
remaining: Potwierdzić pełny końcowy SOURCE i PR CI na finalnym source SHA; naprawić ewentualne FAIL w tym samym etapie. Po zielonych bramkach zatrzymać się przed etapem 2 do nowego DALEJ.
required_ci: SOURCE process-docs, JavaScript, Kotlin/Robolectric, AndroidTest compile, Android lint/lint-regression, summary oraz PR JavaScript, Kotlin/Robolectric, AndroidTest compile, lint i error-learning evidence, wszystkie na końcowym source SHA.
device: NOT_TESTED; fizyczny Nothing Phone (1), Android 15 i aktualizacja rzeczywiście istniejącego dodatku nie zostały przetestowane.
apk: NOT_BUILT_IN_THIS_TASK; kompilacja i pobieranie APK są zabronione. Historyczny zdalny v1228 z run 42916351673 bazuje na starym SHA.
known_failures: Historyczne NO_ACK i brak page events z wcześniejszego telefonu pozostają bez potwierdzonej przyczyny. Wcześniejsze modyfikacje content.js przy niezmienionym 1.0.1 mogą nie być aktualizowane przez Gecko ensureBuiltIn. Etapy 2-5 obejmują retry, właściciela portu, diagnostykę i obserwację ramek.
verification: Na SHA d5cf05362c67fd3b836f7dc8e71ad8be93084a6b źródłowy SOURCE 42937899241 oraz PR 42937910559 miały SUCCESS; te runy nie weryfikują zmian tego etapu. Obecne testy i CI oczekują na weryfikację.
verified_before_push: Odczytano live HEAD, AGENTS.md, ACTIVE_CLEAN_LINE.md, AI_HANDOFF.md, STARTUP_PREFLIGHT.md, CHATGPT_PROJECT_INSTRUCTIONS.md oraz polityki testów, jakości, błędów i checkpointu z d5cf05362c67fd3b836f7dc8e71ad8be93084a6b. Wymagana ponowna kontrola HEAD przed update_ref.
blockers: Fizyczna zgodność wgranej wersji WebExtension wymaga osobnego zatwierdzonego APK po etapach 1-6 i realnego raportu z telefonu. Bez DEVICE_VERIFIED.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/42937899241
  - https://github.com/laszko14/nira-browser/actions/runs/42937910559
  - https://github.com/laszko14/nira-browser/commit/d5cf05362c67fd3b836f7dc8e71ad8be93084a6b
changed_paths:
  - app/src/main/assets/extensions/zavro-preview-diagnostics/manifest.json
  - app/src/main/assets/extensions/zavro-preview-diagnostics/content.js
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayPageBridgeController.kt
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayDiagnostics.kt
  - app/src/test/js/mpv-play-diagnostics-stage5.test.cjs
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayPageBridgeControllerTest.kt
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
next_action: Zamknąć etap 1 wymaganymi testami/CI na końcowym SHA. Po sukcesie kolejny DALEJ oznacza etap 2/6. Bez APK, merge, release i force-push.
```

## Uzasadnienie i ograniczenia

W tej wersji GeckoEngine używa `ensureBuiltIn`, które nie instaluje dodatku ponownie, gdy jego manifest deklaruje tę samą wersję. Skrypt diagnostyczny zmieniał się przy `manifest.version=1.0.1`. Zwiększenie wersji i handshake `READY.scriptVersion` umożliwiają rozpoznanie starego czteropolowego READY jako `MISSING` zamiast ogólnego `NO_ACK`. Odczytana wersja pochodzi z wykonywanego skryptu, nie z samej deklaracji instalacji; zdalny CI nie potwierdza jednak instalacji po aktualizacji na fizycznym urządzeniu.
