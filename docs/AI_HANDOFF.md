# AI_HANDOFF — bieżący checkpoint (Zavro Clean / proces AI)

> Jedno źródło **aktualnego stanu tej gałęzi**; nie jest to globalny status wszystkich równoległych prac. Poprzednia pełna historia jest bez zmian w [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md). Nie odczytuj jej bez potrzeby.

```yaml
task_id: ZC-PROC-03
workflow_mode: staged
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: improve/zavro-clean-chatgpt-github-process-20261101
base_sha: 5d778fa98c859ce414c0e44bb07d19dad5d123f2
observed_head_sha: 5d778fa98c859ce414c0e44bb07d19dad5d123f2
status: CI_PENDING
acceptance_source: prompty.txt / ETAP 3/9 (załączony przez użytkownika)
required_ci: zavro-ai-process-docs.yml / process-docs + quality-regressions
device: NOT_TESTED
apk: NOT_BUILT
changed_paths:
  - AGENTS.md
  - docs/AI_HANDOFF.md
  - docs/ai/TASK_TEMPLATE.md
  - docs/ai/TEST_PATTERNS.md
  - app/src/test/js/media-detector.test.cjs
  - app/src/test/js/helpers/transport-snapshot.cjs
  - app/src/test/js/transport-snapshot-contract.test.cjs
  - scripts/check-ai-handoff.test.cjs
  - .github/workflows/zavro-ai-process-docs.yml
verified_before_push:
  - Przejrzano Robolectric AppBoundariesTest i VideoSearchV32IntegrationTest
  - Sprawdzono przypadek HLS 17C i istniejący Node harness
  - CI etapu 2: SUCCESS na 5d778fa98c859ce414c0e44bb07d19dad5d123f2
blockers: []
next_action: Odczytaj HEAD i status pełnego JS/JVM CI na końcowym SHA; po PASS czekaj na Dalej do ETAPU 4/9
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41252838480
```

## Jak wznowić pracę

1. Odczytaj `AGENTS.md` i powyższy checkpoint **na jawnym ref/SHA**. Zweryfikuj, że `task_id`, produkt i gałąź odpowiadają zleceniu. Jeśli zadanie dotyczy innej gałęzi, nie przenoś na nie tego checkpointu.
2. Odczytaj na żywo HEAD gałęzi; przy rozbieżności z `observed_head_sha` sprawdź diff, zamiast nadpisywać nowe zmiany. Ten SHA jest **obserwacją sprzed push**, nie własnym SHA commita.
3. Odbierz status GitHub Actions dla **końcowego HEAD, konkretnego workflow, run i próby**. `status: CI_PENDING` jest migawką przed wypchnięciem — nie jest końcową oceną, gdy CI już się zakończyło. Zweryfikowane SUCCESS wszystkich wymaganych kontroli dopiero pozwala zakończyć etap.
4. Nie czytaj pełnej historii do rutynowego wznowienia. Wystarczy `AGENTS.md`, ten plik i wskazane kryteria zadania. Szablon przekazania: [TRANSFER_TEMPLATE.md](ai/TRANSFER_TEMPLATE.md).

## Granice i inne prace

- Zakres tej gałęzi: **proces ChatGPT/GitHub**, bez zmian aplikacji, bez merge do `main`, bez APK. `workflow_mode=staged`: jedno „Dalej” = jedno zadanie i jego kompletna weryfikacja; potem STOP.
- Prace nad GeckoView to **odrębne gałęzie**. Ostatnio zweryfikowana w etapie 1 linia `test/zavro-clean-post19-gecko-g4d-update-preflight-20261101` miała HEAD `7cfa2d13a467235102666822d40631fe1818c9b4`; stan sprawdzaj ponownie na GitHubie, nie nadpisuj go tym checkpointem.
- Następne uzgodnione zadanie po zatwierdzeniu etapu 3: **ETAP 4/9 — bezpieczne wspólne CI** (według załączonego `prompty.txt`).
- Zachowano pełną zawartość oryginalnego `docs/AI_HANDOFF.md` jako obiekt Git o SHA `ebb553e63d69158da4f4fe89c4e5ae673bbe53f6` w `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`. Rejestry audytu nadal są odrębnym materiałem dowodowym.
