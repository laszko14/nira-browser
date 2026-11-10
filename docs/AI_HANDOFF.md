# AI_HANDOFF — Zavro Clean stage 6/10 (GeckoView and WebExtension lifecycle)

Archiwum dawnego handoffu: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`.
Checkpoint przed końcowym CI. Live HEAD i workflow na końcowym SHA są autorytatywne.

```yaml
task_id: ZC-PREVIEW-COMPAT-6
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-preview-mediasession-stage6-20261110
base_sha: 7bee6c61b8c1a6235a3c6e00f4db189393bb4799
observed_head_sha: 7bee6c61b8c1a6235a3c6e00f4db189393bb4799
status: CI_PENDING
acceptance_source: User DALEJ — stage 6/10 audit and repair confirmed GeckoView MediaSession/WebExtension lifecycle risks; preserve profiles and media; no APK or merge.
completed: Stage5 HEAD and PR #134 confirmed with exact SOURCE 42778905706 and PR 42778931733 SUCCESS. Read AGENTS/handoff and V3 policies plus MediaSession, GeckoView, native background playback, shadow hook and profile binding at live SHA. Found missing native bridge after reused extension at cold start, and stale callback may capture active port after reattachment. Added scoped fixes and real Kotlin/Robolectric tests; reviewed other mechanisms and recorded remaining hypotheses.
remaining: Execute required SOURCE and PR CI on exact final SHA and repair any FAIL within this task; device/site/Gecko runtime NOT_TESTED. Stage7 only after next DALEJ.
required_ci: SOURCE process-docs plus shared-tests JavaScript, Kotlin/Robolectric, AndroidTest compile, lint/baseline, quality-evidence SUCCESS; PR Android CI JS/Kotlin/AndroidTest/lint/error-learning SUCCESS on merge checkout referencing final source SHA.
device: NOT_TESTED — no Nothing Phone (1), affected website, live profile switching or audiofocus runtime.
apk: NOT_BUILT — no private signed APK until separate approval after stage 10.
known_failures: ERR-20261110-PREVIEW-01/-03/-04 and stage5 ERR-20261110-PREVIEW-05 plus BGPLAY-ALLOWLIST-RACE remain OPEN for live-site evidence. Stage6 new source-level errors ERR-20261110-BGPLAY-REUSE and ERR-20261110-BGPLAY-PORT-EPOCH.
verification: Direct GitHub connector confirmed stage5 source HEAD, PR, SOURCE/PR CI and read exact policy/code sources from that SHA; no local checkout or physical runtime. High-risk independent review unavailable; self-review and required CI planned.
changed_paths:
  - app/src/main/java/com/prirai/android/nira/permissions/NiraBgPlayController.kt
  - app/src/main/java/com/prirai/android/nira/permissions/NiraBgPlayAllowlistBridge.kt
  - app/src/test/java/com/prirai/android/nira/permissions/NiraBgPlayBridgeLifecycleTest.kt
  - docs/ai/PREVIEW_COMPAT_STAGE6.md
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
verified_before_push:
  - Existing extension reused without reinstall, native handler reattached.
  - Stale port callbacks ignored by generation; installed extension and current host permissions preserved.
  - No changes to per-origin stored data, profiles, Android signing, package, MPV Alpha or APK.
blockers:
  - Mandatory SOURCE and PR CI pending at checkpoint, real Gecko/device/site NOT_TESTED.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/134
  - https://github.com/laszko14/nira-browser/actions/runs/42778905706
  - https://github.com/laszko14/nira-browser/actions/runs/42778931733
next_action: Finish stage6 final SOURCE/PR CI, correct FAIL on new SHA and record exact result in PR; then STOP until DALEJ for stage7.
```
