# AI_HANDOFF — Zavro Clean · synchronizacja instrukcji v2.0
> Checkpoint gałęzi procesu. Inne gałęzie mają własne handoffy.

```yaml
task_id: DOCS-INSTRUCTION-ALIGN-20261101
workflow_mode: documentation_only
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: improve/zavro-clean-chatgpt-github-process-20261101
verified_base_sha: 26e62c860b7e17b7935dbf7553f7dce3fc6446a9
change_parent_sha: b6b29be841d7bb988a103fc8b46f4c59cb58d71c
ci_status: sprawdź wymagany workflow dla aktualnego HEAD; nie dziedzicz PASS z bazowego SHA
apk: NOT_BUILT
device: NOT_TESTED
scope: instrukcje i checkpointy; bez zmian kodu, danych, podpisu, merge i publikacji
blockers:
  - Ustawienia Projektu ChatGPT nie są edytowalne przez dostępne narzędzia; aktualny tekst do wklejenia: docs/ai/CHATGPT_PROJECT_INSTRUCTIONS.md
  - Dostępny GitHub connector nie ma akcji workflow_dispatch; podpisane APK jest BLOCKED bez uprawnionej ścieżki
  - V2.0 nie migruje automatycznie main ani pozostałych gałęzi
next_action: sprawdź CI na bieżącym pełnym HEAD; zsynchronizuj instrukcje Projektu GPT ręcznie; inne gałęzie migruj tylko w osobnym zakresie
```

## Potwierdzony punkt bazowy
V2.0 bazuje na `26e62c860b7e17b7935dbf7553f7dce3fc6446a9`. Run `41284565319`, attempt 1, miał `completed/success` na tym SHA i wszystkie 6 jobów zakończyło się sukcesem. Ten wynik nie jest dowodem CI dla późniejszych commitów.

## Wynik tej naprawy
- `AGENTS.md` wyjaśnia pierwszeństwo v2.0, status starych promptów i zakres migracji gałęzi.
- `docs/ai/CHATGPT_PROJECT_INSTRUCTIONS.md` wprost opisuje warunek dispatch i blokadę APK.
- `docs/ai/LEGACY_INSTRUCTIONS.md` kataloguje historyczne zasady; ich treści nie kasujemy.
- Ustawienia GPT nie zostały zmienione przez repozytorium. Nie deklaruj ich synchronizacji bez ręcznego wklejenia.
- Czysto dokumentacyjne zmiany nie wymagają APK.
