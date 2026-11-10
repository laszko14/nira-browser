# AI_HANDOFF — Zavro Clean MPV/Play, etap 4/5

Archive: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md.

Poprzedni etap 3/5: SOURCE 42840717242 SUCCESS, PR 42840988723 SUCCESS
na pełnym SHA `d80ee2d7d9f438287c98041208e13b73980c8dab`. Zweryfikowano przez bezpośredni GitHub connector.
Obecny rekord jest checkpointem zapisanym PRZED końcowym CI etapu 4;
status źródłowy uzyskuje się tylko przez ponowny odczyt live HEAD i CI.

```yaml
task_id: ZC-MPV-PLAY-DIAG-4
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110
base_sha: d80ee2d7d9f438287c98041208e13b73980c8dab
observed_head_sha: d80ee2d7d9f438287c98041208e13b73980c8dab
status: CI_PENDING
acceptance_source: User DALEJ on 2026-11-10; approved stage 4/5 evidence report and only provable safe fixes; no APK.
completed: Verified stage3 source and PR CI SUCCESS at base. Diagnosed and corrected identical-sender-url Port confusion and overly permissive untrusted page metric strings. Added deterministic bounded diagnostic analysis.json and Polish analysis.txt with explicit UNKNOWN Play owner, gaps, hints and recommended next checks; selected element node correlation; chronology sort; JS and Kotlin acceptance tests; stage4 technical documentation and error registry.
remaining: Verify required SOURCE and PR CI on exact final stage4 SHA, classify and repair any failure in this same stage. Missing target-site runtime reproduction and physical Android tests remain independent NOT_TESTED. Stage5 pending a new DALEJ.
required_ci: SOURCE process-docs, shared-tests JavaScript WebExtension / JVM-Robolectric / AndroidTest compile / lint regression, quality-evidence; PR shared-tests JavaScript / JVM / AndroidTest compile / lint, error-learning evidence; all SUCCESS on final SHA.
apk: NOT_BUILT. Explicitly no APK in stage4; no certificate, ABI, versionCode, manifest, package or signing changes.
device: NOT_TESTED
privacy: Manual START only, private tabs disabled, in-memory and user-initiated ZIP export. Native allowlisted detail/event/target codes and exact metric types/enums; no raw DOM, source URLs, login credentials, foreign addon IDs or automatic transmission. Analysis uses sanitized metadata only.
known_failures: Old ERR-20261110-PREVIEW-01/-03/-04/-05 and ERR-20261110-BGPLAY-ALLOWLIST-RACE remain OPEN pending real site/device evidence; no confirmed ownership of site's Play overlay. Two confirmed stage4 source vulnerabilities corrected; first stage4 SOURCE run 42846284009 failed missing shared-tests; second 42847238424 failed missing legacy archive ref. Both checkpoint contract fields restored without weakening tests; Android checks pending.
verification: Stage3 base SOURCE https://github.com/laszko14/nira-browser/actions/runs/42840717242 SUCCESS, PR https://github.com/laszko14/nira-browser/actions/runs/42840988723 SUCCESS. Stage4 source review + structural verification performed; required Android compiler/CI on final SHA PENDING, target-site NOT_TESTED. Stage4 SOURCE 42846284009: JS 649/649 SUCCESS, process-docs FAIL missing shared-tests; SOURCE 42847238424: process-docs FAIL missing archive reference. Same checkpoint-contract incident, both fields fixed; new SHA CI required.
verified_before_push: Live GitHub branch and PR #140 at base SHA; AGENTS.md, docs/AI_HANDOFF.md, CHATGPT_PROJECT_INSTRUCTIONS.md, STARTUP_PREFLIGHT.md, DEVELOPMENT_WORKFLOW.md, TEST_POLICY.md, QUALITY_GATES.md, ERROR_LEARNING.md, CHECKPOINT_POLICY.md and stage3 report fetched at explicit base SHA. Changes prepared as Git tree/commits using direct GitHub connector; no local checkout/device, so no local Android build claim.
blockers: Real-world Play origin lacks on-device ZIP; do not modify active preview-assist.js, nira-bg-play or external MPV Alpha speculatively. CI pending, not blocked.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/42840717242
  - https://github.com/laszko14/nira-browser/actions/runs/42840988723
  - https://github.com/laszko14/nira-browser/pull/140
  - https://github.com/laszko14/nira-browser/actions/runs/42846284009
  - https://github.com/laszko14/nira-browser/actions/runs/42847238424
  - https://github.com/laszko14/nira-browser/commit/6a8d493293bb2cdca4376ebf715eb35019d307dc
changed_paths:
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayDiagnostics.kt
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayEvidenceReport.kt
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayPageBridgeController.kt
  - app/src/main/assets/extensions/zavro-preview-diagnostics/content.js
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayDiagnosticsTest.kt
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayEvidenceReportTest.kt
  - app/src/test/js/mpv-play-diagnostics-stage2.test.cjs
  - app/src/test/js/mpv-play-diagnostics-stage4.test.cjs
  - docs/ai/MPV_PLAY_DIAGNOSTICS_STAGE4.md
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
next_action: Verify full required SOURCE and PR CI on exact final SHA. Repair and regress any failures before CODE_VERIFIED. Once green, STOP and await next DALEJ for stage 5/5; no APK.
```
