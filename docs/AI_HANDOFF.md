# AI_HANDOFF — Zavro Clean, preview compatibility stage 1/10

Archiwum dawnego handoffu: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`.

Checkpoint przed commitem/CI; live HEAD gałęzi jest źródłem prawdy. Bez merge.

```yaml
task_id: ZC-PREVIEW-COMPAT-1
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-preview-diagnostics-stage1-20261110
base_sha: beebd957ff979ab3644c248e1622b8a98692e6bc
observed_head_sha: beebd957ff979ab3644c248e1622b8a98692e6bc
status: CI_PENDING
acceptance_source: User DALEJ — stage 1 of 10; build hover/touch thumbnail diagnostic tests only, no production autoplay change or APK.
completed: Reviewed Gecko autoplay settings, detector preview injection, old embedded MPV history; introduced synthetic gesture, native-click and NotAllowedError probes, diagnostic matrix and one OPEN incident.
remaining: Verify mandatory SOURCE+PR CI on final SHA; actual Gecko/site/device autoplay evidence NOT_TESTED. Do not start stage 2 before stage 1 gates.
required_ci: SOURCE process-docs plus shared-tests (JavaScript, JVM/Robolectric, AndroidTest compile, lint/baseline), quality-evidence SUCCESS; PR shared JS/JVM/AndroidTest/lint and error-learning SUCCESS on merge ref corresponding to exact final source head.
device: NOT_TESTED — no affected real website or physical GeckoView playback checked.
apk: NOT_BUILT — explicitly excluded before separate authorization after all 10 stages.
known_failures: User-reported hover/touch previews remain OPEN. First SOURCE #42763842314 / attempt 1 / process-docs job 129655735910 rejected a free-text test_result in the new incident; real cause was invalid enum value in the ledger, corrected to NOT_TESTED (no validator weakening). Other previously scheduled checks on that SHA are historical only; final CI must run on new SHA.
verification: AGENTS.md, AI_HANDOFF.md, STARTUP_PREFLIGHT.md, quality gates, test policy and source code read at exact source SHA before starting. CI pending new final commit.
changed_paths:
  - app/src/test/js/preview-compat-stage1.test.cjs
  - docs/ai/PREVIEW_COMPAT_STAGE1.md
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
verified_before_push:
  - No runtime Gecko preferences, WebExtension behavior, application Java/Kotlin, ABI, signing, package or persistent user state changed.
  - No weakened tests or gates; source-level and DOM tests are not claimed as real playback evidence.
blockers:
  - Final SOURCE/PR CI has not run at the time of this checkpoint.
  - Device and specific website reproduction unavailable; real behavior remains NOT_TESTED.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/129
  - https://github.com/laszko14/nira-browser/actions/runs/42685624858
  - https://github.com/laszko14/nira-browser/actions/runs/42685639895
next_action: Resolve required CI for final stage1 SHA, then STOP. Next user DALEJ begins stage 2/10 muted autoplay compatibility work.
```
