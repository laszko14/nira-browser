# AI_HANDOFF — Zavro Clean V4 / P2-E dynamic context resilience
> Historia: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md. P2-D zakończono na b3173f0b7492296d664ad525c9b649ea91234a6a.

```yaml
task_id: ZC-V4-P2-E-DYNAMIC-RESILIENCE
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-v4-p2-evidence-runtime-20261103
base_sha: b3173f0b7492296d664ad525c9b649ea91234a6a
observed_head_sha: 7629e8de75e96bf55bb6a664f7ed7c00d5ede6f1
status: CI_PENDING
acceptance_source: User IMPLEMENT P2-E on current V4 branch; diagnose, implement, regressions, commit/push and green SOURCE/PR integration CI in one task; absolutely NO APK, merge or MPV.
completed: P2-E robust bounded recognition over MAIN/open Shadow/same-origin iframe contexts; shared 2400 DOM visit budget fairly apportioned among context roots; SCAN_LIMIT explicitly fails closed on partial scan; same-origin iframe document substitution invalidates stored scoped locator and reports FRAME_REPLACED; weak confidence 75-84 reports LOW_CONFIDENCE instead of protocol-invalid found; generated hashed/numeric IDs/classes/data attributes avoided in saved selectors; legacy homepage fallback refuses stale/ambiguous/weak/truncated context. New JS and Robolectric protocol regressions. No Room migration or persisted-data changes.
remaining: Verify mandatory SOURCE and PR integration CI on final SHA; fix errors if any; real sites, cross-origin iframe/closed Shadow roots, GeckoView, rotation, phone and true live navigation remain NOT_TESTED.
required_ci: Process-docs, JavaScript WebExtension, Kotlin/Robolectric, AndroidTest Kotlin compile, Android lint regression and source evidence all SUCCESS on exact final source SHA; separate PR integration workflow with required jobs SUCCESS. Build/release may be SKIPPED; do not create APK.
device: NOT_TESTED
apk: NOT_BUILT; no private workflow trigger is authorized by this task.
known_failures: 5 distinct review findings and 4 failed initial SOURCE job/test outcomes (3 JS cases, 1 dependent summary) recorded in ERROR_CASES.json, all FIXED by commits b230410f89baac391aba21fb66f85f8cab36cea5 and 7629e8de75e96bf55bb6a664f7ed7c00d5ede6f1; first SOURCE run 41872440694 failed due three JS test failures. Repeat SOURCE JS job 41872740292 SUCCESS, remaining gates pending when this checkpoint was written.
verification: Live PR #123 HEAD b3173f0b7492296d664ad525c9b649ea91234a6a before start; AGENTS.md, AI_HANDOFF.md, STARTUP_PREFLIGHT, DEVELOPMENT_WORKFLOW, TEST_POLICY, QUALITY_GATES, CHECKPOINT_POLICY, ERROR_LEARNING, TASK_TEMPLATE, TEST_PATTERNS, EXCEPTIONS_AND_TOOLS and registry read from that exact SHA. After two P2-E commits remote HEAD 7629e8de75e96bf55bb6a664f7ed7c00d5ede6f1. Local checkout, full real-site Gecko and device evidence unavailable.
changed_paths:
  - app/src/main/assets/extensions/zavro-video-search/recognition.js
  - app/src/main/assets/extensions/zavro-video-search/content.js
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchBridgeProtocol.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchRecognitionDiagnostics.kt
  - app/src/test/js/video-search-p1-recognition.test.cjs
  - app/src/test/js/video-search-structural-v4.test.cjs
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchP1RecognitionTest.kt
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
verified_before_push:
  - PR #123 remains open/draft on the correct V4 P2 branch; permission admin/push confirmed.
  - First SOURCE 41872440694 process-docs success, JS 460/463 with 3 failures, dependent summary failed; remaining matrix skipped.
  - Failures diagnosed via direct GitHub job logs: 1 root starvation on dense MAIN, 2 incomplete test stubs. Corrected budget fairness and fixture and pushed 7629e8de75e96bf55bb6a664f7ed7c00d5ede6f1.
  - SOURCE 41872740292 JS regressions success on corrected commit; Kotlin/Robolectric, AndroidTest compile and lint were in progress at handoff time. Results must be rechecked via API.
  - No APK, merge, public release, force-push, signing/certificate or MPV changes.
blockers:
  - Live third-party site and Gecko/Android 15 behavior cannot be certified by headless JS/JVM fixtures; DEVICE NOT_TESTED.
  - Cross-origin iframes and closed Shadow DOM intentionally unsupported; any unsupported context fails closed.
  - Independent privacy/lifecycle security reviewer not yet confirmed; self-review and CI do not replace it.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/123
  - https://github.com/laszko14/nira-browser/commit/b230410f89baac391aba21fb66f85f8cab36cea5
  - https://github.com/laszko14/nira-browser/commit/7629e8de75e96bf55bb6a664f7ed7c00d5ede6f1
  - https://github.com/laszko14/nira-browser/actions/runs/41872440694
  - https://github.com/laszko14/nira-browser/actions/runs/41872740292
security: No new network actions, user clicks or auto-submission; only user-authorized existing search flow. No raw query, profiles or DOM saved to telemetry. Replaced frames/ambiguous selectors, incomplete scans and weak evidence fail closed, with no silent repair.
next_action: Green SOURCE and PR integration for final checkpoint SHA; triage if needed and do not build any APK.
```
