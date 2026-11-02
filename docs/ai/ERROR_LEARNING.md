# Zavro Clean V3.1 — uczenie na błędach i pokrycie zgłoszeń
To proces doskonalenia testów i instrukcji na podstawie dowodów, nie trenowanie modelu.
1. Dla ważnej regresji zanotuj objaw, SHA i kontrprzykład.
2. Ustal czy przyczyna leży w kodzie, teście, workflow, środowisku, wymaganiach czy pozostaje nieznana.
3. Sprawdź, czy jest to powracająca klasa błędu i czy wcześniejsze zabezpieczenia działały.
4. Dodaj test zachowania wykrywający błąd w granicach aktualnego zlecenia.
5. Napraw pierwotną przyczynę i zweryfikuj RED/GREEN, integrację i regresje.
6. Zachowaj dowody i uczciwe metryki w istniejących raportach / `docs/ai/PILOT_16.json`; nie wypełniaj braków zgadywaniem ani nie twórz konkurencyjnego rejestru.
7. Sprawdź, czy poprawka do AGENTS, testów albo bramki CI zapobiegłaby powtórzeniu problemu; zaproponuj najmniejszą niesprzeczną zasadę.
8. Stałe instrukcje i politykę CI zmieniaj tylko po osobnej zgodzie użytkownika; testy regresji w ramach zadania można dodawać automatycznie.
## V3.1 — obowiązkowy zapis zdarzeń (jeden rejestr)
Kanon zdarzeń to `docs/ai/ERROR_CASES.json`. `PILOT_16.json` służy wyłącznie pomiarom eksperymentu i nie jest drugim rejestrem błędów. Dla każdego **odrębnego wystąpienia** (komunikat użytkownika, FAIL joba CI, regresja, review) dodaj wpis przed zamknięciem tego samego zadania. Wpis zawiera identyfikator, zadanie, fingerprint, opis bez danych prywatnych, źródłowy SHA, źródło i dowody, przyczynę (także `undetermined`), status, test regresji i SHA naprawy dopiero gdy rzeczywiście znane. Zgłoszenia, które nie mają jeszcze znanego SHA ani linku, są dozwolone jako USER_REPORT; nie wymyślaj danych.

Pole `coverage[].observed_count` musi odzwierciedlać **ręcznie policzone odrębne wystąpienia** w zadaniu, a liczba wpisów musi być identyczna. Każde ponowne wystąpienie tego samego fingerprintu dostaje osobny ID z `duplicate_of`, nie jest po cichu odrzucane. `COMPLETE` jest zabronione przy `OPEN`. Nie oznaczaj `FIXED` bez rzeczywistego `fix_sha` i testu. Niedostępne dawne wiadomości nie są rekonstruowane ani liczone jako 0. Nie kopiuj pełnych logów, tokenów, danych użytkownika, URL z parametrami, cookies ani sekretów do publicznego repo.

SOURCE CI i PR CI generują osobne `v31-ci-errors-*` artefakty z metadanymi run/job/attempt/SHA oraz nazwą pierwszego nieudanego kroku; **bez raw logs**. Odczytaj artefakt po FAIL, sprawdź live API i zmapuj każdy rekord CI do `ERROR_CASES.json` w następnym właściwym commicie naprawczym. CI nie pushuje, nie aktualizuje rejestru samodzielnie i nie ma dostępu do rozmowy: zgłoszenia czatowe zapisuje agent podczas realizacji zadania. `node scripts/error-learning.cjs validate` oraz `node --test scripts/error-learning.test.cjs` wymuszają spójność wpisów i deklarowanej liczby zdarzeń, **nie dowodzą**, że nic poza deklaracją nie zostało pominięte. Artefakt nie jest trwałym rejestrem i podlega retencji Actions.

Przy końcowym raporcie podaj `OBSERVED / RECORDED / OPEN` dla bieżącego zadania oraz znane nieprzypisane artefakty CI. `NOT_RECONCILED` lub niedostępny dowód jest blokadą kompletności uczenia, a nie PASS. Przy FAIL triage logi odczytuj wyłącznie przez bezpośredni GitHub connector; nie używaj TinyFish.

Uczenie nie kończy się po T01–T16 ani po obserwacji 14 dni. Bez commitów wyłącznie dla statystyk lub run ID po CI.
