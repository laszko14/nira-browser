# AI_HANDOFF — Zavro Clean · V2-MIGRATION / instrukcje v2.0

```yaml
task_id: V2-INSTRUCTION-ALIGN-20261101
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: improve/zavro-clean-chatgpt-github-process-20261101
base_sha: 26e62c860b7e17b7935dbf7553f7dce3fc6446a9
observed_head_sha: 4460c7e1de366b13ee2c7c4db04a70abfa651d51
status: CI_PENDING
acceptance_source: Polecenie użytkownika: naprawić niespójne instrukcje i oznaczyć stare
required_ci: process-docs; shared JS/JVM/lint regression; AndroidTest compile; quality-evidence
device: NOT_TESTED
apk: NOT_BUILT
changed_paths:
  - AGENTS.md
  - docs/AI_HANDOFF.md
  - docs/ai/CHATGPT_PROJECT_INSTRUCTIONS.md
  - docs/ai/LEGACY_INSTRUCTIONS.md
verified_before_push:
  - Zmiany ograniczone do instrukcji i checkpointu; bez kodu aplikacji
  - Stare globalne reguły w Library oznaczone nazwami ARCHIWUM, bez zmiany treści
  - Commit v2.0 bazowy i jego CI zweryfikowane oddzielnie
  - SHA końcowego commitu oraz wynik CI ustalane z live ref/run; checkpoint nie zapisuje własnego SHA
blockers:
  - Ustawienia Projektu ChatGPT nie są edytowalne dostępnym narzędziem; tekst do wklejenia jest w docs/ai/CHATGPT_PROJECT_INSTRUCTIONS.md
  - Konektor GitHub nie udostępnia workflow_dispatch; podpisane APK jest BLOCKED bez uprawnionej ścieżki
  - Inne gałęzie nie są automatycznie zmigrowane
next_action: sprawdź pełny wymagany CI na aktualnym HEAD; ręcznie zsynchronizuj ustawienia Projektu ChatGPT
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41284565319
  - https://github.com/laszko14/nira-browser/actions/runs/41290802874
archive: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md
```

## Wynik
V2.0 pozostaje jedyną aktywną polityką na gałęziach, na których wdrożono i zweryfikowano migrację. `docs/ai/LEGACY_INSTRUCTIONS.md` wskazuje historyczne materiały i ich zakres. Nie kasuj starych specyfikacji ani historii Git, nie stosuj promptów „Dalej/Kompiluj” jako globalnej polityki.

Run `41284565319`, attempt 1, zakończył się sukcesem na bazowym SHA `26e62c860b7e17b7935dbf7553f7dce3fc6446a9`; nie jest dowodem dla późniejszych SHA. Zawsze sprawdź wymagany CI dla bieżącego pełnego HEAD. Czysto dokumentacyjna praca nie buduje APK.
