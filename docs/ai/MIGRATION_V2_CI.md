# Historia V2 i kontrolowana migracja do V3
V2 historycznie wprowadzono na `7a60fbcb8463afca3526675a8d47dd28ce89b9be`. Nie przenoś automatycznie polityki na main, x MPV lub starsze refy.

## V3 — baza i zakres
- Źródło Clean v1207: `feat/zavro-clean-search-redesign-v2-20261101`, odczytany HEAD `b161b9403425435e106498c382da34b5e3cb3d58`.
- Izolowana gałąź V3: `chore/zavro-clean-codex-v3-20261101`; status: migracja w weryfikacji, nie aktywuj jako CODE_VERIFIED bez końcowego SOURCE CI.
- Jedyna prywatna ścieżka z czatu: exact-SHA owner PR review. Zwykły push, PR, tag i dispatch nie autoryzują prywatnego podpisu.
- Wymagany komplet dokumentów: DEVELOPMENT_WORKFLOW, QUALITY_GATES, TEST_POLICY, EXCEPTIONS_AND_TOOLS, CHECKPOINT_POLICY, ERROR_LEARNING, APK_RELEASE, LEGACY_INSTRUCTIONS i ten plik.
- Zmiany są doc/process-only: bez kodu aplikacji, APK, Secrets, certyfikatu, wersji ani automatycznego merge.
- Finalny hash zestawu polityki i konkretny CI SOURCE należy odczytać z rzeczywistego commita; sam napis V3 nie potwierdza wdrożenia.
- Retrospektywne zapisy pilotów T01–T16 są nienaruszalne; pilot V3 wymaga oddzielnych rzeczywistych pomiarów.

- Rejestr pomiarowy: `PILOT_16.json` schema v2 z nienaruszonymi T01–T16 i osobnym, początkowo pustym `instruction_experiments`; bez fikcyjnych wyników.

## V3 cleanup — historyczne kompilacje i klasyfikacja duplikatów (2026-11-01)
Audyt 71 workflow na SOURCE SHA `c30a21b834c46264187609c9039f9d0ed1e01ed5` wykazał 9 dodatkowych, historycznych ścieżek podpisywania APK (v1191–v1202). Usunięto z **aktywnego katalogu Actions na gałęzi V3**:
- `.github/workflows/video-search-build-once.yml`
- `.github/workflows/video-search-p3-regression.yml`
- `.github/workflows/zavro-audit60-gecko-build-g4b.yml`
- `.github/workflows/zavro-audit60-gecko-update-g4c.yml`
- `.github/workflows/zavro-t03-v1199-signed-update.yml`
- `.github/workflows/zavro-v1195-signed-arm64-build.yml`
- `.github/workflows/zavro-video-results-fuzzy-v1202-update.yml`
- `.github/workflows/zavro-video-results-v1200-update.yml`
- `.github/workflows/zavro-video-results-v1201-update.yml`

Zachowano zawartość w historii Git (SHA powyżej) i testy historyczne bez podpisywania, m.in. audit60 stage19B. Zmiany na tej gałęzi **nie wyłączają plików w starych refach ani na main**; przed wznowieniem starych branchy trzeba sprawdzić ich rzeczywiste workflow. Kanoniczny `apk-release.yml` nadal wymaga komentarzowej recenzji właściciela PR na dokładnym SHA. Samo `push`, `workflow_dispatch` i PR nie uruchamiają podpisywania.

`scripts/ci-workflow-inventory.cjs` i testy negatywne w `scripts/check-ci-workflows.test.cjs` pilnują, że **tylko** `apk-release.yml` może mieć aktywny etap assemble lub dostęp do sekretów podpisu. Wyjątek dla publicznego release pozostaje wewnątrz tego kanonicznego workflow i wymaga osobnego jawnego polecenia.

Dwa uruchomienia wspólnych testów, SOURCE (`push`) i PR (`pull_request`), nie są tożsame: pierwsze testuje HEAD gałęzi, drugie potencjalny commit integracyjny GitHub. Nie usuwaj żadnej z tych bramek bez sprawdzonego równoważnego dowodu SOURCE i INTEGRATION. Z tego powodu **nie uznano tej pary za bezpieczny duplikat do usunięcia**.
