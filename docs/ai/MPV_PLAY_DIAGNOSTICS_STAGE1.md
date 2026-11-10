# ZAVRO CLEAN — diagnostyka MPV / Play, etap 1/5

## Cel i zakres
Kontekst: podgląd miniatur na niektórych witrynach pokazuje Play kojarzony ze starym wbudowanym MPV. Pochodzenie kontrolki pozostaje NIEUSTALONE bez pomiaru na witrynie/urządzeniu. Etap 1 tworzy bezpieczną, jawną sesję diagnostyczną, nie próbuje naprawiać rozpoznawania miniatur.

- MENU: MPV / Play • ROZPOCZNIJ TEST, OZNACZ PROBLEM, ZAKOŃCZ TEST, EKSPORTUJ ZIP, ANULUJ / WYCZYŚĆ RAPORT.
- Dane: sesja w RAM, maksymalnie 8 znaczników, najwyżej 12000 znaków z pojedynczej migawki natywnego app-decor. Sesja wygasa po 15 minutach. Brak odczytu DOM, JS, URL ze ścieżką, cookies, requestów i danych logowania.
- Plik: ZIP w prywatnym cache aplikacji, dostępny tylko przez existing FileProvider z tymczasowym URI nadanym przy wyraźnym eksporcie. W ZIP są summary.json, timeline.json, native-overlay.txt. Przy ręcznym ANULUJ/WYCZYŚĆ usuwany jest również cache eksportów tej funkcji.
- Bezpieczeństwo: wyłączenie w trybie prywatnym, izolacja po profilu i karcie, współdzielony DiagnosticSessionGate z TestLab; rejestrator nic nie robi przed START.
- Ograniczenia: OverlayWindowDiagnostics próbuje próbkę natywnych widoków w ustalonym punkcie. Nie potrafi wykryć przycisku wewnątrz Gecko/strony ani okien innych aplikacji. Nie twierdzi, że rozpoznało stare MPV.

## Testy
- Robolectric: limit znaczników, spójność ZIP i etykiet ograniczeń, odmowa private-mode, izolacja profilu/karty, konflikt TestLab, powtarzalne STOP oraz zakaz eksportu aktywnego raportu.
- JavaScript: strukturalny kontrakt menu, lokalnego eksportu, ograniczenia zbierania danych; dodatkowo pełne CI Kotlin/JavaScript/lint/AndroidTest.
- Sprzęt: NOT_TESTED do momentu testu rzeczywistej miniatury na Nothing Phone (1).

## Kolejny etap
Etap 2: instrumentacja DOM i HTML5 media w WebExtension, obserwacja kliknięć/iframe/Shadow DOM i mapa pochodzenia przycisku Play, z limitami danych i redakcją sekretów.
