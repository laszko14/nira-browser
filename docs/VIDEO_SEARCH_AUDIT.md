# Zavro Clean — audyt Wyszukiwarki filmów

## Stan i zasady wznowienia

- 2026-10-29: **A1 i A2 / 4 zakończone** (A1: kod + zdjęcie; A2: kod + celowany eksperyment SQL). Żadnej naprawy aplikacji jeszcze nie wdrożono.
- Gałąź: `fix/zavro-clean-video-search-audit-20261029`, baza `1ad05e9d12af3e060e4110880abb3fd65b313f1b` (potwierdzona przez `git ls-remote`). Bazowa gałąź `feat/zavro-clean-video-search-20261029` zachowana.
- Źródła APK v1191: `200f17f5914b328598ddf8724db49cff2f5107ad`. Porównanie z bazą audytu: różni się wyłącznie `docs/AI_HANDOFF.md`; kod UI odpowiada zatem źródłom skompilowanego APK. Historyczne CI: https://github.com/laszko14/nira-browser/actions/runs/40894977952 — nie uruchamiano ponownie i nie przypisujemy jego wyników nowemu commitowi.
- A3, A4: **NOT_TESTED / niewykonane**. Implementacja dopiero po A4 i kolejnym „Dalej”; liczba etapów N dopiero na podstawie końcowego backlogu A4.
- Jedno „Dalej” = jeden następny etap/podetap. Bez APK/workflow budującego APK do osobnego „Kompiluj”/„Już”.

## A1 / 4 — cel, zakres, dowody

Cel: ustalić niedostępność formularza i zbadać ergonomię ścieżki Menu → Ustawienia → Ogólne → Moje witryny, wraz z najbliższymi dialogami, powrotami i wariantem prywatnym. Kryterium zakończenia audytu: udokumentowane objawy, dowody kodu, rozdzielenie faktów od hipotez, priorytety i scenariusze weryfikacji; bez zmian produkcyjnych.

Przeczytano aktualne `AGENTS.md` i checkpoint v1191. Początkowy lokalny checkout `work` @ `0823f24e` był czysty, lecz nie stanowił zadanej bazy. Po pobraniu wskazanej gałęzi utworzono czystą gałąź audytu od `1ad05e9`; nie nadpisywano zmian ani nie analizowano całej historii.

Dowód urządzenia: dostarczony `1000115020.jpg`, 576 × 1280 px, SHA-256 `abeddbddd6a4db58af7a7feae30d3de505fa52ba4502bf421848168bb2f93477`. Według użytkownika: v1191, Nothing Phone (1), Android 15, 120 Hz. Widoczne: pasek systemowy i ActionBar, częściowo ucięty pierwszy rząd zaznaczania, dalsze akcje i opis przeciągania, dużo wolnego miejsca poniżej. Niewidoczne: profil, adres/Dodaj i licznik wyboru. Klawiatura nie jest widoczna. Zrzut nie dowodzi pustej bazy danych, parametrów fontScale/density, pozycji widoków ani wcześniejszych zdarzeń cyklu życia. Nie jest projektem nowego UI.

### Mapa ścieżki i kontrolek

Odnośniki do kodu dotyczą bazy audytu; `MVS` poniżej = `app/src/main/java/com/prirai/android/nira/videosearch/MyVideoSitesActivity.kt`.

| Odcinek | Dowód i ocena statyczna |
| --- | --- |
| Menu → Ustawienia | `components/toolbar/BrowserMenuController.kt:75` i `components/toolbar/unified/UnifiedToolbar.kt:690` kierują do `SettingsActivity`. |
| Ogólne → Moje witryny | `res/xml/preferences_headers.xml:19`, `res/xml/preferences_general.xml:39`, `settings/fragment/GeneralSettingsFragment.kt:27–32`: jawny Intent do MVS; bez parametrów ukrywających formularz. |
| Obszar okna | `AndroidManifest.xml:100–103`: wewnętrzna Activity, `AppThemeSettings`; `res/values/style.xml:107–127,195–200`: dziedziczy motyw z ActionBar. MVS nie korzysta z obsługi insets obecnej w `SettingsActivity:32–57` / `ext/Window.kt:89–116`. |
| Układ normalny | MVS:85–212: profil → adres/Dodaj → licznik → zaznaczanie → równoległość/szukaj → grupa → status/stop → historia/foldery → instrukcja → RecyclerView. Logiczna kolejność istnieje w kodzie. |
| Akcje nagłówka | MVS:113–183: wszystkie mają listenery. `onResume:574–698` włącza akcje zwykłego profilu; `updateSelectionControls:766–775` wyłącza zaznaczanie, gdy nie ma czego zaznaczyć. Szare przyciski na zdjęciu nie dowodzą błędu handlerów. |
| Akcje witryny | MVS:1691–1795: aktywność, wybór, Rozpoznaj/Sprawdź/Wskaż, Szukaj/Wyniki/Edytuj/Usuń, Zbierz/Zebrane; listenery i warunki dostępności istnieją. Część wymaga aktywnej karty Gecko/konfiguracji. Faktyczne wykonanie na urządzeniu NOT_TESTED; konfiguracja i silnik należą do A2/A3. |
| Powrót | MVS:728–730: strzałka wywołuje `finish()`, wracając do istniejących ustawień. `SettingsActivity:66–99` obsługuje stos fragmentów. Historia/foldery i udane uruchomienie/wskazywanie wracają do BrowserActivity i kończą MVS (`904–975`, `1273–1307`). Back z przeglądarki nie przywraca zakończonego MVS; powrót do listy wymaga ponownego wejścia przez ustawienia. Ocena ergonomii na urządzeniu pozostaje otwarta. |
| Prywatny UI | MVS:255–459,494–570,574–609,701–725: oddzielny panel, formularz RAM, komunikaty braku karty/wyników; na pauzie celowo czyszczona prezentacja. Nie traktować tego jako błędu zwykłego formularza ani zapisywać prywatnych pól do Bundle w przyszłej naprawie. Pełna prywatność dopiero A4. |

### Problemy i hipotezy A1

**UI-01 · P0 · formularz adres/Dodaj niedostępny — pierwszy priorytet implementacji P1/N.**

- **Potwierdzone zdjęciem:** brak formularza i przycięcie pierwszego widocznego rzędu. **Potwierdzone kodem:** formularz tworzony bezwarunkowo przed zaznaczaniem (MVS:103–123); w zwykłym profilu nie ma osobnego `GONE`, animacji, przesunięcia ani scroll-to dla formularza. Przełączenie na panel prywatny ukrywałoby również widoczne na zdjęciu akcje normalne. Brak konfiguracji witryn nie jest warunkiem utworzenia formularza; `addSite:741–764` zapisuje adres niezależnie od wykrywania wyszukiwarki.
- **H1, mocna hipoteza, NIE potwierdzony pomiar:** górne widoki rysowane/obcinane pod obszarem dekoracji okna (system/ActionBar) przy edge-to-edge na Androidzie 15. `app/build.gradle:27` targetSdk=37; MVS ma tylko stałe paddingi 16/12 dp i nie obsługuje systemBars/displayCutout/IME. Sąsiednie ustawienia stosują insets, MVS nie. Zniknięcie kilku początkowych elementów i przecięcie kolejnego, przy wolnym dole ekranu, pasuje bardziej do zakrycia/przesunięcia góry niż wyłącznie do braku wysokości.
- Nie potwierdzono nakładania ActionBar, dokładnej wielkości offsetu ani stanu IME/pan po wcześniejszym focusie. Sam brak listenera insets nie dowodzi konkretnej geometrii AppCompat. Na zdjęciu nie ma klawiatury; IME nie można wskazać jako ustalonej przyczyny. Brak identyfikatorów/restoration z UI-03 nie dowodzi samoczynnego przewinięcia tego ekranu.
- Przed naprawą zmierzyć bounds root/info/inputRow/selectionActions względem widocznego obszaru, pozycję okna i insets systemowe/IME przy pierwszym wejściu oraz po schowaniu klawiatury. Bez URL, fraz i danych formularza. Sprawdzić, czy dekoracja konsumuje już górny inset, aby nie dodać go drugi raz.
- Moduły do poprawy: przede wszystkim MVS:onCreate, ewentualnie **lokalna** konfiguracja jego okna w manifeście; wspólny motyw/helper zmieniać tylko przy dowodzie konieczności. Kierunek: poprawne wyznaczenie obszaru treści i dostępne przewijanie, bez stałej wysokości/ujemnych marginesów/wymuszania scroll-to. Nie zmieniać globalnie ustawień/przeglądarki.

**UI-02 · P2 · stały nagłówek bez przewijania, ryzyko wyparcia listy; dotyczy obu profili.**

- Potwierdzona struktura MVS:92–212: root i normalPanel to pionowe LinearLayout; wyłącznie RecyclerView ma wysokość 0 + weight=1. Nagłówek i formularz nie mają rodzica przewijającego. Scroll listy nie udostępni zakrytego nagłówka. Gdy suma wysokości kontrolek przekroczy przestrzeń okna (landscape/duża czcionka/IME), lista może otrzymać zerową wysokość, a dalsze elementy wyjść poza panel bez sposobu przewinięcia.
- Prywatny panel ma jeszcze więcej stałych kontrolek, a tylko wyniki i lista witryn dzielą resztę wysokości (MVS:454–459). ScrollView obejmuje wyłącznie listę prywatnych witryn, nie formularz i akcje.
- Przyciski `android.widget.Button`, długie etykiety i sztywne pary/trójki/czwórki równych kolumn (MVS:124–183,1738–1746) dają zawijanie już na zdjęciu i większą presję wysokości przy fontScale. Nie ustalono błędnego minimalnego touch target ani potrzeby zmniejszania czcionek. `editSite:1480–1542` ma własny wielopolowy LinearLayout bez ScrollView; dostępność z IME/landscape wymaga sprawdzenia.
- Kierunek: adaptacyjna, przewijalna kompozycja formularza/akcji z zachowaniem wirtualizacji i drag listy, czytelnych etykiet i obszarów dotyku. Nie wkładać długiego RecyclerView bez ograniczeń do ScrollView. Dostępność nagłówka ściśle wiąże się z UI-01; decyzja o zakresie P1/N po A4. Rzeczywiste progi przycięcia: NOT_TESTED.

**UI-03 · P2 · niezapisany zwykły formularz nie ma odtwarzania po rekreacji.**

- MVS:85–212,1480–1542: programowo tworzone pola i RecyclerView bez stabilnych ID, brak własnego zapisu/odtworzenia stanu; `savedInstanceState` trafia tylko do `super`. MVS nie deklaruje przejęcia orientation/configChanges. Wpisany zwykły adres i otwarte dialogi nie mają ścieżki przywracania po odtworzeniu Activity. To luka kodu; sam obrót i utrata tekstu na Androidzie NOT_TESTED.
- Na resume adapter jest opróżniany i ponownie zasilany Flow (`onPause:716–725`, `onResume:674–698`): pozycja listy po powrocie wymaga osobnego sprawdzenia; nie twierdzimy, że giną zapisane witryny/wybór. Ponowne wejście po `finish()` tworzy nowy UI. Normalne i prywatne odtwarzanie muszą mieć różne reguły; prywatne czyszczenie jest celowe.
- Kierunek: minimalny zapis zwykłego szkicu i stanu listy, osobno ustalić zachowanie dialogów. Bez utrwalania prywatnego stanu. Pełne ryzyka profili/cyklu życia: A4.

**UI-04 · P2 · dialog kolejki zamyka się także przy błędnej frazie.**

- MVS:814–873: `setPositiveButton` i `setNeutralButton` używają domyślnych listenerów buildera; `return@setPositiveButton` po błędzie nie zatrzymuje standardowego dismiss. Pusta albo zbyt długa fraza kończy się Toastem i zamknięciem dialogu; szkic nie jest zachowany. Również nieudane przygotowanie planu wymaga ponownego wpisania. Potwierdzenie wynika z kontraktu dialogu i kodu; dotyk na urządzeniu NOT_TESTED.
- Wzorzec poprawnej lokalnej walidacji już jest w `searchSite:1449–1477` i `editSite:1512–1542`: listener przycisku ustawiany po pokazaniu dialogu, pole błędu, zamknięcie po sukcesie. Proponowana naprawa analogiczna, bez zmiany silnika kolejki.

**UI-05 · P3 · pusty stan normalnej listy niewystarczająco objaśniony.**

- MVS:185–189,766–775: stała instrukcja przeciągania i licznik aktywnych wybranych witryn, brak dedykowanego „Brak witryn — dodaj adres”. Przy ukrytym formularzu zdjęcie pokazuje akcje i pusty obszar bez drogi rozpoczęcia. Sam pusty obszar zdjęcia nie rozstrzyga, czy lista naprawdę jest pusta. Flow nie ma osobnego wizualnego stanu loading/error; ocena błędów odczytu w A2.
- Kierunek: krótki komunikat tylko dla rzeczywiście pustej listy, bez rozbudowy interfejsu. Priorytet po przywróceniu formularza.

### Weryfikacja A1 i granice dowodów

| Kontrola | Status | Wynik |
| --- | --- | --- |
| Baza, czyste drzewo przed audytem, tożsamość kodu z buildem v1191 | PASS | git status/rev-parse/ls-remote oraz diff źródeł APK → baza: tylko handoff. |
| Analiza zdjęcia i ścieżek kodu UI | PASS | Prześledzono wejście, formularze, listenery, warunki dostępności, powroty, oba panele, motyw, manifest i insets. Objaw braku formularza na zdjęciu pozostaje błędem wersji v1191. |
| Kontrola geometrii/klawiatury/obrotu na Androidzie | NOT_TESTED | `adb devices`: brak podłączonych urządzeń; brak uruchomionego emulatora. Nie ustalono definitywnie H1. |
| JUnit/JS/lint/Gradle/APK/nowe CI | NOT_TESTED | Dokumentacyjny etap, bez uruchamiania buildów; wcześniejszy sukces nie jest testem tego UI. |
| Istniejące pokrycie UI | PASS (przegląd) | `MyVideoSitesRepositoryTest`, `MyVideoSitesManagementTest`, `MyVideoSitesSelectionTest` badają dane/Room; wyszukiwanie odwołań MVS w testach nie wykazało testu uruchamiającego tę Activity i jej geometrię. Testy nawigacji prywatnych wyników nie sprawdzają layoutu. |

Scenariusze dla P1/N i późniejszej weryfikacji (obecnie wszystkie runtime **NOT_TESTED**):

1. Pierwsze wejście v1191 vs poprawka: widoczne pole/Dodaj, profil, licznik; pomiar bounds i insetów, pusta lista oraz istniejące witryny. Dodać poprawny adres bez działającego auto-wykrywania, wyjść/wrócić, bez zmiany dotychczasowej konfiguracji.
2. Telefon portret/landscape, małe okno (np. 320 × 480 dp) i fontScale 1.0/1.3/2.0: wszystkie akcje osiągalne dotykiem/przewijaniem, lista ma użyteczny obszar; bez nakładania systemowych pasków/cutout. Etykiety czytelne, cele dotyku zachowane.
3. Adres i dialogi frazy/edycji: IME otwarta/zamknięta, focus oraz Back; nie zasłaniają się wymagane pola i akcje. Osobno rekreacja Activity z niezapisanym zwykłym adresem, listą przewiniętą i otwartym dialogiem.
4. Lista: pojedynczy/zbiorowy wybór, przełączniki, drag, wszystkie akcje i anulowanie usunięcia. Powrót z Gecko ręcznego wskazywania, wyników i folderów: sprawdzić istniejący stos nawigacji oraz ponowne wejście do MVS, zachowanie zapisanej listy/wyboru.
5. Profile: zwykły/inny zwykły/prywatny, brak prywatnej karty, pauza/resume i zmiana orientacji. Nie dopuścić do kopiowania zwykłych danych do panelu prywatnego ani prywatnego szkicu do stanu trwałego.

Ryzyko przyszłej poprawki: podwójne insets ActionBar/status, zmniejszenie pola listy przez IME, konflikty przewijania i drag, pomylenie stanu dwóch profili, regresje innych Activity przy zmianie wspólnego motywu. Proporcjonalne testy: rzeczywisty pomiar/layout w Robolectric tam, gdzie wiarygodny; instrumentacja i ręczne próby Android 15 do końcowej geometrii, IME i Gecko. Same regexy źródeł ani kompilacja nie zaliczają kryteriów wizualnych. Pomiar 120 Hz w A4; bez zmian częstotliwości.

## A2 / 4 — zarządzanie witrynami i konfiguracja

Audyt na czystym HEAD `674bd5c929959a617e9db063315b8a823f9892a6`, zgodnym z origin; wznowiono tę samą gałąź. Cel: prześledzić dziesięć operacji zarządzania i granice UI–Gecko–WebExtension–repozytorium–Room–profile, bez powtarzania A1. Kryterium ukończenia: dowody zapisu i ochrony konfiguracji, konkretne usterki/ograniczenia oraz luki testów. **PASS dla wykonania audytu, nie dla działania całej funkcji.**

Skróty: `Repo` = VideoSearchRepository.kt, `DAO` = VideoSearchDao.kt, `Bridge` = VideoSearchBridgeController.kt, `JS` = assets/extensions/zavro-video-search/content.js. Pliki Kotlin w tym samym pakiecie videosearch co MVS.

### Przepływ i pokrycie wymagań

| Operacja | Rzeczywista implementacja i wynik przeglądu |
| --- | --- |
| 1. Dodanie | MVS:741–764 → Repo:30–47 → DAO:60–65. `siteInputUrl` dodaje HTTPS do domeny, przycina zewnętrzne białe znaki, zachowuje ścieżkę/query; odrzuca credentials i inne schematy. Zapis nie wymaga sieci ani konfiguracji. Nie uruchamia automatu bezpośrednio po dodaniu; automat DOM jest jawnie wywoływany przyciskiem „Rozpoznaj”. Nie dodawać samowolnie skanowania/sieci w tle. |
| 2. Trwałość mimo błędu | Nowy rekord ma NEEDS_CONFIGURATION, własny ID i pozycję. Transakcja `createSiteIfAbsent` zwraca istniejący rekord dla identycznego URL bez nadpisania konfiguracji. `markAutoConfigurationIncomplete` nie usuwa witryny ani konfiguracji. `VideoSearchDatabase` to osobna trwała baza v1 bez destrukcyjnego fallbacku. Ponowne otwarcie bazy objęte istniejącymi testami, nie wykonano ich ponownie. |
| 3. Automat | MVS:1106–1150 → Bridge:969–973 → request/port → JS:124–145. Skan na żądanie, najwyżej pierwsze 80 inputów, konserwatywna punktacja, wymagany jednoznaczny selektor i bezpieczny mechanizm wysłania. READY wymaga też rozpoznanych kart. Częściowy wynik jest propozycją RAM ważną 120 s dla danego session/document, nie kompletną trwałą konfiguracją (Bridge:309–316,1346–1357). Ograniczenia CFG-01/02/04 poniżej. |
| 4. Ręczny wybór | MVS:1273–1307 → Bridge:1253–1334, kreator input/submit/cards/next. JS:149–307: przechwytuje zaufane dotknięcia, opcjonalne kroki można pominąć; brak zapisu przy przerwaniu kreatora. Zakończony kreator zapisuje pełny lub częściowy JSON przez `applyManualConfiguration`; brak kart daje REPAIR_REQUIRED. Nie jest to edytor osobnych selektorów, zawsze zaczyna od input. |
| 5. Ponawianie i edycja | Nazwa i limit zmieniane jednym UPDATE bez zmiany URL/selektorów. Przy istniejącej konfiguracji „Rozpoznaj” odmawia nadpisania; „Sprawdź” diagnozuje, a `offerRepair` wymaga jawnej zgody na zastąpienie, maksymalnie dwie próby. Automat nie zastępuje również częściowej konfiguracji ręcznej bez `confirmedReplace`. Ręczne ponowne wskazanie tworzy nowy cały JSON; pominięte kroki nie są scalane ze starymi. |
| 6. Włączanie | MVS:1545–1560 → Repo → DAO:107–108, pojedynczy UPDATE pola enabled i daty, bez utraty listy/selektorów. Wyłączona witryna nie jest wykonywana; pamięta wcześniejszy wybór. |
| 7–8. Wybór i pamięć | DAO:170–229: transakcje dla pojedynczego/zbiorowego wyboru, oddzielny JSON ustawień per profileId. „Wszystkie” dodaje wszystkie aktywne, zachowując dawne wyłączone; „Odznacz wszystkie” usuwa także zapamiętane wyłączone. Wybór nie jest ograniczony równoległością. Snapshot wykonania filtruje enabled i zachowuje kolejność. Repo/MVS odtwarzają dane przez Flow, nie przez stan CheckBox. |
| 9. Przeciąganie | MVS:214–248,1562–1595,1640–1690: lokalny porządek podczas drag, blokowanie nadpisania przez Flow, zapis końcowy. DAO:121–137: transakcyjne porównanie pełnej oryginalnej kolejności, kontrola permutacji, gęste pozycje; stare żądanie odrzucane. Po błędzie odświeżenie danych. Realny gest i wyścigi pauzy/Flow pozostają NOT_TESTED. |
| 10. Usunięcie | MVS:1599–1621: dialog z Anuluj/Usuń, dopiero `confirmed=true` trafia do Repo. DAO:144–159 usuwa rekord i jego wybór w jednej transakcji. Wyszukanie call-site'ów nie wykazało automatycznego usuwania witryn po błędzie konfiguracji. Prywatny przycisk „Usuń” jest jawnym żądaniem usunięcia RAM, bez dodatkowego dialogu. |

### Granice danych i autoryzacji

- Room: klucze witryn/ustawień zawierają profileId (`VideoSearchDbRows:12–38`); zapytania są filtrowane po profilu. `VideoSearchRowCodec:66–84` odrzuca prywatny scope przed konwersją do rekordu. Operacje selekcji/kolejności są transakcyjne. Baza nie ma unikalnego indeksu po URL, ale produkcyjna ścieżka dodawania serializuje sprawdzenie i insert w transakcji. Nie uznano tego za samodzielną usterkę.
- UI sprawdza bieżący owner i ponownie czyta witrynę przed konfiguracją. Repo nie czyta ProfileManager; traktuje przekazany scope jako właściciela operacji. Nie oznacza to automatycznie autoryzacji aktualnego foreground podczas każdego asynchronicznego zapisu; wyścigi przełączenia profilu/Activity są do analizy A4.
- Gecko: właściciel pochodzi z BrowserStore/contextId i hosta, nigdy z JS. `currentTab/isFresh` (Bridge:131–159) wiążą EngineSession, URL, documentId i profil; odpowiedzi muszą pasować do requestId, rodzaju zadania i dokumentu (238–320). Jedno pending na session, timeout i odrzucanie nieaktualnych odpowiedzi. Dwie identyczne otwarte witryny powodują odmowę `singleOrNull`, zamiast zgadywania karty.
- WebExtension: wyłącznie główny dokument HTTP(S), `document_idle`, bez iframe (`manifest.json`, JS:7). POST, obce origin i formularze hasła są celowo odrzucane (JS:20–30). Brak wsparcia niektórych stron nie uprawnia do osłabienia tych ograniczeń.
- Prywatne witryny: `VideoSearchPrivateSitesAccess` → `withAuthorizedTab` → `VideoSearchPrivateStore`, wyłącznie RAM, maksymalnie 200 witryn; dodana witryna jest od razu zaznaczona. UI udostępnia dodanie/wybór/usunięcie, nie udostępnia odpowiedników edycji/enable/drag/ręcznego kreatora zwykłego profilu. To **potwierdzona różnica zakresu**, nie dowód parytetu funkcji. Przed rozszerzeniem ustalić wymagany zakres w A4; nie przenosić tych danych do Room. Pełna kontrola końca sesji/prywatnego Gecko należy do A4.

### Usterki, ograniczenia i brakujące scenariusze

**CFG-01 · P1 · przekierowany zapisany URL może uniemożliwić konfigurację i późniejsze szukanie. Potwierdzony warunek w kodzie; witryny/urządzenie NOT_TESTED.**

`Bridge:865–882` dopasowuje automat i zwykłe pojedyncze wyszukiwanie do dokładnego zapisanego URL (pomija tylko końcowe `/`). Przykładowy syntetyczny scenariusz: zapis `http://example.invalid` → przekierowanie na `https://example.invalid`, albo dodane HTTPS → `www`/inna ścieżka językowa. Aktualna karta nie pasuje do rekordu; automat odmawia, a ręczny kreator też odmówi przy zmianie origin. Edycja MVS pozwala zmienić nazwę/limit, nie URL. Witryna pozostaje zapisana, ale użytkownik musi ustalić końcowy adres i dodać osobny rekord. Nie stwierdzono utraty danych.

Naprawa do rozważenia: jawna, kontrolowana aktualizacja adresu/zaakceptowanie kanonicznego celu z zachowaniem ID i danych oraz unieważnieniem niepasującej konfiguracji za zgodą. Nie luzować globalnie dopasowania URL ani obcinać query/tokenów. PASS: kontrolowane przekierowanie i korekta URL umożliwiają konfigurację bez obcej karty/profilu oraz bez zniknięcia starej listy/wyboru. Testy: fixture redirect w Gecko + Room dla korekty/konfliktów i zachowania parametrów.

**CFG-02 · P1 · brak płynnej konfiguracji między stroną formularza a wynikami. Potwierdzona luka przepływu; realne serwisy NOT_TESTED.**

Automat działa tylko na zapisanym dokładnym adresie i rozpoznaje karty wyłącznie tam. Jeśli homepage zawiera pole, ale nie wyniki, otrzymuje PARTIAL; propozycja RAM pozwala spróbować pojedynczego szukania, lecz na `/results` nie ma ścieżki automatycznego uzupełnienia konfiguracji tego rekordu. Ręczny kreator może zacząć na dowolnej stronie tego samego origin (`manualSiteTab`), ale wymaga input i wszystkich dalszych kroków w tym samym dokumencie (Bridge:1270–1300). Nie wysyła zapytania między krokami; picker blokuje normalne kliknięcia (JS:256–307). Gdy wyników nie ma na stronie formularza, można pominąć cards i zapisać częściową konfigurację, lecz nie ma osobnego „uzupełnij karty” bez ponownego wskazywania input. Jeżeli strona wyników ma inny input/submit niż homepage lub nie ma go wcale, pełna konfiguracja jest zablokowana albo zapisane selektory nie pasują do strony startowej.

Naprawa wymaga osobnego, kontrolowanego przepływu uzupełniania konfiguracji na wynikach (zachowanie potwierdzonych selektorów formularza i weryfikacja owner/dokumentu na każdym kroku), nie wyłączenia obecnych zabezpieczeń dokumentu. PASS: fixture z formularzem tylko na homepage i kartami tylko na `/results` można skonfigurować i ponownie użyć po restarcie. Testy: rzeczywisty wielostronicowy kreator Gecko, anulowanie, zmiana profilu/karty, Room z częściowym scalaniem. W A4 ocenić potrzebny oddzielny etap.

**CFG-03 · P1 · ochrona przed starym zapisem ma kolizję znacznika czasu. ODTWORZONE na rzeczywistych zapytaniach DAO w SQLite.**

Repo:51–80 sprawdza konfigurację na przekazanym snapshocie; SQL DAO:72–87 weryfikuje ID/URL/enabled/`updatedAtMs`, bez porównania obecnego JSON. `updatedAtMs` jest czasem ściennym w ms, nie gwarantowaną rewizją. Dwa zapisy z tym samym timestampem pozostawiają stary snapshot nadal ważny. Stary automat, rozpoczęty przed ręczną konfiguracją, może ją zastąpić bez `confirmedReplace`, mimo ochrony poprawnej dla różnych dat.

Eksperyment A2: Python sqlite3 `:memory:`, zapytania wydobyte bez zmian z adnotacji `acceptManualConfiguration` i `acceptAutoConfiguration`; syntetyczny rekord NEEDS_CONFIGURATION, enabled=1, updatedAtMs=1000. Ręczny UPDATE z expected=1000/now=1000 zapisał JSON manual; następnie stary auto UPDATE z expected=1000/now=1001 zapisał JSON auto. **Oba zwróciły rowcount=1; końcowa konfiguracja była auto. FAIL oczekiwanej ochrony.** Nie jest to uruchomienie Repo/Room ani pomiar częstości na Androidzie.

Naprawa: odporna na kolizje kontrola wersji rekordu/porównanie oczekiwanej konfiguracji przy atomowym zapisie; nie opierać wyłącznie na upływie ms. PASS: stary automat i stary kreator nie zmieniają nowszej konfiguracji przy identycznym czasie i przy cofnięciu zegara, także po enable/edycji. Testy: celowane JUnit/Room dla tych kolizji, zachowanie zaakceptowanej naprawy i izolacji profili. Zakres bez konieczności migracji rozstrzygnąć w implementacji, nie zakładać nowej zależności.

**CFG-04 · P2 · SPA ze zmianą URL w tym samym dokumencie traci gotowość mostu. Potwierdzona blokada kodu; zachowanie Gecko NOT_TESTED.**

JS:12,719–720 zapamiętuje startUrl i odrzuca dalsze komendy przy `location.href !== startUrl`; Hello zawiera startUrl. `isFresh` porównuje aktualny URL BrowserStore do portu. Brak ponownego handshake na pushState/popstate. Po client-side zmianie ścieżki/query/hash dokument może pozostać żywy, ale komendy nie będą obsługiwane do ponownego załadowania. Dokładne zachowanie BrowserStore/Gecko po history API wymaga instrumentacji. Kierunek: jawne odnowienie powiązania dokumentu i unieważnienie pending/propozycji przy nawigacji SPA, bez akceptowania starych wiadomości. PASS: konfiguracja/walidacja wracają do gotowości po kontrolowanej same-origin zmianie, obcy origin i stare odpowiedzi nadal odrzucane. Wspólny problem z A3; tam nie dublować zadania.

**CFG-05 · P2 · rozpoznanie kart jest wąską heurystyką, READY nie dowodzi działania wyszukiwarki. Ryzyko jakości, bez dowodu błędu konkretnej witryny.**

JS:103–113 używa siedmiu ustalonych selektorów, wymaga co najmniej dwóch elementów z linkami i dokładnie jednego pasującego selektora. Jedna karta, dwie pasujące rodziny klas lub inne nazwy dają brak pełnego automatu. Nie wykonuje próbnego wyszukiwania; karty polecanych filmów na homepage mogą wystarczyć do READY i trwałego CONFIGURED. Ręczny picker kart dopuszcza jedną kartę, ale wybiera pierwszy pasujący selektor klasy przodka, nie dowodzi semantyki wyników. Przed zmianą heurystyk potrzebne fixture i realne przypadki; nie oznaczać CONFIGURED jako zweryfikowanego end-to-end. Kryteria/testy korelacji i ekstrakcji rozszerzyć w A3.

Nie potwierdzono: utraty witryn po błędzie, zwykłego nadpisywania manual przez automat przy poprawnie różniących się rewizjach, utraty zapamiętanego wyboru, nieatomowej kolejności ani automatycznego usuwania po nieudanym rozpoznaniu. Nie dopisywać takich usterek na podstawie samej hipotezy.

### Testy i ryzyka A2

- **PASS — przegląd:** `MyVideoSitesRepositoryTest` (natychmiastowy zapis, duplikat, reopen, błędy, równoczesne add); `MyVideoSitesManagementTest` (edycja/enable/reorder, rollback, profile); `MyVideoSitesSelectionTest` (pojedynczy/all/disabled, współbieżność, reopen); `VideoSearchAutoConfigurationTest`, `VideoSearchManualConfigurationTest`, `VideoSearchConfigurationHealthTest`; `VideoSearchPrivateSitesAccessTest` i granica codec. To istniejące testy danych/protokołu, nie potwierdzenie uruchomienia kreatora w Gecko.
- **FAIL — celowany eksperyment SQLite:** CFG-03, inwariant odrzucenia starego auto-zapisu. Powtórzenie w prawdziwym Room/JUnit jeszcze **NOT_TESTED**. Testy stale scan/wizard używają różnych `nowMs`; nie zaliczają kolizji czasu.
- **NOT_TESTED:** JUnit/JS/Gradle/lint/CI/APK w A2, realne strony i telefon. Wyszukanie odwołań `zavro-video-search`, `detectResults`, `manualCard` w istniejącym `app/src/test/js` nie znalazło testu tej funkcji; historyczne 218 JS PASS nie dowodzi działania jej DOM/pickera.
- Brakujące scenariusze: pełne add → błąd konfiguracji → zamknięcie procesu → reopen; przekierowania; jeden film/brak wyników; formularz GET na innej stronie niż wyniki; SPA i opóźniony DOM; wygenerowane/zmienione selektory; 2 identyczne karty; anulowanie i timeout każdego kroku; edycja/disable/delete/reorder podczas kreatora; kolizja/rewers czasu; szybkie przeciwne checkbox/toggle z opóźnionymi zapisami; przełączenie profilu podczas odpowiedzi/synchronizacji UI; prywatna lista po ostatniej karcie. Nie zakładać, że zachowanie jest błędne, zanim dany scenariusz zostanie wykonany.
- Ryzyka przyszłych napraw: złagodzenie dopasowania może objąć niewłaściwą kartę/origin; scalenie częściowej konfiguracji może zachować nieaktualne selektory; zmiana CAS może odrzucać prawidłowe zapisy; zmiana URL może uszkodzić powiązanie historii/wyboru; zmiana RAM nie może wprowadzić trwałych danych prywatnych. Dalsze analizy A3/A4, następnie dopiero końcowy backlog i implementacja.

## Mapa dalszych etapów

- A2 / 4: zakończony; dowody i CFG-01–05 powyżej, bez napraw.
- **A3 / 4 — następny, dopiero po „Dalej”**: silnik wyszukiwania, wyniki i metadane. Prześledzić wykonanie normalne/prywatne, kolejkę/limity/paginację, URL/tytuły/miniatury, historię i foldery. Wykorzystać CFG-02/04/05 bez powtórnego audytu zapisu/konfiguracji.
- A4 / 4: prywatność, cykl życia, wydajność, testy i końcowy backlog P0–P3 z kryteriami PASS oraz liczbą etapów implementacyjnych N.
- P1 / N: najpierw UI-01 i konieczne powiązane warunki dostępności formularza. Inne naprawy dopiero według końcowego backlogu. **STOP po A2.**
