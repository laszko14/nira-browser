# Zavro Clean — etap 4/5: raport dowodów i bezpieczna korelacja Play

## Potwierdzone źródła na wejściu
Gałąź: `feat/zavro-clean-mpv-play-diagnostics-stage1-20261110`.
Baza: `d80ee2d7d9f438287c98041208e13b73980c8dab`.
SOURCE `42840717242` oraz PR `42840988723` zakończone **SUCCESS**
na bazowym SHA; potwierdzono przez bezpośrednie GitHub Actions.
Analiza z etapu 3: starego wbudowanego `libmpv` nie ma,
`ACTION_VIEW` do zewnętrznego MPV Alpha zachowujemy.
`preview-assist.js` i `nira-bg-play` mogą wpływać na UI miniatur,
ale nadal BRAK zrzutu diagnostycznego z konkretnej wadliwej strony.
Nie ma dowodu, że można bezpiecznie zmienić zachowanie odtwarzania.

## Realna usterka wykryta podczas przeglądu

1. Odbiornik wiadomości `MpvPlayPageBridgeController.onPortMessage`
   uznawał port za aktywny na podstawie **identycznej nazwy kanału
   i adresu nadawcy**, zamiast tożsamości obiektu `Port`.
   Dwa różne porty z tej samej ramki spełniają poprzednie warunki.
   Poprawka: przynależność do aktywnej listy wyłącznie po referencji,
   bez zaakceptowania zdarzenia ze starego/rozłączonego portu.
2. Walidator `MpvPlayDiagnosticLedger.appendPageEvent` przyjmował
   dowolny tekst z wielkich liter pasujący do wyrażenia regularnego
   jako `detail` lub metrykę. Przykładowe `PRIVATE_PASSWORD` w
   `metrics.frame` mogło trafić do eksportu, choć rozszerzenie
   produkcyjne tego nie wysyłało. Naprawa wymusza dozwolone nazwy
   zdarzeń, szczegóły oraz typy i enumeracje każdej metryki.
   Testy negatywne wykrywają dawny błąd — bez osłabienia ochrony.

## Wynik wdrożenia

- Jedna sesja `MPV / Play • ROZPOCZNIJ TEST` pozostaje lokalna,
  ograniczona czasowo, odseparowana od kart prywatnych/profili.
- Po STOP ZIP zawiera **`analysis.json`** (maszynowy, typowany
  raport) i **`analysis.txt`** (zrozumiałe podsumowanie po polsku),
  oprócz `summary.json`, `timeline.json`, `native-overlay.txt`,
  `page-events.json`, `page-coverage.json` i `addon-inventory.json`.
- `analysis.json` zawiera `diagnosis=UNDETERMINED`,
  `attribution_confidence=NOT_ESTABLISHED` i
  `play_owner=UNKNOWN` bez przypisywania winy na podstawie
  podobieństwa wyglądu kontrolki Play.
- Osobno są wskazówki: etykieta Play na DOM strony, kontrolki HTML5,
  znacznik `preview-assist`, możliwość aktywacji `nira-bg-play`,
  błędy JS, zdarzenie PLAY, obecność iframe i poster.
  **Wskazówki to nie dowody przyczynowe.** Nie włącza się
  niezweryfikowanego poziomu „pewności” i nie zapisuje się surowych
  nazw dodatków ani pełnych adresów URL.
- Raport pokazuje luki: brak zdarzeń, brak wskazania elementu,
  brak metadanych wideo, niedostępność listy dodatków, odrzucone
  pakiety i limit bufora. Niedostępnych ramek/closed Shadow DOM,
  obsłużonych wewnątrz strony odrzuceń `play()` ani rzeczywistego
  testu witryny nie uznaje się za sprawdzone.
- Zapis metryk wskazanego elementu jest skorelowany z jego
  syntetycznym identyfikatorem `node`, bez tekstu strony.
  `timeline.json` jest sortowane po czasie przyjęcia danych,
  aby spóźnione zdarzenie nie przestawiało osi czasu.
- Przeglądarkowe podglądy, `preview-assist.js`, `nira-bg-play`,
  nawigacja i zewnętrzny MPV Alpha **nie są zmieniane ani wyłączane**.

## Użycie po późniejszym zbudowaniu APK

1. Otwórz problematyczną stronę w zwykłej karcie i w razie potrzeby
   odśwież, by skrypt dodatku został dołączony do dokumentu.
2. W menu wybierz `MPV / Play • ROZPOCZNIJ TEST` i odtwórz błąd.
3. Wybierz `WSKAŻ ELEMENT` i dotknij problematycznego Play;
   osobno `OZNACZ PROBLEM`, aby zarejestrować stan natywny.
4. Wybierz `ZAKOŃCZ TEST`, `EKSPORTUJ ZIP`; prześlij ZIP
   do analizy. Eksport jest jedynie lokalny i wymaga użytkownika.
5. Dopiero mając takie dane, można potwierdzić źródło i wybrać
   bezpieczną poprawkę strony, helpera albo background play.

## Testy i zakres dowodów

Robolectric: pusta sesja i brak pozornej atrybucji, wiele hipotez,
braki/limity, fałszywe ciągi danych, izolacja tożsamości portów,
sortowanie timeline i kompletność ZIP. JavaScript: realny skrypt
obserwacji, korelacja wybranego elementu, nieinwazyjne zbieranie.
Wymagane SOURCE i PR CI na końcowym SHA: JS, JVM/Robolectric,
kompilacja AndroidTest, lint/baseline, dokumentacja/evidence.
**Nie wykonano** testu na telefonie ani rzeczywistej witrynie.
**APK nie budowano.** Zmian playback nie dokonywano bez podstaw.

## Następny etap 5/5 — oddzielne DALEJ

Pełne regresje wszystkich istniejących systemów (w tym hover,
touch, preview, site navigation, iframe, HTML5, HLS/MSE,
background play, media sessions, external MPV Alpha), kontrola
rozmiarów/bezpieczeństwa, pełne SOURCE + PR CI, checkpoint,
a dopiero potem pytanie użytkownika o prywatny podpisany APK.
Brak testu urządzenia i pochodzenia Play nadal należy wykazać.
