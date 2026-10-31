# Zavro Clean — GV-G1: bezpieczny plan weryfikacji na rzeczywistym GeckoView

Data: 2026-10-31. Etap POST-19 / GV-G1 = WYŁĄCZNIE PLAN. Nie jest to wykonanie testu na telefonie.

## Źródła i niezmienne statusy

- Bazowy SHA: `7ee61665d747d470d05af3fa2401412d72125179`, gałąź 19B `fix/zavro-clean-audit60-stage19b-final-verification-20261031`; workflow #41245341045 COMPLETED / SUCCESS. Źródło prawdy: `FINAL_AUDIT_19B_60.md` i `REPAIR_REGISTER_19.md`.
- Audyt 19B CORE: **56 PASS / 1 FAIL / 2 BLOCKED / 1 NOT_TESTED**. Urządzenie: wszystkie 60 fizycznych prób **NOT_TESTED**.
- Statusy logiczne (NIE ZMIENIAĆ bez nowych dowodów): `ZC-AUD-002 BLOCKED / SUSPECTED`; `ZC-AUD-030 OPEN / SUSPECTED` (w macierzy BLOCKED); `ZC-AUD-031 PARTIAL` (w macierzy FAIL kryterium zamknięcia); `ZC-AUD-043 CONDITIONAL_GUARD / LIKELY` (w macierzy NOT_TESTED).
- Ten dokument nie modyfikuje kodu produkcyjnego, konfiguracji Gradle, wydania, signing, sesji, profili ani rejestru 60.

## Bezpieczeństwo — bramki obowiązkowe

1. **Bez polecenia `Kompiluj` nie budować ani nie instalować APK**, nie używać `assemble*`, `install*`, `adb install`, nie odinstalowywać aplikacji, nie wykonywać `pm clear`, globalnego `clearData`, wipe, resetów profilu ani modyfikacji certyfikatu. Brak merge do `main`.
2. Testy wymagające fizycznego GeckoView są **ZABLOKOWANE** do chwili, gdy użytkownik zatwierdzi zbudowanie odpowiedniego, update-safe wariantu. Samo `Dalej` upoważnia tylko do jednego kolejnego podetapu przygotowania (bez APK).
3. Pierwszy wybór dla prób ryzykownych: oddzielny testowy installation/package lub odizolowane, jednorazowe środowisko z tym samym kodem silnika, w którym **nie ma danych użytkownika**. Jeżeli izolowany pakiet nie zachowuje wiernie integracji Zavro/Gecko, odnotować ograniczenie; nie uznawać go za dowód działania produkcyjnego pakietu. Zmiana package/flavor tylko w osobnym, zaakceptowanym podetapie.
4. Jeżeli kiedykolwiek będzie rozważona aktualizacja istniejącego `com.prirai.android.nira.debug` na Nothing Phone (1), najpierw osobna zgoda, zweryfikowana kopia zapasowa i możliwość przywrócenia, niezmieniony certyfikat SHA-256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`, prawidłowy versionCode, brak downgrade i brak odinstalowania. Sama kopia bez zweryfikowanego odtworzenia nie jest bramką PASS.
5. Wykorzystać tylko syntetyczne konta, dane, cookies i URL na kontrolowanym origin HTTPS; brak Sync, realnych loginów, tokenów, cudzych stron i produkcyjnych profili. Na etapie 031 usunąć można **wyłącznie profil założony specjalnie do próby**; jeśli taki brak, STOP.
6. Przerwać po nieoczekiwanej zmianie sesji/profilu, błędzie zapisu, podejrzeniu przecieku prywatności, nieudanym backupie, awarii Gecko lub pierwszym naruszeniu inwariantów. Zachować logi zredagowane; nie ponawiać destrukcyjnego kroku bez nowej analizy.

## Przygotowanie i identyfikacja próby (przyszłe podetapy)

- Telefon docelowy: Nothing Phone (1), Android 15, preferowane 120 Hz; zapisać model, build Androida, ustawienia 120 Hz, wersję **faktycznie spakowanego** GeckoView, numer i SHA aplikacji, identyfikator instalacji testowej, datę, oraz identyfikator kontrolnej strony. Wersja najnowszej dokumentacji Gecko nie musi odpowiadać użytej zależności w repo.
- Przygotować lokalnie kontrolowany serwis HTTPS z dwoma poprawnymi, różnymi plikami wideo A/B (różny czas i rozdzielczość), niepoprawnym B i stroną popup. Host ma zwracać wyłącznie syntetyczne znaczniki `A`, `B`, `P`, `Q` w cookies/localStorage/IndexedDB. Powtarzać ten sam origin w różnych kontekstach, aby sprawdzić izolację, nie tylko inną ścieżkę URL.
- Diagnostics tylko debug/test: zdarzenia z monotonically ordered index, monotonic timestamp, identyfikatory tab/EngineSession/extension-instance jako nieodwracalne etykiety na czas próby, Store.contextId label vs GeckoSessionSettings.contextId label, private flag, documentKey, lifecycle i UI media. Nigdy nie logować surowych cookies, wartości kont, pełnych URL ani rzeczywistych contextId.
- Przed uruchomieniem prób przygotować pozytywne kontrole (zwykła nawigacja, poprawne wideo A/B), negatywne kontrole (brak dostępu Q do danych P, brak danych starego źródła) i bezpieczny sposób przywrócenia środowiska testowego.

## Przypadki i kryteria dowodowe

### GV-002 — WebExtension background a żyjąca EngineSession

- Rozpocząć na nieprywatnej sesji testowej; wygenerować >=20 kolejnych dokumentów `media_document`; uchwycić native high-water, ID instancji background i tożsamość `EngineSession` bez ujawniania URL.
- **Wymagany warunek doświadczenia:** zrestartować/reinstancjować wyłącznie WebExtension background, zachowując dokładnie tę samą natywną EngineSession i repozytorium, a nie restart całego procesu. Sposób wykonania musi być wspierany w użytej wersji Gecko i potwierdzony obserwacją before/after. Nie używać spekulatywnego wyłączenia dodatku, jeśli niszczy sesję.
- Po restarcie odtworzyć zdarzenie nowego dokumentu/filmu i logować klucz oraz decyzję native (accepted/rejected), aktualizację FAB i odseparowanie prywatnych kart. Kontrole: normalna nawigacja bez restartu, pełny cold start z nową EngineSession.
- Rozstrzygnięcie: `REPRODUCED` tylko gdy reset background i **identyczna** EngineSession są zaobserwowane, a nowy dokument jest odrzucony wskutek starego high-water; brak kontrolowanego restartu = `BLOCKED` (nie `PASS`). Jeżeli warunek występuje, ale klucze są bezpiecznie przyjmowane, zapisać `NOT_REPRODUCED` z zakresem, bez deklaracji FIXED.

### GV-030 — popup/opener i izolacja Gecko OriginAttributes

- Dwie niezależne *normalne* tożsamości testowe P i Q (plus kontrola prywatna); użyć tego samego origin dla cookies, localStorage i IndexedDB, z unikalnymi znacznikami wyłącznie testowymi. Odnotować contextId Store, przygotowanego engine i faktyczny kontekst Gecko przed ładowaniem.
- Przetestować z P oraz Q: `window.open`, link `target=_blank`, `noopener`, oraz po suspend/restore. Zachować sprawność semantyki opener; nie podmieniać przygotowanego engine tylko po to, aby wymusić zgodność etykiet.
- PASS testu izolacji wymaga jednocześnie: brak odczytu znaczników P w Q i odwrotnie, brak przecieku do/from private, brak różnic po cold restart, zgodność zaobserwowanego kontekstu faktycznego engine z polityką; osobno zachować wynik tego, czy `window.opener` działa tam, gdzie powinien. Sam `Store.contextId` albo brak tokenu w jednym scenariuszu NIE rozstrzyga OriginAttributes.
- Jeśli środowisko nie pozwala bezpiecznie zaobserwować natywnej izolacji, pozostawić `BLOCKED/OPEN`. Naruszenie izolacji = krytyczna blokada dalszych testów i osobny raport bezpieczeństwa bez ujawniania wartości.

### GV-031 — trwałe wymazanie jednorazowego profilu

- Utworzyć profile **P-test (do usunięcia)** i **Q-control (zostaje)**. W obu zapisać różne syntetyczne znaczniki dla tego samego HTTPS origin w cookie/localStorage/IndexedDB; zweryfikować izolację jeszcze przed usunięciem.
- Zamknąć wszystkie karty/PWA/custom-tabs należące do P-test; sprawdzić, że nic aktywnego nie odtwarza zawartości i że session snapshot został pomyślnie zapisany. Następnie normalną ścieżką UI usunąć **tylko P-test**, włącznie z dziennikiem tombstone/retry; nie dotykać Q-control. Obserwować natywny kontekst, zapis i brak późnych restore/AddTab.
- Wykonać cold launch i dodatkowy cold launch (test powtórnego retry), sprawdzić brak starych kart/stanów i danych P-test z testowego harnessu potrafiącego czytać **dawny contextId P-test** bez odtwarzania profilu produkcyjnego; zweryfikować pełne zachowanie danych Q-control. Nie używać globalnego `clearData`.
- API `StorageController.clearDataForSessionContext(String)` zwraca `void`, a oficjalna dokumentacja wymaga zamknięcia sesji przed czyszczeniem, bo otwarta sesja może odtworzyć dane. Samo wywołanie metody, tombstone lub log `success` NIE jest dowodem trwałego usunięcia. Jeżeli brak bezpiecznego odczytu dawnego kontekstu, wynik `BLOCKED`/`PARTIAL`, nie `PASS`.
- Każdy wykryty znacznik P-test po kasowaniu, utrata Q-control, osierocona karta albo odtworzenie usuniętego kontekstu = FAIL próby; zabezpieczyć tylko syntetyczne dowody.

### GV-043 — przejście video.src/currentSrc i metadane

- Na kontrolowanej stronie wykonać sekwencję A (załadowane `loadedmetadata`) → natychmiast B (`src` B, gdy `currentSrc` / `readyState` mogą wskazywać A) → B gotowe; powtórzyć z błędnym B, listą `<source>`, oraz `blob:`/MSE tam, gdzie wspiera je rzeczywisty runtime.
- W każdym kroku rejestrować `src/currentSrc`, `readyState`, `duration`, `videoWidth/Height`, kolejność `MutationObserver`, `emptied`, `loadedmetadata`, `resize`, `durationchange` i kandydaturę wysłaną do native oraz prezentowaną w UI. Użyć dwóch formatów o jawnie różnych wymiarach/czasach i znanych manifestach, bez DRM.
- Wynik prawidłowy: gdy bieżące załadowane źródło jest A, nie wolno przypisać jego technicznych metadanych do B; po `HAVE_METADATA` i `currentSrc=B` B ma właściwe dane; żadne spóźnione zdarzenie A nie nadpisuje B. Brak `dimensionConflict` z pomieszanych źródeł oraz brak regresji detekcji przed Play.
- Test JS z fixture (17E) daje wyłącznie dowód warunkowego guardu, nie kolejności zdarzeń w Gecko. Brak osiągalnego okna A/B na tym egzemplarzu oznacza `NOT_REPRODUCED` w ograniczonym scenariuszu, a nie `FIXED` całej hipotezy.

## Kolejność, zapis i raportowanie

1. G1 (TEN COMMIT): plan i dokumentacyjna bramka CI; żadnych fizycznych testów, APK, zmian statusów.
2. G2 (dopiero `Dalej`): kontrolowane fixture HTTPS, próbki A/B i bezinwazyjna weryfikacja statyczna oraz testy bez APK. Nie uruchamiać na danych użytkownika.
3. G3 (kolejne `Dalej`): minimalne, debug-only hooki do obserwacji i testy instrumentacyjne **wyłącznie przygotowanie/kompilacja źródeł testów bez APK**. Bez modyfikacji logiki produkcyjnej.
4. G4 (wyłącznie osobne `Kompiluj`): utworzenie zgodnego podpisem artefaktu, weryfikacja SHA certyfikatu, wersji i podpisu; instalacja tylko po dodatkowej zgodzie oraz sprawdzeniu backupu i bezpiecznego środowiska.
5. G5 (oddzielne `Dalej` po dostępności urządzenia/artefaktu): najpierw niedestrukcyjne 043, 002, 030; **031 na samym końcu** i tylko na przygotowanym profilu testowym. Każdy test osobny podetap i checkpoint; nie traktować ciemnych obszarów jako PASS.
- Raport dowodowy dla każdego ID: `test-case / base SHA / test SHA / Gecko artifact version / Android build / package / profile labels / event timeline / expected vs actual / control outcomes / artifacts checksum / limitations / verdict + status authorizer`. Odróżnić `REPRODUCED`, `NOT_REPRODUCED`, `BLOCKED`, `NOT_TESTED` i późniejszy `FIXED` po osobnym code+test+CI.
- Zmiana statusu w `REPAIR_REGISTER_19.md` lub `FINAL_AUDIT_19B_60.md` dopiero po nowych, audytowalnych dowodach; historyczny 19B pozostaje niezmienny (ewentualny nowy raport G5, bez przepisywania historii).

## Stan po G1 i bramka STOP

- Dokładnie **4 nierozstrzygnięte przypadki**; żaden nie ma fizycznego dowodu GeckoView/Nothing Phone (1). Wszystkie ich wcześniejsze statusy pozostają jak w 19B.
- **Następne osobne `Dalej`: G2 — przygotowanie bezpiecznego fixture i testów bez APK**. Nie przechodzić dalej bez zakończenia CI G1 na jego finalnym SHA.

Źródła zewnętrzne: dokumentacja Mozilla GeckoView StorageController `https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/StorageController.html`; WebExtension lifecycle `https://mozilla.github.io/geckoview/consumer/docs/web-extensions` oraz MDN `https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Background_scripts`. Zweryfikować API również dla dokładnej zależności projektu przed G3.