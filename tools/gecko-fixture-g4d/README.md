# GV-G4D — bezinwazyjny preflight zainstalowanego Zavro

**To narzędzie niczego nie instaluje i nie uruchamia testów GeckoView.** Dopiero właściciel telefonu, po świadomej decyzji o lokalnym odczycie, może uruchomić je na swoim komputerze z Android SDK i ADB. ChatGPT/GitHub Actions nie ma dostępu do telefonu.

## Co sprawdza

- ręcznie wskazane urządzenie ADB: autoryzowane i Android 15 (API 35);
- obecność `com.prirai.android.nira.debug` i czytelność jego zainstalowanego `base.apk`;
- rzeczywisty `aapt`: kod `1195`, nazwa `0.5.22-alpha100-clean`;
- `apksigner verify --print-certs`: dokładnie jeden signer i SHA-256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`.

APK z telefonu jest tylko **odczytywane** za pomocą `adb pull` do tymczasowego katalogu lokalnego i usuwane z tego katalogu po kontroli. Nie eksportuje danych, cookies, kluczy ani sesji. Nie należy przekazywać prywatnych logów lub plików do repozytorium. Jeżeli Android odmówi odczytu APK, wynik jest BLOCKED, bez obejść przez root lub reset.

## Wywołanie dopiero przez użytkownika, na jego komputerze

1. Zainstalować oficjalne Android SDK build-tools (`apksigner`, `aapt`) i platform-tools (`adb`).
2. Połączyć swój telefon, świadomie włączyć i autoryzować debugowanie USB. Zidentyfikować urządzenie poleceniem `adb devices -l`.
3. Z checkoutu gałęzi G4D uruchomić:

```bash
bash tools/gecko-fixture-g4d/preflight-device.sh SERIAL_URZADZENIA /sciezka/do/Android/Sdk
```

Wynik `INSTALLED_PACKAGE_VERSION_SIGNER=MATCH` dotyczy tylko tych trzech właściwości **starej instalacji**. `RESTORABLE_FULL_BACKUP=NOT_VERIFIED` i `INSTALLATION=NOT_AUTHORIZED` pozostają zawsze bez względu na wynik. Nie wolno używać `adb install -r` ani jakiejkolwiek procedury usuwającej dane na podstawie samego raportu G4D.

## Dlaczego backup nadal blokuje aktualizację

Na Androidzie 15 aplikacja deklaruje `data_extraction_rules.xml` z backupem tylko `scw_preferences.xml` (także dla transferu urządzenie-urządzenie); nie jest to pełny zapis stanu przeglądarki. Funkcje eksportu ustawień i zakładek są oddzielne. `ProfileExportManager` **nie eksportuje** GeckoView sessions/cookies/history/passwords i nie odtwarza grup kart podczas importu. Takie pliki mogą służyć do częściowego odzyskania, ale nie dowodzą pełnej możliwości przywrócenia danych.

Warunek ewentualnej aktualizacji istniejącego pakietu: zgodność starego podpisu, rzeczywiście dostępny i sprawdzony mechanizm pełnego odtworzenia danych **lub odrębna decyzja o izolowanym wariancie testowym**, a następnie osobna zgoda na instalację. Bez spełnienia bramki nie instalować i nie kasować niczego. Test ZC-AUD-031 pozostaje ostatni i wymaga osobnej zgody na usunięcie jednorazowego profilu.

W G4D żaden z tych warunków zależnych od telefonu nie został jeszcze odhaczony.
