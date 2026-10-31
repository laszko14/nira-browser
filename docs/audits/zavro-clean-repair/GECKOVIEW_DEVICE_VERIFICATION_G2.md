# Zavro Clean — GV-G2: izolowane strony i testy bez APK (2026-10-31)

**Baza:** GV-G1 (commit 7238b70ee2ec868a3585e151a3caddc192a25c58; CI 41246314984 = completed/success). G2 = jeden podetap, bez dotykania aplikacji.

## Co przygotowano

1. Statyczne strony pod tym samym HTTPS origin do izolacji dwóch testowych profili P/Q: cookies, localStorage, IndexedDB, popup/window.open, target=_blank i noopener.
2. Dwa niezależne, prawidłowe MP4 wytwarzane przez ffmpeg (A: 2 s, 160×90, niebieski; B: 5 s, 320×180, czerwony), a błędne B reprezentowane przez 404.
3. Przejście A→B, błąd B, wiele source, blob B i dziennik src/currentSrc/readyState/loadedmetadata/resize/durationchange/MutationObserver bez surowych URL.
4. Narzędzie HTTPS tylko localhost domyślnie, bez logowania URL i bez połączeń z aplikacją. Test jednorazowego certyfikatu wyłącznie na desktop/CI, nie na telefonie.
5. Testy statyczne i rzeczywistych plików multimedialnych ffprobe, kontrola endpointów HTTPS i bezpieczeństwa allowlist w GitHub CI. Nie ma testów GeckoView/Android/ADB.

Dane i certyfikaty testowe nie są zapisane w repozytorium. Brak weryfikacji natywnego OriginAttributes, trwałego erasure, restartu background i rzeczywistej kolejności zdarzeń Gecko. Dalsze instrumentowanie zaplanowane w G3.

## Ograniczenia statusów

- ZC-AUD-002: BLOCKED / SUSPECTED.
- ZC-AUD-030: OPEN / SUSPECTED; w 19B BLOCKED.
- ZC-AUD-031: PARTIAL; w 19B FAIL kryterium zamknięcia.
- ZC-AUD-043: CONDITIONAL_GUARD / LIKELY; w 19B NOT_TESTED.

Macierz 19B pozostaje 56 PASS, 1 FAIL, 2 BLOCKED, 1 NOT_TESTED. Na Nothing Phone (1) nie wykonano jeszcze żadnej z tych prób. Sukces CI G2 dowodzi wyłącznie działania narzędzi testowych.

## Bramka STOP

Zmiany tylko tools/gecko-fixture-g2/, ten dokument i workflow G2. Bez APK, zmian aplikacji, podpisu, utraty danych, merge i aktualizacji statusów. Wymagane CI completed/success na końcowym SHA.

**Następne osobne Dalej: GV-G3 — minimalne debug-only hooki obserwacyjne i regresje, bez APK.** Kompiluj jest osobnym poleceniem; nawet po nim instalacja wymaga oddzielnej zgody i zabezpieczenia danych.
