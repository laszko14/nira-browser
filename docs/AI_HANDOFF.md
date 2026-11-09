# AI_HANDOFF — Zavro Clean diagnostics Stage 2/7

Historical archive: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md.
Pre-commit snapshot. Live GitHub ref is authoritative, not the observed SHA.
Stage 1 CODE_VERIFIED at 3467b4967f857c43a4158daf5c52168630310a14, SOURCE #42514106455, PR #42514110934 SUCCESS.

```yaml
task_id: ZC-VIDEO-DIAG-2
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-video-diagnostics-20261108
base_sha: 3467b4967f857c43a4158daf5c52168630310a14
observed_head_sha: 3467b4967f857c43a4158daf5c52168630310a14
status: CI_PENDING
acceptance_source: User explicit "dalej" after Stage 1 CODE_VERIFIED; implement only Stage 2/7; no APK until independent authorization after Stage 7.
completed: Passive opt-in WebExtension diagnostic module, strict native-port diagnostic start/read/stop/snapshot commands, form/results/recognition/extraction/metatada/pagination/SPA observability, bounded DOM/error samples and diagnostics JS tests.
remaining: Verify required SOURCE/PR CI on final SHA, repair actual failures and record observed errors. Stage 3 only after another user "dalej".
required_ci: SOURCE process-docs plus shared-tests JavaScript, Kotlin/Robolectric JVM, compileDebugAndroidTestKotlin, lint regression, required summary; PR shared-tests JavaScript/Kotlin/AndroidTest/lint and error learning evidence. All SUCCESS on identical final source SHA. Build/release SKIPPED.
device: NOT_TESTED — real GeckoView, physical Nothing Phone (1), actual websites and device performance not measured.
apk: NOT_BUILT — prohibited throughout 7 stages.
known_failures: First SOURCE #42569108916 and PR #42569113276 JavaScript suites failed on missing strict braces for empty marker and misaligned fixture/manifest expectation; SOURCE summary correctly rejected red JS. OBSERVED 3 / RECORDED 3 / OPEN 0, repaired by code commit 52348291b1d94dc33dd226786f5ce6cc764e8869. New final SHA CI must verify these repairs.
verification: Read AGENTS.md, docs/AI_HANDOFF.md, docs/ai/STARTUP_PREFLIGHT.md, TEST_POLICY.md, QUALITY_GATES.md, ERROR_LEARNING.md and Stage 1 contract from explicit base_sha. Examined manifest, JS recognition, native bridge and JS tests.
changed_paths:
  - app/src/main/assets/extensions/zavro-video-search/diagnostics.js
  - app/src/main/assets/extensions/zavro-video-search/content.js
  - app/src/main/assets/extensions/zavro-video-search/manifest.json
  - app/src/test/js/video-search-diagnostics-stage2.test.cjs
  - docs/ai/VIDEO_SEARCH_DIAGNOSTICS_STAGE2.md
  - docs/AI_HANDOFF.md
  - docs/ai/ERROR_CASES.json
  - app/src/test/js/video-search-p1-recognition.test.cjs
verified_before_push:
  - WebExtension permissions unchanged; no new automatic clicks or search behavior.
  - Local JS-only buffer is opt-in; native authenticated correlation to follow in Stage 3.
  - No APK, signing, release, merge or changes to user profiles/history/data.
blockers:
  - Real device and site NOT_TESTED; independent high-risk review unavailable.
  - Kotlin event/owner correlation and persistence deferred to Stage 3.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/129
  - https://github.com/laszko14/nira-browser/actions/runs/42514106455
  - https://github.com/laszko14/nira-browser/actions/runs/42514110934
next_action: Verify required CI on exact new SHA and STOP. Stage 3 only on explicit "dalej"; do not ask about APK.
```
