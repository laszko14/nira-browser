# Zavro Clean — punkt startowy rozmów

**Źródło orientacyjne dla Projektu ChatGPT „Przeglądarka”, nie polityka i nie bieżący checkpoint.**

- Repozytorium: `laszko14/nira-browser`.
- Linia robocza V3: `feat/zavro-clean-v3-20261101`. Jest oddzielna od `main` i od produktu x MPV. Przed **każdym** zadaniem potwierdź z GitHuba aktualną gałąź i pełny HEAD SHA; gałąź może zostać zastąpiona w przyszłości.
- Bieżące wymagania dla agentów: `AGENTS.md` i `docs/AI_HANDOFF.md` **z tego samego aktualnego SHA**.
- V3.2: `docs/ai/STARTUP_PREFLIGHT.md` określa selekcję instrukcji i kontrolę rozpoczęcia. Dla prac lokalnych: `scripts/ai-preflight.cjs`. Test checkoutu nie potwierdza, że czat przeczytał dokumenty.
- Błędy CI i testów naprawiaj w tym samym zadaniu: triage konkretnego joba/kroku/logu, poprawka, regresje i wymagane zielone CI na końcowym SHA przed kolejnym etapem. Kanon: `docs/ai/TEST_POLICY.md`, `docs/ai/ERROR_LEARNING.md`.
- Nie przyjmuj historycznego SHA z tego pliku za aktualny. Dokumenty ze Źródeł mają niższy priorytet niż bieżący kod i instrukcje GitHub.
- Do pracy z GitHubem używaj bezpośredniego konektora GitHub i GitHub Actions, nie TinyFish ani zewnętrznych przeglądarek.
- Brak automatycznego merge, publicznego wydania, force-push, zmiany podpisu albo usuwania danych. APK tylko na osobne upoważnienie i przez zaakceptowaną recenzję PR.

Ten plik trzeba **ręcznie dodać do Źródeł projektu ChatGPT**; repozytorium go tam nie publikuje samoczynnie.
