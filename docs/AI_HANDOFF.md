# AI_HANDOFF — Zavro Clean, stała gałąź i przekazywanie APK przez link (10.11.2026)

Archive: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md.

Kanon gałęzi i monotonicznej wersji: `docs/ai/ACTIVE_CLEAN_LINE.md`. Przed pracą sprawdzaj live HEAD wskazanej gałęzi i ostatnie Actions; poniższy checkpoint jest zapisany **przed nowym CI dokumentacji**.

```yaml
task_id: ZC-MPV-PLAY-TRANSPORT-20261110
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110
base_sha: b131a0219ea86e533a1f4f88afd81789427e8f57
observed_head_sha: efea62184e780cc3ac50261eff3175ebd0885391
status: CI_PENDING
acceptance_source: User supplied two v1226 diagnostic ZIPs and requested an opt-in MPV/Play transport repair, tests, registry, checkpoint, commit, push and green SOURCE/PR CI; no APK, merge or release.
completed: Codex commit efea62184e780cc3ac50261eff3175ebd0885391 added START ACK, typed transport statuses, bounded reconnect, safe failure counters, incomplete report, and tests. Live SOURCE 42889451656 and PR 42889463425 on that SHA are SUCCESS. Follow-up review corrected duplicate native Port accounting and STOP/restart retry guard; regression tests added. Original device cause remains unknown.
remaining: Push review fixes on the same branch, confirm final HEAD and all required SOURCE/PR CI SUCCESS, then request new opt-in device ZIP from a newer APK only after separate authorization. Real Play/thumbnail root cause still requires device evidence.
required_ci: SOURCE process-docs and shared-tests JavaScript, JVM/Robolectric, AndroidTest compile, lint plus baseline and evidence all SUCCESS on final source SHA; PR shared-tests and error-learning gates SUCCESS on integration checkout.
apk: NOT_BUILT for this task. Historical v1226 signed artifact run 42864976383; no new build or download.
device: NOT_TESTED; no Android device or real Gecko site test in this task.
known_failures: Two v1226 ZIPs still have no DOM events; actual device-level cause is undetermined. Follow-up review found same Port counted twice if START overlaps onPortConnected, preventing DISCONNECTED after sole port loss; content script also suppressed reconnect after STOP then another START. Corrected with dedicated regressions. Earlier Codex JVM fixture and transient build-environment issues were resolved before green CI on efea62184e780cc3ac50261eff3175ebd0885391.
verification: Confirmed Codex implementation SHA efea62184e780cc3ac50261eff3175ebd0885391 from live GitHub; SOURCE 42889451656 all required jobs SUCCESS and PR 42889463425 JS, JVM, AndroidTest compile, lint and error learning SUCCESS. Follow-up review adds two targeted tests and transport fixes; their final SOURCE/PR CI is pending until new SHA is pushed. Device NOT_TESTED.
verified_before_push: Read AGENTS, active line, handoff, startup, project instructions, task, workflow, test, quality, error learning, checkpoint and diagnostics stage1-5 at explicit base SHA; verified clean isolated checkout, remote HEAD and DEFAULT ai-preflight.
blockers: Physical device unavailable; original report gives no direct native port or script execution trace. No causal attribution to playback or preview helper.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/140
  - https://github.com/laszko14/nira-browser/actions/runs/42870600968
  - https://github.com/laszko14/nira-browser/actions/runs/42870612903
changed_paths:
  - app/src/main/assets/extensions/zavro-preview-diagnostics/content.js
  - app/src/main/java/com/prirai/android/nira/BrowserFragment.kt
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayDiagnostics.kt
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayEvidenceReport.kt
  - app/src/main/java/com/prirai/android/nira/browser/MpvPlayPageBridgeController.kt
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayDiagnosticsTest.kt
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayEvidenceReportTest.kt
  - app/src/test/java/com/prirai/android/nira/browser/MpvPlayPageBridgeControllerTest.kt
  - app/src/test/js/mpv-play-diagnostics-stage1.test.cjs
  - app/src/test/js/mpv-play-diagnostics-stage2.test.cjs
  - app/src/test/js/mpv-play-diagnostics-stage3.test.cjs
  - app/src/test/js/mpv-play-diagnostics-stage5.test.cjs
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
next_action: Verify required SOURCE and PR CI for final fast-forward review commit; preserve no-APK scope, report CODE_VERIFIED only after both are fully green. Collect new phone report only after a separately authorized signed update.
```

## Istotna historia przekazania

- 2026-11-10: po etapie diagnostyki MPV/Play 5/5 SOURCE run 42856474338 i PR run 42857649495 zakończyły się sukcesem na SHA 8453a70e150cd8fbca01e79a16be5980d214aa03.
- Pierwszy podpisany APK v1225 (run 42860175611) nie mógł zaktualizować wersji v1225 już zainstalowanej u użytkownika. Naprawiono oczekiwania builda na v1226 oraz nieaktualny fixture testu neon, po nieudanym SOURCE 42862726025 i PR 42862739371.
- Poprawny commit źródłowy `f24e3463a958c83aa2f2347000fbb7705ae8bfa4` otrzymał zielone SOURCE i PR CI, a zatwierdzony owner-review zbudował i zweryfikował APK v1226. Użytkownik potwierdził skompilowanie v1226; **instalacja nie została potwierdzona**.
- Dokumentacja i historia wcześniejszych etapów pozostają w Git, w tym w przeszłych wersjach `docs/AI_HANDOFF.md` i rejestrze `docs/ai/ERROR_CASES.json`. Nie przepisuj historycznych wyników ani nie zakładaj naprawy usterek podglądu na telefonie.
