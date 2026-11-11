# AI_HANDOFF — Zavro Clean, MPV/Play V2 etap 1/6 (11.11.2026)

Kanon: `docs/ai/ACTIVE_CLEAN_LINE.md`; archiwum: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`. To checkpoint sprzed końcowego CI; aktualny wynik musi pochodzić z live GitHub Actions.

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
required_ci: SOURCE process-docs i shared-tests (JavaScript, Kotlin/Robolectric, AndroidTest compile, Android lint/lint-regression), summary oraz PR shared-tests i error-learning evidence, wszystkie na końcowym source SHA.
device: NOT_TESTED; fizyczny Nothing Phone (1), Android 15 i aktualizacja rzeczywiście istniejącego dodatku nie zostały przetestowane.
apk: NOT_BUILT_IN_THIS_TASK; kompilacja i pobieranie APK są zabronione. Historyczny zdalny v1228 z run 42916351673 bazuje na starym SHA.
known_failures: Drugi SOURCE 42942831114 process-docs FAIL w error-learning.cjs: nowy wpis coverage dodano na początek zamiast na koniec, podczas gdy fixture negative-tests pobiera pierwszy historyczny event i pierwszą historyczną sekcję coverage. Potwierdzono w job 130268488514 i przywrócono pierwotną kolejność bez zmiany testów. Pierwszy SOURCE 42942606380: process-docs FAIL, bo checkpointowi brakowało kanonicznych markerów shared-tests i archiwum; JS FAIL z powodu nieaktualnego pinu manifest.version 1.0.1 w stage2.test.cjs. PR 42942612799 powtórzył JS FAIL; wszystkie to błędy fixture/dokumentacji przy zmianie wersji, poprawione bez osłabiania asercji. Historyczne NO_ACK i brak page events z wcześniejszego telefonu pozostają bez potwierdzonej przyczyny. Wcześniejsze modyfikacje content.js przy niezmienionym 1.0.1 mogą nie być aktualizowane przez Gecko ensureBuiltIn. Etapy 2-5 obejmują retry, właściciela portu, diagnostykę i obserwację ramek.
verification: Drugi SHA 0d0b7799bbeed65bfe81bc684d40a60971f23f71: SOURCE 42942831114 process-docs FAIL (fixture coverage), JS SUCCESS; PR 42942837943 JS SUCCESS; pozostałe joby w chwili checkpointu jeszcze trwały. Poprawka rejestru oczekuje na pełny SOURCE/PR CI finalnego SHA. Pierwszy kodowy SHA 10dd940b7646ad2b7493cb53299f743155f667cd: SOURCE 42942606380 process-docs i JS FAIL (fixture), PR 42942612799 JS FAIL; Kotlin/AndroidTest/lint skipped. Poprawiono źródło w stage2 test i handoff, nowy SHA wymaga pełnego SOURCE/PR CI. Poprzednie sukcesy d5cf SOURCE 42937899241 i PR 42937910559 nie potwierdzają etapu 1.
verified_before_push: Odczytano live HEAD, AGENTS.md, ACTIVE_CLEAN_LINE.md, AI_HANDOFF.md, STARTUP_PREFLIGHT.md, CHATGPT_PROJECT_INSTRUCTIONS.md oraz polityki testów, jakości, błędów i checkpointu z d5cf05362c67fd3b836f7dc8e71ad8be93084a6b. Wymagana ponowna kontrola HEAD przed update_ref.
blockers: Fizyczna zgodność wgranej wersji WebExtension wymaga osobnego zatwierdzonego APK po etapach 1-6 i realnego raportu z telefonu. Bez DEVICE_VERIFIED.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/42937899241
  - https://github.com/laszko14/nira-browser/actions/runs/42937910559
  - https://github.com/laszko14/nira-browser/actions/runs/42942606380
  - https://github.com/laszko14/nira-browser/actions/runs/42942612799
  - https://github.com/laszko14/nira-browser/actions/runs/42942831114
  - https://github.com/laszko14/nira-browser/actions/runs/42942837943
  - https://github.com/laszko14/nira-browser/commit/d5cf05362c67fd3b836f7dc8e71ad8be93084a6b
changed_paths:
  - app/src/main/assets/extensions/zavro-preview-diagnostics/manifest.json
  - app/src/main/assets/extensions/zavro-preview-diagnostics/content.js
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayPageBridgeController.kt
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayDiagnostics.kt
  - app/src/test/js/mpv-play-diagnostics-stage5.test.cjs
  - app/src/test/js/mpv-play-diagnostics-stage2.test.cjs
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayPageBridgeControllerTest.kt
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
next_action: Zamknąć etap 1 wymaganymi testami/CI na końcowym SHA. Po sukcesie kolejny DALEJ oznacza etap 2/6. Bez APK, merge, release i force-push.
```

## Uzasadnienie i ograniczenia

W tej wersji GeckoEngine używa `ensureBuiltIn`, które nie instaluje dodatku ponownie, gdy jego manifest deklaruje tę samą wersję. Skrypt diagnostyczny zmieniał się przy `manifest.version=1.0.1`. Zwiększenie wersji i handshake `READY.scriptVersion` umożliwiają rozpoznanie starego czteropolowego READY jako `MISSING` zamiast ogólnego `NO_ACK`. Odczytana wersja pochodzi z wykonywanego skryptu, nie z samej deklaracji instalacji; zdalny CI nie potwierdza jednak instalacji po aktualizacji na fizycznym urządzeniu.
