# Zavro Clean V3 — testy, CI, triage i regresje

## Jedna pętla do zakończenia etapu

1. Zapisz źródłowy HEAD SHA, workflow/run/attempt/job, komendę i pierwszy istotny FAIL. Najpierw odczytaj stan, kroki i konkretny fragment logu; pobieraj tylko potrzebne dowody.
2. Sklasyfikuj problem na podstawie dowodów: kod produkcyjny, test/fixture, workflow/konfiguracja, infrastruktura/środowisko, uprawnienia, zależność zewnętrzna albo nieustalony. Odróżniaj podejrzenie od potwierdzonej przyczyny.
3. Sprawdź rejestr `docs/ai/ERROR_CASES.json` i wcześniejsze CI pod kątem tego samego fingerprintu. Nie twórz nowej reguły dla duplikatu ani chwilowego ponowienia bez nowych informacji.
4. Postaw hipotezę różnicującą możliwe przyczyny i odtwórz najmniejszy kontrprzykład. Test RED dodaj, gdy jest wykonalny.
5. Napraw najmniejszy właściwy element. Nie wyłączaj testów, nie osłabiaj asercji/lint, nie pomijaj bramki i nie twórz sztucznego PASS.
6. Uruchom test diagnostyczny oraz testy bezpośrednie i zależne. Po zmianie fixture sprawdź, czy błędny wariant nadal zostaje wykryty.
7. Po dwóch identycznych niepowodzeniach bez nowych danych zmień hipotezę, reproduktor lub metodę diagnostyczną. Nie wykonuj kolejnego identycznego ponowienia.
8. Po poprawkach wykonaj wymagane regresje, review oraz pełny obowiązkowy zestaw CI na końcowym SHA.

## Ponawianie GitHub Actions

- Błąd przejściowy infrastruktury bez potrzeby zmiany kodu: po potwierdzeniu przyczyny można ponowić tylko nieudane joby lub właściwy job. Zachowaj run ID i attempt.
- Błąd kodu, testu, konfiguracji lub zależności: popraw przyczynę i uruchom kontrolę na nowym SHA. Nie czekaj na zielony wynik historycznego uruchomienia dla nieaktualnego commita.
- Skipped, cancelled, brak joba, NO-SOURCE, niepełna paginacja, brak wymaganych uprawnień i CI_PENDING nie są PASS. Zidentyfikuj brakującą kontrolę i uruchom właściwą walidację.
- SOURCE CI potwierdza dokładny checkout gałęzi. PR CI/integration sprawdza merge ref; wymagaj obu, gdy są określone w aktywnych bramkach. Nie usuwaj kontroli tylko dlatego, że są podobne.
- Nie twórz sztucznego commita wyłącznie dla run ID. Ogranicz powtórzenia, wykorzystuj poprawne wyniki i obserwuj współbieżne runy, ale nie kosztem wymaganej walidacji.

## Warunek zakończenia IMPLEMENT

Etap kończy się dopiero po wykonaniu uzgodnionego zakresu, testów i regresji, review, commita/pusha na właściwej gałęzi, sprawdzeniu pełnego końcowego SHA, potwierdzeniu wymaganych CI na tym SHA, aktualizacji checkpointu oraz raporcie. Nie rozpoczynaj kolejnego etapu implementacji przy nierozwiązanym FAIL albo brakującej wymaganej kontroli.

Jeśli realna blokada uniemożliwia kontrolę, zatrzymaj zależny etap i zapisz: konkretny job/status, dowód, przyczynę blokady i brakującą czynność. Raportuj `CI_PENDING` dla trwającego runu, `NOT_TESTED` dla niewykonanej weryfikacji, rzeczywistą trwałą blokadę opisz jako `CI_BLOCKED` z dowodami i brakującą czynnością. Nie oznaczaj `CODE_VERIFIED`.

Statusy APK/urządzenia są niezależne: `APK_VERIFIED` wymaga zbudowanego artefaktu i zweryfikowanego podpisu; `DEVICE_VERIFIED` wymaga rzeczywistego testu urządzenia.

## Rejestr błędów

`docs/ai/ERROR_LEARNING.md` i `docs/ai/ERROR_CASES.json` są jedynym kanonem pamięci błędów. Zachowuj odrębne incydenty o nowej wartości diagnostycznej, łącz duplikaty przez fingerprint i aktualizuj istniejący wniosek. Chwilowe ponowienie bez nowej informacji nie tworzy osobnej reguły. Nie kopiuj surowych logów, sekretów, tokenów, cookies ani danych prywatnych.

## Dobór testów według ryzyka

- JS/parser/WebExtension: `node --test app/src/test/js/*.test.cjs` i negatywne warianty protokołu.
- Kotlin/Room/UI: `./gradlew :app:testDebugUnitTest`, Robolectric, zmiany sygnatur/fixture; profile, prywatność, migracje, atomowość, rollback, restart.
- Lifecycle/Gecko/asynchronia: owner, cancel, Stop/Start, spóźniony callback, zmiana profilu, ponowna inicjalizacja.
- Orientacja/scroll/gesty: pion i poziom, widoczność/dotyk, zwijanie, powrót, puste dane; test struktury nie zastępuje testu zachowania.
- AndroidTest: `./gradlew :app:compileDebugAndroidTestKotlin` oznacza tylko kompilację, bez testu urządzenia.
- Lint: `./gradlew :app:lintDebug` i obowiązkowy `scripts/lint-regression.cjs` z zachowanym baseline.
W iteracji uruchamiaj trafione testy; przed zamknięciem wykonaj wszystkie obowiązkowe bramki na finalnym source SHA. Zobacz `docs/ai/QUALITY_GATES.md` i `docs/ai/ERROR_LEARNING.md`.
