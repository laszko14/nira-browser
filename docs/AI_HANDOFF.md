# AI_HANDOFF — Zavro Clean, preview-assist compatibility stage 3/10

Archiwum dawnego handoffu: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`.
Checkpoint przed końcowym CI; live HEAD i GitHub Actions są źródłem prawdy. Bez merge.

```yaml
task_id: ZC-PREVIEW-COMPAT-3
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-preview-assist-stage3-20261110
base_sha: 7f7eac80ab47986a5f896951db77ed243bd81dbf
observed_head_sha: 7f7eac80ab47986a5f896951db77ed243bd81dbf
status: CI_PENDING
acceptance_source: User DALEJ — stage 3/10; stop preview-assist stealing site-owned clicks while preserving declared previews and restoring navigation after play rejection; no APK.
completed: Confirmed stage2 PR #131 and SOURCE #42767694277 + PR #42767702011 SUCCESS on 7f7eac80ab47986a5f896951db77ed243bd81dbf; reviewed actual shipped preview-assist.js and 18A/18B/stage1 tests; narrowed helper interception for ordinary page controls and site-owned muted autoplay; introduced stage3 DOM contract and negative tests plus diagnosis; recorded separate production-risk incident and closed confirmed stage2 handoff-CI failure.
remaining: Run targeted stage3 and existing 18A/18B/stage1 regressions, then require final SOURCE and PR CI on exact new HEAD; actual problem-website and physical GeckoView behavior remain NOT_TESTED; after CI stop until new DALEJ for stage4.
required_ci: SOURCE process-docs plus shared-tests full JS, Kotlin/Robolectric, AndroidTest compile, Android lint/baseline, quality-evidence SUCCESS; PR Android CI shared JS, JVM, AndroidTest compile, lint, error-learning SUCCESS on integration ref tied to final source HEAD.
device: NOT_TESTED — no test on Nothing Phone (1), Android 15 or actual failing website.
apk: NOT_BUILT — explicitly excluded for stages 1–10, separate authorization only after stage 10.
known_failures: Main ERR-20261110-PREVIEW-01 remains OPEN (user issue not proven fixed); stage3 risk ERR-20261110-PREVIEW-03 OPEN until evidence; historical stage2 CI failure CI-42767349592-1-129666996717 FIXED on 7f7eac80ab47986a5f896951db77ed243bd81dbf.
verification: PR #131 current head 7f7eac80ab47986a5f896951db77ed243bd81dbf; SOURCE/PR conclusion success confirmed live; read AGENTS.md, AI_HANDOFF.md, STARTUP_PREFLIGHT.md, CHATGPT_PROJECT_INSTRUCTIONS.md, TEST_POLICY.md, QUALITY_GATES.md, ERROR_LEARNING.md, DEVELOPMENT_WORKFLOW.md, TASK_TEMPLATE.md, actual preview JS and legacy tests from 7f7eac80ab47986a5f896951db77ed243bd81dbf; CODE/DEVICE tests are distinct.
changed_paths:
  - app/src/main/assets/extensions/nira-media-detector/preview-assist.js
  - app/src/test/js/preview-assist-stage3.test.cjs
  - docs/ai/PREVIEW_COMPAT_STAGE3.md
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
verified_before_push:
  - No Gecko prefs, autoplay policy, MPV Alpha integration, signing, APK, package, ABI, profiles or stored user data changed.
  - Ordinary site controls remain site-owned; muted native autoplay click not captured; explicit preview control and preview-assist 18A/18B behavior must remain covered by tests.
  - Source-level synthetic DOM alone does not demonstrate Android/Gecko behavior.
blockers:
  - Mandatory final SOURCE/PR CI pending after push.
  - No actual affected website or physical device evidence; DEVICE NOT_TESTED.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/131
  - https://github.com/laszko14/nira-browser/actions/runs/42767694277
  - https://github.com/laszko14/nira-browser/actions/runs/42767702011
  - https://github.com/laszko14/nira-browser/commit/2d28a6bfd7291f285a45fae0eb7570c5dc25a06d
next_action: Resolve stage3 targeted and mandatory SOURCE/PR CI on exact final SHA, triage and fix any failure in this stage, record verified status without self-referential commit, then STOP. New DALEJ begins stage 4/10 only when stage 3 verified.
```
