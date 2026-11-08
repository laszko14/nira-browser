# AI_HANDOFF - Zavro Clean Video Search Diagnostic Session 1/7

Historical archive: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md.
This is a pre-commit checkpoint. The live HEAD on GitHub is authoritative.
Previously verified P4-F2 final source SHA:
469d93b236b0a14790b82919dbc89c8c5abacdb4.
P4-F2 SOURCE #42500376062 and PR #42500381650 SUCCESS.

```yaml
task_id: ZC-VIDEO-DIAG-1
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-video-diagnostics-20261108
base_sha: 469d93b236b0a14790b82919dbc89c8c5abacdb4
observed_head_sha: 469d93b236b0a14790b82919dbc89c8c5abacdb4
status: CI_PENDING
acceptance_source: User authorized IMPLEMENT staged 1/7, one "dalej" per stage, no APK until separate approval after stage 7.
completed: Separate versioned diagnostics event schema and recorder foundation, correlation keys, observation vs inference, explicit loss counters, bounded queue admission and off-main writing, hashed per-normal-profile no-backup files, private RAM retirement and strict redaction; synthetic Robolectric tests and documented contract.
remaining: Verify required SOURCE and PR CI on same final SHA. Triage actual failures within stage 1. Do not start stage 2 without user's separate "dalej".
required_ci: SOURCE process-docs and shared-tests JS, Kotlin/Robolectric JVM, compileDebugAndroidTestKotlin, lint regression and required-summary; PR shared-tests JS/JVM/AndroidTest Kotlin compile/lint/error-learning evidence. Build/release SKIPPED.
device: NOT_TESTED - no real GeckoView, live sites or Nothing Phone (1).
apk: NOT_BUILT - forbidden during all seven stages until a new approval afterward.
known_failures: Initial SOURCE #42512509777 had process-docs missing YAML checkpoint, compiler errors at VideoSearchDiagnosticRecorder.kt lines 264-265 in 3 Kotlin jobs, and dependent required-summary failure; PR #42512537056 had the same Kotlin compiler error in 3 jobs. Ten observed CI job failures (first SOURCE/PR and second JVM SOURCE/PR) and one self-review finding, all recorded in docs/ai/ERROR_CASES.json. Repairs in commits a01d17e6a510bb8fd3d969397415a67e990a87e3, ea48beaa615afc58e7ea46b726781e04c0dc0957 and d1db6bb1841859374cfb4d836397d7894eec6305; final SOURCE/PR CI verification still pending.
verification: Read AGENTS.md, docs/AI_HANDOFF.md, docs/ai/STARTUP_PREFLIGHT.md and relevant quality/test/workflow/checkpoint/error-learning instructions at exact base SHA via GitHub connector. Inspected WebExtension, bridge, protocol, queue, run journal, Room/private stores, results presenter and TestLab exports.
changed_paths:
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchDiagnosticRecorder.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchDiagnosticRecorderTest.kt
  - docs/ai/VIDEO_SEARCH_DIAGNOSTICS_STAGE1.md
  - docs/AI_HANDOFF.md
verified_before_push:
  - No changes to WebExtension permissions, existing search/queue/Room decisions, package, profile data, signing or CI workflow.
  - Diagnostic module is not yet connected to search; never claim coverage.
  - Stage 1 tests use synthetic data and do not certify a real phone.
blockers:
  - Independent high-risk reviewer unavailable; self-review is not independent acceptance.
  - Real site and Android device acceptance NOT_TESTED.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/128
  - https://github.com/laszko14/nira-browser/blob/469d93b236b0a14790b82919dbc89c8c5abacdb4/docs/ai/P4_FINAL_REGRESSION.md
next_action: Check and repair required CI if necessary; STOP after stage 1, wait for "dalej". Do not ask about APK.
```
