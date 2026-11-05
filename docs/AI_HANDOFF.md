# AI_HANDOFF — Zavro Clean V4 / P2-G2 context-aware recognition
> History: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md. P2-G1 completed at 7a85b7be59ee689d5b5bdbacbdb44bf93f9a975a; SOURCE 42086762179 / PR 42086777014 SUCCESS (attempt 1). P2-G3 remains separate.

```yaml
task_id: ZC-V4-P2-G2-CONTEXT-AND-BOUNDED-SCANS
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-v4-p2-evidence-runtime-20261103
base_sha: 7a85b7be59ee689d5b5bdbacbdb44bf93f9a975a
observed_head_sha: 7a85b7be59ee689d5b5bdbacbdb44bf93f9a975a
status: CI_PENDING
acceptance_source: User said "dalej" after verified three-level P2-G plan and completed P2-G1; implement P2-G2 intelligent recognition, DOM context-change precision, AJAX resilience and bounded scanning. Do not merge or build APK.
completed: P2-G2 code ef5d0e84e984d46def534f8b9148fa477ecfebad changes only recognition.js/content.js; exact accepted root/selector tracking in the isolated script prevents unrelated Shadow/frame changes from revoking known healthy results, while replaced active root still fails closed. Existing structural scoring, native status protocol, fallback guards and zcs1 identity remain unchanged. Legacy card discovery uses at most 2400 TreeWalker DOM visits and 260 matching candidates instead of unbounded querySelectorAll allocation when TreeWalker is available. Analyze reuses one scored input list and one bounded result discovery per request. Seven new JS tests in cb45e181cb99b7ad9f4adc64136cc89ab0b6ffbf cover irrelevant widget swap with scoped/main result roots, active-host revocation, AJAX card reorder, one-scan behavior, results-only form and bounded fallback. No new UI, Room, storage, network, profile, workflow, MPV or signed artifact.
remaining: Final SOURCE and PR CI on pushed exact SHA; investigate any failing jobs, update ERROR_CASES for actual new CI failures and repair. P2-G3 final end-to-end verification is a separate next user level; device/real third-party GeckoView NOT_TESTED.
required_ci: SOURCE process-docs, JavaScript WebExtension, JVM/Robolectric, Android instrumentation test compilation, Android lint and baseline, source checkout/evidence summary all SUCCESS on exact final SHA; PR integration JS/JVM/AndroidTest compile/lint/evidence all SUCCESS. Never treat skipped APK jobs as failure in tests-only workflows.
device: NOT_TESTED
apk: NOT_BUILT (explicit three-level implementation without APK).
known_failures: Three distinct P2-G2 review defects recorded in docs/ai/ERROR_CASES.json with tests and code fix SHA ef5d0e84e984d46def534f8b9148fa477ecfebad. No CI failure observed prior to push.
verification: Preflight branch/ref PR #123 live 7a85b7be59ee689d5b5bdbacbdb44bf93f9a975a; root AGENTS.md, docs/AI_HANDOFF.md, STARTUP_PREFLIGHT.md, TASK_TEMPLATE.md, TEST_POLICY.md, QUALITY_GATES.md, ERROR_LEARNING.md, EXCEPTIONS_AND_TOOLS.md, DEVELOPMENT_WORKFLOW.md, CHECKPOINT_POLICY.md read at identical pinned HEAD. GitHub compare/diff reviewed; 4 changed app/test files, no nested AGENTS.md. V8 syntax checked for four JavaScript files, historical RED and local Gradle/ADB NOT_TESTED. CI must supply behavior evidence.
changed_paths:
  - app/src/main/assets/extensions/zavro-video-search/recognition.js
  - app/src/main/assets/extensions/zavro-video-search/content.js
  - app/src/test/js/video-search-p1-recognition.test.cjs
  - app/src/test/js/video-search-structural-v4.test.cjs
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
verified_before_push:
  - Live GitHub HEAD and draft PR #123 match checkpoint base 7a85b7be59ee689d5b5bdbacbdb44bf93f9a975a; commit ancestry linear ef5d0e84e984d46def534f8b9148fa477ecfebad -> cb45e181cb99b7ad9f4adc64136cc89ab0b6ffbf.
  - New tests preserve P2-F host/replaced-frame protection and P2-G1 manual selector contract.
  - Existing safety guards against sponsored/category, low confidence, stale selectors and unsafe forms remain unchanged.
  - All new diagnostics remain local, read-only and bounded; no raw user query, URLs or DOM sent to native.
blockers:
  - Independent lifecycle/privacy code review unavailable; self-review and CI cannot replace independent risk acceptance.
  - Real device GeckoView, third-party site timing and cross-origin/closed Shadow behavior NOT_TESTED.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/123
  - https://github.com/laszko14/nira-browser/commit/ef5d0e84e984d46def534f8b9148fa477ecfebad
  - https://github.com/laszko14/nira-browser/commit/cb45e181cb99b7ad9f4adc64136cc89ab0b6ffbf
  - https://github.com/laszko14/nira-browser/actions/runs/42086762179
  - https://github.com/laszko14/nira-browser/actions/runs/42086777014
security: Fail-closed replacement of selected Shadow/iframe result contexts; unrelated widget changes can no longer invalidate an already verified context. No clicks, network actions or persistence added.
next_action: Verify final SOURCE and PR CI on final pushed P2-G2 SHA; triage failures. Stop after P2-G2; await next "dalej" to begin P2-G3.
```
