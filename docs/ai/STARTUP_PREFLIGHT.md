# Zavro Clean V3.2 — kontrola wejścia i dowód źródeł

To jedyna procedura G1 dla kolejnych zadań Zavro Clean. Nie zastępuje `AGENTS.md`, a pliki projektu ChatGPT są **wskazówkami** do pobrania aktualnych źródeł, nie drugim kanonem polityki.

## Każde nowe zadanie ChatGPT / Codex

1. Określ produkt, rodzaj zadania i upoważnienie. Repo: `laszko14/nira-browser`; Clean jest osobno od x MPV.
2. Pracuj na jednej **stałej** gałęzi rozwoju Clean: `feat/zavro-clean-mpv-play-diagnostics-stage1-20261110` (nie na `main`). Najpierw odczytaj jej pełny live HEAD przez GitHub, potem z dokładnego SHA `docs/ai/ACTIVE_CLEAN_LINE.md`; porównaj nazwę aktywnej gałęzi i checkpoint. Nie twórz gałęzi kolejnego etapu ani nie bazuj na historycznej gałęzi z pamięci. Zmiana kanonicznej gałęzi tylko po osobnej decyzji użytkownika i aktualizacji źródeł startowych.
3. Z **tego dokładnego SHA** przeczytaj `AGENTS.md`, `docs/AI_HANDOFF.md` i instrukcje katalogowe dotyczące modyfikowanych plików. Porównaj pole `branch:` checkpointu z bieżącą gałęzią; `observed_head_sha` jest historycznym snapshotem, nie może zastąpić live SHA.
4. Dobierz tylko potrzebne dokumenty przez `scope` z `scripts/ai-preflight.cjs`: `DEFAULT`, `VIDEO_SEARCH`, `UI_GESTURES`, `DATA_PRIVACY`, `CI_TOOLING`, `APK`. Dla APK sprawdź osobny kanon autoryzacji, ale **nie buduj** APK bez upoważnienia.
5. Przed zapisem sprawdź ponownie HEAD i uprawnienia. Przy niespójności: `PREFLIGHT_BLOCKED`, zamiast zgadywania lub zastępowania źródeł plikami z Projektu.
6. Przy implementacji zapisz checkpoint przed końcowym commitem, wykonaj wymagane testy i zweryfikuj SOURCE oraz PR CI na końcowym HEAD. Błędy i regresje rozliczaj w `docs/ai/ERROR_CASES.json`. Dla podpisanego APK odczytaj ostatni potwierdzony numer z live GitHub Actions (bazowy odnośnik w `ACTIVE_CLEAN_LINE.md`), sprawdź monotoniczność i podaj tylko link do zakończonego runu oraz nazwę artefaktu; bez pobierania APK/ZIP. Raportuj uczciwie `CI_PENDING` oraz brak dowodów.

**Przykładowy minimalny zapis w zadaniu**: `branch + live_sha + read(AGENTS@sha, AI_HANDOFF@sha) + selected_paths + scope + last_head_check`. To dowód przebiegu pracy tylko wtedy, gdy odczyty rzeczywiście wykonano, nie gdy ktoś przekleił gotowy tekst.

## Lokalna kontrola checkoutu — narzędzie pomocnicze Codex

```bash
node scripts/ai-preflight.cjs feat/zavro-clean-mpv-play-diagnostics-stage1-20261110 LIVE_40_HEX_SHA VIDEO_SEARCH
```

Skrypt wywołany w **rzeczywistym** checkoutcie porównuje lokalny HEAD, branch i zawartość wymaganych dokumentów, w tym checkpoint. Wynik `SOURCE_SNAPSHOT_VERIFIED` zawiera skróty SHA-256 odczytanych plików i wybrane źródła. Nie jest dowodem, że model je zrozumiał, ani potwierdzeniem sieciowego live HEAD; należy osobno sprawdzić remote.

GitHub Actions na SOURCE używa `node scripts/ai-preflight.cjs --ci "$GITHUB_REF_NAME" "$GITHUB_SHA" CI_TOOLING`. Testuje checkout i instrukcje, **nie potrafi potwierdzić odczytów ChatGPT**. Testy negatywne odrzucają niezgodny SHA, gałąź, handoff i brakujące źródła. Nie przyznawaj PASS gdy `PREFLIGHT_BLOCKED`.

## Źródła Projektu ChatGPT

Trzy krótkie pliki `docs/ai/project-sources/ZAVRO_*.md` są statycznym indeksem wiedzy, **nie kopią `AGENTS.md` ani `AI_HANDOFF.md`**. Są przeznaczone do ręcznego dodania do Źródeł projektu; samo zapisanie ich na GitHubie nie dodaje ich automatycznie do ustawień projektu. Zapisy ChatGPT są zależne od dostępnych narzędzi; GitHub Actions nie czyta plików Projektu ChatGPT.

## Kryteria akceptacji

- G1: obowiązkowe źródła i checkpoint z jednego live SHA, brak niejawnego `main`.
- CI: testy skryptu i integralności trzech plików oraz realny checkout w `process-docs`; cały SOURCE CI i PR CI zielone na tym samym finalnym SHA.
- Zakres: zero zmian `app/`, bez APK, merge, release, podpisu ani resetowania danych.
- Ograniczenie: nie istnieje zdalny mechanizm gwarantujący, że każdy nowy czat sam pobierze pliki. W Projektach wymagane są poprawne instrukcje projektu oraz dostęp do GitHub connector.
