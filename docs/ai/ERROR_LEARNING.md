# Zavro Clean V3.2 — pamięć błędów i zapobieganie regresjom

To trwały dziennik dowodów oraz napraw, nie trenowanie modelu. Jedynym rejestrem jest `docs/ai/ERROR_CASES.json`; `PILOT_16.json` służy wyłącznie pomiarom eksperymentu. Artefakty `v31-ci-errors-*` są krótkotrwałym dowodem pomocniczym i nie zapisują zmian w repozytorium.

## Triage i naprawa

Dla nieudanego testu lub istotnego zgłoszenia:

1. Odczytaj właściwy workflow, run/attempt, job, pierwszy nieudany krok i konkretny fragment logu przez bezpośredni konektor GitHub.
2. Rozdziel problem kodu, testu/fixture, workflow/konfiguracji, środowiska/infrastruktury, uprawnień lub zależności zewnętrznej.
3. Zapisz, co jest potwierdzone, a co tylko podejrzane. Nie zgaduj przyczyny ani nie kopiuj surowych logów.
4. Sprawdź istniejące wpisy po `task_id + fingerprint`. Powtórkę łącz z istniejącym wpisem; nowy wpis twórz tylko dla odrębnego incydentu, który wnosi nowy dowód, naprawę lub ryzyko regresji. Ponowienie tej samej awarii bez nowych informacji nie tworzy osobnej reguły.
5. Zapisz minimalną poprawkę, wynik testów, wymagane końcowe CI i test regresyjny. `FIXED` oznacza potwierdzoną naprawę, a nie samą zmianę kodu.
6. Dodaj lub wzmocnij regresję, jeśli to wykonalne i w zakresie zadania. Zachowaj otwarty status, gdy przyczyna albo weryfikacja pozostaje nierozstrzygnięta.

## Kanoniczny format

Każdy rekord zawiera identyfikator, powiązany etap, fingerprint, podsumowanie bez danych prywatnych, źródłową gałąź i SHA, identyfikator workflow/run, job i attempt (gdy dotyczy), przyczynę oraz poziom pewności, zastosowaną poprawkę i fix SHA, wynik testów, wynik końcowego CI, warunki ponownego wystąpienia, test regresyjny, dowody i status rozwiązania.

Nieznane dane oznaczaj jako nieznane / `null`; nie rekonstruuj brakujących historycznych informacji. Rejestr może zawierać powiązane wystąpienia z `duplicate_of`, ale wspólny wniosek, reguła zapobiegania i fingerprint mają pozostać pojedyncze. Aktualizuj rekord nadrzędny przy nowych dowodach. Nie utrwalaj sekretów, tokenów, danych uwierzytelniających, cookies, prywatnych URL z parametrami ani pełnych logów.

`coverage[].observed_count` jest liczbą udokumentowanych wystąpień w zadaniu i musi zgadzać się z rekordami. Nie deklaruj kompletności tylko dlatego, że walidator JSON przeszedł. Porównaj obserwacje w zadaniu z rekordami i artefaktami CI; brak dostępu lub rozbieżność oznacz `NOT_RECONCILED`. `COMPLETE` jest niedozwolone przy otwartym błędzie.

## CI i artefakty

SOURCE i PR CI mogą tworzyć oddzielne `v31-ci-errors-*` artefakty z metadanymi run/job/attempt/SHA i pierwszym nieudanym krokiem, bez raw logs. Po FAIL odczytaj artefakt i zweryfikuj go z live API. CI nie ma dostępu do treści rozmowy, nie pushuje i nie aktualizuje rejestru. Agent zapisuje istotny wynik w ramach tego samego zadania lub następnym właściwym commicie naprawczym.

Przejściowe problemy infrastruktury można ponowić selektywnie po potwierdzeniu przyczyny. Błąd kodu/testu wymaga poprawki i nowego SHA. Historyczne nieudane runy dla starych SHA nie blokują sukcesu końcowego; nie usuwa się ich z historii. Zakończenie wymaga wszystkich obowiązkowych kontroli na dokładnym końcowym SHA.

## Walidacja i raport

Uruchom `node scripts/error-learning.cjs validate` i `node --test scripts/error-learning.test.cjs`. Walidacja potwierdza format oraz wewnętrzną spójność, nie kompletność źródeł.

Raport bieżącego zadania oddziela `OBSERVED / RECORDED / OPEN`, podaje znane nieprzypisane artefakty i wynik końcowego CI. Używaj bezpośredniego konektora GitHub do odczytu logów; bez TinyFish.
