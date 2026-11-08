# AI_HANDOFF — Zavro Clean V4 / P4-E2 merged bulk selection

> Historical archive: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md. Pre-commit snapshot only; live GitHub HEAD overrides observed_head_sha. Previous P4-E1 CODE_VERIFIED at f7fb266ccb06264ca4b6847da6deeefc3b43550c; SOURCE #42488971704 and PR #42488976490 SUCCESS.

```yaml
task_id: ZC-V4-P4-E2-BULK-MERGED-SELECTION
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v4-p4-20261107
base_sha: f7fb266ccb06264ca4b6847da6deeefc3b43550c
observed_head_sha: f7fb266ccb06264ca4b6847da6deeefc3b43550c
status: CI_PENDING
acceptance_source: User "dalej" after completed P4-E1. Perform P4-E2 selecting films and non-destructive bulk actions across merged groups, without APK, merge or unrelated changes.
completed: Fixed merged film-card selection in both tile and list/mini-list. Tapping, checkbox and long press in selection mode toggle the whole original merged cluster rather than its primary film; partially selected cluster becomes fully selected before another click deselects it. Group selection checks run, profile, site, exact saved URL and duplicates against full snapshot and fails closed on stale/foreign cards. UI displays original group count and selected-member count. Website rows use long-press-to-toggle only during selection. Existing 11 bulk actions, group ownership, film URLs, scroll/filter state and source attribution remain unchanged. Five new JVM tests including 370 merged items beyond the first 150 and duplicate URLs from different sites.
remaining: Verify exact final-SHA SOURCE process-docs and shared-tests JS/Kotlin/Robolectric/AndroidTest Kotlin compile/lint with summary; verify PR JS/Kotlin/AndroidTest/lint/error-learning evidence, repair and register any confirmed failures. Next stage only after fresh user instruction.
required_ci: SOURCE process-docs and shared-tests JavaScript WebExtension, Kotlin/Robolectric JVM, AndroidTest Kotlin compilation, Android lint regression baseline and required evidence summary all SUCCESS on exact final SHA; PR shared-tests JS/Kotlin/AndroidTest/lint and error-learning evidence SUCCESS on identical head SHA.
device: NOT_TESTED — no physical Nothing Phone (1), live GeckoView selection/long-press or actual external site.
apk: NOT_BUILT_FOR_P4 — no APK authorized; no signing.
known_failures: No P4-E2 CI failures observed before push; record actual CI findings without assumptions.
verification: Live GitHub Draft PR #128, branch feat/zavro-clean-v4-p4-20261107, SHA f7fb266ccb06264ca4b6847da6deeefc3b43550c verified; AGENTS.md and docs/AI_HANDOFF.md at exact SHA and scoped preflight, quality, test, checkpoint and error-policy docs read. Reviewed full native bulk selection/actions/group profile checks and list/grid click handlers. No local checkout or device build.
changed_paths:
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchBulkSelection.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchResultsActivity.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchBulkSelectionTest.kt
  - docs/AI_HANDOFF.md
  - docs/ai/ERROR_CASES.json
verified_before_push:
  - No Room migration, native protocol, site recognition, new permissions, signing, package, or network changes.
  - Selected IDs remain per result tab, run and profile; original saved URLs and all films remain intact.
  - Selection-mode long press cannot open the film action menu or merged picker accidentally.
blockers:
  - Device and real-site behavior NOT_TESTED.
  - Independent high-risk review unavailable; self-review plus CI do not establish device usability.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/128
  - https://github.com/laszko14/nira-browser/actions/runs/42488971704
  - https://github.com/laszko14/nira-browser/actions/runs/42488976490
next_action: Verify full SOURCE and PR CI on new final SHA; reconcile real failures, then STOP at P4-E2. Do not start another stage or APK without new user request.
```
