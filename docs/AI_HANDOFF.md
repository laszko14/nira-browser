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
observed_head_sha: b131a0219ea86e533a1f4f88afd81789427e8f57
status: CI_PENDING
acceptance_source: User supplied two v1226 diagnostic ZIPs and requested an opt-in MPV/Play transport repair, tests, registry, checkpoint, commit, push and green SOURCE/PR CI; no APK, merge or release.
completed: Verified two valid archives with native markers and zero DOM events. RED JS test showed no START acknowledgement. Added typed ACK, bounded JS reconnect, native transport states and safe failure counters, explicit incomplete report and menu status. Root cause of the two phone transport failures remains undetermined.
remaining: Finish local Kotlin and full regression gates, review diff, push on this branch, verify final source SHA and all required SOURCE/PR CI; inspect a new device report for the exact transport failure. Do not infer the cause of broken thumbnails.
required_ci: SOURCE process-docs and shared-tests JavaScript, JVM/Robolectric, AndroidTest compile, lint plus baseline and evidence all SUCCESS on final source SHA; PR shared-tests and error-learning gates SUCCESS on integration checkout.
apk: NOT_BUILT for this task. Historical v1226 signed artifact run 42864976383; no new build or download.
device: NOT_TESTED; no Android device or real Gecko site test in this task.
known_failures: Both supplied ZIPs have the same empty-page-evidence fingerprint. Original v1226 report cannot distinguish no port, missing ACK or rejected page packet. Local Robolectric initially lacked its Android 15 image; verified Maven SHA-512 and reran. One test fixture used a Kotlin-null Mockito matcher and was corrected; an OOM-killed Gradle daemon was rerun with a smaller heap.
verification: RED node stage2 ACK test failed before implementation. Final local JS 660/660, process 63/63, JVM/Robolectric 1561/1561 and AndroidTest Kotlin compile succeeded. Final lint is running; earlier lint and baseline passed before final minor edits. SOURCE 42870600968 and PR 42870612903 succeeded on the historical base only.
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
next_action: Finish required regression tests and CI on the eventual fast-forward SHA; report honest transport uncertainty and request a new opt-in phone report only after code and CI verification.
```

## Istotna historia przekazania

- 2026-11-10: po etapie diagnostyki MPV/Play 5/5 SOURCE run 42856474338 i PR run 42857649495 zakończyły się sukcesem na SHA 8453a70e150cd8fbca01e79a16be5980d214aa03.
- Pierwszy podpisany APK v1225 (run 42860175611) nie mógł zaktualizować wersji v1225 już zainstalowanej u użytkownika. Naprawiono oczekiwania builda na v1226 oraz nieaktualny fixture testu neon, po nieudanym SOURCE 42862726025 i PR 42862739371.
- Poprawny commit źródłowy `f24e3463a958c83aa2f2347000fbb7705ae8bfa4` otrzymał zielone SOURCE i PR CI, a zatwierdzony owner-review zbudował i zweryfikował APK v1226. Użytkownik potwierdził skompilowanie v1226; **instalacja nie została potwierdzona**.
- Dokumentacja i historia wcześniejszych etapów pozostają w Git, w tym w przeszłych wersjach `docs/AI_HANDOFF.md` i rejestrze `docs/ai/ERROR_CASES.json`. Nie przepisuj historycznych wyników ani nie zakładaj naprawy usterek podglądu na telefonie.
