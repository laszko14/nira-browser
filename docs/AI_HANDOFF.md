# AI_HANDOFF — Zavro Clean, MPV/Play READY handshake (10.11.2026)

Archive: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md.

Kanon aktywnej linii: `docs/ai/ACTIVE_CLEAN_LINE.md`. Ten checkpoint zapisano przed końcowym CI; sam nie dowodzi PASS.

```yaml
task_id: ZC-MPV-PLAY-READY-20261110
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110
base_sha: c8b5fde894dedf784e49206a363de699512346b5
observed_head_sha: c8b5fde894dedf784e49206a363de699512346b5
status: CI_PENDING
acceptance_source: User requested IMPLEMENT+VERIFY for MPV/Play diagnosis from original nine-file v1227 device ZIP, explicitly without a new APK.
completed: Read all nine original ZIP files and GeckoView messaging API. Verified previous SOURCE 42896394644 and PR 42896407543 SUCCESS at base_sha. Previous signed v1227 42898317447 remotely verified. Added content listener READY handshake before native START, bounded retries, typed reject/receive metrics, v2 transport ZIP fields, compact menu states and JS/Kotlin regression fixtures.
remaining: Review diff; commit/push same active branch, verify final SOURCE process-docs/shared-tests and PR shared-tests/error-learning on exact checkout SHA. Investigate and fix any FAIL. Confirm transport and moving preview behavior only in a later physical phone test after separately authorized APK.
required_ci: SOURCE process-docs and shared-tests JavaScript, Kotlin/Robolectric, AndroidTest compile, Android lint/regression baseline; PR shared-tests and error-learning on integration checkout.
apk: EXISTING_VERIFIED_v1227; run 42898317447, artifact zavro-arm64-update-apk-v1227, versionCode 1227, versionName 0.5.22-alpha123-clean, arm64-v8a, unchanged certificate. Do not build new APK during this task.
device: PREVIOUS_DIAGNOSTIC_FAILED; new handshake NOT_TESTED_ON_DEVICE.
known_failures: v1227 ZIP session ~8s: port connected=1, START attempts=1, ACK=0, page events=0, rejected packets=0; transport NO_ACK. Counters formerly did not cover silently discarded message type or identity. Actual cause of device failure undetermined.
verification: Pre-implementation baseline SOURCE/PR green. New code needs SOURCE and PR CI at final SHA. Real GeckoView runtime and physical device not available in CI.
verified_before_push: Read exact live HEAD AGENTS.md, ACTIVE_CLEAN_LINE.md, AI_HANDOFF.md, STARTUP_PREFLIGHT.md, CHATGPT_PROJECT_INSTRUCTIONS.md, relevant V3.2 policies, PR 140, source checks, signed v1227 and original ZIP at source SHA.
blockers: No access to physical device; no new signed APK requested.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/140
  - https://github.com/laszko14/nira-browser/actions/runs/42896394644
  - https://github.com/laszko14/nira-browser/actions/runs/42896407543
  - https://github.com/laszko14/nira-browser/actions/runs/42898317447
changed_paths:
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayPageBridgeController.kt
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayDiagnostics.kt
  - app/src/main/java/com/prirai/android/nira/BrowserFragment.kt
  - app/src/main/assets/extensions/zavro-preview-diagnostics/content.js
  - app/src/main/assets/extensions/zavro-preview-diagnostics/manifest.json
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayPageBridgeControllerTest.kt
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayDiagnosticsTest.kt
  - app/src/test/js/mpv-play-diagnostics-stage2.test.cjs
  - app/src/test/js/mpv-play-diagnostics-stage5.test.cjs
  - docs/ai/ERROR_CASES.json
  - docs/ai/MPV_PLAY_TRANSPORT_READY_20261110.md
  - docs/AI_HANDOFF.md
next_action: Verify final HEAD + required SOURCE and PR CI. Do not claim moving thumbnails repaired without a new device report.
```

## Oryginalny raport (v1227)

- Archiwum `mpv-play-eb76ff96-9c5c-422e-b904-3f49b6a5a1d0.zip`: dziewięć plików, sesja 8 sekund, jeden podłączony port, jedna próba START, zero ACK i zdarzeń strony, NO_ACK. Inwentarz dodatków dowodzi instalacji, nie wykonania skryptu w dokumencie.
- GeckoView dostarcza wysłany obiekt JavaScript jako `JSONObject`, a typ prosty jako odpowiadający mu typ JVM. Rozbudowano liczenie niepoprawnych typów, parse, schematu, sesji i dokumentu bez ujawniania treści.
- Wcześniejszy `onPortConnected` wykonywał `postMessage(START)` zanim odbiornik content JS potwierdził rejestrację słuchacza. Wdrożony handshake READY rozdziela połączenie, gotowość, wysłanie START, ACK, BASELINE oraz brak zdarzeń. Prawdopodobieństwo wyścigu potwierdza analiza kolejności kodu, ale nie dowodzi, że to źródło raportu z telefonu.
- Wnioski o przyczynie niesprawnych miniaturek pozostają `UNDETERMINED`; nie zmieniono odtwarzania MPV/HTML5, profili, certyfikatu ani danych.
