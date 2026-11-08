# AI_HANDOFF — Zavro Clean V4 / P4-D2 episode-aware smart grouping

> Historical archive: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md. This checkpoint is a pre-commit snapshot; current remote branch HEAD overrides observed_head_sha. P4-D1 CODE_VERIFIED on 300f8bc4a1ebf258ff91629502ef7a2b0235edc0, SOURCE #42480650819 and PR #42480654191 all SUCCESS.

```yaml
task_id: ZC-V4-P4-D2-EPISODE-GROUPING
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v4-p4-20261107
base_sha: 300f8bc4a1ebf258ff91629502ef7a2b0235edc0
observed_head_sha: 300f8bc4a1ebf258ff91629502ef7a2b0235edc0
status: CI_PENDING
acceptance_source: User wrote "Dalej" after verified P4-D1; next approved step P4-D2 intelligent grouping with strict protection against different seasons and episodes. No APK or merge.
completed: Bounded canonical normalization for explicit S02E03, 2x03, Season 2 Episode 3, Sezon 2 Odcinek 3, Episode 3, Part 3 and Część 3. Smart grouping alone uses canonical episode/part identity and episode-scoped exact/fuzzy buckets. Preserves exact titles, URLs, attribution, ordering, run/profile isolation and both permissive legacy OR toggles. Seven Kotlin JVM regression tests including sixty episode pairs.
remaining: Verify SOURCE process-docs and shared-tests JS/Kotlin/Robolectric/AndroidTest Kotlin compile/lint and final evidence summary; verify stacked PR JS/Kotlin/AndroidTest/lint/error evidence all SUCCESS on final SHA. Triage and register each observed CI failure. Next P4-D3 only on separate "Dalej".
required_ci: SOURCE process-docs and shared-tests JS WebExtension, Kotlin/Robolectric JVM, AndroidTest Kotlin compile, Android lint regression baseline, final evidence summary all SUCCESS on final SHA; PR JS/Kotlin/AndroidTest/lint and error-learning evidence all SUCCESS on same head SHA.
device: NOT_TESTED — no physical Nothing Phone (1), GeckoView or real-site testing.
apk: NOT_BUILT_FOR_P4 — no P4 APK signing authorized.
known_failures: Zero observed P4-D2 failures before push. New CI failures must be reconciled using evidence.
verification: Confirmed live PR #128 head 300f8bc4a1ebf258ff91629502ef7a2b0235edc0, correct branch, open unmerged Draft; read AGENTS.md, docs/AI_HANDOFF.md, scoped preflight/test/quality/error/development policies at SHA. Examined actual Kotlin smart and legacy grouping and JVM tests. No local app checkout or physical device tests.
changed_paths:
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchResultsMerge.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchResultsMergeTest.kt
  - docs/AI_HANDOFF.md
  - docs/ai/ERROR_CASES.json
verified_before_push:
  - Only opt-in "Scalaj inteligentnie" behavior changes; existing "po nazwie" and "po długości" stay unchanged.
  - Explicit different season, episode or part identities do not merge even if durations match; unknown season is not a known season.
  - All original film URLs, titles, profile/run ownership and website attribution stay intact.
  - No Room, native protocol, permission, package, ABI, certificate, private session, network or navigation modifications.
blockers:
  - Real-site and device verification NOT_TESTED.
  - Independent high-risk review unavailable; self-review plus CI are not independent risk signoff.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/128
  - https://github.com/laszko14/nira-browser/actions/runs/42480650819
  - https://github.com/laszko14/nira-browser/actions/runs/42480654191
next_action: Inspect final CI at exact source SHA, fix only proven defects and reconcile failures; stop at P4-D2. Next stage requires a new "Dalej" and no implicit APK.
```
