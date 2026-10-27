# Zavro Development System — Roadmap (2026-10-27)

This infrastructure effort is **independent of production Android variants**. Milestones must not be conflated: PLANNED, IMPLEMENTED, TESTED, CONFIRMED WORKING.

| Stage | Scope | Gate to proceed | Artifact |
|---|---|---|---|
| 1 | Audit actual connector/repo/branches/workflows/signing configuration; isolated branch; read-only policy, checkpoint | Verify GitHub ref and content; no Android build | This directory + devtools policy |
| 2 | Build Control: variant/ref/SHA preflight, dispatch design, idempotency and dry run | Tests of rejected unsafe arguments and no double dispatch | Build controller with manual approval |
| 3 | ChatGPT integration: connector capabilities, private plugin or broker with minimal scopes | Authenticated request tested in ordinary chat, no token leak | Working trigger or documented tested fallback |
| 4 | Results: runs/jobs/steps/logs, artifact integrity and signed arm64 APK verification | Bind observed run to requested immutable SHA | Build result report |
| 5 | Smart CI: fast checks, test selection, Kotlin/JS/Lua/lint, Gradle cache/concurrency and signed metadata checks | Compare real CI durations and failure cases | Faster verified pipelines |
| 6 | Inspector: diffs/branches/Codex protection, graph of code dependencies, TestLab diagnostics and regression analysis | Real-repo tests; no automatic code modification | Evidence-based inspector |
| 7 | Orchestrate analyze → inspect → implement isolated → test → build → report; resume via checkpoints | End-to-end scenario and safe replay tested | Single workflow |
| 8 | Success/failure/security/recovery tests; daily commands and limitations | Audit all claims with real links/SHA | Final acceptance dossier |

Dependencies: each stage requires the previous gate. Never start a later stage automatically. Human says **Dalej**. Documentation writes may occur on devtools branch; no merges, force-pushes, releases or expensive Android builds by default.

Key engineering decisions:
- Support `clean` and `mpv` as distinct variants, never infer from a similarly named branch.
- Every build request binds to a full 40-character commit SHA. Re-read ref before authorizing.
- A dispatch request needs explicitly validated variant, workflow allowlist, SHA and human intent. Stage 1 deliberately denies dispatch.
- GitHub App tools can inspect runs, jobs, steps, logs and artifacts, and rerun failed jobs, but this connector does NOT expose a new workflow_dispatch action. Stage 3 must independently prove a supported bridge.
- GitHub workflow_dispatch has special workflow registration and token/permission constraints. Do not presume a workflow on an arbitrary non-default branch can be invoked. Design a secure bridge only after investigating GitHub's live API conditions.
- CI cost: no PR for this documentation branch during Stage 1 (the latest Clean workflow runs on PRs). No new APK or build artifacts in Stage 1.
- Checkpoint verification from GitHub is mandatory before every resumed stage. Never trust a checkpoint over a moving branch head.
