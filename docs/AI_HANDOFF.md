# Alpha Browser — przekazanie Etapu 1

Data: 2026-10-22 (Europe/Warsaw).

## Potwierdzony wynik

Wykorzystano istniejący udany build, bez ponawiania go w celu uzyskania APK:
- Commit aplikacji: `1d004a0d7d1f4473e2becb252747159197157880`.
- GitHub Actions: https://github.com/laszko14/nira-browser/actions/runs/39686116222
- Artefakt `debug-apks`: https://github.com/laszko14/nira-browser/actions/runs/39686116222/artifacts/12386308193
- Artefakt nie był przeterminowany podczas weryfikacji. Pobranie może wymagać zalogowania do GitHub; artefakty Actions mają ograniczoną retencję.
- Plik dla Nothing Phone (1): `app-arm64-v8a-debug.apk` w archiwum artefaktu.
- SHA-256 APK: `8270de782aaa44fe8c51f9a2cee93ebec9586be828657d63b5f1414d1b4e51eb`.
- `aapt dump badging`: ABI `arm64-v8a`, applicationId `com.prirai.android.nira.debug`, versionName `0.5.15`, versionCode `1079`, minSdk `27`, targetSdk `37`.
- `apksigner verify --verbose --print-certs`: poprawny podpis APK v2, certyfikat Android Debug. SHA-256 certyfikatu: `6197bb0ab893b8f480e97209137589581b386592d3b33e78e9ac8ea032eff593`.
- Android 15 spełnia minSdk; działania na fizycznym urządzeniu NIE sprawdzono.

## Kontrole i zmiany

W istniejącym CI zadania `build` i `lint` zakończyły się sukcesem, `release` pominięto. `test` miał status success, ale `:app:testDebugUnitTest` zgłosił `NO-SOURCE`: faktycznych testów jednostkowych nie wykonano. Konfiguracja lint zawiera `abortOnError = false`; sukces zadania nie dowodzi braku usterek lint.

Commit `5b8c9c5` poprawia wyłącznie warunek istniejącego joba release: ręczny dispatch bez tagu na main pomija podpisane wydanie, jawny tag lub ref `refs/tags/v*` nadal dopuszcza release i wymaga sekretów podpisu. Bez zmian w kodzie aplikacji, wyglądzie, identyfikatorze, zależnościach i lockfile. Poprawiony warunek nie był jeszcze sprawdzony przez ręczny dispatch; nie uruchamiano podpisanego wydania.

Ostatni commit implementacyjny przed tym dokumentem: `5b8c9c5`; dokładny commit zawierający dokumentację ustal przez `git log -1` (dokument nie zapisuje własnego hasha).

## Środowisko chmurowe i ograniczenia

Przygotowano Gradle 9.7.1, JDK 21 JetBrains, SDK Android 37.1, Build Tools 37.0.0 i Platform Tools. Ścieżki: `GRADLE_USER_HOME=/workspace/.gradle`, `ANDROID_HOME=/workspace/android-sdk`, `ANDROID_USER_HOME=/workspace/.android`. Proxy Java oraz zaufanie TLS skonfigurowano poza repozytorium. Lokalny build nie został potwierdzony: pobieranie zależności Google Maven zwracało HTTP 502; zatrzymano go po znalezieniu gotowego APK w CI. Nie jest to dowód błędu kodu. Logi sesji: `/workspace/setup/build.log`, `/workspace/setup/build-configured.log`, `/workspace/setup/github-run.log`; nie są wersjonowane.

Instrukcje instalacji i uruchamiania zapisano w szkicu konfiguracji środowiska; zapis nie jest publikacją ani potwierdzeniem działania w nowym zadaniu.

## Dokładny następny krok

Etap 1: pobrać powyższy artefakt, rozpakować i zainstalować `app-arm64-v8a-debug.apk` na Nothing Phone (1), np. `adb install -r app-arm64-v8a-debug.apk`; sprawdzić uruchomienie i otwarcie strony. Klucz debug może zmieniać się między runnerami; konflikt podpisu wymaga świadomego rozwiązania, bez automatycznego usuwania danych aplikacji.

Etap 2 rozpocząć wyłącznie w osobnej sesji: ustalić faktyczny package name i Intent filter MPV Alpha w laszko14/REX-Player; najpierw otworzyć ręcznie podany publiczny link wideo z fallback chooser. Nie implementowano wykrywania wideo ani integracji MPV.

## Blokada na końcu sesji

Push commitów `5b8c9c5` i `4d5183d` nie powiódł się (proxy HTTP 403); odczyt API GitHub również zaczął zwracać Forbidden. Nie potwierdzono zmiany zdalnego main. W ustawieniach środowiska zapisano szkic obejmujący również `api.github.com`; wymaga przejrzenia, zapisania i publikacji przez użytkownika. Po przywróceniu dostępu najpierw opublikować lokalną dokumentację i poprawkę workflow (`git push origin HEAD:main`, bez force, po sprawdzeniu zdalnej historii). To jest pozostały krok Etapu 1 przed przejściem do Etapu 2. Bieżący końcowy commit ustal przez `git log -1`.
