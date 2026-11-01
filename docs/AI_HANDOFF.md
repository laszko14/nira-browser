# AI_HANDOFF — Zavro Clean / inteligentne scalanie wyników v1202

```yaml
task_id: VIDEO-RESULTS-FUZZY-V1202
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-video-results-fuzzy-v1202-20261101
base_sha: 659a2a469687b86e8ba778fa7e80de33a2c38cc5
observed_head_sha: 659a2a469687b86e8ba778fa7e80de33a2c38cc5
status: CI_PENDING
acceptance_source: User asks for percent-like fuzzy comparison of film titles and duration, not only punctuation stripping, and a signed update APK.
change_scope: Fuzzy character and word similarity with duration evidence; 95% strong title match, 85% with up to 2s and 75% with up to 1s and strong shared word core. Protect episode numbers. Preserve original strict duration and OR behavior of the two existing switches. Presentation-only with no Room changes.
required_ci: .github/workflows/zavro-video-results-fuzzy-v1202-update.yml; authorized-source, shared javascript, JVM/Robolectric, androidTest compile, lint, signed-update, verify-all.
device: V1201_USER_REPORTED_DUPLICATES; V1202_NOT_TESTED
apk: V1202_CI_PENDING; arm64-v8a versionCode 1202 versionName 0.5.22-alpha107-clean com.prirai.android.nira.debug same certificate
changed_paths:
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchResultsMerge.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchResultsMergeTest.kt
  - app/build.gradle
  - .github/workflows/apk-release.yml
  - app/src/test/js/release-contract-stage19a.test.cjs
  - app/src/test/js/update-apk-workflow-scope.test.cjs
  - .github/workflows/zavro-video-results-fuzzy-v1202-update.yml
  - docs/AI_HANDOFF.md
safety:
  - No main merge/force-push, no MPV, profile/session changes or database migrations.
  - Signed APK only from CI GitHub Secrets, unchanged certificate, appId, arm64.
  - Do not claim device verified before user installation.
next_action: Check all required CI jobs and the verified signed artifact against final SHA. Fix test failures on same task only, avoid pointless repeated analysis.
```

## Wznowienie
Jedno zadanie 1/1. Odczytaj HEAD/AGENTS.md/checkpoint na konkretnym SHA. Sprawdź GitHub Actions i artefakt do aktualizacji, bez publikacji i bez instalacji na urządzeniu.
