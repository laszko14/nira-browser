# Zavro Clean — MPV/Play — etap 2/5: obserwacja strony

## Zakres
Etap 2 rozszerza rejestrator etapu 1. Osobna wbudowana WebExtension
zavro-preview-diagnostics jest rejestrowana przy uruchomieniu przeglądarki,
ale nie obserwuje strony do momentu świadomego ROZPOCZNIJ TEST.

- START: losowy identyfikator sesji pochodzi tylko z kodu natywnego i trafia do
  właściwej EngineSession.
- Migawka: ograniczony zestaw elementów video, source, button, link i iframe,
  metadane HTML5, style widoczności i położenie.
- Zdarzenia: kategorie pointerover, pointerdown, click, play, pause, error,
  zmian atrybutów i liczby dodawanych węzłów; JS error i Promise rejection bez stack.
- OZNACZ PROBLEM: natywny znacznik etapu 1 oraz nowa migawka strony.
- WSKAŻ ELEMENT: przechwytuje tylko jeden świadomie wskazany dotyk, rozbraja
  pozostałe ramki po potwierdzonym wyborze.
- STOP/ANULUJ: odłączenie obserwatorów; ZIP dostępny dopiero po STOP.
- ZIP: summary.json, timeline.json, native-overlay.txt, page-events.json,
  page-coverage.json.

## Limity i prywatność
15 minut, 8 znaczników natywnych, 160 zdarzeń strony, 2300 znaków w pakiecie,
40 elementów i 6 korzeni DOM na migawkę, 24 porty na EngineSession, 512 KiB ZIP.
Tylko RAM i lokalny eksport FileProvider, bez automatycznego uploadu.
Natywny odbiorca odrzuca obcy nonce, błędne sekwencje, nieznane pola, wolny
tekst i payloady niezgodne z typowanym schematem. Brak treści formularzy,
cookies, tokenów, surowego HTML, wartości atrybutów, nagłówków i adresów
źródeł wideo. Bez wywołań play() lub pause(). Karty prywatne wyłączone.

## Ograniczenia
Dostęp do iframe jest ograniczony uprawnieniami rozszerzenia i HTTP(S).
Dostęp do Shadow DOM wyłącznie w otwartych korzeniach, w limitowanym skanie.
JS: klasa zdarzenia, bez kodu i stack trace. Złapane wewnętrznie odrzucenia
video.play() mogą pozostać niewidoczne, ponieważ nie podmieniamy play().
Brak metadanych sieciowych i zrzutu ekranu w etapie 2.
Jeśli nowy dodatek zainstalował się już po załadowaniu dokumentu,
przed START może być wymagane odświeżenie strony; brak portu oznacza
NO_PAGE_EVENTS, nie pozytywny wynik diagnostyki.
Etap nie ustala jeszcze właściciela kontrolki Play.

## Akceptacja
JS: realny content.js w kontrolowanej atrapie DOM, brak zbierania przed START,
nonce, oznaczenie elementu, STOP, ramka iframe, redakcja sekretów.
Kotlin: ograniczenie bufora i archiwum, odrzucenie duplikatów, obcego nonce
i niedozwolonych danych. SOURCE i PR CI osobno (JS, Kotlin/Robolectric,
AndroidTest compile, lint regression i bramki procesowe).
Android/Gecko na urządzeniu: NOT_TESTED. APK: NOT_BUILT.

## Następny etap
3/5 — identyfikacja źródła Play oraz pozostałości MPV na podstawie dowodów,
w tym instrumentacja preview-assist.js, nira-bg-play i aktywnych rozszerzeń.
