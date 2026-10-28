# Zavro Clean — rejestr usterek audytu

Odniesienia do kodu dotyczą bazy `b7d62f79f285ac81c29a79b326529d42e9b9fde4`. Bez wdrażania napraw. Obserwacje wymagające późniejszej analizy są w raporcie etapu; sama obecność starej nazwy lub niepołączonej gałęzi nie stanowi usterki.

| ID | Etap | Priorytet | Pewność | Problem |
| --- | --- | --- | --- | --- |
| ZC-AUD-001 | 1 | P2 MEDIUM | CONFIRMED | Job release oczekuje historycznych metadanych v1182 zamiast bieżących v1190 |
| ZC-AUD-002 | 2A | P2 MEDIUM (potencjalny) | SUSPECTED | Restart background rozszerzenia może rozjechać generację kluczy dokumentów z zachowanym repozytorium native |

## ZC-AUD-001 — niespójny kontrakt wersji joba release

- **Etap:** 1 — konfiguracja aktualnej bazy i kompletność integracji wydania.
- **Miejsce:** [`.github/workflows/apk-release.yml:159`](https://github.com/laszko14/nira-browser/blob/b7d62f79f285ac81c29a79b326529d42e9b9fde4/.github/workflows/apk-release.yml#L159), job `release`, pola `EXPECTED_VERSION_CODE` (161) i `EXPECTED_VERSION_NAME` (162); wywołanie weryfikatora: linia 230. Gradle: [`app/build.gradle:29`](https://github.com/laszko14/nira-browser/blob/b7d62f79f285ac81c29a79b326529d42e9b9fde4/app/build.gradle#L29), `defaultConfig`; mapa ABI: linia 119, zastosowanie mnożnika: linia 129.
- **Problem:** Gradle deklaruje bazowy kod 190 i nazwę `0.5.22-alpha95-clean`; ARM64 otrzymuje kod 1190. Job `build` oczekuje właściwych danych. Job `release` oczekuje nadal 1182 i `0.5.22-alpha87-clean`.
- **Mechanizm:** tag `v*.*.*` wskazujący bieżący kod aktywuje `release` (warunek w linii 166). Checkout wskazuje kod taga (178–182). Jeśli kompilacja i podpis zakończą się powodzeniem, APK będzie miał dane v1190. Następnie `scripts/verify-update-apk.sh` dostanie oczekiwania v1182 i zakończy się błędem na porównaniu metadanych (linie 60–69). Publikacja z kolejnych kroków nie nastąpi. Kontrola podpisu działa prawidłowo; problemem są nieaktualne oczekiwania wywołującego.
- **Dowód:** wykonane porównanie statyczne rzeczywistych pól Gradle/workflow: `build` PASS `(1190, alpha95-clean)`, `release` FAIL `(1182, alpha87-clean)`. Weryfikator wymaga zgodności obu pól. Istniejące CI dla bazy pomijało `release`, więc jego SUCCESS nie sprawdza tej ścieżki.
- **Pewność:** CONFIRMED — deterministyczna niezgodność konfiguracji i ścieżka błędu; nie uruchamiano wydania ani budowania APK. Nie jest to obserwacja awarii na telefonie.
- **Ważność:** P2 MEDIUM — blokada alternatywnej ścieżki wydania. Nie blokuje aktualnego debug APK aktualizacyjnego z joba `build`; nie oznacza uszkodzenia danych użytkownika.
- **Wpływ:** proces wydawania, walidacja wersji, wiarygodność zbiorczego statusu CI. Release ma własny pakiet `com.prirai.android.nira`; nie wolno traktować go jako aktualizacji Clean z pakietem `.debug`.
- **Kierunek naprawy:** w osobnym zadaniu ustalić przeznaczenie ścieżki release oraz synchronizować oczekiwania z kodem faktycznie budowanego taga. Zachować kontrolę certyfikatu i ABI oraz pakiet zainstalowanego Clean. Nie obniżać wersji aplikacji i nie osłabiać weryfikatora.
- **Regresja do przyszłego wdrożenia:** test statyczny osobno dla jobów `build` i `release`, uwzględniający checkout taga; wykrywanie rozjazdu kodu/nazwy oraz odrębności pakietów. Obecny helper `app/src/test/js/helpers/update-apk-contract.cjs` celowo ogranicza się do joba `build` (linie 23–24). Późniejsza weryfikacja prawdziwego APK wyłącznie w osobno zleconym wydaniu.

## ZC-AUD-002 — możliwy brak uzgodnienia nowej generacji rozszerzenia

- **Etap:** 2A — granica cyklu życia Gecko/WebExtension/native.
- **Miejsce:** [`background.js:107`](https://github.com/laszko14/nira-browser/blob/b7d62f79f285ac81c29a79b326529d42e9b9fde4/app/src/main/assets/extensions/nira-media-detector/background.js#L107), licznik `nextDocumentKey`, oraz `startDocument` (249, inkrementacja 264); [`MediaRepository.kt:344`](https://github.com/laszko14/nira-browser/blob/b7d62f79f285ac81c29a79b326529d42e9b9fde4/app/src/main/java/com/prirai/android/nira/media/MediaRepository.kt#L344), `registerDocument`, szczególnie 350–353. Przekazanie dokumentu: `content.js:96`; schemat `media_document`: `MediaMessageProtocol.kt:138`.
- **Problem:** licznik background jest stanem pamięci jego środowiska JS; repozytorium native żyje w procesie aplikacji i zachowuje maksymalny klucz dla EngineSession. Protokół v1 nie przekazuje generacji instalacji/restartu rozszerzenia.
- **Warunek i mechanizm:** **jeśli** środowisko background zostanie uruchomione ponownie, a aplikacja i ta sama EngineSession zachowają repozytorium z kluczem np. 20, nowy background zacznie od 1. `registerDocument` odrzuci niższy klucz jako stary. Content nie przejdzie do `document_ready` po odpowiedzi innej niż `true`. Zwykłe czyszczenie podczas nawigacji zachowuje poprzedni maksymalny klucz (`MediaRepository.kt:315–321`), zatem samo odświeżenie nie gwarantuje uzgodnienia. Nowy klucz większy od starego albo nowa sesja mogą zakończyć tę sytuację.
- **Dowód i ograniczenie:** powyższe reguły producenta i konsumenta odczytano bezpośrednio z kodu, brak pola generacji potwierdza zamknięta lista pól `media_document`. **Nie sprawdzono**, czy GeckoView 156 w realnym scenariuszu awarii/reinstancjacji rozszerzenia zachowuje tę samą EngineSession i natywne scope. Nie wykonano restartu procesu rozszerzeń. To nie jest potwierdzony błąd działania.
- **Pewność:** SUSPECTED. Testy zwykłej nawigacji i starych kluczy PASS nie odtwarzają niezależnego restartu producenta identyfikatorów.
- **Ważność:** potencjalny P2 MEDIUM — brak nowych wyników detektora w zachowanych sesjach, jeśli warunek zostanie potwierdzony.
- **Wpływ:** FAB/lista filmów, metadane i diagnostyka odkrywania; nie wykazano utraty profili ani sesji przeglądarki i nie wykazano wpływu na odtwarzanie strony.
- **Kierunek ewentualnej naprawy:** dopiero po potwierdzeniu warunku zaprojektować zaufany handshake nowej generacji po stronie native, zachowując odrzucanie opóźnionych komunikatów starej generacji. Nie usuwać ochrony przed starymi kluczami i nie resetować repozytorium na podstawie dowolnej wiadomości strony.
- **Wymagane testy:** na GeckoView 156 zachować kartę po wielu rejestracjach, odtworzyć niezależny restart background, sprawdzić tożsamość EngineSession i odpowiedź rejestracji. Osobno przypadek restartu całej aplikacji/nowej sesji jako kontrola negatywna; następnie integracyjny test handshake i odrzucania wiadomości poprzedniej generacji. Specyfikacja do późniejszego wdrożenia; nie dodano testów do aplikacji.

Stan po 2A: potwierdzone=1 (ZC-AUD-001); LIKELY=0; SUSPECTED=1 (ZC-AUD-002). Etap 2 pozostaje PARTIAL. Nie doliczać potencjalnego P2 do liczby potwierdzonych błędów.
