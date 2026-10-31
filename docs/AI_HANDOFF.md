# AI_HANDOFF — bieżący checkpoint (Zavro Clean / proces AI)

> Jedno źródło **aktualnego stanu tej gałęzi**; nie jest to globalny status wszystkich równoległych prac. Poprzednia pełna historia jest bez zmian w [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md). Nie odczytuj jej bez potrzeby.

```yaml
task_id: ZC-PROC-05
workflow_mode: staged
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: improve/zavro-clean-chatgpt-github-process-20261101
base_sha: 7f27f5309a4097c4a620c717684c563ff7f321dc
observed_head_sha: 7f27f5309a4097c4a620c717684c563ff7f321dc
status: CI_PENDING
acceptance_source: ETAP 5/9 — lint regression gate and SHA-linked CI evidence
required_ci: zavro-ai-process-docs.yml / process-docs + shared-tests/{javascript,unit,android-test-compile,lint} + quality-evidence
device: NOT_TESTED
apk: NOT_BUILT
changed_paths:
  - .github/workflows/zavro-tests-reusable.yml
  - .github/workflows/zavro-ai-process-docs.yml
  - scripts/lint-stage4-baseline.cjs
  - scripts/lint-regression.cjs
  - scripts/lint-regression.test.cjs
  - scripts/ci-evidence.cjs
  - scripts/ci-evidence.test.cjs
  - docs/ai/LINT_QUALITY_GATE.md
  - docs/AI_HANDOFF.md
  - AGENTS.md
verified_before_push:
  - Stage 4 CI 41253701343 completed/success at 7f27f5309a4097c4a620c717684c563ff7f321dc
  - Original lint XML SHA256 e79e415b38e65e13a33e276b4b6e67671eb9b2436b2d1b5869710425937399a8
  - Baseline: 1462 Fatal, 164 Error, 508 Warning, 18 Hint (2152 total)
  - Baseline groups: 124 paths, 178 path/severity/rule combinations
  - No changes in app runtime, signing, release or Gecko
blockers: []
next_action: Verify process-docs, four shared tests and quality-evidence at final HEAD; then await Dalej for stage 6/9
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41253701343
```

## Jak wznowić pracę

1. Odczytaj `AGENTS.md` i powyższy checkpoint **na jawnym ref/SHA**. Zweryfikuj, że `task_id`, produkt i gałąź odpowiadają zleceniu. Jeśli zadanie dotyczy innej gałęzi, nie przenoś na nie tego checkpointu.
2. Odczytaj na żywo HEAD gałęzi; przy rozbieżności z `observed_head_sha` sprawdź diff, zamiast nadpisywać nowe zmiany. Ten SHA jest **obserwacją sprzed push**, nie własnym SHA commita.
3. Odbierz status GitHub Actions dla **końcowego HEAD, konkretnego workflow, run i próby**. `status: CI_PENDING` jest migawką przed wypchnięciem — nie jest końcową oceną, gdy CI już się zakończyło. Zweryfikowane SUCCESS wszystkich wymaganych kontroli dopiero pozwala zakończyć etap.
4. Nie czytaj pełnej historii do rutynowego wznowienia. Wystarczy `AGENTS.md`, ten plik i wskazane kryteria zadania. Szablon przekazania: [TRANSFER_TEMPLATE.md](ai/TRANSFER_TEMPLATE.md).

## Granice i inne prace

- Zakres tej gałęzi: **proces ChatGPT/GitHub**, bez zmian aplikacji, bez merge do `main`, bez APK. `workflow_mode=staged`: jedno „Dalej” = jedno zadanie i jego kompletna weryfikacja; potem STOP.
- Prace nad GeckoView to **odrębne gałęzie**. Ostatnio zweryfikowana w etapie 1 linia `test/zavro-clean-post19-gecko-g4d-update-preflight-20261101` miała HEAD `7cfa2d13a467235102666822d40631fe1818c9b4`; stan sprawdzaj ponownie na GitHubie, nie nadpisuj go tym checkpointem.
- Następne uzgodnione zadanie po etapie 5: **ETAP 6/9 — niezależne review i kontrola regresji**.
- Zachowano pełną zawartość oryginalnego `docs/AI_HANDOFF.md` jako obiekt Git o SHA `ebb553e63d69158da4f4fe89c4e5ae673bbe53f6` w `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`. Rejestry audytu nadal są odrębnym materiałem dowodowym.
