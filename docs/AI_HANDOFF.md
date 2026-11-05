# AI_HANDOFF — Zavro Clean V4 / P2-G1 scoped manual picker
> History: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md. Previous P2-F completed at 7059e011a5d7af0de88ce835302ebbccb40cec05; SOURCE 42074787626 / PR 42074798942 SUCCESS (attempt 1). Remaining P2-G2/G3 work is separate.

```yaml
task_id: ZC-V4-P2-G1-SCOPED-MANUAL-PICKER
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-v4-p2-evidence-runtime-20261103
base_sha: 7059e011a5d7af0de88ce835302ebbccb40cec05
observed_head_sha: 7059e011a5d7af0de88ce835302ebbccb40cec05
status: CI_PENDING
acceptance_source: User requested P2-G in three thorough levels and approved implementation starting with level 1. G1 handles scoped/manual DOM selection; G2 recognition accuracy/performance and context invalidation; G3 end-to-end search/pagination/persistence/security. No APK or merge in G1.
completed: P2-G1 prepared code 79aef695fb44b0e0a3262c4917586ca99f944cfd for shared zcs1 picker resolution, genuine touched-node membership for grouped film cards, scoped ShadowRoot group selectors, and prevention of stale/swap host confirmation. 6 new JS regression tests added across two existing test suites, including open Shadow input, submit, cards, next link, swapped root and unrelated CSS group. Previous automatic recognition remains compatible. No changes to Room, signing, profiles, MPV, network permissions or workflows.
remaining: Verify final SOURCE and PR CI at the pushed SHA; triage any failing checks. P2-G2 and P2-G3 intentionally not implemented in this level. Independent GeckoView real-device validation remains NOT_TESTED.
required_ci: SOURCE process-docs and shared-tests JavaScript, JVM/Robolectric, AndroidTest compile, Android lint baseline and evidence summary SUCCESS on exact final SHA; separate PR integration CI JavaScript/JVM/AndroidTest/lint/evidence SUCCESS. Build/release SKIPPED permitted; APK prohibited.
device: NOT_TESTED
apk: NOT_BUILT; no signing workflow requested or authorized.
known_failures: Three P2-G1 review findings recorded as ERR-20261105-P2G1-0001 through 0003, with source SHA 7059e011a5d7af0de88ce835302ebbccb40cec05, code fix commit 79aef695fb44b0e0a3262c4917586ca99f944cfd and regression paths. SOURCE 42086427757 process-docs failed at check-ai-handoff.test.cjs because this checkpoint omitted the required archive pointer; PR JS and SOURCE JS 476/476 PASS. Archive reference restored without weakening validator. Final SOURCE and PR CI must verify the fix.
verification: GitHub PR #123 confirmed open/draft at initial source HEAD 7059e011a5d7af0de88ce835302ebbccb40cec05; AGENTS.md, docs/AI_HANDOFF.md, docs/ai/STARTUP_PREFLIGHT.md, TEST_POLICY.md, TEST_PATTERNS.md, QUALITY_GATES.md, DEVELOPMENT_WORKFLOW.md, CHECKPOINT_POLICY.md, ERROR_LEARNING.md, TASK_TEMPLATE.md and EXCEPTIONS_AND_TOOLS.md fetched from this explicit SHA. JavaScript source/test syntax checked in V8 before creating prepared Git objects. Historical RED execution and local JDK/ADB tests NOT_TESTED. First SOURCE and PR JavaScript suites 476/476 PASS on f2734ed109853b357213816aa9bfa22ae2e9e22a; SOURCE process-docs reported one missing archive-pointer assertion. Final GitHub Actions must reverify.
changed_paths:
  - app/src/main/assets/extensions/zavro-video-search/recognition.js
  - app/src/main/assets/extensions/zavro-video-search/content.js
  - app/src/test/js/video-search-picker-v2.test.cjs
  - app/src/test/js/video-search-p1-recognition.test.cjs
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
verified_before_push:
  - Scope restricted to manual picker, shared locator identity and behavior regressions for P2-G1.
  - Source tree and two prepared commits have linear ancestry; no merge/force-push.
  - Open Shadow inputs, buttons, groups and next controls use zcs1 through the existing resolver; iframe manual picking remains unsupported from top-level content script, not silently enabled.
  - Document main picker rules retained; profile, session, persistence and signing paths unchanged.
  - New regression scenarios added without weakening existing assertions or lint.
blockers:
  - Independent security/lifecycle reviewer not yet performed; own review cannot substitute for independent acceptance.
  - Physical Android/GeckoView, real third-party site selection and iframe click retargeting remain NOT_TESTED.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/123
  - https://github.com/laszko14/nira-browser/commit/79aef695fb44b0e0a3262c4917586ca99f944cfd
  - https://github.com/laszko14/nira-browser/actions/runs/42074787626
  - https://github.com/laszko14/nira-browser/actions/runs/42074798942
security: The picker does not submit searches, follow links, or persist raw DOM. It binds user-selected nodes to the same DOM context via a bounded zcs1 locator, and rejects stale contexts before sending selectors to native.
next_action: Verify SOURCE and PR CI on final pushed P2-G1 SHA, record and fix any failures. Stop after P2-G1 success; next user instruction can start P2-G2. Never build APK in this task.
```
