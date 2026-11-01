# Etap 6/9 — niezależna od opisu autora kontrola wymagań, diffu i regresji

Przejrzano 8 commitów procesu między `7cfa2d13a467235102666822d40631fe1818c9b4` a `f2bc94a042beb488ef736d9d8b802bc631499777` (25 zmienionych plików). Przegląd obejmował wymagania oparte na `AGENTS.md`, checkpoint, oryginalne implementacje i testy; nie opierał się na samym opisie autora. To techniczne ponowne review, **nie druga osoba ani odrębny model**.

| ID | Waga | Wykryta luka i minimalny kontrprzykład | Korekta i regresja |
|---|---|---|---|
| R6-01 | HIGH | `normalizeFile('/home/runner/work/nira-browser/nira-browser/other/app/src/main/Fake.kt')` brał ostatnie `/app/` mimo obcego podkatalogu. Analogicznie `/tmp/nira-browser/app/...` mógł udawać checkout. | W CI akceptuj tylko ścieżki w `GITHUB_WORKSPACE`; w raporcie lokalnym akceptuj tylko poprawny końcowy segment repo. Negatywne testy obcego katalogu, ścieżki spoza checkout i `..`. |
| R6-02 | HIGH | Manualny `release` nie wymagał SUCCESS `regression-tests`, a checkout `inputs.tag` nie musiał odpowiadać SHA testowanemu przez CI. | `release` zależy od `regression-tests` i przed budową wymaga `git rev-parse HEAD == GITHUB_SHA`. Test mutacji wykrywa usunięcie którejkolwiek bariery. |
| R6-03 | OTWARTE / poza zakresem | Legacy workflow innych aktywnych gałęzi mają potencjalne push-triggered APK. | Zgodnie z `CI_MIGRATION.md` pozostały nietknięte; bez ich migracji nie wolno zakładać bezpieczeństwa innych gałęzi. |
| R6-04 | OGRANICZENIE | Lint mierzy liczność grup `plik/severity/rule`, nie tożsamość komunikatu; wymiana diagnozy w tej samej grupie może pozostać niewidoczna. | Dług techniczny jawny, istniejące 2152 ustaleń nie oznacza naprawienia. |

## Plan dowodu końcowego

- Sprawdź `process-docs`, `shared-tests/javascript`, `shared-tests/unit`, `shared-tests/android-test-compile`, `shared-tests/lint` i `quality-evidence` osobno na końcowym SHA; wymagane `completed/success`, `skipped` nie jest PASS.
- Zestaw testów negatywnych R6-01/R6-02 plus pełne JavaScript, JVM/Robolectric, kompilacja testów Android i lint są obowiązkowymi kontrolami tego etapu.
- Test urządzenia: NOT_TESTED. APK: NOT_BUILT. Brak PR/merge.
- Wpis wyniku workflow i finalnego SHA wyłącznie w raporcie końcowym, bez dodatkowego commita.

**Granica niezależności:** przegląd wymagań/diffu/testów i automatyczne kontrole to dowód techniczny, ale nie zastępują recenzji osoby trzeciej. Zasady gałęzi procesu nie są automatycznie aktywne na main ani starszych tagach.
