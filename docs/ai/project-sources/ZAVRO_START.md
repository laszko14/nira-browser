# Zavro Clean — punkt startowy rozmów

**Źródło orientacyjne dla Projektu ChatGPT „Przeglądarka”, nie polityka i nie bieżący checkpoint.**

- Repozytorium: `laszko14/nira-browser`.
- Jedyna aktywna gałąź rozwoju Clean: `feat/zavro-clean-mpv-play-diagnostics-stage1-20261110`. Kontynuuj ją fast-forward w każdej rozmowie i kolejnym etapie; nie zakładaj osobnych gałęzi. Jedyny kanoniczny opis linii: `docs/ai/ACTIVE_CLEAN_LINE.md` na live HEAD tej gałęzi. Zmiana gałęzi wymaga osobnej decyzji użytkownika. Odrębnie od `main` i x MPV.
- Bieżące wymagania dla agentów: `AGENTS.md` i `docs/AI_HANDOFF.md` **z tego samego aktualnego SHA**.
- V3.2: `docs/ai/STARTUP_PREFLIGHT.md` określa selekcję instrukcji i kontrolę rozpoczęcia. Dla prac lokalnych: `scripts/ai-preflight.cjs`. Test checkoutu nie potwierdza, że czat przeczytał dokumenty.
- Błędy CI i testów naprawiaj w tym samym zadaniu: triage konkretnego joba/kroku/logu, poprawka, regresje i wymagane zielone CI na końcowym SHA przed kolejnym etapem. Kanon: `docs/ai/TEST_POLICY.md`, `docs/ai/ERROR_LEARNING.md`.
- Nie przyjmuj historycznego SHA z tego pliku za aktualny. Dokumenty ze Źródeł mają niższy priorytet niż bieżący kod i instrukcje GitHub.
- Do pracy z GitHubem używaj bezpośredniego konektora GitHub i GitHub Actions, nie TinyFish ani zewnętrznych przeglądarek.
- Brak automatycznego merge, publicznego wydania, force-push, zmiany podpisu albo usuwania danych. APK tylko na upoważnienie i przez zaakceptowaną recenzję PR. **Nie pobieraj APK ani ZIP**: podaj link do uruchomienia GitHub Actions i nazwę artefaktu, a użytkownik sam go pobierze. Numer wersji weryfikuj w aktualnym buildzie i ostatnim potwierdzonym runie (punkt odniesienia: v1226).

Ten plik trzeba **ręcznie dodać do Źródeł projektu ChatGPT**; repozytorium go tam nie publikuje samoczynnie.
