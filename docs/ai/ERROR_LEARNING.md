# Zavro Clean v2.0 — uczenie na błędach
To proces doskonalenia testów i instrukcji na podstawie dowodów, nie trenowanie modelu.
1. Dla ważnej regresji zanotuj objaw, SHA i kontrprzykład.
2. Ustal czy przyczyna leży w kodzie, teście, workflow, środowisku, wymaganiach czy pozostaje nieznana.
3. Sprawdź, czy jest to powracająca klasa błędu i czy wcześniejsze zabezpieczenia działały.
4. Dodaj test zachowania wykrywający błąd w granicach aktualnego zlecenia.
5. Napraw pierwotną przyczynę i zweryfikuj RED/GREEN, integrację i regresje.
6. Zachowaj dowody i uczciwe metryki w istniejących raportach / `docs/ai/PILOT_16.json`; nie wypełniaj braków zgadywaniem ani nie twórz konkurencyjnego rejestru.
7. Sprawdź, czy poprawka do AGENTS, testów albo bramki CI zapobiegłaby powtórzeniu problemu; zaproponuj najmniejszą niesprzeczną zasadę.
8. Stałe instrukcje i politykę CI zmieniaj tylko po osobnej zgodzie użytkownika; testy regresji w ramach zadania można dodawać automatycznie.
Uczenie nie kończy się po T01–T16 ani po obserwacji 14 dni. Bez commitów wyłącznie dla statystyk lub run ID po CI.
