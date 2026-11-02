# V3.2 — weryfikacja zamknięcia błędów, bezpieczeństwa i testu nowej rozmowy

Data przeglądu: 2026-11-01. Repo `laszko14/nira-browser`, gałąź `feat/zavro-clean-v3-20261101`. Odczytany przed tym raportem HEAD: `5458e884ebb5b135db1caa7f2f3f7a0f4ddfe43c` (pełny HEAD po zapisie trzeba sprawdzić ponownie). Baza aplikacji Clean v1207, bez zmian w `app/`. Raport jest dowodem **samokontroli**, nie niezależnym review.

## 1. Rejestr błędów — sprawdzenie napraw

| Zdarzenie | Przyczyna | Commit naprawy | Dowód testów |
| --- | --- | --- | --- |
| `CI-41335018971-1-124933016011` | Fixture zakładało jeden wpis V3.1 | `75d59b0ba47269b84e4e027556fd59dcbee66287` | SOURCE `41335154679` SUCCESS; nowy test działa przy rosnącym rejestrze |
| `CI-41337049270-1-124938975803` | Plik ZAVRO_FUNKCJE nie wymieniał wprost GitHuba | `0138e1e73de2b3fd4677a774e6adea0cd608b717` | `41337335979` SUCCESS, `scripts/project-sources.test.cjs` |
| `CI-41337163423-1-124939468643` | Fixture V3.1 mieszało coverage dwóch zadań | `5458e884ebb5b135db1caa7f2f3f7a0f4ddfe43c` | `41337335979` SUCCESS, `scripts/error-learning.test.cjs` |

Rzeczywiste porównania commitów potwierdzają każdą zmianę wskazanych testów/plików. Stan rejestru po zmianie: **OBSERVED 5 / RECORDED 5 / FIXED 4 / OPEN 1**. Otwarty pozostaje `ERR-20261101-0001` (zgłoszenie użytkownika: część dawnych błędów nie trafiła do rejestru). Brak treści tych dawnych zdarzeń i brak reprezentatywnej próbki nowych rozmów — nie wpisuj nieznanych przypadków ani nie zamykaj tego zgłoszenia tylko na podstawie zielonego CI. `coverage` dla obu zadań pozostaje `ACTIVE` do wykonania próby praktycznej.

## 2. Przegląd zabezpieczeń — samokontrola, G4 niezamknięte

Przeczytano z tego samego SHA: `AGENTS.md`, `docs/AI_HANDOFF.md`, `docs/ai/STARTUP_PREFLIGHT.md`, `.github/workflows/apk-release.yml`, `.github/workflows/zavro-ai-process-docs.yml`, `scripts/authorize-private-apk-review.cjs`, `scripts/ci-run-receipt.cjs`, `scripts/ci-workflow-inventory.cjs`, `scripts/capture-ci-errors.cjs`, `scripts/ai-preflight.cjs` i testy. Potwierdzono:

- SOURCE CI nie ma sekretów podpisu; `quality-evidence` ma `contents: read` i `actions: read`. Raporty błędów nie wykonują pushu, nie pakują APK i zbierają metadane, a nie pełne logi.
- `apk-release.yml`: job `build` ma warunek `pull_request_review`, właściciela, komentarza, tego samego repo, oraz w kodzie oddzielny walidator SHA/PR i weryfikację SOURCE CI. Zwykły push/PR nie uruchamia podpisu. Publiczne `release` wymaga odrębnego `workflow_dispatch` i frazy `Publikuj`.
- Preflight na SOURCE wymaga `GITHUB_SHA`, zgodnego checkoutu i czytelnego `AGENTS.md` / `AI_HANDOFF.md`; testy negatywne odrzucają brak pliku, złe SHA i gałąź. To nie dowodzi, że nowa rozmowa rzeczywiście odczytała dokumenty.
- Dwa końcowe przebiegi `41337335979` (SOURCE) i `41337339338` (PR integration) zakończyły się `completed/success` na `5458e884ebb5b135db1caa7f2f3f7a0f4ddfe43c`; PR pomija joby `build` i `release`. Nie przeprowadzono testu sekretów, podpisu ani urządzenia.

**Granice / ryzyka pozostające:** brak niezależnej recenzji PR #119 (na przeglądanym SHA lista recenzji była pusta). Kod zapisuje nazwy jobów/kroków z GitHub API w artefakcie; aktualne nazwy są statyczne, ale filtr znaków sterujących sam nie zapewnia redakcji przyszłych nazw zawierających dane wrażliwe. Nie potwierdzono produkcyjnej aktualizacji APK, certyfikatu na urządzeniu ani rzeczywistej kontroli uruchomienia owner review. **SECURITY_REVIEW_PENDING** — ta samokontrola nie zastępuje niezależnego recenzenta.

## 3. Próba nowej rozmowy — instrukcja do wykonania w nowym czacie

W tej samej rozmowie odczytano aktualny branch i obowiązujące pliki GitHub z jawnego SHA — **CURRENT_CHAT_G1_OBSERVED**. SOURCE CI sprawdziło preflight i trzy pliki źródłowe, ale nie jest to test niezależnej, nowo otwartej rozmowy.

Wklej w **nowej rozmowie w projekcie Przeglądarka** (wymagany połączony GitHub):

> TEST V3.2 — TRYB ANALYZE, NICZEGO NIE ZAPISUJ, BEZ APK. Bez podpowiedzianego SHA pobierz bezpośrednio z GitHuba aktualny pełny HEAD dla `feat/zavro-clean-v3-20261101`. Odczytaj `AGENTS.md`, `docs/AI_HANDOFF.md`, `docs/ai/STARTUP_PREFLIGHT.md` z tego samego SHA. Dobierz dodatkowe dokumenty dla zakresu `VIDEO_SEARCH` i `CI_TOOLING`, ale odczytaj tylko niezbędne fragmenty. Podaj: bieżący SHA, rzeczywiście przeczytane ścieżki, otwarte i zamknięte wpisy `docs/ai/ERROR_CASES.json`, zgodność checkpointu z gałęzią, oraz ograniczenia dowodów. Jeśli nie możesz sprawdzić GitHuba, zwróć `PREFLIGHT_BLOCKED`. Nie implementuj niczego.

Akceptacja: rzeczywisty odczyt ze źródeł, jeden wspólny live SHA, brak pomylenia `main` i x MPV, niezmyślone liczby z rejestru, brak zapisu i APK. Weryfikację porównaj z nowym live SHA **po teście**. Nie przeprowadzono takiej nowej rozmowy w ramach obecnego narzędzia: **FRESH_CHAT_NOT_TESTED**. Pełne potwierdzenie 10/10 zgłoszeń wymaga osobnego obserwowania kolejnych faktycznych przypadków.

## 4. Pozostałe zależności

Trzy `docs/ai/project-sources/ZAVRO_*.md` są przygotowane, lecz dodanie do Źródeł Projektu ChatGPT jest operacją po stronie użytkownika. Nie deklaruj instalacji bez potwierdzenia ustawień projektu. Bez merge, APK, zmiany certyfikatu i danych aplikacji.
