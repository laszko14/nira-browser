# AI_HANDOFF — Zavro Clean / inteligentne scalanie wyników

```yaml
task_id: VIDEO-RESULTS-SMART-MERGE-20261101
workflow_mode: staged
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-video-results-smart-merge-20261101
base_sha: e9d1f76d085cdff8432d92a880d6df76adcd3f7d
observed_head_sha: e9d1f76d085cdff8432d92a880d6df76adcd3f7d
status: CI_PENDING
acceptance_source: Screenshots show HD 4K duration prefixes, apostrophe and hyphen variants, and nearby (one-second) durations remaining in separate result clusters.
change_scope: Presentation-only normalization of leading HD/4K/duration title tags and punctuation; duration tolerance one second only if names normalize identically; original exact-duration behavior retained; no Room/storage changes.
required_ci: .github/workflows/zavro-video-results-smart-merge-tests.yml; scope, shared JavaScript, shared JVM and Robolectric, AndroidTest compile, lint regression, verify-all.
device: V1201_USER_REPORTED_DUPLICATES; SMART_MERGE_NOT_TESTED
apk: NOT_BUILT; building a signed update requires separate explicit user command (Kompiluj)
changed_paths:
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchResultsMerge.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchResultsMergeTest.kt
  - .github/workflows/zavro-video-results-smart-merge-tests.yml
  - docs/AI_HANDOFF.md
constraints:
  - Do not merge to main or modify signing/package/profile data.
  - Do not build installation APK automatically.
  - Do not update pilot metrics without adequate evidence and scope approval.
next_action: Inspect complete green CI on actual final SHA; if failures, fix and retest before declaring CODE_VERIFIED. Next APK only after separate authorization; test on Nothing Phone (1) afterwards.
```
