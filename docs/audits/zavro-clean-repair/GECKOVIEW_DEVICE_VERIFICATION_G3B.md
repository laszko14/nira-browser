# Zavro Clean — GV-G3B: native evidence probes, test-only (2026-10-31)

Baza: GV-G3 commit 8515ab00bf7b95cd0c62b28ab466d545c080436c; CI 41247300321 COMPLETED / SUCCESS. Jeden podetap G3B.

## Co dodano

- GeckoG3NativeFacts w debug source: bezpieczne MATCH/MISMATCH/NOT_OBSERVED dla Store.contextId vs rzeczywiste GeckoSession.settings.contextId, flagi private oraz syntetycznych metadanych A/B. Ustawienia Gecko nie są dowodem na wewnętrzne OriginAttributes.
- GeckoG3NativeFactsTest w JVM: kontrola default, nazwanych kontekstów, privacy, niezgodności i brakujących metadanych.
- GeckoG3NativeEvidenceTest w androidTest: bez zmiany kodu produkcyjnego. Test wymaga jawnego gv_g3_opt_in=synthetic-only oraz gv_g3_origin=trusted HTTPS origin. Odczytuje WYŁĄCZNIE już otwarte strony G2 na tym origin, ich rzeczywiste GeckoEngineSession.geckoSession.settings, prywatność, syntetyczne media A/B przez MediaDetectorController.uiRepository oraz opcjonalną flagę tombstone wskazanego jednorazowego UUID. Nigdy nie loguje URL, contextId, UUID, cookie ani innych danych profilu; nie wykonuje nawigacji ani kasowania. Nie jest uruchamiany w CI, tylko kompilowany.
- Dokumentacyjna bramka CI, regresje JVM, kompilacja AndroidTest, WebExtension JS i lint bez APK.

## Nierozstrzygnięte, zgodnie z planem

- ZC-AUD-002 BLOCKED: potwierdzenie fizycznego restartu WebExtension background z zachowaną EngineSession, epoka i accepted/rejected native media_document nadal NOT_OBSERVED.
- ZC-AUD-030 OPEN: ustawienia sesji Gecko można teraz porównać z BrowserStore, ale prawdziwe OriginAttributes i izolacja cookies/storage popup P/Q pozostają NOT_OBSERVED.
- ZC-AUD-031 PARTIAL: odczyt istniejącej tombstone to tylko kontrola dziennika; zakończenie clearDataForSessionContext i brak starych danych po cold restart nadal NOT_OBSERVED. Nie wolno usuwać profili w G3B.
- ZC-AUD-043 CONDITIONAL_GUARD: porównanie wpisów A/B z rzeczywistym native repozytorium jest możliwe, ale kolejność eventów w Gecko i runtime source switch NIE była obserwowana.

Audyt CORE 19B = 56 PASS, 1 FAIL, 2 BLOCKED, 1 NOT_TESTED. Wszystkie fizyczne próby na Nothing Phone (1) nadal NOT_TESTED.

## Bramka STOP

Wyłącznie app/src/debug, app/src/test, app/src/androidTest, dokumentacja i workflow. Brak zmian app/src/main, Gradle, wersji, podpisu, danych, main. Bez APK przed Kompiluj, bez instalacji oraz resetów. G3B kończy się po SUCCESS CI na finalnym SHA. Następne osobne Dalej = G3C, przygotowanie kontrolowanej metody dowodu dla nadal nieobserwowanych zdarzeń, nadal bez APK. Nie deklarować czterech ustaleń jako naprawionych.
