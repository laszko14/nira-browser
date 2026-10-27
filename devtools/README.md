# Zavro Development System (infrastructure only)

Stage 1 is an **audit and read-only policy gate**, not a build controller. This folder never stores credentials and does not modify Android app source.

Validate offline with Python 3:

```sh
python3 devtools/check_policy.py
python3 -m unittest discover -s devtools/tests -v
```

The validator refuses enabling dispatch, merge, release, force push or mutations while stage_state is `audit_only`. This is **not** an active GitHub policy enforcement bot yet; it validates configuration and provides a tested baseline. Never use the older `main` APK workflow to install over Clean or MPV.

Next stage begins ONLY after the human message `Dalej`. Check `docs/dev-system/CHECKPOINT.json`, refresh GitHub branch refs and confirm every test and commit. Do not silently reuse the stale target SHAs.

Rollback: stop using the isolated `tools/zavro-development-system-stage1-20261027` branch. Do not delete or modify other branches.


## Stage 2 Build Control
See `docs/dev-system/BUILD_CONTROL_STAGE2.md`. The new controller is read-only, validates both variants and blocks dispatch unconditionally. Execute Node 22 tests with `node --test devtools/tests/build-control.test.mjs`.

## Stage 3 — Ordinary ChatGPT integration
Connector-triggered GitHub Actions without a new plugin is implemented. See `docs/dev-system/CHATGPT_INTEGRATION_STAGE3.md`. Probe GitHub Actions **40466137395** passed 26/26 Node tests; intentionally invalid build request **40466190188** failed preflight and skipped dispatch. Creating a `devtools/build-requests/<variant>-<sha>.json` file can start a signed ARM64 build: do this ONLY when the human explicitly orders compilation. The build dispatch itself has not yet been exercised.
