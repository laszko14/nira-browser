# AI_HANDOFF — Zavro Clean V4 / P4-A2 dynamic lifecycle regressions

> Historical checkpoints: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md. This checkpoint is a pre-commit snapshot, not the live head ref. P4-A1 was CODE_VERIFIED on `36699b20701664b6f65a42fd8f6fb18558ec513a` with SOURCE #42348993221 and PR #42348996814, both completed SUCCESS, attempt 1.

```yaml
task_id: ZC-V4-P4-A2-DYNAMIC-LIFECYCLE
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v4-p4-20261107
base_sha: 36699b20701664b6f65a42fd8f6fb18558ec513a
observed_head_sha: 36699b20701664b6f65a42fd8f6fb18558ec513a
status: CI_PENDING
acceptance_source: User said "Dalej" after P4-A1 CODE_VERIFIED and accepted the 12-stage P4 plan. P4-A2 tests dynamic AJAX/SPA DOM changes, queued actions, partial hydration and navigation. No APK authorized.
completed: Added actual production WebExtension content.js lifecycle regression coverage for AJAX hydration, recycled DOM cards after ACK, coalesced MutationObserver notifications, same-origin SPA route revocation, unannounced route switch while scroll is queued, pagehide cancelling queued mutation work and login gate arriving after ACK. Added real recognition.js+content.js tests for disabled/detached search controls and late-result hydration. Updated only synthetic JS test fixtures and error-learning/checkpoint. No shipping JS, Kotlin, Room, manifest, permissions, signing, profiles or sessions changed.
remaining: Complete exact-source SOURCE process-docs, JS, Kotlin/Robolectric, AndroidTest Kotlin compilation, lint baseline and final required summary; verify all PR gates at matching final SHA, register and fix any CI failure. Next P4-B1 is more intelligent field/button recognition, authorized separately by "Dalej".
required_ci: SOURCE process-docs and shared-tests JS WebExtension, Kotlin/Robolectric, AndroidTest Kotlin compile, Android lint regression plus evidence summary on final HEAD; stacked PR JS/Kotlin/AndroidTest/lint and error-learning evidence on identical PR HEAD.
device: NOT_TESTED — no actual Nothing Phone (1), GeckoView or live third-party site tests.
apk: NOT_BUILT_FOR_P4 — previous verified P3-E v1224 APK preserved, no signature/Secrets changes or P4 private APK authorization.
known_failures: At initial P4-A2 commit zero observed CI errors; reconcile against exact run IDs before reporting final status. Prior P4-A1 8 failures recorded FIXED in ERROR_CASES.json.
verification: At pre-write SHA 36699b20701664b6f65a42fd8f6fb18558ec513a, verified remote branch, open Draft PR #128, matching P4-A1 head and relevant AGENTS.md, docs/AI_HANDOFF.md, STARTUP_PREFLIGHT, TASK_TEMPLATE, DEVELOPMENT_WORKFLOW, TEST_POLICY, TEST_PATTERNS, QUALITY_GATES, ERROR_LEARNING, CHECKPOINT_POLICY, EXCEPTIONS_AND_TOOLS and P3-E real-site/device acceptance. Read relevant production content.js/recognition.js and existing P14/P3E/P11/P2C regressions. No local checkout, Gradle, Android device or real web sites; full tests via GitHub Actions.
changed_paths:
  - app/src/test/js/video-search-p14-spa-rebind.test.cjs
  - app/src/test/js/video-search-p3e-integration.test.cjs
  - docs/AI_HANDOFF.md
  - docs/ai/ERROR_CASES.json
verified_before_push:
  - Existing production files unchanged; no new permissions, side effects, migrations, cert, profiles or session reset.
  - Synthetic MutationObserver and cancellable timer queue preserve native protocol and test real content.js rather than duplicated algorithms.
  - Negative assertions for stale route tokens, detached/disabled submit controls, false completion, unsolicited clicks and protected login gates.
  - Existing P3-E integration and P4-A1 regression coverage preserved.
blockers:
  - Real-device and real-site acceptance E01-E18 is NOT_TESTED.
  - Independent review unavailable; self-review plus mandatory CI only.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/128
  - https://github.com/laszko14/nira-browser/actions/runs/42348993221
  - https://github.com/laszko14/nira-browser/actions/runs/42348996814
next_action: Complete SOURCE and PR CI on exact final P4-A2 SHA; reconcile observed errors, fix failures, verify branch=PR=run SHA, STOP. Next user "Dalej" begins P4-B1 smarter field/button recognition. No APK unless separately authorized.
```
