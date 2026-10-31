# Zavro Clean — GV-G4A: preflight artefaktu, bez kompilacji (2026-10-31)

Baza: GV-G3C na commit `cc781928f69a347e5016bf0ba587233431cb9bbc`, CI 41249407385 COMPLETED / SUCCESS.

## Cel i rzeczywisty zakres

`G4A` jest **tylko przygotowaniem kontraktu wejściowego**. Dodano:
- `tools/gecko-fixture-g4a/BUILD_CONTRACT.json`: zamrożone, źródłowo zweryfikowane oczekiwania dotyczące *przyszłego* artefaktu i stan wszystkich zgód `NOT_GRANTED`.
- `check-build-contract.cjs`: odczyt istniejących deklaracji Gradle i podpisu, sprawdzenie pochodzenia G3C, wersji, ABI, pakietu i obecności skryptu walidującego APK. Wynik `PREPARED_NOT_AUTHORIZED` nie jest zgodą, nie uruchamia Gradle ani urządzenia.
- 7 regresji fail-closed oraz workflow wykonujący **wyłącznie** Node i ochronę historycznego audytu.

## Istotne odkrycie bezpieczeństwa

W `app/build.gradle`: `applicationId=com.prirai.android.nira`, debug suffix `.debug`, ABI `arm64-v8a`, bazowy `versionCode=195`, offset `1000`: wyliczony kod APK debug `1195`, `versionName=0.5.22-alpha100-clean`. Oczekiwany SHA-256 dotychczasowego certyfikatu: `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`. To **kontrakt deklaracji źródłowych**, a nie dowód z pliku APK lub telefonu.

Ten sam identyfikator `com.prirai.android.nira.debug` może nadpisać istniejącą instalację; nawet poprawnie podpisany APK nie jest bezpieczny do instalacji bez osobnej zgody i wiarygodnego backupu z próbą odtworzenia. Nie traktować odrębnego użytkownika Androida jako niezależnej instalacji innej wersji tego samego pakietu. Alternatywny identyfikator aplikacji wymaga osobnego, zatwierdzonego podetapu i testu poprawności integracji Gecko, nie wolno go podmieniać automatycznie.

## Co stanie się dopiero po wyraźnym `Kompiluj`

1. Sprawdzić aktualny SHA checkout, zgodność wersji i klucza; ponowić kontrakt preflight. Wybrać świadomie: ten sam pakiet jako aktualizacja *tylko* po backupie i osobnej zgodzie **na instalację**, albo osobny bezpieczny wariant/pakiet po dodatkowej analizie zgodności.
2. W osobnym build-only zadaniu wykonać pełne JS/JVM/AndroidTest compile/lint, zbudować wyłącznie arm64-v8a ze stabilnym podpisem ze skrytki CI, uruchomić `scripts/verify-update-apk.sh` na faktycznym APK, opublikować SHA-256 binarki i identyfikator artefaktu. Samo `Kompiluj` **nie** autoryzuje instalacji.
3. Nawet po buildzie odrębna decyzja: weryfikacja kopii, bezpieczna instalacja i syntetyczne HTTPS G2. Kolejność fizyczna: 043, 002, 030; 031 **ostatni**, tylko jednorazowy profil P-test i osobna zgoda na skasowanie.

## Wynik bez fałszywych deklaracji

Nie powstał APK, nie instalowano niczego, nie użyto ADB ani telefonu. Nie zmieniono `app/src/main`, `app/src/debug`, Gradle, podpisu, profili, sesji, danych, `main` i historycznych raportów.

Niezmienny audyt 19B CORE: **56 PASS, 1 FAIL, 2 BLOCKED, 1 NOT_TESTED**. ZC-AUD-002 BLOCKED/SUSPECTED, ZC-AUD-030 OPEN/SUSPECTED, ZC-AUD-031 PARTIAL, ZC-AUD-043 CONDITIONAL_GUARD/LIKELY. Wszystkie cztery fizyczne testy NOT_TESTED.

**STOP po zielonym CI na finalnym SHA G4A. Następny krok G4-build wymaga dosłownego polecenia `Kompiluj`.**
