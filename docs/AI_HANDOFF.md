# AI_HANDOFF — Zavro Clean, stała gałąź i przekazywanie APK przez link (10.11.2026)

Kanon gałęzi i monotonicznej wersji: `docs/ai/ACTIVE_CLEAN_LINE.md`. Przed pracą sprawdzaj live HEAD wskazanej gałęzi i ostatnie Actions; poniższy checkpoint jest zapisany **przed nowym CI dokumentacji**.

```yaml
task_id: ZC-CLEAN-CONTINUOUS-LINE-APK-LINK-20261110
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110
base_sha: f24e3463a958c83aa2f2347000fbb7705ae8bfa4
observed_head_sha: f24e3463a958c83aa2f2347000fbb7705ae8bfa4
status: CI_PENDING
acceptance_source: User 2026-11-10 requested no agent APK downloads in ChatGPT/Work/Codex, only GitHub Actions run links; one continuously advancing development branch and strictly increasing APK update numbers.
completed: Confirmed live branch and PR #140 at base SHA. SOURCE 42863285968 and PR 42863296609 completed/success on base SHA. Private signed v1226 build 42864976383 completed/success; build job verifier confirmed package, versionCode 1226, versionName alpha122-clean, arm64-v8a and pinned SHA-256 certificate; artifact 13787720924. Prepared docs-only canonical active line and harmonized agent, preflight, project, Copilot, process and APK instructions; no app or signing changes.
remaining: Push docs-only fast-forward to the same branch; confirm full final SHA and required SOURCE/PR CI on that exact SHA; triage any FAIL in the same task. No APK for documentation change. Manually replace ChatGPT Project Instructions and refresh static Project Sources outside repo if desired.
required_ci: SOURCE process-docs, shared-tests JavaScript, JVM/Robolectric, AndroidTest compile, lint, evidence; PR shared-tests and error-learning gates all SUCCESS on final source HEAD / corresponding PR merge-ref.
apk: APK_VERIFIED v1226 from run 42864976383 and artifact zavro-arm64-update-apk-v1226 (ID 13787720924), source SHA f24e3463a958c83aa2f2347000fbb7705ae8bfa4. DO NOT download APK/ZIP as agent; user downloads from run page. This docs-only change does not build another APK.
device: NOT_TESTED; user previously confirmed installed v1225, installation of v1226 NOT_CONFIRMED.
privacy: No changes to profiles, sessions, history, permissions, Android package, ABI, signing key, app code, merge or release.
known_failures: Initial v1226 neon test fixture failure on old SHA a2a4c414da65f0249c4afd800625521679045b81 corrected before base; historical failed runs 42862726025 and 42862739371 do not block green base SHA. Runtime Play/preview site cases still require device evidence.
verification: SOURCE 42863285968 success; PR 42863296609 success; APK 42864976383 completed/success with verifier output versionCode=1226, cert and ABI; artifact metadata confirmed. New documentation commit CI remains PENDING.
verified_before_push: Read AGENTS.md, docs/AI_HANDOFF.md, docs/ai/CHATGPT_PROJECT_INSTRUCTIONS.md, docs/ai/STARTUP_PREFLIGHT.md, APK_CHAT_GITHUB_REVIEW_TRIGGER.md, DEVELOPMENT_WORKFLOW.md, CHECKPOINT_POLICY.md, project sources and build.gradle at explicit live base SHA; confirmed branch ref and active PR.
blockers: ChatGPT Project Instructions and uploaded Project Sources cannot be changed by repo commit; user must apply latest bootstrap manually. Local Android device not accessible.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/42863285968
  - https://github.com/laszko14/nira-browser/actions/runs/42863296609
  - https://github.com/laszko14/nira-browser/actions/runs/42864976383
  - https://github.com/laszko14/nira-browser/pull/140
changed_paths:
  - AGENTS.md
  - docs/ai/ACTIVE_CLEAN_LINE.md
  - docs/ai/STARTUP_PREFLIGHT.md
  - docs/ai/CHATGPT_PROJECT_INSTRUCTIONS.md
  - docs/ai/project-sources/ZAVRO_START.md
  - docs/ai/project-sources/ZAVRO_SYSTEM_V3.md
  - docs/ai/DEVELOPMENT_WORKFLOW.md
  - docs/ai/APK_CHAT_GITHUB_REVIEW_TRIGGER.md
  - docs/ai/APK_RELEASE.md
  - .github/copilot-instructions.md
  - docs/AI_HANDOFF.md
next_action: Verify new docs-only commit full SHA and green SOURCE+PR CI. If green report CODE_VERIFIED for docs change; do not build/download APK and do not start unrelated stage.
```

## Istotna historia przekazania

- 2026-11-10: po etapie diagnostyki MPV/Play 5/5 SOURCE run 42856474338 i PR run 42857649495 zakończyły się sukcesem na SHA 8453a70e150cd8fbca01e79a16be5980d214aa03.
- Pierwszy podpisany APK v1225 (run 42860175611) nie mógł zaktualizować wersji v1225 już zainstalowanej u użytkownika. Naprawiono oczekiwania builda na v1226 oraz nieaktualny fixture testu neon, po nieudanym SOURCE 42862726025 i PR 42862739371.
- Poprawny commit źródłowy `f24e3463a958c83aa2f2347000fbb7705ae8bfa4` otrzymał zielone SOURCE i PR CI, a zatwierdzony owner-review zbudował i zweryfikował APK v1226. Użytkownik potwierdził skompilowanie v1226; **instalacja nie została potwierdzona**.
- Dokumentacja i historia wcześniejszych etapów pozostają w Git, w tym w przeszłych wersjach `docs/AI_HANDOFF.md` i rejestrze `docs/ai/ERROR_CASES.json`. Nie przepisuj historycznych wyników ani nie zakładaj naprawy usterek podglądu na telefonie.
