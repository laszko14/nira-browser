# Zavro Clean — GV-G4B: signed ARM64 debug APK — kompilacja po zgodzie

Data zlecenia: 2026-10-31. Podstawa: etap G4A, commit `a48c68a0334dc0822d59cbe37f93d6da205b3f0b` i [zielone CI 41249712753](https://github.com/laszko14/nira-browser/actions/runs/41249712753).

## Wyraźnie zatwierdzony zakres

Użytkownik wydał polecenie **„Kompiluj”** po „Dalej”. Daje to zgodę na **wytworzenie artefaktu testowego APK w CI, bez instalacji**. Nie daje zgody na `adb install`, uruchomienie aplikacji na Nothing Phone (1), odinstalowanie czy skasowanie profili. Nie oznacza też potwierdzenia bezpieczeństwa aktualizacji istniejącego pakietu.

Nowy workflow `.github/workflows/zavro-audit60-gecko-build-g4b.yml` przeznaczony jest tylko dla osobnej gałęzi G4B i commitu ze znacznikiem `[authorized-g4b-build]`. Wymaga 60/60 zamrożonego audytu, testów G3C/G4A, pełnych WebExtension JS, Kotlin/Robolectric, kompilacji AndroidTest (bez uruchomienia), lint i dopiero na końcu assembleDebug. Żadnego testu fizycznego.

## Bramka rzeczywistego artefaktu

Jedynie klucz CI z istniejących sekretów repo może podpisać APK (brak automatycznego fallbacku do debug keystore). Istniejący `scripts/verify-update-apk.sh` ma sprawdzić **rzeczywisty binarny APK**: dokładnie jeden signer i certyfikat SHA-256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`; pakiet `com.prirai.android.nira.debug`, versionCode `1195`, versionName `0.5.22-alpha100-clean`, wyłącznie `arm64-v8a`. Gdy walidacja się nie powiedzie, nie publikować APK.

Po sukcesie GitHub Actions umieści APK, sumę SHA-256 i pochodzenie (SHA commitu, run id, wersje, certyfikat) w artefakcie `zavro-gecko-g4b-signed-arm64-not-installed`. Wynik weryfikacji **ustala się z logu tego run**, nigdy tylko z założonej konfiguracji.

## Ryzyko i dalszy warunek instalacji

Pakiet debug **ma ten sam applicationId co używana wersja Zavro Clean**. Zbudowanie zgodnego APK może umożliwić aktualizację istniejącej aplikacji, ale nie upoważnia do zastępowania jej danych. Wymaga to osobnej zgody, zweryfikowanej kopii zapasowej **wraz z możliwością przywrócenia**, sprawdzenia dotychczasowej wersji i izolacji prób. Inny pakiet/flavor — tylko po nowej analizie i akceptacji, ponieważ nie musi odzwierciedlać produkcyjnej integracji GeckoView.

ZC-AUD-031 pozostaje **ostatnim** testem fizycznym, wyłącznie na jednorazowym profilu i po osobnej zgodzie na usunięcie. Testy 043, 002 i 030 wcześniej, pojedynczo.

## Brak zmiany werdyktów audytu

ZC-AUD-002 BLOCKED/SUSPECTED, ZC-AUD-030 OPEN/SUSPECTED, ZC-AUD-031 PARTIAL, ZC-AUD-043 CONDITIONAL_GUARD/LIKELY. CORE 19B = **56 PASS, 1 FAIL, 2 BLOCKED, 1 NOT_TESTED**. Fizyczny test GeckoView **NOT_EXECUTED**.

Po sukcesie workflow — **STOP**, bez instalacji i bez merge do `main`.
