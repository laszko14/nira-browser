# Alpha Browser — przekazanie Etapu 1

Data: 2026-10-22 (Europe/Warsaw).

## Wynik

- APK arm64-v8a zbudowany przez istniejący CI; identyfikator pakietu `com.prirai.android.nira.debug`, wersja `0.5.15` / code `1079`, minSdk `27`, targetSdk `37`.
- Run dla poprawionego workflow: https://github.com/laszko14/nira-browser/actions/runs/39688896991 — build, lint i test job zakończone success; release skipped. Test jednostkowy to `NO-SOURCE`, testów faktycznie nie wykonano.
- Artefakt `debug-apks`: https://github.com/laszko14/nira-browser/actions/runs/39688896991/artifacts/12388435254 (id `12388435254`, nieprzeterminowany przy sprawdzeniu). Zawiera `app-arm64-v8a-debug.apk` dla Nothing Phone (1).
- Poprzednio pobrany APK arm64 przeszedł `aapt dump badging` i `apksigner verify` (podpis debug v2). SHA-256: `8270de782aaa44fe8c51f9a2cee93ebec9586be828657d63b5f1414d1b4e51eb`. APK nie był testowany na fizycznym telefonie.

## Zmiana CI i publikacja

Commit `5b8c9c5` zmienia warunek joba release: ręczny dispatch na main bez jawnego tagu pomija podpisane wydanie. Push do main potwierdził, że release jest skipped. Ręcznego dispatchu bez tagu osobno nie wykonywano.

Dokumentacja i poprawka workflow są wypchnięte na `main`. Ostatni potwierdzony zdalny HEAD przed korektą tego pliku: `c2790f99bed0968171c278410439c1f38910341a`; po kolejnej korekcie sprawdź `git log -1` i `git ls-remote origin HEAD`. Czysty stan roboczy należy potwierdzić przed kontynuacją.

## Środowisko

Przygotowano Gradle 9.7.1, JDK 21, Android SDK 37.1, Build Tools 37.0.0 i Platform Tools w ścieżkach `/workspace/.gradle` i `/workspace/android-sdk`. Lokalna kompilacja nie została potwierdzona z powodu HTTP 502 Google Maven; CI zbudowało APK poprawnie. Nie zmieniano kodu aplikacji ani zależności. Konfiguracja onboarding została zapisana; rzeczywisty build potwierdza run powyżej.

## Następny krok

Etap 1 jest zakończony po potwierdzeniu ostatniego commitu tej dokumentacji na GitHubie. Użytkownik może pobrać artefakt i zainstalować APK na Nothing Phone (1), np. `adb install -r app-arm64-v8a-debug.apk`; sprawdzić uruchomienie oraz stronę WWW. Aplikacja nie była testowana na urządzeniu.

Etap 2 rozpocząć w osobnej sesji: ustalić rzeczywisty package name i Intent filter MPV Alpha w `laszko14/REX-Player`, a następnie obsłużyć ręcznie podany publiczny link i fallback chooser. Nie implementowano wykrywania wideo ani integracji z MPV.
