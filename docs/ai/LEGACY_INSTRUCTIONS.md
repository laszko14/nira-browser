# Rejestr starych instrukcji — Zavro Clean
Stan: 2026-11-01. Ten plik odróżnia aktualną politykę v2.0 od historycznych materiałów.

## Jedyna aktywna polityka na gałęziach po migracji
- `AGENTS.md` oraz wskazane przez niego aktywne dokumenty `docs/ai/*`.
- `docs/ai/CHATGPT_PROJECT_INSTRUCTIONS.md` jest tekstem do wklejenia w ustawienia projektu GPT; aktualizacja pliku repo nie zmienia tych ustawień.
- `docs/AI_HANDOFF.md` jest checkpointem tylko dla bieżącej gałęzi i SHA.

## Materiały archiwalne / nie stosować jako globalnych instrukcji
- Dawne `AGENTS.md`/Alpha Browser na `main`: historyczne zasady pierwotnego forka, nie instrukcje Zavro Clean v2.0.
- `CODEX_RULES.md` i starszy plik Library `AGENTS.md`: wcześniejsze preferencje dla Codexa, w tym osobne polecenie kompilacji APK; nie są zgodne z domyślnym kontraktem IMPLEMENT v2.0.
- Prompty projektowe z komendami „Dalej” / „Kompiluj” (m.in. stare prompty UI i MME): zachować jako specyfikacje historycznych zadań. Nie traktować ich jako bieżącej polityki procesu.
- Prompty Zavro x MPV: dotyczą odrębnej linii produktu i nie mogą rozszerzać zakresu Zavro Clean.
- Raporty audytowe, dokumenty planów i dawne handoffy: źródła historii, nie bieżący status. Weryfikuj kod/CI i aktualny handoff gałęzi.

## Zasada bezpiecznego porządkowania
Nie kasuj historii Git ani oryginalnych specyfikacji. Gdy archiwizujesz kopię w ChatGPT Library, zmień wyłącznie jej nazwę/dostępność, nie treść. Nie archiwizuj specyfikacji, które nadal są potrzebne do aktywnego zadania; oznacz jedynie ich procesowe zasady jako historyczne.
