# Etap 7/9 — plan rzeczywistej weryfikacji GeckoView i Nothing Phone (1)

**Rodzaj:** wyłącznie plan i przygotowanie materiałów. Stan po ZC-PROC-06 (`5215b217fd70a9094b5ee75b11751670e3a0b45c`), historyczna macierz `FINAL_AUDIT_19B_60.md` oraz późniejsze G2/G3B/G3C/G4D. Ten dokument NIE jest wykonanym testem, nie zmienia rejestru 60 ustaleń ani nie wprowadza nowych hooków. Użyj go dopiero w odrębnie autoryzowanej, bezpiecznej próbie.

## Aktualność dowodów i granica pewności

- **19B CORE:** 56 PASS / 1 FAIL / 2 BLOCKED / 1 NOT_TESTED, co nie oznacza testów Android/Gecko. W G3C i G4D nie przyznano żadnemu z czterech ID nowego werdyktu.
- **Nowa obserwacja użytkownika po G4D:** aktualizacja do 1196 uruchomiła aplikację i zachowała widoczne ustawienia. To informacja z obsługi telefonu, NIE niezależny pomiar `contextId`, `OriginAttributes`, trwałości usuwania, zgodności wszystkich danych, podpisu instalacji lub zachowania na wszystkich profilach.
- **Ważna zmiana warunku:** `tools/gecko-fixture-g4d/preflight-device.sh` wymaga **zainstalowanej wersji 1195**; po zgłoszeniu 1196 nie uruchamiać go bez aktualizacji kontraktu w osobno zatwierdzonym zadaniu. Dane G4C o podpisie artefaktu nie są dowodem podpisu rzeczywiście zainstalowanego APK.
- Odnotuj przed przyszłym testem `appSha`, rzeczywisty `appPackage`, `versionCode`, Gecko build, Android 15, tryb wyświetlacza 120 Hz *i zaobserwowany refresh rate*. Sam tryb 120 Hz nie jest dowodem płynności. Nie przypisuj kompilacji testów instrumentacyjnych statusu DEVICE_VERIFIED.

## Bramka bezpieczeństwa przed jakimkolwiek przyszłym testem

1. **W tym etapie:** żadnych `adb`, instalacji/aktualizacji, `assemble*`, `pm clear`, kasowania profili, otwierania prywatnych adresów, modyfikacji telefonu, danych ani kont. NIE zbieraj logów z aktywnej prywatnej sesji użytkownika.
2. Przed przyszłym testem konieczna jest osobna autoryzacja odpowiedniego **testowego środowiska**, świadoma decyzja dotycząca instalacji i weryfikacji podpisu oraz plan odzyskania potrzebnego stanu. Android backup / eksport profilu G4D jest niepełny. Nie zakładaj, że zachowane ustawienia oznaczają backup. Preferuj izolowany profil/instalację testową, bez migracji realnych danych.
3. Syntetyczne P/Q i A/B muszą pochodzić z kontrolowanego, naprawdę zaufanego HTTPS origin (G2). Nie importuj niezaufanego certyfikatu G2/localhost na telefon, nie wyłączaj weryfikacji TLS, nie używaj prawdziwych cookies, haseł, URL z tokenami ani kont.
4. Pierwsze wykonanie: tylko scenariusze **niedestrukcyjne**. ZC-AUD-031 przeprowadź **jako ostatni** po oddzielnej jednoznacznej zgodzie na usunięcie *jednorazowego testowego profilu*, potwierdzeniu odtworzenia lub niezależnego środowiska i kontroli nieusuwalnego Q; w przeciwnym razie BLOCKED.
5. Użyj istniejącego `tools/gecko-fixture-g3c/NOT_TESTED_TEMPLATE.json`, `extract-g2.cjs` i `tools/gecko-fixture-g3c/check-evidence.cjs` — **oddzielnie dla każdego ID**, nigdy jako deklaracji PASS. `physicalDeviceObserved` ustawiaj na true wyłącznie po faktycznym pomiarze. Zachowaj tylko zredagowane pola z SHA-256 źródeł; surowe lokalne logi nie trafiają do repo.

## Macierz przyszłych prób (wszystkie NIE WYKONANE w ZC-PROC-07)

### ZC-AUD-043 — CONDITIONAL_GUARD / LIKELY
**CORE 19B:** NOT_TESTED. **Device:** NOT_TESTED. **Kolejność:** 1 (bez kasowania).

- **Wejście:** `tools/gecko-fixture-g2/site/video.html` w odizolowanym profilu; A (2 s, 160×90), potem B (5 s, 320×180). Oddzielne próby B=404, `source` lista, `blob`; z nową kartą jako kontrolą negatywną.
- **Obserwuj:** rzeczywistą kolejność `src`/`currentSrc`/`loadedmetadata`/`error`/`resize`/`durationchange`, rodzaj właściciela DOM, zmianę aktualnej wersji w `uiRepository` i to, co pokazuje kafelek/FAB; tylko zredagowane A/B i wyniki native.
- **Oracle:** po A→B w UI nie mogą zostać metadane A przy nieprawidłowym B; po poprawnym B metadane B należą do bieżącego dokumentu; nie wnioskuj z samego `currentSrc` przy błędnym B. Kontroluj niezależną kartę i źródła z różnych ramek.
- **Brak dowodu:** sama oś G2 DOM lub test JS na komputerze bez korelacji z realnym Gecko i native to NOT_TESTED.

### ZC-AUD-002 — BLOCKED / SUSPECTED
**CORE 19B:** BLOCKED. **Device:** NOT_TESTED. **Kolejność:** 2 (nie resetuj aplikacji zamiast samego background).

- **Wejście:** izolowana karta testowa i co najmniej 20 kolejnych `media_document`; odnotuj native `EngineSession` i high-water przed interwencją.
- **Obserwuj:** **kontrolowany restart samego WebExtension background** przy zachowanej tej samej `EngineSession`, nowe `documentKey` po resecie licznika, `document_ready`/native accepted vs rejected, aktualne menu filmów; porównaj z normalną nawigacją bez restartu i osobną nową `EngineSession`.
- **Oracle:** nowy prawidłowy dokument nie powinien zostać odrzucony ani pomylony z poprzednim, a high-water nie może zostać zaniżony przez przestarzałe wiadomości. Inny profil i prywatna sesja nie mogą współdzielić epoki.
- **Blokada:** G3B nie potrafi wykazać kontrolowanego restartu background przy niezmienionej sesji; bez zaakceptowanego niedestrukcyjnego hooku i obserwacji warunku wstępnego pozostać BLOCKED. Nie używaj `force-stop` jako substytutu.

### ZC-AUD-030 — OPEN / SUSPECTED
**CORE 19B:** BLOCKED. **Device:** NOT_TESTED. **Kolejność:** 3 (tylko syntetyczne P/Q).

- **Wejście:** dwa **jednorazowe** profile P/Q, w tym kontrolny prywatny kontekst, ta sama poprawna domena HTTPS; `tools/gecko-fixture-g2/site/index.html` i `tools/gecko-fixture-g2/site/popup.html`. Testuj `window.open`, `target=_blank` i `noopener`, a także zamknięcie i cold restart.
- **Obserwuj:** faktyczny wynik cookie / localStorage / IndexedDB odczytywany w P oraz Q; nowo otwarte okno, profil, prywatność i profil odbiorcy; zestawienie `BrowserStore.contextId` z `GeckoSession.settings.contextId` oraz niezależnym wynikiem behawioralnym.
- **Oracle:** wartości P nie są czytelne w Q i odwrotnie, w żadnym wariancie otwarcia; prywatny zapis nie przechodzi do zwykłego profilu ani do kolejnej sesji. Dodatkowo sprawdź, że otwarcie z `noopener` nie odziedziczy właściciela błędnie.
- **Granica:** ustawienia `GeckoSession.settings.contextId` **nie dowodzą** wewnętrznych `OriginAttributes`; bez rzeczywistych obserwacji wszystkich trzech magazynów nie twierdź, że izolacja jest naprawiona lub że wyciek występuje.

### ZC-AUD-031 — PARTIAL
**CORE 19B:** FAIL warunku pełnego zamknięcia. **Device:** NOT_TESTED. **Kolejność:** 4 — wyłącznie po osobnej zgodzie na destrukcyjny test.

- **Wejście:** P-test, jednorazowy i pusty od prywatnych danych, i niezależne Q-control; zapisz tylko syntetyczne znaczniki cookie/localStorage/IndexedDB, otwórz i zamknij wszystkie sesje P-test. Zapisz identyfikator dawnego kontekstu wyłącznie lokalnie, bez eksportu do chatu.
- **Obserwuj:** wyłącznie po zatwierdzonym usunięciu **P-test** stan tombstone/retry i rzeczywistą dostępność starych znaczników przez *dawny* `contextId`, po **dwóch** cold restartach; Q-control ma zachować swoje dane i żadnych niepowiązanych profili/kart nie dotknąć.
- **Oracle:** żaden magazyn starego P-test nie jest dostępny w dawnym kontekście po restartach, Q bez zmian. Wywołanie void `clearDataForSessionContext` nie stanowi potwierdzenia zakończenia operacji.
- **STOP:** bez nowej jednoznacznej zgody, odizolowanego środowiska i możliwego do sprawdzenia stanu przed/po **nie usuwaj niczego**, status pozostaje PARTIAL.

## Kontrole uzupełniające — urządzenie i 120 Hz

- **Profile/prywatność:** sprawdź normalny, własny i prywatny kontekst z izolacją kart, miniatur, favicon i cache po bezpiecznym restarcie. Bez ingerowania w istniejące profile właściciela.
- **Sesje i wznowienie:** uruchom/zamknij jednorazową kartę, zmień grupę i profil, wróć z tła i wykonaj normalny cold restart; po każdym porównaj bieżący owner/epokę z oczekiwanym stanem. Nie myl cold restart z restartem tylko background z 002.
- **Media:** rozszerz A→B o przejście SPA/hashchange, `currentSrc` błędnego B i późne zdarzenie z poprzedniej karty. Wszystkie obserwacje z fikcyjnymi URL i bez obchodzenia DRM.
- **Panele i gesty:** dla lewej i prawej krawędzi zbadaj otwarcie/zamknięcie, szybki fling, częściowy swipe i anulowanie, wielodotyk, systemowy Back (nawigacja trzyprzyciskowa), rotację, priorytet przesuwania kart vs chwyt panelu. Sprawdź ręczną szerokość P/L oraz RTL; zarejestruj oczekiwany/zaobserwowany stan, nie tylko screenshot.
- **TestLab/jank:** kontrolowany przebieg 60 s i ewentualne 120 s na 120 Hz: zapisz liczbę ramek, procent jank, czas najdłuższej klatki oraz czasy LEFT_PANEL_OPEN / RIGHT_PANEL_OPEN z tym samym scenariuszem. 120 Hz *nie* jest automatycznie PASS płynności. Zablokuj zbieranie nazw, adresów i prywatnej zawartości stron.

## Procedura odbioru dowodów i decyzji

| Krok | Warunek przejścia | Gdy brak |
|---|---|---|
| 0. Autoryzacja i izolacja | Jednoznaczna zgoda na właściwe testy, podpis/wersja rozpoznane, zaufany HTTPS, odtwarzalna i bezpieczna izolacja; dla 031 osobna zgoda | STOP — NOT_TESTED/BLOCKED |
| 1. Reprodukcja | Test normalny + negatywny na tym samym buildzie, kolejność i owner potwierdzone przez zaufane źródła | NOT_TESTED lub INCOMPLETE |
| 2. Materiały | Oddzielny zredagowany pakiet G3C na ID, rozróżnienie czasu DOM i monotonicznego Android, źródła NATIVE/DOM/CONTROL i hash danych | INCOMPLETE |
| 3. Review | Człowiek ocenia oryginalny kontrprzykład, alternatywne przyczyny i dane; sam `check-evidence.cjs` nie orzeka PASS | MANUAL_REVIEW_REQUIRED |
| 4. Ewentualny status | Tylko nowy, wiarygodny dowód uprawnia do zmiany rejestru w osobnym zadaniu i CI | Zachowaj werdykt 19B |

**Wynik etapu 7/9:** PLAN_PREPARED, fizyczne ZC-AUD-002/030/031/043 **NOT_TESTED**, DEVICE_VERIFIED = NO, APK_VERIFIED = NO. Weryfikacja CI tego dokumentu/testów oznacza wyłącznie **CODE_VERIFIED** materiałów planistycznych, nie weryfikację Gecko na urządzeniu.
