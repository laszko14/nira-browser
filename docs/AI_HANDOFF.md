# AI_HANDOFF — Zavro Clean / video search UI state v1200
> Historia: [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md).

```yaml
task_id: VIDEO-RESULTS-STATE-V1200
workflow_mode: staged
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-video-results-state-v1200-20261101
base_sha: c6df406cb630660d26ef9d2a105d22768b1f7ad3
observed_head_sha: c6df406cb630660d26ef9d2a105d22768b1f7ad3
status: CI_PENDING
acceptance_source: preserve flat grouping, duration sort, filter, expanded sites, loaded result pages and scroll when a film opens and browser returns to results; user authorized updated signed APK
required_ci: .github/workflows/zavro-video-results-v1200-update.yml authorized-source shared-js shared-jvm shared-lint shared-androidtest-compile signed-update verify-all
device: V1199 user feedback; V1200_NOT_TESTED
apk: V1200_NOT_BUILT; arm64-v8a versionCode 1200 versionName 0.5.22-alpha105-clean com.prirai.android.nira.debug stable cert
changed_paths:
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchResultsActivity.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchResultsTabContent.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchResultsTabUiMemory.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchResultsTabUiMemoryTest.kt
  - app/build.gradle
  - .github/workflows/apk-release.yml
  - app/src/test/js/release-contract-stage19a.test.cjs
  - app/src/test/js/update-apk-workflow-scope.test.cjs
  - .github/workflows/zavro-video-results-v1200-update.yml
  - docs/AI_HANDOFF.md
verified_before_push:
  - Read active HEAD c6df406cb630660d26ef9d2a105d22768b1f7ad3 and AGENTS.md at explicit SHA
  - Sort/group/scroll state lost on Compose unmount when film tab becomes selected
  - Per-tab/profile/run bounded RAM UI state, no film URLs, results or private storage
  - Same package, arm64 ABI and signature checked on real APK
blockers:
  - Require complete green CI on final SHA and signed APK verification
  - User device validation of navigation/scroll after installation
next_action: Inspect v1200 workflow run and exact artifact checksum; code and device checks separate; no merge/install.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41273039027
```

## Wznowienie
ETAP 1/1. Odczytaj AGENTS.md i ten checkpoint z jawnego SHA, zweryfikuj żywy HEAD. Napraw błędy CI i zweryfikuj podpisany APK bez zmieniania linii x MPV, publikacji i danych telefonu.
