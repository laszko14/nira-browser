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
| ZC-AUD-002 | możliwy brak uzgodnienia nowej generacji rozszerzenia | SUSPECTED | P2 | 15E/19 | BLOCKED — conditional JS/native collision demonstrated; real Gecko background restart with retained EngineSession NOT_TESTED |
| ZC-AUD-003 | starsze zadanie zmienia politykę aktywnego profilu | CONFIRMED | P2 | 8/19 | FIXED — 8B; Kotlin/JS/lint GREEN on code SHA d9e1047b; device NOT_TESTED |
| ZC-AUD-004 | brak końcowego sprzątania slotów zamkniętych kart | CONFIRMED | P2 | 11/19 | FIXED — 11B post-restore/Undo slot and Room prune; CI PASS; device NOT_TESTED |
| ZC-AUD-005 | scope komunikatu Cofnij przeżywa swojego właściciela | CONFIRMED | P2 | 11/19 | FIXED CORE — 11B independent snackbar scope; CI PASS; device UX NOT_TESTED |
| ZC-AUD-006 | pozostawione przełączniki nie sterują aktualnym interfejsem | CONFIRMED | P2 | 14G/19 | FIXED CORE — bar choice honored; list/grid settings/dialog synchronized with migrated key; unsupported swap and interface scale removed from UI; device NOT_TESTED |
| ZC-AUD-007 | uchwyt resize po niewłaściwej stronie w RTL | CONFIRMED | P2 | 12/19 | FIXED — 12A physical RIGHT grip + RTL Compose padding; CI PASS, device NOT_TESTED |
| ZC-AUD-008 | resize nie zachowuje tożsamości palca | CONFIRMED | P2 | 12/19 | FIXED — 12A pointer ownership, rollback on multi-touch/CANCEL; CI PASS, device NOT_TESTED |
| ZC-AUD-009 | nieatomowa walidacja tworzenia grupy z Bez grupy | CONFIRMED | P2 | 11/19 | FIXED — 11A atomic Room guard; JVM/JS/lint CI PASS; device NOT_TESTED |
| ZC-AUD-010 | hover pozostaje przy poprzednim celu podczas autoscroll | LIKELY | P2 | 12/19 | FIXED CORE — 12C live geometry, delayed guard; JVM/JS/lint CI PASS; real Compose device NOT_TESTED |
| ZC-AUD-011 | dialog zachowuje callback usuniętego panelu zakładek | CONFIRMED | P1 | 2/19 | IMPLEMENTED — stage 2, verify CI |
| ZC-AUD-012 | dwa kryteria sortowania działają jak nazwa | CONFIRMED | P2 | 14B/19 | FIXED CORE — URL sort and remove fake date, atomic save+rollback; Kotlin/Robolectric/JS/lint CI PASS, device NOT_TESTED |
| ZC-AUD-013 | kwadratowy koszt projekcji kolejności pasków kart | CONFIRMED | P2 | 11/19 | FIXED — 11A linear projection; JVM/JS/lint CI PASS; 120 Hz NOT_TESTED |
| ZC-AUD-014 | dialog kolejności nie rozróżnia upuszczenia przed i za wierszem | CONFIRMED | P2 | 14C/19 | FIXED CORE — drag row midpoint before/after + direct end; Kotlin/JS + CI FINAL_PENDING; device NOT_TESTED |
| ZC-AUD-015 | konfiguracja przycisków zależy od zmiany szerokości zamiast orientacji | CONFIRMED | P2 | 14D/19 | FIXED CORE — explicit Configuration callback and width+orientation invalidation; CI verification via SHA |
| ZC-AUD-016 | nieidempotentna obsługa klawiatury przy dismiss | LIKELY | P2 | 13/19 | FIXED CORE — 13A explicit dialog-owned IME hide, Kotlin/JS/lint CI PASS; device NOT_TESTED |
| ZC-AUD-017 | flaga gestów nie zatrzymuje już przejętego streamu | CONFIRMED | P2 | 12/19 | FIXED CORE — 12B immediate owner CANCEL and stream drain; Kotlin/JS/lint CI PASS, device NOT_TESTED |
| ZC-AUD-018 | starsza animacja toolbaru przeżywa rozpoczęcie nowego gestu | CONFIRMED | P2 | 13/19 | FIXED CORE — 13B generation/animator owner for WWW/Home; Kotlin/JS/lint PASS, device NOT_TESTED |
| ZC-AUD-019 | raw swipe karty nie respektuje własności gestu Compose | LIKELY | P2 | 12/19 | FIXED CORE — 12D pointer ownership + consumed/DnD/cancel, JVM/JS/lint PASS; real Compose device NOT_TESTED |
| ZC-AUD-020 | obsługa IME usuwa rezerwację miejsca na dolny pasek | CONFIRMED | P2 | 14F/19 | FIXED CORE — single margin owner composes chrome + IME + fullscreen; Kotlin/JS CI PENDING; device NOT_TESTED |
| ZC-AUD-021 | Anuluj nie cofa wyboru wyszukiwarki | CONFIRMED | P2 | 14A/19 | FIXED CORE — transactional picker & custom cancel; Kotlin/Robolectric/JS/lint CI PASS; device NOT_TESTED |
| ZC-AUD-022 | import własnego eksportu zmienia typy preferencji | CONFIRMED | P1 | 3/19 | IMPLEMENTED — stage 3, CI_PENDING |
| ZC-AUD-023 | wybór canonical default nie uzgadnia aktywnego profilu | CONFIRMED | P2 | 8/19 | FIXED — 8B; resolver/selection tests + CI GREEN on d9e1047b; device NOT_TESTED |
| ZC-AUD-024 | menu NewTab ma różną semantykę prywatności zależnie od profilu | CONFIRMED | P2 | 8/19 | FIXED — 8B; explicit NewTab privacy + regressions; device NOT_TESTED |
| ZC-AUD-025 | zakres sugestii kart nie jest ograniczony do bieżącego profilu | SUSPECTED | P2 | 8/19 | SCOPE_DEFINED — 8B local suggestions + guarded callbacks; device UX NOT_TESTED |
| ZC-AUD-026 | killProcess wyprzedza trwały zapis sesji | CONFIRMED | P1 | 4/19 | IMPLEMENTED — stage 4, CI_PENDING |
| ZC-AUD-027 | pusty stan startowy jest traktowany jak zamknięcie wszystkich kart | CONFIRMED | P2 | 9/19 | FIXED — stage 9, Kotlin/JS/lint CI PASS, device NOT_TESTED |
| ZC-AUD-028 | default PWA nie używa canonical kontekstu default | CONFIRMED | P2 | 10/19 | FIXED — stage 10; JVM/instrumented-compile/JS/lint CI PASS; device storage NOT_TESTED |
| ZC-AUD-029 | ContextMenuFeature PWA jest podłączony do obcej sesji | CONFIRMED | P2 | 10/19 | FIXED CORE — stage 10; PWA-owned hit menu CI PASS; device UX NOT_TESTED |
| ZC-AUD-030 | opener ma rozbieżne konteksty karty i przygotowanego engine | SUSPECTED | P1 (potencjalny) | 10/19 | OPEN — SUSPECTED, native OriginAttributes / storage test REQUIRED; NOT_FIXED |
| ZC-AUD-031 | usunięcie profilu pozostawia osierocone karty | CONFIRMED | P2 | 10/19 | PARTIAL — tabs/tombstones/erasure retry CI PASS; durable Gecko deletion NOT_VERIFIED |
| ZC-AUD-032 | konfiguracje script nie wracają po wznowieniu tego samego DOM | CONFIRMED | P2 | 15A/19 | FIXED CORE — BFCache script cache invalidated; JS regression, CI_PENDING; device NOT_TESTED |
| ZC-AUD-033 | childList z source nie skanuje istniejącego video | CONFIRMED | P2 | 15A/19 | FIXED CORE — source mutation rechecks owning VIDEO; JS regression, CI_PENDING; device NOT_TESTED |
| ZC-AUD-034 | brak wyzwalacza dla późnego open Shadow DOM | CONFIRMED | P2 | 15B/19 | FIXED CORE — MAIN-world open-root hint, isolated DOM verification, bounded rescan; JS regression, CI_PENDING; device NOT_TESTED |
| ZC-AUD-035 | deduplikacja pomija przywrócenie dostępu | CONFIRMED | P2 | 15C/19 | FIXED CORE — latest exact-URL response state/sequence handles 200→403→200 and 200→410→200; JS+Kotlin regressions; CI_PENDING; real Gecko NOT_TESTED |
| ZC-AUD-036 | ochrona DASH rozpoznawana z surowego XML | CONFIRMED | P2 | 17/19 | DO_RECHECK |
| ZC-AUD-037 | grupowanie wariantów zależy od kolejności DOM i sieci | CONFIRMED | P2 | 16A/19 | FIXED CORE — exact-URL manifest lineage reconciles reverse order; JVM/CI evidence, device NOT_TESTED |
| ZC-AUD-038 | wybór reprezentanta pomija metadane i dostępne alternatywy | CONFIRMED | P2 | 16B/19 | FIXED CORE — playback-ranked variant, explicit source menu, conflict-safe poster/time fallback, per-URL size, CI_PENDING, device NOT_TESTED |
| ZC-AUD-039 | niestabilna tożsamość konfiguracji odczytywanej z atrybutu | CONFIRMED | P2 | 16C/19 | FIXED CORE — stable DOM-owner/attribute/path source-set identity; JS regression; real Gecko NOT_TESTED, CI_PENDING |
| ZC-AUD-040 | odrzucane typowe ułamkowe FPS DASH | CONFIRMED | P2 | 17/19 | DO_RECHECK |
| ZC-AUD-041 | pochodzenie czasu HLS zmienia się na HTML5 | CONFIRMED | P3 | 17/19 | DO_RECHECK |
| ZC-AUD-042 | stary szacunek rozmiaru przeżywa utratę average bitrate | CONFIRMED | P2 | 17/19 | DO_RECHECK |
| ZC-AUD-043 | możliwe przeniesienie parametrów starego źródła na nowe src | LIKELY | P2 | 17/19 | VERIFY_FIRST |
| ZC-AUD-044 | czas DASH sumuje osobno zaokrąglone okresy | CONFIRMED | P3 | 17/19 | DO_RECHECK |
| ZC-AUD-045 | zwykłe video zostaje uznane za podgląd i wyciszone | CONFIRMED | P2 | 18/19 | DO_RECHECK |
| ZC-AUD-046 | odtwarzanie błędnego podglądu blokuje każdy kolejny tap | CONFIRMED | P2 | 18/19 | DO_RECHECK |
| ZC-AUD-047 | spóźnione przywrócenie toolbaru koliduje z kolejnym fullscreen | CONFIRMED | P2 | 14E/19 | FIXED CORE — generation/view identity guard of deferred restore and viewport; CI PENDING; device NOT_TESTED |
| ZC-AUD-048 | prostokąt toolbaru pod panelem nadal wygrywa gest | CONFIRMED | P2 | 13/19 | FIXED CORE — 13C live panel-ownership guard; Kotlin/Robolectric/JS/lint CI PASS, device NOT_TESTED |
| ZC-AUD-049 | różny znak flingu w toolbarze WWW i Home | CONFIRMED | P2 | 13/19 | ALREADY_RESOLVED — equal sign on 13C base; 13D shared policy/regressions CI PASS; device NOT_TESTED |
| ZC-AUD-050 | migracja profilu przenosi stan odtwarzania starego silnika | CONFIRMED | P2 | 10/19 | FIXED — stage 10; stale MediaSession reset with JVM CI PASS; device NOT_TESTED |
| ZC-AUD-051 | zmiana filmu w trasie hash zachowuje stare media | CONFIRMED | P2 | 15D/19 | FIXED CORE — hash material-route generation shared by background/native; old media dropped and stale DOM offers gated; JS/Kotlin regressions, CI_PENDING; device NOT_TESTED |
| ZC-AUD-052 | brak rejestracji okna filmów otwartego w PREPARING | CONFIRMED | P2 | 7/19 | FIXED — stage 7, JVM + CI PASS; device frames NOT_TESTED |
| ZC-AUD-053 | parametry uwierzytelniania w diagnostyce Sync | CONFIRMED | P2 | 6/19 | DO_RECHECK |
| ZC-AUD-054 | trwały cache favicon z prywatnej karty | CONFIRMED | P2 | 6/19 | DO_RECHECK |
| ZC-AUD-055 | probe pobierania nie respektuje anulowania | CONFIRMED | P2 | 18/19 | DO_RECHECK |
| ZC-AUD-056 | import nadpisuje zakładki przed walidacją | CONFIRMED | P1 | 5/19 | IMPLEMENTED — stage 5, CI_PENDING |
| ZC-AUD-057 | resume nie wiąże części z wersją zasobu | CONFIRMED | P2 | 18/19 | DO_RECHECK |
| ZC-AUD-058 | nowy intent PWA ginie przed utworzeniem fragmentu | CONFIRMED | P2 | 9/19 | FIXED — stage 9, Kotlin/JS/lint CI PASS, device NOT_TESTED |
| ZC-AUD-059 | nieatomowe mutacje listy profili | CONFIRMED | P2 | 8A/19 | FIXED — stage 8A, JVM + CI PASS; device NOT_TESTED |
| ZC-AUD-060 | nieaktualny scenariusz TestLabDeviceTest | CONFIRMED | P2 | 7/19 | IMPLEMENTED — stage 7, Android test compilation + CI PASS; device execution NOT_TESTED |

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

### Weryfikacja etapu 7 — checkpoint CI

- Run [41133086777](https://github.com/laszko14/nira-browser/actions/runs/41133086777), `completed/success` na SHA `cb665e038c27d74b05d50537fb826783da810734`: pełny Kotlin/JVM/Robolectric PASS, kompilacja źródeł instrumentacji Android PASS, pełny Node.js JavaScript PASS, Android lint SUCCESS. Żaden z tych kroków nie buduje APK.
- ZC-AUD-052: strukturalna regresja chroni bezwarunkową rejestrację dialog.Window, regresje Robolectric chronią cykl życia na granicach PREPARING/RUNNING/stop; to nie dowodzi prawdziwych klatek JankStats na Android 15.
- ZC-AUD-060: scenariusz i jego źródła przeszły kompilację instrumentacji; **sam test na urządzeniu nie został wykonany** z powodu zakazu budowania APK bez polecenia `Kompiluj`. Nie deklarować urządzeniowego PASS.
- Ten checkpoint dokumentacji został dopisany po przebiegu 41133086777; oddzielne CI dla aktualizowanego końcowego SHA pozostaje obowiązkowe i jest weryfikowane po push.

## Etap 8A/19 — ZC-AUD-059: atomowy rejestr profili (2026-10-30)

- Baza: etap 7 `afcaf4f7aceec972f10e5dca695825960028d01b`, CI [41134345578](https://github.com/laszko14/nira-browser/actions/runs/41134345578) completed/success. Gałąź `fix/zavro-clean-audit60-stage8-profile-isolation-20261030`.
- **059 STILL_PRESENT** na bazie: create/update/delete używały niezależnego read-modify-apply bez wspólnej blokady między instancjami. Równoległy import IO i dialog UI mogły trwale utracić zaakceptowane zmiany.
- Poprawka: wspólna w procesie blokada całej transakcji read/modify/commit dla wszystkich instancji managera; `commit()` zapewnia jawne odrzucenie nieudanego zapisu. Usunięcie aktywnego profilu zapisuje listę i fallback default atomowo. Brak czyszczenia danych przy brakującym ID lub nieudanym commicie. Zachowano ID, format JSON, domyślny profil i konteksty Gecko.
- Regresje: 48 jednoczesnych create z wielu instancji i odczyt po ponownym otwarciu managera; równoległe create/update/delete bez gubienia innych ID; wymuszona odmowa `SharedPreferences.Editor.commit()`; ochrona default/fallback aktywnego profilu; delete nieznanego ID bez zapisu. Realny restart procesu, awaria dysku i Android 15: NOT_TESTED.
- **Pozostałe do 8B/19:** ZC-AUD-003 (stare zadanie polityki dodatków), 023 (canonical default po selection), 024 (NewTab/private), 025 (polityka sugestii). Nie oznaczono ich jako naprawionych w 8A.
- Brak merge do main, brak APK, bez zmiany podpisu czy danych użytkownika.

### Etap 8A — wyniki i granice CI

- [GitHub Actions 41136992563](https://github.com/laszko14/nira-browser/actions/runs/41136992563): `completed/success` na SHA `95ebf74188c4ed5a8e4a8b816c749892a8174054`. Pełny Kotlin/JVM/Robolectric PASS, JavaScript PASS, Android lint SUCCESS; bez APK.
- Dwa wcześniejsze przebiegi FAILED dotyczyły wyłącznie nowo dodanych testów: niedozwolonego generycznego wywołania RuntimeEnvironment.getApplication oraz asynchronicznej polityki BrowserApp wywołanej na atrapowym Application. Pięć testów rejestru na drugim przebiegu PASS, ale wyjątek tła zatruł obcy GroupPersistenceTest. Końcowy zestaw izoluje efekt uboczny i ma zielone pełne CI.
- Końcowe potwierdzenie CI dla tego *dokumentacyjnego* commita następuje po zapisie, aby nie deklarować sukcesu dla innego SHA.

## Etap 8B/19 — uzgadnianie profilu, NewTab i sugestie (2026-10-30)

- Baza: 8A `228e04b9019a36dfff58b94c9b20c25b58c1de4e`, CI [41137945310](https://github.com/laszko14/nira-browser/actions/runs/41137945310) SUCCESS. Gałąź `fix/zavro-clean-audit60-stage8b-profile-scope-20261030`.
- **003 STILL_PRESENT:** stare niezależne zadania `ioScope.launch` mogły zmienić globalny stan dodatków później niż nowy profil. Teraz wspólna dla instancji sekwencja generacji i mutex obejmują pełne uzgadnianie z suspend/IO, a każda mutacja addon sprawdza aktualność po powrocie z IO i przed działaniem. Nowsza zmiana czeka na starą rozpoczętą, a stare zadanie oczekujące zostaje pominięte. Kontrola SearchEnginePreferences również po sprawdzeniu generacji.
- **023 STILL_PRESENT:** BrowserFragment ignorował `contextId=null`/legacy `profile_default`. SelectedTab→profil rozwiązywany jest jawnie tylko dla normalnych kart; private zmienia privateMode bez przypadkowego nadpisania ostatniego normalnego profilu. Obserwator reaguje też na zmianę kontekstu/prywatności bez zmiany tabId. BrowserActivity używa contextId faktycznie wybranej normalnej karty jako źródła dla nowego URL zamiast starego aktywnego profilu.
- **024 STILL_PRESENT:** menu nowej karty w BrowserMenuController i ComposeHomeFragment używa obecnego normal/private z wybranej karty albo BrowsingMode i jawnego kontekstu; dla default normal wysyła sentinel `profile_default`, który istniejący ProfileMiddleware kanonizuje do null, nie pozwalając zamienić jawnej normalnej karty na prywatną. NewPrivateTab pozostała osobną akcją.
- **025 VERIFY_FIRST → wybrana polityka lokalna:** paski kart i panele ograniczają się do aktywnego profilu. Identyczny zakres przyjęto dla nieprywatnych sugestii: default null/legacy lub dokładny `profile_ID`, bez innych normalnych profili i private. Callback select/close sprawdza profil i przynależność ponownie, jeżeli użytkownik zmieni zakres między wyświetleniem a kliknięciem. Nie ograniczano globalnej historii ani zakładek.
- Regresje: czysty resolver default/named/legacy/private/unknown, target NewTab macierz default/named × normal/private, filtr zakresu i wyłączenie private, serializacja i generacja A→B→A oraz odrzucanie starych operacji. Testowanie realnego addon managera, rzeczywistych callbacks i telefonu Android 15: NOT_TESTED. Nie zmieniono istniejących contextId ani danych użytkownika. Bez APK i merge.
- Wymagane pełne CI Kotlin/Robolectric, JavaScript, Android lint na końcowym SHA.

### Etap 8B — checkpoint po weryfikacji pierwszej rewizji

- [CI 41139260080](https://github.com/laszko14/nira-browser/actions/runs/41139260080) ukończone `completed/success` na SHA `d9e1047b7daef3bf4a31c75c6e2dc92713c86c0c`: pełny Kotlin/Robolectric, JavaScript oraz Android lint. Po tym przebiegu wzmocniono ochronę kliknięcia starych sugestii przy przejściu do private i dodano test rzeczywistego ProfileMiddleware dla jawnej normalnej karty default.
- Ostateczny status wymaga ponownego `completed/success` na końcowym SHA po dopisaniu tych regresji. Wszelkie realne kliknięcia i callbacks addon engine na telefonie Android 15 pozostają NOT_TESTED. W przypadku niedostępności CI nie uznawać etapu za ukończony.

### Uzupełnienie 8B — asynchroniczne zakończenia AddonManager

- Potwierdzono w API Mozilla Components, że `enableAddon`/`disableAddon` używają asynchronicznych callbacków, a `getAddons(waitForPendingActions=true)` oczekuje na zadania zarządzane przez AddonManager. Przed odczytem i po zleceniu mutacji polityka 8B czeka na oczekujące operacje. Blokada `ProfilePolicySequencer` obejmuje to oczekiwanie, więc profil B nie może uznać profilu A za zakończony, zanim A zamknie kolejkę działań. Nie zmienia to wyboru globalnego katalogu dodatków ani uprawnień.
- Regresje JVM testują kolejność i generacje, nie emulują rzeczywistego Gecko callback. Rzeczywista integracja i obciążone siecią dodatki: NOT_TESTED na urządzeniu.

## Etap 9/19 — ZC-AUD-027 i ZC-AUD-058: start/restore oraz intenty PWA (2026-10-30)

- Baza `3fc99274c524ce34009f2a740838c15932777007`, zielone CI 8B [41140352443](https://github.com/laszko14/nira-browser/actions/runs/41140352443); gałąź `fix/zavro-clean-audit60-stage9-restore-pwa-intents-20261030`.
- **027 STILL_PRESENT**: restore oraz observer liczby kart posiadały konkurencyjne tworzenie startowej karty. Zredukowano to do jednego obserwatora `observeRestoreComplete`; nic nie tworzy karty przy `restoreComplete=false`, zapobiega powtórnemu wstrzyknięciu przy pustych emisjach i po rzeczywistym zamknięciu ostatniej karty może utworzyć nową. Ukończony restore z kartami i wybranymi intencjami nie tworzy dodatkowego homepage.
- **058 STILL_PRESENT**: `onNewIntent` cicho porzucał URL, jeśli `WebAppFragment` nie był jeszcze dodany, a opóźniony onCreate nadpisywał nowsze żądanie. Teraz wspólna generacja `WebAppLaunchGate` z `Job.cancel()` sprawdza aktualność lookupu, instalacji fragmentu (również do pustego kontenera) i opisu zadania/ikony. FragmentManager `isStateSaved` odracza instalację do `onPostResume`. Ten sam URL/profil jest idempotentny po obrocie/restore.
- Regresje: gate bootstrap false/true/empty/intent/close; PWA A→B oraz B→C, stare wyniki, kolejność ikon, stan zachowany. JVM/Robolectric oraz JavaScript/lint; szczegóły dowodów i CI poniżej.
- Urządzenie Android 15: fizyczny cold restore PWA singleTop i realne opóźnienia Gecko/dysku NOT_TESTED. Nie zmieniać 028/029/030/031/050 (etap 10). Bez APK i merge.

### Etap 9 — checkpoint implementacyjny

- Zapisano atomowy commit `579e2ccd9f12ba32d4d493b5946b309ad978d4a7` z dwoma gate, poprawionymi observerami i PWA oraz regresjami. Status: CI_PENDING. Nie uznawać etapu za ukończony bez zielonego przebiegu GitHub Actions dla finalnego SHA.

### Etap 9 — wyniki CI i pozostające testy urządzeniowe

- [GitHub Actions 41142164683](https://github.com/laszko14/nira-browser/actions/runs/41142164683), `completed/success` na SHA `1a3ce33a911788bc2eca915d6eb3aedefd3ba31b`. Kotlin + Robolectric PASS, JavaScript **297/297 PASS**, Android lint SUCCESS. Żadnego tworzenia APK ani zmiany podpisu.
- W pierwszym przebiegu [41141625822](https://github.com/laszko14/nira-browser/actions/runs/41141625822) Kotlin PASS, JavaScript 296/297. Jedna nowa asercja strukturalna niepoprawnie liczyła `addTab` w całej klasie, obejmując niezwiązany kod; poprawiono zakres do obu obserwatorów i cały zestaw jest zielony.
- Kontrakt rozdziela pre-restore i rzeczywisty last-close, chroni przed duplikatem przy empty, preservuje wybór odzyskanej karty i pierwszeństwo rzeczywistego intentu. Kontrakt PWA porządkuje zimny start, późniejsze żądania, stan fragmentu i ikonę przy generacji ostatniego intentu.
- **NOT_TESTED**: faktyczne cold boot i opóźnienie SessionStorage, działanie pojedynczego PWA na urządzeniu Android 15, prawdziwy FragmentManager przy obracaniu telefonu, realne pobieranie ikon. Przy kompilacji APK do testów konieczna regresja manualna (restore snapshot→normal/private tabs, puste/corrupt storage, A→B→C PWA singleTop, obrót i tło).
- CI w tym punkcie dotyczy commita kodu; końcowa dokumentacja powinna także mieć osobny zielony przebieg na końcowym SHA.
- Następny etap po zakończeniu 9/19: **10/19** — ZC-AUD-028, 029, 030 (VERIFY_FIRST), 031 i 050. Bez merge do main.

## Etap 10/19 — PWA, konteksty, kasowanie profili, MediaSession i opener (2026-10-31)

- Baza etapu 9: `82cc03077114417292320e7c50e85ed40d8ff582`, CI [41143047578](https://github.com/laszko14/nira-browser/actions/runs/41143047578) completed/success. Gałąź `fix/zavro-clean-audit60-stage10-pwa-profile-erasure-20261031`.
- **028 CONFIRMED**: PWA dla default tworzył `"profile_default"` zamiast `null`. WebAppFragment wyznacza contextId przez `BrowserProfile.contextIdFor(id,false)`. Nie wykonuje destrukcyjnej migracji starych danych/cookies w `profile_default`: istniejące instalacje mają dwie historyczne partycje do świadomej migracji. Named pozostaje `profile_UUID`; brak łączenia danych innych profili.
- **029 CONFIRMED**: usunięto `ContextMenuIntegration(sessionId=null)` z PWA, bo czytała niewłaściwą `BrowserStore.selectedTab`. Nowa obsługa `EngineSession.Observer.onLongPress(HitResult)` korzysta wyłącznie z odrębnego silnika PWA; pokazuje bezpieczny dla URL (HTTP/S) zestaw Open in browser / Copy address / Share link. Dodatkowe dotychczasowe akcje menu głównej przeglądarki (np. pobranie grafiki) nie są przenoszone bez tożsamości PWA; ręczna ocena UX nadal konieczna. PWA session nie trafia do głównego Store tylko po to, by otworzyć menu.
- **050 CONFIRMED**: migracja `TabSessionState` zeruje `mediaSessionState` razem z EngineState, aby nowa sesja nie przejmowała kontrolera/playbackState starego engine. Zachowany ID, adres i metadane wyboru/grup poza planową zmianą parentId.
- **031 CONFIRMED**: na kasowanie profilu nałożono preflight aktywnych PWA / custom tabs, jawne usuwanie jego normalnych kart bez tworzenia Undo, tombstone profilu w tej samej transakcji SharedPreferences co usunięcie rejestru, a `ProfileMiddleware` filtruje późne RestoreAction/AddTabAction z usuniętych contextId. W tle domykane jest powiązanie grup, wymagany jest `SessionStorage.save(state)==true`, potem `GeckoRuntime.storageController.clearDataForSessionContext(profile_ID)` tylko dla danego named profile. API Gecko zwraca void, więc brak potwierdzenia trwałego kasowania na dysku. Jeżeli save/erasure zawiedzie, rejestr i tombstone są już zapisane, log zgłasza niedokończone czyszczenie; wymagany retry i manualny test na urządzeniu przed deklaracją pełnego erasure. Dialog nie przełącza aktywnego profilu, jeżeli usuwanie zostało odrzucone.
- **030 SUSPECTED, VERIFY_FIRST / NOT_RESOLVED:** upstream Mozilla Android Components GeckoEngineSession.onNewSession konstruuje child GeckoEngineSession bez jawnego contextId; GeckoWindowRequest.prepare zwraca tę sesję, WindowFeature przekazuje prepared engine + parentId, a ProfileMiddleware przydziela label rodzica. Weryfikacja źródeł potwierdza rozbieżność `Store.contextId` i `GeckoSessionSettings.contextId`, ale nie dziedziczenie natywnych OriginAttributes Gecko przez opener. Nie wolno deklarować wycieku cookies ani podmieniać engine (zerwałoby window.opener) bez testu izolowanego `window.open`/target_blank; 030 pozostaje otwarty jako bezpieczeństwo do zweryfikowania na urządzeniu.
- Regresje: Kotlin/Robolectric dla PWA hit-source i guardów live PWA, kasowania kart nazwanych/tombstone i MediaSession PLAYING; JS kontrakty mappera, separacji PWA Store i 030 (statyczny marker). Rzeczywista sesja browser/profile oraz model wykasowania cookies na Android 15 **NOT_TESTED**. Nie ma APK, podpisu, merge do main.
- Po zielonym pełnym CI: zaktualizować statusy wyników dla 028/029/031/050, zachować 030 VERIFY_FIRST.

### Etap 10 — niezawodność wznowienia erasure

- Dodatkowa kontrola wykazała ryzyko: nawet przy tombstone poprawny zapis profili przed crash nie oznacza, że nieodwracalna operacja Gecko już wystartowała. Tombstone jest więc również dziennikiem ponawiania: `BrowserApp.restoreBrowserState` po `restoreComplete` i konfiguracji autoSave wywołuje `ProfileManager.retryDeletedProfileErasure()`. Dla każdego nieistniejącego named ID najpierw blokuje otwarcie nowej PWA, sprawdza brak tab/customTab z tym kontekstem, wymaga sukcesu SessionStorage.save, ponawia bezpiecznie wyłącznie `clearDataForSessionContext(profile_ID)`. Brak potwierdzenia trwałego kasowania przez API void — kolejne cold launch może ponowić. Żadnego globalnego `clearData`.

### Etap 10 — pełna weryfikacja GitHub Actions

- **PASS:** [workflow 41145757527](https://github.com/laszko14/nira-browser/actions/runs/41145757527) `completed/success` dla dokładnego SHA `d60b9b0cfd0b9d096a5d32ab99524b304343238e`: cały Kotlin/Robolectric PASS, `:app:compileDebugAndroidTestKotlin` PASS (bez APK), Node.js 301/301 PASS, `:app:lintDebug` SUCCESS.
- Pierwsze dwa nieudane przebiegi dotyczyły nadmiernie dosłownych asercji nowych testów JS (komentarz o ContextMenuIntegration i nazwa zmiennej SessionStorage.save). Produkcja oraz JVM/instrumentation compile w obu były zielone; finalny test strukturalny sprawdza działanie, nie komentarze i lokalne nazwy.
- **028:** potwierdzono zmianę mappera PWA na `null` dla default; zachowanie historycznej partycji `profile_default` i cookie migracja są świadomie poza zakresem automatycznej, destrukcyjnej operacji.
- **029:** appowe menu długiego przytrzymania czyta tylko własny `EngineSession.Observer.HitResult`. Zestaw Open/Copy/Share zawiera funkcje podstawowe; pełna zgodność z gestami i akcjami graficznymi wymaga ręcznej oceny.
- **031:** restore z tombstoned contextId jest filtrowany, named tabs są zamykane, PWA/customTabs stanowią blokadę, zapis sesji ma potwierdzenie Boolean, a Gecko clearDataForSessionContext ma plan ponowienia po restore. **Nie ma potwierdzenia zakończenia trwałego usunięcia cookies ani historycznego cache, ponieważ API Gecko zwraca void.** Status PARTIAL pozostaje do czasu testu izolowanych kontekstów na fizycznym urządzeniu i potwierdzenia braku późnego odtworzenia. Pozostałe profile mają pozostać nietknięte.
- **030:** mismatch etykiet engine Store istnieje, ale bez natywnego testu `OriginAttributes` nie można ani potwierdzić wycieku, ani stwierdzić bezpiecznego dziedziczenia. Status OPEN. Nie odłączać gotowego engine od opener bez weryfikacji. Wymagana kontrolowana testowa strona A/B z `window.open`, `target=_blank`, `noopener`, private i suspend/restart, bez produkcyjnych loginów.
- Nie budowano APK, nie zmieniono signing i nie wykonano merge. Kolejne `Dalej` rozpocznie **11/19**; nierozstrzygnięte testy urządzeniowe 030/031 pozostają w rejestrze i nie zostają fałszywie uznane za PASS.
- Końcowy commit dokumentacji jest osobnym SHA; wymagane zielone CI również dla niego.


## Etap 11A/19 — recovery grup i liniowa projekcja (2026-10-31)

- Baza: stage 10 `b346f8275170fed23c1a037f6d305a18f305883b`, CI [41146634949](https://github.com/laszko14/nira-browser/actions/runs/41146634949) SUCCESS. Branch `fix/zavro-clean-audit60-stage11-tabs-undo-groups-20261031`.
- ZC-AUD-009 STILL_PRESENT: dawny preflight w ViewModel nie był atomowy z mutacją Room. Nowe `createGroupIfUngrouped` sprawdza bieżące członkostwo i tożsamość profilu pod blokadą `UnifiedTabGroupManager.mutate`, w tej samej transakcji co `createGroup`; przy konflikcie zwraca null bez częściowego przenoszenia. Zwykłe `createGroup` nadal świadomie przenosi członków. Testy: konflikt z już przydzieloną kartą, wyścig z wcześniejszą mutacją, izolacja kontekstów.
- ZC-AUD-013 STILL_PRESENT: `TabBarCompose.buildBarItems` zastępuje wielokrotne `tabs.find` mapą ID→Tab i mapą grup; natywny `PortraitNativeTabStrip.updateTabs` stosuje Set zamiast listowego contains. Sprawdzane: żywe grupy, brakujące ID, nowa karta, kolejność odwrócona 400 kart. Zmiana złożoności wyszukiwania O(n²)→O(n) dla opisanych fragmentów; rzeczywisty FPS na 120 Hz pozostaje NOT_TESTED.
- Kryteria: pełne JVM/Robolectric, instrumented-test compilation (bez APK), Node JS, lint w GitHub Actions `zavro-audit60-stage11-regression.yml`. Oznaczyć PASS dopiero po zielonym CI finalnego SHA. Nie zmieniono certyfikatu, wersji, prywatnych sesji ani danych.
- **Etap 11B/19 (następne `Dalej`):** ZC-AUD-004, ZC-AUD-005 — kontrolowany cleanup po Undo/restoration, właściciel globalnego snackbar; wymagają osobnych regresji, bez przedwczesnego usuwania slotów.

### Etap 11A — CI GREEN, ograniczenia regresji

- [GitHub Actions #41149556383](https://github.com/laszko14/nira-browser/actions/runs/41149556383) — `completed/success` dla dokładnego commita `cbfd05bc8c014367924a4ad67ab80a57794bad60`: pełne Kotlin/Robolectric PASS, kompilacja instrumented test sources PASS (bez APK), JS 301/301 PASS i Android lint SUCCESS (wykonanie lint, nie deklaracja braku wszystkich ostrzeżeń).
- Dwa stare testy statyczne wymagały zmiany kontraktu oczekiwanych symboli: kolor grup pochodzi teraz z `groupsById`, a recovery sprawdza członkostwo w mutacji managera zamiast w ViewModel. Testy zachowano, zaktualizowano asercje.
- Testy uruchomione na CI są potwierdzeniem logiki i regresji, nie pomiarem płynności urządzenia ani testem fizycznego Gecko. **Nie zamyka to ZC-AUD-004/005** — kontynuacja wyłącznie w 11B/19.
- Nowy commit raportu wymaga ponownego CI na jego SHA; do potwierdzenia `CI_PENDING` dotyczy tylko tego końcowego dokumentacyjnego commita.


## Etap 11B/19 — zamknięte sloty i własność Undo (2026-10-31)

- Baza: `886fa5628a84525171de0635ead2be15d1c1c87c` (11A, [CI #41150245036](https://github.com/laszko14/nira-browser/actions/runs/41150245036) SUCCESS). Gałąź `fix/zavro-clean-audit60-stage11b-undo-slot-finalization-20261031`.
- **004 STILL_PRESENT → IMPLEMENTED:** wcześniej `reconciled` ponownie dopisywał wszystkie zamknięte identyfikatory. Nowy obserwator `BrowserApp` jest aktywowany dopiero po potwierdzonym `restoreComplete`, rozróżnia identyfikatory żywe i oczekujące w `BrowserState.undoHistory` i czeka 6 sekund po ostatniej zmianie tej struktury. Wtedy `TabOrderManager.finalizeClosedTabs` serializuje usunięcie niechronionych slotów i osieroconego członkostwa Room. Pending `pending_root` pomija dopóki nie zostanie odtworzony. Brak globalnego kasowania sesji lub profili.
- **005 STILL_PRESENT → IMPLEMENTED:** `GlobalSnackbarManager` nie używa już scope żadnej kompozycji (ani paska, ani panelu). Posiada własny scope i jeden `SnackbarHostState`, więc zamknięcie panelu nie anuluje Undo w przeglądarce. Starsze żądanie jest anulowane przed nowym; przy braku hosta komunikat nie może pozostać dłużej niż 5 s. Zachowano `TabsUseCases.undo()` i kontrakt identycznego ID.
- Testy: `OrderPersistenceTest` (300 zamkniętych slotów, niepełny restore, pending Undo, członek grupy/Room i odtwarzanie ID) oraz `GlobalSnackbarOwnerTest` (zamknięty panel, timeout i zastąpienie starego komunikatu). Kontrole pośrednie JVM + AndroidTestKotlin compile + JS + lint. Wynik dla finalnego SHA musi być potwierdzony przez GitHub Actions.
- Nierozstrzygnięte ZC-AUD-030 OPEN / ZC-AUD-031 PARTIAL pozostają bez zmian; brak testów fizycznych Gecko/120 Hz i bez niszczenia danych. Bez APK, signing i merge. Następny etap dopiero po zakończeniu 11B i osobnym `Dalej`: **12/19**, panele/gesty.

### Etap 11B — końcowy checkpoint regresji

- **PASS:** [workflow #41152171168](https://github.com/laszko14/nira-browser/actions/runs/41152171168), `completed/success` na dokładnym produkcyjnym SHA `d9f07e8acbb512b095c1eb262674f9d8bb3aff65`. Pełny Kotlin/JVM/Robolectric, kompilacja instrumented tests bez APK, WebExtension JS i Android lint mają wyniki SUCCESS. Lint nie oznacza braku wszelkich ostrzeżeń.
- Dodatkowa regresja wymusza ochronę `pending_root` **przed** sprzątaniem Room, nie tylko przy zapisie DataStore. Testy obejmują wygaśnięte Undo, trwające Undo, niepełny restore, 300 historycznych zamkniętych slotów, członkostwo grupy, ponowne pojawienie się ID oraz zakończenie przerwanego move.
- Snackbar ma własny scope zamiast przejmowania scope z Compose; testy chronią przed disposal krótkotrwałego panelu, brakującym hostem i zastąpieniem starego Undo. Realny UI / Android 15 / 120 Hz: **NOT_TESTED**, nie wpisywać PASS dla urządzenia.
- Nie zmieniono wersji, certyfikatu, PWA/Gecko storage ani ustawień profili. Brak merge do main, brak APK. ZC-AUD-030 nadal OPEN, 031 PARTIAL.
- Ten dokumentacyjny commit jest osobnym SHA: końcowe CI sprawdzić także dla niego. Następne `Dalej` → **12/19**.


## Etap 12A/19 — fizyczna krawędź i właściciel resize (2026-10-31)

- **Baza:** etap 11B `8cb2f17393eeffd516eba349bb4adba6d528cfe7` z zielonym [CI #41153064645](https://github.com/laszko14/nira-browser/actions/runs/41153064645); osobna gałąź `fix/zavro-clean-audit60-stage12a-resize-rtl-pointer-20261031`. Brak merge, APK i zmian podpisu.
- **ZC-AUD-007 STILL_PRESENT → IMPLEMENTED:** rzeczywisty `TabsBottomSheetFragment.setupSidePanelResizeGrip` nadal korzystał z `Gravity.END`, a Compose rezerwował `padding(end=20.dp)`, gdy host używa fizycznej LEFT. Uchwyt używa teraz fizycznego `Gravity.RIGHT`; rezerwa Compose w RTL używa `start` (fizyczna prawa), w LTR `end`. Dialogowy fallback ma fizyczne `Gravity.LEFT`. Nie wymuszono LTR dla tekstu ani nie ruszono wartości zapisanych szerokości.
- **ZC-AUD-008 STILL_PRESENT → IMPLEMENTED:** stary handler sumował rawX z indeksu 0 bez identyfikatora palca i zapisywał wynik po `UP`. Nowy `SidePanelResizeGesture` wiąże strumień z `pointerId` przy `DOWN`; dodatkowy palec, utrata właściciela lub `ACTION_CANCEL` kończą operację i cofają niezapisany draft. Tylko legalne `UP` właściciela zapisuje zmianę. Klucz orientacji utrwalany jest przy `DOWN`, osobno `width_portrait` / `width_landscape`. Własność gestu zwalnia blokadę rodzica po zakończeniu i nie przekazuje reszty anulowanego streamu do wierszy.
- **Regresje źródłowe:** `SidePanelResizeGestureTest` używa prawdziwych zdarzeń `MotionEvent` (pojedynczy palec, indeksy 0→1, `POINTER_DOWN/UP`, `CANCEL`, restart gestu, niezależny klucz orientacji) i `CoordinatorLayout` w LTR/RTL do pomiaru fizycznych bounds uchwytu. Integracyjne dotknięcie osadzonego panelu, landscape/portrait i 120 Hz na Nothing Phone (1) pozostaje **NOT_TESTED**.
- **CI:** `.github/workflows/zavro-audit60-stage12a-regression.yml` na push tej gałęzi — pełny `testDebugUnitTest`, `compileDebugAndroidTestKotlin` bez APK, Node JS i `lintDebug`. Wynik musi być zweryfikowany na **finalnym SHA**, a nie na poprzednim etapie. Status przy zapisie: **CI_PENDING**.
- **Nierozwiązane w etapie 12:** ZC-AUD-010 (VERIFY_FIRST; autoscroll hover), ZC-AUD-017 (DO_RECHECK; wyłączenie przejętego gestu), ZC-AUD-019 (VERIFY_FIRST; Compose DnD/swipe); wymagają kolejnych podetapów z osobnymi testami. ZC-AUD-030 nadal OPEN, 031 PARTIAL.
- **Następny podetap po 12B:** 12C/19 — ZC-AUD-010 (VERIFY_FIRST), autoscroll i hover.


## Etap 12B/19 — anulowanie przejętego gestu (2026-10-31)

- **Baza:** 12A poprawka kompilacji testu `4e4ea2824af64069dc993dc66d8294ba67437b51` na gałęzi `fix/zavro-clean-audit60-stage12a-resize-rtl-pointer-20261031`. Pierwsze CI 12A [#41154231746](https://github.com/laszko14/nira-browser/actions/runs/41154231746) FAIL na test Kotlin compile (niepoprawny parametr generyczny `RuntimeEnvironment.getApplication<Application>()`), naprawione w 4e4ea28. Powtórne CI 12A [#41154605539](https://github.com/laszko14/nira-browser/actions/runs/41154605539) zakończyło się completed/success na dokładnym kodowym SHA 4e4ea28: pełny Kotlin/Robolectric, instrumented compile, WebExtension JS i Android lint PASS (lint job SUCCESS nie oznacza zero ostrzeżeń).
- **ZC-AUD-017 STILL_PRESENT → IMPLEMENTED:** na wejściu 12B `SwipeGestureLayout.isSwipeEnabled` nadal było zwykłym polem; po przejęciu MOVE i bezpośrednim skierowaniu kolejnych eventów do `onTouchEvent`, przełączenie flagi na false mogło nadal wywołać update/finish. Setter teraz natychmiast unieważnia właściciela, wywołuje dokładnie jedno `onSwipeCancelled` i oznacza pozostałą część strumienia jako zablokowaną. Obie ścieżki `onInterceptTouchEvent` / `onTouchEvent` sprawdzają flagę i blokadę. Wcześniej przejęty touch pozostaje konsumowany przez rodzica do UP/CANCEL; ponowne włączenie nie wskrzesza starego gestu bez świeżego DOWN. Stan jest czyszczony przy końcu strumienia; nie zmieniono kolejności panel/toolbar ani mapowania fizycznych krawędzi.
- **Regresje:** `SwipeGestureLayoutArbitrationTest` rozbudowano o rzeczywisty Robolectric `ViewGroup.dispatchTouchEvent`: przejęcie, disable przed MOVE/UP, dokładnie jedno CANCEL, brak update/finish, re-enable w środku nadal nieaktywny, od DOWN disabled, drugi palec/CANCEL i ponowne rozpoczęcie poprawnego gestu. Pełne `testDebugUnitTest`, `compileDebugAndroidTestKotlin` bez APK, WebExtension JS i Android lint w `.github/workflows/zavro-audit60-stage12b-regression.yml` na dokładnym finalnym SHA. Zweryfikowano **CI PASS** na produkcyjnym SHA 08706ce; finalny commit dokumentacyjny wymaga osobnej weryfikacji.
- **Bezpieczeństwo:** brak zmian danych/profili, prywatnych kart, Gecko storage, wersji, podpisu i plików APK; brak merge do main. Android 15/Gecko/120 Hz i faktyczny fullscreen pozostają **NOT_TESTED** na telefonie; nie przypisywać im PASS.
- **Następne `Dalej`:** po zielonym finalnym CI 12B → 12C/19 ZC-AUD-010 VERIFY_FIRST (DnD/autoscroll hover), następnie 12D ZC-AUD-019 VERIFY_FIRST. ZC-AUD-030 OPEN, ZC-AUD-031 PARTIAL.

- **Zweryfikowane CI 12A:** [#41154605539](https://github.com/laszko14/nira-browser/actions/runs/41154605539) `completed/success` na `4e4ea2824af64069dc993dc66d8294ba67437b51`; Android device test nadal NOT_TESTED.


### Etap 12B — końcowy checkpoint testów

- **PASS**: [GitHub Actions #41155198142](https://github.com/laszko14/nira-browser/actions/runs/41155198142), `completed/success` na dokładnym kodowym SHA `08706cebeb345440fa10c31237d9868966306fdc`. Kroki `testDebugUnitTest` (Kotlin/JVM i Robolectric), `compileDebugAndroidTestKotlin` (bez APK), `node --test` i `lintDebug` mają wszystkie `completed/success`. Sukces lint nie oznacza nieobecności ostrzeżeń.
- Regresje obejmują wyłączenie gestów już po intercept, jednokrotne anulowanie, brak dalszego update/finish, re-enable w tej samej sekwencji, fresh DOWN i dwa palce. To kontrola platformowego ViewGroup przez Robolectric, nie test na fizycznym Androidzie/Gecko.
- ZC-AUD-017: FIXED CORE w zakresie zaobserwowanego zachowania i nowych testów. Test realnego fullscreen i interakcji toolbar/panel/Gecko pozostaje NOT_TESTED. ZC-AUD-010 i ZC-AUD-019 nadal VERIFY_FIRST.
- ZC-AUD-030 nadal OPEN; ZC-AUD-031 nadal PARTIAL. Brak merge, APK, wersjonowania, zmian podpisu, preferencji, zapisanych danych i sesji.
- Końcowy commit aktualizujący ten rejestr wymaga również zielonego CI na dokładnym SHA; do takiej weryfikacji checkpoint dokumentacyjny pozostaje CI_PENDING.


## Etap 12C/19 — live DnD hover podczas autoscroll (2026-10-31)

- **Baza:** etap 12B `bcb83c0a68d1e15775769201cf8df2b3ba754085`, [GitHub Actions #41155970845](https://github.com/laszko14/nira-browser/actions/runs/41155970845) completed/success. Gałąź `fix/zavro-clean-audit60-stage12c-dnd-hover-autoscroll-20261031`.
- **ZC-AUD-010 LIKELY → REPRODUCED in source-model → IMPLEMENTED:** aktualny `CompactSideTabs` ponownie potwierdził brak refresh `hoveredKey` w pętli `scrollBy`. Rzeczywiste dopasowanie celu w `finishDrag` pobierało późniejszy `layoutInfo`; podświetlenie i opóźnione rozwinięcie używały starego wiersza. Analogiczna izolowana reprodukcja z oryginalnego audytu: nieruchome `y=75`, wiersz B:50..100 przed scroll, B:0..50 i C:50..100 po scroll → podświetlenie B przy aktualnym drop C. Nie uznawać tego za test fizycznego Compose na telefonie.
- **Naprawa:** jedna funkcja `currentSideDrop` pobiera **aktualne** `LazyListState.layoutInfo` i wspólny `DragPlacement.sideTarget` dla hover przy MOVE, `snapshotFlow` zmian geometrii, końca każdego `scrollBy` i finalnego `finishDrag`. Wyjście palca poza viewport/szerokość panelu anuluje target. Opóźnione o 450 ms rozwinięcie porównuje bieżący klucz z aktualną geometrią i profilem; nie może rozwijać poprzedniej grupy po zmianie ułożenia. Efekt działa tylko gdy aktywny jest lokalny drag i kończy się z jego generacją; brak zmian w zapisie grup lub kart.
- **Testy:** `SidePanelHoverAutoscrollTest` sprawdza model B→C bez kolejnego MOVE, aktualny drop/hover, nieaktualny delayed target, przekroczenie granic viewportu/panelu i zmianę layoutu po rozwinięciu; `side-panel-autoscroll-hover-audit60.test.cjs` chroni rzeczywisty callsite Compose (`snapshotFlow`, `scrollBy`, delayed guard, `finishDrag`). Pokrycie logiki nie zastępuje integracyjnego długiego przytrzymania na urządzeniu.
- **CI:** `.github/workflows/zavro-audit60-stage12c-regression.yml` — pełne Kotlin JVM/Robolectric, kompilacja instrumented Kotlin (bez APK), WebExtension JS i Android lint. Dopóki nie ma wyniku `completed/success` na finalnym SHA: **CI_PENDING**, nie PASS.
- **Bezpieczeństwo:** profil/prywatność/sesje/Undo/Gecko storage i policy plików pozostawione nietknięte. Nie zmieniono release/versionCode, podpisu, preferencji szerokości, nie ma merge i APK.
- **Następne `Dalej`:** 12D/19 ZC-AUD-019 (VERIFY_FIRST) — własność Compose swipe kontra DnD, multipointer/CANCEL, bez domniemania naprawy bez reprodukcji. ZC-AUD-030 OPEN, ZC-AUD-031 PARTIAL.


### Etap 12C — wynik pełnej regresji produkcyjnej

- **PASS** — [GitHub Actions #41156770936](https://github.com/laszko14/nira-browser/actions/runs/41156770936), `completed/success` na kodowym SHA `3035fea7dfd0e6b66b0914b6188b0c572b61b95e`. Pełny Kotlin/JVM/Robolectric, `compileDebugAndroidTestKotlin` bez APK, WebExtension JS i Android lint zakończone `success`. Raport lint może nadal mieć ostrzeżenia.
- W `currentSideDrop` ograniczono hit-test do rzeczywistego dodatniego początku viewportu mimo ujemnego `viewportStartOffset` od top contentPadding. Ten sam resolver jest używany przez hover i finalny drop.
- ZC-AUD-010 oznaczono **FIXED CORE** w kodzie z regresją modelu/listy. Brak realnego gestu, Compose animation timing, automatycznego rozwinięcia i 120 Hz na Nothing Phone (1): **NOT_TESTED**, więc nie twierdzimy, że fizyczny przypadek jest potwierdzony na urządzeniu.
- Pozostałe w etapie 12: ZC-AUD-019 `VERIFY_FIRST`. ZC-AUD-030 `OPEN`, ZC-AUD-031 `PARTIAL`. Bez merge do main i bez APK.
- Ten dokumentacyjny commit również wymaga końcowego `completed/success` dla dokładnego SHA.

## Etap 12D/19 — ZC-AUD-019: własność swipe karty kontra Compose DnD (2026-10-31)

- **Baza:** etap 12C `ae31b6a5588ca80194bc5defd631e4d5e9a941bb`, [GitHub Actions #41157504579](https://github.com/laszko14/nira-browser/actions/runs/41157504579) completed/success. Gałąź `fix/zavro-clean-audit60-stage12d-compose-swipe-ownership-20261031`.
- **019 LIKELY → POTWIERDZONE źródłowo:** `SwipeableTabPill` zagnieżdżony w `draggableItem` ignorował `isConsumed`, czytał pierwszy indeks zmian i uruchamiał close/menu niezależnie od DnD. Bez urządzeniowego odtworzenia podwójnej akcji pozostaje LIKELY co do faktycznego skutku użytkowego.
- **Implementacja:** pojedynczy `PointerId` od DOWN do UP, odmowa przy drugim palcu, wymianie ID, zużytym zdarzeniu lub trwającym DnD; pionowy swipe przejmuje strumień dopiero po przewadze nad ruchem poziomym i Compose touchSlop. Odczyt Main/Final, callback tylko po legalnym release, pełne czyszczenie w `finally` także przy CANCEL/detach. Callsite rozwiniętej grupy przekazuje własność z `DragCoordinator`. Nie zmienia algorytmu DnD, tabel grup ani semantyki swipe up=close, down=menu.
- **Regresja:** `SwipePillGestureOwnerTest` — oba kierunki, próg, wyłączne ID, drugi palec, konsumowane zdarzenie, przejęcie DnD, anulowanie i świeży DOWN. `swipe-pill-gesture-ownership-audit60.test.cjs` zabezpiecza rzeczywiste spięcie Compose. Pełne Kotlin/JVM/Robolectric, `compileDebugAndroidTestKotlin` (bez APK), Node.js JS, lint przez `.github/workflows/zavro-audit60-stage12d-regression.yml`.
- **Granica dowodu:** realny Compose pointer dispatch Main/Final, long-press vs drag, dwa palce, Android 15/120 Hz i Gecko **NOT_TESTED** bez APK. Jeśli workflow przejdzie, oznaczyć FIXED CORE, nie „sprawdzone fizycznie”.
- **Ochrona:** nie ruszać danych/profili, prywatności, sesji, Gecko storage, podpisu, wersji ani APK. Bez merge do main. ZC-AUD-030 OPEN i ZC-AUD-031 PARTIAL.

### Etap 12D — checkpoint zielonego CI

- **PASS:** [GitHub Actions #41158925754](https://github.com/laszko14/nira-browser/actions/runs/41158925754) — `completed/success` na dokładnym produkcyjnym SHA `c1e04e13e2f213c45d56c5ebc1eea39d4b4704c1`. Pełny `:app:testDebugUnitTest` (Kotlin + Robolectric), `:app:compileDebugAndroidTestKotlin` (bez APK), komplet JavaScript i `:app:lintDebug` zakończyły się sukcesem. Lint może zawierać nieblokujące ostrzeżenia.
- Pierwszy workflow [#41158474031](https://github.com/laszko14/nira-browser/actions/runs/41158474031) zakończył się FAIL wyłącznie w dwóch starych asercjach JS, które wciąż oczekiwały progu i bezpośrednich callbacków w `SwipeableTabPill`. Po ekstrakcji kontrolera `SwipePillGestureOwner` te warunki są rozdzielone; zaktualizowano oba testy, zachowując ochronę gestów bez nakładki X. Produkcja Kotlin, JVM i kompilacja źródeł instrumentacyjnych w tym pierwszym przebiegu miały PASS.
- ZC-AUD-019 oznaczono **FIXED CORE** z granicą: testy własności zdarzeń i modelu/źródła potwierdzają zachowanie implementacji, ale rzeczywiste `PointerEventPass.Main/Final`, dwa palce i aktywne DnD w środowisku Compose na Nothing Phone (1) Android 15 120 Hz pozostają **NOT_TESTED**. Nie deklarować pełnej reprodukcji i potwierdzenia urządzeniowego.
- Końcowy dokumentacyjny commit wymaga własnego `completed/success` na dokładnym SHA, nie wolno przypisywać mu wyniku wcześniejszego commita. **Następny etap: 13/19** dopiero po zielonym CI tej gałęzi. ZC-AUD-030 OPEN i ZC-AUD-031 PARTIAL. Bez APK, merge do main i zmian podpisu.

## Etap 13A/19 — ZC-AUD-016: idempotentne zamykanie IME (2026-10-31)

- **Baza:** etap 12D `f8267d3ba2c480682e2606e39aadf7006893b6b0`; [CI #41159604037](https://github.com/laszko14/nira-browser/actions/runs/41159604037) completed/success na tym dokładnym SHA. Nowa gałąź `fix/zavro-clean-audit60-stage13a-search-ime-dismiss-20261031`.
- **016 LIKELY — potwierdzone źródłowo:** `SearchDialogFragment.hideDeviceKeyboard` wywoływało `InputMethodManager.toggleSoftInput(SHOW_IMPLICIT, 0)`, gdy `dialogHandledAction=false`. System Back już wcześniej wykonywał `view.hideKeyboard()` bez ustawienia tej flagi. Toggle może przywrócić ukrytą klawiaturę; faktyczny efekt w systemie Android 15 nadal **NOT_TESTED**.
- **Naprawa:** `onDismiss` najpierw wykonuje jawne `WindowInsetsControllerCompat.hide(Type.ime())` dla **okna zamykanego dialogu** (o ile akcja nie ma już właściciela w interaktorze), a dopiero potem wywołuje `super.onDismiss`. Dla braku okna awaryjnie `view?.hideKeyboard()`; nie ma już przełączenia/SHOW_IMPLICIT. Zachowano wcześniejsze zasady `dialogHandledAction`, Back, scrim, wysyłanie URL, clipboard/QR, historię oraz tryb prywatny.
- **Regresje:** `search-dialog-ime-dismiss-audit60.test.cjs` sprawdza faktyczne powiązanie w źródle: bez `toggleSoftInput`, właściciel okna, `hide(Type.ime())`, kolejność przed `super`, fallback oraz Back i akcje interaktora. Pełny Kotlin/JVM/Robolectric, kompilacja źródeł instrumentacyjnych **bez APK**, JS i Android lint przez `.github/workflows/zavro-audit60-stage13a-regression.yml`. Status przed zakończeniem CI: **CI_PENDING**, nie PASS.
- **Granica dowodu:** testy i kod nie potwierdzają rzeczywistego stanu IME, fokusu i animacji viewportu na Nothing Phone (1) Android 15 — wymagana osobna regresja urządzeniowa (Back, scrim, wybór sugestii, powrót do pola strony). Brak APK, merge, zmian profili/prywatności, danych sesji, storage Gecko, versionCode lub podpisu.
- **Następne `Dalej`:** po zielonym CI: **13B/19 ZC-AUD-018** (generacja i anulowanie animacji toolbaru WWW/Home), następnie 13C ZC-AUD-048 (priorytet panelu) i 13D ZC-AUD-049 (znak flingu). ZC-AUD-030 OPEN, 031 PARTIAL.

### Etap 13A — checkpoint po zielonym CI

- **PASS:** [GitHub Actions #41184917638](https://github.com/laszko14/nira-browser/actions/runs/41184917638) — `completed/success` na dokładnym kodowym SHA `39408a27e9f954b1196735cc91b1e5f6d0d9a31c`. Pełny Kotlin JVM/Robolectric, kompilacja instrumentacji Android bez assemble APK, Node.js JavaScript oraz Android lint zakończone sukcesem (lint może zawierać nieblokujące ostrzeżenia).
- Pierwszy workflow [#41184796747](https://github.com/laszko14/nira-browser/actions/runs/41184796747) anulowano po korekcie **testu**, który błędnie ograniczał wycinek do pierwszego `onBackPressed()` wewnętrznego `Dialog`, zamiast do fragmentowego override po `onDismiss`. Zmiany w kodzie produkcyjnym nie były wymagane.
- ZC-AUD-016: **FIXED CORE** w zakresie wyeliminowania `toggleSoftInput` i jawnego `hide(Type.ime())` przed super dla nadal żywego okna. Nie ma testu fizycznego realnego stanu IME/fokusu/resize na Nothing Phone (1) Android 15; manualne przypadki Back, scrim, wpisywanie w URL i powrót z input strony pozostają **NOT_TESTED**.
- Ten dokumentacyjny SHA również wymaga końcowego `completed/success`; wynik kodowej rewizji nie może być przypisywany automatycznie późniejszemu commitowi. Następne `Dalej`: **13B/19 — ZC-AUD-018**. ZC-AUD-030 OPEN, ZC-AUD-031 PARTIAL. Bez APK, merge i zmiany podpisu.

## Etap 13B/19 — ZC-AUD-018: animacje gestów toolbaru WWW i Home (2026-10-31)

- **Baza:** etap 13A `ee1325b7029f0e98ee9bd6966968f5a622b9af78`, [CI #41185523285](https://github.com/laszko14/nira-browser/actions/runs/41185523285) `completed/success`. Gałąź `fix/zavro-clean-audit60-stage13b-toolbar-gesture-animation-owner-20261031`.
- **018 CONFIRMED w kodzie:** oba handlery uruchamiały kolejne `ValueAnimator` bez unieważnienia starej generacji; `onAnimationEnd` wywoływane nawet po `cancel()` mogło wykonać `selectTabUseCase` lub ukryć nowy podgląd. Animator czytał zmienny `gestureDirection` zamiast kierunku konkretnego gestu.
- **Naprawa:** wspólny `ToolbarSwipeAnimationOwner` dla obu toolbarów; token generacji przy każdym zaakceptowanym nowym geście, unieważnienie **przed** anulowaniem starego `ValueAnimator` i animacji alpha podglądu; callback i aktualizacja klatek działają tylko dla właściciela i dołączonych widoków. Kierunek jest przechwytywany na czas animacji. `detach` widoku unieważnia właściciela i resetuje translacje. Anulowany lub stary `onAnimationEnd` nie może przełączyć karty, nawet jeśli nadal istnieje w `BrowserStore`; dotychczasowe `canFinishSwipe` nadal chroni profil/prywatność/source/target.
- **Regresje:** `ToolbarSwipeAnimationOwnerTest` na Robolectric z prawdziwym `ValueAnimator`: release→nowy gest→spóźniony callback, anulowany powrót, zastąpienie animacji w tej samej generacji i detach. Test JS `toolbar-swipe-animation-owner-audit60.test.cjs` sprawdza **oba rzeczywiste handlery** i ochronę klatek, callbacków, alpha i źródła. Pełny `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin` bez APK, wszystkie JS i `:app:lintDebug` w `.github/workflows/zavro-audit60-stage13b-regression.yml`; wynik na końcowym SHA obowiązkowy.
- **Nieruszone:** kierunek flingu (ZC-AUD-049 — do osobnego 13D), priorytet panel/toolbar (ZC-AUD-048 — 13C), klawiatura (13A), profil, sesje, prywatność i storage. Bez merge/main, versionCode, APK i podpisu.
- **Granica:** rzeczywisty fast swipe, obrót/fragment detach i 120 Hz na Nothing Phone (1) Android 15 nie były wykonane; wynik urządzeniowy **NOT_TESTED**. ZC-AUD-030 OPEN, ZC-AUD-031 PARTIAL.
- **Następne `Dalej`:** 13C/19 — ZC-AUD-048. Jeden `Dalej` = jeden podetap.

### Etap 13B — wynik pełnego CI

- **PASS:** [GitHub Actions #41186267358](https://github.com/laszko14/nira-browser/actions/runs/41186267358), `completed/success` na dokładnym kodowym SHA `730f041761c23648a865a3cf15540d0df5983c66`: `:app:testDebugUnitTest` (pełne JVM/Robolectric), `:app:compileDebugAndroidTestKotlin` (bez APK), komplet WebExtension JS i `:app:lintDebug` (job SUCCESS, nie dowodzi zera ostrzeżeń).
- Pierwszy kodowy SHA `a48a902e9512f9af2d385da6cc53ac9d52db72fe` zastąpiono drobnym importem `androidx.core.view.isVisible` w nowym teście; pierwsze CI zostało anulowane przez `concurrency.cancel-in-progress`. Na końcowym kodzie testy wszystkich kategorii zakończyły się SUCCESS.
- **ZC-AUD-018 FIXED CORE:** właściwości generacji i anulowania zweryfikowano regresją prawdziwego `ValueAnimator` w Robolectric oraz ochroną obu produkcyjnych callsite. Nie uruchomiono rzeczywistych szybkich gestów, obrotu i rysowania podglądu 120 Hz na Nothing Phone (1) Android 15: **NOT_TESTED**. Bez zgadywania wyniku testów fizycznych.
- Dokumentacyjny commit końcowy również wymaga `completed/success` na własnym SHA; nie przypisywać mu automatycznie wyniku kodowego commita. Następne `Dalej`: **13C/19 — ZC-AUD-048** (panel kontra hitbox toolbaru), następnie 13D/19 — ZC-AUD-049 (znak flingu). ZC-AUD-030 OPEN, ZC-AUD-031 PARTIAL. Bez APK, merge, wersjonowania i zmiany podpisu.

## Etap 13C/19 — ZC-AUD-048: panel ponad hitboxem toolbaru (2026-10-31)

- **Baza:** etap 13B `233f8284b7b5c0261fe829140c06d7d0ed1bc1fa`, [końcowe CI #41186656199](https://github.com/laszko14/nira-browser/actions/runs/41186656199) `completed/success` na dokładnym SHA. Gałąź `fix/zavro-clean-audit60-stage13c-toolbar-panel-ownership-20261031`.
- **048 CONFIRMED statycznie:** `BrowserFragment` dodaje `ToolbarGestureHandler` przed `SidePanelInfrastructure`. `SwipeGestureLayout` wybiera pierwszego akceptującego słuchacza. Geometryczny prostokąt paska pod nakładką panelu wygrywał nad zamykaniem otwartego lewego/prawego panelu.
- **Naprawa:** `BrowserFragment` przekazuje dynamiczne `canClaimToolbarSwipe`, oceniające `sidePanelInfrastructure?.anyPanelEngaged() != true` przy ofercie gestu, a nie przy konstrukcji. Toolbar natychmiast odmawia przejęcia, gdy lewy lub prawy panel jest zamontowany, otwarty, w trakcie przeciągania, wyhamowywania albo czeka na odmontowanie. Wtedy normalny słuchacz panelowy może przejąć gest zgodnie ze swoimi ograniczeniami (kierunek, lokalizacja, IME i priorytet DnD/resize). Po zamknięciu odzyskuje działanie toolbaru. Gdy panel wchodzi w stan engaged podczas aktywnego streamu lub końcowego `ValueAnimator`, toolbar unieważnia animację i nie może wybrać karty pod nakładką.
- **Regresja:** `SwipeGestureLayoutArbitrationTest` wysyła rzeczywiste `MotionEvent` do dispatchera z kolejnością toolbar→panel, testuje zamykanie lewej/prawej strony i odzyskanie toolbaru po zamknięciu; JS `toolbar-panel-ownership-audit60.test.cjs` dowodzi rzeczywistego spięcia BrowserFragment/handler/panele/dispatcher i ochrony animacji. Workflow `.github/workflows/zavro-audit60-stage13c-regression.yml`: pełny Kotlin JVM/Robolectric, kompilacja instrumentacyjna bez APK, JS i Android lint. Do ukończenia wymagane zielone CI na końcowym SHA.
- **Granica:** ręczny gest na realnym Compose panelu, przecięcia z DnD/resize, fullscreen, multi-touch, orientation i 120 Hz na Nothing Phone (1) Android 15 **NOT_TESTED**. Nie zmieniono geometrii/progów ani gestów systemowych, algorytmu paneli, profili, sesji, historii, trybu prywatnego, Gecko storage, podpisu lub wersji. ZC-AUD-030 OPEN, ZC-AUD-031 PARTIAL. Bez APK i merge do main.
- **Następne `Dalej`:** 13D/19 — ZC-AUD-049 (znaki flingu WWW/Home), dopiero po zielonym CI 13C.

### Etap 13C — checkpoint pełnego zielonego CI

- **PASS:** [GitHub Actions #41188585404](https://github.com/laszko14/nira-browser/actions/runs/41188585404) — `completed/success` na dokładnym **kodowym SHA `6bb4d8a6c02f36dce1d55637dc0afe8fa864615a`**. Pełne `:app:testDebugUnitTest` (Kotlin + Robolectric), `:app:compileDebugAndroidTestKotlin` (bez APK), wszystkie Node.js JS oraz `:app:lintDebug` zakończyły się pomyślnie (możliwe nieblokujące ostrzeżenia lint).
- [Pierwszy workflow #41188216437](https://github.com/laszko14/nira-browser/actions/runs/41188216437) zakończył się `FAIL` **wyłącznie na jednej przestarzałej asercji w `toolbar-swipe-animation-owner-audit60.test.cjs`**. Oczekiwała warunku `!gestureActive || !animationOwner.isCurrent`, a rzeczywisty `ToolbarGestureHandler` ma dodatkowo `!toolbarIsUncovered()`. Kotlin JVM/Robolectric i kompilacja instrumentacyjna w pierwszym przebiegu: PASS; Android lint został wtedy pominięty z powodu błędu JS. Zaktualizowano tylko test, zachowując osobne rygorystyczne asercje dla WWW i Home; kod produkcyjny nie wymagał poprawek.
- **ZC-AUD-048 FIXED CORE:** testy źródłowe potwierdzają, że panel zasłaniający toolbar zabiera mu własność gestu, a fizyczny dispatcher `SwipeGestureLayout` z dwoma słuchaczami działa w Robolectric. Ekran Nothing Phone (1), realny Compose hitbox, DnD, resize, dwa palce i działanie na 120 Hz pozostają **NOT_TESTED**. Nie deklarować urządzeniowego potwierdzenia.
- **Granica bezpieczeństwa:** brak zmian w profilach, sesjach, prywatności, Gecko storage, podpisie, wersji, APK i main. ZC-AUD-030 OPEN, ZC-AUD-031 PARTIAL. **Następne `Dalej`: 13D/19 — ZC-AUD-049 (znak flingu)**.
- Końcowy dokumentacyjny SHA również wymaga własnego `completed/success` w GitHub Actions; wynik wcześniejszego kodowego commita nie dowodzi statusu późniejszego SHA.

## Etap 13D/19 — ZC-AUD-049: zgodny fizyczny znak flingu WWW/Home (2026-10-31)

- **Baza:** 13C commit `2b971fe01d3c9ad3a6bd1dece25ba9b69238566a`, [CI #41189174430](https://github.com/laszko14/nira-browser/actions/runs/41189174430) completed/success. Gałąź `fix/zavro-clean-audit60-stage13d-toolbar-fling-parity-20261031`.
- **Ponowna weryfikacja:** na bazie 13C oba handlery miały już `RIGHT_TO_LEFT -> velocityX <= 0` i `LEFT_TO_RIGHT -> velocityX >= 0`; `SwipeGestureLayout.onFling` przekazuje niezmienione `velocityX`. Stara niezgodność znaków z v1190 jest **ALREADY_RESOLVED**. W Home `translationX` ma typ Float, a więc obliczenie udziału odsłonięcia nie używa dzielenia całkowitego. Żadnej z tych sytuacji nie przedstawiać jako nowo naprawionej usterki.
- **13D — zabezpieczenie:** jedna `ToolbarSwipeFinishPolicy` zastępuje dwie kopie reguł bez zmiany fizycznych znaków, progu 25%, minimum flingu, obracania gestu ani dotychczasowych obliczeń odsłonięcia. Bez zmian LRU, RTL wyboru celu, `canFinishSwipe`, scope profili/prywatności, animacji 13B i panelowej arbitraży 13C.
- **Regresje:** `ToolbarSwipeFinishPolicyTest` (oba kierunki, naturalny/reverse, prędkość poniżej/na/powyżej progu, ułamek 25%, zerowa prędkość, RTL) i `toolbar-fling-parity-audit60.test.cjs` (rzeczywiste dwa callsite i niezmienione `GestureDetector`, `canFinishSwipe`, LRU). Workflow `.github/workflows/zavro-audit60-stage13d-regression.yml`: pełne JVM/Robolectric, kompilacja testów instrumentacyjnych bez APK, JS oraz Android lint.
- **Status kodu:** CI PASS na produkcyjnym SHA `14637ea60ad3fc517b9a99fbe4d76f3ea3fea74c` (workflow #41190193286). Dokumentacyjny SHA po tej aktualizacji wymaga osobnego CI. Rzeczywisty fling Android 15 / 120 Hz, rotation, podgląd i rzeczywiste RTL **NOT_TESTED**. Bez APK, merge do main, zmian podpisu, wersji, danych, profili, sesji i Gecko storage. ZC-AUD-030 **OPEN**, ZC-AUD-031 **PARTIAL**. Po zielonym CI: następne `Dalej` → etap **14/19**.

### Etap 13D — checkpoint zielonego CI kodu

- **PASS:** [GitHub Actions #41190193286](https://github.com/laszko14/nira-browser/actions/runs/41190193286), `completed/success` dokładnie na SHA `14637ea60ad3fc517b9a99fbe4d76f3ea3fea74c`.
- Kroki `:app:testDebugUnitTest` (Kotlin/Robolectric), `:app:compileDebugAndroidTestKotlin` (bez assemble APK), `node --test app/src/test/js/*.test.cjs` i `:app:lintDebug` **SUCCESS**. Lint może zawierać ostrzeżenia nieblokujące.
- Pierwotne odwrócenie znaku flingu ze starego audytu v1190: **ALREADY_RESOLVED** na bazie 13C. Etap 13D utrwala identyczną politykę i jej testy w obu handlerach; nie deklarować nowej naprawy dawno usuniętej rozbieżności.
- Przebieg fizyczny Android 15 / Nothing Phone (1) / 120 Hz i RTL: **NOT_TESTED**. ZC-AUD-030 **OPEN**, ZC-AUD-031 **PARTIAL**. Bez APK, merge do main, zmian podpisu, danych profili i sesji.
- Końcowy commit dokumentacji musi mieć własny `completed/success`. Następne `Dalej`: etap **14/19**, po zielonym CI dokumentacji.

## Etap 14A/19 — ZC-AUD-021: Anuluj nie zapisuje wyszukiwarki (2026-10-31)

- **Baza:** etap 13D `e55aa1fc196c0c5bcbeee52a429af7f3e332422a`; [CI #41190777518](https://github.com/laszko14/nira-browser/actions/runs/41190777518) `completed/success`. Gałąź `fix/zavro-clean-audit60-stage14a-search-engine-dialog-transaction-20261031`.
- **Weryfikacja na aktualnym SHA:** nadal `STILL_PRESENT`: kliknięcie pojedynczej pozycji bez OK zapisywało `searchEngineChoice`, `customSearchEngine` lub `privateSearchEngineChoice`. Anulowanie edytora Custom zerowało `customSearchEngine` pomimo wcześniejszego wyboru.
- **Naprawa:** lokalny `SearchEngineSelectionDraft` dla normalnego/prywatnego selektora; na tap zmienia się wyłącznie draft. Jedyny zapis do `UserPreferences` selektora następuje przy pozytywnym OK, przed `SearchEnginePreferences.apply`. Cancel/Back/outside/dispose nie zmieniają utrwalonej konfiguracji. Custom ma osobny OK i walidację URL; jego Cancel niczego nie wyłącza. Wybór private inherit=-1 i dotychczasowe `clearPrivateSearchEngine` po zmianie normalnej wyszukiwarki pozostają.
- **Regresje:** `SearchEngineSelectionDraftTest` (anulowanie i ponowne otwarcie, normalny wybór, zachowanie Custom, prywatne dziedziczenie/indeks ostatniej wyszukiwarki) i `search-engine-dialog-transaction-audit60.test.cjs` (prawdziwe callbacki Android). Workflow `.github/workflows/zavro-audit60-stage14a-regression.yml` uruchamia pełne Kotlin/Robolectric, compile Android tests bez APK, komplet JS i lint.
- **Granice:** prawdziwe kliknięcia dialogu, działania Gecko/store i test realnego urządzenia Android 15/120 Hz **NOT_TESTED**. Nie modyfikowano profili, prywatnych sesji, danych, podpisu, versionCode, storage, APK ani main. ZC-AUD-030 **OPEN**, ZC-AUD-031 **PARTIAL**. W etapie 14 pozostają 006,012,014,015,020,047. Następne `Dalej` → **14B/19**, dopiero po CI końcowego SHA.

### Etap 14A — checkpoint zielonego CI kodu

- **PASS:** [GitHub Actions #41195358059](https://github.com/laszko14/nira-browser/actions/runs/41195358059) `completed/success` na dokładnym SHA kodowym `baa46aafbf256079ec97417612269faa5b081aba`. Pełny `:app:testDebugUnitTest` (Kotlin/Robolectric), `:app:compileDebugAndroidTestKotlin` (bez APK), `node --test app/src/test/js/*.test.cjs` i `:app:lintDebug` zakończyły się SUCCESS (lint może nadal mieć nieblokujące ostrzeżenia).
- **ZC-AUD-021 FIXED CORE:** regresja stanu roboczego i produkcyjnych callbacków, bez zapisów na wyborze/Anuluj, Custom Cancel bez mutacji. Android AlertDialog/Gecko, Back/dismiss, profil użytkownika i fizyczne urządzenie **NOT_TESTED**.
- Końcowy commit dokumentacji musi przejść własne CI. ZC-AUD-030 **OPEN**, ZC-AUD-031 **PARTIAL**. Bez APK/merge/main/podpisu.

## Etap 14B/19 — ZC-AUD-012: sortowanie zakładek wg URL (2026-10-31)

- **Baza:** 14A final `a772769aa26d23cb837a4557d1a6f486cde4128a`, [GitHub CI #41195961219](https://github.com/laszko14/nira-browser/actions/runs/41195961219) completed/success. Gałąź `fix/zavro-clean-audit60-stage14b-bookmark-sort-20261031`.
- **STILL_PRESENT na 14A:** `BookmarksBottomSheetFragment.showSortDialog` mapował nazwę, URL i datę na to samo `BookmarkSortType.A_Z`. `BookmarkSiteItem` ma `url`, lecz w bazowym schemacie nie ma daty dodania; także foldery nie mają takiej daty. Nie wolno zgadywać z ID lub kolejności importu.
- **Naprawa:** menu eksponuje jedynie prawdziwe kryteria nazwa/URL (opcja daty ukryta do oddzielnej migracji modelu). `BookmarkUrlSort` trzyma foldery jako pierwsze, sortowane po nazwie; witryny po URL, remisy po tytule/ID, Locale.ROOT; brak URL nie powoduje crash. Oba wybory sortują rzeczywistą listę aktualnego folderu, a `BookmarkManager.save()` (AtomicFile) zachowuje kolejność po ponownym otwarciu. Nieudany zapis cofa in-memory kolejność.
- **Regresje:** `BookmarkUrlSortTest` (nazwy kontra URL, foldery, null tytułu, brak URL, remisy i idempotencja); `bookmark-sort-audit60.test.cjs` kontroluje podłączenie menu, atomowy zapis/rollback i brak pozorowanej daty. CI `.github/workflows/zavro-audit60-stage14b-regression.yml`: pełny Kotlin/Robolectric, kompilacja AndroidTest bez APK, wszystkie JS oraz Android lint.
- **Granice:** interaktywny panel Compose, orientacje i realny Android 15/120 Hz NOT_TESTED. Brak zmian w profilach, prywatności, sesjach, Gecko storage, podpisie, versionCode, APK i main. ZC-AUD-030 OPEN, ZC-AUD-031 PARTIAL. Pozostałe w 14/19: 006,014,015,020,047. Następne `Dalej` → 14C/19 po zielonym CI na końcowym SHA.

### Etap 14B — checkpoint zielonego CI kodu

- **PASS:** [GitHub Actions #41196755486](https://github.com/laszko14/nira-browser/actions/runs/41196755486), `completed/success` na dokładnym produkcyjnym SHA `3994c3d54ae1f7445106a3bd07d27dbe761042aa`. `:app:testDebugUnitTest` (Kotlin/Robolectric), `:app:compileDebugAndroidTestKotlin` bez APK, komplet `node --test app/src/test/js/*.test.cjs` i `:app:lintDebug` — SUCCESS. Lint job nie dowodzi zera ostrzeżeń.
- **ZC-AUD-012 FIXED CORE:** rzeczywisty URL i nazwa mają osobne operacje; daty nie da się poprawnie sortować bez metadanych i migracji, dlatego niedziałającą opcję wycofano. Sort zapisuje trwałą kolejność przez AtomicFile i przy błędzie przywraca stary stan w RAM; regresje objęły obie ścieżki i model folderów.
- Test fizycznego panelu, rotacji, gestów i długiej listy na Nothing Phone (1) Android 15/120 Hz **NOT_TESTED**. ZC-AUD-030 **OPEN**, ZC-AUD-031 **PARTIAL**. Bez APK/merge/main/podpisu. Końcowy commit dokumentacji wymaga własnego `completed/success`. Następne `Dalej` → 14C/19.

## Etap 14C/19 — ZC-AUD-014: dokładne pozycjonowanie DnD przycisków (2026-10-31)

- **Baza:** 14B final `b3e0dfc701ef0884751f2e8bb127da5dc11d1790`, CI [#41197363222](https://github.com/laszko14/nira-browser/actions/runs/41197363222) `completed/success`. Gałąź `fix/zavro-clean-audit60-stage14c-toolbar-drop-boundary-20261031`.
- **STILL_PRESENT na 14B:** `CustomizationSettingsFragment.showToolbarActionDialog` zawsze wykonywał `remove(moved)` i `add(indexOf(target), moved)` — wszystkie dropy działały jak BEFORE. `A` na dolną połowę `C` w `[A,B,C]` dawało `[B,A,C]` zamiast `[B,C,A]`, a bezpośrednie przesunięcie o jedną pozycję w dół nie działało.
- **Naprawa:** `ToolbarActionDropPosition.isAfter(y, height)` wybiera BEFORE/AFTER według połowy wiersza; `move` liczy indeks celu po usunięciu źródła, dzięki czemu dolna połowa ostatniego wiersza oznacza pozycję końcową. Self-drop, niepoprawne ID i niezmieniona pozycja są no-op bez zapisu. Dialog podaje użytkownikowi instrukcję granic. Zachowano identyczne `storeOrder(prefs, landscape, ...)`, natychmiastowy zapis po zmianie, edycję widoczności i rozmiaru oraz orientacyjne ustawienia pion/poziom.
- **Regresje:** `ToolbarActionDropPositionTest` sprawdza direct-end, sąsiada, ruch w górę, granicę połowy, invalid/self/no-op. `toolbar-drop-boundary-audit60.test.cjs` pilnuje rzeczywistego podłączenia do `ACTION_DROP` i `storeOrder` z orientacją. Workflow `.github/workflows/zavro-audit60-stage14c-regression.yml` uruchamia pełny Kotlin/Robolectric, kompilację AndroidTest (bez APK), cały JS oraz Android lint na SHA końcowym.
- **Checkpoint:** kod `0564bbbf8da35ba0ff27b86f0db7cd5205fb3aef`; pełny końcowy CI: **PENDING** do sprawdzenia po commicie dokumentacji. Nie zgłaszać PASS dopóki wynik nie ma `completed/success` na końcowym SHA. Przeciąganie palcem, rotacja i Nothing Phone (1) Android 15/120 Hz **NOT_TESTED**. Bez APK, merge do main, zmian podpisu, profili, prywatności, sesji i Gecko storage. ZC-AUD-030 **OPEN**, ZC-AUD-031 **PARTIAL**. Pozostałe etap 14: **006,015,020,047**. Następne `Dalej` → **14D/19** po zielonym CI końcowego commitu.

## Etap 14D/19 — ZC-AUD-015: odświeżanie personalizacji po zmianie orientacji (2026-10-31)

- **Baza:** 14C final `567208cfd8c0a2fa08d07e38ea5303db64f3a41b`, [CI #41203766433](https://github.com/laszko14/nira-browser/actions/runs/41203766433) `completed/success`. Gałąź `fix/zavro-clean-audit60-stage14d-toolbar-orientation-20261031`.
- **STILL_PRESENT:** `UnifiedToolbar.setupToolbarButtons` w handlerze `addOnLayoutChangeListener` śledził tylko szerokość. Przy przejściu portrait ↔ landscape po zmianie samej wysokości, bez zmian preferencji i szerokości, pozycje/ukrycia/rozmiary akcji mogły pozostać z poprzedniej orientacji.
- **Naprawa:** `UnifiedToolbar.onConfigurationChanged` jawnie uruchamia ponowne zastosowanie ustawień po zdarzeniu Androida; callback używa `actions.post` i zabezpieczenia `isAttachedToWindow`. Listener layoutu dodatkowo porównuje orientację w `ToolbarActionLayoutRefreshPolicy` oraz dotychczasowe warunki szerokości, także jeśli layout przebiegnie bez osobnego odświeżenia. `applyActionPreferences` zapamiętuje faktyczną orientację i nadal stosuje istniejące `getOrder/getHidden/getSize(prefs, horizontal)` i przelicza overflow. Callback jest usuwany podczas `onDestroy` hosta.
- **Regresje:** `ToolbarActionLayoutRefreshPolicyTest` — stała szerokość + obie zmiany orientacji, zmiana szerokości, start i niezmieniony layout. `toolbar-orientation-audit60.test.cjs` — powiązanie realnych callbacków Android + bezpośrednie użycie konfiguracji pion/poziom. CI `.github/workflows/zavro-audit60-stage14d-regression.yml`: Kotlin/Robolectric, compile AndroidTest bez APK, komplet Node testów, lint. Wynik sprawdzać na końcowym SHA w [GitHub Actions](https://github.com/laszko14/nira-browser/actions/workflows/zavro-audit60-stage14d-regression.yml).
- **Granice:** test fizyczny resize/multi-window/obrót i 120 Hz na Nothing Phone (1) Android 15: **NOT_TESTED**. ZC-AUD-030 **OPEN**, ZC-AUD-031 **PARTIAL**. Bez APK, merge do main, zmian podpisu, danych profili/sesji i Gecko storage. Pozostałe w 14/19: **006,020,047**. Następne `Dalej` → **14E/19**, po zielonym CI końcowego SHA.

## Etap 14E/19 — ZC-AUD-047: nieważne callbacki po ponownym fullscreen (2026-10-31)

- **Baza:** 14D final `3c02a73396352cf408eaecdff03a82118dc92d7d`, [CI #41207787639](https://github.com/laszko14/nira-browser/actions/runs/41207787639) `completed/success`. Gałąź `fix/zavro-clean-audit60-stage14e-fullscreen-restore-20261031`.
- **STILL_PRESENT:** exit→enter fullscreen mógł pozostawić ze starego exit oczekujące `View.post` z `expand/reconcileEngineViewHeight`, a odroczona rezerwacja w `BaseBrowserFragment.initializeEngineView` nie sprawdzała już bieżącego trybu. Mogło to przywrócić marginesy ukrytego chrome.
- **Naprawa:** `FullscreenChromeRestoreGate` numeruje zdarzenia enter/exit i generacje view. `BrowserFragment.collapseBrowserViewFromFullscreen` odtwarza chrome tylko przy aktualnym tokenie, niezmienionym widoku i podłączonym toolbarze; `onDestroyView` unieważnia oczekujące prace. Offset listener nie zapisuje rezerwacji w fullscreen, zeruje stare marginesy i translationY. Bazowy `initializeEngineView` waliduje generację fullscreen oraz identyczny binding/toolbar przed odroczonym marginesem. Obrót nie wywoła reconcile ukrytego chrome.
- **Regresje:** `FullscreenChromeRestoreGateTest` (exit→enter, normalny exit, wiele przejść, destroy/recreate, duplikat zdarzenia); `fullscreen-chrome-audit60.test.cjs` kontroluje rzeczywiste integracje. Workflow `.github/workflows/zavro-audit60-stage14e-regression.yml` uruchamia komplet Kotlin/Robolectric, compile AndroidTest (bez APK), komplet JavaScript i lint. Status CI końcowego SHA jest warunkiem ukończenia.
- **Granice:** pełny Gecko fullscreen/PiP, obrót, TOP/BOTTOM i Android 15 / 120 Hz **NOT_TESTED**. ZC-AUD-020 (konkurencyjny zapis IME) nie został naprawiony. ZC-AUD-030 **OPEN**, ZC-AUD-031 **PARTIAL**. Brak APK, merge do main, zmian podpisu, profili, prywatnych sesji i trwałych danych. Pozostałe w 14/19: **006,020**. Następne `Dalej` → **14F/19** po zielonym CI.

## Etap 14F/19 — ZC-AUD-020: współdzielona rezerwacja viewportu dla IME/chrome (2026-10-31)

- **Baza:** 14E final `713f44de6c34989b7af391c85576cc418d604838`, [CI #41209204871](https://github.com/laszko14/nira-browser/actions/runs/41209204871) `completed/success`. Gałąź `fix/zavro-clean-audit60-stage14f-ime-chrome-viewport-20261031`.
- **STILL_PRESENT:** `WebContentPositionManager.handleWindowInsets` nadpisywał `swipeRefresh.bottomMargin` na wysokość IME, a po ukryciu na `0`, nawet gdy `BrowserFragment.adjustWebContentMarginsForToolbarOffset` rezerwował widoczny dolny toolbar/tab strip. W odwrotnej kolejności callback chrome nadpisywał otwarty IME; bazowy `initializeEngineView` również nadpisywał oba marginesy.
- **Naprawa:** `WebViewportReservation` utrzymuje oddzielnie bieżące wartości chrome TOP, chrome BOTTOM, IME minus już zarezerwowany inset nawigacji, oraz fullscreen. `WebContentPositionManager` jest jedynym writerem marginesów i stosuje `top=chromeTop`, `bottom=max(chromeBottom, ime)`, w fullscreen oba `0`. `BrowserFragment` publikuje wymierzone wysokości pasków zamiast bezpośrednich zapisów; `BaseBrowserFragment.initializeEngineView` także publikuje do managera, a fullScreenChanged przekazuje stan fullscreen. Zachowano bezpośrednią transformację Y w trakcie animacji bez relayout co klatkę i dotychczasową rezerwację systemowych pasków przez Activity. Wysokości nie są sumowane podwójnie.
- **Regresje:** `WebViewportReservationTest` sprawdza TOP/BOTTOM, kolejność IME→toolbar i toolbar→IME, ukrycie IME, zmiany toolbaru podczas aktywnego IME, przejście fullscreen/exit, wartości ujemne i idempotencję. `viewport-ime-chrome-audit60.test.cjs` sprawdza rzeczywiste wywołania oraz brak bezpośrednich writerów w trzech ścieżkach. Workflow `.github/workflows/zavro-audit60-stage14f-regression.yml`: pełne Kotlin/Robolectric, compile AndroidTest bez APK, JS i Android lint. CI końcowego SHA wymagane przed uznaniem zakończenia.
- **Granice:** rzeczywisty układ Android 15 z 3-przyciskową nawigacją, gestami, IME animowanym, TOP/BOTTOM, niezależnym paskiem kart, Gecko/fullscreen i 120 Hz **NOT_TESTED**. `ZC-AUD-006` pozostaje do etapu 14G. `ZC-AUD-030` **OPEN**, `ZC-AUD-031` **PARTIAL**. Bez APK, merge do main, zmian podpisu, danych profili, sesji i Gecko storage. Następne `Dalej` → **14G/19** po zielonym CI.

### Etap 14F — korekta testów zgodności po zmianie właściciela marginesów

- Pierwsze CI [#41210410925](https://github.com/laszko14/nira-browser/actions/runs/41210410925) na SHA `ba4a84001d14b0cba573343694a6ff6ff315912f`: Kotlin/Robolectric PASS, AndroidTest compile PASS, JavaScript FAIL (330/333 PASS, 3 nieaktualne asercje), lint SKIPPED. Asercje z `v1120` i `v1169` oraz `fullscreen-chrome-audit60` dotyczyły konkretnych starych instrukcji zapisu marginesów. Zaktualizowane do sprawdzania zachowanych niezmienników: brak podwójnego paddingu Gecko, finalny settle `translationY` + publikacja stanu do ownera oraz fullscreen = zerowa rezerwacja w polityce. Implementacji produkcyjnej nie cofnięto; dodatkowe testy `WebViewportReservationTest` i `viewport-ime-chrome-audit60` pozostają. Końcowe CI nowego SHA jest wymagane przed uznaniem PASS.

## Etap 14G/19 — ZC-AUD-006: działające opcje personalizacji (2026-10-31)

- **Baza:** 14F final `0cf64da3f0175dd3733a2f5a91865de3d3d981ef`, [GitHub Actions #41210945759](https://github.com/laszko14/nira-browser/actions/runs/41210945759) `completed/success`. Gałąź `fix/zavro-clean-audit60-stage14g-settings-contract-20261031`.
- **A — pasek kart/profili:** usunięto bezwarunkowe `showTabGroupBar = true` z `BrowserFragment.initializeUI`, więc zapis ustawień/onboardingu nie jest nadpisywany przed odczytem przez `UnifiedToolbar`. Panel boczny kart pozostaje dostępny. Opis przełącznika zaktualizowano.
- **B — siatka kart:** `TabGridViewPreference` ujednolica ustawienie personalizacji i aktywnego Compose menedżera kart. Kanoniczne `tabs_view_prefs/is_grid_view` ma priorytet; przy jego braku migrowana jest istniejąca jawna wartość `scw_preferences/show_tabs_in_grid`; bez obu domyślnie lista. Zapis synchronizuje oba klucze, także po kliknięciu w dialogu. Panel boczny świadomie pozostaje kompaktową listą, opis w ustawieniach to wyjaśnia.
- **C — zamiana paneli:** usunięto z XML przełącznik `swap_drawers`, który nie miał żadnego odbiorcy w aktualnym interfejsie. Fizyczne lewo=karty, prawo=zakładki, gesty i szerokości pozostają bez zmian. Dawne dane nie są czyszczone.
- **D — skala tekstu interfejsu:** usunięto nieskuteczną pozycję `interface_font_scale` i powiązany dialog, zachowując getter oraz stare wartości w `UserPreferences`. Nie wprowadzono częściowej skali, która psułaby spójność Android View i Compose; web/systemowa skala bez zmian.
- **Regresje:** `TabGridViewPreferenceTest`: konflikt starego i nowego klucza, migracja, default listy, zapis obu UI; `customization-contract-audit60.test.cjs`: rzeczywiste powiązania z UI, brak wymuszenia paska, brak nieskutecznych kontrolek i zachowanie dawnych preferencji. Workflow `.github/workflows/zavro-audit60-stage14g-regression.yml`: testy Kotlin/Robolectric, AndroidTest compile bez APK, komplet JS i lint. CI uznać za zielone wyłącznie na końcowym SHA.
- **Granice:** test UI na fizycznym Nothing Phone (1) Android 15 / 120 Hz, obrót, onboarding, restart i panel boczny **NOT_TESTED**. ZC-AUD-030 **OPEN**, ZC-AUD-031 **PARTIAL**. Bez APK, merge do main, zmian podpisu, profili/sesji i Gecko storage. Następne `Dalej` → **15/19** po zielonym CI.

### Etap 14G — poprawka kompilacji testów po CI

- Pierwsze CI [#41212667758](https://github.com/laszko14/nira-browser/actions/runs/41212667758) na SHA `a49600e9a9f178e79af209981c03605d82d393f6`: **FAIL** w `:app:compileDebugUnitTestKotlin` z powodu niedostępnego `androidx.test.core.app.ApplicationProvider` w nowym teście. Kompilacja głównego kodu Kotlin przeszła. Test używa teraz `org.robolectric.RuntimeEnvironment.getApplication()`, zgodnie z dostępnym Robolectric, bez nowych zależności; pełny CI końcowego commitu pozostaje wymagany.

### Etap 14G — konfiguracja SDK Robolectric

- CI [#41213082415](https://github.com/laszko14/nira-browser/actions/runs/41213082415) na SHA `26c03af0b4ca7b63df34c7d66e4df055b2942636`: kompilacja Kotlin PASS, ale `TabGridViewPreferenceTest.initializationError` w Robolectric (`DefaultSdkPicker`), 1313 testów wykonanych, 1 błąd. Nowy test używa teraz tego samego jawnego `@Config(sdk=[35], application=Application::class, manifest=Config.NONE)` co istniejące funkcjonalne testy paneli; bez zmian kodu produkcyjnego. Kolejny CI na nowym SHA wymagany.

## Etap 15A/19 — BFCache script i dynamiczne source (2026-10-31)

- Baza: 14G `ab999a5e1e0b6a01fbd9a42071fb7ab74c612515`, [CI 41213583034](https://github.com/laszko14/nira-browser/actions/runs/41213583034) SUCCESS. Gałąź `fix/zavro-clean-audit60-stage15a-script-source-20261031`.
- **ZC-AUD-032 STILL_PRESENT → FIXED CORE:** `scriptVersions` nie było resetowane w `pagehide`. Przy BFCache niezmieniony obiekt script pozostawał przetworzony mimo nowej rejestracji dokumentu. Teraz cache resetuje się po pagehide; jedna aktywna generacja nadal nie duplikuje wyników. Regresja Node: script JSON, hide→show(persisted), nowy documentKey i brak duplikatów w zwykłym scan.
- **ZC-AUD-033 STILL_PRESENT → FIXED CORE:** childList na `HTMLVideoElement` kolejkuje samego właściciela, więc dynamiczne `source` (MP4/HLS) zostaje wykryte również przy `paused`, bez Play lub pełnego rescanu. Koalescencja, limity skanera i walidacja native pozostają.
- ZC-AUD-002 **SUSPECTED / VERIFY_FIRST**: restart background Gecko przy zachowanej EngineSession wymaga urządzenia; nie deklarować naprawy. ZC-AUD-034/035/051: **DO_RECHECK** w kolejnych podetapach etapu 15.
- Weryfikacja: nowy workflow `zavro-audit60-stage15a-regression.yml` (pełne Kotlin/Robolectric, instrumented-test source compile, wszystkie JS, lint). **CI_PENDING** do potwierdzenia finalnego SHA. Gecko BFCache, MutationObserver real DOM, Android 15/120 Hz: **NOT_TESTED**. Bez APK, merge, zmiany signing, profili ani sesji. ZC-AUD-030 **OPEN**, ZC-AUD-031 **PARTIAL**.
- Kolejne `Dalej`: **15B/19 — ZC-AUD-034** late open Shadow DOM.

## Etap 15B/19 — late open Shadow DOM (2026-10-31)

- Baza: 15A `10072f2972b18e1c33aa93fc462a07b8952eba05`, [CI 41216927633](https://github.com/laszko14/nira-browser/actions/runs/41216927633) `completed/success`. Nowa gałąź `fix/zavro-clean-audit60-stage15b-shadow-open-20261031`.
- **ZC-AUD-034 STILL_PRESENT → FIXED CORE:** dotychczasowy `MutationObserver(document)` nie informował o `attachShadow({mode:"open"})` na już obecnym hoście. Dodano minimalny `open-shadow-hook.js` w MAIN-world MV2 `document_start` dla wszystkich ramek; przechwytuje natywny `attachShadow`, zachowuje zwrot i wyjątki, emituje wyłącznie DOM event bez URL/tokenów i bez WebExtension API. Isolated `content.js` nie ufa wiadomości strony: sam odczytuje `host.shadowRoot`, wymaga rzeczywistego `mode=open` i zgodnego `root.host`, obserwuje najwyżej raz przez `WeakSet` oraz kolejkuje poddrzewo do istniejącego `MAX_ROOTS/MAX_JOBS` bez globalnego pollingu lub odtwarzania wideo. Zdarzenie composed obsługuje nested open roots. Zamknięte roots nie są odsłaniane.
- Regresje JS: porządek injection i wszystkie ramki, kontrakt attachShadow zwrot/wyjątek/open/closed/ponowne załadowanie, późny root z paused video wykryty bez pełnego skanu, deduplikacja observer, ignorowanie sfałszowanych, zamkniętych i odłączonych hostów.
- Granice: MAIN-world może być nadpisany przez stronę, a GeckoView na fizycznym Androidzie nie było testowane; brak gwarancji dla stron, które nadpisują `attachShadow` po hooku lub blokują modyfikację prototypu. Sprawdzić w realnym Gecko z late/nested shadow roots i BFCache (strona bez Play). Nie deklarować PASS dla urządzenia. `world=MAIN` w Firefox MV2 jest wspierane od 128 (źródło: Mozilla addons 2024-07-10).
- Testy automatyczne: Kotlin/Robolectric, AndroidTest source compile, wszystkie Node JS i lint w `zavro-audit60-stage15b-regression.yml`. **CI_PENDING** do zakończenia finalnego SHA. Bez APK, main, zmiany signing, profili, prywatności i sesji. ZC-AUD-030 **OPEN**, ZC-AUD-031 **PARTIAL**.
- Następne `Dalej`: **15C/19 — ZC-AUD-035** (200→403→200 odzyskanie dostępu).

## Etap 15C/19 — odzyskiwanie dostępu do medium po błędzie odpowiedzi (2026-10-31)

- **Baza:** 15B `ed1177eab79714b806a4fd2b8d351f771709ff62` — [CI #41218082667](https://github.com/laszko14/nira-browser/actions/runs/41218082667) `completed/success`. Gałąź `fix/zavro-clean-audit60-stage15c-network-recovery-20261031`.
- **ZC-AUD-035 STILL_PRESENT → FIXED CORE:** `background.js:onHeaders` używał odrębnych wpisów deduplikacji dla status/MIME. Przy szybkim `200 → 403 → 200` ostatnie 200 trafiało na zapamiętany sukces z pierwszego żądania i znikało. Nowy, ograniczony do 128 wpisów cache trzyma **ostatni stan** dla dokładnego URL, documentKey, frameKey, tabId i privacy. Równe kolejno stany nadal są tłumione do 20 s; przejście przez 401/403/404/410 resetuje porównanie. Podpisy URL pozostają nietknięte; starsza `requestSequence` nie cofa nowszego stanu, także po dedupie. Limity 96 routów/5 s, sieć i strefy prywatne pozostają.
- **Regresja Node:** 200→401/403/404/410→200; brak późniejszych powtórzeń tego samego stanu; zmiana Content-Type i Content-Length; starszy response callback po nowszym; ramki i privacy. **Regresja Kotlin:** ten sam rekord/URL w native przechodzi AUTH_ERROR/EXPIRED→NEEDS_VERIFICATION przy rosnącym requestSequence i ignoruje opóźnioną odpowiedź starszą.
- **Granice:** brak rzeczywistych odpowiedzi GeckoView i 120 Hz na telefonie; `manifest_observed` oznacza potrzebę weryfikacji, nie potwierdzenie odtwarzalności. Brak aktywnych pobrań i zmian danych profili. ZC-AUD-030 **OPEN**, ZC-AUD-031 **PARTIAL**.
- **Weryfikacja:** pełne Kotlin/Robolectric, AndroidTest compile, WebExtension Node, Android lint na finalnym SHA w `zavro-audit60-stage15c-regression.yml` — `CI_PENDING` do zakończenia workflow. Bez APK, merge do main i zmian podpisu.
- **Następne „Dalej”**: **15D/19 — ZC-AUD-051** (hash-route → nowy film i invalidacja poprzedniego stanu).

## Etap 15D/19 — hash-router: stary film po zmianie trasy (2026-10-31)

- Baza: commit 15C `cdc963befac82d402442f51009a7dce84eee25d7`, [CI #41219650006](https://github.com/laszko14/nira-browser/actions/runs/41219650006) `completed/success`. Gałąź `fix/zavro-clean-audit60-stage15d-hash-route-20261031`.
- **ZC-AUD-051 STILL_PRESENT → FIXED CORE:** `background.sameRoute` wcześniej ignorował każde `#`; dla `#/film/a → #/film/b` oddawał stary `documentKey`. Natywne `MediaRepository.observe` uznawało wszystkie fragmenty za kotwicę, więc w FAB zostawał poprzedni film. Teraz spójna polityka materiałowej trasy rozpoznaje `#/...`, `#!/...`, `#film/...`, `#video/...`, `#movie/...`, `#watch/...`, `#episode/...`, `#player/...` (także wariant `?`), zachowując zwykłe kotwice `#intro`, `#comments` oraz drugą kotwicę w tej samej trasie `#/film/a#chapter2`. Zmiana filmu mintuje nowy klucz dokumentu, a `observe` natychmiast usuwa stare wpisy, unieważnia rejestrację i odrzuca stary klucz przy rejestracji.
- Zabezpieczenie przed wyścigiem: `content.js` po zmianie materiałowej trasy nie skanuje jeszcze podłączonego wideo poprzedniej trasy, aż observer zaobserwuje mutację elementu związanego z mediami (wymiana/usunięcie wideo lub `source`, zmiana `src`, konfiguracja script). Brak odtwarzania wideo i stałego globalnego pollingu. Nowe kandydaty są przyjmowane dopiero w bieżącym dokumencie. Zwykłe kotwice zachowują stare wpisy.
- **Regresje JS:** nowy klucz na A→B→A przy tym samym Gecko UUID; query + hash, zwykłe kotwice, kotwica w obrębie tego samego filmu; stary wciąż podłączony DOM video nie wraca przed wymianą, nowy odtwarzacz wykryty bez Play. **Kotlin:** natychmiastowe czyszczenie, ochrona innych kart, high-water documentKey, odrzucanie starych zdarzeń i kotwice bez czyszczenia.
- **Granice:** nie można bez znajomości danej witryny odróżnić *wszystkich* niestandardowych hash-routerów od kotwic; niesklasyfikowane hashe wymagają testu realnej strony. Gdy strona zmienia film bez obserwowalnej mutacji mediów (np. wyłącznie JS `srcObject`) wymagana osobna weryfikacja. Rzeczywisty GeckoView, Store, FAB, 120 Hz i BFCache na telefonie NOT_TESTED. Niezmienione URL podpisane, profile, tryb prywatny, sesje, signing i dane; ZC-AUD-030 OPEN, 031 PARTIAL.
- **CI:** Kotlin/Robolectric, AndroidTest compile, wszystkie JS, Android lint na finalnym SHA poprzez `zavro-audit60-stage15d-regression.yml` — CI_PENDING do zakończenia. Bez APK i merge.
- **Następne „Dalej”: 15E/19 — ZC-AUD-002 VERIFY_FIRST** (restart generacji WebExtension/Gecko bez zgadywania regresji).

## Etap 15E/19 — ZC-AUD-002: VERIFY_FIRST, reset środowiska WebExtension (2026-10-31)

- **Baza:** 15D final `37bc321777bc26fc755364e0be58bfbfb3f17754`, [CI #41222110726](https://github.com/laszko14/nira-browser/actions/runs/41222110726) `completed/success`. Oddzielna gałąź `fix/zavro-clean-audit60-stage15e-extension-restart-verify-20261031`.
- **Wynik weryfikacji kodu:** background `nextDocumentKey=0` jest lokalny dla instancji JS; `startDocument` nadaje `++nextDocumentKey`, bez identyfikatora instancji rozszerzenia. Native `MediaRepository.registerDocument` trzyma najwyższy zaakceptowany klucz dla tej samej `EngineSession` także po nawigacji i odrzuca mniejsze wartości. `media_document` (schemaVersion 1) nie ma pola restart/epoch. Zamknięty schemat został zachowany.
- **Deterministyczna symulacja warunku:** test Node uruchamia dwa niezależne VM `background.js`, po 22 kluczach pierwszej instancji druga przydziela ponownie `documentKey=1`. Test Kotlin/Robolectric zachowuje tożsamość natywnej sesji i `documentKey=20`: rejestracja klucza 1 jest odrzucona bez utraty obecnych danych; niezależna prywatna sesja akceptuje własny klucz 1, a nowszy klucz 21 usuwa starsze media. To dowodzi **warunkowej kolizji protokołu**, nie restartu Gecko ani działania FAB na telefonie.
- **Decyzja ZC-AUD-002: BLOCKED / SUSPECTED — REAL_GECKO_REQUIRED.** Brakuje warunku wstępnego: pomiaru, czy GeckoView podczas rzeczywistej reinstancjacji background zachowuje `EngineSession` oraz natywne scope. Nie wdrażano spekulatywnego handshake ani resetu high-water; nie pozwolono stronie mintować kluczy, nie osłabiono guardów.
- **Plan próby na urządzeniu bez danych użytkownika:** w izolowanym profilu testowym z kartą HTTP(S) zarejestrować >=20 dokumentów i zanotować aktualną tożsamość `EngineSession` oraz odpowiedzi `media_document/document_ready`; restartować wyłącznie środowisko background rozszerzenia przez kontrolowane środowisko GeckoView (nie proces aplikacji), pozostawiając tę samą kartę; zarejestrować nowy DOM media i sprawdzić akceptację native, elementy FAB, identyfikator sesji i brak mieszania trybu prywatnego. Kontrole: zwykła nawigacja bez restartu oraz restart całego procesu/nowa `EngineSession`. Dowód musi zawierać wersję Gecko i SHA programu; dane URL/tokeny redagować. Bez realnego testu nie wolno zmieniać statusu na FIXED/PASS.
- **CI test-only:** `.github/workflows/zavro-audit60-stage15e-regression.yml` wykonuje pełne Kotlin/Robolectric, AndroidTest compile, wszystkie JS i lint bez APK. Zielony CI potwierdza wyłącznie kod symulacji. Final SHA i CI podać w raporcie po `completed/success`.
- **Granice:** brak zmian produkcyjnych, danych profili, sesji, podpisu, APK i merge. `ZC-AUD-030 OPEN`, `ZC-AUD-031 PARTIAL`. Etap 15E pozostaje zablokowany w zakresie realnego Gecko; dalsze etapy 16–19 nie mogą traktować 002 jako naprawionego.

## Etap 16A/19 — ZC-AUD-037: niezależność grupowania wariantów od kolejności (2026-10-31)

- **Baza:** etap 15E test-only `2c57e0ac166686e52dab42093706cd37f6d179ac`, [CI #41223496850](https://github.com/laszko14/nira-browser/actions/runs/41223496850) `completed/success`. Gałąź `fix/zavro-clean-audit60-stage16a-variant-group-order-20261031`.
- **STILL_PRESENT:** `MediaRepository.add` rozpoznawał współdzielony master przez ten sam dokładny URL i parserowy token `manifest-...`, ale przy NETWORK(U,X)→PAGE(U,G,tytuł) nie zmieniał grupy U na G. Kolejna lokalna alternatywa V z G była osobnym filmem; przy odwrotnej kolejności oba warianty były razem.
- **FIXED CORE:** każdy rekord pamięta wyłącznie powiązany z dokładnym URL/tokenem parsera i jego autorytatywny lokalny właściciel (PAGE/DOM + nieogólny tytuł); grupa jest przeliczana dla jednej tożsamości `EngineSession`, dokumentu i `frameKey`, także gdy wariant pochodny pojawił się wcześniej. Nie scala się po samym tytule, URL hosta ani niesprawdzonym wspólnym podpisie. W razie późniejszego konfliktu dwóch lokalnych właścicieli odziedziczona grupa jest cofana, a każdy właściciel pozostaje oddzielny. Starsze reguły ochrony dokumentów, frame i dokładnych URL nie są znoszone.
- **Regresje:** NETWORK→PAGE master→PAGE alternatywa, PAGE→NETWORK/derived i NETWORK/derived→PAGE, późna sprzeczna grupa z tym samym tytułem, dwa frame z tym samym manifest tokenem i URL, nowa generacja dokumentu/stary callback, odrębne tokeny manifestów. Zachowano dotychczasowe testy `MediaFrameGroupCorrelationStage5Test`, izolację profili/sesji i ochronę podpisanych URL.
- **CI:** `.github/workflows/zavro-audit60-stage16a-regression.yml` uruchamia pełne Kotlin/Robolectric, AndroidTest compile bez APK, wszystkie JS oraz Android lint. Status końcowego commitu wymaga osobnej weryfikacji `completed/success`. Realny Gecko, zmiany źródeł w SPA, fab, Nothing Phone (1) Android 15/120 Hz: **NOT_TESTED**.
- **Granice:** ZC-AUD-038 i ZC-AUD-039 pozostają DO_RECHECK; ZC-AUD-002 BLOCKED / REAL_GECKO_REQUIRED, ZC-AUD-030 OPEN, ZC-AUD-031 PARTIAL. Bez APK, merge do main, zmiany podpisu, profili, prywatności i sesji. Następne `Dalej` → **16B/19 — ZC-AUD-038**, po CI.

## Etap 16B/19 — ZC-AUD-038: odtwarzalny wariant, poster i metadane kafelka (2026-10-31)

- **Baza:** etap 16A `1b7184602e020f1f90f3bba5103e990497fa2490`, [CI #41225476595](https://github.com/laszko14/nira-browser/actions/runs/41225476595) `completed/success`. Gałąź `fix/zavro-clean-audit60-stage16b-variant-representative-20261031`.
- **STILL_PRESENT:** `mediaTileItems` sortował wyłącznie po NETWORK/DOM. Gdy pierwszy master był AUTH_ERROR/UNPLAYABLE, a alternatywa CONFIRMED, Play na kafelku pozostawał wyłączony. Poster, czas i LIVE czytano wyłącznie z pierwszego rekordu, a menu nie dawało wyboru wykrytych wariantów. Odmienny rozmiar/bitrate nie może być bezpiecznie przenoszony między URL.
- **FIXED CORE:** reprezentant automatycznie wybierany według `CONFIRMED → NEEDS_VERIFICATION → niedostępny`, potem sieć/DOM i stabilny ID. Menu `Wybierz źródło` pozwala jawnie wskazać każdy wykryty wariant (bez wyświetlania podpisanego URL); Play, kopiowanie i pobieranie używają ID wskazanego rekordu, a nie URL innego wariantu. UI trzyma tylko ID w pamięci Compose i przy usunięciu źródła powraca do najbezpieczniejszego wyboru. Akcja weryfikuje przy aktualnym snapshot tę samą sesję, tabId, profil, prywatność, dokumentową stronę/loading, grupę, ramkę i format oraz korzysta z najnowszego URL po odnowieniu podpisu. Niedostępny wariant nie może zostać odtworzony.
- **Poster:** grupowy wybór przez istniejący `MediaPosterResolver` z rangami pochodzenia, a konflikt o równej mocy daje brak obrazu zamiast arbitralnego wariantu. Nie przenosi się obrazów między grupami ani frame. Miniatury wyłączone preferencją nadal pozostają wyłączone.
- **Metadata:** jednolity, niekonfliktowy czas VOD może służyć jako grupowy fallback, a jednoznaczny sygnał LIVE nie jest ukrywany przez anonimowy master. Rozmiar/bitrate, format, jakość oraz operacje zostają przypisane do **konkretnego wybranego URL**; przy braku jego danych rozmiar jest nieznany, nie kopiowany z jakości alternatywnej. Sprzeczne czasy nie są zgadywane.
- **Regresje:** Kotlin `MediaVariantSelectionAudit60Test`: potwierdzony child przy uszkodzonym masterze niezależnie od kolejności, jawne ID i fallback po usunięciu, poster z drugiego źródła, większy i konfliktujący poster, trzy niezależne frame/grupy, niejednoznaczne obrazy, VOD/LIVE/sprzeczne czasy/odrębne rozmiary, izolacja EngineSession/profili/private/tab/group/frame oraz URL renewal. Pozostałe testy kafelków oraz metadata pozostają uruchamiane w pełnej macierzy.
- **Weryfikacja:** `.github/workflows/zavro-audit60-stage16b-regression.yml` — Kotlin/Robolectric, AndroidTest compile bez APK, komplet JS i lint; zielony final SHA wymagany. Realny Gecko, rzeczywiste kadry posterów i akcje w MPV, Nothing Phone (1)/Android 15/120 Hz: **NOT_TESTED**.
- **Granice:** ZC-AUD-039 DO_RECHECK; ZC-AUD-002 BLOCKED / REAL_GECKO_REQUIRED, ZC-AUD-030 OPEN, ZC-AUD-031 PARTIAL. Bez APK, merge, zmian podpisu, profili i sesji. Kolejne `Dalej` → **16C/19 — ZC-AUD-039** po zielonym CI.

## Etap 16C/19 — ZC-AUD-039: stabilna tożsamość konfiguracji atrybutów (2026-10-31)

- **Baza:** 16B final `bdd9c3048037dea274c08190a21afdc78a9a6a3d`, [CI #41226970402](https://github.com/laszko14/nira-browser/actions/runs/41226970402) `completed/success`. Gałąź `fix/zavro-clean-audit60-stage16c-config-group-identity-20261031`.
- **STILL_PRESENT:** `scanPlayerConfigs.walk` odczytywał `data-setup/data-config/data-player-config` przez `JSON.parse` przy każdym skanie, a `groupFor(node)` używał świeżego obiektu. Pomimo identycznych źródeł kolejny skan tworzył nowe `groupId` i omijał `seen`, ponawiając IPC. Błąd w bazie 16B.
- **FIXED CORE:** grupa jest przypięta do elementu DOM, nazwy atrybutu i ścieżki strukturalnej (w tym pozycji playlisty), a nie nowego obiektu JSON, tytułu czy postera. Identyczny zestaw URL zachowuje grupę; zmiany metadanych aktualizują ją; nowa jakość lub zmiana źródeł generuje nową grupę. Pełne podpisane URL nie są obcinane ani publikowane w diagnostyce. WeakMap i limit 512 kotwic na element ograniczają pamięć. Zmiana dokumentu/historia resetuje tożsamości; niezależni właściciele nie są łączeni.
- **Regresje Node:** identyczny ponowny skan bez IPC, zmiana tytułu/postera w starej grupie, różne pozycje playlisty o tym samym tytule, nowa jakość i wymiana filmu, brak duplikatów po zmianie, dwa elementy z identycznym JSON/URL oraz nowy dokument SPA. Pełna macierz Kotlin/Robolectric, AndroidTest compile bez APK, JS, lint w `.github/workflows/zavro-audit60-stage16c-regression.yml`; końcowy SHA musi potwierdzić `completed/success`.
- **Granice:** realny GeckoView/DOM, 120 Hz i Nothing Phone (1) **NOT_TESTED**; ZC-AUD-002 **BLOCKED / REAL_GECKO_REQUIRED**, ZC-AUD-030 **OPEN**, ZC-AUD-031 **PARTIAL**. Bez APK, merge, zmian podpisu, profili i sesji.
- **Następne `Dalej`: 17A/19 — ZC-AUD-036** (DASH XML/DRM). Nie rozpoczynać przed osobną zgodą.

### Etap 16C — korekta izolowanego testu po pierwszym CI

- [CI #41228188285](https://github.com/laszko14/nira-browser/actions/runs/41228188285) na `0bbbb9abfebebc2a560931fba1af04b52bcfe548`: Kotlin/Robolectric PASS, AndroidTest compile PASS, nowe regresje 16C JS PASS; jedna dotychczasowa próba `player-config-titles-stage4.test.cjs` FAIL (2/3), bo harness wycinał fragment `scanPlayerConfigs` bez nowego `configGroupFor`. Produkcyjny skaner w tym teście łapał `ReferenceError` w dotychczasowym `try/catch`. Harness dołącza teraz dokładnie aktualną implementację pomocniczą ze źródła, bez jej duplikowania i bez modyfikacji kodu produkcyjnego. Ponowny pełny CI na końcowym SHA wymagany.
