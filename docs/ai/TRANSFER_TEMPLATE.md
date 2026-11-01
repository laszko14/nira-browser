# Przekazanie zadania — zwykły ChatGPT / Codex

Skopiuj poniższy krótki tekst do nowego czatu. **Uzupełnij z bieżącego `docs/AI_HANDOFF.md` na właściwej gałęzi**, nie z pamięci ani archiwum. Nie wstawiaj przypadkowego HEAD innej gałęzi.

```text
Kontynuuj zadanie [task_id] w laszko14/nira-browser (linia: [product_line]).
Tryb: [workflow_mode]. Gałąź: [branch].
Ostatni zaobserwowany HEAD: [observed_head_sha]. Baza zadania: [base_sha].
Przeczytaj AGENTS.md i docs/AI_HANDOFF.md z tego ref; porównaj aktualny HEAD przed zmianą.
Wymagania: [acceptance_source]. Wymagany CI: [required_ci].
Ostatni faktyczny run: [URL, attempt, conclusion lub CI_PENDING — odczytane na żywo].
Zrobione: [krótko, z dowodami]. Pozostało: [next_action]. Blokery: [blockers].
Nie ponawiaj dobrych kontroli bez zmiany wejść; nie odczytuj całego archiwum.
Bez merge, publicznego release i destrukcyjnych zmian. IMPLEMENT obejmuje prywatny APK po zielonym CI przy uprawnionym dispatch; ANALYZE nie. Krótki raport i STOP.
```

## Kontrola poprawności przed wznowieniem

- Potwierdź, że branch i zadanie pasują do zlecenia; `observed_head_sha` może być starszy niż live HEAD.
- Nie uznawaj `CI_PENDING`, `skipped` ani `NOT_TESTED` za PASS; sprawdź prawidłowy SHA, workflow i wymagane joby.
- Dla pracy równoległej użyj checkpointu **jej własnej gałęzi**, nie ostatnio czytanego checkpointu tej gałęzi.
- Jeżeli po dwóch próbach nie ma nowego dowodu, zmień metodę albo raportuj rzeczywistą blokadę.
