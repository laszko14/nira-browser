# Zavro Clean V3 — narzędzia i wyjątki
| Przypadek | Zachowanie |
|---|---|
| Brak praw GitHub lub brak właściciela gałęzi | Nie pisz, raportuj konkretną blokadę |
| HEAD zmieniony przez równoległego autora | Przed zapisem przerwij, ustal nową bazę; żadnego force/reset/stash |
| Brak lokalnego JDK/Gradle/ADB | Nie modyfikuj poprawnego kodu; użyj wiarygodnego CI lub NOT_TESTED |
| CI pending/skipped/failure lub zły checkout | Nie deklaruj CODE_VERIFIED; analiza przyczyny i właściwe wymagane joby |
| Brak niezależnego review wysokiego ryzyka | Ujawnij ograniczenie, nie nazywaj self-review niezależnym |
| Brak autoryzowanej recenzji PR/Secrets/APK verifier | APK_BLOCKED; bez dispatch, podpisu lokalnego i obejścia |
| Nieudana próba z tym samym fingerprintem | Według TEST_POLICY zmień metodę, nie powtarzaj ślepo |
| Konflikt AGENTS, dokumentów i aktualnego użytkownika | Obowiązuje jawny zakres użytkownika i zasady platformy; nie kopiuj obcych promptów |
Dla projektowych skryptów: bash, nie fish. Nie dodawaj automatycznie repo Skills, hooks, MCP, `.codex/config.toml` ani `model_instructions_file`. Ewentualne parametry `model`, `model_reasoning_effort` i limit subagentów są opcją zaufanego klienta, nie ustawieniem repo; nie są dowodem oszczędności limitu. Bez TinyFish i innych automatycznych przeglądarek GitHub.
