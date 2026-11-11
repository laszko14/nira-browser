# Zavro Clean MPV/Play V2 — końcowy audyt źródła (etap 6/6)

**11.11.2026. Status przed końcowym CI: CI_PENDING.** To analiza repozytorium, nie pomiar na telefonie. Bazowy live SHA: `ad41702077ed7f155781c9ff66d443bf61c772ff`. Końcowe zatwierdzenie kodu wymaga pełnych SOURCE/PR CI na źródłowym SHA zawierającym ten raport. Nie wykonywano APK ani testów urządzenia.

## Przedmiot i metodologia

Odczytano aktywną linię i instrukcje z jednego SHA. Prześledzono przepływ: przycisk w `BrowserFragment.kt` → właściciel profilu/karty/EngineSession → kontroler `MpvPlayPageBridgeController.kt` → dodatki `BrowserApp.kt`/GeckoView → natywny Port → skrypt `zavro-preview-diagnostics/content.js` → `READY`/`START`/`START_ACK` → obserwacje `TOP`/iframe/open Shadow → `STOP`/`control_ack` → `MpvPlayDiagnosticLedger` → pliki ZIP.

Audyt obejmuje testy i kontrakty z etapów 1–5 oraz nowe regresje etapu 6. Sprawdzono granice z `preview-assist.js`, `nira-bg-play`, `nira-media-detector` i zewnętrznym MPV Alpha (przez `MpvPlaybackHeaders.kt`); **nie implementowano nowych zmian odtwarzania ani usuwania pozostałości po dawnym MPV** bez przyczyny potwierdzonej na urządzeniu.

## Wyniki analizy

| Obszar | Potwierdzona cecha kodu / testu | Ograniczenie dowodu |
|---|---|---|
| Wersja dodatku | Manifest, `content.js` i natywny `EXPECTED_SCRIPT_VERSION` są w testach wymagane jako zgodne (etap 6: `1.0.6`). | Zdalny CI nie dowodzi, że zaktualizowany skrypt działa na telefonie; wymaga instalacji APK i odczytu READY. |
| Transport | Pasywne READY, pojedyncza próba ponowienia i ograniczenie częstotliwości; rozliczanie portów, właściciela i ramek, kontrolowane ponowienia START. | Faktyczne GeckoSession i czas inicjalizacji są testowane w CI przez mocki, nie przez telefon. |
| Sesje | Oryginalny właściciel jest przypisany do portu; nieaktywna/prywatna karta nie powinna dostać danych starego portu. | Przełączenia w tle i odtworzenie aktywności Androida wymagają testu na urządzeniu. |
| STAGE6-01: stary START | Odkryto regresję: pojedynczy `stoppedId` zapamiętywał wyłącznie ostatnio zakończoną sesję. Po STOP A/START B/STOP B spóźniony START A mógł wznowić obserwację. | Naprawa: zbiór zakończonych nonce ograniczony do 128 na jeden dokument. Po przekroczeniu granicy archiwalny nonce może wypaść; bez gwarancji odrzucenia skrajnie starych pakietów. |
| Komendy UI | START_ACK nie jest sukcesem Play. MARK/PICK/STOP mają niezależny `control_ack`; STOP ma ograniczenie oczekiwania 600 ms. | ACK potwierdza obsługę w JS, nie zachowanie witryny ani wykrycie przycisku Play. |
| Obserwacja wideo | Typy `play`, `pause`, `playing`, `waiting`, `stalled`, `touchstart` i hover; wykrywanie otwartych ShadowRoot i ograniczonej liczby nowych elementów. | Zamknięte ShadowRoot, obsłużone odrzucenia `play()` i DRM nie są dostępne bez ingerencji; pełna przyczynowość pozostaje niepotwierdzona. |
| Frame fairness | Limit 160 zdarzeń, maksymalnie 96 łącznie SUBFRAME i 24 na jedno źródło; 64 rezerwowane dla TOP. | Nie wszystkie iframe są wstrzyknięte; zakres host permissions i `about:blank` może powodować niewidoczne ramki. |
| Prywatność | Protokół ma enumerowane kategorie i liczniki, bez surowego HTML, URL wideo, tekstu strony, cookies i poświadczeń; brak wywołań `play()`/`pause()` w samym dodatku diagnostycznym. | ZIP może zawierać nazwę hosta; użytkownik powinien sprawdzić raport przed udostępnieniem. |
| ZIP | Odrębne `transport.json`, `summary.json`, `timeline.json`, `page-events.json`, `page-coverage.json`, `analysis.json`, `analysis.txt`, `addon-inventory.json`, `native-overlay.txt`. | `diagnostics_complete` nie jest równoważne „Play działa”; bez realnego zdarzenia strony i TOP baseline analiza powinna pozostać niepełna. |

## Bramka regresji etapów 1–6

1. **Protokół/instalacja:** manifest i skrypt zgodne, starszy READY (brak wersji), błędna rejestracja handlera, opóźniona instalacja i ponowne uruchomienie.
2. **Połączenie:** bezterminowa dostępność ograniczonego retry, wielokrotne połączenia/rozłączenia, duplikaty portu, przekroczenie 24 portów, utrata postMessage i niedostępny właściciel.
3. **Transport/ZIP:** START, port READY, ACK, baseline, zdarzenia strony, STOP, błąd etapu transportu, limity ramki, spójne pliki eksportu i jawne brakujące dowody.
4. **UI:** wysłane ≠ potwierdzone, nonce po zmianie profilu, opóźniony MARK, potwierdzony/niepotwierdzony STOP, brak aktywności po anulowaniu.
5. **Strona:** syntetyczne HTML5, animowane miniatury, otwarte Shadow DOM i nowe elementy, iframe TOP/SUBFRAME, brak mutowania playback i nieprzeciekanie surowych danych.
6. **Nowe regresje etapu 6:** pełny syntetyczny przepływ TOP + SUBFRAME + Shadow + MARK/PICK + STOP + START; stary nonce A po STOP B, także po pasywnym reconnect; spójność dziewięciu plików ZIP i brak fałszywej identyfikacji właściciela Play.

Testy uruchamiane automatycznie: `node --test app/src/test/js/*.test.cjs`, komplet JVM/Robolectric, `AndroidTest compile`, lint i lint-regression, process-docs oraz error-learning. SOURCE i PR CI są osobnymi kontraktami; **wyniki przyszłego SHA nie są jeszcze potwierdzone w tym dokumencie**.

## Ryzyka pozostające po etapie 6

- **R1 (blokada DEVICE_VERIFIED):** brak rzeczywistego APK i powtórzenia usterki na Nothing Phone (1). Nie można przypisać historycznego `NO_ACK` ani ruchomych miniaturek do konkretnej pozostałości MPV bez materiału z urządzenia.
- **R2 (granice obserwacji):** nieudowodniona widoczność zamkniętych korzeni, części iframe/cross-origin, obsłużonych Promise rejection i zachowania stron z DRM.
- **R3 (limit pamięci):** pamięć 128 zakończonych nonce; bardzo stare opóźnione START po przekroczeniu tego limitu nadal mogą nie być rozpoznane. Przy realnym odtworzeniu wymaga to osobnego protokołu generacji/epoki po stronie natywnej i skryptu.
- **R4 (UX na telefonie):** orientacja, 60/120 Hz, przełączanie profili i faktyczne raportowanie ACK wymagają D01–D12, opisanych w `docs/ai/MPV_PLAY_V2_STAGE6_DEVICE_TEST.md`.
- **R5 (review):** krytyczny self-review i zdalne CI nie zastępują niezależnego recenzenta wysokiego ryzyka. Potwierdzenie ograniczone do wykonanych kontroli.

## Decyzja

Jeżeli finalne SOURCE i PR CI dla dokładnego końcowego SHA przejdą w całości: `CODE_VERIFIED` dla kodu diagnostycznego. Bez APK: `APK NOT_BUILT`, a `DEVICE_VERIFIED = NIE`. **Nie przypisywać statusu „naprawiono Play/minaturki”**, dopóki nie ma testów D01–D12 i udowodnionej poprawy zachowania witryny.
