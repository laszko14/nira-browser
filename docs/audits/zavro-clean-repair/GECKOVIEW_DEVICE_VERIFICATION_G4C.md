# Zavro Clean — GV-G4C: signed update APK 1196 (2026-11-01)

## Potwierdzony stan urządzenia i autoryzacja

Użytkownik pokazał ekran **O aplikacji** z telefonu Nothing Phone (1): `0.5.22-alpha100-clean (1195)`, GeckoView `156.0.1-20260921121718` i Mozilla Android Components `156.0.1`. Sam zrzut ekranu **nie ujawnia rzeczywistej nazwy packageId ani odcisku podpisu zainstalowanej aplikacji**, dlatego zgodności tych dwóch cech na urządzeniu nie wolno uznać za potwierdzoną.

Użytkownik poprosił: **„skompiluj apk do aktualizacji”**. To autoryzuje wytworzenie i publikację artefaktu, nie instalację. W G4B zbudowano apk 1195, czyli z takim samym versionCode jak pokazana instalacja; ten etap przygotowuje nowy testowy APK `1196`.

## Jedyna zmiana aplikacji

Bazowy commit G4B `709f80e8d0ef24be5f14352e0fd1f99ddc301aa7` ([CI #41249830850](https://github.com/laszko14/nira-browser/actions/runs/41249830850) SUCCESS). W `app/build.gradle` tylko:
- bazowy `versionCode = 195` → `196`; podział ABI dodaje 1000, więc `arm64-v8a` otrzyma **1196** (o 1 wyższy od zainstalowanego 1195);
- `versionName = "0.5.22-alpha100-clean"` → `"0.5.22-alpha101-clean"`.

Żadnych zmian `app/src/main`, `app/src/debug`, pakietu, GeckoView, logiki produkcyjnej, rejestru audytu, `main`, certyfikatu, konfiguracji signing ani danych użytkownika. **Podniesienie numeru nie oznacza naprawy błędów GeckoView.**

## Warunki CI

`zavro-audit60-gecko-update-g4c.yml` działa na nowej gałęzi z dokładną zgodą `[authorized-g4c-build]` w komunikacie commitu. Odmawia wykonania zmian innych niż ta wersja, workflow i dokument. Porównuje całą zawartość Gradle z G4B po wyłącznie dwóch dozwolonych podstawieniach, kontroluje pakiet, kod 1196 i integralność historycznej formatki G4A. Testuje rejestr 60/60, G3C, pełne JavaScript/Kotlin/Robolectric, kompilację AndroidTest, lint. Buduje i publikuje APK tylko po PASS wszystkich bramek.

Sekrety CI zapewniają niezmienny klucz, a `scripts/verify-update-apk.sh` weryfikuje na **rzeczywistym APK**: `com.prirai.android.nira.debug`, versionCode `1196`, versionName `0.5.22-alpha101-clean`, `arm64-v8a`, certyfikat SHA-256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00` i dokładnie jednego signera. Publikacja obejmie sumę SHA-256 APK oraz `G4C_PROVENANCE.txt`.

## Zgoda na instalację: NIE

Nie wykonywać `adb install`, `pm clear`, odinstalowania ani testów fizycznych bez osobnej zgody i weryfikacji podpisu **już zainstalowanego** pakietu. Nawet przy zgodnym numerze wersji APK o tym samym packageId może zastąpić dotychczasową instalację; sprawdzić backup z możliwością odtworzenia, packageId i podpis przed aktualizacją. Utrata danych lub konflikt podpisu = **STOP**, bez odinstalowania.

Nierozstrzygnięte: ZC-AUD-002 BLOCKED/SUSPECTED, 030 OPEN/SUSPECTED, 031 PARTIAL, 043 CONDITIONAL_GUARD/LIKELY. CORE: 56 PASS / 1 FAIL / 2 BLOCKED / 1 NOT_TESTED. Fizyka GeckoView NOT_TESTED. ZC-AUD-031 testować jako ostatni, tylko na jednorazowym profilu.

**STOP po zielonym CI i zweryfikowanym artefakcie.**

## CI correction — build gate preserved

Pierwszy CI dla wersji 1196 zatrzymał się na 11 z 398 testów JS, gdyż dotychczasowy standardowy `.github/workflows/apk-release.yml` nadal wymagał debug `1195`, a cztery asercje 19A zawierały historyczne stałe `1195`. Wszystkie pozostałe testy JS przeszły. Zgodnie z istniejącym kontraktem aktualizacji zaktualizowano wyłącznie trzy pola **debug build** w istniejącym workflow (kod 1196, nazwa alpha101, nazwa artefaktu v1196) oraz oczekiwania wersji w `app/src/test/js/release-contract-stage19a.test.cjs`. Semantyka testów negatywnych i reguły release/tag pozostają nienaruszone.

G4C CI porównuje te pliki bajt-po-bajcie z poprawnie określonymi zmianami względem G4B; nie pomija regresji, a weryfikacja certyfikatu, ABI, pakietu i rzeczywistego APK pozostaje obowiązkowa. Nie buduje się artefaktu po niezaliczonych testach. Po naprawie CI musi uzyskać COMPLETED/SUCCESS na finalnym commit.
