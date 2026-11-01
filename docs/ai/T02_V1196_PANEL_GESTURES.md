# T02/16 — gesty lewego i prawego panelu Zavro Clean v1196

**Stan:** ACTIVE; test na telefonie jeszcze **NOT_TESTED**.
**Zadanie:** T02-PANEL-GESTURES-V1196; zakres `UI_GESTURES`, złożoność `MEDIUM`; rozpoczęto (UTC) `2026-11-01T01:16:48.378Z`.
**Rzeczywiste źródło:** plan `docs/ai/GECKO_DEVICE_TEST_PLAN.md` („Panele i gesty”) oraz zgłoszone wymagania gestów 1DM. T01 (nawigacja, karty, tło) ma wynik użytkownika 3/3 i osobną 14-dniową obserwację — **nie powtarzać T01**.

## Zadanie dla użytkownika — tylko istniejąca aplikacja, bez danych prywatnych

Na Nothing Phone (1), Android 15, trzyprzyciskowa nawigacja, zainstalowany Zavro Clean v1196; karta zwykła, najlepiej publiczna strona `https://example.com`. Nie modyfikuj ustawień paneli ani profili.

1. **Lewy panel:** przeciągnij palcem od lewej krawędzi w prawo (gdy obsługa gestu jest włączona), potem zamknij panel ruchem przeciwnym. Zaobserwuj, czy panel otwiera się i domyka bez zacięcia. Jeśli gest jest wyłączony, zanotuj „gest niedostępny”, nie zmieniaj konfiguracji tylko na potrzeby testu.
2. **Prawy panel:** analogicznie przeciągnij od prawej krawędzi w lewo i zamknij. Sprawdź, czy nie otwiera się lewy panel i czy nie ma konfliktu z aktualną kartą.
3. **Szybki i przerwany ruch:** wykonaj jeden krótki szybki swipe i jeden częściowy swipe zakończony puszczeniem palca, osobno przy każdej krawędzi. Oczekiwane: panel kończy animację w stabilnym położeniu, bez zamrożenia, losowego kliknięcia lub przypadkowego zamknięcia karty. **Nie zakładaj konkretnej fizyki ani progu 50% bez pomiaru.**
4. **Klawisz systemowy Wstecz:** z otwartym panelem naciśnij systemowe Wstecz; sprawdź, czy aplikacja pozostaje responsywna i nie kończy się awaryjnie. Jeśli Back nawiguje inaczej niż oczekujesz, opisz efekt zamiast nazywać go automatycznie regresją.
5. **Konflikt ze stroną (opcjonalny):** podczas normalnego przewijania publicznej strony palcem od środka ekranu obserwuj, czy panel nie wysuwa się sam. Nie testuj na stronach z loginami, tokenami i poufnymi danymi.

**Odpowiedź:** `Lewy: OK / problem / gest wyłączony; Prawy: OK / problem / gest wyłączony; Szybki/częściowy: OK / problem; Wstecz: OK / problem; przewijanie: OK / pominięte`. W razie problemu krótki opis gestu i wyniku, bez prywatnego zrzutu ekranu.

## Bezpieczeństwo / ocena

- Bez nowych APK, ADB, force-stop, czyszczenia cache, danych, reinstalacji, usuwania profili, zmian konfiguracji, uprawnień i podpisu.
- Test jest obserwacją UI na urządzeniu, a nie pomiarem 120 Hz/jank, formalną zgodnością fizyki 1DM ani weryfikacją OriginAttributes GeckoView. Własne źródło G4C: `717608d1af23638614499d25dae7682f16a7fd8a` ([CI G4C](https://github.com/laszko14/nira-browser/actions/runs/41250929267)), ale nie sprawdzono hasha zainstalowanego APK.
- **Brak z góry ustalonego wyniku** — wszystkie liczniki w rejestrze T02 pozostają `null`. Po rzeczywistej odpowiedzi zapisz zawężony werdykt `USER_REPORTED` i rozpocznij 14-dniową obserwację dopiero po faktycznym zakończeniu zadania.
- ZC-AUD-002/030/031/043 pozostają nierozstrzygnięte i **nie są celem** T02. T01 pozostaje `FOLLOWUP_PENDING` do 2026-11-15; brak przenoszenia jego wyników do T02.
