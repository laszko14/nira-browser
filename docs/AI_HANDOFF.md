# AI_HANDOFF — Zavro Clean MPV / Play diagnostic stage 1/5

Archive: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md.
Predecessor: stage 10/10 on branch feat/zavro-clean-preview-final-stage10-20261110, SHA 05c389b0ed1f2ee5f07cbf91aeadb8533d962bd9.

```yaml
task_id: ZC-MPV-PLAY-DIAG-1
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110
base_sha: 05c389b0ed1f2ee5f07cbf91aeadb8533d962bd9
observed_head_sha: 98634125e21af4f10e80fb54fc5b49a76f570752
status: CI_PENDING
acceptance_source: User approved stage 1/5 of MPV / Play forensic diagnostic button, with locally retained test data and user-initiated ZIP export; no APK request.
predecessor_ci: SOURCE 42809772570 SUCCESS; PR 42809778220 SUCCESS on 05c389b0ed1f2ee5f07cbf91aeadb8533d962bd9.
completed: Implemented in code - START / MARK / STOP / ZIP EXPORT / DISCARD, bounded in-memory native snapshots, FileProvider export, profile isolation, private mode exclusion, TestLab gate. Repaired JS fixture, legacy MPV naming, checkpoint fields and lint UseKtx. Code fix 98634125e21af4f10e80fb54fc5b49a76f570752 SOURCE 42824863024 and PR 42824871763 SUCCESS.
remaining: Verify all final SOURCE and PR CI on exact final SHA; test the UI on physical Android and collect evidence on affected websites; stage 2 to add DOM, JavaScript and iframe observations.
required_ci: SOURCE process-docs, shared-tests JavaScript / JVM / AndroidTest compile / lint regression, quality-evidence; PR shared-tests integration JavaScript / JVM / AndroidTest compile / lint, error-learning evidence.
apk: NOT_BUILT — no APK requested. No signing, package, ABI, versionCode or certificate changes in stage 1.
device: NOT_TESTED
privacy: Explicit start only, native-window view metadata only; no DOM, page content, URL path, cookie, token or network capture. No automatic upload. Private tabs excluded in stage 1.
known_failures: ERR-20261110-PREVIEW-01 stays OPEN; the real site, stale MPV owner and physical device remain unverified.
verification: Code SHA 98634125e21af4f10e80fb54fc5b49a76f570752 SOURCE 42824863024 and PR 42824871763 SUCCESS (JS, process-docs, Kotlin/Robolectric, AndroidTest compile and lint baseline). Final documentation commit requires its own SOURCE/PR CI. Physical device and real website NOT_TESTED.
verified_before_push: Direct connector reviewed exact branch, menu, FileProvider, shared diagnostics gate, bounded session and targeted tests. Not a local Android compile.
blockers: No code blocker at verified 98634125e21af4f10e80fb54fc5b49a76f570752. Final SOURCE and PR CI pending on the next documentation/checkpoint SHA. Four stage1 failures recorded with tested corrections, including confirmed lint UseKtx on old SHA d9a55d90f3a2dde064c83adffeed15feca158a32.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/42809772570
  - https://github.com/laszko14/nira-browser/actions/runs/42809778220
  - https://github.com/laszko14/nira-browser/actions/runs/42819325010
  - https://github.com/laszko14/nira-browser/actions/runs/42819284332
  - https://github.com/laszko14/nira-browser/actions/runs/42820842539
  - https://github.com/laszko14/nira-browser/actions/runs/42820851062
  - https://github.com/laszko14/nira-browser/actions/runs/42824863024
  - https://github.com/laszko14/nira-browser/actions/runs/42824871763
  - https://github.com/laszko14/nira-browser/pull/140
changed_paths:
  - app/src/main/java/com/prirai/android/nira/BrowserFragment.kt
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayDiagnostics.kt
  - app/src/main/java/com/prirai/android/nira/diagnostics/testlab/DiagnosticSessionGate.kt
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayDiagnosticsTest.kt
  - app/src/test/js/mpv-play-diagnostics-stage1.test.cjs
  - docs/ai/MPV_PLAY_DIAGNOSTICS_STAGE1.md
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
next_action: Confirm final SOURCE and PR CI; if successful report CODE_VERIFIED with APK/device NOT_TESTED and wait for DALEJ (stage 2).
```
