# AI_HANDOFF — Zavro Clean / T03 — prawy panel i diagnostyka prostokąta

> Bieżący checkpoint gałęzi procesu; historia: [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md).

```yaml
task_id: T03-RIGHT-BOOKMARKS-SURFACE-OVERLAY-REPORT
workflow_mode: staged
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: improve/zavro-clean-chatgpt-github-process-20261101
base_sha: 678c429e80d651d2ac38ed05f94a7b6f29f57749
observed_head_sha: 678c429e80d651d2ac38ed05f94a7b6f29f57749
status: CI_PENDING
acceptance_source: ETAP 9/9 — user confirmed v1198 left first-open lag, persistent translucent bookmarks right drawer and landscape grey rectangle; requested right panel fix and diagnostic button, plus new-chat transfer
required_ci: zavro-ai-process-docs.yml process-docs shared-js shared-jvm shared-lint shared-androidtest-compile quality-evidence
device: USER_REPORT_V1198 (first left open slightly janks, subsequent smooth; bookmarks transparent; rectangle persists); NEW_FIX_NOT_TESTED
apk: V1198_SIGNED_CI_RUN_41269564194; NEW_APK_NOT_AUTHORIZED
changed_paths:
  - app/src/main/java/com/prirai/android/nira/browser/panels/SidePanelHost.kt
  - app/src/main/java/com/prirai/android/nira/BrowserFragment.kt
  - app/src/main/java/com/prirai/android/nira/browser/OverlayWindowDiagnostics.kt
  - app/src/test/java/com/prirai/android/nira/browser/panels/SidePanelInfrastructureTest.kt
  - app/src/test/java/com/prirai/android/nira/browser/OverlayWindowDiagnosticsTest.kt
  - docs/AI_HANDOFF.md
verified_before_push:
  - Process branch HEAD 678c429e80d651d2ac38ed05f94a7b6f29f57749 and prior signed build source d282b38b980d92f88d683b662d143d2ed5e44eb9 checked against branch refs
  - Right embedded bookmarks RelativeLayout root has no background; rightContainer was also transparent
  - Right drawer receives an opaque Material colorSurface in host only; left and legacy dialog unchanged
  - Existing TestLab records frame/panel events but not native view ownership; diagnosis button uses bounded app decor metadata, no screenshots, URLs or text
  - User-reported grey rectangle is not attributed; separate system/PiP windows and Gecko-composited pixels cannot be inspected by this report
blockers:
  - Need confirm green CI for final new SHA
  - A new signed APK needs a separate Kompiluj; user must test root cause on device
next_action: Check final workflow and commit diff. STOP after green CI. On next Kompiluj build v1199 strictly signed arm64 update. After device install gather one-shot landscape report via ⋮ → Zbadaj prostokąt • raport.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41269564194
```

## Wznowienie

Read AGENTS.md and this handoff at exact final SHA; confirm branch HEAD before work. Distinguish left first-open cost from warmed cache, right bookmarks surface from system overlay. No merge, APK, install, profile/session changes. Do not claim the rectangle fixed or detected from metadata alone. Do not add CI-only commits after green result.
