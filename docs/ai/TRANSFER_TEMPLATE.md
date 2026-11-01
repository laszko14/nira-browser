# V3 — przekazanie do nowej rozmowy
Uzupełnij wyłącznie z bieżącego `docs/AI_HANDOFF.md` na właściwej gałęzi i live GitHub HEAD; historyczne archiwum nie jest checkpointem.

```text
Kontynuuj [task_id] w [repo], linia [product_line].
Tryb: [workflow_mode]; branch: [branch].
Obserwowany HEAD: [observed_head_sha]; bazowy SHA: [base_sha].
Sprawdź live HEAD, AGENTS.md i docs/AI_HANDOFF.md z jawnego SHA.
Wymaganie: [acceptance_source]; required SOURCE CI: [required_ci].
Zrobione: [completed]; pozostało: [remaining]; następnym działaniem jest [next_action].
Dowody i błędy: [verification], [known_failures], [blockers].
Nie powtarzaj niezmienionych dowodów bez przyczyny; po nowym SHA zrewiduj ich ważność.
Bez merge, publicznego release, destrukcji i zmiany certyfikatu.
Jeśli APK jest w zakresie, jedynie exact-SHA owner PR review wg docs/ai/APK_CHAT_GITHUB_REVIEW_TRIGGER.md; doc-only bez APK. Raport i STOP.
```
