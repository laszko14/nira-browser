# Zavro x MPV v1133 — poprawki AUTO Play i czarnego ekranu

## Zgłoszenie z testu telefonu
- v1132: naciśnięcie Play / fullscreen pozostawia Gecko; mimo AUTO MPV nie uruchamia się.
- Ręczny wybór z panelu wykrytych strumieni otwiera MPV, ale kończy odtwarzanie czarnym ekranem i komunikatem „Koniec lub błąd filmu”.
- Sam zrzut nie określa, czy źródłem błędu jest HTTP 401/403, wymagane cookies, wygasły URL, niedostępny format, czy moment uruchomienia surface. Nie oznaczamy przyczyny sieciowej jako pewnej bez logu.

## Zmiany
- Osobna gałąź od zatwierdzonej kompilacji v1132, pakiet `com.prirai.android.nira.mpv`, `versionCode=133` → ARM64 1133; `0.5.22-alpha39-mpv7`, bez merge.
- Automatyczna propozycja filmu po zaufanym pointer/key gesture powiązanym w tej samej ramce z natywnym zdarzeniem `play`/`playing`/`loadedmetadata`/`durationchange`. Nie wymagamy `isTrusted` samego zdarzenia medialnego, bo bywa emitowane asynchronicznie. Okno korelacji gestu do 10 s.
- Wsparcie dla odtwarzaczy z przyciskiem Play obok `video` (zamiast wyłącznie wewnątrz elementu). Jeden aktywny nie-wyciszony film w ramce, minimalny czas trwania 180 s (lub aktywny HLS), opt-in AUTO, potwierdzone pause ACK. DRM, blob/MSE, WebRTC, krótkie filmy i podejrzane reklamy — Gecko fallback.
- Przeniesiono pierwsze `playFile` do `surface.post` po zainstalowaniu natywnego SurfaceView w hierarchii Android UI. Redukuje ryzyko startu odtwarzania przed przygotowaniem widoku (hipoteza do sprawdzenia na urządzeniu).
- Rozróżnienie błędu przed `FILE_LOADED` od normalnego `END_FILE`. Dla AUTO natychmiastowy rollback do strony przy błędzie oraz timeout po 20 s. Ręczny tryb podaje komunikat z możliwymi przyczynami; nie twierdzi bez dowodów, że winny jest serwer.
- Znacznik `autoHandoff` istnieje tylko w RAM w bilecie odtwarzania. Signed URLs i cookies nie są utrwalane, nie przesyłamy ich przez Intent.
- Testy nowych zdarzeń i fallback, aktualizacja numeru wersji w testach historycznych.

## Niezmienione ograniczenia
- Nie wszystkie odtwarzacze stron są HTMLVideoElement z bezpośrednim HTTP URL (często MSE blob). Nie przejmujemy DRM ani WebRTC.
- Strumienie wymagające konkretnych sesyjnych cookies albo odświeżania podpisanych adresów mogą nadal nie otwierać się w libmpv. Cel: bezpieczna przekazywana autoryzacja tylko dla właściwego profilu, bez mieszania kart/prywatności; na razie fallback do Gecko.
- Testu fizycznego nie zastępuje zielone CI. Sprawdzić porty Gecko we wszystkich iframe, ręczny MP4/HLS, dźwięk, rotację, PiP i 30 cykli otwórz/zamknij.
- Pełny panel Lua 30+8, Audio Premium, VAD/RNNoise/DeepFilterNet, audyt GPL/FFmpeg pozostają do zrobienia.

## Testowanie
`node --test app/src/test/js/*.test.cjs`, `./gradlew testDebugUnitTest`, `./gradlew lintDebug` oraz podpisany ARM64 build w GitHub Actions.
