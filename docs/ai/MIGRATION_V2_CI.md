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
