# AI_HANDOFF — Zavro Clean V3.2 CI/error-policy alignment

Archiwum dawnego handoffu: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`.

Checkpoint przed commitem; źródłem prawdy jest live HEAD gałęzi, nie ten snapshot. PR #129 pozostaje otwarty i bez merge.

```yaml
task_id: ZC-V32-CI-ERROR-POLICY
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-video-diagnostics-20261108
base_sha: 252f0d717aa2a49bb5c7a64bdf7fa5bbdd1bd403
observed_head_sha: 0d4514bc4c0f8bc5be2f34b247b4356cbd1bc905
status: CI_PENDING
acceptance_source: User request WORK — ZAVRO CLEAN V3 — UJEDNOLICENIE OBSŁUGI BŁĘDÓW CI I INSTRUKCJI AI.
completed: Read current branch instructions and checkpoint at exact SHA, V3 policy docs, workflow triggers, error registry, validator, tests, and PR #129. Found a conflict between the former per-occurrence logging rule and the requested deduplicated incident policy, plus an obsolete separate-approval note. Implemented docs and ledger schema alignment in commit ceef8fd4c92a2a4aace267a77093655484de434e; no app code or workflow trigger changes.
remaining: Verify doc integrity, error-learning schema/tests, workflow safety, full SOURCE CI and required PR CI at the exact final SHA; fix any real failure in this task.
required_ci: SOURCE process-docs and shared-tests (JS, Kotlin/Robolectric JVM, AndroidTest compile, lint and regression baseline), required evidence summary; PR shared tests and error-learning evidence SUCCESS on the same final SHA. Build/release are not required for docs-only and must remain skipped.
device: NOT_TESTED — no app or device changes.
apk: NOT_BUILT — explicitly excluded.
known_failures: No new test or CI failure observed in this documentation task. Prior V3.1 event records have only metadata verifiable from their original sources; newly added schema fields are null/unknown rather than reconstructed.
verification: SOURCE #42678530146 and PR #42678537351 passed every required job on SHA ceef8fd4c92a2a4aace267a77093655484de434e. The checkpoint-only YAML and status correction changes SHA; verify required CI on this final candidate SHA. Review found no workflow security, signing, test-threshold, lint-baseline or app change.
changed_paths:
  - AGENTS.md
  - .github/agents.md
  - docs/ai/TEST_POLICY.md
  - docs/ai/QUALITY_GATES.md
  - docs/ai/DEVELOPMENT_WORKFLOW.md
  - docs/ai/ERROR_LEARNING.md
  - docs/ai/ERROR_CASES.json
  - scripts/error-learning.cjs
  - scripts/error-learning.test.cjs
  - docs/ai/CHATGPT_PROJECT_INSTRUCTIONS.md
  - docs/ai/project-sources/ZAVRO_START.md
  - docs/ai/project-sources/ZAVRO_SYSTEM_V3.md
  - docs/AI_HANDOFF.md
verified_before_push:
  - No APK, application behavior, MPV, merge, release, Secrets, signing/certificate, user data, or CI workflow trigger changed.
  - Schema migration preserves existing error records and leaves unknown historical fields null.
blockers:
  - Final CI awaits this commit; no CODE_VERIFIED claim before exact final SHA checks.
  - ChatGPT Project instructions and uploaded source snapshots require manual refresh. Repository AGENTS.md already covers Codex project guidance, so global Codex settings were left unchanged.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/129
  - https://github.com/laszko14/nira-browser/actions/runs/42675012847
next_action: Verify required CI on this SHA, then report final status and precise manual project-setting updates.
```
