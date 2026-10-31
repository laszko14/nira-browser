# AI_HANDOFF — bieżący checkpoint (Zavro Clean / proces AI)

> Jedno źródło **aktualnego stanu tej gałęzi**; nie jest to globalny status wszystkich równoległych prac. Poprzednia pełna historia jest bez zmian w [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md). Nie odczytuj jej bez potrzeby.

```yaml
task_id: ZC-PROC-04
workflow_mode: staged
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: improve/zavro-clean-chatgpt-github-process-20261101
base_sha: 5fdc40bfb514b2c579307cdff307254e0ce4e78b
observed_head_sha: 5fdc40bfb514b2c579307cdff307254e0ce4e78b
status: CI_PENDING
acceptance_source: prompty.txt / ETAP 4/9 (załączony przez użytkownika)
required_ci: zavro-ai-process-docs.yml / process-docs + shared-tests/{javascript,unit,android-test-compile,lint}
device: NOT_TESTED
apk: NOT_BUILT
changed_paths:
  - .github/workflows/zavro-tests-reusable.yml
  - .github/workflows/zavro-ai-process-docs.yml
  - .github/workflows/apk-release.yml
  - docs/ai/CI_MIGRATION.md
  - scripts/ci-workflow-inventory.cjs
  - scripts/check-ci-workflows.test.cjs
  - AGENTS.md
  - docs/AI_HANDOFF.md
verified_before_push:
  - Repo before stage 4 had 65 workflow files
  - Legacy Android CI auto-built signed debug APK for PR except selected branches
  - Stage 3 CI: SUCCESS on 5fdc40bfb514b2c579307cdff307254e0ce4e78b
  - Legacy audit/G4 workflows retained; no PR or APK is authorized
blockers: []
next_action: Verify process-docs and all 4 shared test jobs on final SHA; after PASS await Dalej to ETAP 5/9
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41253155256
```

## Jak wznowić pracę

1. Odczytaj `AGENTS.md` i powyższy checkpoint **na jawnym ref/SHA**. Zweryfikuj, że `task_id`, produkt i gałąź odpowiadają zleceniu. Jeśli zadanie dotyczy innej gałęzi, nie przenoś na nie tego checkpointu.
2. Odczytaj na żywo HEAD gałęzi; przy rozbieżności z `observed_head_sha` sprawdź diff, zamiast nadpisywać nowe zmiany. Ten SHA jest **obserwacją sprzed push**, nie własnym SHA commita.
3. Odbierz status GitHub Actions dla **końcowego HEAD, konkretnego workflow, run i próby**. `status: CI_PENDING` jest migawką przed wypchnięciem — nie jest końcową oceną, gdy CI już się zakończyło. Zweryfikowane SUCCESS wszystkich wymaganych kontroli dopiero pozwala zakończyć etap.
4. Nie czytaj pełnej historii do rutynowego wznowienia. Wystarczy `AGENTS.md`, ten plik i wskazane kryteria zadania. Szablon przekazania: [TRANSFER_TEMPLATE.md](ai/TRANSFER_TEMPLATE.md).

## Granice i inne prace

- Zakres tej gałęzi: **proces ChatGPT/GitHub**, bez zmian aplikacji, bez merge do `main`, bez APK. `workflow_mode=staged`: jedno „Dalej” = jedno zadanie i jego kompletna weryfikacja; potem STOP.
- Prace nad GeckoView to **odrębne gałęzie**. Ostatnio zweryfikowana w etapie 1 linia `test/zavro-clean-post19-gecko-g4d-update-preflight-20261101` miała HEAD `7cfa2d13a467235102666822d40631fe1818c9b4`; stan sprawdzaj ponownie na GitHubie, nie nadpisuj go tym checkpointem.
- Następne uzgodnione zadanie po zatwierdzeniu etapu 4: **ETAP 5/9 — bramki jakości i dowody CI** (według załączonego `prompty.txt`).
- Zachowano pełną zawartość oryginalnego `docs/AI_HANDOFF.md` jako obiekt Git o SHA `ebb553e63d69158da4f4fe89c4e5ae673bbe53f6` w `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`. Rejestry audytu nadal są odrębnym materiałem dowodowym.
