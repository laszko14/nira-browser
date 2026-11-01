# AI_HANDOFF — Zavro Clean · synchronizacja instrukcji v2.0
> Bieżący checkpoint tej gałęzi. Nie jest checkpointem dla main ani innych gałęzi.

```yaml
task_id: DOCS-INSTRUCTION-ALIGN-20261101
workflow_mode: documentation_only
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: chore/zavro-instructions-align-20261101
base_sha: 26e62c860b7e17b7935dbf7553f7dce3fc6446a9
base_ci_run: 41284565319
base_ci_attempt: 1
base_ci_status: completed/success
status: IN_PROGRESS
apk: NOT_BUILT
device: NOT_TESTED
scope:
  - Ujednolicenie AGENTS v2.0, instrukcji Projektu GPT i checkpointu
  - Oznaczenie nieaktualnych reguł jako archiwalnych bez kasowania historii
  - Bez zmian kodu aplikacji, danych, podpisu, merge i publikacji
blockers:
  - Ustawienia Projektu ChatGPT nie są edytowalne przez dostępne narzędzia; tekst do ręcznego wklejenia znajduje się w docs/ai/CHATGPT_PROJECT_INSTRUCTIONS.md
  - GitHub connector nie udostępnia workflow_dispatch; signed APK jest BLOCKED dopóki dostępna nie będzie uprawniona ścieżka
  - V2.0 nie migruje automatycznie main ani pozostałych gałęzi
next_action: zweryfikować dokumentacyjny CI na finalnym SHA; potem zsynchronizować tekst ustawień Projektu GPT i zachować inne historyczne gałęzie bez zmian
```

## Potwierdzony punkt bazowy
Commit v2.0 `26e62c860b7e17b7935dbf7553f7dce3fc6446a9` jest rodzicem tej pracy. Dokumentacyjny CI run `41284565319`, attempt 1, zakończył się sukcesem na dokładnie tym SHA; wszystkie 6 jobów zakończono sukcesem. To wynik bazowy, nie walidacja zmian tej gałęzi.

## Ustalenia i granice
- Lista starszych materiałów i ich status jest w `docs/ai/LEGACY_INSTRUCTIONS.md`.
- Zachowaj stare treści/historyczne commity; nie usuwaj plików tylko dlatego, że są nieaktualne.
- Nie migruj main ani gałęzi produktowych przy okazji dokumentacyjnego zadania.
- Nie twierdź, że ustawienia Projektu ChatGPT zostały zaktualizowane — dostępny tekst jest propozycją do wklejenia.
- Nie buduj APK dla zmian czysto dokumentacyjnych.
