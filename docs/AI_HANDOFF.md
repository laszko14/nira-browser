# AI_HANDOFF — Zavro Clean MPV / Play diagnostic stage 2/5

Archive: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md.
Predecessor: stage 1/5, SHA e14157ad6ad5d39ca9ec8c42e41dad8918a47750; SOURCE 42826748109 and PR 42826759307 SUCCESS.

```yaml
task_id: ZC-MPV-PLAY-DIAG-2
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110
base_sha: e14157ad6ad5d39ca9ec8c42e41dad8918a47750
observed_head_sha: e14157ad6ad5d39ca9ec8c42e41dad8918a47750
status: CI_PENDING
acceptance_source: User DALEJ on 2026-11-10, approved stage 2/5 passive page instrumentation and local ZIP; NO APK.
completed: Stage 1 preflight and source/PR CI verified. Stage 2 bounded WebExtension probe, native normal-profile and EngineSession port ownership, page event validation, picker, archive and tests prepared.
remaining: Verify all required SOURCE and PR CI on exact final SHA; physical Android site test; await user DALEJ for stage 3.
required_ci: SOURCE process-docs, shared-tests JavaScript / JVM / AndroidTest compile / lint regression, quality-evidence; PR shared-tests integration JavaScript / JVM / AndroidTest compile / lint, error-learning evidence.
apk: NOT_BUILT — NO APK requested. Package, ABI, signing and versionCode unchanged.
device: NOT_TESTED
privacy: Explicit START only, no raw DOM content, URL, login or network payloads. Normal profile and target tab only; private tabs excluded. No auto-upload.
known_failures: ERR-20261110-PREVIEW-01 and earlier bgplay cases remain OPEN; origin of Play not confirmed.
verification: Stage1 SOURCE 42826748109 and PR 42826759307 SUCCESS on base SHA. Stage2 final CI pending. Device NOT_TESTED.
verified_before_push: Direct GitHub connector fetched live HEAD and AGENTS.md, AI_HANDOFF.md, STARTUP_PREFLIGHT and task-specific policies at base SHA; inspected WebExtension and GeckoView APIs in repository. Local Android compile unavailable.
blockers: No pre-commit blocker known. Real-site and Android runtime test await explicitly requested signed APK.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/42826748109
  - https://github.com/laszko14/nira-browser/actions/runs/42826759307
  - https://github.com/laszko14/nira-browser/pull/140
changed_paths:
  - app/src/main/java/com/prirai/android/nira/BrowserApp.kt
  - app/src/main/java/com/prirai/android/nira/BrowserFragment.kt
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayDiagnostics.kt
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayPageBridgeController.kt
  - app/src/main/assets/extensions/zavro-preview-diagnostics/manifest.json
  - app/src/main/assets/extensions/zavro-preview-diagnostics/content.js
  - app/src/test/js/mpv-play-diagnostics-stage1.test.cjs
  - app/src/test/js/mpv-play-diagnostics-stage2.test.cjs
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayDiagnosticsTest.kt
  - docs/ai/MPV_PLAY_DIAGNOSTICS_STAGE2.md
  - docs/AI_HANDOFF.md
next_action: Complete SOURCE and PR CI on final SHA; repair FAIL in this task; report CODE_VERIFIED only after green required checks and then wait for DALEJ.
```
