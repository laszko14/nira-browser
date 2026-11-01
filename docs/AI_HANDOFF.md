# AI_HANDOFF — Zavro Clean / T03 — ograniczenie ponownego montowania lewego panelu

> Checkpoint gałęzi procesu. Historyczne wpisy: [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md).

```yaml
task_id: T03-LEFT-PANEL-SHORT-REUSE
workflow_mode: staged
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: improve/zavro-clean-chatgpt-github-process-20261101
base_sha: af36b043b185650e5775ce933a745d606bfef547
observed_head_sha: af36b043b185650e5775ce933a745d606bfef547
status: CI_PENDING
acceptance_source: ETAP 9/9 — T03/16 user TestLab PANELS_GESTURES v1197 and user Dalej
required_ci: zavro-ai-process-docs.yml process-docs shared-js shared-jvm shared-lint shared-androidtest-compile quality-evidence
device: MEASURED_V1197_120HZ; NEW_CODE_NOT_TESTED; 60HZ_NOT_TESTED
apk: EXISTING_V1197_VERIFIED_CI; NEW_APK_NOT_AUTHORIZED
pilot: T01/T02 FOLLOWUP_PENDING; T03 ACTIVE; 14-day observation not completed
changed_paths:
  - app/src/main/java/com/prirai/android/nira/browser/panels/SidePanelInfrastructure.kt
  - app/src/main/java/com/prirai/android/nira/BrowserFragment.kt
  - app/src/test/java/com/prirai/android/nira/browser/panels/SidePanelInfrastructureTest.kt
  - docs/ai/T03_PANEL_DIAGNOSTIC_60HZ.md
  - docs/AI_HANDOFF.md
verified_before_push:
  - Checked exact process HEAD af36b043b185650e5775ce933a745d606bfef547
  - User measurement v1197 completed 38.280s, 871 JankStats samples, 26 jank, 233.683ms max
  - Left mount 8/8 up to 43.083ms; right mount 8/8 up to 19.651ms
  - Actual measured refresh 120Hz despite 60Hz intended; no claim of 60Hz validation
  - Existing panel view is synchronously removed on CLOSED and remounted with commitNow on reopen
  - Change caches only normal-owner unchanged left content for up to 3s; right/owner/lifecycle invalidate
blockers:
  - No runtime device evidence for change; new signed APK requires separate Kompiluj
next_action: Verify final SHA and all CI jobs. Then STOP; request device comparison only after explicitly authorized APK.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41263325642
```

## Wznowienie

Check current process branch HEAD against this pre-push checkpoint. Do not treat CI_PENDING as success. Follow `AGENTS.md`. No merge, APK, signing, or changes to profile/session data. Compare manual 60Hz and 120Hz recordings only after an explicitly requested signed update. If build branch v1197 is used later, reconcile the code diff explicitly; branches are not merged automatically.
