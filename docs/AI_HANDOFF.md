# AI_HANDOFF — Zavro Clean · results toolbar viewport v1206

```yaml
task_id: VIDEO_RESULTS_HEADER_VIEWPORT_V1206_20261101
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-video-results-header-v1206-20261101
base_sha: c4c1cba7ef1f51d11fc98cee8e69cdb78abcfa53
observed_head_sha: c4c1cba7ef1f51d11fc98cee8e69cdb78abcfa53
status: CI_PENDING
source_version: 0.5.22-alpha110-clean / arm64 versionCode 1205
target_version: 0.5.22-alpha111-clean / arm64 versionCode 1206
device: NOT_TESTED
apk: NOT_BUILT
scope:
  - Restore visible and clickable results header and actions underneath browser chrome.
  - Reserve the same viewport margins for results ComposeView as for GeckoView.
  - Keep toolbar animation translation synchronized with Gecko's viewport.
  - Preserve results state, filters, selection, scroll, profiles and signed update identity.
tests:
  - Robolectric WebContentPositionManagerTest: top, collapsed, bottom chrome, fullscreen restoration
  - Required JavaScript/Kotlin/lint/AndroidTest compile on final SHA via Android CI
limitations:
  - No connected physical device; DEVICE_NOT_TESTED.
  - Existing review-triggered private APK flow requires live validation and authenticated owner review after green CI.
  - Changes are based on the v1205 APK-review branch (AGENTS v1.6); V2.0 migration is not automatically inherited.
next_action: Confirm all required CI gates on final HEAD, then if authorized invoke the reviewed PR command and verify signed APK; otherwise APK_BLOCKED.
```

No merge to main, force-push, public release, data deletion or signing-key changes.
Checkpoint is pre-commit by design; consult live ref and CI for final SHA and run.
