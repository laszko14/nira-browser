# AI_HANDOFF — Zavro Clean / GitHub Actions: prywatny APK bez TinyFish

```yaml
task_id: ZAVRO-CI-PRIVATE-APK-REVIEW-20261101
workflow_mode: autonomous_task
repo: laszko14/nira-browser
branch: fix/zavro-clean-ci-private-apk-review-20261101
base_sha: c66350841741f16d1fccbf81fd876b012f4fe335
source_app_version: 0.5.22-alpha110-clean / arm64 versionCode 1205
product_line: Zavro Clean
status: CI_PENDING
apk: NOT_BUILT
device: NOT_TESTED
scope:
  - Restore controlled private APK trigger from ChatGPT GitHub connector via explicit PR review command.
  - Fail closed unless repo owner, exact PR head SHA, review commit SHA, signed source and prior green CI match.
  - Preserve normal push/PR/tag tests-only and workflow_dispatch backup.
  - Preserve existing signing cert/package/ABI/version and no public release.
changed_paths:
  - .github/workflows/apk-release.yml
  - scripts/authorize-private-apk-review.cjs
  - app/src/test/js/private-apk-review-request.test.cjs
  - docs/ai/APK_CHAT_GITHUB_REVIEW_TRIGGER.md
  - docs/AI_HANDOFF.md
safety:
  - No merge, no force push, no publishing, no certificate replacement.
  - No action on untrusted or fork PR; owner-only review, non-main branch only.
  - GitHub Actions artifacts in public repo require authentication but are not equivalent to a private repository.
verification:
  - PR required CI on final HEAD with JS/JVM/AndroidTest-compile/lint.
  - Only after green CI: explicit GitHub PR review at final SHA, then signed APK with verified certificate.
known_limit:
  - GitHub connector has no workflow_dispatch and main workflow form has historical inputs.
  - Future branches must inherit review-enabled workflow; v2 policy still scoped to migrated branches.
next_action: Open draft PR against feat/zavro-clean-video-results-bulk-v1205-20261101; verify CI then issue one owner review command from GitHub connector; inspect post-review workflow. Without signed run report APK_BLOCKED.
```

## Checkpoint

Base v1205 application is unchanged. The new review-command path must be tested
in actual GitHub Actions before reporting that the execution route is restored.
A green workflow on an earlier SHA cannot validate the final implementation.
