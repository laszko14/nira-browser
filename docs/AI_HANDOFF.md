# AI_HANDOFF — Zavro Clean v2.0 / migracja polityki i CI
> Jeden aktywny checkpoint procesu; historia: [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md).

```yaml
task_id: V2-MIGRATION-20261101
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: improve/zavro-clean-chatgpt-github-process-20261101
base_sha: e496e141f97045f1a9d315e349a1666277547569
observed_head_sha: e496e141f97045f1a9d315e349a1666277547569
status: CI_PENDING
acceptance_source: Wdrożenie AGENTS v2.0 i bezpieczna migracja CI, bez merge do main
required_ci: zavro-ai-process-docs.yml process-docs shared-tests quality-evidence, shared JS/JVM/lint/AndroidTest compile and CI-contract tests
device: NOT_TESTED; zadanie procesowe bez testu telefonu
apk: NOT_BUILT; zadanie procesowe bez budowy APK
changed_paths:
  - AGENTS.md, docs/ai instrukcje i wzorce
  - .github/agents.md, .github/copilot-instructions.md
  - .github/workflows/apk-release.yml, zavro-ai-process-docs.yml
  - scripts/check-ci-workflows.test.cjs, scripts/check-ai-handoff.test.cjs
  - docs/AI_HANDOFF.md
verified_before_push:
  - Baza i pełny HEAD gałęzi procesu potwierdzone przed zmianą
  - Podpis stałym kluczem i pakiet/ABI zachowane; publikacja oddzielna
  - Push/PR/tag pozostają tests-only; brak automatycznego signed APK w tej migracji
  - Historyczne workflow i pilotaż pozostają zachowane
blockers:
  - Potwierdź zielone wymagane CI na końcowym SHA
  - Wklejenie instrukcji Projektu ChatGPT pozostaje osobnym działaniem użytkownika
  - Inne historyczne gałęzie nie zostały automatycznie zmigrowane
next_action: Sprawdź rzeczywiste REQUIRED CI na końcowym SHA, usuń ewentualne FAIL; podaj status wdrożenia bez budowy APK, merge ani publikacji
evidence_links:
  - https://github.com/laszko14/nira-browser/commit/e496e141f97045f1a9d315e349a1666277547569
```

## Wznowienie
Historyczny ETAP 9/9, v1199 i poprzednie runy nie są obecnym zadaniem. Odczytaj live HEAD i właściwy checkpoint; nie twórz commita tylko dla wyniku CI. Bez merge i utraty danych.
