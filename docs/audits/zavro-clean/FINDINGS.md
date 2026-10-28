# Zavro Clean — rejestr usterek audytu

Odniesienia do kodu dotyczą bazy `b7d62f79f285ac81c29a79b326529d42e9b9fde4`. Bez wdrażania napraw. Obserwacje wymagające późniejszej analizy są w raporcie etapu; sama obecność starej nazwy lub niepołączonej gałęzi nie stanowi usterki.

| ID | Etap | Priorytet | Pewność | Problem |
| --- | --- | --- | --- | --- |
| ZC-AUD-001 | 1 | P2 MEDIUM | CONFIRMED | Job release oczekuje historycznych metadanych v1182 zamiast bieżących v1190 |

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

Stan po etapie 1: potwierdzone=1; LIKELY/SUSPECTED=0. Pozostałe moduły oczekują na właściwe etapy audytu.
