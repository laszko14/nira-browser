# Zavro Clean — etap 1/19 — rejestr napraw audytu 18/18

**Data:** 2026-10-30. **Stan:** PLAN / NOT_RECHECKED — nie ma jeszcze napraw usterek ZC-AUD na tej gałęzi. Ten dokument jest checkpointem wdrożeniowym, a nie dowodem ich usunięcia.

## Faktyczna baza i zasada ochrony nowszych wdrożeń

- Repozytorium: `laszko14/nira-browser`; gałąź robocza: `plan/zavro-clean-audit60-stage1-20261030`.
- Bazowy commit roboczy: `8739cf4f1fa3a21925cbbdc3fff4fe8c10db37f5` z `audit/zavro-clean-p1-p16-stage1-20261030`. Rodzic `c31a36ca5789585f32c55a2f3297427194fe5054` — aktualny stan rozwoju v1195 z Picker V2, poprawkami P1–P16 i późniejszymi usprawnieniami Wyszukiwarki filmów. Dodatkowy commit 8739… zawiera wyłącznie testy i dokumentację ponownego audytu P1–P16; oba zestawy prac muszą pozostać.
- Bazowy commit audytu v1190: `b7d62f79f285ac81c29a79b326529d42e9b9fde4`; audyt jest osobną gałęzią `audit/zavro-clean-20261028` @ `2b091209b0720d7c24074f8de8d4b69bf420b215`. Porównanie wskazuje 208 dodatkowych commitów na v1195 względem v1190, a potem jeden commit testów P1–P16.
- Prawidłowa aplikacja: `com.prirai.android.nira.debug`, arm64-v8a, rzeczywisty versionCode arm64=1195 (Gradle base 195), `0.5.22-alpha100-clean`. Stały certyfikat aktualizacji SHA-256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`. Bez merge do main, resetu danych, zmiany podpisu i budowania APK przed wyraźnym `Kompiluj`.
- Historyczne CI: [v1195 signed APK, SUCCESS na c31a36c](https://github.com/laszko14/nira-browser/actions/runs/41055239605) i [P1–P16 tests only, SUCCESS na 8739cf4](https://github.com/laszko14/nira-browser/actions/runs/41111844287). To **nie** są testy przyszłych poprawek ani potwierdzenie działania 120 Hz/Gecko na telefonie.
- Źródła prawdy: [FINAL_REPORT.md](https://github.com/laszko14/nira-browser/blob/audit/zavro-clean-20261028/docs/audits/zavro-clean/FINAL_REPORT.md), [FINDINGS.md](https://github.com/laszko14/nira-browser/blob/audit/zavro-clean-20261028/docs/audits/zavro-clean/FINDINGS.md), [STAGE_17.md](https://github.com/laszko14/nira-browser/blob/audit/zavro-clean-20261028/docs/audits/zavro-clean/STAGE_17.md), [STAGE_01–18](https://github.com/laszko14/nira-browser/blob/audit/zavro-clean-20261028/docs/audits/zavro-clean) oraz `docs/VIDEO_SEARCH_P1_P16_REAUDIT_STAGE1.md` z wybranej bazy.
- Przed każdą zmianą kodu: przeczytać oryginalny dowód ZC-AUD, znaleźć aktualny symbol i zweryfikować na **nowym SHA**, czy problem nadal istnieje. Jeśli już naprawiony w v1191–v1195, odnotować `ALREADY_RESOLVED` z dowodem zamiast ponownie go implementować. Nie przenosić plików v1190 na aktualną gałąź ani nie nadpisywać Picker V2.

## Plan 19 etapów i zależności

Poniżej jest bezpieczne przypisanie **każdego ID dokładnie jeden raz** do etapu formalnej decyzji. Zależności pochodzą z pakietów A–M raportu Codex; wczesne sprawdzenie 030 w etapie 8 nie oznacza podwójnej naprawy. Etapy 2–19 można podzielić na uzasadnione podetapy, z checkpointem i zielonym wymaganym CI przed STOP.

### Etap 1/19 — Weryfikacja bazy, rejestr 60 ustaleń i bramki bezpieczeństwa

**ID:** wszystkie 60 (rejestr, bez napraw).

Dokumentacja, powiązanie starych dowodów z nowym kodem, brak modyfikacji aplikacji.

### Etap 2/19 — P1: callback zakładek po destroy

**ID:** ZC-AUD-011.

Odtworzenie po zniszczeniu panelu; odpięcie callbacku; brak crash i regresji zakładek.

### Etap 3/19 — P1: bezpieczny round-trip importu preferencji

**ID:** ZC-AUD-022.

Zachowanie typów, walidacja i odwracalny import bez resetu preferencji.

### Etap 4/19 — P1: bariera zapisu sesji przy zamykaniu

**ID:** ZC-AUD-026.

Zapis zakończony przed końcem procesu, fault-injection i restore; nie czyścić danych.

### Etap 5/19 — P1: atomowy import zakładek

**ID:** ZC-AUD-056.

Walidacja przed podmianą, rollback/awaria pliku, zachowanie dotychczasowego indeksu.

### Etap 6/19 — Prywatność diagnostyki i favicon

**ID:** ZC-AUD-053, ZC-AUD-054.

Sanityzacja OAuth/logów; prywatny cache na RAM, cleanup, późne callbacki; normalny cache bez regresji.

### Etap 7/19 — TestLab i instrumentacja

**ID:** ZC-AUD-052, ZC-AUD-060.

Okno PREPARING, aktualne urządzeniowe FULL_AUDIT 120 s; prawidłowe odróżnienie janku okna od FPS Gecko.

### Etap 8/19 — Rejestr profili, wybór aktywnego i privacy UX

**ID:** ZC-AUD-003, ZC-AUD-023, ZC-AUD-024, ZC-AUD-025, ZC-AUD-059.

Serializacja mutacji profili 059 przed 003/023/024; polityka sugestii 025 wyłącznie po weryfikacji; wczesny preflight 030 bez zmiany zachowania.

### Etap 9/19 — Start/restore i intenty PWA

**ID:** ZC-AUD-027, ZC-AUD-058.

Zależność po 026; nowy intent wygrywa z przywróconą kartą, brak duplikatu na pustym starcie.

### Etap 10/19 — PWA, opener, migracja kontekstów i erasure

**ID:** ZC-AUD-028, ZC-AUD-029, ZC-AUD-030, ZC-AUD-031, ZC-AUD-050.

Konteksty i opener 030 po testach izolacji; mapowanie przed migracją 028 i kasowaniem 031; nowy engine bez starego PLAYING.

### Etap 11/19 — Kolejność kart, Undo, recovery i koszt projekcji

**ID:** ZC-AUD-004, ZC-AUD-005, ZC-AUD-009, ZC-AUD-013.

Po 026/027; bez utraty slotów podczas restore; trwałe grupy, Undo i liniowy koszt skalowania.

### Etap 12/19 — Panele i własność gestów palca

**ID:** ZC-AUD-007, ZC-AUD-008, ZC-AUD-010, ZC-AUD-017, ZC-AUD-019.

RTL, dwa palce, CANCEL, przełączenie flagi gestów, DnD/autoscroll; 010/019 najpierw potwierdzić na Compose.

### Etap 13/19 — Toolbar i koordynacja gestów oraz IME

**ID:** ZC-AUD-016, ZC-AUD-018, ZC-AUD-048, ZC-AUD-049.

Wspólny właściciel animacji i hitbox; poprawna klawiatura i znaki flingu; 016 najpierw potwierdzić.

### Etap 14/19 — Chrome, ustawienia interfejsu, fullscreen i viewport

**ID:** ZC-AUD-006, ZC-AUD-012, ZC-AUD-014, ZC-AUD-015, ZC-AUD-020, ZC-AUD-021, ZC-AUD-047.

Po 12–13; prawidłowa orientacja, Anuluj bez zapisu, przyciski, IME i przeplot fullscreen.

### Etap 15/19 — Generacje dokumentu, skanowanie i SPA

**ID:** ZC-AUD-002, ZC-AUD-032, ZC-AUD-033, ZC-AUD-034, ZC-AUD-035, ZC-AUD-051.

BFCache, ShadowRoot, source, retry 200→403→200, hash router; 002 najpierw zweryfikować w Gecko.

### Etap 16/19 — Tożsamość grup i wybór reprezentanta mediów

**ID:** ZC-AUD-037, ZC-AUD-038, ZC-AUD-039.

Po 15; odporność na kolejność DOM/network, stabilne ID, poprawny poster i odtwarzalny wariant.

### Etap 17/19 — DASH/HLS — parser i pochodzenie metadanych

**ID:** ZC-AUD-036, ZC-AUD-040, ZC-AUD-041, ZC-AUD-042, ZC-AUD-043, ZC-AUD-044.

Odrębne regresje XML/DRM, ułamkowe FPS, bitrate, HLS, suma Period; 043 wymaga realnego Gecko.

### Etap 18/19 — Preview oraz pobieranie i integralność resume

**ID:** ZC-AUD-045, ZC-AUD-046, ZC-AUD-055, ZC-AUD-057.

Klik/mute/playsinline, cancellation i probe generations; A→B tego samego rozmiaru nie może sklejać bajtów.

### Etap 19/19 — Kontrakt aktualizacji i pełna regresja końcowa

**ID:** ZC-AUD-001.

Spójność build/release/versionCode/certyfikatu bez APK do polecenia Kompiluj; pełna macierz 60 z dokumentacją PASS/FAIL/BLOCKED.

## Rejestr 60 problemów — stan na początku napraw

**CONFIRMED: 53** (P1 4, P2 47, P3 2); **LIKELY: 4** (P2); **SUSPECTED: 3** (P1 1, P2 2). Siedem niepotwierdzonych ID to **002, 010, 016, 019, 025, 030, 043**. Potencjalny priorytet 030 nie jest piątym potwierdzonym P1.

| ID | Treść ustalenia Codex | Pewność | Priorytet | Etap | Stan na v1195 |
| --- | --- | --- | --- | --- | --- |
| ZC-AUD-001 | niespójny kontrakt wersji joba release | CONFIRMED | P2 | 19/19 | IMPLEMENTED — stage 6, CI_PENDING |
| ZC-AUD-002 | możliwy brak uzgodnienia nowej generacji rozszerzenia | SUSPECTED | P2 | 15/19 | VERIFY_FIRST |
| ZC-AUD-003 | starsze zadanie zmienia politykę aktywnego profilu | CONFIRMED | P2 | 8/19 | IMPLEMENTED — stage 6, CI_PENDING |
| ZC-AUD-004 | brak końcowego sprzątania slotów zamkniętych kart | CONFIRMED | P2 | 11/19 | DO_RECHECK |
| ZC-AUD-005 | scope komunikatu Cofnij przeżywa swojego właściciela | CONFIRMED | P2 | 11/19 | DO_RECHECK |
| ZC-AUD-006 | pozostawione przełączniki nie sterują aktualnym interfejsem | CONFIRMED | P2 | 14/19 | DO_RECHECK |
| ZC-AUD-007 | uchwyt resize po niewłaściwej stronie w RTL | CONFIRMED | P2 | 12/19 | DO_RECHECK |
| ZC-AUD-008 | resize nie zachowuje tożsamości palca | CONFIRMED | P2 | 12/19 | DO_RECHECK |
| ZC-AUD-009 | nieatomowa walidacja tworzenia grupy z Bez grupy | CONFIRMED | P2 | 11/19 | DO_RECHECK |
| ZC-AUD-010 | hover pozostaje przy poprzednim celu podczas autoscroll | LIKELY | P2 | 12/19 | VERIFY_FIRST |
| ZC-AUD-011 | dialog zachowuje callback usuniętego panelu zakładek | CONFIRMED | P1 | 2/19 | IMPLEMENTED — stage 2, verify CI |
| ZC-AUD-012 | dwa kryteria sortowania działają jak nazwa | CONFIRMED | P2 | 14/19 | DO_RECHECK |
| ZC-AUD-013 | kwadratowy koszt projekcji kolejności pasków kart | CONFIRMED | P2 | 11/19 | DO_RECHECK |
| ZC-AUD-014 | dialog kolejności nie rozróżnia upuszczenia przed i za wierszem | CONFIRMED | P2 | 14/19 | DO_RECHECK |
| ZC-AUD-015 | konfiguracja przycisków zależy od zmiany szerokości zamiast orientacji | CONFIRMED | P2 | 14/19 | DO_RECHECK |
| ZC-AUD-016 | nieidempotentna obsługa klawiatury przy dismiss | LIKELY | P2 | 13/19 | VERIFY_FIRST |
| ZC-AUD-017 | flaga gestów nie zatrzymuje już przejętego streamu | CONFIRMED | P2 | 12/19 | DO_RECHECK |
| ZC-AUD-018 | starsza animacja toolbaru przeżywa rozpoczęcie nowego gestu | CONFIRMED | P2 | 13/19 | DO_RECHECK |
| ZC-AUD-019 | raw swipe karty nie respektuje własności gestu Compose | LIKELY | P2 | 12/19 | VERIFY_FIRST |
| ZC-AUD-020 | obsługa IME usuwa rezerwację miejsca na dolny pasek | CONFIRMED | P2 | 14/19 | DO_RECHECK |
| ZC-AUD-021 | Anuluj nie cofa wyboru wyszukiwarki | CONFIRMED | P2 | 14/19 | DO_RECHECK |
| ZC-AUD-022 | import własnego eksportu zmienia typy preferencji | CONFIRMED | P1 | 3/19 | IMPLEMENTED — stage 3, CI_PENDING |
| ZC-AUD-023 | wybór canonical default nie uzgadnia aktywnego profilu | CONFIRMED | P2 | 8/19 | DO_RECHECK |
| ZC-AUD-024 | menu NewTab ma różną semantykę prywatności zależnie od profilu | CONFIRMED | P2 | 8/19 | DO_RECHECK |
| ZC-AUD-025 | zakres sugestii kart nie jest ograniczony do bieżącego profilu | SUSPECTED | P2 | 8/19 | VERIFY_FIRST |
| ZC-AUD-026 | killProcess wyprzedza trwały zapis sesji | CONFIRMED | P1 | 4/19 | IMPLEMENTED — stage 4, CI_PENDING |
| ZC-AUD-027 | pusty stan startowy jest traktowany jak zamknięcie wszystkich kart | CONFIRMED | P2 | 9/19 | DO_RECHECK |
| ZC-AUD-028 | default PWA nie używa canonical kontekstu default | CONFIRMED | P2 | 10/19 | DO_RECHECK |
| ZC-AUD-029 | ContextMenuFeature PWA jest podłączony do obcej sesji | CONFIRMED | P2 | 10/19 | DO_RECHECK |
| ZC-AUD-030 | opener ma rozbieżne konteksty karty i przygotowanego engine | SUSPECTED | P1 (potencjalny) | 10/19 | VERIFY_FIRST |
| ZC-AUD-031 | usunięcie profilu pozostawia osierocone karty | CONFIRMED | P2 | 10/19 | DO_RECHECK |
| ZC-AUD-032 | konfiguracje script nie wracają po wznowieniu tego samego DOM | CONFIRMED | P2 | 15/19 | DO_RECHECK |
| ZC-AUD-033 | childList z source nie skanuje istniejącego video | CONFIRMED | P2 | 15/19 | DO_RECHECK |
| ZC-AUD-034 | brak wyzwalacza dla późnego open Shadow DOM | CONFIRMED | P2 | 15/19 | DO_RECHECK |
| ZC-AUD-035 | deduplikacja pomija przywrócenie dostępu | CONFIRMED | P2 | 15/19 | DO_RECHECK |
| ZC-AUD-036 | ochrona DASH rozpoznawana z surowego XML | CONFIRMED | P2 | 17/19 | DO_RECHECK |
| ZC-AUD-037 | grupowanie wariantów zależy od kolejności DOM i sieci | CONFIRMED | P2 | 16/19 | DO_RECHECK |
| ZC-AUD-038 | wybór reprezentanta pomija metadane i dostępne alternatywy | CONFIRMED | P2 | 16/19 | DO_RECHECK |
| ZC-AUD-039 | niestabilna tożsamość konfiguracji odczytywanej z atrybutu | CONFIRMED | P2 | 16/19 | DO_RECHECK |
| ZC-AUD-040 | odrzucane typowe ułamkowe FPS DASH | CONFIRMED | P2 | 17/19 | DO_RECHECK |
| ZC-AUD-041 | pochodzenie czasu HLS zmienia się na HTML5 | CONFIRMED | P3 | 17/19 | DO_RECHECK |
| ZC-AUD-042 | stary szacunek rozmiaru przeżywa utratę average bitrate | CONFIRMED | P2 | 17/19 | DO_RECHECK |
| ZC-AUD-043 | możliwe przeniesienie parametrów starego źródła na nowe src | LIKELY | P2 | 17/19 | VERIFY_FIRST |
| ZC-AUD-044 | czas DASH sumuje osobno zaokrąglone okresy | CONFIRMED | P3 | 17/19 | DO_RECHECK |
| ZC-AUD-045 | zwykłe video zostaje uznane za podgląd i wyciszone | CONFIRMED | P2 | 18/19 | DO_RECHECK |
| ZC-AUD-046 | odtwarzanie błędnego podglądu blokuje każdy kolejny tap | CONFIRMED | P2 | 18/19 | DO_RECHECK |
| ZC-AUD-047 | spóźnione przywrócenie toolbaru koliduje z kolejnym fullscreen | CONFIRMED | P2 | 14/19 | DO_RECHECK |
| ZC-AUD-048 | prostokąt toolbaru pod panelem nadal wygrywa gest | CONFIRMED | P2 | 13/19 | DO_RECHECK |
| ZC-AUD-049 | różny znak flingu w toolbarze WWW i Home | CONFIRMED | P2 | 13/19 | DO_RECHECK |
| ZC-AUD-050 | migracja profilu przenosi stan odtwarzania starego silnika | CONFIRMED | P2 | 10/19 | DO_RECHECK |
| ZC-AUD-051 | zmiana filmu w trasie hash zachowuje stare media | CONFIRMED | P2 | 15/19 | DO_RECHECK |
| ZC-AUD-052 | brak rejestracji okna filmów otwartego w PREPARING | CONFIRMED | P2 | 7/19 | IMPLEMENTED — stage 7, CI_PENDING |
| ZC-AUD-053 | parametry uwierzytelniania w diagnostyce Sync | CONFIRMED | P2 | 6/19 | DO_RECHECK |
| ZC-AUD-054 | trwały cache favicon z prywatnej karty | CONFIRMED | P2 | 6/19 | DO_RECHECK |
| ZC-AUD-055 | probe pobierania nie respektuje anulowania | CONFIRMED | P2 | 18/19 | DO_RECHECK |
| ZC-AUD-056 | import nadpisuje zakładki przed walidacją | CONFIRMED | P1 | 5/19 | IMPLEMENTED — stage 5, CI_PENDING |
| ZC-AUD-057 | resume nie wiąże części z wersją zasobu | CONFIRMED | P2 | 18/19 | DO_RECHECK |
| ZC-AUD-058 | nowy intent PWA ginie przed utworzeniem fragmentu | CONFIRMED | P2 | 9/19 | DO_RECHECK |
| ZC-AUD-059 | nieatomowe mutacje listy profili | CONFIRMED | P2 | 8/19 | DO_RECHECK |
| ZC-AUD-060 | nieaktualny scenariusz TestLabDeviceTest | CONFIRMED | P2 | 7/19 | IMPLEMENTED — stage 7, CI_PENDING |

## Kryteria przejścia i sposób oznaczania stanów

1. **DO_RECHECK** = potwierdzone na starym v1190, ale jeszcze niesprawdzone na v1195; **VERIFY_FIRST** = hipoteza ze starego audytu. Nie używać etykiety FIXED przed reprodukcją/porównaniem aktualnego stanu, testem regresyjnym i dowodem.
2. Po weryfikacji ID przyjmuje `STILL_PRESENT`, `ALREADY_RESOLVED` (z konkretnym commitem/testem), `NOT_REPRODUCED` (z zakresem próby), `FIXED` (commit + test + CI) lub `BLOCKED` (z powodem). Brak dowodu oznacza OPEN, nie PASS. Każdy z 60 wierszy musi zachować osobną historię i wymagane regresje z FINDINGS.
3. **Najpierw A (2–5)** dla P1, **B (6)** ochrona danych prywatnych, **C (7)** poprawność telemetrii przed wnioskami o 120 Hz. W D: 059 przed innymi mutacjami profili; 026 przed 027; konteksty/opener przed migracją 028 i cleanup 031. W E: nie kasować starych slotów 004 podczas niezakończonego restore. W F przed G kontrakt gestu i anulowania; H przed I zasady generacji dokumentów; J integrować z H/I, L niezależnie od playera.
4. Szczególne hipotezy: 030 sprawdzić wstępnie przy profilach przed modyfikacją kontekstów, 025 wymaga decyzji UX normalnych profili, 002 restartu rozszerzenia, 010 autoscroll/DnD, 016 IME, 019 własności pointer Compose, 043 rzeczywistej kolejności `src/currentSrc/loadedmetadata`. Nie zmieniać zachowania na podstawie samej hipotezy.
5. Każdy etap produkcyjny: minimalny diff, test odtwarzający błąd (RED w kontrolowanym przypadku), test prawidłowego przypadku, GREEN po naprawie, kontrola kombinacji sąsiedniej funkcji; GitHub CI **dla finalnego SHA** do `completed/success`, albo jawny `CI_PENDING/BLOCKED` bez deklaracji ukończenia. Android lint `abortOnError=false` wymaga rozróżnienia sukcesu joba i problemów lint.
6. Telefon (Nothing Phone (1), Android 15, 120 Hz), sesje prywatne, rotacja, gesty i ochrona danych pozostają `NOT_TESTED`, dopóki nie ma realnych prób. Nie wykonywać destrukcyjnych prób na profilu użytkownika. Zachować foldery, zapisane filmy, indeksy i cache polityki V3.2.
7. Etap 19 to bramka pełnego rechecku 60/60 i kontraktu wersji/aktualizacji. Przygotować testy podpisu bez budowania APK; rzeczywiste APK wyłącznie po `Kompiluj`, oddzielne od zakończenia etapu 1.

## Checkpoint etapu 1

- **Kod produkcyjny:** niezmieniony. **Zmiany dozwolone:** rejestr/plan i mały workflow walidacyjny dokumentacji (bez Gradle, assemble, podpisu, APK).
- **Weryfikacja samego rejestru:** 60 unikalnych ID, dokładnie po jednym przypisaniu, 19 nagłówków, 53/4/3 według poziomu pewności. Wynik dokumentacyjnego CI należy sprawdzić na SHA commita tej gałęzi.
- **Następne polecenie użytkownika:** `Dalej` → **Etap 2/19, ZC-AUD-011**. Zanim zacznie się edycja produkcyjna, ponownie potwierdzić HEAD i CI/zmiany równoległych gałęzi.

## Etap 2/19 — aktualizacja ZC-AUD-011 (2026-10-30)

- Aktualny v1195 nadal miał ścieżki `showAddFolderDialog` / `showAddBookmarkDialog` / `editBookmark`, których `AddBookmarkFolderDialog` i `AddBookmarkDialog` przechowują callback do `BookmarksBottomSheetFragment` przez czas dłuższy niż widok. Stare `onDestroyView` czyściło tylko binding i callbacki panelu, nie otwarte okna.
- Wprowadzono `BookmarkViewDialogScope` na jedną generację widoku; przejmuje okna, odłącza callbacki, unieważnia odświeżenie po zamknięciu i zamyka okna przed `super.onDestroyView` i zwolnieniem bindingu. Dotyczy osadzonego panelu i normalnego fragmentu dialogowego.
- Dodatkowe akcje sort/delete/flatten oraz wybór folderu otrzymały bramkę aktywnego widoku. Nie zmieniono reguł managera, nazw folderów, kolejności, zapisanych zakładek ani procedury podpisywania i wersji.
- Testy: `BookmarkViewDialogLifecycleTest` (właściciel, manual dismiss, rzeczywiste dwa warianty FragmentManager + dialog Robolectric), pełny JVM/Robolectric i pełny WebExtension JS oraz lint. Wykonanie CI i faktyczny wynik są raportowane osobno na konkretnym końcowym SHA; do jego potwierdzenia pozostaje `CI_PENDING`.
- Ograniczenie: brak rzeczywistego obracania telefonu i testu klikania okna po wywołaniu onDestroyView w Gecko/Android 15. Te scenariusze są `NOT_TESTED` na urządzeniu.
- Następny etap: **3/19 ZC-AUD-022**, typy importu preferencji. Bez merge do main, bez APK przed `Kompiluj`.

## Etap 3/19 — ZC-AUD-022: ochrona typów importowanych preferencji (2026-10-30)

- Baza: końcowy etap 2 `14a2006078a8ac4021d8cd3d311d3b36288fb75c`, potwierdzone CI [41116117575](https://github.com/laszko14/nira-browser/actions/runs/41116117575) SUCCESS. Osobna gałąź `fix/zavro-clean-audit60-stage3-settings-import-types-20261030`.
- Aktualne odtworzenie statyczne: `ImportExportSettingsFragment.importSettings` nadal konwertował każdą zawierającą cyfry/booleany wartość JSON typu String do Int/Boolean/Float; m.in. `collection_user="12345"` kolidował z `stringPreference` w `UserPreferences`. Stan: STILL_PRESENT na bazowym SHA.
- `SettingsImportCodec`: eksport/import formatu v1 zachowuje tekst; legacy nie zgaduje typów arbitralnych ciągów, ale przelicza tylko deklarowany typ klucza lub typ istniejącej wartości. Pełna walidacja typu i struktury przed jakimkolwiek edytorem. Odmowa nieznanych wersji/null/zagnieżdżonych obiektów/mieszanych tablic; pojedynczy `commit()` i jawna obsługa wyniku. Brak `clear()`, nietknięte inne ustawienia.
- Znane uszkodzone typy String (liczba/bool już zapisana przez stary import) są korygowane dla znanych kluczy przy późniejszym poprawnym imporcie; wszystkie 11 jawnych pól tekstowych `UserPreferences` mają bezpieczny odczyt i punktową naprawę, bez masowego resetu i bez przywracania niemożliwych do odzyskania zer wiodących.
- Test `SettingsImportCodecTest`: realne `SharedPreferences` w Robolectric, round-trip 5 tekstów, Int/Long/Float/Boolean/Set, starszy format `"key"="value"`, odmowa niepoprawnych wartości bez częściowego zapisu, zachowanie obcych danych i naprawa wcześniej błędnego klucza przy ponownym uruchomieniu ustawień. Wymagana pełna JVM/Robolectric, pełne JS i lint na końcowym SHA. Rzeczywiste Android 15, restart procesu i ręczne UI kolekcji na telefonie: NOT_TESTED.
- Bez APK, bez merge do main; następny etap **4/19 — ZC-AUD-026** dopiero po zielonym CI etapu 3.

## Etap 4/19 — ZC-AUD-026: zapis sesji przed zakończeniem (2026-10-30)

- Baza: `f73a29df98117baf7a353ec6b4aed1c2893b9ddd` (etap 3, CI [41122519025](https://github.com/laszko14/nira-browser/actions/runs/41122519025) SUCCESS), gałąź `fix/zavro-clean-audit60-stage4-session-exit-barrier-20261030`.
- Weryfikacja v1195: `BrowserActivity.onDestroy` wciąż bezwarunkowo wołał `Process.killProcess` przy root finish po stop media service. `AutoSave` Mozilla jest asynchroniczny z minimalnym interwałem 2000 ms; dlatego stan kart może pozostać niezapisany. `SessionStorage.save(BrowserState)` zwraca `Boolean`, używa blokady pliku i `AtomicFile` i filtruje prywatne karty.
- `BrowserApp` udostępnia `requestTaskExitSessionSave`, uruchamia jednorazową barierę `TaskExitSaveBarrier` w Application `SupervisorJob`. Przed finalnym zapisem musi zakończyć się przywracanie stanu (`restoreComplete` reduktora) oraz inicjalizacja AutoSave. Zapis używa najnowszego dostępnego `BrowserStore.state` po ukończeniu odtwarzania; API AC156 nie obsługuje oczekiwania przez `dispatch(...).join()`. `SessionStorage.save(store.state)` wywoływany jest na `Dispatchers.IO`. `killProcess` następuje tylko po `save == true`; w przypadku błędu I/O/restore/timeout nie wymuszamy kill ani resetu danych.
- `BrowserActivity` w normalnym root finish zachowuje dotychczasowy stop mediów i usługi, ale usuwa natychmiastowy kill. Onboarding (zanim browser UI jest zainstalowane), zwykłe tło, obrót, PiP i nie-root nie wywołują bariery. Brak synchronizowanego I/O na Main.
- Testy `TaskExitSaveBarrierTest`: opóźniony zapis S0→S1, restore gate, odmowa błędnego restore, `save=false`, exception dysku, idempotentne wielokrotne wywołanie, kontrola braku kill przed ack. Wymagana pełna JVM/Robolectric, WebExtension JS, Android lint. Na fizycznym Android 15: kill/relaunch i zamykanie/ponowne otwarcie kart/URL, pending AutoSave, PiP, obsługa first launch: NOT_TESTED.
- Ograniczenia: to gwarancja kolejności na kontrolowanej ścieżce `isFinishing && isTaskRoot`, nie gwarancja uratowania stanu po niespodziewanym zabiciu przez system. Żaden plik sesji nie był zmieniany ręcznie. Etap 4 nie zastępuje planowanych napraw startu/restora w etapie 9.
- Bez APK, bez merge do main; etap 5 **ZC-AUD-056** dopiero po zielonym CI końcowego SHA.

## Etap 5/19 — ZC-AUD-056: bezpieczny, atomowy import zakładek (2026-10-30)

- Baza: etap 4 `091f87afe06959ce5d6dcd353f1bff5221070080`, zielone CI [41124834816](https://github.com/laszko14/nira-browser/actions/runs/41124834816). Odseparowana gałąź `fix/zavro-clean-audit60-stage5-atomic-bookmarks-import-20261030`.
- Problem nadal występował: wejściowe `JSONArray` było zapisywane bez sprawdzenia elementów przez `FileOutputStream(..., false)`; błąd Moshi `readForRoot=false` nie był poprawnie propagowany. Ścieżka HTML i stary JSON usuwały plik zanim powstał kompletny nowy model. Przykład `[1]` mógł zastąpić plik niepoprawnym JSON.
- `BookmarkImportCodec.prepare` waliduje pełne drzewo własnego JSON, strukturę Netscape HTML i każdą linię formatu legacy w oddzielnym drzewie, bez modyfikacji danych aktywnych. Wymagane poprawne elementy, identyfikatory, zagnieżdżenie, URL i format; limit źródła 32 MiB, maksymalnie 100 000 wpisów i 64 poziomy. `[]` jest wyłącznie świadomym pustym importem w natywnym formacie.
- Zatwierdzenie `BookmarkImportCodec.atomicWrite` używa Android `AtomicFile` z `startWrite/finishWrite/failWrite`; w przypadku błędu stare bajty zostają zachowane. Dopiero po udanym zatwierdzeniu `BookmarkManager.importPrepared` przełącza `root` i odbudowuje indeks. `BookmarkManager.initialize` nie czyści aktywnego drzewa przed sprawdzeniem całego odczytu; także zwykły zapis managera używa AtomicFile, co chroni po awarii dysku.
- Fragment czyta dokument w ograniczonych porcjach na `Dispatchers.IO`, przygotowuje import, zapisuje i zgłasza sukces tylko po zatwierdzeniu; błąd URI/parsowania/zapisu daje komunikat bez kasowania danych. Nie zmieniono formatów eksportu zakładek ani folderów użytkownika.
- Regresje `BookmarkAtomicImportTest`: prawidłowy JSON→restart, poprawne `[]`, JSON `[1]` i inne zepsute schematy, HTML Netscape (w tym encje) i HTML uszkodzony, legacy line-JSON i brakujące pole, niepełny zapis z wstrzykniętym `IOException`, sprawdzanie identycznych bajtów starego pliku i wpisów indeksu po niepowodzeniu. Pełny JVM/Robolectric, JS, lint — wymagane CI na końcowym SHA.
- Granice: nie wykonywano fizycznej próby przerwania zasilania i restartu Androida 15; wynik automatyczny nie zastępuje ręcznej weryfikacji dużych kopii/folderów i odzysku na realnym urządzeniu. Zero APK, zero merge do main.
- Po zielonym CI następny etap 6/19: ZC-AUD-053/054 (prywatność diagnostyki i favicon).

## Etap 6/19 — ZC-AUD-053 + ZC-AUD-054: diagnostyka Sync i favicon private (2026-10-30)

- Baza: etap 5 `295b1ecc8efd2e47538a0d9dd827123a4493cb32`, zielone CI [41126492555](https://github.com/laszko14/nira-browser/actions/runs/41126492555). Gałąź: `fix/zavro-clean-audit60-stage6-private-sync-favicon-20261030`.
- **053 STILL_PRESENT**: `FxaSyncManager.finishAuthentication` utrwalał fragmenty OAuth code/state, adres email, wyniki i surowe wyjątki w `lastAuthDebugInfo`/logcat; `SyncSettingsFragment.onExportLogs` ujawniał to, accountEmail, avatar i syncError w ACTION_SEND oraz kopiował cały raport do Log.d.
- Naprawa: `SyncDiagnosticPolicy` dostarcza wyłącznie status próby, losowy identyfikator korelacji i typ błędu (bez komunikatu/stacktrace); opóźniona kontrola pokazuje tylko obecność konta. Przy wylogowaniu czyści ostatnią próbę. Komunikat błędu Sync bez surowego `e.message`. Raport Share jest budowany z ograniczonej listy stanów, bez email/awatara/URL/tokenu/parametrów OAuth i bez logowania całego eksportu. Kwalifikacja błędu i raport mają testy z syntetycznymi wartościami i historycznym skażonym `lastAuthDebugInfo`.
- **054 STILL_PRESENT**: `PortraitNativeTabStrip.bindFavicon` wołał `FaviconLoader` bez flagi prywatności, który używał wspólnego `FaviconCache` z trwałym `filesDir/favicon_cache`.
- Naprawa: `tab.content.private` propagowany przez rebind i async do `FaviconLoader.loadFavicon(..., isPrivate=true)` i `IconRequest(isPrivate=true)`. Dla private loader nie tworzy, nie czyta ani nie zapisuje współdzielonego `FaviconCache` (pamięć/dysk) także po późnym zakończeniu żądania. Zwykłe karty pozostają w dotychczasowym szybkim cache. Rebind rozróżnia prywatność, chroniąc recycled View. Brak masowego kasowania prawidłowego cache normalnych kart. Już istniejących plików prywatnych nie da się wiarygodnie odróżnić od zwykłych z powodu historycznego klucza tylko według domeny; nie przypisujemy im fałszywie udanego usunięcia.
- Regresje: `SyncDiagnosticPolicyTest` — sukces, błąd, opóźnienie, skażone debug-info, brak fragmentów OAuth i adresu e-mail w raporcie; `FaviconPrivateCacheTest` — private cold miss bez pliku, normalny disk/memory hit, wspólna domena private/normal, opóźniona odpowiedź przy zamknięciu karty i przekazanie isPrivate do Mozilla. Wymagane pełne `:app:testDebugUnitTest`, JavaScript, Android lint — wynik CI końcowego SHA osobno.
- Urządzenie Nothing Phone (1) Android 15, realne konta FxA i prawdziwa sieć/Gecko: NOT_TESTED. Bez APK i merge. Następny etap 7/19: ZC-AUD-052/060, dopiero po zielonym CI.

## Etap 7/19 — ZC-AUD-052 + ZC-AUD-060: TestLab Window i regresja urządzeniowa (2026-10-30)

- Baza: etap 6 `414f8a1a414f06828d2d17aca5e7863bfe9fa350`, zielone CI [41131554080](https://github.com/laszko14/nira-browser/actions/runs/41131554080). Osobna gałąź `fix/zavro-clean-audit60-stage7-testlab-measurements-20261030`.
- **052 STILL_PRESENT:** `MediaFloatingUi.showSheet` wykonywał `TestLab.attachWindow(dialog.window)` tylko przy tokenie `FAB_OPEN`. Podczas PREPARING `TestLab.capture()==null`, więc widoczne okno nie trafiało do mapy TestLabRuntime, a późniejsze begin()/JankStats nie miało czego podpiąć. Naprawa: rejestracja fizycznego Window niezależnie od tokenu; opóźnienie FAB_OPEN nadal mierzone wyłącznie przy prawidłowym tokenie. onDismiss zawsze wykonuje detach; runtime.stop() zwalnia pozostałe okna. Disabled TestLab: metody fasady pozostają no-op.
- Regresje `TestLabRuntimeTest`: Window dialogu otwarte w PREPARING i istniejące po przejściu do RUNNING, dismiss przed begin, otwarcie po begin bez tokenu, idempotentny attach, anulowanie podczas odliczania, stop z otwartym oknem oraz usunięcie nieinteraktywnego licznika. JVM/Robolectric **nie** dostarcza prawdziwych klatek JankStats ani FPS Gecko: te dane wymagają urządzenia.
- **060 STILL_PRESENT:** `TestLabDeviceTest` iterował 60-sekundowe przyciski starych trybów i wymagał `NiraSideTabs` DialogFragment. Bieżący UI udostępnia jeden `FULL_AUDIT` 120 s i osadzone `NiraEmbeddedSideTabs` / `NiraEmbeddedSideBookmarks`. Nowy scenariusz instrumentacji: realny start UI i 120 s, oba panele przez gesty i ich zamknięcie, obrót, kontrola pokrycia Window vs Gecko, aktualne versionCode/versionName odczytane z pakietu, PARTIAL po wcześniejszym stop oraz eksport bez nazw/ID fixture. Nie zakłada historycznego wersjonowania emulatora.
- Workflow regresji etapu 7 uruchamia pełne JVM/Robolectric, Node JavaScript oraz Android lint **bez assemble, APK i podpisu**. Test urządzeniowy jest zaktualizowany, lecz jego rzeczywiste wykonanie, klatki JankStats arkusza i fizyczny Android 15 są **NOT_TESTED** dopóki nie zostanie jawnie dozwolona kompilacja APK wymaganych przez instrumentację. Sam zielony JVM/lint nie dowodzi pomiaru klatek arkusza.
- Bez zmian profili, zakładek, odtwarzania, wyszukiwarki filmów, podpisu, pakietu, danych użytkownika czy merge do main. Następny etap po zielonym CI: **8/19**.
