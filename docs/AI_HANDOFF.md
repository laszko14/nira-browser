# AI_HANDOFF — Zavro Clean MPV / Play diagnostic stage 3/5

Archive: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md.
Stage2 checkpoint: base SHA b33afe322e7b44467ee33a83810ffb95a7eb29de; SOURCE 42836649028 and PR 42836662817 verified SUCCESS. Stage2 handoff kept as historical source at that SHA.

```yaml
task_id: ZC-MPV-PLAY-DIAG-3
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110
base_sha: b33afe322e7b44467ee33a83810ffb95a7eb29de
observed_head_sha: b33afe322e7b44467ee33a83810ffb95a7eb29de
status: CI_PENDING
acceptance_source: User DALEJ 2026-11-10: stage 3/5 provenance of Play, embedded-MPV residue review, packaged preview assist, bgplay allowlist and runtime addon inventory. No APK.
completed: Verified stage2 SOURCE run 42836649028 and PR run 42836662817 SUCCESS at base SHA. Stage3 static ownership audit and bounded opt-in addon inventory, helper DOM marker hint, nonce/profiles isolation tests, report prepared.
remaining: Required SOURCE and PR CI on final stage3 SHA; real website and Android device evidence; defer targeted changes to stage4 only after next DALEJ.
required_ci: SOURCE process-docs, shared-tests JavaScript / JVM / AndroidTest compile / lint regression, quality-evidence; PR shared-tests integration JavaScript / JVM / AndroidTest compile / lint, error-learning evidence.
apk: NOT_BUILT — no APK requested, package, ABI, signing and versionCode unchanged.
device: NOT_TESTED
privacy: Manual START only. Only three known builtin extension installation statuses and target-origin allow permission exported. No foreign extension IDs or host/URL values. Private tabs excluded. DOM helper marker is untrusted hint, not origin proof.
known_failures: ERR-20261110-PREVIEW-01/-03/-04/-05 and BGPLAY allowlist issues remain OPEN for real website/device evidence. No new confirmed cause of the particular Play overlay. Historical stage2 issues fixed and CI verified at base SHA.
verification: Base stage2 SOURCE 42836649028 attempt1 SUCCESS and PR 42836662817 attempt1 SUCCESS on b33afe322e7b44467ee33a83810ffb95a7eb29de. Stage3 source CI pending. No APK and no physical website repro.
verified_before_push: Direct GitHub live branch head, AGENTS.md, docs/AI_HANDOFF.md, CHATGPT_PROJECT_INSTRUCTIONS.md and selected preflight/test/quality sources read at base SHA. Preview helper, bgplay, extension manifests and historical stage7 audit crosschecked; synthetic targeted JS source syntax and structural conditions passed. Local Kotlin compile unavailable.
blockers: Device + target site unavailable; Play origin cannot be asserted. CI pending until final source and PR workflows complete.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/42836649028
  - https://github.com/laszko14/nira-browser/actions/runs/42836662817
  - https://github.com/laszko14/nira-browser/pull/140
changed_paths:
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayDiagnostics.kt
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayPageBridgeController.kt
  - app/src/main/assets/extensions/zavro-preview-diagnostics/content.js
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayDiagnosticsTest.kt
  - app/src/test/js/mpv-play-diagnostics-stage2.test.cjs
  - app/src/test/js/mpv-play-diagnostics-stage3.test.cjs
  - docs/ai/MPV_PLAY_DIAGNOSTICS_STAGE3.md
  - docs/AI_HANDOFF.md
next_action: Finish stage3 required SOURCE and PR CI on final exact SHA; any FAIL must be fixed in stage3 and rerun on new SHA, then report CODE_VERIFIED, no APK and wait for DALEJ to start stage4.
```
