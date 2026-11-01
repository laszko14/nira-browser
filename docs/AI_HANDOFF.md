# AI_HANDOFF — Zavro Clean / scalanie wyników v1203

```yaml
task_id: VIDEO-RESULTS-MERGE-V1203
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-video-results-merge-v1203-20261101
base_sha: 5a8098e52a215c67940cb201a24326a6165755a0
observed_head_sha: 5a8098e52a215c67940cb201a24326a6165755a0
status: CI_PENDING
acceptance_source: Screenshot user 2026-11-01: 24:35/24:34, 27:13/27:12 and 32:05/32:04 remain separate despite matching titles.
change_scope: Strip view-count prefixes only before timestamps; always compare nearby candidates even for an exact-key repeat; retain earliest candidates in bounded fuzzy buckets. Regression: three real screenshot pairs, 2+2 clusters, view-count genuine-title guard, unrelated near-duration control.
required_ci: .github/workflows/apk-release.yml workflow_dispatch tests-only then authorized private debug-apk from same final SHA; JavaScript, JVM/Robolectric, AndroidTest compile, lint regression; signed update verification.
apk: TARGET versionCode 1203, versionName 0.5.22-alpha108-clean, arm64-v8a, com.prirai.android.nira.debug; fixed certificate.
device: NOT_TESTED; install on user's Nothing Phone (1) requires user action.
safety: No merge to main, public release, force-push, database migration, changes to profiles/sessions/signature.
changed_paths:
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchResultsMerge.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchResultsMergeTest.kt
  - app/build.gradle
  - .github/workflows/apk-release.yml
  - app/src/test/js/release-contract-stage19a.test.cjs
  - app/src/test/js/update-apk-workflow-scope.test.cjs
  - docs/AI_HANDOFF.md
next_action: Verify required CI and signed private APK artifact on final commit SHA; fix failures in this one task only.
```

## Wznowienie
Zawsze odczytaj bieżący HEAD gałęzi i AGENTS.md z tego samego SHA. To zadanie jednej poprawki 1/1; nie uznawaj CI_PENDING za PASS i nie przypisuj sobie testu telefonu.
