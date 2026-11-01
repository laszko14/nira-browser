# AI_HANDOFF — Zavro Clean / T03 signed v1197 APK

> Jedno bieżące źródło tego brancha. Historia procesu: [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md).

```yaml
task_id: T03-BUILD-V1197
workflow_mode: staged
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: build/zavro-clean-t03-panels-v1197-20261101
base_sha: 8dd4d59d85e280e350aaf9990556ead2dd8fd37b
observed_head_sha: 8dd4d59d85e280e350aaf9990556ead2dd8fd37b
status: CI_PENDING
acceptance_source: ETAP 9/9 — docs/ai/T03_SIGNED_BUILD_V1197.md
required_ci: zavro-t03-panels-update-v1197.yml authorized-source / shared regressions / signed-update / verify-all
device: NOT_TESTED
apk: BUILD_PENDING
pilot: T01/T02 FOLLOWUP_PENDING; T03 ACTIVE
expected_package: com.prirai.android.nira.debug
expected_version_code: 1197
expected_version_name: 0.5.22-alpha102-clean
signer_sha256: df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00
changed_paths:
  - app/build.gradle
  - .github/workflows/apk-release.yml
  - .github/workflows/zavro-t03-panels-update-v1197.yml
  - app/src/test/js/release-contract-stage19a.test.cjs
  - app/src/test/js/update-apk-workflow-scope.test.cjs
  - docs/ai/T03_SIGNED_BUILD_V1197.md
  - docs/AI_HANDOFF.md
verified_before_push:
  - Base 8dd4d59d85e280e350aaf9990556ead2dd8fd37b CI 41262560676 success, six jobs, one attempt
  - User explicitly authorized Kompiluj; version-only Gradle bump, stable signer required
blockers: []
next_action: Verify final SHA and all CI jobs, signed APK artifact; STOP before installation or further repairs
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41262560676
```

## Wznowienie

Odczytaj AGENTS.md oraz ten checkpoint z jawnego SHA, porównaj aktywny branch HEAD. `CI_PENDING` przed push nie oznacza rezultatu. Nie twórz commita wyłącznie po to, aby zapisać run ID. Bez merge, publikacji, instalacji, kasowania danych, zmian profili, klucza i sesji. Rzeczywiste testy telefonu należą do następnego etapu po ręcznej instalacji użytkownika.
