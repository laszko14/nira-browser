# AI_HANDOFF — Zavro Clean · v2.0 search redesign

> Historia wcześniejszych przekazań: [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md).

```yaml
task_id: ZAVRO-CLEAN-SEARCH-REDESIGN-V2-20261101
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-search-redesign-v2-20261101
base_sha: de8d893731471bd9ac78e4c6006ab34d567eef0d
observed_head_sha: de8d893731471bd9ac78e4c6006ab34d567eef0d
status: CI_PENDING
implementation_status: UI implementation and CI v2.0 migration in progress
acceptance_source: User request 2026-11-01 to migrate v1206 to CI v2.0 and redesign My sites and video search results from the attached portrait reference with a separate landscape adaptation
required_ci: zavro-ai-process-docs (process-docs, four shared-tests jobs, quality-evidence); Android CI pull_request (four shared regression jobs); exact-SHA owner review for private APK
device: DEVICE_NOT_TESTED; adb is unavailable in this environment
apk: pending green final-SHA CI and exact-SHA authenticated PR review; expected com.prirai.android.nira.debug, arm64-v8a, versionCode 1207, versionName 0.5.22-alpha112-clean, signing certificate retained
changed_paths:
  - AGENTS.md and v2.0 instruction set
  - CI workflows and their regression tests
  - MyVideoSitesActivity and its layout tests
  - VideoSearchResultsScreen and UI state tests
verified_before_push:
  - Local starting checkout was clean and moved to a task branch from v1206
  - GitHub connector confirmed source commit de8d893731471bd9ac78e4c6006ab34d567eef0d
  - v1206 and v2.0 instruction branch share ancestor e496e141f97045f1a9d315e349a1666277547569
  - Original v1206 owner-review APK trigger is retained alongside v2.0 dispatch guards
  - Baseline handoff contract test failed because acceptance_source was missing
  - Local Gradle bootstrap could not download Gradle 9.7.1; adb is unavailable
blockers:
  - Android unit/UI tests require GitHub CI: Gradle 9.7.1 is cached, but the required JetBrains JDK 21 cannot be downloaded from Foojay in this environment
  - Required GitHub CI and authenticated private APK review remain to be run on final SHA
next_action: Finish UI implementation and local JavaScript/process gates, commit and push a PR without merging, verify all required jobs on the final SHA, then use the exact authorized review trigger for one verified private APK
evidence_links:
  - https://github.com/laszko14/nira-browser/commit/de8d893731471bd9ac78e4c6006ab34d567eef0d
  - https://github.com/laszko14/nira-browser/tree/7a60fbcb8463afca3526675a8d47dd28ce89b9be
```
