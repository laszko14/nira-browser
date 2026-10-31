# Zavro Clean — GV-G4D: update preflight 1195 → 1196 (2026-11-01)

Baza: GV-G4C `717608d1af23638614499d25dae7682f16a7fd8a`. Actions run [41250929267](https://github.com/laszko14/nira-browser/actions/runs/41250929267): COMPLETED/SUCCESS; artefakt `zavro-gecko-g4c-v1196-update-not-installed`, id `13061815195`, nie wygasł w chwili G4D. SHA-256 certyfikatu APK 1196: `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`.

## Zakres jednego podetapu G4D

- Źródłowo oceniono backup: `AndroidManifest.xml` odwołuje się do `data_extraction_rules.xml`; obie ścieżki Android 12+ (cloud i device-transfer) zawierają tylko `scw_preferences.xml`. `backup_rules.xml` także nie dopuszcza pełnej kopii historii / danych silnika. To celowe ograniczenie prywatności, **nie** zabezpieczenie przed utratą danych przy aktualizacji.
- `ImportExportSettingsFragment.kt` zapewnia oddzielny eksport/import ustawień i zakładek; nie dowodzi kopii całej aplikacji.
- `ProfileExportManager.kt` eksportuje `manifest.json`, `profile.json`, `tabgroups.json`, `tabs.json` z metadanymi. Jego jawny kontrakt wyłącza sesje/cookies/historię/hasła GeckoView, a import grup jest no-op. ZIP profilu nie jest pełnym przywracalnym backupem.
- Dodano `tools/gecko-fixture-g4d/preflight-device.sh` i regresje z **fałszywym ADB**. Nie uruchomiono ADB na fizycznym Nothing Phone (1), nie pobrano starego APK z urządzenia. Skrypt może lokalnie, po świadomym uruchomieniu przez właściciela, sprawdzić właściwy pakiet/versionCode/versionName oraz certyfikat **rzeczywiście zainstalowanego** `base.apk`, używając tylko odczytu.
- Nie dotykano podpisu, Gradle, aplikacji, audytu, `main`, danych na telefonie, nie wywołano `assemble*`, `adb install`, `pm clear` ani żadnych fizycznych testów.

## Bramka do aktualizacji

| Kontrola | Stan po G4D |
| --- | --- |
| APK 1196 z tego commitu, package `com.prirai.android.nira.debug`, code 1196, podpis ze stałego certyfikatu, ARM64 | **PASS** w historycznym G4C CI (bez instalacji) |
| Zainstalowana aplikacja 1195 na ekranie telefonu | **OBSERWACJA UI**, a nie dowód package ID i certyfikatu |
| Zainstalowany package/version/cert z fizycznego telefonu | **NOT_VERIFIED**; gotowy wyłącznie skrypt odczytowy |
| Kopia obejmująca cały potrzebny stan aplikacji | **NOT_VERIFIED/INCOMPLETE BY DESIGN** dla dostępnych systemowych i ZIP eksportów |
| Próba odtworzenia bez ryzyka dla danych | **NOT_EXECUTED**; żadnego importu do obecnej instalacji |
| Zgoda na instalację | **NOT_GRANTED** |

**STOP — instalacja jest zablokowana.** Jeżeli podpis 1195 jest inny, nie odinstalowywać w celu „obejścia” i nie przenosić danych w ciemno. Jeżeli podpis jest taki sam, również nie instalować bez osobnej zgody i uzgodnionego zabezpieczenia danych. Gdy nie da się uzyskać pełnego backupu na nierootowanym urządzeniu, rozważyć w oddzielnym etapie izolowany wariant pakietu testowego z kontrolą równoważności integracji Gecko, zamiast ryzykować istniejącą instalację.

Kolejność ewentualnych późniejszych prób fizycznych: **043 → 002 → 030 → 031** (031 tylko jednorazowy profil i osobna zgoda na jego skasowanie). Brak dowodów do zmiany ZC-AUD-043 `CONDITIONAL_GUARD/LIKELY`, ZC-AUD-002 `BLOCKED/SUSPECTED`, ZC-AUD-030 `OPEN/SUSPECTED` i ZC-AUD-031 `PARTIAL`. Historyczna macierz CORE 19B: **56 PASS / 1 FAIL / 2 BLOCKED / 1 NOT_TESTED**.

**Następny krok:** operator może wykonać G4D preflight lokalnie na telefonie (wyłącznie odczyt) i przekazać zredagowane podsumowanie, ale zgoda na instalację musi być odrębna. Jeśli pełne odzyskiwanie danych nie jest możliwe, pierwszeństwo ma odizolowany wariant testowy po osobnej analizie i zgodzie. Bez automatycznego przejścia do G5.
