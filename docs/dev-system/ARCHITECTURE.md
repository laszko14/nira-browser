# Zavro Development System — Architecture v0 (stage 1)

## Control plane (non-production)
Normal ChatGPT + connected GitHub (read/branch/commit/compare/run logs) + optionally a private plugin and a narrowly scoped broker (later stages; NOT DEPLOYED in stage 1).

```text
User command
    |
    v
ChatGPT: request and variant/branch/SHA interpretation
    |
    v
Code Inspector (read-only preflight) -- evidence --> Smart CI test selection
    |                                           |
    v                                           v
Build Control (approve + idempotency) -----> GitHub Actions jobs
                                                 |
                                                 v
                                     logs / checks / signed artifacts
                                                 |
                                                 v
                               inspect APK + summarize + checkpoint
```

## Isolation contract
- Infrastructure root: `devtools/` and `docs/dev-system/`; no Android production source touched.
- Infrastructure branch: `tools/zavro-development-system-stage1-20261027`.
- Its base is frozen `main` SHA `0823f24eb48e2eefbe38c7616bc1d55e73917b37`; this is NOT a declaration that main contains the latest Android variant.
- Target variant snapshots in `devtools/policy.json` are read-only audit samples. Clean is moving rapidly; refresh SHA from GitHub before the next operation.
- Policy is deny-by-default. Stage-1 check code is local/offline only. A passing policy test is NOT evidence of compilation, workflow dispatch or plugin connectivity.
- Once Stage 2 begins, separate per-variant job definitions should be introduced with explicit applicationId, versionCode/name, arm64 ABI, certificate fingerprint and provenance; do not copy the older main workflow into latest branches.

## Security and correctness
- Never read secret values; public certificate SHA-256 may be compared. Use narrow permissions and ephemeral auth in future broker.
- Protect `main`, all `feat/*` and Codex branches in software even when GitHub repository reports `protected=false`.
- No `merge`, `force`, `release`, mutable latest-based deploy, unpinned build, webhook trust without signature verification or direct uncontrolled GitHub API execution.
- Report run ID, immutable SHA, workflow path, job status, APK metadata and verification provenance only when actually observed.
- Build cache and parallelism may save time, but job-level cache scope, signing and test selection must be measured, not presumed safe.
- Rollback: disable controller flags, stop using infrastructure branch, no merge needed. Existing production code remains untouched.

## Integration decision record
GitHub connector observed: authenticated user has admin/push access; GitHub fetch GET, branch creation, commits, compare, re-run and artifact read/download available; no direct workflow dispatch, so Stage 3 requires a verified alternative such as a dedicated private plugin to a secure broker using a narrow GitHub App installation token. A broker would validate source branch, SHA, variant and run uniqueness before calling the GitHub REST Actions API. This is an architectural proposal, not a live service.
