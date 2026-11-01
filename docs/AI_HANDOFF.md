# AI_HANDOFF — Zavro Clean / bulk selection of video-search results

```yaml
task_id: VIDEO-SEARCH-BULK-V1205-20261101
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-video-results-bulk-v1205-20261101
base_sha: 85317ed642942ddcb7416825ab3bd16cfb3b881c
observed_pre_handoff_sha: 3a22af491a8f015b55501128d967a9ffa526ce3a
source_version: 0.5.22-alpha109-clean / arm64 versionCode 1204
target_version: 0.5.22-alpha110-clean / arm64 versionCode 1205
status: CI_PENDING
local_android_tests: NOT_TESTED (no Gradle network toolchain available)
device: NOT_TESTED
apk: NOT_BUILT
scope:
  - Select arbitrary film counts in tile/list views; stable across sorts, filters, first 150, tab navigation and merged result variations.
  - Single- or multi-site group opening; existing and active groups; ungrouped and background browser tabs.
  - Save to existing/new video library folders, copy/share exact links, add bookmarks in default profile.
  - No deleting results; no persistent data wipe; no MPV; protect profiles and the fixed signing certificate.
  - Add JVM tests for 365+ selections, exact attribution, invert, stale owner and per-tab isolation.
  - Bump version and APK verification metadata before CI.
known_limits:
  - Legacy BookmarkManager is global, so bulk bookmarks are disabled outside default profile.
  - A large interrupted batch can partially open tabs or save links; report partial count and do not destroy new tabs.
  - Legacy v1204 branch AGENTS v1.6; do not claim automatic v2.0 CI migration.
  - GitHub connector alone cannot dispatch APK; authenticated browser operator may be attempted after green CI.
verification:
  - GitHub Actions required JS/JVM/AndroidTest compile/lint must pass on the final SHA.
  - Explicit private debug-apk dispatch on same branch/commit; verify certificate/package/version/ABI in runner output.
next_action: Open Draft PR to feat/zavro-clean-video-search-ui-date-v1203-20261101 and inspect CI failures; fix only supported causes, re-run on final SHA. If green, dispatch signed private APK and verify before reporting completion.
```

This checkpoint is from before the final CI run. The live branch ref and CI must be checked after push. Do not turn CI_PENDING into PASS without GitHub Actions evidence. No merge, release, force-push, uninstall or data reset.
