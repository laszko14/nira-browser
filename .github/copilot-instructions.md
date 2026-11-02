# Zavro Clean — wejście Copilota
Kanon polityki: [AGENTS.md](../AGENTS.md); bieżący stan wyłącznie w [AI_HANDOFF.md](../docs/AI_HANDOFF.md) odczytanym z prawidłowego live ref. Clean i x MPV są oddzielne. Weryfikuj pełny HEAD SHA, własność zmian i jawny zakres.
Kontrola startowa V3.2: [STARTUP_PREFLIGHT.md](../docs/ai/STARTUP_PREFLIGHT.md); w lokalnym checkoutcie pomocniczy `scripts/ai-preflight.cjs`, który nie zastępuje czytania źródeł.
Procedura implementacji: [DEVELOPMENT_WORKFLOW.md](../docs/ai/DEVELOPMENT_WORKFLOW.md); testy: [TEST_POLICY.md](../docs/ai/TEST_POLICY.md); bramki: [QUALITY_GATES.md](../docs/ai/QUALITY_GATES.md).
Nie uruchamiaj podpisu przy push/PR/tag ani przez prywatny dispatch. Prywatny APK wyłącznie według [autoryzowanej recenzji PR](../docs/ai/APK_CHAT_GITHUB_REVIEW_TRIGGER.md), po końcowym zielonym CI i tylko gdy w zakresie zadania. Bez merge, release, force-push, usuwania profili i danych; zachowaj certyfikat.
