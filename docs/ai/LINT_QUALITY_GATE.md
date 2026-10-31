# Etap 5/9 — bramka lint oparta na rzeczywistych danych i dowód CI

## Stan wyjściowy — nie oznacza naprawienia ustaleń

Dowód: GitHub Actions https://github.com/laszko14/nira-browser/actions/runs/41253701343, commit 7f27f5309a4097c4a620c717684c563ff7f321dc, artefakt shared-lint-reports, plik lint-results-debug.xml, SHA-256 e79e415b38e65e13a33e276b4b6e67671eb9b2436b2d1b5869710425937399a8.

| Severity | Historyczny stan |
|---|---:|
| Fatal | 1462 |
| Error | 164 |
| Warning | 508 |
| Hint | 18 |
| Razem | 2152 |

1626 Fatal/Error występowało przy SUCCESS joba Gradle lint, ponieważ w app/build.gradle jest abortOnError=false. Największe grupy to ExtraTranslation 1439, MissingDefaultResource 23 oraz MissingTranslation 162. Nie maskujemy, nie kasujemy ani nie uznajemy ich za naprawione.

## Co wykonuje nowe CI

1. Po Gradle :app:lintDebug narzędzie scripts/lint-regression.cjs wczytuje aktualny lint-results-debug.xml. Brak lub nieprawidłowy raport powoduje FAIL.
2. Historyczny baseline w scripts/lint-stage4-baseline.cjs to obserwowany agregat 178 kombinacji severity + lint ID + plik w 124 plikach. Dane są spakowane bezstratnie jako deflate/base64 JSON, a metadane zawierają SHA, run ID i sumy. Nie jest generowany automatycznie od nowa.
3. Jeśli liczba ustaleń w dowolnej kombinacji plik / severity / reguła wzrośnie, test kończy się błędem niezależnie od abortOnError=false. Raport przyrostów i sum trafia do GITHUB_STEP_SUMMARY.
4. Kontrola negatywna obejmuje nowy rodzaj błędu, dodatkowy przypadek istniejącego ostrzeżenia, zmianę severity, uszkodzony XML i ścieżki spoza repo.
5. Zadanie quality-evidence działa zawsze po process-docs oraz shared-tests, raportuje run, próbę, event, SHA i ich wyniki. Brak sukcesu wymaganej kontroli oznacza FAIL. Cztery potomne joby shared-tests nadal muszą być weryfikowane osobno.

Ograniczenie: jest to kontrola licznika dla pliku i reguły, nie tożsamości konkretnego komunikatu. Gdy jeden stary problem zniknie i powstanie inny o tym samym ID i severity w tym samym pliku, licznik może pozostać identyczny. Nie traktuj tego jako pełnego audytu bezpieczeństwa lub wykonania testu na urządzeniu.

## Zasady dalszej pracy

Nie zmieniaj baseline tylko po to, aby ponownie otrzymać zielone CI. Naprawy historycznych problemów tłumaczeń i brakujących zasobów wymagają oddzielnych zmian i testów. Sam lint Gradle SUCCESS nie oznacza braku błędów; pełne CODE_VERIFIED wymaga wszystkich obowiązkowych testów na końcowym SHA. DEVICE_VERIFIED oraz APK_VERIFIED pozostają osobnymi dowodami. Nie uruchamiaj APK, PR ani merge bez osobnej zgody.
