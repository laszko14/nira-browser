# AI_HANDOFF — Zavro Clean / scalanie wyników filmów v1201

```yaml
task_id: VIDEO-RESULTS-MERGE-V1201
workflow_mode: staged
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-video-results-merge-v1201-20261101
base_sha: ec6584e07390f687e246cd8d25c934ba0b784123
observed_head_sha: 19371548880629e16cfd62ab117b6d78ef285d76
status: CI_PENDING
acceptance_source: Dwa przełączniki scalania wg nazwy i czasu trwania. Gdy włączone oba, logika LUB, także przechodnia. Jeden kafelek/listowy wynik na grupę, wybór konkretnego filmu i witryny, zachowanie oryginalnych linków i historii.
required_ci: .github/workflows/zavro-video-results-v1201-update.yml authorized-source shared-javascript shared-unit shared-lint shared-androidtest-compile signed-update verify-all
device: V1200_USER_VERIFIED; V1201_NOT_TESTED
apk: V1201_CI_PENDING; arm64-v8a versionCode 1201 versionName 0.5.22-alpha106-clean com.prirai.android.nira.debug stable certificate
changed_paths:
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchResultsActivity.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchResultsTabUiMemory.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchResultsMerge.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchResultsTabUiMemoryTest.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchResultsMergeTest.kt
  - app/build.gradle
  - .github/workflows/apk-release.yml
  - app/src/test/js/release-contract-stage19a.test.cjs
  - app/src/test/js/update-apk-workflow-scope.test.cjs
  - .github/workflows/zavro-video-results-v1201-update.yml
  - docs/AI_HANDOFF.md
verified_before_push:
  - AGENTS.md and previous checkpoint read at explicit SHA ec6584e07390f687e246cd8d25c934ba0b784123; original branch HEAD confirmed
  - Pure UI merger reads only validated normal-profile run results; no DB migration, deletion or private exposure
  - Full merged result set paginated by group, with per-tab/run/profile toggles retained on film navigation
  - No merge to main, no force-push, unchanged package, certificate expectation and ABI
blockers:
  - Need full CI completed/success on final SHA including exact signed update APK
  - Device test of v1201 not yet performed
next_action: Inspect last workflow run and artifact; fix CI if needed and verify final SHA before claiming completion; no installation on device.
```

## Wznowienie
ETAP 1/1. Sprawdź żywy HEAD gałęzi powyżej, AGENTS.md, ten checkpoint na jawnym SHA, wymagane joby CI oraz artefakt aktualizacji. Nie zmieniaj linii x MPV i nie instaluj na telefonie.
